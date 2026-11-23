package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDetailPresentationTest {
    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun steadyStateWorkoutBuildsSingleStepBarAndFormattedAvgPace() {
        val easy = PlannedSession(
            id = "steady-1",
            date = monday,
            discipline = "run",
            title = "Easy Base Run",
            minutes = 45,
            distanceKm = 7.5,
            kind = "easy",
            runningPrescription = RunningPrescription(
                role = "EASY",
                family = null,
                segments = listOf(RunningSegment("EASY", 7500, "Conversational pace"))
            )
        )

        val items = buildWorkoutStepItems(
            session = easy,
            segments = easy.runningPrescription?.segments,
            mixedSegments = null,
            textFallback = easy.steps,
            planningPaceSecPerKm = 360.0
        )
        assertEquals(1, items.size)
        val single = items.single() as WorkoutStepItem.SingleStep
        assertEquals("Easy running", single.step.title)
        assertEquals("Z1-Z2 · Easy Aerobic", single.step.zoneBadge)
        assertEquals("7.5 km", single.step.metricLine)

        val bars = flattenProfileBars(items)
        assertEquals(1, bars.size)
        assertEquals(0, bars.single().stepIndex)
        assertEquals("~6:00/km", formatWorkoutAvgPace(easy.distanceKm, easy.minutes))
    }

    @Test
    fun multiRepIntervalWorkoutFlattensProfileBarsWithSyncedParentIndices() {
        val mixed = listOf(
            RunningStepV2("WARM_UP", "Easy warm-up", seconds = 1500, distanceEquivalentMeters = 3500),
            RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
            RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
            RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
            RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
            RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
            RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
            RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
            RunningStepV2("COOL_DOWN", "Easy warm-down", seconds = 1500, distanceEquivalentMeters = 3500)
        )
        val session = PlannedSession(
            id = "thresh-1",
            date = monday.plusDays(3),
            discipline = "run",
            title = "Threshold Workout",
            minutes = 80,
            distanceKm = 11.36,
            kind = "quality",
            quality = true,
            runningPrescription = RunningPrescription(
                role = "QUALITY",
                family = "Threshold",
                segments = emptyList(),
                mixedSegments = mixed,
                reconciledDistanceMeters = 11360
            )
        )

        val items = buildWorkoutStepItems(
            session = session,
            segments = null,
            mixedSegments = mixed,
            textFallback = session.steps
        )
        assertEquals(3, items.size)
        assertTrue(items[0] is WorkoutStepItem.SingleStep)
        val group = items[1] as WorkoutStepItem.RepeatGroup
        assertEquals(4, group.reps)
        assertEquals("4 × Interval Set", group.title)
        assertEquals("4 REPS", group.badge)
        assertEquals("5 min · 0.88 km", group.children[0].metricLine)
        assertEquals("2 min · 0.28 km", group.children[1].metricLine)
        assertTrue(items[2] is WorkoutStepItem.SingleStep)

        // 1 Warm-up + (4 Work + 3 Recover) + 1 Cool-down = 9 flattened bars
        val bars = flattenProfileBars(items)
        assertEquals(9, bars.size)
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 1, 1, 2), bars.map { it.stepIndex })
    }

    @Test
    fun difficultyAdjustmentsUpdateMetricsDeltaSummaryBarsAndRepeatRepsCleanly() {
        val req = PlanRequest(goal = "Half marathon", start = monday, planningPace = 400.0, timeBudgetEnabled = false)
        val mixed = listOf(
            RunningStepV2("WARM_UP", "Easy", seconds = 1200, distanceEquivalentMeters = 3000),
            RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1000),
            RunningStepV2("RECOVERY", "Jog", seconds = 120, distanceEquivalentMeters = 300),
            RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1000),
            RunningStepV2("RECOVERY", "Jog", seconds = 120, distanceEquivalentMeters = 300),
            RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1000),
            RunningStepV2("RECOVERY", "Jog", seconds = 120, distanceEquivalentMeters = 300),
            RunningStepV2("WORK", "Threshold", seconds = 300, distanceEquivalentMeters = 1000),
            RunningStepV2("COOL_DOWN", "Easy", seconds = 1200, distanceEquivalentMeters = 3000)
        )
        val base = PlannedSession(
            id = "thresh-adj",
            date = monday.plusDays(3),
            discipline = "run",
            title = "Threshold Workout",
            minutes = 76,
            distanceKm = 10.9,
            kind = "quality",
            quality = true,
            runningPrescription = RunningPrescription(
                role = "QUALITY",
                family = "Threshold",
                segments = emptyList(),
                mixedSegments = mixed,
                reconciledDistanceMeters = 10900
            )
        )

        val harder = Engine15WorkoutDifficultyAdjuster.adjust(base, req, +1)
        val harderSummary = Engine15WorkoutDifficultyAdjuster.changeSummary(harder)
        assertNotNull(harderSummary)
        assertTrue(harderSummary!!.contains("4 → 5 reps"))
        val harderItems = buildWorkoutStepItems(harder, null, harder.runningPrescription?.mixedSegments, harder.steps)
        val harderGroup = harderItems[1] as WorkoutStepItem.RepeatGroup
        assertEquals(5, harderGroup.reps)
        assertEquals(11, flattenProfileBars(harderItems).size) // 1 + (5 + 4) + 1
        assertTrue(harder.distanceKm!! > base.distanceKm!!)
        assertTrue(formatWorkoutAvgPace(harder.distanceKm, harder.minutes).startsWith("~"))

        val easier = Engine15WorkoutDifficultyAdjuster.adjust(base, req, -1)
        val easierItems = buildWorkoutStepItems(easier, null, easier.runningPrescription?.mixedSegments, easier.steps)
        assertEquals(3, (easierItems[1] as WorkoutStepItem.RepeatGroup).reps)
        assertEquals(7, flattenProfileBars(easierItems).size) // 1 + (3 + 2) + 1

        val reset = Engine15WorkoutDifficultyAdjuster.adjust(harder, req, 0)
        assertEquals(base, reset)
    }
}
