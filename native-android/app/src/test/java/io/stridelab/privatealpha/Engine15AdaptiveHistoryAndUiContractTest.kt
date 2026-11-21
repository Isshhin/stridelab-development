package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15AdaptiveHistoryAndUiContractTest {
    private val now = Instant.parse("2026-11-20T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")

    private fun run(id: String, daysAgo: Long, km: Double, paceSecPerKm: Double): ActivityRecord {
        val distM = km * 1000.0
        val durS = km * paceSecPerKm
        return ActivityRecord(
            id = id,
            title = "Run $id",
            type = "running",
            source = "garmin",
            startedAt = now.minusSeconds(daysAgo * 86_400L),
            distanceM = distM,
            durationS = durS,
            movingTimeS = durS,
            recordedPace = paceSecPerKm,
            averageHr = 152.0,
            elevationM = 40.0
        )
    }

    @Test fun automaticRunningExperienceClassifiesBeginnerIntermediateAndAdvancedFromHistory() {
        assertNull(automaticRunningExperience(emptyList(), now = now, zone = ZoneOffset.UTC))

        val beginnerRuns = listOf(
            run("b1", 2, 4.0, 390.0),
            run("b2", 5, 4.5, 385.0)
        )
        assertEquals(0, automaticRunningExperience(beginnerRuns, now = now, zone = ZoneOffset.UTC))

        val intermediateRuns = listOf(
            run("i1", 2, 8.0, 325.0),
            run("i2", 4, 12.0, 330.0),
            run("i3", 6, 7.0, 320.0),
            run("i4", 9, 9.0, 325.0),
            run("i5", 11, 11.0, 330.0),
            run("i6", 13, 7.0, 320.0)
        )
        assertEquals(1, automaticRunningExperience(intermediateRuns, now = now, zone = ZoneOffset.UTC))

        val advancedRuns = listOf(
            run("a1", 2, 12.0, 285.0),
            run("a2", 4, 14.0, 260.0),
            run("a3", 6, 22.0, 295.0),
            run("a4", 8, 12.0, 285.0),
            run("a5", 10, 15.0, 265.0),
            run("a6", 12, 24.0, 295.0)
        )
        assertEquals(2, automaticRunningExperience(advancedRuns, now = now, zone = ZoneOffset.UTC))
    }

    @Test fun paceZonesAdaptAccuratelyFromHistoricalRunsInsteadOfDefaultSixMinPace() {
        val fastHistory = listOf(
            run("f1", 3, 10.0, 255.0), // 4:15/km sustained fast run
            run("f2", 5, 12.0, 300.0), // 5:00/km easy run
            run("f3", 7, 14.0, 305.0), // 5:05/km easy run
            run("f4", 10, 10.0, 298.0)
        )
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 2,
            start = monday,
            weeks = 8,
            days = setOf(1, 2, 3, 5, 6, 7),
            runDays = 6,
            longDay = 6,
            weeklyKm = 60.0,
            longestKm = 18.0,
            planningPace = 360.0, // stale 6:00/km default
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val zones = Engine15PaceZoneEstimator.estimate(req, activities = fastHistory)
        assertEquals(Engine15PaceBandBasis.CONFIRMED_EASY_BASELINE_ESTIMATE, zones.basis)
        // Easy midpoint should be around ~5:00/km (285-320s), NOT 6:00/km (360s)
        assertTrue("Expected easy pace < 325 s/km, got ${zones.easy.midpointSecondsPerKm}",
            zones.easy.midpointSecondsPerKm < 325.0)
        // Threshold midpoint should be around ~4:10-4:25/km (240-268s)
        assertTrue("Expected threshold pace < 270 s/km, got ${zones.threshold.midpointSecondsPerKm}",
            zones.threshold.midpointSecondsPerKm < 270.0)
    }

    @Test fun adaptiveFiftyFiftyQualityAnchorPreventsJunkTenMinuteWorkoutsForHighVolumeRunners() {
        val history = listOf(
            run("h1", 2, 12.0, 260.0), // ~52 min fast tempo run -> ~34 min detected quality work
            run("h2", 4, 10.0, 310.0),
            run("h3", 6, 18.0, 315.0),
            run("h4", 9, 12.0, 265.0),
            run("h5", 11, 14.0, 312.0)
        )
        val req = PlanRequest(
            goal = "Aerobic foundation",
            experience = 2,
            start = monday,
            weeks = 8,
            days = setOf(1, 2, 3, 5, 6, 7),
            runDays = 6,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 65.0,
            longestKm = 18.0,
            targetWeeklyKm = 70.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val tempoAnchor = Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
            request = req,
            family = IndependentQualityFamily.TEMPO,
            activities = history
        )
        // For a 65 km/wk runner with fast history, Tempo work anchor must be >= 24 min (1440s), never 10 min (600s)
        assertTrue("Expected Tempo anchor >= 1440s, got $tempoAnchor", tempoAnchor >= 1440)
    }

    @Test fun runningStepDisplayKmLabelAlwaysFormatsInKilometers() {
        assertEquals("0.4 km", runningStepDisplayKmLabel(400))
        assertEquals("0.45 km", runningStepDisplayKmLabel(450))
        assertEquals("1.3 km", runningStepDisplayKmLabel(1300))
        assertEquals("1.8 km", runningStepDisplayKmLabel(1780))
    }
}
