package io.stridelab.privatealpha

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Displayed GPS previews are requested newest first. */
class TodayCachedGpsPreviewPriorityTest {
    private fun activity(id: String, start: String, type: String = "run") =
        ActivityRecord(
            id = id, title = id, type = type, source = "garmin",
            startedAt = Instant.parse(start), distanceM = 5_000.0,
            durationS = 1_800.0, movingTimeS = 1_790.0,
            recordedPace = null, averageHr = null, elevationM = null
        )

    @Test fun requestsNewestSavedGpsNotTheOldestOrUncachedCard() {
        val older = activity("older", "2026-11-07T01:00:00Z")
        val newest = activity("newest", "2026-11-07T12:00:00Z")
        val noLocalFit = activity("not-cached", "2026-11-07T13:00:00Z")
        val strength = activity("strength", "2026-11-07T14:00:00Z", "strength")
        val selected = todayCachedGpsPreviewIds(
            listOf(older, strength, noLocalFit, newest),
            setOf("older", "newest", "strength")
        )
        assertEquals(listOf("strength", "newest", "older"), selected)
    }

    @Test fun allDisplayedActivitiesAreRequested() {
        val records = (0..39).map {
            activity("run-$it", Instant.parse("2026-11-07T12:00:00Z")
                .minusSeconds(it * 60L).toString())
        }
        assertEquals((0..39).map { "run-$it" },
            todayCachedGpsPreviewIds(records.reversed(), records.map { it.id }.toSet()))
    }

    @Test fun sameTimestampUsesDeterministicRecentFirstTieBreak() {
        val one = activity("a", "2026-11-07T12:00:00Z")
        val two = activity("b", "2026-11-07T12:00:00Z")
        assertEquals(listOf("b","a"),
            todayCachedGpsPreviewIds(listOf(one, two, one), setOf("a","b")))
    }
}
