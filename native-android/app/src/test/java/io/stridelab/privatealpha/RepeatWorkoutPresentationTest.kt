package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RepeatWorkoutPresentationTest {
    private val stride = RunningStepV2("TRANSITION", "Smooth stride", seconds = 20,
        stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 240, targetPaceMaxSecPerKm = 270)
    private val recover = RunningStepV2("RECOVERY", "Easy jog", seconds = 60)

    @Test fun stridesAndRecoveriesGroupWithoutChangingStepsOrGarminBytes() {
        val steps = listOf(RunningStepV2("EASY", "Easy running", meters = 5_000)) +
            List(3) { listOf(stride, recover) }.flatten() + stride
        val targets = RunningPrescription("EASY", null, emptyList(), mixedSegments = steps,
            estimatedDistanceKm = 5.8)
        val session = PlannedSession("run", LocalDate.of(2026, 11, 23), "run", "Easy Run + Strides",
            40, null, "easy", runningPrescription = targets)
        val before = GarminFitWorkoutEncoder.encode(session.title, targets, Instant.EPOCH.plusSeconds(1_200_000_000))
        val rendered = buildWorkoutStepItems(session, null, steps, emptyList())
        val repeat = rendered.filterIsInstance<WorkoutStepItem.RepeatGroup>().single()
        assertEquals(4, repeat.reps)
        assertFalse(repeat.recoveryAfterLast)
        assertEquals(listOf("Stride", "Recover"), repeat.children.map { it.title })
        assertTrue(repeat.children.first().effortLine.orEmpty().contains("4:00–4:30 /km"))
        assertEquals(8, flattenProfileBars(rendered).size)
        assertEquals(steps, session.runningPrescription?.mixedSegments)
        assertArrayEquals(before, GarminFitWorkoutEncoder.encode(session.title, targets, Instant.EPOCH.plusSeconds(1_200_000_000)))
    }

    @Test fun separatePaceTargetsDoNotMergeIntoAnIdenticalRepeat() {
        val steps = listOf(stride, recover, stride.copy(targetPaceMinSecPerKm = 245), recover)
        val session = PlannedSession("run", LocalDate.of(2026, 11, 23), "run", "Strides", 20, null, "easy")
        val rendered = buildWorkoutStepItems(session, null, steps, emptyList())
        assertEquals(4, rendered.size)
        assertTrue(rendered.all { it is WorkoutStepItem.SingleStep })
    }
}
