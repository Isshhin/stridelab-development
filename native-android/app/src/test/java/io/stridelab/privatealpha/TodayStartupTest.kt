package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TodayStartupTest {
    private fun run(id: String, day: Int, source: String = "garmin") = ActivityRecord(
        id, id, "run", source, Instant.parse("2026-11-${day}T07:00:00Z"),
        5_000.0, 1_500.0, 1_480.0, 296.0, 145.0, null
    )

    @Test fun planReadinessIsIndependentOfActivityHistory() {
        val state = TrainingState().copy(todayPlanReady = true)
        assertTrue(state.todayPlanReady)
        assertFalse(state.activitySummariesReady)
        assertTrue(state.initializing)
        assertTrue(state.details.isEmpty())
    }

    @Test fun combinedFeedPublishesNewestFirstUsingOnlySummaryRecords() {
        val cloud = listOf(run("old", 20), run("cloud", 22), run("same", 23, "fit"))
        val local = listOf(run("same", 21), run("recent", 23))
        val summaries = mergeActivitySummaries(cloud, local)
        assertEquals(listOf("recent", "cloud", "same", "old"), summaries.map { it.id })
        assertEquals("garmin", summaries.single { it.id == "same" }.source)

        val rows = TodayOverviewCalculator.buildRecentActivities(
            summaries.reversed(), summaries, emptyList(), limit = 2,
            now = Instant.parse("2026-11-24T00:00:00Z")
        )
        assertEquals(listOf("recent", "cloud"), rows.map { it.activityId })
        assertTrue(rows.first().subtitle.contains("145 bpm"))
    }

    @Test fun thumbnailBoundsPointsAndJoinsMovingTrailAcrossGaps() {
        val samples = List(1_000) { index ->
            ActivitySample(index, index.toDouble(), null, null, null,
                14.5.takeUnless { index == 500 }, 121.0 + index * 0.00001)
        }
        val route = checkNotNull(ActivityDetail("route", samples, emptyList(), 1).thumbnailRoute())
        assertEquals(240, route.pointCount)
        assertEquals(0.0, route.segments.single().first().elapsedSeconds, 0.0)
        assertEquals(999.0, route.segments.single().last().elapsedSeconds, 0.0)
        assertTrue(route.allPoints.none { it.elapsedSeconds == 500.0 })
    }
}
