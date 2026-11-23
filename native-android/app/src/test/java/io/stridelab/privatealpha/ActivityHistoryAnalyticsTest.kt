package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ActivityHistoryAnalyticsTest {
    private val manila = ZoneId.of("Asia/Manila")
    private val now = Instant.parse("2026-11-18T11:00:00Z")

    private fun run(id: String, time: String, distance: Double? = 5000.0, elapsed: Double? = 1800.0) =
        ActivityRecord(id, "Run", "run", "garmin", Instant.parse(time), distance, elapsed,
            null, null, null, null)

    @Test fun emptyHistoryHasNoInventedDistanceOrDuration() {
        val weeks = ActivityHistoryAnalytics.series(emptyList(), now, manila, ActivityAnalyticsPeriod.WEEK)
        assertEquals(8, weeks.size)
        assertEquals("2026-09-28", weeks.first().start.toString())
        assertEquals("2026-11-16", weeks.last().start.toString())
        assertTrue(weeks.all {
            it.activityCount == 0 && it.recordedDistanceM == null && it.recordedElapsedS == null
        })
    }

    @Test fun usesLocalMondayAndHandlesSundayToMondayBoundary() {
        val rows = listOf(
            run("sunday", "2026-11-15T15:59:00Z"),
            run("monday", "2026-11-15T16:01:00Z")
        )
        val buckets = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.WEEK)
        assertEquals(1, buckets[buckets.lastIndex - 1].activityCount)
        assertEquals(1, buckets.last().activityCount)
        assertEquals("2026-11-16", buckets.last().start.toString())
    }

    @Test fun missingMeasurementsAreNotZeroFilled() {
        val rows = listOf(
            run("unknown", "2026-11-16T12:00:00Z", distance = null, elapsed = null),
            run("measured-zero", "2026-11-17T12:00:00Z", distance = 0.0, elapsed = 0.0)
        )
        val bucket = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.WEEK).last()
        assertEquals(2, bucket.activityCount)
        assertEquals(1, bucket.recordedDistanceCount)
        assertEquals(0.0, bucket.recordedDistanceM!!, 0.0)
        assertEquals(1, bucket.recordedElapsedCount)
        assertEquals(0.0, bucket.recordedElapsedS!!, 0.0)
        val unknown = ActivityHistoryAnalytics.series(rows.take(1), now, manila, ActivityAnalyticsPeriod.WEEK).last()
        assertNull(unknown.recordedDistanceM)
        assertNull(unknown.recordedElapsedS)
    }

    @Test fun ignoresFutureEntriesAndNonRunningDisciplines() {
        val valid = run("past", "2026-11-17T01:00:00Z")
        val rows = listOf(
            valid,
            run("future", "2026-11-19T01:00:00Z"),
            valid.copy(id = "walk", type = "walk"),
            valid.copy(id = "strength", type = "strength"),
            valid.copy(id = "trail", type = "trail_running")
        )
        val bucket = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.WEEK).last()
        assertEquals(2, bucket.activityCount)
        assertEquals(10000.0, bucket.recordedDistanceM!!, 0.0)
    }

    @Test fun suppressesExactSourceIdentityDuplicatesButNotOtherProviders() {
        val r = run("shared", "2026-11-17T01:00:00Z")
        val bucket = ActivityHistoryAnalytics.series(
            listOf(r, r.copy(title = "Same record"), r.copy(source = "manual")),
            now, manila, ActivityAnalyticsPeriod.WEEK
        ).last()
        assertEquals(2, bucket.activityCount)
    }

    @Test fun monthBoundariesIncludeYears() {
        val rows = listOf(
            run("oct", "2025-10-30T12:00:00Z"),
            run("nov", "2025-11-03T12:00:00Z"),
            run("current", "2026-11-16T12:00:00Z")
        )
        val buckets = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.MONTH)
        assertEquals(12, buckets.size)
        assertEquals("2025-12-01", buckets.first().start.toString())
        assertEquals("2026-11-01", buckets.last().start.toString())
        assertEquals(1, buckets.last().activityCount)
        assertEquals(0, buckets.first().activityCount)
    }

    @Test fun yearBucketsUseLocalDatesAndDoNotIncludeAncientHistory() {
        val rows = listOf(
            run("old", "2020-01-01T00:00:00Z"),
            run("previous", "2025-10-30T12:00:00Z"),
            run("current", "2026-11-16T12:00:00Z")
        )
        val buckets = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.YEAR)
        assertEquals(5, buckets.size)
        assertEquals("2022-01-01", buckets.first().start.toString())
        assertEquals(1, buckets[buckets.lastIndex - 1].activityCount)
        assertEquals(1, buckets.last().activityCount)
    }

    @Test fun elapsedAndMovingTimeAreNotInterchanged() {
        val r = run("timer", "2026-11-17T01:00:00Z", elapsed = null)
            .copy(movingTimeS = 1200.0)
        val bucket = ActivityHistoryAnalytics.series(listOf(r), now, manila, ActivityAnalyticsPeriod.WEEK).last()
        assertEquals(1, bucket.activityCount)
        assertNull(bucket.recordedElapsedS)
        assertEquals(0, bucket.recordedElapsedCount)
    }

    @Test fun sourceGapsAreRepresentedWithoutFillingMissingWeeks() {
        val rows = listOf(
            run("prior", "2026-10-20T12:00:00Z"),
            run("current", "2026-11-17T12:00:00Z")
        )
        val buckets = ActivityHistoryAnalytics.series(rows, now, manila, ActivityAnalyticsPeriod.WEEK)
        assertEquals(2, buckets.count { it.activityCount > 0 })
        assertEquals(6, buckets.count { it.activityCount == 0 })
    }
}
