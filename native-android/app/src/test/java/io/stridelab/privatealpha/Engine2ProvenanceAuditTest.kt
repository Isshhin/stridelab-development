package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Engine2ProvenanceAuditTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private fun workout(id: String, provider: String, rawId: String) =
        Engine2WorkoutEvidence(id, Engine2Discipline.RUNNING, now.minusSeconds(60),
            Engine2RecordStatus.CONFIRMED, listOf(Engine2Measurement(
                Engine2Metric.DISTANCE_KM, 5.0,
                Engine2Source("synthetic-account", provider, rawId,
                    now.minusSeconds(120), now.minusSeconds(30))
            )))
    private fun context(vararg workouts: Engine2WorkoutEvidence) =
        Engine2AthleteContext("synthetic-account", now, workouts.toList(), emptyList())

    @Test fun exactSameProviderRecordAcrossTwoWorkoutsProducesCollision() {
        val a = workout("first", "synthetic-provider-a", "raw-123")
        val b = workout("second", "synthetic-provider-a", "raw-123")
        val report = Engine2ProvenanceAudit.inspect(context(a, b))
        assertFalse(report.hasNoExactProviderIdCollisions)
        assertEquals(1, report.distinctProviderRecords)
        assertEquals(2, report.workoutRecordsWithSource)
        assertEquals(setOf("first", "second"), report.sourceCollisions.single().workoutIds)
        assertEquals("raw-123", report.sourceCollisions.single().key.providerRecordId)
    }

    @Test fun differentProvidersWithSameRawIdRemainDistinctUntilExplicitReview() {
        val a = workout("first", "synthetic-provider-a", "raw-123")
        val b = workout("second", "synthetic-provider-b", "raw-123")
        val report = Engine2ProvenanceAudit.inspect(context(a, b))
        assertEquals(2, report.distinctProviderRecords)
        assertTrue(report.sourceCollisions.isEmpty())
        assertTrue(report.hasNoExactProviderIdCollisions)
        // This does NOT assert they are separate physical activities.
        // It only refuses an unsafe automatic provider-identity merge.
    }

    @Test fun missingProviderMetricsAreUnknownNotZeroDistance() {
        val unknown = Engine2WorkoutEvidence("manual", Engine2Discipline.RUNNING,
            now.minusSeconds(300), Engine2RecordStatus.CONFIRMED)
        val report = Engine2ProvenanceAudit.inspect(context(unknown))
        assertEquals(1, report.runsWithoutProviderMeasurements)
        assertEquals(0, report.distinctProviderRecords)
        assertNull(context(unknown).runningCoverage().knownDistanceKm)
    }

    @Test fun actualInputIsNeverMutatedByProvenanceInspection() {
        val ctx = context(workout("first", "synthetic-provider-a", "raw-123"))
        val same = ctx.copy()
        val a = Engine2ProvenanceAudit.inspect(ctx)
        assertEquals(ctx, same)
        assertTrue(a.hasNoExactProviderIdCollisions)
        assertEquals(1, a.distinctProviderRecords)
    }
}
