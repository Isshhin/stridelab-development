package io.stridelab.privatealpha

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Milestone3GuidedDemoAndPolishTest {
    @Test
    fun all36CuratedWorkoutsClassifyIntoGuidedMotionPatternsCoveringAll8Patterns() {
        val observed = mutableSetOf<GuidedMotionPattern>()
        CuratedWorkoutLibrary.all.forEach { workout ->
            workout.steps.forEach { step ->
                observed += GuidedMotionClassifier.classify(step.name, workout.category)
            }
        }
        assertTrue(
            "Expected all 8 GuidedMotionPattern variants to be covered, got $observed",
            observed.containsAll(GuidedMotionPattern.entries)
        )
    }

    @Test
    fun allCuratedCollectionsMatchAccurateSubsets() {
        CuratedCollection.entries.forEach { collection ->
            val matches = CuratedWorkoutLibrary.all.filter { collection.matches(it) }
            assertTrue("Collection ${collection.name} should match >= 1 workout", matches.isNotEmpty())
            when (collection) {
                CuratedCollection.UNDER_15 -> assertTrue(matches.all { it.durationMinutes <= 15 })
                CuratedCollection.NO_GEAR -> assertTrue(matches.all { it.equipment == setOf(WorkoutEquipment.NONE) })
                CuratedCollection.RECOVERY_SLEEP -> assertTrue(
                    matches.all { it.category in setOf(WorkoutHubCategory.RECOVERY, WorkoutHubCategory.MOBILITY) }
                )
                CuratedCollection.RUNNER_DURABILITY -> assertTrue(
                    matches.all {
                        it.category in setOf(
                            WorkoutHubCategory.STRENGTH,
                            WorkoutHubCategory.PLYOMETRICS,
                            WorkoutHubCategory.ISOMETRICS
                        )
                    }
                )
            }
        }
    }

    @Test
    fun curatedLibraryAndRecommenderContainZeroEmDashes() {
        CuratedWorkoutLibrary.all.forEach { w ->
            assertFalse("\u2014" in w.title)
            assertFalse("\u2014" in w.subtitle)
            assertFalse("\u2014" in w.coachingNotes)
            w.steps.forEach { step ->
                assertFalse("\u2014" in step.name)
                assertFalse("\u2014" in step.cue)
            }
        }
        val recs = WorkoutHubRecommender.recommend(
            library = CuratedWorkoutLibrary.all,
            readiness = null,
            plannedSessionsToday = emptyList(),
            plannedSessionsTomorrow = emptyList(),
            recentFreeform = emptyList(),
            savedEquipment = emptySet()
        )
        recs.forEach { rec ->
            assertFalse("\u2014" in rec.reason)
        }
    }
}
