package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Synthetic work-lap HR signal review only: NEVER threshold intensity approval. */
class Engine15I4WorkLapHeartRateReviewTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val asOf = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.of("Asia/Manila")
    private val owner = "synthetic-owner"
    private val recentDay = asOf.minusDays(3)
    private val olderDay = asOf.minusDays(10)

    private fun source(): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = 8, days = (1..7).toSet(),
            runDays = 6, longDay = 7, qualityCount = 0,
            weeklyKm = 75.0, targetWeeklyKm = 80.0,
            longestKm = 20.0, returning = false,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(8, 6, 80.0, false, false)
        )
    }

    private fun request(
        confirmedSeconds: Int? = 480,
        reviewed: Boolean = true
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = 1, slotNumber = 1,
        family = IndependentQualityFamily.THRESHOLD,
        fitnessInput = IndependentQualityFitnessInput(
            confirmedRecentWorkSeconds = confirmedSeconds,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-reviewed-dose"
    )

    private fun plan() = PlanSnapshot(
        id = "saved-plan", owner = owner, engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY, createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(
            PlannedSession(
                id = "session-older", date = olderDay, discipline = "run",
                title = "Synthetic earlier workout", minutes = 45,
                distanceKm = 7.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "record-older"
            ),
            PlannedSession(
                id = "session-recent", date = recentDay, discipline = "run",
                title = "Synthetic recent workout", minutes = 45,
                distanceKm = 7.0, kind = "quality", quality = true,
                status = "completed", linkedActivityId = "record-recent"
            )
        ),
        explanations = emptyList(), status = "approved"
    )

    private fun activity(id: String, day: LocalDate, provider: String) =
        ActivityRecord(
            id = id, title = "Synthetic recorded running", type = "running",
            source = provider,
            startedAt = day.atTime(7, 0).atZone(zone).toInstant(),
            distanceM = 7000.0, durationS = 2400.0, movingTimeS = null,
            recordedPace = null, averageHr = null, elevationM = null
        )

    private fun sample(seconds: Double, index: Int, bpm: Double? = 150.0) =
        ActivitySample(index, seconds, null, bpm, null, null, null)

    private fun detail(id: String): ActivityDetail {
        val times = (0..24).map { 300.0 + it * 10.0 } +
            (0..24).map { 900.0 + it * 10.0 }
        return ActivityDetail(
            activityId = id,
            samples = times.mapIndexed { index, seconds -> sample(seconds, index) },
            fetchedAt = 0L,
            laps = listOf(
                ActivityLap(0, 1000.0, 240.0, null, 150.0, 300.0),
                ActivityLap(1, 300.0, 120.0, null, null, 600.0),
                ActivityLap(2, 1000.0, 240.0, null, 150.0, 900.0)
            )
        )
    }

    private fun observation(
        id: String,
        session: String,
        day: LocalDate
    ) = Engine15I3LinkedQualityObservation(
        lapClaim = IndependentQualityLapClaim(
            evidence = IndependentQualitySegmentEvidence(
                ownerId = owner, sourceRecordId = id,
                sessionId = session, performedOn = day,
                family = IndependentQualityFamily.THRESHOLD,
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

    private fun evidence(
        observations: List<Engine15I3LinkedQualityObservation> = observations(),
        plans: List<PlanSnapshot> = listOf(plan())
    ) = Engine15I3QualityDurationInput(
        assertedOwnerId = owner, asOf = asOf, zone = zone, plans = plans,
        sourceActivities = listOf(
            activity("record-recent", recentDay, "garmin"),
            activity("record-older", olderDay, "fit")
        ),
        observations = observations
    )

    private fun accepted(
        request: Engine15M5QualityPreviewRequest = request(),
        input: Engine15I3QualityDurationInput = evidence()
    ): Engine15I4HrSignalDecision.SignalReview {
        val got = Engine15I4WorkLapHeartRateReview.inspect(source(), request, input)
        assertTrue("Expected I4 descriptive signal candidate; got $got",
            got is Engine15I4HrSignalDecision.SignalReview)
        return got as Engine15I4HrSignalDecision.SignalReview
    }

    private fun deferred(
        reason: String,
        request: Engine15M5QualityPreviewRequest = request(),
        input: Engine15I3QualityDurationInput = evidence()
    ) {
        val got = Engine15I4WorkLapHeartRateReview.inspect(source(), request, input)
        assertTrue("Expected $reason; got $got",
            got is Engine15I4HrSignalDecision.Deferred)
        assertEquals(reason,
            (got as Engine15I4HrSignalDecision.Deferred).reasonCode)
    }

    @Test fun fullSelectedLapCoverageIsDescriptiveAndCannotCertifyThreshold() {
        val input = evidence()
        val original = Engine15I3SourceLinkedQualityDuration.inspect(
            source(), request(), input
        )
        val result = accepted(input = input)
        assertEquals(original, result.originalI3)
        assertEquals(listOf("record-recent", "record-older"),
            result.recordings.map { it.activityId })
        assertEquals(2, result.recordings.size)
        assertEquals(4, result.recordings.sumOf { it.selectedWorkLaps.size })
        for (record in result.recordings) {
            assertEquals(480.0, record.selectedLapSeconds, 0.0)
            assertEquals(480.0, record.coveredSeconds, 0.0)
            assertEquals(1.0, record.coverageFraction, 0.0)
            assertEquals(150.0, record.timeWeightedMeanBpm, 0.0)
            for (lap in record.selectedWorkLaps) {
                assertEquals(240.0, lap.coveredSeconds, 0.0)
                assertEquals(1.0, lap.coverageFraction, 0.0)
                assertEquals(150.0, lap.timeWeightedMeanBpm, 0.0)
                assertTrue(lap.sourceLapMeanCrossChecked)
            }
        }
        assertTrue(result.hrStreamReviewOnly)
        assertFalse(result.sourceOwnerAuthenticated)
        assertFalse(result.sensorCalibrationVerified)
        assertFalse(result.thresholdOrTempoIntensityVerified)
        assertFalse(result.athleteResponseVerified)
        assertFalse(result.doseAuthorized)
        assertFalse(result.distanceReconciled)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertFalse(result.originalI3.qualityIntensityVerified)
    }

    @Test fun missingWorkLapHeartRateMustNotBeFilledFromOtherLaps() {
        val records = observations().mapIndexed { i, record ->
            if (i == 0) record.copy(detail = requireNotNull(record.detail).copy(
                samples = requireNotNull(record.detail).samples.map { sample ->
                    if (sample.seconds in 900.0..1140.0)
                        sample.copy(hr = null) else sample
                }
            )) else record
        }
        deferred("INSUFFICIENT_WORK_LAP_HR_COVERAGE",
            input = evidence(observations = records))
    }

    @Test fun sparseSamplesWithSixtySecondGapsDoNotProveCoverage() {
        val records = observations().mapIndexed { i, record ->
            if (i == 0) record.copy(detail = requireNotNull(record.detail).copy(
                samples = requireNotNull(record.detail).samples.filter {
                    it.seconds % 60.0 == 0.0
                }
            )) else record
        }
        deferred("INSUFFICIENT_WORK_LAP_HR_COVERAGE",
            input = evidence(observations = records))
    }

    @Test fun nonmonotonicAndDuplicateSourceSamplesFailBeforeClassification() {
        val initial = observations()
        fun altered(change: (ActivitySample, Int) -> ActivitySample) =
            evidence(observations = listOf(initial[0].copy(
                detail = requireNotNull(initial[0].detail).copy(
                    samples = requireNotNull(initial[0].detail).samples
                        .mapIndexed { i, value -> change(value, i) }
                )
            ), initial[1]))
        deferred("SOURCE_SAMPLE_ORDER_INVALID",
            input = altered { value, i ->
                if (i == 5) value.copy(seconds = 300.0) else value
            })
        deferred("SOURCE_SAMPLE_ORDER_INVALID",
            input = altered { value, i ->
                if (i == 5) value.copy(index = 4) else value
            })
    }

    @Test fun impossibleOrNonfiniteHeartRatesAreRejected() {
        val original = observations()
        fun bad(bpm: Double) = evidence(observations = listOf(
            original[0].copy(detail = requireNotNull(original[0].detail).copy(
                samples = requireNotNull(original[0].detail).samples.mapIndexed {
                    i, v -> if (i == 4) v.copy(hr = bpm) else v
                }
            )), original[1]
        ))
        deferred("INVALID_WORK_LAP_HEART_RATE", input = bad(280.0))
        deferred("INVALID_WORK_LAP_HEART_RATE", input = bad(Double.NaN))
    }

    @Test fun contradictoryRecordedLapAverageCannotBeSilentlyIgnored() {
        val original = observations()
        val wrong = original[0].copy(
            detail = requireNotNull(original[0].detail).copy(
                laps = requireNotNull(original[0].detail).laps.map {
                    if (it.index == 0) it.copy(hr = 110.0) else it
                }
            )
        )
        deferred("SOURCE_LAP_AVG_HR_CONFLICT",
            input = evidence(observations = listOf(wrong, original[1])))
        val unavailable = original[0].copy(
            detail = requireNotNull(original[0].detail).copy(
                laps = requireNotNull(original[0].detail).laps.map {
                    if (it.index == 0) it.copy(hr = null) else it
                }
            )
        )
        val accepted = accepted(input = evidence(
            observations = listOf(unavailable, original[1])
        ))
        assertFalse(accepted.recordings.first().selectedWorkLaps.first()
            .sourceLapMeanCrossChecked)
        assertFalse(accepted.thresholdOrTempoIntensityVerified)
    }

    @Test fun limitedMissingEndpointsCanPassButCannotVerifyIntensity() {
        val original = observations()
        val partiallyRecorded = original[0].copy(
            detail = requireNotNull(original[0].detail).copy(
                samples = requireNotNull(original[0].detail).samples.filterNot {
                    it.seconds in 300.0..330.0
                }
            )
        )
        val result = accepted(input = evidence(
            observations = listOf(partiallyRecorded, original[1])
        ))
        assertEquals(200.0 / 240.0,
            result.recordings.first().selectedWorkLaps.first().coverageFraction,
            1e-8)
        assertFalse(result.thresholdOrTempoIntensityVerified)
        assertFalse(result.executable)
    }

    @Test fun sourceDetailsAndPauseConflictsStillDeferThroughI3() {
        val original = observations()
        val missing = original[0].copy(detail = null)
        deferred("UPSTREAM_SOURCE_SOURCE_DETAIL_UNAVAILABLE",
            input = evidence(observations = listOf(missing, original[1])))
        val paused = original[0].copy(detail = requireNotNull(original[0].detail)
            .copy(pauseIntervals = listOf(ActivityPauseInterval(305.0, 315.0))))
        deferred("UPSTREAM_SOURCE_SOURCE_WORK_OVERLAPS_PAUSE",
            input = evidence(observations = listOf(paused, original[1])))
    }

    @Test fun unreviewedOrInflatedDoseAndDuplicateSourcesStillFailAtI3() {
        deferred("UPSTREAM_UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            request = request(reviewed = false))
        deferred("UPSTREAM_REQUESTED_WORK_EXCEEDS_SOURCE_LAPS",
            request = request(confirmedSeconds = 540))
        val original = observations()
        val duplicated = original[1].copy(
            lapClaim = original[1].lapClaim.copy(
                evidence = original[1].lapClaim.evidence.copy(
                    sourceRecordId = "record-recent"
                )
            )
        )
        deferred("UPSTREAM_DUPLICATE_QUALITY_SOURCE",
            input = evidence(observations = listOf(original[0], duplicated)))
    }

    @Test fun identicalInputReturnsSameDescriptiveSourceSignalSummary() {
        assertEquals(accepted(), accepted())
        assertTrue(accepted().ruleIds.contains(
            "HR_SIGNAL_ONLY_NOT_THRESHOLD_OR_TEMPO_CLASSIFICATION"))
    }
}
