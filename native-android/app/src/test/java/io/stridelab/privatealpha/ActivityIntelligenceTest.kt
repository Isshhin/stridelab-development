package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ActivityIntelligenceTest {
    private val run = ActivityRecord("current", "Run", "run", "garmin", Instant.parse("2026-10-26T00:00:00Z"), 5000.0, 1800.0, 1750.0, 360.0, 145.0, 0.0)
    private fun prior(id: String, pace: Double = 400.0) = run.copy(id = id, recordedPace = pace, startedAt = run.startedAt.minusSeconds(86400))
    @Test fun comparesMedianOfEarlierMatchingEvidence() {
        val result = ActivityIntelligence.comparison(run, listOf(prior("1", 380.0), prior("2", 400.0), prior("3", 800.0)))!!
        assertEquals(400.0, result.medianPace, 0.0)
        assertEquals(-10.0, result.changePercent, .0001)
        assertTrue(ActivityIntelligence.narrative(run, listOf(prior("1"),prior("2"),prior("3"))).contains("not improved or reduced fitness"))
    }
    @Test fun requiresThreeDistinctRunsAndExcludesCurrentOrFuture() {
        assertNull(ActivityIntelligence.comparison(run, listOf(prior("1"), prior("1"), prior("2"),run,prior("future").copy(startedAt = run.startedAt.plusSeconds(1)))))
    }
    @Test fun excludesDifferentSourceDisciplineDistanceAndOldHistory() {
        val invalid = listOf(prior("source").copy(source="manual"),prior("trail").copy(type="trailrun"),prior("distance").copy(distanceM=6000.0),prior("old").copy(startedAt=run.startedAt.minusSeconds(91*86400)))
        assertNull(ActivityIntelligence.comparison(run, invalid + listOf(prior("1"), prior("2"))))
    }
    @Test fun noPaceInferenceOrStrengthAndWalkingAnalysis() {
        val history = listOf(prior("1"),prior("2"),prior("3"))
        for (a in listOf(run.copy(recordedPace=null), run.copy(distanceM=null), run.copy(type="strength"), run.copy(type="walk"))) assertNull(ActivityIntelligence.comparison(a,history))
    }
    @Test fun evenMedianAndExactBoundaries() {
        val history = listOf(prior("1",380.0),prior("2",400.0),prior("3",420.0),prior("4",440.0).copy(distanceM=5500.0,startedAt=run.startedAt.minusSeconds(90*86400)))
        assertEquals(410.0,ActivityIntelligence.comparison(run,history)!!.medianPace,0.0)
    }
}
