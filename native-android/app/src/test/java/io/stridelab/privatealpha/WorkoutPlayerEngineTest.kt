package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class WorkoutPlayerEngineTest {
    private var now = 10_000L
    private val wall = 100_000L
    private val engine = WorkoutPlayerEngine { now }
    private val date = LocalDate.of(2026, 11, 23)
    private val at = Instant.parse("2026-11-23T08:00:00Z")
    private val hold = ExercisePrescription("hold", "Hold", 2, seconds = 45, rest = 60)

    private fun session(discipline: String = "iso", target: ExercisePrescription = hold) =
        PlannedSession("session", date, discipline, "Session", 20, null, "structured-$discipline", exercises = listOf(target))
    private fun execution(session: PlannedSession = session()) = WorkoutExecution.create("exec", "plan", session, "finish")
    private fun roundTrip(state: WorkoutExecution, gap: Long, newBoot: Boolean = false): WorkoutExecution {
        val raw = WorkoutExecutionCheckpoint.encode("owner", state, now, wall, bootCount = 3)
        now = if (newBoot) 1000L else now + gap
        return WorkoutExecutionCheckpoint.decode(raw, "owner", "plan", "session", now, wall + gap,
            bootCount = if (newBoot) 4 else 3)
    }

    @Test fun monotonicWorkPauseResumeAndRestExtension() {
        var state = engine.reduce(execution(), PlayerEvent.StartWorkTimer)
        now += 12_345
        val tick = engine.reduce(state, PlayerEvent.Tick(now))
        assertEquals(32_655L, tick.timer!!.remainingMs)
        assertEquals(state.revision, tick.revision)
        state = engine.reduce(state, PlayerEvent.PauseTimer)
        now += 180_000
        assertEquals(32_655L, state.timer!!.remaining(now))
        state = engine.reduce(state, PlayerEvent.ResumeTimer)
        now += 17_655
        state = engine.reduce(state, PlayerEvent.CompleteSet(SetActual(seconds = 29)))
        assertEquals(30_000L, state.current.measuredWorkMs)
        assertEquals(29, state.current.actual!!.seconds)
        assertEquals(45, state.current.target.seconds)
        now += 20_000
        state = engine.reduce(state, PlayerEvent.ExtendRest)
        assertEquals(55_000L, state.timer!!.remaining(now))
        now += 70_000
        state = engine.reduce(state, PlayerEvent.SkipRest)
        assertEquals(90_000L, state.sets[0].actualRestMs)
        assertEquals(1, state.cursor)
    }

    @Test fun timerExpiryAndSkipNeverBecomePerformedDose() {
        var state = engine.reduce(execution(), PlayerEvent.StartWorkTimer)
        now += 55_000
        state = engine.reduce(state, PlayerEvent.Tick(now))
        assertEquals(0L, state.timer!!.remainingMs)
        assertNull(state.current.actual)
        assertEquals(SetDisposition.PENDING, state.current.disposition)
        state = engine.reduce(state, PlayerEvent.SkipSet)
        assertEquals(SetDisposition.SKIPPED, state.sets[0].disposition)
        state = engine.reduce(state, PlayerEvent.CompleteSet(SetActual(seconds = 30, kg = 5.0, rir = 2)))
        state = engine.reduce(state, PlayerEvent.EditPreviousSet(1, SetActual(seconds = 31, kg = 5.0, rir = 2)))
        state = engine.reduce(state, PlayerEvent.FinishEarly)
        state = engine.reduce(state, PlayerEvent.FinalizeSession(at, 5, "", date))
        assertEquals(ExecutionLifecycle.FINISHED_NORMALLY, state.lifecycle)
        assertEquals(PrescriptionOutcome.PARTIALLY_PERFORMED, state.outcome.prescription)
        assertEquals(listOf(SetRecord("hold", 2, kg = 5.0, seconds = 31)), state.finalLog!!.sets)
        assertEquals(state, engine.reduce(state, PlayerEvent.FinalizeSession(at.plusSeconds(40), 8, "different")))
    }

    @Test fun finishingTheQueueAndCompletingThePrescriptionAreIndependent() {
        fun finish(skipLast: Boolean, early: Boolean): WorkoutExecution {
            var state = engine.reduce(execution(), PlayerEvent.CompleteSet(SetActual(seconds = 45)))
            state = engine.reduce(state, PlayerEvent.SkipRest)
            state = when {
                early -> engine.reduce(state, PlayerEvent.FinishEarly)
                skipLast -> engine.reduce(state, PlayerEvent.SkipSet)
                else -> engine.reduce(engine.reduce(state, PlayerEvent.CompleteSet(SetActual(seconds = 40))), PlayerEvent.SkipRest)
            }
            val final = engine.reduce(state, PlayerEvent.FinalizeSession(at, 5, "", date))
            val restored = roundTrip(final, 100).copy(phase = PlayerPhase.Completed, receipt = true)
            assertEquals(restored, engine.reduce(restored, PlayerEvent.FinalizeSession(at.plusSeconds(60), 8, "retry")))
            return restored
        }
        assertEquals(WorkoutExecutionOutcome(ExecutionLifecycle.FINISHED_NORMALLY, PrescriptionOutcome.FULLY_PERFORMED, 2, 2, 0),
            finish(skipLast = false, early = false).outcome)
        assertEquals(WorkoutExecutionOutcome(ExecutionLifecycle.FINISHED_NORMALLY, PrescriptionOutcome.PARTIALLY_PERFORMED, 2, 1, 1),
            finish(skipLast = true, early = false).outcome)
        assertEquals(WorkoutExecutionOutcome(ExecutionLifecycle.FINISHED_EARLY, PrescriptionOutcome.PARTIALLY_PERFORMED, 2, 1, 0),
            finish(skipLast = false, early = true).outcome)
    }

    @Test fun allSkippedLeavesOnlyALocalReceiptAndHistoricalCompletionIsUnknown() {
        val skipped = engine.reduce(engine.reduce(execution(), PlayerEvent.SkipSet), PlayerEvent.SkipSet)
        val finished = engine.reduce(skipped, PlayerEvent.FinalizeSession(at, null, ""))
        assertEquals(WorkoutExecutionOutcome(ExecutionLifecycle.FINISHED_NORMALLY, PrescriptionOutcome.NO_WORK_PERFORMED, 2, 0, 2),
            roundTrip(finished, 10).outcome)
        assertNull(finished.finalLog)
        assertTrue(finished.receipt)
        val historical = session().copy(status = "completed", log = WorkoutLog(at))
        assertTrue(historical.isMarkedComplete)
        assertEquals(PrescriptionOutcome.UNKNOWN, TrainingState(plans = listOf(plan(historical)))
            .prescriptionOutcome("plan", historical.id))
    }

    @Test fun oldCheckpointDecodesTheLegacyCompletionValueWithoutInventingPerformedSets() {
        val state = engine.reduce(engine.reduce(execution(), PlayerEvent.SkipSet), PlayerEvent.SkipSet)
        val finished = engine.reduce(state, PlayerEvent.FinalizeSession(at, null, ""))
        val json = org.json.JSONObject(WorkoutExecutionCheckpoint.encode("owner", finished, now, wall))
        listOf("lifecycle", "prescriptionOutcome", "confirmedSets", "skippedSets").forEach(json::remove)
        assertEquals(finished.outcome,
            WorkoutExecutionCheckpoint.decode(json.toString(), "owner", "plan", "session", now, wall).outcome)
    }

    @Test fun abandonmentIsTerminalAndDoesNotCreateAWorkoutLog() {
        val state = engine.reduce(execution(), PlayerEvent.AbandonSession)
        assertEquals(ExecutionLifecycle.ABANDONED, state.lifecycle)
        assertEquals(PlayerPhase.Completed, state.phase)
        assertEquals(state, engine.reduce(state, PlayerEvent.FinalizeSession(at, null, "")))
        assertNull(state.finalLog)
    }

    @Test fun recoveryRestoresBothPausedAndRunningHolds() {
        var state = engine.reduce(execution(), PlayerEvent.StartWorkTimer)
        state = roundTrip(state, 10_000)
        assertEquals(35_000L, state.timer!!.remaining(now))
        state = engine.reduce(state, PlayerEvent.PauseTimer)
        state = roundTrip(state, 40_000, newBoot = true)
        assertEquals(ExecutionLifecycle.PAUSED, state.lifecycle)
        assertEquals(35_000L, state.timer!!.remaining(now))
        state = engine.reduce(state, PlayerEvent.ResumeTimer)
        state = roundTrip(state, 5000, newBoot = true)
        assertEquals(30_000L, state.timer!!.remaining(now))
    }

    @Test fun recoveryAfterConfirmationAlreadyHasRestPhaseAndActuals() {
        val confirmed = engine.reduce(execution(), PlayerEvent.CompleteSet(SetActual(seconds = 30, rir = 1)))
        val state = roundTrip(confirmed, 15_000)
        assertEquals(PlayerPhase.Resting, state.phase)
        assertEquals(45_000L, state.timer!!.remaining(now))
        assertEquals(30, state.current.actual!!.seconds)
        assertEquals(1, state.current.actual!!.rir)
        assertEquals(state, engine.reduce(state, PlayerEvent.CompleteSet(SetActual(seconds = 45))))
    }

    @Test fun emomHoldsTheMinuteBoundaryAndNeverConfirmsTheNextRound() {
        val prescription = hold.copy(seconds = 40, rest = 20)
        var state = engine.reduce(execution(session("cond", prescription)), PlayerEvent.StartWorkTimer)
        now += 25_000
        state = engine.reduce(state, PlayerEvent.CompleteSet(SetActual(seconds = 25), emom = true))
        assertEquals(35_000L, state.timer!!.remaining(now))
        assertEquals(state, engine.reduce(state, PlayerEvent.AdvanceEmom))
        now += 35_150
        state = engine.reduce(state, PlayerEvent.AdvanceEmom)
        assertEquals(1, state.cursor)
        assertEquals(PlayerPhase.ActiveWork, state.phase)
        assertEquals(39_850L, state.timer!!.remaining(now))
        assertEquals(35_000L, state.sets[0].actualRestMs)
        assertNull(state.current.actual)
    }

    @Test fun emomCanCycleThroughAnUnconfirmedRoundAndReviewItLater() {
        val prescription = hold.copy(seconds = 40, rest = 20)
        var state = engine.reduce(execution(session("cond", prescription)), PlayerEvent.StartWorkTimer)
        now += 40_150
        state = engine.reduce(state, PlayerEvent.EmomWorkElapsed)
        assertEquals(PlayerPhase.Resting, state.phase)
        assertNull(state.current.actual)
        assertEquals(40_000L, state.current.measuredWorkMs)
        now += 20_000
        state = engine.reduce(state, PlayerEvent.AdvanceEmom)
        assertEquals(1, state.cursor)
        assertNull(state.sets[0].actual)
        state = engine.reduce(state, PlayerEvent.Review)
        state = engine.reduce(state, PlayerEvent.EditPreviousSet(0, SetActual(seconds = 36)))
        state = engine.reduce(state, PlayerEvent.FinalizeSession(at, null, ""))
        assertEquals(listOf(SetRecord("hold", 1, seconds = 36)), state.finalLog!!.sets)
    }

    @Test fun undoConfirmationReturnsToTheSameSetWithoutSavingItsActuals() {
        val state = engine.reduce(execution(), PlayerEvent.CompleteSet(SetActual(seconds = 25)))
        val undone = engine.reduce(state, PlayerEvent.EditPreviousSet(0, null))
        assertEquals(PlayerPhase.ActiveWork, undone.phase)
        assertEquals(0, undone.cursor)
        assertEquals(SetDisposition.PENDING, undone.current.disposition)
        assertNull(undone.current.actual)
        assertNull(undone.timer)
    }

    @Test fun sameBootMonotonicClockSurvivesWallClockAdjustment() {
        val running = engine.reduce(execution(), PlayerEvent.StartWorkTimer)
        val raw = WorkoutExecutionCheckpoint.encode("owner", running, now, wall, 3)
        now += 5_000
        val recovered = WorkoutExecutionCheckpoint.decode(raw, "owner", "plan", "session", now, wall - 30_000, 3)
        assertEquals(40_000L, recovered.timer!!.remaining(now))
    }

    @Test fun staleRevisionRecoveryAcceptsOnlyTheAlreadyCommittedLog() {
        val session = session()
        val confirmed = engine.reduce(execution(session), PlayerEvent.CompleteSet(SetActual(seconds = 30)))
        val reviewing = engine.reduce(confirmed, PlayerEvent.Review)
        val frozen = engine.reduce(reviewing, PlayerEvent.FinalizeSession(at, 5, "", date))
        val log = requireNotNull(frozen.finalLog)
        val completed = plan(session.copy(status = "completed", log = log)).copy(revision = 17)
        val recovered = roundTrip(frozen, 10_000)
        assertEquals("finish", recovered.finalizationId)
        assertSame(completed, reconcileWorkoutFinalization(completed, session.id, recovered.finalLog!!))
        assertThrows(PlanConstraint::class.java) {
            reconcileWorkoutFinalization(completed, session.id, log.copy(effort = 7))
        }
        val receipt = roundTrip(recovered.copy(receipt = true, phase = PlayerPhase.Completed), 10_000)
        assertTrue(receipt.receipt)
        assertEquals(log, receipt.finalLog)
    }

    @Test fun finalizedLogsSupplyPerformedEvidenceToAllFiveAnalyzers() {
        val rep = ExercisePrescription("push", "Push", 2, reps = 12)
        for ((discipline, target) in listOf("str" to rep, "cal" to rep, "iso" to hold, "cond" to hold,
            "plyo" to rep.copy(id = "pogo-hop"))) {
            val session = session(discipline, target)
            val actual = if (target.seconds != null) SetActual(seconds = 30) else SetActual(reps = 9)
            val confirmed = engine.reduce(execution(session), PlayerEvent.CompleteSet(actual))
            val next = engine.reduce(confirmed, PlayerEvent.SkipRest)
            val review = engine.reduce(next, PlayerEvent.SkipSet)
            val frozen = engine.reduce(review, PlayerEvent.FinalizeSession(at, 5, "", date))
            assertEquals(1, frozen.finalLog!!.sets.size)
            val plan = plan(session.copy(status = "completed", log = frozen.finalLog))
            val debrief = Engine15DebriefEngine.workout(plan, session.id, listOf(plan), zone = ZoneOffset.UTC)
            assertTrue(discipline, debrief?.insights?.any { it.category == DebriefCategory.EXECUTION } == true)
        }
    }

    private fun plan(session: PlannedSession) = PlanSnapshot("plan", "owner", ENGINE15_VERSION, ENGINE15_POLICY, at,
        PlanRequest(goal = "General"), listOf(session), emptyList(), "approved")
}
