package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15AthleteEffortBaselineTest {
    private val owner = "effort-baseline-runner"
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")

    private fun run(
        id: String,
        daysAgo: Long,
        distanceM: Double = 8_000.0,
        pace: Double? = 330.0,
        hr: Double? = 144.0
    ) = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = asOf.minusSeconds(daysAgo * 86_400L),
        distanceM = distanceM,
        durationS = pace?.let { it * (distanceM / 1000.0) } ?: 2_600.0,
        movingTimeS = pace?.let { it * (distanceM / 1000.0) } ?: 2_600.0,
        recordedPace = pace,
        averageHr = hr,
        elevationM = 20.0
    )

    @Test fun emptyHistoryReportsExplicitMissingDataWithoutFabricatingMetrics() {
        val baseline = Engine15AthleteEffortBaselineBuilder.build(
            owner = owner,
            activities = emptyList(),
            allowedActivityIds = emptySet(),
            detailsByActivityId = emptyMap(),
            plans = emptyList(),
            userLabels = emptyMap(),
            asOf = asOf
        )
        assertEquals(Engine15HistoryCoverage.NO_RECORDED_RUNS, baseline.historyCoverage)
        assertEquals(0, baseline.recordedRunCount28d)
        assertNull(baseline.recordedMeanWeeklyKm28d)
        assertNull(baseline.longestRunKm28d)
        assertEquals(Engine15BaselineMetricStatus.MISSING_DATA, baseline.easyEffort.status)
        assertNull(baseline.easyEffort.medianPaceSecondsPerKm)
        assertNull(baseline.easyEffort.medianAverageHr)
        assertNull(baseline.sourceHeartRateZones)
        assertFalse(baseline.sourceEvidenceVerified)
        assertTrue(baseline.missingDataNotes.any { it.contains("never treated as confirmed inactivity") })
        assertTrue(baseline.familyExposures.values.all {
            it.confidence == IndependentQualityExposureConfidence.UNAVAILABLE &&
                it.repeatedWorkSeconds == null
        })
    }

    @Test fun confirmedEasyRunsAndLinkedQualityWorkoutsBuildAuditableEffortAndExposureBaseline() {
        val easy1 = run("easy-1", daysAgo = 12, distanceM = 8_000.0, pace = 340.0, hr = 140.0)
        val easy2 = run("easy-2", daysAgo = 5, distanceM = 10_000.0, pace = 330.0, hr = 144.0)
        val thr1 = run("thr-1", daysAgo = 10, distanceM = 9_000.0, pace = 290.0, hr = 166.0)
        val thr2 = run("thr-2", daysAgo = 3, distanceM = 9_000.0, pace = 288.0, hr = 167.0)
        val unconfirmedInterval = run("int-heur", daysAgo = 2, distanceM = 7_000.0, pace = 300.0, hr = 162.0)

        val intervalDetail = ActivityDetail(
            activityId = "int-heur",
            samples = emptyList(),
            laps = listOf(
                ActivityLap(0, 1500.0, 600.0, 340.0, 140.0),
                ActivityLap(1, 500.0, 120.0, 240.0, 172.0),
                ActivityLap(2, 330.0, 120.0, 360.0, 145.0),
                ActivityLap(3, 500.0, 120.0, 242.0, 174.0),
                ActivityLap(4, 1400.0, 480.0, 345.0, 142.0)
            ),
            fetchedAt = 1L,
            sourceHeartRateZones = ActivitySourceHeartRateZones(190, 172, 52, "LTHR", listOf(130, 148, 162, 174, 190))
        )

        val prescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = emptyList(),
            mixedSegments = listOf(
                RunningStepV2("WARM_UP", "Easy", seconds = 600, distanceEquivalentMeters = 1500),
                RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1100),
                RunningStepV2("RECOVERY", "Easy", seconds = 120, distanceEquivalentMeters = 300),
                RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1100),
                RunningStepV2("COOL_DOWN", "Easy", seconds = 480, distanceEquivalentMeters = 1200)
            ),
            reconciledDistanceMeters = 5200
        )
        val plan = PlanSnapshot(
            id = "p-1",
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
            createdAt = asOf,
            request = PlanRequest(start = LocalDate.parse("2026-11-02"), weeks = 4),
            sessions = listOf(
                PlannedSession("s-1", LocalDate.parse("2026-11-10"), "run", "Threshold", 30, 5.2, "quality", quality = true, status = "completed", linkedActivityId = "thr-1", runningPrescription = prescription),
                PlannedSession("s-2", LocalDate.parse("2026-11-17"), "run", "Threshold", 30, 5.2, "quality", quality = true, status = "completed", linkedActivityId = "thr-2", runningPrescription = prescription)
            ),
            explanations = listOf("Plan"),
            status = "approved"
        )

        val activities = listOf(easy1, easy2, thr1, thr2, unconfirmedInterval)
        val baseline = Engine15AthleteEffortBaselineBuilder.build(
            owner = owner,
            activities = activities,
            allowedActivityIds = activities.map { it.id }.toSet(),
            detailsByActivityId = mapOf("int-heur" to intervalDetail),
            plans = listOf(plan),
            userLabels = mapOf(
                "easy-1" to UserActivityWorkoutLabel(ActivityWorkoutKind.EASY_RUN),
                "easy-2" to UserActivityWorkoutLabel(ActivityWorkoutKind.EASY_RUN)
            ),
            asOf = asOf
        )

        assertEquals(Engine15HistoryCoverage.UNKNOWN_PARTIAL_CACHE, baseline.historyCoverage)
        assertEquals(5, baseline.recordedRunCount28d)
        assertEquals(10.0, baseline.longestRunKm28d!!, 1e-6)
        assertEquals(43.0 / 4.0, baseline.recordedMeanWeeklyKm28d!!, 1e-6)
        assertEquals(Engine15BaselineMetricStatus.AVAILABLE_REPEATED, baseline.easyEffort.status)
        assertEquals(335.0, baseline.easyEffort.medianPaceSecondsPerKm!!, 1e-6)
        assertEquals(142.0, baseline.easyEffort.medianAverageHr!!, 1e-6)
        assertEquals(172, baseline.sourceHeartRateZones?.thresholdHr)

        val thrExposure = baseline.familyExposures.getValue(IndependentQualityFamily.THRESHOLD)
        assertEquals(IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED, thrExposure.confidence)
        assertEquals(600, thrExposure.repeatedWorkSeconds)
        assertEquals(2, thrExposure.confirmedSessionCount)

        // Unconfirmed interval candidate is counted transparently but NOT promoted into exposure!
        val intExposure = baseline.familyExposures.getValue(IndependentQualityFamily.INTERVALS)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, intExposure.confidence)
        assertEquals(0, intExposure.confirmedSessionCount)
        assertEquals(1, intExposure.unconfirmedHeuristicCandidateCount)
        assertTrue(baseline.missingDataNotes.any { it.contains("1 unconfirmed lap-structure quality candidate") })
    }
}
