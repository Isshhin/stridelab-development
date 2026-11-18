package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivityRunComparisonTest {
    private val recent = ActivityRecord(
        "new", "New run", "run", "garmin", Instant.parse("2026-11-18T08:00:00Z"),
        10000.0, 4000.0, 3750.0, 350.0, 145.0, 80.0
    )
    private fun earlier(
        id: String = "old", distance: Double? = 10000.0,
        pace: Double? = 370.0, type: String = "run", source: String = "garmin"
    ) = recent.copy(
        id = id, title = "Earlier run", startedAt = recent.startedAt.minusSeconds(7 * 86400),
        distanceM = distance, recordedPace = pace, type = type, source = source, averageHr = 149.0
    )

    @Test fun comparesTwoSourceRecordedRunsWithoutInferringFitness() {
        val result = ActivityRunComparison.compare(recent, earlier())!!
        assertEquals("new", result.recent.id)
        assertEquals("old", result.earlier.id)
        assertEquals((350.0 / 370.0 - 1.0) * 100.0, result.recordedPaceChangePercent!!, 1e-8)
        assertEquals(-4.0, result.recordedHeartRateDifferenceBpm!!, 0.0)
    }

    @Test fun missingPaceOrHeartRateStayUnknown() {
        val result = ActivityRunComparison.compare(
            recent.copy(recordedPace = null, averageHr = null),
            earlier()
        )!!
        assertNull(result.recordedPaceChangePercent)
        assertNull(result.recordedHeartRateDifferenceBpm)
    }

    @Test fun doesNotSubstituteMovingTimeForRecordedPace() {
        val result = ActivityRunComparison.compare(
            recent.copy(recordedPace = null, movingTimeS = 3200.0),
            earlier()
        )!!
        assertNull(result.recordedPaceChangePercent)
    }

    @Test fun rejectsOtherSourcesAndDifferentSports() {
        assertNull(ActivityRunComparison.compare(recent, earlier(source = "fit")))
        assertNull(ActivityRunComparison.compare(recent, earlier(type = "trailrun")))
        assertNull(ActivityRunComparison.compare(recent, earlier(type = "walk")))
    }

    @Test fun rejectsSameRecordAndFutureOrEqualDate() {
        assertNull(ActivityRunComparison.compare(recent, recent))
        assertNull(ActivityRunComparison.compare(recent, earlier().copy(startedAt = recent.startedAt)))
        assertNull(ActivityRunComparison.compare(recent, earlier().copy(startedAt = recent.startedAt.plusSeconds(60))))
    }

    @Test fun enforcesDistanceToleranceAndRejectsUnknownDistance() {
        assertNotNull(ActivityRunComparison.compare(recent, earlier(distance = 9000.0)))
        assertNull(ActivityRunComparison.compare(recent, earlier(distance = 8899.0)))
        assertNull(ActivityRunComparison.compare(recent, earlier(distance = null)))
        assertNull(ActivityRunComparison.compare(recent.copy(distanceM = 0.0), earlier()))
    }

    @Test fun filtersEarlierCandidatesOnlyAndDoesNotMergeProviders() {
        val old = earlier()
        val another = earlier("old2", 10100.0).copy(startedAt = recent.startedAt.minusSeconds(86400))
        val list = listOf(
            old, old.copy(title = "duplicate"), another, earlier("other", source = "fit"),
            recent, recent.copy(id = "future", startedAt = recent.startedAt.plusSeconds(1))
        )
        assertEquals(listOf("old2", "old"), ActivityRunComparison.earlierCandidates(recent, list).map { it.id })
        assertEquals(5, ActivityRunComparison.recentRuns(list).size)
    }

    @Test fun futureDatedRecordIsExcludedFromHistoricalSelection() {
        val future = recent.copy(id = "future", startedAt = recent.startedAt.plusSeconds(1))
        val result = ActivityRunComparison.recentRuns(
            listOf(recent, future, earlier()), asOf = recent.startedAt
        )
        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test fun missingSourceDistanceCannotBeCompared() {
        assertTrue(ActivityRunComparison.recentRuns(
            listOf(recent.copy(distanceM = null), recent.copy(type = "strength"), earlier())
        ).map { it.id } == listOf("old"))
    }
}
