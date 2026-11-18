package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class RecordedDistanceBestsTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private fun row(id: String, distance: Double?, pace: Double?, source: String = "garmin") =
        ActivityRecord(id, id, "run", source, now.minusSeconds(3600),
            distance, 2000.0, 1800.0, pace, 140.0, null)

    @Test fun bestRequiresActualSourceRecordedPace() {
        val best = RecordedDistanceBests.find(
            listOf(row("slow", 5000.0, 400.0), row("fast", 5020.0, 350.0)),
            5000.0, now
        )!!
        assertEquals("fast", best.activity.id)
        assertEquals(5000.0, best.targetMeters, 0.0)
    }

    @Test fun missingOrOutOfRangeDistanceCannotEstablishBest() {
        val history = listOf(
            row("null", null, 310.0),
            row("far", 5200.0, 320.0),
            row("unknownPace", 5000.0, null),
            row("valid", 4995.0, 380.0)
        )
        assertEquals("valid", RecordedDistanceBests.find(history, 5000.0, now)!!.activity.id)
        assertNull(RecordedDistanceBests.find(history, 10000.0, now))
    }

    @Test fun rejectWalkingAndStrengthData() {
        val r = row("runner", 5000.0, 400.0)
        assertNull(RecordedDistanceBests.find(
            listOf(r.copy(type="walk"),r.copy(type="strength")),5000.0,now))
    }

    @Test fun futureEntriesAndRepeatedSourceIdentityAreExcluded() {
        val older = row("old",5000.0,370.0)
        val future = row("future",5000.0,320.0).copy(startedAt=now.plusSeconds(60))
        assertEquals("old", RecordedDistanceBests.find(
            listOf(older, older.copy(recordedPace=200.0), future),5000.0,now)!!.activity.id)
    }

    @Test fun halfMarathonAndMarathonTargetsStaySeparate() {
        val rows = listOf(
            row("half",21097.5,350.0),
            row("marathon",42195.0,380.0)
        )
        assertEquals("half",RecordedDistanceBests.find(rows,21097.5,now)!!.activity.id)
        assertEquals("marathon",RecordedDistanceBests.find(rows,42195.0,now)!!.activity.id)
        assertEquals(4,RecordedDistanceBests.distanceTargets.size)
    }
}
