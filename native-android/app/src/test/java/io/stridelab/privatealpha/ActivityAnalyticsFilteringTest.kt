package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivityAnalyticsFilteringTest {
    private fun row(id: String, sport: String, source: String, distance: Double?) =
        ActivityRecord(id, id, sport, source, Instant.parse("2026-11-18T08:00:00Z"),
            distance, null, null, null, null, null)

    private val history = listOf(
        row("a", "run", "garmin", null),
        row("b", "run", "garmin", 4999.0),
        row("c", "run", "fit", 5000.0),
        row("d", "trail_running", "garmin", 9999.9),
        row("e", "treadmill", "manual", 10000.0),
        row("f", "virtualrun", "garmin", 16000.0),
        row("g", "walking", "garmin", 5000.0),
        row("h", "strength", "garmin", null)
    )

    @Test fun sourcesAreRestrictedToActualSavedRunningSources() {
        assertEquals(listOf("fit", "garmin", "manual"),
            ActivityAnalyticsFiltering.availableSources(history))
    }

    @Test fun allRetainsUnknownDistanceButNeverIncludesOtherSports() {
        val rows = ActivityAnalyticsFiltering.apply(history, ActivityAnalyticsFilter())
        assertEquals(6, rows.size)
        assertTrue(rows.any { it.id == "a" && it.distanceM == null })
    }

    @Test fun filtersByProviderWithoutModifyingSourceRecords() {
        val subset = ActivityAnalyticsFiltering.apply(
            history, ActivityAnalyticsFilter(source = "garmin"))
        assertEquals(listOf("a", "b", "d", "f"), subset.map { it.id })
        assertEquals(8, history.size)
    }

    @Test fun sportFiltersKeepRoadTrailAndTreadmillDistinct() {
        fun selected(kind: ActivityAnalyticsKind) = ActivityAnalyticsFiltering
            .apply(history, ActivityAnalyticsFilter(kind = kind)).map { it.id }
        assertEquals(listOf("a", "b", "c"), selected(ActivityAnalyticsKind.ROAD))
        assertEquals(listOf("d"), selected(ActivityAnalyticsKind.TRAIL))
        assertEquals(listOf("e", "f"), selected(ActivityAnalyticsKind.TREADMILL))
    }

    @Test fun distanceBandsHaveExactBoundariesAndNoMadeUpDistance() {
        fun selected(distance: ActivityAnalyticsDistance) = ActivityAnalyticsFiltering
            .apply(history, ActivityAnalyticsFilter(distance = distance)).map { it.id }
        assertEquals(listOf("b"), selected(ActivityAnalyticsDistance.SHORT))
        assertEquals(listOf("c", "d"), selected(ActivityAnalyticsDistance.MID))
        assertEquals(listOf("e", "f"), selected(ActivityAnalyticsDistance.LONG))
    }

    @Test fun combinedFiltersSelectOnlyValidMatchingRows() {
        val result = ActivityAnalyticsFiltering.apply(
            history, ActivityAnalyticsFilter("garmin", ActivityAnalyticsKind.TRAIL,
                ActivityAnalyticsDistance.MID)
        )
        assertEquals(listOf("d"), result.map { it.id })
        assertTrue(ActivityAnalyticsFiltering.apply(
            history, ActivityAnalyticsFilter("fit", ActivityAnalyticsKind.TRAIL,
                ActivityAnalyticsDistance.MID)).isEmpty())
    }
}
