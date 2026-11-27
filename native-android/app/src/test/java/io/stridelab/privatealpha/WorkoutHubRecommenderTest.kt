package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WorkoutHubRecommenderTest {
    @Test
    fun highAutonomicStrainPrioritizesRecoveryAndMobility() {
        val today = LocalDate.of(2026, 11, 28)
        val records = mutableListOf<WellnessRecord>()
        for (daysAgo in 21 downTo 3) {
            val instant = today.minusDays(daysAgo.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC)
            records += WellnessRecord("rhr-$daysAgo", "resting_hr", 50.0, "bpm", instant, "garmin")
            records += WellnessRecord("hrv-$daysAgo", "hrv_rmssd", 62.0, "ms", instant, "garmin")
            records += WellnessRecord("sleep-$daysAgo", "sleep_duration", 7.8, "hours", instant, "garmin")
        }
        for (daysAgo in 2 downTo 0) {
            val instant = today.minusDays(daysAgo.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC)
            records += WellnessRecord("rhr-s-$daysAgo", "resting_hr", 63.0, "bpm", instant, "garmin")
            records += WellnessRecord("hrv-s-$daysAgo", "hrv_rmssd", 36.0, "ms", instant, "garmin")
            records += WellnessRecord("sleep-s-$daysAgo", "sleep_duration", 5.2, "hours", instant, "garmin")
        }
        val readiness = BiometricReadinessEvaluator.evaluate(records, today)
        val recs = WorkoutHubRecommender.recommend(
            library = CuratedWorkoutLibrary.all,
            readiness = readiness,
            plannedSessionsToday = emptyList(),
            plannedSessionsTomorrow = emptyList(),
            recentFreeform = emptyList(),
            savedEquipment = emptySet(),
            limit = 3
        )
        assertEquals(3, recs.size)
        assertEquals(WorkoutHubCategory.RECOVERY, recs.first().workout.category)
        assertTrue(recs.none { it.workout.intensity == WorkoutIntensity.HIGH })
    }

    @Test
    fun upcomingKeySessionAvoidsHighIntensityPlyoAndStrength() {
        val tomorrowSession = PlannedSession(
            id = "s1",
            date = LocalDate.of(2026, 11, 29),
            discipline = "run",
            title = "Threshold Intervals",
            minutes = 55,
            distanceKm = 10.0,
            kind = "threshold",
            quality = true
        )
        val recs = WorkoutHubRecommender.recommend(
            library = CuratedWorkoutLibrary.all,
            readiness = null,
            plannedSessionsToday = emptyList(),
            plannedSessionsTomorrow = listOf(tomorrowSession),
            recentFreeform = emptyList(),
            savedEquipment = emptySet(),
            limit = 3
        )
        assertEquals(3, recs.size)
        assertTrue(recs.none { it.workout.intensity == WorkoutIntensity.HIGH })
        assertTrue(recs.any { it.workout.category == WorkoutHubCategory.MOBILITY || it.workout.category == WorkoutHubCategory.RECOVERY })
    }
}
