package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class RunningAerobicEfficiencyTest {
    private val now = Instant.parse("2026-11-21T06:00:00Z")

    private fun run(id: String, daysAgo: Long, distKm: Double, paceSec: Double, hr: Double) = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = now.minus(daysAgo, ChronoUnit.DAYS),
        distanceM = distKm * 1000.0,
        durationS = distKm * paceSec,
        movingTimeS = distKm * paceSec,
        recordedPace = paceSec,
        averageHr = hr,
        elevationM = 15.0
    )

    @Test fun fasterEasyPaceAtSameHrShowsPositiveEfficiencyGain() {
        val runs = listOf(
            // Earlier weeks: ~6:50/km (410s) @ 146 bpm
            run("e1", 49, 8.0, 410.0, 146.0),
            run("e2", 42, 8.0, 408.0, 146.0),
            // Recent weeks: ~6:30/km (390s) @ 146 bpm
            run("l1", 14, 8.0, 392.0, 146.0),
            run("l2", 7, 8.0, 388.0, 146.0)
        )
        val summary = RunningAerobicEfficiency.analyze(runs, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertEquals(4, summary.totalQualifyingRuns)
        assertTrue("Expected positive efficiency gain, got ${summary.percentChange}", (summary.percentChange ?: 0.0) > 3.5)
    }

    @Test fun samePaceAtLowerHrShowsPositiveEfficiencyGain() {
        val runs = listOf(
            run("e1", 49, 7.0, 390.0, 152.0),
            run("e2", 42, 7.0, 390.0, 150.0),
            run("l1", 14, 7.0, 390.0, 142.0),
            run("l2", 7, 7.0, 390.0, 140.0)
        )
        val summary = RunningAerobicEfficiency.analyze(runs, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertTrue("Expected positive efficiency gain from lower HR, got ${summary.percentChange}", (summary.percentChange ?: 0.0) > 5.0)
    }

    @Test fun intervalAndRaceOutliersAreExcluded() {
        val runs = listOf(
            run("e1", 49, 8.0, 400.0, 145.0),
            run("e2", 42, 8.0, 400.0, 145.0),
            run("l1", 14, 8.0, 400.0, 145.0),
            run("l2", 7, 8.0, 400.0, 145.0),
            // Fast 5K race @ 240s/km (4:00/km) should be filtered out vs 400s median
            run("race", 3, 5.0, 240.0, 185.0)
        )
        val summary = RunningAerobicEfficiency.analyze(runs, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertEquals(4, summary.totalQualifyingRuns)
        assertEquals(0.0, summary.percentChange ?: -1.0, 0.2)
    }

    @Test fun sparseDataDefersWithHonestBaselineCaveat() {
        val runs = listOf(
            run("e1", 14, 6.0, 400.0, 145.0),
            run("e2", 7, 6.0, 395.0, 144.0)
        )
        val summary = RunningAerobicEfficiency.analyze(runs, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertNull(summary.percentChange)
        assertTrue(summary.caveat.contains("Building baseline"))
    }
}
