package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class IndependentWeeklyEvidenceLedgerTest {
    private val owner = "synthetic-owner"
    private val zone = ZoneId.of("Asia/Manila")
    private val monday = LocalDate.of(2026, 11, 9)

    private fun run(
        id: String, day: LocalDate, link: String? = null,
        status: String = if (link == null) "planned" else "completed"
    ) = PlannedSession(
        id = id, date = day, discipline = "run",
        title = "Easy run", minutes = 45, distanceKm = 10.0,
        kind = "easy", status = status, linkedActivityId = link
    )

    private fun plan(
        ownerId: String = owner,
        sessions: List<PlannedSession> = listOf(
            run("session-1", monday.plusDays(1), "source-1"),
            run("session-2", monday.plusDays(3))
        ),
        status: String = "approved"
    ) = PlanSnapshot(
        id = "plan-ledger", owner = ownerId,
        engineVersion = ENGINE15_VERSION, policyId = ENGINE15_POLICY,
        createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = sessions,
        explanations = emptyList(),
        status = status
    )

    private fun recorded(
        id: String = "source-1",
        day: LocalDate = monday.plusDays(1),
        distanceM: Double? = 5000.0
    ) = IndependentScopedRunningRecord(
        owner,
        ActivityRecord(
            id = id, title = "Actual recorded run",
            type = "running", source = "garmin",
            startedAt = day.atTime(18, 0).atZone(zone).toInstant(),
            distanceM = distanceM, durationS = 1800.0,
            movingTimeS = null, recordedPace = null,
            averageHr = null, elevationM = null
        )
    )

    private fun ledger(
        p: PlanSnapshot = plan(),
        otherPlans: List<PlanSnapshot> = emptyList(),
        sources: List<IndependentScopedRunningRecord> = listOf(recorded())
    ): IndependentWeeklyEvidenceLedgerDecision =
        IndependentWeeklyEvidenceLedger.inspectPlan(
            owner, "plan-ledger", listOf(p) + otherPlans, sources, zone
        )

    private fun candidate(
        p: PlanSnapshot = plan(),
        sources: List<IndependentScopedRunningRecord> = listOf(recorded())
    ): IndependentWeeklyEvidenceLedgerDecision.Candidate {
        val outcome = ledger(p, sources = sources)
        assertTrue(outcome.toString(), outcome is IndependentWeeklyEvidenceLedgerDecision.Candidate)
        return outcome as IndependentWeeklyEvidenceLedgerDecision.Candidate
    }

    private fun deferred(ruleId: String,
                         p: PlanSnapshot = plan(),
                         sources: List<IndependentScopedRunningRecord> = listOf(recorded())) {
        val decision = ledger(p, sources = sources)
        assertTrue(decision.toString(), decision is IndependentWeeklyEvidenceLedgerDecision.Deferred)
        assertEquals(ruleId,
            (decision as IndependentWeeklyEvidenceLedgerDecision.Deferred).ruleId)
    }

    @Test fun partialPlanLinkedDistanceNeverClaimsCompleteWeek() {
        val result = candidate()
        val week = result.weeks.single()
        assertEquals(5.0, week.observedLinkedKmPartial, 0.00001)
        assertEquals(1, week.linkedWithKnownDistanceCount)
        assertEquals(1, week.deferredOrUnlinkedSessionCount)
        assertNull(week.verifiedCompleteWeekKm)
        assertFalse(week.sourceCompleteForAdaptation)
        assertFalse(result.completeWeeksVerified)
        assertFalse(result.authorizedForAdaptiveLoad)
    }

    @Test fun allLinkedRunsStillDoNotProveAllAthleteActivityCoverage() {
        val p = plan(sessions = listOf(
            run("session-1", monday.plusDays(1), "source-1"),
            run("session-2", monday.plusDays(3), "source-2")
        ))
        val r = candidate(p, listOf(
            recorded(), recorded("source-2", monday.plusDays(3), 7000.0)
        ))
        val week = r.weeks.single()
        assertEquals(12.0, week.observedLinkedKmPartial, 0.00001)
        assertEquals(2, week.linkedWithKnownDistanceCount)
        assertEquals(0, week.deferredOrUnlinkedSessionCount)
        assertNull(week.verifiedCompleteWeekKm)
        assertFalse(r.completeWeeksVerified)
    }

    @Test fun unknownRecordedDistanceStaysUnknownInsteadOfZero() {
        val result = candidate(sources = listOf(recorded(distanceM = null)))
        val week = result.weeks.single()
        assertEquals(0, week.linkedWithKnownDistanceCount)
        assertEquals(1, week.attributedButMissingDistanceCount)
        assertEquals(1, week.deferredOrUnlinkedSessionCount)
        assertEquals(0.0, week.observedLinkedKmPartial, 0.0)
        assertNull(week.verifiedCompleteWeekKm)
    }

    @Test fun explicitMissedStatusRemainsSeparateFromRecordedZeroKilometers() {
        val p = plan(sessions = listOf(
            run("session-1", monday.plusDays(1), status = "missed")
        ))
        val week = candidate(p, emptyList()).weeks.single()
        assertEquals(1, week.savedMissedOrSkippedCount)
        assertEquals(1, week.deferredOrUnlinkedSessionCount)
        assertNull(week.verifiedCompleteWeekKm)
    }

    @Test fun calendarWeeksAreGroupedBySessionDateNotGuessedActivityWeek() {
        val p = plan(sessions = listOf(
            run("session-1", monday.plusDays(1), "source-1"),
            run("session-2", monday.plusWeeks(1).plusDays(1), "source-2")
        ))
        val weeks = candidate(p, listOf(
            recorded(), recorded("source-2", monday.plusWeeks(1).plusDays(1), 6000.0)
        )).weeks
        assertEquals(listOf(monday, monday.plusWeeks(1)), weeks.map { it.monday })
        assertEquals(listOf(5.0, 6.0), weeks.map { it.observedLinkedKmPartial })
        assertTrue(weeks.all { it.verifiedCompleteWeekKm == null })
    }

    @Test fun ambiguousDuplicateSourceDoesNotContributeToPartialSum() {
        val sameEventDifferentSource = recorded("fit-copy").copy(
            activity = recorded("fit-copy").activity.copy(source = "fit")
        )
        val week = candidate(sources = listOf(recorded(), sameEventDifferentSource))
            .weeks.single()
        assertEquals(0.0, week.observedLinkedKmPartial, 0.0)
        assertEquals(2, week.deferredOrUnlinkedSessionCount)
        assertEquals(0, week.linkedWithKnownDistanceCount)
    }

    @Test fun contradictoryActivityOwnerScopeIsAnAuditFailure() {
        deferred("ACTIVITY_OWNER_SCOPE_CONFLICT",
            sources = listOf(recorded().copy(assertedOwnerId = "other")))
    }

    @Test fun foreignPlanScopeIsAnAuditFailure() {
        deferred("PLAN_OWNER_SCOPE_CONFLICT", p = plan(ownerId = "other"))
    }

    @Test fun ambiguousPlanIdIsRejectedBeforeGrouping() {
        val p = plan()
        val result = ledger(p, otherPlans = listOf(p.copy(createdAt = Instant.ofEpochSecond(1))))
        assertTrue(result is IndependentWeeklyEvidenceLedgerDecision.Deferred)
        assertEquals("PLAN_NOT_UNIQUE",
            (result as IndependentWeeklyEvidenceLedgerDecision.Deferred).ruleId)
    }

    @Test fun duplicateRunningSessionIdsCannotAccumulateDoubleDistance() {
        val session = run("session-1", monday.plusDays(1), "source-1")
        val p = plan(sessions = listOf(session, session))
        deferred("SESSION_NOT_UNIQUE", p = p)
    }

    @Test fun plansWithoutRunningSessionsReturnNoLedgerRatherThanZeroTraining() {
        val p = plan(sessions = listOf(
            run("session-1", monday.plusDays(1)).copy(discipline = "str")
        ))
        deferred("NO_RUNNING_SESSIONS", p = p)
    }

    @Test fun returnedLedgerIsStableAndDoesNotMutateSavedPlanOrSourceRecords() {
        val p = plan()
        val inputs = listOf(recorded())
        val a = ledger(p, sources = inputs)
        val b = ledger(p, sources = inputs)
        assertEquals(a, b)
        assertEquals("completed", p.sessions.first().status)
        assertEquals(5000.0, inputs.single().activity.distanceM!!, 0.00001)
        assertFalse((a as IndependentWeeklyEvidenceLedgerDecision.Candidate).authorizedForProduction)
    }
}
