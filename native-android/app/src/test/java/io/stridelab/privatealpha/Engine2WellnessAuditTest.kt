package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Engine2WellnessAuditTest {
    private val asOf = Instant.parse("2026-10-27T12:00:00Z")
    private fun observation(metric: Engine2Metric, daysAgo: Long, value: Double, id: String) =
        Engine2WellnessEvidence(id, Engine2Measurement(metric, value,
            Engine2Source("athlete-a", "synthetic-provider", id,
                asOf.minusSeconds(daysAgo * 86400), asOf)))
    private fun context(vararg observations: Engine2WellnessEvidence) =
        Engine2AthleteContext("athlete-a", asOf, emptyList(), observations.toList())

    @Test fun missingWellnessIsUncertaintyNotGoodRecovery() {
        val result = Engine2WellnessAudit.inspect(context())
        assertEquals(14L, result.windowDays)
        assertEquals(3, result.missingMetrics.size)
        assertFalse(result.hasEnoughInformationForReadinessClassification)
        assertTrue(result.metricCoverage.values.all {
            it.count == 0 && it.latestObservedAt == null && !it.presentInWindow
        })
    }

    @Test fun oldValuesAreNotMistakenForCurrentRecoveryReadings() {
        val result = Engine2WellnessAudit.inspect(context(
            observation(Engine2Metric.HRV_MS, 20, 55.0, "old-hrv"),
            observation(Engine2Metric.RESTING_HR_BPM, 1, 52.0, "current-resting")
        ), 7)
        assertEquals(1, result.metricCoverage.getValue(Engine2Metric.RESTING_HR_BPM).count)
        assertTrue(Engine2Metric.HRV_MS in result.missingMetrics)
        assertTrue(Engine2Metric.SLEEP_SECONDS in result.missingMetrics)
        assertFalse(result.hasEnoughInformationForReadinessClassification)
    }

    @Test fun completeMeasurementCoverageStillDoesNotAssertReadinessOrDiagnosis() {
        val readings = context(
            observation(Engine2Metric.HRV_MS, 1, 55.0, "hrv"),
            observation(Engine2Metric.RESTING_HR_BPM, 0, 60.0, "rhr"),
            observation(Engine2Metric.SLEEP_SECONDS, 0, 24000.0, "sleep")
        )
        val result = Engine2WellnessAudit.inspect(readings)
        assertTrue(result.missingMetrics.isEmpty())
        assertFalse(result.hasEnoughInformationForReadinessClassification)
        assertEquals(1, result.metricCoverage.getValue(Engine2Metric.HRV_MS).count)
        assertEquals(readings, context(*readings.wellness.toTypedArray()))
    }

    @Test fun invalidLookbackOrCrossAccountWellnessFailsClosed() {
        val p = context()
        for (invalid in listOf(0L, 366L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) {
                Engine2WellnessAudit.inspect(p, invalid)
            }
        }
        val other = Engine2WellnessEvidence("other",
            Engine2Measurement(Engine2Metric.HRV_MS, 50.0,
                Engine2Source("athlete-b", "synthetic-source", "other",
                    asOf.minusSeconds(60), asOf)))
        assertThrows(IllegalArgumentException::class.java) { context(other) }
    }
}
