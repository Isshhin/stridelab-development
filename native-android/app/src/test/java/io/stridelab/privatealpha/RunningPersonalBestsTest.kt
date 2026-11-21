package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RunningPersonalBestsTest {
    private val now = Instant.parse("2026-11-21T06:00:00Z")

    private fun run(id: String, daysAgo: Long, distKm: Double, paceSec: Double) = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = now.minus(daysAgo, ChronoUnit.DAYS),
        distanceM = distKm * 1000.0,
        durationS = distKm * paceSec,
        movingTimeS = distKm * paceSec,
        recordedPace = paceSec,
        averageHr = 155.0,
        elevationM = 20.0
    )

    private fun detailWithKmSplits(id: String, pacesPerKm: List<Double>): ActivityDetail {
        val laps = pacesPerKm.mapIndexed { idx, pace ->
            ActivityLap(index = idx, distance = 1000.0, seconds = pace, pace = pace, hr = 155.0)
        }
        return ActivityDetail(
            activityId = id,
            samples = emptyList(),
            laps = laps,
            fetchedAt = now.toEpochMilli()
        )
    }

    @Test fun findsFastestContiguousSplitWindowInsideLongerRun() {
        val longRun = run("long1", 4, 10.0, 340.0)
        // Middle 5 km (splits 2..6) at 270 s/km = 1350 s (22:30 5K best effort)
        val detail = detailWithKmSplits("long1", listOf(380.0, 380.0, 270.0, 270.0, 270.0, 270.0, 270.0, 380.0, 380.0, 380.0))
        val entries = RunningPersonalBests.compute(
            activities = listOf(longRun),
            details = mapOf("long1" to detail),
            asOf = now
        )
        val fiveK = entries.first { it.distance == PersonalBestDistance.FIVE_K }
        assertNotNull(fiveK.recordedBest)
        assertEquals(PersonalBestSource.SPLIT_BEST_EFFORT, fiveK.recordedBest!!.source)
        assertEquals(1350.0, fiveK.recordedBest!!.durationSeconds, 0.5)
    }

    @Test fun rejectsGpsGlitchLapsFasterThanValidThreshold() {
        val corruptRun = run("glitch", 2, 5.0, 290.0)
        // Lap 2 has an impossible 90 s/km GPS spike (1:30/km)
        val detail = detailWithKmSplits("glitch", listOf(300.0, 90.0, 300.0, 300.0, 300.0))
        val entries = RunningPersonalBests.compute(
            activities = listOf(corruptRun),
            details = mapOf("glitch" to detail),
            asOf = now
        )
        val oneK = entries.first { it.distance == PersonalBestDistance.ONE_K }
        assertNotNull(oneK.recordedBest)
        assertTrue("1K pace should be >= 150 s/km, was ${oneK.recordedBest!!.paceSecPerKm}", oneK.recordedBest!!.paceSecPerKm >= 150.0)
    }

    @Test fun shortRunsNeverExtrapolateLongerBestEfforts() {
        val shortRun = run("short", 1, 3.0, 270.0)
        val detail = detailWithKmSplits("short", listOf(270.0, 270.0, 270.0))
        val entries = RunningPersonalBests.compute(
            activities = listOf(shortRun),
            details = mapOf("short" to detail),
            asOf = now
        )
        val fiveK = entries.first { it.distance == PersonalBestDistance.FIVE_K }
        assertNull(fiveK.recordedBest)
    }

    @Test fun keepsOfficialRacePrSeparateFromRecordedBestEffort() {
        val racePr = OfficialRacePr(
            distanceKey = PersonalBestDistance.FIVE_K.key,
            durationSeconds = 1185,
            eventName = "City 5K",
            achievedOn = LocalDate.of(2026, 5, 10)
        )
        val longRun = run("r1", 3, 6.0, 300.0)
        val detail = detailWithKmSplits("r1", List(6) { 300.0 })
        val entries = RunningPersonalBests.compute(
            activities = listOf(longRun),
            details = mapOf("r1" to detail),
            officialPrs = mapOf(racePr.distanceKey to racePr),
            asOf = now
        )
        val fiveK = entries.first { it.distance == PersonalBestDistance.FIVE_K }
        assertEquals(1185, fiveK.officialPr?.durationSeconds)
        assertEquals(1500.0, fiveK.recordedBest?.durationSeconds ?: 0.0, 0.5)
        assertEquals(PersonalBestSource.SPLIT_BEST_EFFORT, fiveK.recordedBest?.source)
    }
}
