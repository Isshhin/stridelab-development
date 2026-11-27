package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UiRegressionFixesTest {

    @Test
    fun displayEffort_preservesPaceTargetsWhenHrBoundsPresent() {
        val seg = RunningSegment(
            purpose = "WORK",
            meters = 8000,
            effort = "Threshold effort",
            stepIntent = RunningStepIntent.THRESHOLD,
            targetPaceMinSecPerKm = 270,
            targetPaceMaxSecPerKm = 280,
            targetHrMinBpm = 165,
            targetHrMaxBpm = 175
        )
        val session = PlannedSession(
            id = "s-1",
            date = LocalDate.of(2026, 11, 27),
            discipline = "run",
            title = "Threshold Run",
            minutes = 45,
            distanceKm = 8.0,
            kind = "threshold",
            quality = true,
            runningPrescription = RunningPrescription(
                role = "QUALITY",
                family = "Threshold",
                segments = listOf(seg)
            )
        )
        val items = buildWorkoutStepItems(
            session = session,
            segments = listOf(seg),
            mixedSegments = null,
            textFallback = emptyList(),
            planningPaceSecPerKm = 300.0
        )
        val detail = (items.single() as WorkoutStepItem.SingleStep).step
        assertNotNull(detail.effortLine)
        assertTrue("Expected pace to remain visible alongside HR: ${detail.effortLine}", detail.effortLine!!.contains("/km"))
        assertTrue("Expected HR bounds to be visible: ${detail.effortLine}", detail.effortLine!!.contains("165–175 bpm"))
    }

    @Test
    fun workoutVisualBlocks_showsHrHintForHrTargetedSegmentsWithoutPace() {
        val seg = RunningSegment(
            purpose = "EASY",
            meters = 6000,
            effort = "Aerobic HR",
            stepIntent = RunningStepIntent.AEROBIC_EASY,
            targetHrMinBpm = 135,
            targetHrMaxBpm = 148
        )
        val session = PlannedSession(
            id = "s-2",
            date = LocalDate.of(2026, 11, 27),
            discipline = "run",
            title = "Easy HR Run",
            minutes = 40,
            distanceKm = 6.0,
            kind = "easy",
            quality = false,
            runningPrescription = RunningPrescription(
                role = "EASY",
                family = null,
                segments = listOf(seg)
            )
        )
        val blocks = WorkoutVisualBlocks.fromSession(session)
        assertEquals("135-148 bpm", blocks.single().paceHint)
    }

    @Test
    fun formatWorkoutAvgPace_usesAsciiDashPlaceholderWithoutEmDash() {
        assertEquals("--", formatWorkoutAvgPace(null, 30))
        assertFalse(formatWorkoutAvgPace(null, 30).contains("—"))
    }
}
