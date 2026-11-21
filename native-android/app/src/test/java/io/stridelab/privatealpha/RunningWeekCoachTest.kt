package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunningWeekCoachTest {
    private val start = LocalDate.of(2026, 11, 9)

    private fun week(start: LocalDate, meters: Double, missing: Int = 0) =
        TodayDisciplineWeek(start, listOf(2), listOf(0.0), listOf(meters),
            2, 1, null, meters, 2, missing, null, 0, emptyList(), emptyList())

    @Test fun missingEvidenceDoesNotBecomeEasyRunning() {
        val result = summarizeRunningWeek(week(start, 8000.0)) { null }
        assertEquals(mapOf(WeeklyRunType.UNKNOWN to 2), result.types)
        assertNull(result.mostPerformed)
    }

    @Test fun volumeLabelsRequireCompletedWeeksAndCompleteDistances() {
        val previous = summarizeRunningWeek(week(start.minusWeeks(1), 8000.0)) { null }
        val finished = summarizeRunningWeek(week(start, 10000.0)) { null }
        val partial = summarizeRunningWeek(week(start, 10000.0, missing = 1)) { null }
        assertEquals("Increasing", runningWeekVolumeLabel(finished, previous, start.plusDays(7)))
        assertNull(runningWeekVolumeLabel(finished, previous, start.plusDays(6)))
        assertNull(runningWeekVolumeLabel(partial, previous, start.plusDays(7)))
    }
}
