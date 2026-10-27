package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Engine2TimelineTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private fun source(recordId: String) = Engine2Source("fictional-athlete",
        "synthetic-provider", recordId, now.minusSeconds(3600), now.minusSeconds(300))
    private fun row(id: String, sport: Engine2Discipline, measurements: List<Engine2Measurement>,
                    whenAt: Instant = now.minusSeconds(3000),
                    status: Engine2RecordStatus = Engine2RecordStatus.CONFIRMED) =
        Engine2WorkoutEvidence(id, sport, whenAt, status, measurements)
    private fun ctx(vararg workouts: Engine2WorkoutEvidence) =
        Engine2AthleteContext("fictional-athlete", now, workouts.toList(), emptyList())
    private fun distance(id: String, km: Double) =
        Engine2Measurement(Engine2Metric.DISTANCE_KM, km, source(id))
    private fun duration(id: String, seconds: Double) =
        Engine2Measurement(Engine2Metric.DURATION_SECONDS, seconds, source(id))

    @Test fun observedRunningDistanceAndSecondsStaySeparateAndNeverFillMissingMeasurements() {
        val p = ctx(
            row("run-1", Engine2Discipline.RUNNING, listOf(distance("a", 8.0), duration("a", 2500.0))),
            row("run-2", Engine2Discipline.RUNNING, listOf(duration("b", 2000.0))),
            row("strength", Engine2Discipline.STRENGTH, listOf(duration("c", 1800.0)))
        )
        val summary = Engine2Timeline.summarize(p, now.minusSeconds(7200), now)
        assertEquals(2, summary.running.confirmedRuns)
        assertEquals(1, summary.running.runsMissingDistance)
        assertEquals(8.0, summary.running.knownDistanceKm!!, 0.0)
        assertFalse(summary.running.complete)
        assertEquals(4500.0,
            summary.byDiscipline.getValue(Engine2Discipline.RUNNING).knownDurationSeconds!!, 0.0)
        assertEquals(1800.0,
            summary.byDiscipline.getValue(Engine2Discipline.STRENGTH).knownDurationSeconds!!, 0.0)
        assertEquals(0, summary.byDiscipline.getValue(Engine2Discipline.CALISTHENICS).confirmedWorkouts)
        assertNull(summary.byDiscipline.getValue(Engine2Discipline.CALISTHENICS).knownDurationSeconds)
        assertFalse(summary.hasProvenanceCollisions)
    }

    @Test fun draftsOutsideWindowAndFuturePlannedTargetsNeverBecomeMeasuredVolume() {
        val old = row("old", Engine2Discipline.RUNNING, listOf(distance("old", 20.0)),
            now.minusSeconds(200000))
        val draft = row("draft", Engine2Discipline.RUNNING, listOf(distance("draft", 13.0)),
            status = Engine2RecordStatus.DRAFT)
        val planned = row("plan", Engine2Discipline.RUNNING, emptyList(),
            now.plusSeconds(86400), Engine2RecordStatus.PLANNED)
        val actual = row("recent", Engine2Discipline.RUNNING, emptyList())
        val summary = Engine2Timeline.summarize(ctx(old, draft, planned, actual),
            now.minusSeconds(7200), now)
        assertEquals(1, summary.running.confirmedRuns)
        assertNull(summary.running.knownDistanceKm)
        assertEquals(1, summary.running.runsMissingDistance)
        assertFalse(summary.running.complete)
        assertNull(summary.byDiscipline.getValue(Engine2Discipline.RUNNING).knownDurationSeconds)
    }

    @Test fun exactProviderCollisionFlagsOnlyItsOwnWindow() {
        val src = source("duplicate")
        val a = row("one", Engine2Discipline.RUNNING,
            listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 4.0, src)))
        val b = row("two", Engine2Discipline.RUNNING,
            listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 4.0, src)))
        val all = Engine2Timeline.summarize(ctx(a, b), now.minusSeconds(7200), now)
        assertTrue(all.hasProvenanceCollisions)
        val empty = Engine2Timeline.summarize(ctx(a, b), now.minusSeconds(7200),
            now.minusSeconds(5000))
        assertFalse(empty.hasProvenanceCollisions)
    }

    @Test fun futureOrReversedTimeWindowsAreRejectedWithoutMutation() {
        val context = ctx()
        assertThrows(IllegalArgumentException::class.java) {
            Engine2Timeline.summarize(context, now, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2Timeline.summarize(context, now.minusSeconds(60), now.plusSeconds(60))
        }
        assertEquals(context, ctx())
    }
}
