package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IndependentWorkoutCompositionTest {
    private fun step(purpose: WorkoutPurpose, unit: WorkoutUnit, amount: Double, target: WorkoutTarget) =
        WorkoutStep(purpose, unit, amount, 1, target)

    @Test fun fullWorkoutPreservesOrderAndSeparateWorkAndRecoveryDose() {
        val warm = step(WorkoutPurpose.WARM_UP, WorkoutUnit.METERS, 2000.0, WorkoutTarget.EASY)
        val work = step(WorkoutPurpose.WORK, WorkoutUnit.METERS, 1000.0, WorkoutTarget.THRESHOLD)
        val recovery = step(WorkoutPurpose.RECOVERY, WorkoutUnit.METERS, 200.0, WorkoutTarget.EASY)
        val cool = step(WorkoutPurpose.COOL_DOWN, WorkoutUnit.METERS, 1000.0, WorkoutTarget.EASY)
        val workout = IndependentWorkoutComposition(listOf(IndependentWorkoutElement.Step(warm),
            IndependentWorkoutElement.Intervals(IndependentIntervalGroup(3, work, recovery)), IndependentWorkoutElement.Step(cool)))
        assertEquals(6400.0, workout.total(WorkoutUnit.METERS)!!, 0.0)
        assertEquals(3000.0, workout.dose(WorkoutUnit.METERS, WorkoutPurpose.WORK)!!, 0.0)
        assertEquals(400.0, workout.dose(WorkoutUnit.METERS, WorkoutPurpose.RECOVERY)!!, 0.0)
        assertEquals(workout, IndependentWorkoutComposition.decode(JSONObject(workout.encode().toString())))
    }

    @Test fun mixedUnitsLeaveTotalUnknownButPreserveKnownPurposeDoses() {
        val work = step(WorkoutPurpose.WORK, WorkoutUnit.METERS, 1000.0, WorkoutTarget.TEMPO)
        val recovery = step(WorkoutPurpose.RECOVERY, WorkoutUnit.SECONDS, 90.0, WorkoutTarget.EASY)
        val workout = IndependentWorkoutComposition(listOf(IndependentWorkoutElement.Intervals(IndependentIntervalGroup(3, work, recovery))))
        assertNull(workout.total(WorkoutUnit.METERS))
        assertEquals(3000.0, workout.dose(WorkoutUnit.METERS, WorkoutPurpose.WORK)!!, 0.0)
        assertEquals(180.0, workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.RECOVERY)!!, 0.0)
        assertNull(workout.dose(WorkoutUnit.METERS, WorkoutPurpose.RECOVERY))
    }

    @Test fun unsupportedVersionsAndElementKindsAreRejected() {
        val workout = IndependentWorkoutComposition(listOf(IndependentWorkoutElement.Step(
            step(WorkoutPurpose.WORK, WorkoutUnit.SECONDS, 30.0, WorkoutTarget.STRIDES))))
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkoutComposition.decode(workout.encode().put("version", 2)) }
        val bad = workout.encode()
        bad.getJSONArray("elements").getJSONObject(0).put("kind", "future")
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkoutComposition.decode(bad) }
    }
}
