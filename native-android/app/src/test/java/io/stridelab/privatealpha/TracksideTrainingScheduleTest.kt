package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TracksideTrainingScheduleTest {
    private fun session(
        id: String,
        date: String,
        status: String = "planned",
        discipline: String = "run"
    ) = PlannedSession(
        id = id,
        date = LocalDate.parse(date),
        discipline = discipline,
        title = id,
        minutes = 45,
        distanceKm = null,
        kind = "easy",
        status = status
    )

    @Test fun noPlanSessionsMeansNoCalendarDates() {
        assertTrue(tracksideScheduledDates(emptyList()).isEmpty())
    }

    @Test fun onlyScheduledDatesAppearInChronologicalOrder() {
        val days = tracksideScheduledDates(
            listOf(
                session("later", "2026-11-14"),
                session("first", "2026-11-09"),
                session("unassigned", "2026-11-10", status = "unassigned"),
                session("rest-day", "2026-11-11", status = "unassigned"),
                session("middle", "2026-11-12")
            )
        )
        assertEquals(
            listOf("2026-11-09", "2026-11-12", "2026-11-14"),
            days.map { it.date.toString() }
        )
    }

    @Test fun multipleDisciplinesStayUnderTheirOriginalDateAndOrder() {
        val days = tracksideScheduledDates(
            listOf(
                session("run", "2026-11-10"),
                session("strength", "2026-11-10", discipline = "str"),
                session("next", "2026-11-12")
            )
        )
        assertEquals(2, days.size)
        assertEquals(listOf("run", "strength"), days.first().sessions.map { it.id })
        assertEquals(1, days.last().sessions.size)
    }

    @Test fun completedAndInProgressRemainScheduledWhileUnassignedIsHidden() {
        val days = tracksideScheduledDates(
            listOf(
                session("completed", "2026-11-05", status = "completed"),
                session("progress", "2026-11-05", status = "in_progress"),
                session("unassigned", "2026-11-05", status = "unassigned")
            )
        )
        assertEquals(listOf("completed", "progress"), days.single().sessions.map { it.id })
    }

    @Test fun monthBoundaryDoesNotInsertUnscheduledDays() {
        val days = tracksideScheduledDates(
            listOf(
                session("dec", "2026-12-02"),
                session("nov", "2026-11-28")
            )
        )
        assertEquals(2, days.size)
        assertEquals(listOf(11, 12), days.map { it.date.monthValue })
    }
}
