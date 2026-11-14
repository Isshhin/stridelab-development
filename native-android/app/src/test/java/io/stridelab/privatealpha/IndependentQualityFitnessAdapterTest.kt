package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IndependentQualityFitnessAdapterTest {
    private fun input(
        observedMinutes: Int? = 12,
        ceilingMinutes: Int? = 30,
        additionalMinutes: Int = 0,
        sessionMinutes: Int = 60,
        authorized: Boolean = true,
        hold: Boolean = false
    ) = IndependentQualityFitnessInput(
        confirmedRecentWorkSeconds = observedMinutes?.times(60),
        approvedWorkCeilingSeconds = ceilingMinutes?.times(60),
        approvedIncreaseSeconds = additionalMinutes * 60,
        availableSessionSeconds = sessionMinutes * 60,
        qualityAuthorized = authorized,
        recoveryOrReturnHold = hold
    )

    private fun candidate(
        family: IndependentQualityFamily, profile: IndependentQualityFitnessInput
    ): IndependentQualityFitnessDecision.Candidate {
        val outcome = IndependentQualityFitnessAdapter.generate(family, profile)
        assertTrue(outcome.toString(), outcome is IndependentQualityFitnessDecision.Candidate)
        return outcome as IndependentQualityFitnessDecision.Candidate
    }

    private fun assertDeferred(
        expected: String, family: IndependentQualityFamily,
        profile: IndependentQualityFitnessInput
    ) {
        val decision = IndependentQualityFitnessAdapter.generate(family, profile)
        assertTrue(decision.toString(), decision is IndependentQualityFitnessDecision.Deferred)
        assertEquals(expected, (decision as IndependentQualityFitnessDecision.Deferred).ruleId)
    }

    @Test fun confirmedExposureChangesThresholdRepetitionsAndWorkDuration() {
        val intro = candidate(IndependentQualityFamily.THRESHOLD, input(observedMinutes = 12))
        val developed = candidate(IndependentQualityFamily.THRESHOLD, input(observedMinutes = 20))
        val first = (intro.workout.elements[1] as IndependentWorkoutElement.Intervals).value
        val second = (developed.workout.elements[1] as IndependentWorkoutElement.Intervals).value
        assertEquals(3, first.repetitions)
        assertEquals(240.0, first.work.amount, 0.0)
        assertEquals(720, intro.workSeconds)
        assertEquals(240, intro.recoverySeconds)
        assertEquals(2040, intro.totalSeconds)
        assertEquals(4, second.repetitions)
        assertEquals(300.0, second.work.amount, 0.0)
        assertEquals(1200, developed.workSeconds)
        assertEquals(360, developed.recoverySeconds)
        assertEquals(2760, developed.totalSeconds)
    }

    @Test fun tempoChangesContinuousDoseWithConfirmedFitnessExposure() {
        val low = candidate(IndependentQualityFamily.TEMPO, input(observedMinutes = 12))
        val high = candidate(IndependentQualityFamily.TEMPO, input(observedMinutes = 20))
        assertEquals(720, low.workSeconds)
        assertEquals(1800, low.totalSeconds)
        assertEquals(1200, high.workSeconds)
        assertEquals(2400, high.totalSeconds)
        assertEquals(0, low.recoverySeconds)
        assertTrue(high.workout.elements[1] is IndependentWorkoutElement.Step)
    }

    @Test fun explicitAllowanceCanIncreaseDoseButCannotExceedIndependentCeiling() {
        val held = candidate(IndependentQualityFamily.TEMPO, input(observedMinutes = 12, ceilingMinutes = 14))
        val increment = candidate(IndependentQualityFamily.TEMPO,
            input(observedMinutes = 12, ceilingMinutes = 14, additionalMinutes = 10))
        assertEquals(720, held.workSeconds)
        assertEquals(840, increment.workSeconds)
        assertTrue("EXPLICIT_INCREMENT_ALLOWANCE" in increment.ruleIds)
    }

    @Test fun tightSessionTimeSelectsSmallerValidWorkoutOrDefers() {
        val constrained = candidate(IndependentQualityFamily.THRESHOLD,
            input(observedMinutes = 20, sessionMinutes = 34))
        assertTrue(constrained.totalSeconds <= 34 * 60)
        assertTrue(constrained.workSeconds <= 20 * 60)
        assertDeferred("DOSE_OR_TIME_CONFLICT", IndependentQualityFamily.THRESHOLD,
            input(observedMinutes = 20, sessionMinutes = 20))
    }

    @Test fun unknownExposureAndCeilingDoNotTriggerInventedFitness() {
        assertDeferred("QUALITY_EXPOSURE_UNKNOWN", IndependentQualityFamily.THRESHOLD,
            input(observedMinutes = null))
        assertDeferred("WORK_CEILING_UNKNOWN", IndependentQualityFamily.TEMPO,
            input(ceilingMinutes = null))
        assertDeferred("NO_CONFIRMED_QUALITY_DOSE", IndependentQualityFamily.TEMPO,
            input(observedMinutes = 0))
    }

    @Test fun authorizationAndRecoveryHoldOverrideHighExposure() {
        assertDeferred("QUALITY_NOT_AUTHORIZED", IndependentQualityFamily.THRESHOLD,
            input(observedMinutes = 40, authorized = false))
        assertDeferred("RECOVERY_HOLD", IndependentQualityFamily.TEMPO,
            input(observedMinutes = 40, hold = true))
    }

    @Test fun generatedWorkoutsRemainVersionedAndArithmeticIsExact() {
        for (family in IndependentQualityFamily.entries) {
            for (observed in listOf(8, 12, 18, 20, 24, 30, 40)) {
                val output = candidate(family, input(observedMinutes = observed,
                    ceilingMinutes = 40, sessionMinutes = 120))
                val replay = IndependentWorkoutComposition.decode(
                    JSONObject(output.workout.encode().toString()))
                assertEquals(output.workout, replay)
                assertEquals(output.workSeconds.toDouble(),
                    replay.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)!!, 0.0)
                assertEquals(output.totalSeconds.toDouble(),
                    replay.total(WorkoutUnit.SECONDS)!!, 0.0)
                assertNull(replay.total(WorkoutUnit.METERS))
                assertTrue(output.workSeconds <= observed * 60)
                assertTrue(output.totalSeconds <= 120 * 60)
            }
        }
    }

    @Test fun invalidFitnessContractsRejectImpossibleInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            input(observedMinutes = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            input(additionalMinutes = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            input(sessionMinutes = -1)
        }
    }
}
