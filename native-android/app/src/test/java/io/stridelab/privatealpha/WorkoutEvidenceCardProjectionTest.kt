package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class WorkoutEvidenceCardProjectionTest {
    @Test
    fun garminAndFitRunsProjectRealOriginLabelsAndRecordedMetrics() {
        val day = LocalDate.of(2026, 11, 23)
        val startedAt = day.atTime(6, 45).toInstant(ZoneOffset.UTC)
        val garminRun = ActivityRecord(
            id = "garmin-run-1",
            title = "Aerobic Base Run",
            type = "Run",
            source = "garmin",
            startedAt = startedAt,
            distanceM = 7450.0,
            durationS = 2460.0,
            movingTimeS = 2400.0,
            recordedPace = 322.0,
            averageHr = 152.0,
            elevationM = 45.0
        )
        val fitLinkedRun = ActivityRecord(
            id = "fit-run-2",
            title = "Tempo 10K",
            type = "Run",
            source = "fit",
            startedAt = startedAt.minusSeconds(86_400),
            distanceM = 10_000.0,
            durationS = 3060.0,
            movingTimeS = 3000.0,
            recordedPace = null,
            averageHr = null,
            elevationM = 20.0
        )
        val linkedSession = PlannedSession(
            id = "s-tempo",
            date = day.minusDays(1),
            discipline = "run",
            title = "Tempo Session",
            minutes = 50,
            distanceKm = 10.0,
            kind = "quality",
            status = "completed",
            linkedActivityId = fitLinkedRun.id
        )
        val plan = PlanSnapshot(
            id = "plan-1",
            owner = "local",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = Instant.now(),
            request = PlanRequest(goal = "10K", runDays = 4, days = setOf(1, 3, 5, 7)),
            sessions = listOf(linkedSession),
            explanations = emptyList(),
            status = "approved"
        )
        val board = FreeformBoard(
            workouts = listOf(
                FreeformWorkout(
                    id = "str-1",
                    owner = "local",
                    discipline = ManualDiscipline.STRENGTH,
                    performedOn = day.minusDays(2).toString(),
                    startedAt = 1000L,
                    modifiedAt = 2000L,
                    completedAt = 2000L,
                    durationMinutes = "45",
                    workoutName = "Lower Body"
                )
            )
        )

        val evidence = projectWorkoutEvidence(
            owner = "local",
            plans = listOf(plan),
            board = board,
            activities = listOf(DisplayActivity(garminRun), DisplayActivity(fitLinkedRun)),
            zone = ZoneOffset.UTC
        )

        val garmin = evidence.first { it.key == "import:garmin-run-1" }
        assertEquals("Garmin", garmin.originLabel)
        assertEquals("Garmin", garmin.sourceLabel)
        assertFalse(garmin.sourceLabel.contains("Imported", ignoreCase = true))
        assertEquals(322.0, garmin.avgPaceSecPerKm!!, 0.01)
        assertEquals(152.0, garmin.avgHrBpm!!, 0.01)
        assertEquals(2400.0, garmin.movingTimeSeconds!!, 0.01)

        val fit = evidence.first { it.key == "import:fit-run-2" }
        assertEquals("FIT", fit.originLabel)
        assertEquals("FIT · Coached", fit.sourceLabel)
        assertFalse(fit.sourceLabel.contains("Imported", ignoreCase = true))
        assertEquals(300.0, fit.avgPaceSecPerKm!!, 0.01)
        assertNull(fit.avgHrBpm)
        assertEquals(3000.0, fit.movingTimeSeconds!!, 0.01)

        val strength = evidence.first { it.key == "manual:str-1" }
        assertEquals("Freeform", strength.sourceLabel)
        assertEquals(2700.0, strength.movingTimeSeconds!!, 0.01)
    }
}
