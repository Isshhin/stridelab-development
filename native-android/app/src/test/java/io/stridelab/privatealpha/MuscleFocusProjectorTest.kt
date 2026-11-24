package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MuscleFocusProjectorTest {
    private val assets: File
        get() = listOf(
            File("src/main/assets"),
            File("app/src/main/assets"),
            File("native-android/app/src/main/assets")
        ).first { File(it, "native-library/catalog.json").exists() }

    private fun augmentedCatalog(): List<ExerciseLibraryRecordV2> =
        FreeformExerciseCatalog.augment(
            ExerciseLibraryV2.load { File(assets, it).inputStream() }.records
        )

    @Test
    fun hundredPercentCatalogAndBuiltinMuscleKeyCoverage() {
        val v2Records = ExerciseLibraryV2.load { File(assets, it).inputStream() }.records
        val builtins = FreeformExerciseCatalog.builtinRecords()
        assertEquals(66, v2Records.size)
        assertEquals(19, builtins.size)
        val allRecords = v2Records + builtins

        for (key in ExerciseMuscleCatalog.STANDARD_MUSCLES) {
            val mapped = MuscleFocusProjector.regionsForKey(key)
            assertTrue("Canonical key $key must map to >= 1 region", mapped.isNotEmpty())
        }

        for (record in allRecords) {
            assertTrue("${record.id} missing primaryMuscles", record.primaryMuscles.isNotEmpty())
            for (key in record.primaryMuscles + record.secondaryMuscles) {
                assertTrue(
                    "${record.id} muscle key $key not mapped in MuscleFocusProjector",
                    MuscleFocusProjector.regionsForKey(key).isNotEmpty()
                )
            }
            val focus = MuscleFocusProjector.forExercise(record.id, record.name, allRecords)
            assertFalse("${record.id} must produce non-empty MuscleFocusMap", focus.isEmpty)
        }
    }

    @Test
    fun singleExerciseFocusAppliesPrimarySecondaryWeightsAndAliasResolution() {
        val catalog = augmentedCatalog()
        val bpFocus = MuscleFocusProjector.forExercise("bp", null, catalog)
        assertEquals(1.0f, bpFocus.regionIntensities[AnatomicalMuscleRegion.CHEST]!!, 1e-6f)
        assertEquals(0.35f, bpFocus.regionIntensities[AnatomicalMuscleRegion.TRICEPS]!!, 1e-6f)
        assertEquals(0.35f, bpFocus.regionIntensities[AnatomicalMuscleRegion.FRONT_DELTS]!!, 1e-6f)

        // Overlap precedence: primary wins (1.0f) over secondary (0.35f)
        val overlap = MuscleFocusProjector.forMuscles(
            primaryKeys = listOf("pectoralis_major"),
            secondaryKeys = listOf("pectoralis_major", "triceps")
        )
        assertEquals(1.0f, overlap.regionIntensities[AnatomicalMuscleRegion.CHEST]!!, 1e-6f)
        assertEquals(0.35f, overlap.regionIntensities[AnatomicalMuscleRegion.TRICEPS]!!, 1e-6f)

        // Swap wrapper & legacy alias resolution
        val swapSq = MuscleFocusProjector.forExercise("swap-sq-progression", "Tempo split squat", catalog)
        assertFalse(swapSq.isEmpty)
        assertEquals(1.0f, swapSq.regionIntensities[AnatomicalMuscleRegion.QUADS]!!, 1e-6f)

        val latPulldown = MuscleFocusProjector.forExercise("cable-latpulldown", null, catalog)
        assertFalse(latPulldown.isEmpty)
        assertEquals(1.0f, latPulldown.regionIntensities[AnatomicalMuscleRegion.LATS]!!, 1e-6f)

        // Unmapped custom exercise returns EMPTY cleanly
        val custom = MuscleFocusProjector.forExercise("custom-unmapped-xyz", "My weird drill", catalog)
        assertTrue(custom.isEmpty)
    }

    @Test
    fun plannedAndConfirmedSessionProjectorsWeightSetsAndExcludeUnconfirmedOrSkipped() {
        val catalog = augmentedCatalog()
        val squat = ExercisePrescription("sq", "Squat", 3, 8, null, 90, "Barbell", "Control depth.")
        val bench = ExercisePrescription("bp", "Bench press", 1, 8, null, 90, "Dumbbell", "Control bar.")

        val planned = MuscleFocusProjector.forPlannedSession(listOf(squat, bench), catalog)
        assertEquals(4, planned.setCount)
        assertEquals(1.0f, planned.regionIntensities[AnatomicalMuscleRegion.QUADS]!!, 1e-6f)
        assertTrue(
            (planned.regionIntensities[AnatomicalMuscleRegion.CHEST] ?: 0f) <
                (planned.regionIntensities[AnatomicalMuscleRegion.QUADS] ?: 0f)
        )

        val execution = WorkoutExecution(
            executionId = "exec-1",
            planId = "p1",
            sessionId = "s1",
            finalizationId = "fin-1",
            sets = listOf(
                ExecutionSet(squat, 1, SetDisposition.COMPLETED, SetActual(reps = 8, kg = 60.0)),
                ExecutionSet(squat, 2, SetDisposition.SKIPPED, null),
                ExecutionSet(bench, 1, SetDisposition.PENDING, SetActual(seconds = 12)) // partial unconfirmed hold
            )
        )
        val confirmedExec = MuscleFocusProjector.forConfirmedExecution(execution, catalog)
        assertEquals(1, confirmedExec.setCount)
        assertTrue(confirmedExec.regionIntensities.containsKey(AnatomicalMuscleRegion.QUADS))
        assertFalse(confirmedExec.regionIntensities.containsKey(AnatomicalMuscleRegion.CHEST))

        val unconfirmedOnly = execution.copy(
            sets = listOf(
                ExecutionSet(bench, 1, SetDisposition.SKIPPED, null),
                ExecutionSet(bench, 2, SetDisposition.PENDING, SetActual(seconds = 15))
            )
        )
        assertTrue(MuscleFocusProjector.forConfirmedExecution(unconfirmedOnly, catalog).isEmpty)

        // Completed freeform workout excludes completed == false sets
        val freeform = FreeformWorkout(
            id = "fw-1",
            owner = "local",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 1L,
            modifiedAt = 2L,
            completedAt = 3L,
            exercises = listOf(
                FreeformExercise(
                    name = "Bench press",
                    catalogId = "bp",
                    sets = listOf(
                        FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "40"), completed = true),
                        FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "40"), completed = false)
                    )
                ),
                FreeformExercise(
                    name = "Custom stretch",
                    catalogId = null,
                    sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "10"), completed = true))
                )
            )
        )
        val freeformFocus = MuscleFocusProjector.forConfirmedFreeformWorkout(freeform, catalog)
        assertEquals(2, freeformFocus.setCount)
        assertEquals(1.0f, freeformFocus.regionIntensities[AnatomicalMuscleRegion.CHEST]!!, 1e-6f)
        assertFalse(freeformFocus.regionIntensities.containsKey(AnatomicalMuscleRegion.QUADS))
    }
}
