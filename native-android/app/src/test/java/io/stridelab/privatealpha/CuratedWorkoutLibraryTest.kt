package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CuratedWorkoutLibraryTest {
    @Test
    fun libraryContains36UniqueWorkoutsAcrossAll8CategoriesWithNoEmDashes() {
        val all = CuratedWorkoutLibrary.all
        assertEquals(36, all.size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(WorkoutHubCategory.entries.toSet(), all.map { it.category }.toSet())
        all.forEach { workout ->
            assertTrue("${workout.id} must have >= 2 steps", workout.steps.size >= 2)
            assertFalse("${workout.id} title contains em-dash", "\u2014" in workout.title)
            assertFalse("${workout.id} subtitle contains em-dash", "\u2014" in workout.subtitle)
            assertFalse("${workout.id} notes contain em-dash", "\u2014" in workout.coachingNotes)
        }
    }

    @Test
    fun everyCuratedWorkoutEncodesToValidTemplateAndFreeformWorkout() {
        val owner = "test-athlete"
        val date = LocalDate.of(2026, 11, 28)
        CuratedWorkoutLibrary.all.forEach { workout ->
            val template = workout.toManualTemplate(owner)
            val encodedTemplate = ManualWorkoutTemplateCodec.encode(template)
            assertTrue(encodedTemplate.isNotBlank())

            val freeform = workout.toFreeformWorkout(owner, date, 1_700_000_000_000L)
            val encodedFreeform = FreeformCodec.encode(freeform)
            assertTrue(encodedFreeform.isNotBlank())

            val finished = workout.toGuidedCompletionDraft(
                owner = owner,
                date = date,
                now = 1_700_000_000_000L,
                elapsedMinutes = workout.durationMinutes
            ).finish(1_700_000_005_000L)
            assertTrue(finished.isComplete)
            assertTrue(FreeformCodec.encode(finished).isNotBlank())
        }
    }
}
