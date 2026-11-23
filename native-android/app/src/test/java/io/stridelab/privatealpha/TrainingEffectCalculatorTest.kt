package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TrainingEffectCalculatorTest {
    private val zones = ExecutionHrZoneProfile(95, 120, 145, 160, 180, 195)
    private fun run(seconds: Int) = ActivityRecord("run", "Run", "run", "garmin",
        Instant.EPOCH, null, seconds.toDouble(), seconds.toDouble(), null, 140.0, null)

    private fun recording(seconds: Int, hr: (Int) -> Double?, pace: (Int) -> Double? = { 390.0 }) =
        ActivityDetail("run", (0..seconds step 5).mapIndexed { i, t ->
            ActivitySample(i, t.toDouble(), pace(t), hr(t), null, null, null, 180.0)
        }, emptyList(), 1)

    @Test fun aerobicNeedsVerifiedZonesAndSufficientRecordedHr() {
        val noHr = recording(1800, { null })
        val sparseHr = recording(1800, { if (it < 100) 133.0 else null })
        assertNull(TrainingEffectCalculator.calculate(run(1800), noHr, zones).aerobic)
        assertNull(TrainingEffectCalculator.calculate(run(1800), sparseHr, zones).aerobic)
        assertNull(TrainingEffectCalculator.calculate(run(1800), recording(1800, { 133.0 })).aerobic)
        assertEquals(TrainingEffect(null, null), TrainingEffectCalculator.calculate(run(1800), null, zones))
        assertTrue(TrainingEffectCalculator.calculate(run(1800), recording(1800, { 133.0 }), zones).aerobic!! > 0)
    }

    @Test fun steadyHighIntensityIsNotASurgeAndRepeatedBurstsScoreWithinBounds() {
        val constant = TrainingEffectCalculator.calculate(run(10_800), recording(10_800, { 190.0 }), zones)
        assertEquals(5.0, constant.aerobic!!, 0.0)
        assertEquals(0.0, constant.anaerobic!!, 0.0)
        val bursts = recording(3600, { null }) { if (it % 180 in 60..110) 265.0 else 430.0 }
        val result = TrainingEffectCalculator.calculate(run(3600), bursts, zones)
        assertNull(result.aerobic)
        assertTrue(result.anaerobic!! in 1.0..5.0)
        assertEquals("Highly Improving", TrainingEffectCalculator.label(4.9))
        assertEquals("Overreaching", TrainingEffectCalculator.label(5.0))
    }

    @Test fun strideAndRatioEstimatesUseMovingPaceCadenceAndRecordedOscillation() {
        val detail = recording(300, { null }, { 333.333 }).let {
            it.copy(samples = it.samples.map { sample ->
                sample.copy(runningDynamics = RunningDynamics(verticalOscillationCm = 8.0))
            })
        }
        val result = requireNotNull(summarizeRunningDynamics(detail, runningMovingStream(detail)))
        assertEquals(1.0, result.values.stepLengthM!!, .001)
        assertEquals(8.0, result.values.verticalRatioPct!!, .001)
        assertTrue(result.estimatedStepLength && result.estimatedVerticalRatio)
        assertNull(result.values.groundContactMs)
    }

    @Test fun verifiedShortZoneFourAndFiveBoutsCanScoreWithoutPace() {
        val detail = recording(1200, { if (it % 180 in 60..110) 181.0 else 135.0 }, { null })
        val result = TrainingEffectCalculator.calculate(run(1200), detail, zones)
        assertTrue(result.anaerobic!! > 0)
        assertNull(TrainingEffectCalculator.calculate(run(1200), detail).anaerobic)
    }
}
