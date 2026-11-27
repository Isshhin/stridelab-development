package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorkoutRoutineTest {
    private val owner = "routine-owner"
    private val date = LocalDate.of(2026, 11, 23)

    @Test fun completedActualsNeverBecomeTemplateTargets() {
        val exercise = FreeformExercise(name = "Squat", sets = listOf(FreeformSet(
            actual = FreeformMeasure(quantity = "10", kg = "75"), target = FreeformMeasure(quantity = "8"),
            restSeconds = "90", rir = "1", targetRir = "3", notes = "Last rep slow", completed = true
        )))
        val workout = FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 100)
            .copy(exercises = listOf(exercise), notes = "Personal history").finish(101)
        val template = ManualWorkoutTemplate.fromWorkout(workout)
        val started = template.startWorkout(date, 102)
        val set = started.exercises.single().sets.single()
        assertEquals(template.id, started.templateId)
        assertEquals("8", set.target.quantity)
        assertEquals("3", set.targetRir)
        assertEquals("90", set.restSeconds)
        assertEquals(FreeformMeasure(), set.actual)
        assertFalse(set.completed)
        assertTrue(set.rir.isEmpty() && set.notes.isEmpty() && started.notes.isEmpty())
        assertNotEquals(exercise.id, started.exercises.single().id)
        assertEquals(workout, FreeformCodec.decode(FreeformCodec.encode(workout), owner))
    }

    @Test fun runStartsWithSeparateTargetsAndBlankActuals() {
        val template = ManualWorkoutTemplate.empty(owner, ManualDiscipline.RUNNING).copy(name = "Tempo")
            .let { it.copy(recipe = WorkoutRecipe(distanceKm = "8.5", durationMinutes = "45", runType = "Tempo")) }
        val start = template.startWorkout(date, 100)
        assertEquals("8.5", start.targetDistanceKm)
        assertEquals("45", start.targetDurationMinutes)
        assertEquals("Tempo", start.runType)
        assertTrue(start.distanceKm.isEmpty() && start.durationMinutes.isEmpty())
        assertEquals(start, FreeformCodec.decode(FreeformCodec.encode(start), owner))
    }

    @Test fun archiveVersionFourPreservesBothTemplateShapes() {
        val legacy = ManualWorkoutTemplate("legacy-routine", owner, "Old squat",
            ManualDiscipline.STRENGTH, listOf(ManualExerciseEntry("Squat", 3, reps = 8)))
        val routine = ManualWorkoutTemplate.empty(owner, ManualDiscipline.ISOMETRICS).copy(
            recipe = WorkoutRecipe(exercises = listOf(FreeformExercise(name = "Wall sit", sets = listOf(
                FreeformSet(target = FreeformMeasure(seconds = "45"), restSeconds = "30")
            ))))
        )
        val archive = ManualDisciplineArchive.export(owner, emptyList(), listOf(legacy, routine))
        val json = JSONObject(JSONObject(archive).getString("data"))
        assertEquals(4, json.getInt("schema"))
        assertEquals(listOf(legacy, routine), ManualDisciplineArchive.import(archive, owner).templates)
        assertEquals(listOf("8", "8", "8"), legacy.structure.exercises.single().sets.map { it.target.quantity })
        val legacyArchive = ManualDisciplineArchive.export(owner, emptyList(), listOf(legacy))
        assertEquals(listOf(legacy), ManualDisciplineArchive.import(legacyArchive, owner).templates)
    }

    @Test fun conditioningStructureSurvivesTemplateAndWorkoutSnapshots() {
        val template = ManualWorkoutTemplate.empty(owner, ManualDiscipline.CONDITIONING)
            .copy(recipe = WorkoutRecipe(exercises = listOf(FreeformExercise(
                name = "Row", format = ConditioningFormat.EMOM, blockMinutes = "6",
                sets = listOf(FreeformSet(target = FreeformMeasure(seconds = "30"), restSeconds = "30"))
            ))))
        val draft = template.startWorkout(date, 100)
        val edit = template.copy(name = "New row", recipe = WorkoutRecipe())
        assertEquals("6", draft.exercises.single().blockMinutes)
        assertEquals(ConditioningFormat.EMOM, draft.exercises.single().format)
        assertTrue(edit.startWorkout(date, 101).exercises.isEmpty())
    }
}
