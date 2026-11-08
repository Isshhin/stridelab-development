package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TracksideTrainingCalendarTest {
    private val monday = LocalDate.parse("2026-11-02")

    private fun snapshot(sessions: List<PlannedSession>) = PlanSnapshot(
        id = "synthetic-week", owner = "synthetic-account", engineVersion = ENGINE1_VERSION,
        policyId = ENGINE1_POLICY, createdAt = Instant.EPOCH,
        request = PlanRequest(start = monday, weeks = 2), sessions = sessions,
        explanations = emptyList()
    )

    @Test fun weeklySummaryKeepsBothSessionsOnOneDayAndCountsPrescribedMinutes() {
        val tuesday = monday.plusDays(1)
        val sessions = listOf(
            PlannedSession("r1", tuesday, "run", "Easy run", 50, 8.0, "easy"),
            PlannedSession("s1", tuesday, "str", "Upper body", 40, null, "strength"),
            PlannedSession("r2", monday.plusDays(6), "run", "Long run", 90, 14.0, "long")
        )
        val days = tracksideTrainingWeek(snapshot(sessions), tuesday)
        assertEquals(monday, days.first().date)
        assertEquals(monday.plusDays(6), days.last().date)
        assertEquals(7, days.size)
        assertEquals(2, days[1].sessions.size)
        assertEquals(90, days[1].plannedMinutes)
        assertEquals(180, days.sumOf { it.plannedMinutes })
        assertEquals(listOf("run" to 50, "str" to 40), days[1].byDiscipline)
    }

    @Test fun chartShowsNoArtificialWorkoutsAndDoesNotModifyPlan() {
        val sessions = listOf(
            PlannedSession("r1", monday.plusDays(6), "run", "Long run", 90, 14.0, "long")
        )
        val plan = snapshot(sessions)
        val days = tracksideTrainingWeek(plan, monday.plusDays(3))
        assertEquals(0, days.first().plannedMinutes)
        assertTrue(days.first().sessions.isEmpty())
        assertEquals(90, days.last().plannedMinutes)
        assertEquals(1, plan.sessions.size)
        assertEquals("planned", plan.sessions.single().status)
        assertEquals(monday, tracksideCalendarMonday(monday.plusDays(6)))
    }

    @Test fun trainingPaletteUsesLimeRunningAccentAndNeutralSurfaces() {
        assertEquals(TracksideTrainingPalette.running, tracksideTrainingDisciplineColor("run"))
        assertEquals(TracksideTrainingPalette.surface, TracksideTrainingPalette.workoutSurface)
        assertEquals(TracksideTrainingPalette.selectionControl, TracksideTrainingPalette.calendarSelected)
        assertEquals(TracksideTrainingPalette.running, TracksideTrainingPalette.calendarSelectionBorder)
    }
}
