package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** M2 isolated examples: no generation, scheduling, athlete-capacity or Android UI calls. */
class IndependentQualityTemplatesTest {
    private fun get(family: IndependentQualityFamily, stage: IndependentQualityStage) =
        IndependentQualityTemplates.get(family, stage)

    @Test fun thresholdIntroHasThreeEffortsAndOnlyTwoRecoveries() {
        val template = get(IndependentQualityFamily.THRESHOLD, IndependentQualityStage.INTRO)
        assertEquals("engine15-threshold-intro-v1", template.id)
        assertEquals(2040.0, template.totalSeconds(), 0.0) // 10 + 3×4 + 2×2 + 8 min
        assertEquals(720.0, template.workSeconds(), 0.0)
        assertEquals(240.0, template.recoverySeconds(), 0.0)
        val group = (template.workout.elements[1] as IndependentWorkoutElement.Intervals).value
        assertEquals(3, group.repetitions)
        assertEquals(WorkoutTarget.THRESHOLD, group.work.target)
    }

    @Test fun tempoIntroIsContinuousAndHasNoIntervalRecovery() {
        val template = get(IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO)
        assertEquals("engine15-tempo-intro-v1", template.id)
        assertEquals(1800.0, template.totalSeconds(), 0.0) // 10 + 12 + 8 min
        assertEquals(720.0, template.workSeconds(), 0.0)
        assertEquals(0.0, template.recoverySeconds(), 0.0)
        assertTrue(template.workout.elements[1] is IndependentWorkoutElement.Step)
    }

    @Test fun extendedExamplesHaveDistinctHigherWorkDoseWithoutAutoSelection() {
        val threshold = get(IndependentQualityFamily.THRESHOLD, IndependentQualityStage.EXTENDED)
        val tempo = get(IndependentQualityFamily.TEMPO, IndependentQualityStage.EXTENDED)
        assertEquals(2760.0, threshold.totalSeconds(), 0.0) // 10 + 4×5 + 3×2 + 10
        assertEquals(1200.0, threshold.workSeconds(), 0.0)
        assertEquals(360.0, threshold.recoverySeconds(), 0.0)
        assertEquals(2400.0, tempo.totalSeconds(), 0.0) // 10 + 20 + 10
        assertEquals(1200.0, tempo.workSeconds(), 0.0)
        assertEquals(0.0, tempo.recoverySeconds(), 0.0)
        assertTrue(threshold.workSeconds() > get(IndependentQualityFamily.THRESHOLD, IndependentQualityStage.INTRO).workSeconds())
        assertTrue(tempo.workSeconds() > get(IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO).workSeconds())
    }

    @Test fun threeByThreeKilometerThresholdUsesTwoNinetySecondRecoveries() {
        val template = IndependentQualityTemplates.all().singleOrNull {
            it.id == "engine15-threshold-long_repetitions-v1"
        }
        assertNotNull("Versioned 3×3 km research preview is missing", template)
        val group = (template!!.workout.elements[1] as IndependentWorkoutElement.Intervals).value
        assertEquals(3, group.repetitions)
        assertEquals(WorkoutUnit.METERS, group.work.unit)
        assertEquals(3000.0, group.work.amount, 0.0)
        assertEquals(WorkoutTarget.THRESHOLD, group.work.target)
        assertEquals(WorkoutUnit.SECONDS, group.recovery.unit)
        assertEquals(90.0, group.recovery.amount, 0.0)
        assertEquals(WorkoutTarget.EASY, group.recovery.target)
        assertEquals(2000.0,
            (template.workout.elements.first() as IndependentWorkoutElement.Step).value.amount, 0.0)
        assertEquals(2000.0,
            (template.workout.elements.last() as IndependentWorkoutElement.Step).value.amount, 0.0)
        assertNull("Mixed-unit recovery distance must remain unknown",
            template.workout.total(WorkoutUnit.METERS))
        assertEquals("HEURISTIC", template.evidence)
    }

    @Test fun allExamplesAreUniqueVersionedAndRoundTripWithoutUnitConversion() {
        val all = IndependentQualityTemplates.all()
        assertEquals(5, all.size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(all.size, all.map { it.family to it.stage }.distinct().size)
        for (template in all) {
            assertEquals("HEURISTIC", template.evidence)
            assertNull(template.workout.total(WorkoutUnit.METERS))
            val restored = IndependentWorkoutComposition.decode(JSONObject(template.workout.encode().toString()))
            assertEquals(template.workout, restored)
            if (template.stage == IndependentQualityStage.LONG_REPETITIONS) {
                assertNull(restored.total(WorkoutUnit.SECONDS))
                assertNull(restored.total(WorkoutUnit.METERS))
            } else assertEquals(template.totalSeconds(), restored.total(WorkoutUnit.SECONDS)!!, 0.0)
        }
    }

    @Test fun mismatchedFamilyOrTargetCannotBePresentedAsAnotherQualityTemplate() {
        val tempo = get(IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO)
        assertThrows(IllegalArgumentException::class.java) {
            tempo.copy(family = IndependentQualityFamily.THRESHOLD)
        }
        assertThrows(IllegalArgumentException::class.java) {
            tempo.copy(id = "engine15-tempo-extended-v1")
        }
        val invalidWork = (tempo.workout.elements[1] as IndependentWorkoutElement.Step).value
            .copy(target = WorkoutTarget.EASY)
        val invalidComposition = tempo.workout.copy(elements = listOf(
            tempo.workout.elements.first(), IndependentWorkoutElement.Step(invalidWork),
            tempo.workout.elements.last()
        ))
        assertThrows(IllegalArgumentException::class.java) {
            tempo.copy(workout = invalidComposition)
        }
    }
}
