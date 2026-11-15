package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class IndependentRunningHistoryTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val asOf = LocalDate.of(2026, 11, 14)
    private val mondays = listOf(
        LocalDate.of(2026, 10, 12),
        LocalDate.of(2026, 10, 19),
        LocalDate.of(2026, 10, 26),
        LocalDate.of(2026, 11, 2)
    )

    private fun run(
        id: String, date: LocalDate,
        km: Double? = 10.0, source: String = "garmin",
        type: String = "running"
    ) = ActivityRecord(id, "Recorded run", type, source,
        date.atTime(10, 0).atZone(zone).toInstant(),
        km?.times(1000), 3600.0, null, null, null, null)

    @Test fun fourConfirmedWeeksProvideExactIndependentMeansAndFrequency() {
        val records = mondays.mapIndexed { i, monday ->
            run("run-$i", monday.plusDays(1), listOf(10.0, 15.0, 20.0, 25.0)[i])
        }
        val result = IndependentRunningHistory.summarize(records, asOf, zone, mondays.toSet())
        assertEquals(4, result.completeWeekCount)
        assertEquals(17.5, result.confirmedFourWeekMeanKm!!, 0.0)
        assertEquals(22.5, result.confirmedRecentTwoWeekMeanKm!!, 0.0)
        assertEquals(1.0, result.confirmedMeanRunsPerWeek!!, 0.0)
        assertEquals(15.0, result.confirmedWeeklyDistanceRangeKm!!, 0.0)
        assertEquals(25.0, result.completedWeeks.last().longestKnownRunKm!!, 0.0)
        assertEquals(1, result.completedWeeks.last().recordedDays)
    }

    @Test fun missingCoverageNeverBecomesAZeroMileageWeek() {
        val result = IndependentRunningHistory.summarize(
            listOf(run("one", mondays[0], 10.0)),
            asOf, zone, setOf(mondays[0], mondays[2], mondays[3]))
        assertEquals(3, result.completeWeekCount)
        assertEquals(0.0, result.completedWeeks[2].confirmedWeeklyKm!!, 0.0)
        assertNull(result.completedWeeks[1].confirmedWeeklyKm)
        assertNull(result.confirmedFourWeekMeanKm)
        assertEquals(0.0, result.confirmedRecentTwoWeekMeanKm!!, 0.0)
        assertNull(result.confirmedMeanRunsPerWeek)
        assertNull(result.confirmedWeeklyDistanceRangeKm)
    }

    @Test fun runWithoutDistanceMakesWeekIncompleteEvenWithSourceCoverage() {
        val result = IndependentRunningHistory.summarize(
            listOf(run("one", mondays.last(), 6.0), run("two", mondays.last().plusDays(1), null)),
            asOf, zone, mondays.toSet())
        val latest = result.completedWeeks.last()
        assertEquals(6.0, latest.knownDistanceKm, 0.0)
        assertEquals(1, latest.runsWithoutDistance)
        assertEquals(2, latest.recordedRunCount)
        assertNull(latest.confirmedWeeklyKm)
        assertNull(result.confirmedFourWeekMeanKm)
    }

    @Test fun duplicateSameSourceIdIsNotDoubleCountedAndConflictsAreVisible() {
        val first = run("same", mondays.last(), 8.0)
        val identical = IndependentRunningHistory.summarize(
            listOf(first, first), asOf, zone, mondays.toSet())
        assertEquals(1, identical.completedWeeks.last().recordedRunCount)
        assertEquals(8.0, identical.completedWeeks.last().confirmedWeeklyKm!!, 0.0)

        val conflicting = IndependentRunningHistory.summarize(
            listOf(first, first.copy(distanceM = 9000.0)),
            asOf, zone, mondays.toSet())
        assertEquals(1, conflicting.completedWeeks.last().ambiguousSourceIds)
        assertEquals(0, conflicting.completedWeeks.last().recordedRunCount)
        assertNull(conflicting.completedWeeks.last().confirmedWeeklyKm)
    }

    @Test fun localTimezoneWeekBoundaryExcludesCurrentPartialWeek() {
        val startOfCurrentMonday = Instant.parse("2026-11-08T16:30:00Z")
        val prevSunday = Instant.parse("2026-11-08T15:30:00Z")
        val old = run("prev", mondays.last(), 5.0).copy(startedAt = prevSunday)
        val current = run("current", mondays.last(), 100.0)
            .copy(startedAt = startOfCurrentMonday)
        val result = IndependentRunningHistory.summarize(
            listOf(old, current), asOf, zone, mondays.toSet())
        assertEquals(1, result.completedWeeks.last().recordedRunCount)
        assertEquals(5.0, result.completedWeeks.last().confirmedWeeklyKm!!, 0.0)
    }

    @Test fun onlyRunningAndActiveSourcesContributeToTrainingVolume() {
        val records = listOf(
            run("good", mondays.last(), 8.0),
            run("walk", mondays.last(), 4.0, type = "walking"),
            run("strength", mondays.last(), 3.0, type = "strength"),
            run("retired", mondays.last(), 12.0, source = "intervals"))
        val result = IndependentRunningHistory.summarize(records, asOf, zone, mondays.toSet())
        assertEquals(1, result.completedWeeks.last().recordedRunCount)
        assertEquals(8.0, result.completedWeeks.last().confirmedWeeklyKm!!, 0.0)
    }

    @Test fun currentAndFutureWeeksCannotBeAssertedAsCompleted() {
        assertThrows(IllegalArgumentException::class.java) {
            IndependentRunningHistory.summarize(emptyList(), asOf, zone,
                setOf(LocalDate.of(2026, 11, 9)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IndependentRunningHistory.summarize(
                listOf(run("bad", mondays.last(), Double.NaN)),
                asOf, zone, mondays.toSet())
        }
    }
}
