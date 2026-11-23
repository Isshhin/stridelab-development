package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CustomPlannerTest {
    private val owner = "planner-test-owner"
    private val first = LocalDate.of(2026, 11, 23)
    private val template = ManualWorkoutTemplate.empty(owner, ManualDiscipline.STRENGTH).copy(name = "Strength day")

    @Test fun oneOffAndWeeklyWindowsHaveStableDistinctKeys() {
        val single = CustomSchedule.create(template, ScheduleRule(first), 1)
        val weekly = CustomSchedule.create(template, ScheduleRule(first, weekly = true, end = first.plusWeeks(2)), 1)
        assertEquals(listOf(first), single.between(first, first.plusWeeks(4)).map { it.date })
        val repeats = weekly.between(first, first.plusWeeks(4))
        assertEquals(listOf(first, first.plusWeeks(1), first.plusWeeks(2)), repeats.map { it.date })
        assertEquals(3, repeats.map { it.key.workoutId }.distinct().size)
        assertEquals(repeats, CustomScheduleCodec.decode(CustomScheduleCodec.encode(weekly), owner)
            .between(first, first.plusWeeks(4)))
        assertThrows(IllegalArgumentException::class.java) { weekly.between(first, first.plusYears(1)) }
    }

    @Test fun movedAndSkippedOccurrencesSurviveEditsToFollowingWeeks() {
        val schedule = CustomSchedule.create(template, ScheduleRule(first, weekly = true), 1)
        val moved = CustomOccurrenceKey(schedule.id, first.plusWeeks(2))
        val skipped = CustomOccurrenceKey(schedule.id, first.plusWeeks(3))
        val movedTo = first.plusDays(17)
        val revised = schedule.move(moved, movedTo, 2).move(skipped, null, 3)
            .replaceFuture(first.plusWeeks(1), ScheduleRule(first.plusDays(8), weekly = true), 4)
        assertEquals(movedTo, revised.resolve(moved)?.date)
        assertNull(revised.resolve(skipped))
        assertEquals(first, revised.between(first, first).single().date)
        assertEquals(listOf(first.plusDays(8), first.plusDays(15), movedTo, first.plusDays(22)),
            revised.between(first.plusDays(6), first.plusDays(22)).map { it.date })
        assertEquals(moved.workoutId, revised.between(movedTo, movedTo).single().key.workoutId)
    }

    @Test fun endingSeriesPreservesEarlierOccurrencesAndArchivesRestoreSchedules() {
        val current = CustomSchedule.create(template, ScheduleRule(first, weekly = true), 1)
        val removed = current.replaceFuture(first.plusWeeks(1), null, 2)
        assertEquals(listOf(first), removed.between(first, first.plusWeeks(4)).map { it.date })
        val raw = ManualDisciplineArchive.export(owner, emptyList(), emptyList(), schedules = listOf(removed))
        val restored = ManualDisciplineArchive.import(raw, owner)
        assertEquals(listOf(removed), restored.schedules)
        assertThrows(IllegalArgumentException::class.java) { ManualDisciplineArchive.import(raw, "other-owner") }
    }

    @Test fun recoveryNotesUseCoachedQualityAndSameDayOverlap() {
        val quality = PlannedSession("quality", first.plusDays(1), "run", "Intervals", 35, null, "intervals", quality = true)
        val lower = CustomSchedule.create(template, ScheduleRule(first), 1)
        val custom = CustomCalendarItem(CustomOccurrenceKey(lower.id, first), first, template.name, template.discipline)
        assertNotNull(customAdvisory(ManualDiscipline.PLYOMETRICS, first, listOf(quality), emptyList()))
        assertNotNull(customAdvisory(ManualDiscipline.RUNNING, first, emptyList(), listOf(custom)))
        assertNull(customAdvisory(ManualDiscipline.STRENGTH, first.minusDays(3), listOf(quality), emptyList()))
    }

    @Test fun customDraftAndCompletionShareAnIdAndKeepScheduledDateSeparate() {
        val schedule = CustomSchedule.create(
            ManualWorkoutTemplate.empty(owner, ManualDiscipline.RUNNING), ScheduleRule(first), 1
        )
        val planned = schedule.between(first, first).single()
        val draft = planned.start(first.plusDays(1), 2)
        val complete = draft.copy(distanceKm = "5", durationMinutes = "30").finish(3)
        assertEquals(planned.key.workoutId, complete.id)
        assertEquals(first, complete.custom?.date)
        assertEquals(first.plusDays(1), complete.date)
        assertEquals(complete, FreeformCodec.decode(FreeformCodec.encode(complete), owner))
        val board = FreeformBoard(workouts = listOf(complete), schedules = listOf(schedule))
        assertTrue(board.customBetween(first, first.plusDays(2)).isEmpty())
    }
}
