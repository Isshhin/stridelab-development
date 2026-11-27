package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Milestone2UiAnalyticsTest {
    @Test
    fun strengthAnalyticsComputesPrAndWeeklyMuscleSetsFromFreeform() {
        val monday = LocalDate.of(2026, 11, 23)
        val workout = FreeformWorkout(
            id = "w1",
            owner = "athlete",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = monday.plusDays(1).toString(),
            startedAt = 1_700_000_000_000L,
            modifiedAt = 1_700_000_010_000L,
            completedAt = 1_700_000_010_000L,
            exercises = listOf(
                FreeformExercise(
                    name = "Barbell back squat",
                    sets = listOf(
                        FreeformSet(
                            actual = FreeformMeasure(quantity = "5", kg = "100"),
                            completed = true
                        ),
                        FreeformSet(
                            actual = FreeformMeasure(quantity = "5", kg = "100"),
                            completed = true
                        )
                    )
                )
            )
        )
        val summary = StrengthAnalyticsProjector.summarize(
            plans = emptyList(),
            freeformWorkouts = listOf(workout),
            catalog = FreeformExerciseCatalog.builtinRecords(),
            ownerId = "athlete",
            weekStart = monday,
            today = monday.plusDays(2)
        )
        assertEquals(2, summary.totalCompletedSets)
        assertEquals(1000.0, summary.totalVolumeKg, 0.1)
        assertEquals(1, summary.topPrs.size)
        assertEquals(100.0, summary.topPrs.first().bestWeightKg, 0.01)
    }

    @Test
    fun calisthenicsSkillJourneyMarksMasteredNodesAndUnlocksDependents() {
        val workout = FreeformWorkout(
            id = "cal1",
            owner = "athlete",
            discipline = ManualDiscipline.CALISTHENICS,
            performedOn = "2026-11-24",
            startedAt = 1_700_000_000_000L,
            modifiedAt = 1_700_000_010_000L,
            completedAt = 1_700_000_010_000L,
            exercises = listOf(
                FreeformExercise(
                    name = "Wall or elevated push-up",
                    catalogId = "wall-push",
                    sets = listOf(
                        FreeformSet(
                            actual = FreeformMeasure(quantity = "15"),
                            completed = true
                        )
                    )
                )
            )
        )
        val journeys = CalisthenicsSkillJourneyProjector.buildJourneys(
            plans = emptyList(),
            freeformWorkouts = listOf(workout),
            ownerId = "athlete"
        )
        val pushJourney = journeys.first { it.domain == CalisthenicsDomain.PUSH_UP }
        val wallPush = pushJourney.nodes.first { it.node.exercise.id == "wall-push" }
        val floorPush = pushJourney.nodes.first { it.node.exercise.id == "e4" }
        assertEquals(SkillNodeStatus.MASTERED, wallPush.status)
        assertEquals(SkillNodeStatus.UNLOCKED, floorPush.status)
    }

    @Test
    fun hybridInsightsComputesDistributionAndDetectsBackToBackLowerAndQualityRun() {
        val monday = LocalDate.of(2026, 11, 23)
        val plan = PlanSnapshot(
            id = "p1",
            owner = "athlete",
            status = "approved",
            createdAt = Instant.EPOCH,
            revision = 1,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
            explanations = emptyList(),
            request = PlanRequest(),
            sessions = listOf(
                PlannedSession(
                    id = "s-str",
                    date = monday,
                    discipline = "str",
                    title = "Lower Strength",
                    minutes = 45,
                    distanceKm = null,
                    kind = "strength",
                    lower = true
                ),
                PlannedSession(
                    id = "s-run",
                    date = monday.plusDays(1),
                    discipline = "run",
                    title = "Threshold Run",
                    minutes = 50,
                    distanceKm = 9.0,
                    kind = "threshold",
                    quality = true
                )
            )
        )
        val slices = HybridTrainingInsights.weeklyDistribution(listOf(plan), monday)
        assertEquals(2, slices.size)
        val insights = HybridTrainingInsights.detectInterference(listOf(plan), monday)
        assertTrue(insights.isNotEmpty())
    }
}
