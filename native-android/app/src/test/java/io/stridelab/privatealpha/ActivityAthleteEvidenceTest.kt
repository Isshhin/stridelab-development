package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActivityAthleteEvidenceTest {
    private val now = Instant.parse("2026-11-17T12:00:00Z")
    private fun run(id: String, day: Long, km: Double?, pace: Double? = null, hr: Double? = null) =
        ActivityRecord(id, "Run", "run", "garmin", now.minusSeconds(day * 86400),
            km?.times(1000), 3600.0, 3500.0, pace, hr, null)

    @Test fun profileUsesOnlyAllowedRecentRunsAndIdentifiesUnknownCoverage() {
        val a = run("a", 2, 6.0, 360.0, 150.0)
        val b = run("b", 5, 8.0, 350.0, 155.0)
        val old = run("old", 130, 30.0)
        val foreign = run("foreign", 1, 50.0)
        val profile = ActivityAthleteEvidence.profile(
            "owner", listOf(a, b, old, foreign), setOf("a", "b", "old"),
            mapOf("a" to UserActivityWorkoutLabel(ActivityWorkoutKind.EASY_RUN),
                "b" to UserActivityWorkoutLabel(ActivityWorkoutKind.EASY_RUN)), now
        )

        assertEquals(1, profile.version)
        assertEquals(2, profile.recordedRunCount)
        assertEquals(14.0, profile.recordedDistanceByWeekKm.values.sum(), 0.0)
        assertEquals(8.0, profile.longestRecordedRunLast28DaysKm!!, 0.0)
        assertEquals(355.0, profile.userReportedRunCharacteristics
            .getValue(ActivityWorkoutKind.EASY_RUN).medianRecordedPaceSecondsPerKm!!, 0.0)
        assertEquals("UNKNOWN", profile.sourceCoverage)
        assertEquals(listOf("a", "b"), profile.evidenceActivityIds)
    }

    @Test fun profileDoesNotInventDistanceOrEasyRunPurpose() {
        val noDistance = run("no-distance", 1, null, 330.0, 180.0)
        val profile = ActivityAthleteEvidence.profile(
            "owner", listOf(noDistance), setOf(noDistance.id), emptyMap(), now
        )

        assertEquals(1, profile.recordedRunCount)
        assertEquals(0, profile.recordedDistanceByWeekKm.size)
        assertNull(profile.longestRecordedRunLast28DaysKm)
        assertEquals(0, profile.userReportedRunCharacteristics.size)
    }
}
