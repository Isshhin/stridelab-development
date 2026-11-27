package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkoutPlayerEngineOverhaulTest {
    private val now = Instant.parse("2026-11-24T12:00:00Z")

    private fun testSession() = PlannedSession(
        id = "s-str-1",
        date = LocalDate.of(2026, 11, 24),
        discipline = "str",
        title = "Full Body Strength",
        minutes = 40,
        distanceKm = null,
        kind = "strength",
        exercises = listOf(
            ExercisePrescription("gs", "Goblet squat", sets = 2, reps = 8, rest = 60, equipment = "Dumbbell"),
            ExercisePrescription("plank", "Forearm plank", sets = 2, seconds = 30, rest = 45, equipment = "Mat / floor")
        )
    )

    @Test fun partialHoldThreeSecondsSavesUnconfirmedAndRoundTripsCheckpointWithoutEnteringFinalLog() {
        var clockMs = 10_000L
        val engine = WorkoutPlayerEngine { clockMs }
        var state = WorkoutExecution.create("exec-1", "plan-1", testSession(), "fin-1")

        // Focus Forearm plank set 1 (index 2)
        state = engine.reduce(state, PlayerEvent.FocusSet(2))
        assertEquals(2, state.cursor)
        assertEquals("plank", state.current.target.id)

        // < 3s partial hold is ignored cleanly
        val ignored = engine.reduce(state, PlayerEvent.SaveUnconfirmedPartialHold(2, seconds = 2))
        assertEquals(state.revision, ignored.revision)
        assertNull(ignored.sets[2].actual)

        // >= 3s partial hold saves seconds into SetActual while keeping PENDING (unconfirmed)
        state = engine.reduce(state, PlayerEvent.SaveUnconfirmedPartialHold(2, seconds = 14))
        assertEquals(SetDisposition.PENDING, state.sets[2].disposition)
        assertFalse(state.sets[2].isConfirmed)
        assertEquals(14, state.sets[2].actual?.seconds)
        assertEquals(0, state.outcome.confirmedSets)

        // Checkpoint v1 encodes and decodes cleanly without invariant failure
        val encoded = WorkoutExecutionCheckpoint.encode("owner-1", state, clockMs, 1_700_000_000_000L)
        val decoded = WorkoutExecutionCheckpoint.decode(encoded, "owner-1", "plan-1", "s-str-1", clockMs, 1_700_000_000_000L)
        assertEquals(14, decoded.sets[2].actual?.seconds)
        assertEquals(SetDisposition.PENDING, decoded.sets[2].disposition)

        // Confirm set 0 (Goblet squat 10kg x 8) and leave partial hold unconfirmed
        state = engine.reduce(decoded, PlayerEvent.CompleteSetAt(0, SetActual(reps = 8, kg = 25.0), startRest = false))
        assertEquals(1, state.outcome.confirmedSets)

        // Finalize: unconfirmed partial hold must NEVER enter finalLog.sets
        state = engine.reduce(state, PlayerEvent.Review)
        val finalized = engine.reduce(state, PlayerEvent.FinalizeSession(now, effort = 7, notes = ""))
        val log = finalized.finalLog
        assertNotNull(log)
        assertEquals(1, log!!.sets.size)
        assertEquals("gs", log.sets.single().exerciseId)
    }

    @Test fun completeAddExtraUnconfirmAndStartNextSetPreserveStructureAndReceipts() {
        var clockMs = 1_000L
        val engine = WorkoutPlayerEngine { clockMs }
        val session = testSession()
        var state = WorkoutExecution.create("exec-2", "plan-1", session, "fin-2")
        val originalStructure = WorkoutExecution.create("exec-2", "plan-1", session, "fin-2")

        // Add extra set to Goblet squat
        state = engine.reduce(state, PlayerEvent.AddExtraSet("gs"))
        assertEquals(5, state.sets.size)
        assertEquals(3, state.sets.count { it.target.id == "gs" })
        assertTrue(state.matchesPrescribedStructure(originalStructure))

        // Confirm plank set 1 (now at index 3) and enter rest, then StartNextSet advances to plank set 2 with work timer running
        state = engine.reduce(state, PlayerEvent.CompleteSetAt(3, SetActual(seconds = 30), startRest = true))
        assertEquals(PlayerPhase.Resting, state.phase)
        clockMs += 5_000L
        state = engine.reduce(state, PlayerEvent.StartNextSet(exerciseId = "plank", autoStartTimer = true))
        assertEquals(4, state.cursor)
        assertEquals(PlayerPhase.ActiveWork, state.phase)
        assertNotNull(state.timer?.deadlineMs)

        // Unconfirm reverts disposition to PENDING while retaining draft values
        state = engine.reduce(state, PlayerEvent.UnconfirmSetAt(3))
        assertFalse(state.sets[3].isConfirmed)
        assertEquals(30, state.sets[3].actual?.seconds)
    }
}
