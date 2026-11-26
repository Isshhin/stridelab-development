package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FreeformWorkoutTest {
    private val owner = "local-owner"
    private val date = LocalDate.of(2026, 11, 23)

    @Test fun partialDraftRoundTripsWithoutConfirmingPerformance() {
        val draft = FreeformWorkout.start(owner, ManualDiscipline.RUNNING, date, 100)
            .copy(performedOn = "2026-11-", distanceKm = "2.", durationMinutes = "")
        assertEquals(draft, FreeformCodec.decode(FreeformCodec.encode(draft), owner))
        assertThrows(IllegalArgumentException::class.java) { draft.finish(101) }
        assertFalse(draft.isComplete)
    }

    @Test fun allSixDisciplinesRoundTripActualsAndPreserveTargets() {
        for (discipline in ManualDiscipline.entries) {
            val draft = FreeformWorkout.start(owner, discipline, date, 100)
            val set = FreeformSet().copy(
                target = FreeformMeasure(quantity = "8", seconds = "45", kg = "50"),
                actual = FreeformMeasure(quantity = "6", seconds = "40", kg = "45"),
                restSeconds = "90", rpe = "8", rir = "2", completed = true
            )
            val entry = if (discipline == ManualDiscipline.RUNNING) {
                draft.copy(distanceKm = "5.25", durationMinutes = "30.5", effort = "6", runType = "Easy")
            } else {
                draft.copy(exercises = listOf(FreeformExercise(name = "Movement", sets = listOf(set))))
            }
            val finished = entry.finish(200)
            assertTrue(finished.isComplete)
            assertEquals(finished, FreeformCodec.decode(FreeformCodec.encode(finished), owner))
            if (discipline == ManualDiscipline.RUNNING) {
                val json = JSONObject(FreeformCodec.encode(finished))
                for (field in listOf("gps", "heartRate", "pace", "trainingEffect", "fit")) assertFalse(json.has(field))
            } else {
                val actual = finished.exercises.single().sets.single()
                assertEquals("8", actual.target.quantity)
                assertEquals("6", actual.actual.quantity)
                assertEquals("45", actual.actual.kg)
                assertEquals("40", actual.actual.seconds)
            }
        }
    }

    @Test fun unconfirmedRowsDoNotContributeToTotalsAndCannotFinishAlone() {
        val draft = FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 100)
        val pending = FreeformSet(actual = FreeformMeasure(quantity = "10", kg = "100"))
        val confirmed = FreeformSet(actual = FreeformMeasure(quantity = "5", kg = "20"), completed = true)
        val movement = FreeformExercise(name = "Squat", sets = listOf(pending, confirmed))
        val filled = draft.copy(exercises = listOf(movement))
        assertEquals("1 set · 5 reps · 100 kg volume", filled.finish(200).summary())
        assertThrows(IllegalArgumentException::class.java) {
            filled.copy(exercises = listOf(movement.copy(sets = listOf(pending)))).finish(200)
        }
        assertThrows(IllegalArgumentException::class.java) {
            filled.copy(exercises = listOf(movement.copy(sets = listOf(confirmed.copy(
                actual = FreeformMeasure(kg = "20")
            ))))).finish(200)
        }
    }

    @Test fun versionThreeArchivePreservesDraftsAndRejectsOtherOwners() {
        val draft = FreeformWorkout.start(owner, ManualDiscipline.ISOMETRICS, date, 100)
        val legacy = ManualDisciplineSession("legacy-row", owner, ManualDiscipline.STRENGTH,
            "Strength", date, listOf(ManualExerciseEntry("Squat", 3, reps = 8)))
        val archive = ManualDisciplineArchive.export(owner, listOf(legacy), emptyList(), listOf(draft))
        val restored = ManualDisciplineArchive.import(archive, owner)
        assertEquals(listOf(draft), restored.freeform)
        assertEquals(listOf(legacy), restored.sessions)
        assertThrows(IllegalArgumentException::class.java) { ManualDisciplineArchive.import(archive, "other-owner") }
        assertThrows(IllegalArgumentException::class.java) {
            ManualDisciplineArchive.export(owner, emptyList(), emptyList(), listOf(draft, draft))
        }
        val v2 = ManualDisciplineArchive.export(owner, listOf(legacy), emptyList())
        assertTrue(ManualDisciplineArchive.import(v2, owner).freeform.isEmpty())
    }

    @Test fun universalWorkoutMixesRepsAndTimedHoldsAndSharesPreviousHistory() {
        val squat = FreeformExercise(
            name = "Back squat",
            sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "5", kg = "100"), completed = true))
        )
        val wallSit = FreeformExercise(
            name = "Wall sit",
            sets = listOf(FreeformSet(actual = FreeformMeasure(seconds = "45"), completed = true))
        )
        val completed = FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, 100)
            .copy(exercises = listOf(squat, wallSit))
            .finish(200)
        assertTrue(completed.isComplete)
        assertEquals("2 sets · 5 reps · 45 s · 500 kg volume", completed.summary())

        val board = FreeformBoard(workouts = listOf(completed))
        assertEquals(
            "45",
            board.previous(ManualDiscipline.ISOMETRICS, FreeformExercise(name = "Wall sit"))?.seconds
        )
        assertFalse(ManualDiscipline.RUNNING in loggableManualDisciplines)
    }
}
