package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IndependentIntervalGroupTest {
    private val work = WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.METERS, 1000.0, 1, WorkoutTarget.THRESHOLD)
    private val recovery = WorkoutStep(WorkoutPurpose.RECOVERY, WorkoutUnit.METERS, 200.0, 1, WorkoutTarget.EASY)

    @Test fun threeEffortsHaveOnlyTwoRecoveriesAndRoundTrip() {
        val group = IndependentIntervalGroup(3, work, recovery)
        assertEquals(3400.0, group.total(WorkoutUnit.METERS)!!, 0.0)
        assertEquals(group, IndependentIntervalGroup.decode(JSONObject(group.encode().toString())))
    }

    @Test fun mixedUnitsStayUnknown() {
        val group = IndependentIntervalGroup(3, work, recovery.copy(unit = WorkoutUnit.SECONDS, amount = 90.0))
        assertNull(group.total(WorkoutUnit.METERS))
        assertNull(group.total(WorkoutUnit.SECONDS))
    }

    @Test fun malformedGroupsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { IndependentIntervalGroup(3, work, work) }
        assertThrows(IllegalArgumentException::class.java) { IndependentIntervalGroup(3, work.copy(repetitions = 2), recovery) }
        assertThrows(IllegalArgumentException::class.java) { IndependentIntervalGroup(1, work, recovery) }
        assertThrows(IllegalArgumentException::class.java) { IndependentIntervalGroup.decode(IndependentIntervalGroup(3, work, recovery).encode().put("version", 2)) }
        assertThrows(IllegalArgumentException::class.java) { IndependentIntervalGroup.decode(IndependentIntervalGroup(3, work, recovery).encode().put("repetitions", 3.5)) }
    }
}
