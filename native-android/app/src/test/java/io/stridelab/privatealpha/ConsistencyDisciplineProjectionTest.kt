package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ConsistencyDisciplineProjectionTest {
    @Test
    fun projectsMultiDisciplineDaysInCanonicalOrderWithin28DayWindow() {
        val asOf = LocalDate.of(2026, 11, 24)
        val sameDay = asOf.minusDays(2)
        val outsideWindow = asOf.minusDays(30)

        val runActivity = ActivityRecord(
            id = "garmin-run",
            source = "garmin",
            type = "Run",
            title = "Easy Run",
            startedAt = sameDay.atTime(7, 0).toInstant(ZoneOffset.UTC),
            distanceM = 6000.0,
            durationS = 1900.0,
            movingTimeS = 1900.0,
            recordedPace = null,
            averageHr = null,
            elevationM = null
        )
        val oldRunActivity = ActivityRecord(
            id = "garmin-old",
            source = "garmin",
            type = "Run",
            title = "Old Run",
            startedAt = outsideWindow.atTime(7, 0).toInstant(ZoneOffset.UTC),
            distanceM = 5000.0,
            durationS = 1500.0,
            movingTimeS = 1500.0,
            recordedPace = null,
            averageHr = null,
            elevationM = null
        )
        val completedStrength = FreeformWorkout(
            id = "str-1",
            owner = "local",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = sameDay.toString(),
            startedAt = 100L,
            modifiedAt = 200L,
            completedAt = 200L,
            workoutName = "Lower Lift"
        )
        val draftCalisthenics = FreeformWorkout(
            id = "cal-draft",
            owner = "local",
            discipline = ManualDiscipline.CALISTHENICS,
            performedOn = sameDay.toString(),
            startedAt = 100L,
            modifiedAt = 200L,
            completedAt = null,
            workoutName = "Incomplete Draft"
        )
        val completedCalisthenicsSession = PlannedSession(
            id = "s-cal",
            date = asOf,
            discipline = "cal",
            title = "Calisthenics",
            minutes = 30,
            distanceKm = null,
            kind = "calisthenics",
            status = "completed",
            log = WorkoutLog(at = Instant.parse("2026-11-24T09:00:00Z"), performedOn = asOf)
        )
        val plan = PlanSnapshot(
            id = "p-1",
            owner = "local",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            request = PlanRequest(goal = "General", runDays = 0, calDays = 1, days = setOf(2)),
            sessions = listOf(completedCalisthenicsSession),
            explanations = emptyList(),
            status = "approved"
        )

        val result = projectConsistencyDisciplinesByDate(
            activities = listOf(runActivity, oldRunActivity),
            plans = listOf(plan),
            freeformBoard = FreeformBoard(workouts = listOf(completedStrength, draftCalisthenics)),
            owner = "local",
            asOf = asOf,
            zone = ZoneOffset.UTC
        )

        assertFalse("Dates outside 28-day window must be excluded", outsideWindow in result)
        assertEquals(
            listOf(ManualDiscipline.RUNNING, ManualDiscipline.STRENGTH),
            result[sameDay]
        )
        assertEquals(listOf(ManualDiscipline.CALISTHENICS), result[asOf])
    }
}
