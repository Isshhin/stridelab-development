package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class IndependentCompletionAttributionTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val day = LocalDate.of(2026, 11, 12)
    private val owner = "synthetic-runner"
    private val activityId = "garmin-source-01"

    private fun activity(
        id: String = activityId,
        source: String = "garmin",
        type: String = "running",
        date: LocalDate = day,
        distance: Double? = 8000.0,
        duration: Double? = 2900.0
    ) = ActivityRecord(
        id, "Synthetic evening run", type, source,
        date.atTime(18, 15).atZone(zone).toInstant(),
        distance, duration, null, null, null, null
    )

    private fun source(
        record: ActivityRecord = activity(),
        scopedOwner: String = owner,
        identity: ImportedSourceIdentity? = null
    ) = IndependentScopedRunningRecord(scopedOwner, record, identity)

    private fun plan(
        ownerId: String = owner,
        linked: String? = activityId,
        status: String = "completed",
        planStatus: String = "approved",
        sessionId: String = "workout-01"
    ) = PlanSnapshot(
        id = "plan-01",
        owner = ownerId,
        engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY,
        createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(PlannedSession(
            id = sessionId,
            date = day,
            discipline = "run",
            title = "Easy aerobic",
            minutes = 50,
            distanceKm = 8.0,
            kind = "easy",
            status = status,
            linkedActivityId = linked
        )),
        explanations = emptyList(),
        status = planStatus
    )

    private fun inspect(
        plans: List<PlanSnapshot> = listOf(plan()),
        rows: List<IndependentScopedRunningRecord> = listOf(source())
    ) = IndependentCompletionAttribution.inspect(owner, "plan-01",
        "workout-01", plans, rows, zone)

    private fun observed(
        plans: List<PlanSnapshot> = listOf(plan()),
        rows: List<IndependentScopedRunningRecord> = listOf(source())
    ): IndependentCompletionAttributionDecision.LinkedObservation {
        val decision = inspect(plans, rows)
        assertTrue(decision.toString(),
            decision is IndependentCompletionAttributionDecision.LinkedObservation)
        return decision as IndependentCompletionAttributionDecision.LinkedObservation
    }

    private fun reject(
        rule: String,
        plans: List<PlanSnapshot> = listOf(plan()),
        rows: List<IndependentScopedRunningRecord> = listOf(source())
    ) {
        val result = inspect(plans, rows)
        assertTrue(result.toString(), result is IndependentCompletionAttributionDecision.Deferred)
        assertEquals(rule, (result as IndependentCompletionAttributionDecision.Deferred).ruleId)
    }

    @Test fun exactManualLinkExposesRecordedDistanceWithoutClaimingExecution() {
        val out = observed()
        assertEquals(8.0, out.recordedDistanceKm!!, 0.00001)
        assertEquals(2900.0, out.recordedElapsedSeconds!!, 0.00001)
        assertEquals(day, out.observedOn)
        assertTrue(out.linkedStatusReportsCompleted)
        assertFalse(out.hasManualWorkoutLog)
        assertFalse(out.sourceEvidenceVerified)
        assertFalse(out.workoutCompletionVerified)
        assertFalse(out.authorizedForAdaptiveLoad)
        assertFalse(out.authorizedForProduction)
    }

    @Test fun identityClaimsAreNotElevatedToVerifiedCompletion() {
        val identity = ImportedSourceIdentity(owner, "garmin", "external-1")
        val out = observed(rows = listOf(source(identity = identity)))
        assertTrue(out.assertedSourceIdentityAvailable)
        assertFalse(out.sourceEvidenceVerified)
        assertFalse(out.workoutCompletionVerified)
    }

    @Test fun differentPlanOwnerFailsClosed() {
        reject("PLAN_OWNER_SCOPE_CONFLICT", plans = listOf(plan(ownerId = "other-athlete")))
    }

    @Test fun differentActivityOwnerOrClaimedIdentityOwnerFailsClosed() {
        reject("ACTIVITY_OWNER_SCOPE_CONFLICT",
            rows = listOf(source(scopedOwner = "other-athlete")))
        reject("ACTIVITY_OWNER_SCOPE_CONFLICT", rows = listOf(source(
            identity = ImportedSourceIdentity("other-athlete", "garmin", "external-1")
        )))
    }

    @Test fun repeatedIdenticalActivityRowsCanBeReadWithoutDoubleCounting() {
        val row = source()
        val out = observed(rows = listOf(row, row))
        assertEquals(8.0, out.recordedDistanceKm!!, 0.00001)
    }

    @Test fun conflictingSameIdRecordContentIsNotEligible() {
        reject("LINKED_SOURCE_NOT_UNIQUE",
            rows = listOf(source(), source(activity(distance = 9000.0))))
    }

    @Test fun twoLocalIdsForOneOriginalProviderEventRequireReview() {
        val first = source(identity = ImportedSourceIdentity(owner, "garmin", "same-external"))
        val duplicate = source(
            record = activity(id = "secondary-cached-id",
                date = day.plusDays(2), distance = 12000.0),
            identity = ImportedSourceIdentity(owner, "garmin", "same-external")
        )
        reject("REPEATED_ORIGINAL_SOURCE_IDENTITY", rows = listOf(first, duplicate))
    }

    @Test fun distinctProviderIdsWithMatchingTimeAndMetricsRequireReview() {
        reject("POSSIBLE_DUPLICATE_EVENT_REVIEW",
            rows = listOf(source(), source(activity(id = "fit-activity", source = "fit"))))
    }

    @Test fun twoDifferentRunsSeparatedByAnHourAreNotCollapsed() {
        val later = activity(id = "other-run", source = "fit").copy(
            startedAt = activity().startedAt.plusSeconds(3600)
        )
        val out = observed(rows = listOf(source(), source(later)))
        assertEquals(activityId, out.activityId)
    }

    @Test fun manuallyLinkedButDifferentRecordedDayRequiresReview() {
        reject("LINKED_DATE_REVIEW_REQUIRED",
            rows = listOf(source(activity(date = day.plusDays(1)))))
    }

    @Test fun missingOrUnlinkedRecordIsNotInferredFromDateOrDistance() {
        reject("LINKED_SOURCE_NOT_UNIQUE", rows = emptyList())
        reject("NO_EXPLICIT_SOURCE_LINK", plans = listOf(plan(linked = null)))
    }

    @Test fun nonrunningAndRetiredSourceRecordsCannotCountAsTrainingEvidence() {
        reject("LINKED_SOURCE_NOT_ACTIVE_RUN",
            rows = listOf(source(activity(type = "walk"))))
        reject("LINKED_SOURCE_NOT_ACTIVE_RUN",
            rows = listOf(source(activity(source = "intervals"))))
    }

    @Test fun missingDistanceOrTimeRemainNullRatherThanBecomingZero() {
        val out = observed(rows = listOf(source(activity(distance = null, duration = null))))
        assertNull(out.recordedDistanceKm)
        assertNull(out.recordedElapsedSeconds)
        assertFalse(out.authorizedForAdaptiveLoad)
    }

    @Test fun malformedRawMetricsCannotBecomeEvidence() {
        reject("INVALID_SOURCE_METRICS",
            rows = listOf(source(activity(distance = Double.NaN))))
        reject("INVALID_SOURCE_METRICS",
            rows = listOf(source(activity(duration = -1.0))))
    }

    @Test fun draftOrArchivedPlansCannotEstablishLinkedCompletion() {
        reject("PLAN_NOT_APPROVED", plans = listOf(plan(planStatus = "draft")))
        reject("PLAN_NOT_APPROVED", plans = listOf(plan(planStatus = "archived")))
    }

    @Test fun unreportedOrNonrunningSessionCannotEstablishRunCompletion() {
        reject("SESSION_NOT_REPORTED_COMPLETED", plans = listOf(plan(status = "planned")))
        val nonRun = plan().copy(sessions = listOf(
            plan().sessions.single().copy(discipline = "str")
        ))
        reject("SESSION_NOT_RUNNING", plans = listOf(nonRun))
    }

    @Test fun oneSourceLinkedToTwoSessionsIsAmbiguous() {
        val first = plan()
        val second = first.copy(
            id = "plan-02", sessions = listOf(
                first.sessions.single().copy(id = "workout-02")
            )
        )
        reject("SOURCE_LINK_NOT_UNIQUE", plans = listOf(first, second))
    }

    @Test fun duplicatePlanIdOrSessionIdCannotBeSelected() {
        reject("PLAN_NOT_UNIQUE", plans = listOf(plan(), plan()))
        val duplicatedSessions = plan().copy(sessions = listOf(
            plan().sessions.single(), plan().sessions.single()
        ))
        reject("SESSION_NOT_UNIQUE", plans = listOf(duplicatedSessions))
    }

    @Test fun sameInputsProduceSameReadOnlyResult() {
        val current = plan()
        val row = source()
        val a = inspect(listOf(current), listOf(row))
        val b = inspect(listOf(current), listOf(row))
        assertEquals(a, b)
        assertEquals("completed", current.sessions.single().status)
        assertEquals(8000.0, row.activity.distanceM!!, 0.00001)
    }
}
