package io.stridelab.privatealpha

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class Engine15PaceZoneEstimatorTest {
    private val now = Instant.parse("2026-11-21T06:00:00Z")

    private fun run(id: String, daysAgo: Long, distM: Double, paceSPerKm: Double) = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = now.minus(daysAgo, ChronoUnit.DAYS),
        distanceM = distM,
        durationS = (distM / 1000.0) * paceSPerKm,
        movingTimeS = (distM / 1000.0) * paceSPerKm,
        recordedPace = paceSPerKm,
        averageHr = null,
        elevationM = null
    )

    private val baseRequest = PlanRequest(
        goal = "10K",
        weeks = 8,
        runDays = 4,
        start = LocalDate.of(2026, 11, 23),
        longDay = 7
    )

    @Test fun recentRunsDrivePaceZonesAndIgnoreStaleMonthsOldRuns() {
        val activities = listOf(
            // Stale 120-day old fast runs that should be ignored
            run("stale-1", 120, 10_000.0, 240.0),
            run("stale-2", 110, 10_000.0, 245.0),
            run("stale-3", 100, 10_000.0, 250.0),
            // Recent runs around 6:30–6:45/km easy
            run("rec-1", 2, 8_000.0, 405.0),
            run("rec-2", 5, 10_000.0, 395.0),
            run("rec-3", 10, 6_000.0, 390.0),
            run("rec-4", 18, 12_000.0, 400.0)
        )
        val zones = Engine15PaceZoneEstimator.estimate(baseRequest, activities = activities)
        assertTrue(
            "Expected Easy pace zone around recent ~395-405s/km, got ${zones.easy.midpointSecondsPerKm}",
            zones.easy.midpointSecondsPerKm in 375.0..425.0
        )
    }

    @Test fun outlierSpikeInRecentRunsDoesNotSkewThresholdOrIntervals() {
        val activities = listOf(
            // Single anomalous GPS glitch fast run + 4 realistic runs
            run("glitch", 12, 4_200.0, 195.0),
            run("s1", 3, 8_000.0, 395.0),
            run("s2", 6, 6_000.0, 340.0),
            run("s3", 9, 10_000.0, 400.0),
            run("s4", 15, 7_000.0, 345.0)
        )
        val zones = Engine15PaceZoneEstimator.estimate(baseRequest, activities = activities)
        assertTrue(
            "Expected outlier resistance keeping threshold > 275s/km (not pulled down by glitch), got ${zones.threshold.midpointSecondsPerKm}",
            zones.threshold.midpointSecondsPerKm > 275.0
        )
    }
}
