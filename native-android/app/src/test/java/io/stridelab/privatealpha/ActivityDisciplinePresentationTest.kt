package io.stridelab.privatealpha

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDisciplinePresentationTest {
    private fun activity(type: String, distance: Double? = 0.0) = ActivityRecord(
        id = "discipline-source",
        title = "Source activity",
        type = type,
        source = "garmin",
        startedAt = Instant.parse("2026-11-19T07:00:00Z"),
        distanceM = distance,
        durationS = 317.0,
        movingTimeS = 310.0,
        recordedPace = null,
        averageHr = 105.0,
        elevationM = null
    )

    @Test fun garminStrengthZeroDistanceRoutesToStationaryDetails() {
        val strength = activity("strength_training")
        assertTrue(activityUsesStationaryDetail(strength))
        assertFalse(activityHasDisplayDistance(strength))
        assertEquals(317.0, activityDisplayDurationSeconds(strength)!!, 0.001)
        assertEquals("5:17" to "105 bpm", timelineRecordedMetrics(strength))
    }

    @Test fun stationaryWorkoutNeverDisplaysDistanceEvenIfSourceHasMeters() {
        val calisthenics = activity("calisthenics", distance = 1200.0)
        assertTrue(activityUsesStationaryDetail(calisthenics))
        assertFalse(activityHasDisplayDistance(calisthenics))
        assertEquals("5:17" to "105 bpm", timelineRecordedMetrics(calisthenics))
    }

    @Test fun realRunningDistanceAndPacePresentationAreRetained() {
        val running = activity("run", distance = 2110.0)
            .copy(recordedPace = 485.0, averageHr = 144.0)
        assertFalse(activityUsesStationaryDetail(running))
        assertTrue(activityHasDisplayDistance(running))
        assertEquals("2.11 km" to "5:17 · 8:05 /km · 144 bpm",
            timelineRecordedMetrics(running))
    }

    @Test fun zeroDistanceRunUsesDurationWithoutInventingMeters() {
        val running = activity("run", distance = 0.0)
        assertFalse(activityUsesStationaryDetail(running))
        assertFalse(activityHasDisplayDistance(running))
        assertEquals("5:17" to "105 bpm", timelineRecordedMetrics(running))
    }

    @Test fun absentDurationAndHrKeepStationaryMetricsUnknown() {
        val strength = activity("strength", distance = 0.0)
            .copy(durationS = null, movingTimeS = null, averageHr = null)
        assertEquals("Recorded workout" to "", timelineRecordedMetrics(strength))
    }
}
