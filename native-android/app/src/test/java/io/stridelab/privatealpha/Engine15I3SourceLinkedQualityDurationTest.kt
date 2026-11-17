package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** I3 test data is invented; source lap seconds are not intensity proof. */
class Engine15I3SourceLinkedQualityDurationTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val asOf = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.of("Asia/Manila")
    private val owner = "synthetic-owner"
    private val recentDay = asOf.minusDays(3)
    private val olderDay = asOf.minusDays(10)

    private fun source(returning: Boolean = false) = run {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = 8, days = (1..7).toSet(),
            runDays = 6, longDay = 7, qualityCount = 0,
            weeklyKm = 75.0, targetWeeklyKm = 80.0,
            longestKm = 20.0, returning = returning,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                8, 6, 80.0, returning, false
            )
        )
    }

    private fun preview(
        week: Int = 1,
        slot: Int = 1,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        observed: Int? = 480,
        reviewed: Boolean = true,
        available: Int = 3600
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week, slotNumber = slot, family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = observed,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = available,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "external-dose-review-synthetic"
    )

    private fun plan() = PlanSnapshot(
        id = "saved-plan", owner = owner, engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY, createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(
            PlannedSession(
                id = "session-older", date = olderDay, discipline = "run",
                title = "Previously recorded effort", minutes = 45,
                distanceKm = 7.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "record-older"
            ),
            PlannedSession(
                id = "session-recent", date = recentDay, discipline = "run",
                title = "Recently recorded effort", minutes = 45,
                distanceKm = 7.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "record-recent"
            )
        ),
        explanations = emptyList(), status = "approved"
    )

    private fun activity(id: String, day: LocalDate, source: String) =
        ActivityRecord(
            id = id, title = "Recorded run", type = "running", source = source,
            startedAt = day.atTime(7, 0).atZone(zone).toInstant(),
            distanceM = 7000.0, durationS = 2400.0, movingTimeS = null,
            recordedPace = null, averageHr = null, elevationM = null
        )

    private fun detail(id: String) = ActivityDetail(
        activityId = id, samples = emptyList(), fetchedAt = 0L,
        laps = listOf(
            ActivityLap(0, 1000.0, 240.0, null, null, 300.0),
            ActivityLap(1, 300.0, 120.0, null, null, 600.0),
            ActivityLap(2, 1000.0, 240.0, null, null, 900.0)
        )
    )

    private fun observation(
        id: String,
        session: String,
        date: LocalDate,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD
    ): Engine15I3LinkedQualityObservation = Engine15I3LinkedQualityObservation(
        lapClaim = IndependentQualityLapClaim(
            evidence = IndependentQualitySegmentEvidence(
                ownerId = owner, sourceRecordId = id, sessionId = session,
                performedOn = date, family = family,
                origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                sourceSessionMatchAsserted = true,
                performedWorkSegmentsSeconds = listOf(240, 240)
            ),
            recordedWorkLapIndices = listOf(0, 2)
        ),
        detail = detail(id)
    )

    private fun observations() = listOf(
        observation("record-recent", "session-recent", recentDay),
        observation("record-older", "session-older", olderDay)
    )

    private fun input(
        records: List<Engine15I3LinkedQualityObservation> = observations(),
        activities: List<ActivityRecord> = listOf(
            activity("record-recent", recentDay, "garmin"),
            activity("record-older", olderDay, "fit")
        ),
        plans: List<PlanSnapshot> = listOf(plan()),
        assertedOwner: String = owner
    ) = Engine15I3QualityDurationInput(
        assertedOwnerId = assertedOwner,
        asOf = asOf,
        zone = zone,
        plans = plans,
        sourceActivities = activities,
        observations = records
    )

    private fun accepted(
        request: Engine15M5QualityPreviewRequest = preview(),
        evidence: Engine15I3QualityDurationInput = input()
    ): Engine15I3QualityDurationDecision.DurationConsistent {
        val result = Engine15I3SourceLinkedQualityDuration.inspect(
            source(), request, evidence
        )
        assertTrue("Expected a source-duration candidate, got $result",
            result is Engine15I3QualityDurationDecision.DurationConsistent)
        return result as Engine15I3QualityDurationDecision.DurationConsistent
    }

    private fun deferred(
        code: String,
        request: Engine15M5QualityPreviewRequest = preview(),
        evidence: Engine15I3QualityDurationInput = input(),
        baseline: Engine15IndependentVolumePlanner.Result = source()
    ) {
        val result = Engine15I3SourceLinkedQualityDuration.inspect(
            baseline, request, evidence
        )
        assertTrue("Expected $code, got $result",
            result is Engine15I3QualityDurationDecision.Deferred)
        assertEquals(code,
            (result as Engine15I3QualityDurationDecision.Deferred).reasonCode)
    }

    @Test fun twoLinkedSourcesReconcileDurationsButNeverApproveQuality() {
        val result = accepted()
        assertEquals(480, result.repeatedWorkDurationFloorSeconds)
        assertEquals(480, result.requestedWorkExposureSeconds)
        assertEquals(listOf("record-recent", "record-older"),
            result.lapBindings.map { it.activityId })
        assertEquals(listOf(480.0,480.0),
            result.lapBindings.map { it.recordedWorkSeconds })
        assertTrue(result.lapDurationsReconciled)
        assertFalse(result.ownerSourceNamespaceAuthenticated)
        assertFalse(result.qualityIntensityVerified)
        assertFalse(result.athleteResponseVerified)
        assertFalse(result.doseAuthorized)
        assertFalse(result.distanceReconciled)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.originalM5B.executable)
        assertFalse(result.originalM5B.authorizedForProduction)
        assertEquals(
            Engine15M5QualityPreviewComposer.compose(source(), listOf(preview())),
            result.originalM5B
        )
        assertTrue(result.lapBindings.all {
            !it.qualityClassificationVerified && !it.authorizedForTemplateSelection
        })
    }

    @Test fun missingExtraDuplicateAndSameDaySourcesCannotCountAsTwo() {
        deferred("EXACTLY_TWO_RECORDINGS_REQUIRED",
            evidence=input(records=observations().take(1)))
        deferred("EXACTLY_TWO_RECORDINGS_REQUIRED",
            evidence=input(records=observations() + observations().first()))
        deferred("DUPLICATE_QUALITY_SOURCE",
            evidence=input(records=listOf(observations()[0],
                observations()[1].copy(lapClaim=observations()[1].lapClaim.copy(
                    evidence=observations()[1].lapClaim.evidence.copy(
                        sourceRecordId="record-recent"))))))
        deferred("DUPLICATE_QUALITY_PLAN_SESSION",
            evidence=input(records=listOf(observations()[0],
                observations()[1].copy(lapClaim=observations()[1].lapClaim.copy(
                    evidence=observations()[1].lapClaim.evidence.copy(
                        sessionId="session-recent"))))))
        deferred("DUPLICATE_QUALITY_PERFORMED_DAY",
            evidence=input(records=listOf(observations()[0],
                observations()[1].copy(lapClaim=observations()[1].lapClaim.copy(
                    evidence=observations()[1].lapClaim.evidence.copy(
                        performedOn=recentDay))))))
    }

    @Test fun wrongOwnerAndQualityFamilyFailBeforeSourceLookup() {
        deferred("QUALITY_OWNER_SCOPE_CONFLICT",
            evidence=input(assertedOwner="different-owner"))
        val wrong = observations()[0].copy(
            lapClaim=observations()[0].lapClaim.copy(
                evidence=observations()[0].lapClaim.evidence.copy(
                    family=IndependentQualityFamily.TEMPO)))
        deferred("QUALITY_FAMILY_CLAIM_MISMATCH",
            evidence=input(records=listOf(wrong,observations()[1])))
    }

    @Test fun linkedSourceMustHaveRecordedLapDetailsAndMatchingDurations() {
        deferred("SOURCE_SOURCE_DETAIL_UNAVAILABLE",
            evidence=input(records=listOf(observations()[0].copy(detail=null),
                observations()[1])))
        val malformed=observations()[0].copy(
            lapClaim=observations()[0].lapClaim.copy(
                evidence=observations()[0].lapClaim.evidence.copy(
                    performedWorkSegmentsSeconds=listOf(240,300))))
        deferred("SOURCE_RECORDED_WORK_DURATION_MISMATCH",
            evidence=input(records=listOf(malformed,observations()[1])))
        val paused=observations()[0].copy(detail=detail("record-recent").copy(
            pauseIntervals=listOf(ActivityPauseInterval(305.0,320.0))))
        deferred("SOURCE_SOURCE_WORK_OVERLAPS_PAUSE",
            evidence=input(records=listOf(paused,observations()[1])))
    }

    @Test fun claimedWorkCannotExceedRepeatedRecordedWorkSeconds() {
        deferred("REQUESTED_WORK_EXCEEDS_SOURCE_LAPS",
            request=preview(observed=540))
        val acceptedConservative=accepted(request=preview(observed=420))
        assertEquals(480,acceptedConservative.repeatedWorkDurationFloorSeconds)
        assertEquals(420,acceptedConservative.requestedWorkExposureSeconds)
    }

    @Test fun unreviewedUnknownOrInfeasibleQualityIsHeldUpstream() {
        deferred("UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            request=preview(reviewed=false))
        deferred("UPSTREAM_QUALITY_QUALITY_EXPOSURE_UNKNOWN",
            request=preview(observed=null))
        deferred("UPSTREAM_QUALITY_DOSE_OR_TIME_CONFLICT",
            request=preview(available=300))
        deferred("UPSTREAM_QUALITY_RECOVERY_WEEK_HOLD",
            request=preview(week=4))
        deferred("UPSTREAM_QUALITY_ON_LONG_SLOT",
            request=preview(slot=6))
        deferred("UPSTREAM_RETURNING_RUNNER_QUALITY_HOLD",
            baseline=source(returning=true))
    }

    @Test fun mismatchedActivityOwnerAndStaleOrFutureSessionsCannotPassBinding() {
        deferred("SOURCE_PLAN_OWNER_SCOPE_CONFLICT",
            evidence=input(plans=listOf(plan().copy(owner="different-owner"))))
        val future=observations()[0].copy(
            lapClaim=observations()[0].lapClaim.copy(
                evidence=observations()[0].lapClaim.evidence.copy(
                    performedOn=asOf.plusDays(1))))
        deferred("SOURCE_OUTSIDE_RECENT_WINDOW",
            evidence=input(records=listOf(future, observations()[1])))
        val missing= input(activities=listOf(activity("record-older", olderDay, "fit")))
        deferred("SOURCE_SOURCE_RECORD_NOT_UNIQUE",evidence=missing)
    }

    @Test fun distinctKnownSourcesCannotBeReplacedByManuallyReportedActivity() {
        val manual = input(activities=listOf(
            activity("record-recent",recentDay,"manual"),
            activity("record-older",olderDay,"fit")))
        deferred("SOURCE_UNSUPPORTED_SOURCE",evidence=manual)
        val selfReport=observations()[0].copy(
            lapClaim=observations()[0].lapClaim.copy(
                evidence=observations()[0].lapClaim.evidence.copy(
                    origin=IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS)))
        deferred("SOURCE_UNSUPPORTED_SEGMENT_ORIGIN",
            evidence=input(records=listOf(selfReport,observations()[1])))
    }

    @Test fun repeatedEvaluationIsDeterministicWithIdenticalPlanAndLaps() {
        assertEquals(accepted(), accepted())
    }
}
