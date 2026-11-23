package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IndependentWorkoutTest {
    @Test fun versionedStepsRoundTripAndCountRepeatedWork() {
        val workout = IndependentWorkout(listOf(
            WorkoutStep(WorkoutPurpose.WARM_UP, WorkoutUnit.METERS, 2000.0, 1, WorkoutTarget.EASY),
            WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.METERS, 1000.0, 3, WorkoutTarget.THRESHOLD),
            WorkoutStep(WorkoutPurpose.COOL_DOWN, WorkoutUnit.METERS, 1000.0, 1, WorkoutTarget.EASY)
        ))
        assertEquals(6000.0, workout.total(WorkoutUnit.METERS)!!, 0.0)
        assertEquals(workout, IndependentWorkout.decode(JSONObject(workout.encode().toString())))
    }

    @Test fun mixedUnitsDoNotInventTotals() {
        val workout = IndependentWorkout(listOf(
            WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.METERS, 1000.0, 3, WorkoutTarget.TEMPO),
            WorkoutStep(WorkoutPurpose.RECOVERY, WorkoutUnit.SECONDS, 90.0, 2, WorkoutTarget.EASY)
        ))
        assertNull(workout.total(WorkoutUnit.METERS))
        assertNull(workout.total(WorkoutUnit.SECONDS))
    }

    @Test fun invalidAndFutureContractsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.METERS, Double.NaN, 1, WorkoutTarget.EASY) }
        assertThrows(IllegalArgumentException::class.java) { WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.SECONDS, 30.0, 0, WorkoutTarget.EASY) }
        val valid = IndependentWorkout(listOf(WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.SECONDS, 30.0, 1, WorkoutTarget.STRIDES)))
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkout.decode(valid.encode().put("version", 2)) }
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkout.decode(valid.encode().put("version", 1.5)) }
        val fractional = valid.encode()
        fractional.getJSONArray("steps").getJSONObject(0).put("repetitions", 1.5)
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkout.decode(fractional) }
        assertThrows(IllegalArgumentException::class.java) { IndependentWorkout(emptyList()) }
    }
}
