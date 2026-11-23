package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class WorkoutPlayerIntegrationTest {
    private val date = LocalDate.of(2026, 11, 23)
    private val at = Instant.parse("2026-11-23T08:00:00Z")

    @Test fun runnerStrengthDebriefUsesEditedDoseAndExplicitSessionRir() {
        val run = Exercise("str", "Strength for Runners", ExercisePrescription("split-squat", "Split squat", 3, reps = 8))
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 8, kg = 20.0)))
        run.send(PlayerEvent.EditPreviousSet(0, null))
        assertEquals(SetDisposition.PENDING, run.state.current.disposition)
        assertNull(run.state.current.actual)
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 7, kg = 16.0)))
        run.send(PlayerEvent.SkipRest)
        run.send(PlayerEvent.SkipSet)
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 8)))
        run.send(PlayerEvent.SkipRest)
        assertEquals(listOf(SetDisposition.MODIFIED, SetDisposition.SKIPPED, SetDisposition.COMPLETED),
            run.state.sets.map { it.disposition })

        val report = run.finish(effort = 6, notes = "RIR: 2")
        assertEquals(listOf(SetRecord("split-squat", 1, reps = 7, kg = 16.0),
            SetRecord("split-squat", 3, reps = 8)), run.log.sets)
        report.expect(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, "1 of the 2", "3 sets")
        report.expect(DebriefCategory.LOAD_AND_RECOVERY, DebriefTone.CAUTION, "2 reps", "3 in reserve")
    }

    @Test fun plyometricsCountsActualContactsFromBothMovements() {
        val run = Exercise("plyo", "Plyometrics",
            ExercisePrescription("pogo-hop", "Pogo hop", 2, reps = 12),
            ExercisePrescription("line-bound", "Line bound", 1, reps = 10))
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 11)))
        run.send(PlayerEvent.SkipRest)
        run.send(PlayerEvent.SkipSet)
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 9)))
        run.send(PlayerEvent.SkipRest)

        val report = run.finish()
        assertEquals(listOf(SetRecord("pogo-hop", 1, reps = 11), SetRecord("line-bound", 1, reps = 9)), run.log.sets)
        report.expect(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, "20 ground contacts", "cap of 34")
    }

    @Test fun isometricsKeepsAnEarlyHoldAndEffortSeparateFromThePrescription() {
        val run = Exercise("iso", "Isometrics", ExercisePrescription("wall-sit", "Wall sit", 1, seconds = 45))
        run.send(PlayerEvent.StartWorkTimer)
        run.now += 30_000L
        run.restore()
        assertEquals(15_000L, run.state.timer!!.remaining(run.now))
        run.send(PlayerEvent.CompleteSet(SetActual(seconds = 30)))
        assertEquals(30_000L, run.state.current.measuredWorkMs)
        assertEquals(45, run.state.current.target.seconds)
        run.send(PlayerEvent.SkipRest)

        val report = run.finish(effort = 7)
        assertEquals(listOf(SetRecord("wall-sit", 1, seconds = 30)), run.log.sets)
        report.expect(DebriefCategory.EXECUTION, DebriefTone.CAUTION, "0 of the 1", "0:30", "0:45")
        report.expect(DebriefCategory.LOAD_AND_RECOVERY, DebriefTone.CAUTION, "7/10", "6/10")
    }

    @Test fun calisthenicsFinishesEarlyWithOneRepSetAndOneHold() {
        val run = Exercise("cal", "Calisthenics",
            ExercisePrescription("push-up", "Push-up", 1, reps = 8),
            ExercisePrescription("plank", "Plank", 2, seconds = 30))
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 8)))
        run.send(PlayerEvent.SkipRest)
        run.send(PlayerEvent.CompleteSet(SetActual(seconds = 20)))
        run.send(PlayerEvent.FinishEarly)

        val report = run.finish()
        assertEquals(ExecutionStatus.FINISHED_EARLY, run.state.status)
        assertEquals(listOf(SetRecord("push-up", 1, reps = 8), SetRecord("plank", 1, seconds = 20)), run.log.sets)
        assertEquals(1, report.insights.size)
        report.expect(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, "1 of the 2", "0:20", "1:00")
    }

    @Test fun emomCyclesPastAnUnconfirmedRoundAndDebriefsOnlyConfirmedWork() {
        val run = Exercise("cond", "EMOM work capacity", ExercisePrescription("march", "March", 3, seconds = 40, rest = 20))
        run.send(PlayerEvent.StartWorkTimer)
        run.now += 40_000L
        run.send(PlayerEvent.CompleteSet(SetActual(seconds = 40), emom = true))
        run.now += 20_000L
        run.send(PlayerEvent.AdvanceEmom)
        run.now += 40_000L
        run.send(PlayerEvent.EmomWorkElapsed)
        assertEquals(40_000L, run.state.current.measuredWorkMs)
        assertEquals(SetDisposition.PENDING, run.state.current.disposition)
        run.now += 20_000L
        run.send(PlayerEvent.AdvanceEmom)
        assertEquals(2, run.state.cursor)
        assertEquals(40_000L, run.state.timer!!.remaining(run.now))
        run.now += 25_000L
        run.send(PlayerEvent.CompleteSet(SetActual(seconds = 25), emom = true))
        run.now += 35_000L
        run.send(PlayerEvent.AdvanceEmom)

        val report = run.finish(effort = 8)
        assertEquals(listOf(SetRecord("march", 1, seconds = 40), SetRecord("march", 3, seconds = 25)), run.log.sets)
        report.expect(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, "1 of the 2", "1:05", "2:00")
        report.expect(DebriefCategory.LOAD_AND_RECOVERY, DebriefTone.CAUTION, "8/10", "7/10")
    }

    @Test fun abandonedCheckpointCannotCommitEvenAfterConfirmedWork() {
        val run = Exercise("str", "Strength", ExercisePrescription("press", "Press", 2, reps = 8))
        run.send(PlayerEvent.CompleteSet(SetActual(reps = 8)))
        run.send(PlayerEvent.AbandonSession)
        val abandoned = run.state
        run.send(PlayerEvent.FinalizeSession(at, 5, "", date))
        assertEquals(abandoned, run.state)
        assertEquals(ExecutionStatus.ABANDONED, run.state.status)
        assertNull(run.state.finalLog)
        assertNull(run.plan.sessions.single().log)
        assertNull(Engine15DebriefEngine.workout(run.plan, "session", listOf(run.plan), zone = ZoneOffset.UTC))
    }

    private fun SessionDebrief.expect(category: DebriefCategory, tone: DebriefTone, vararg values: String) {
        val insight = insights.single { it.category == category }
        assertEquals(category.name, tone, insight.tone)
        values.forEach { assertTrue("Missing '$it' in '${insight.body}'", it in insight.body) }
    }

    // A serialized checkpoint boundary with deterministic clocks. Android tests exercise the SQLite writer.
    private inner class Exercise(discipline: String, title: String, vararg targets: ExercisePrescription) {
        var now = 10_000L
        private val session = PlannedSession("session", date, discipline, title, 20, null, "structured-$discipline",
            quality = discipline == "cond", exercises = targets.toList())
        val plan = PlanSnapshot("plan", "owner", ENGINE15_VERSION, ENGINE15_POLICY, at,
            PlanRequest(goal = "General"), listOf(session), emptyList(), "approved")
        private val engine = WorkoutPlayerEngine { now }
        var state = WorkoutExecution.create("execution", plan.id, session, "finalization")
            private set
        private var checkpoint = encode()
        lateinit var log: WorkoutLog
            private set

        private fun encode() = WorkoutExecutionCheckpoint.encode(plan.owner, state, now, 100_000L + now, bootCount = 4)

        fun restore() {
            state = WorkoutExecutionCheckpoint.decode(checkpoint, plan.owner, plan.id, session.id, now, 100_000L + now, 4)
        }

        fun send(event: PlayerEvent) {
            restore()
            state = engine.reduce(state, event)
            checkpoint = encode()
            restore()
        }

        fun finish(effort: Int? = null, notes: String = ""): SessionDebrief {
            assertEquals(PlayerPhase.Reviewing, state.phase)
            val save = PlayerEvent.FinalizeSession(at, effort, notes, date)
            send(save)
            val frozen = state
            log = requireNotNull(frozen.finalLog)
            val committed = reconcileWorkoutFinalization(plan, session.id, log).copy(revision = plan.revision + 1)
            // Simulate a restart after the plan commit but before the receipt write.
            send(save.copy(effort = 10, notes = "Retry"))
            assertEquals(frozen, state)
            assertEquals("finalization", state.finalizationId)
            assertSame(committed, reconcileWorkoutFinalization(committed, session.id, requireNotNull(state.finalLog)))
            assertEquals(log, committed.sessions.single().log)
            return requireNotNull(Engine15DebriefEngine.workout(committed, session.id, listOf(committed), zone = ZoneOffset.UTC))
        }
    }
}
