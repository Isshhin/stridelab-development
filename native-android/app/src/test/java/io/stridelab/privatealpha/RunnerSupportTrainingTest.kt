package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RunnerSupportTrainingTest {
    private val start = LocalDate.parse("2026-11-23")
    private val now = Instant.parse("2026-11-21T00:00:00Z")
    private val owner = "support-fixture"

    private fun plan(
        goal: String = "Strength for Runners",
        date: LocalDate = start,
        days: Set<Int> = setOf(1, 3, 5),
        fixed: Boolean = false
    ): PlanSnapshot {
        val request = PlanRequest(goal = goal, start = date, weeks = 12, days = days, runDays = 0,
            strengthDays = 1, timeBudgetEnabled = false, weeklySlots = if (fixed) mapOf("str" to listOf(days.min())) else emptyMap())
        return PlanSnapshot("support", owner, ENGINE15_VERSION, ENGINE15_INDEPENDENT_STRENGTH_POLICY,
            now, request, Engine15DisciplineProgression.compose(request, "support", "str", false), emptyList())
    }

    private fun running(vararg qualityDates: LocalDate, longDate: LocalDate? = null): PlanSnapshot {
        val request = PlanRequest(goal = "Improve 10K", start = start, weeks = 12, experience = 2)
        val sessions = qualityDates.mapIndexed { i, date ->
            PlannedSession("run-q$i", date, "run", "Threshold", 45, 8.0, "quality", quality = true)
        } + listOfNotNull(longDate?.let { PlannedSession("run-long", it, "run", "Long run", 90, 18.0, "long") })
        return PlanSnapshot("running", owner, ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            now, request, sessions, emptyList(), status = "approved")
    }

    @Test fun heavyLimitsPlyoCapsAndUpperWorkRemainsFlexible() {
        val key = start.plusDays(1)
        val keys = RunnerSupportSchedule.keyRuns(listOf(running(key)))
        val heavy = plan("Full Body").sessions.first()
        val plyo = plan("Plyometrics").sessions.first()
        val upper = plan("Upper Body & Posture").sessions.first()
        assertNull(RunnerSupportSchedule.placement(heavy, start, keys))
        assertNull(RunnerSupportSchedule.placement(plyo, start, keys))
        assertEquals(RunnerSupportSchedule.Placement(0), RunnerSupportSchedule.placement(upper, start, keys))
        assertEquals(RunnerSupportSchedule.Placement(1, afterQuality = true), RunnerSupportSchedule.placement(heavy, key, keys))
        assertEquals(RunnerSupportSchedule.Placement(1, afterQuality = true, cap = true), RunnerSupportSchedule.placement(plyo, key, keys))
    }

    @Test fun previewOffersMovePairCapAndNoSafeFixedDay() {
        val run = running(start.plusDays(1))
        val original = plan("Full Body")
        val move = requireNotNull(RunnerSupportSchedule.review(original, listOf(run)))
        assertEquals(start.plusDays(4), move.candidate.sessions.first().date)
        assertTrue(RunnerSupportSchedule.Option.MOVE in move.changes.first().options)
        Engine15DisciplineProgression.validateSaved(move.candidate, "str", false)

        val fixed = plan("Full Body", days = setOf(1), fixed = true)
        assertTrue(RunnerSupportSchedule.review(fixed, listOf(run))!!.blocked)
        val locked = original.copy(request = original.request.copy(lockedSessionDays =
            mapOf(original.sessions.first().id to start.toString())))
        assertTrue(RunnerSupportSchedule.review(locked, listOf(run))!!.blocked)
        val pair = plan("Plyometrics", days = setOf(2), fixed = true)
        val paired = RunnerSupportSchedule.review(pair, listOf(run))!!
        assertFalse(paired.blocked)
        assertTrue(paired.changes.first().options.containsAll(setOf(RunnerSupportSchedule.Option.AFTER_QUALITY, RunnerSupportSchedule.Option.CAP_DOSE)))
        assertEquals("8 contacts", RunnerSupportDose.label(paired.candidate.sessions.first()))
        val moderate = plan(days = setOf(1), fixed = true)
        val capped = RunnerSupportSchedule.review(moderate, listOf(run))!!.candidate.sessions.first()
        assertTrue(capped.exercises.all { it.sets == 2 })
        assertEquals(running(start.plusDays(1)), run)
    }

    @Test fun offsetCalendarUsesRunningRecoveryAndTaperDates() {
        val runs = listOf(running(start.plusDays(1)))
        val support = plan(date = start.plusDays(10), days = setOf(5), fixed = true)
        val review = RunnerSupportSchedule.review(support, runs)!!
        val phases = RunnerSupportSchedule.phases(runs)
        val recovery = review.candidate.sessions.single { it.date == start.plusWeeks(3).plusDays(4) }
        val taper = review.candidate.sessions.single { it.date == start.plusWeeks(10).plusDays(4) }
        assertEquals(Engine15MacroPhase.RECOVERY, phases[recovery.date])
        assertEquals(Engine15MacroPhase.TAPER, phases[taper.date])
        assertEquals(RunnerSupportDose.LIGHT, recovery.runnerSupportDose)
        assertEquals(RunnerSupportDose.LIGHT, taper.runnerSupportDose)
        assertEquals(support.sessions.first { it.id == taper.id }.exercises.map { it.reps }, taper.exercises.map { it.reps })
        Engine15DisciplineProgression.validateSaved(review.candidate, "str", false)
    }

    @Test fun calisthenicsKeepsControlAndUpperBodyDosesModestDuringRunningBuild() {
        val run = running(start.plusDays(15)).let { it.copy(request = it.request.copy(goal = "General fitness")) }
        val phases = RunnerSupportSchedule.phases(listOf(run))
        assertEquals(Engine15MacroPhase.BUILD, phases[start.plusWeeks(2)])
        for (goal in listOf("Single-Leg Control", "Core / Anti-Rotation", "Push")) {
            val request = plan(date = start).request.copy(goal = goal, strengthDays = 0, calDays = 1)
            val support = PlanSnapshot("cali", owner, ENGINE15_VERSION, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY,
                now, request, Engine15DisciplineProgression.compose(request, "cali", "cal", false), emptyList())
            val reviewed = RunnerSupportSchedule.review(support, listOf(run))!!.candidate
            val inBuild = reviewed.sessions.filter { phases[it.date] == Engine15MacroPhase.BUILD }
            assertTrue(inBuild.isNotEmpty())
            assertTrue(inBuild.all { it.runnerSupportDose == RunnerSupportDose.ENTRY || it.runnerSupportDose == RunnerSupportDose.LIGHT })
            assertTrue(inBuild.all { it.exercises.all { exercise -> exercise.sets <= 3 } })
            Engine15DisciplineProgression.validateSaved(reviewed, "cal", false)
        }
    }

    private fun withLog(rir: Int?, partial: Boolean = false, discomfort: String? = null, goal: String = "Strength for Runners"):
        Pair<PlanSnapshot, WorkoutExecution> {
        val fixture = plan(goal)
        val entry = RunnerSupportSchedule.review(fixture, emptyList())!!.candidate.copy(status = "approved")
        val first = entry.sessions.first()
        val execution = WorkoutExecution.create("execution", entry.id, first, "final")
        val rows = execution.sets.mapIndexed { i, set ->
            if (partial && i == 0) set.copy(disposition = SetDisposition.SKIPPED)
            else set.confirmed(SetActual(set.target.reps, seconds = set.target.seconds, rir = rir))
        }
        val log = WorkoutLog(now, first.minutes.toDouble(), null, 6, rows.filter { it.isConfirmed }.map {
            SetRecord(it.target.id, it.number, it.actual!!.reps, null, it.actual.seconds)
        }, "", first.date, discomfort = discomfort)
        return entry.copy(sessions = listOf(first.copy(status = "completed", log = log)) + entry.sessions.drop(1)) to
            execution.copy(sets = rows, phase = PlayerPhase.Completed, lifecycle = ExecutionLifecycle.FINISHED_NORMALLY,
                receipt = true, finalLog = log)
    }

    @Test fun confirmedRirApprovalRoundTripsAndRollsBack() {
        val (plan, receipt) = withLog(4)
        fun restored(p: PlanSnapshot) = PlanSnapshot.decode(JSONObject(p.encode().toString()), owner)
        Engine15DisciplineProgression.validateSaved(restored(plan), "str", false)
        val proposal = Engine15PlanAdaptationEngine.evaluateRunnerSupport(plan, emptyList(), start) { receipt }!!
        val apply = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, start)
        val after = restored(apply.appliedPlan)
        assertEquals(apply.appliedPlan, after)
        assertEquals(plan.sessions.first(), after.sessions.first())
        assertEquals(RunnerSupportDose.PROGRESS, after.sessions[1].runnerSupportDose)
        val beforeBridge = plan.sessions[1].exercises.single { it.id == "glute-bridge" }
        val afterBridge = after.sessions[1].exercises.single { it.id == "glute-bridge" }
        assertEquals(beforeBridge.reps!! + 1, afterBridge.reps)
        Engine15DisciplineProgression.validateSaved(after, "str", false)
        assertEquals(plan.sessions, Engine15PlanAdaptationEngine.revertRevision(after, apply.revision, start).restoredPlan.sessions)
    }

    @Test fun partialAndMissingRirHoldAndExplicitStrainReduces() {
        for ((plan, receipt) in listOf(withLog(null), withLog(4, partial = true))) {
            assertNull(Engine15PlanAdaptationEngine.evaluateRunnerSupport(plan, emptyList(), start) { receipt })
        }
        val (withRir, _) = withLog(4)
        assertNull(Engine15PlanAdaptationEngine.evaluateRunnerSupport(withRir, emptyList(), start) { null })
        for ((plan, receipt) in listOf(withLog(1), withLog(4, discomfort = "Soreness"))) {
            val proposal = Engine15PlanAdaptationEngine.evaluateRunnerSupport(plan, emptyList(), start) { receipt }!!
            val applied = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, start)
            assertEquals(RunnerSupportDose.LIGHT, applied.appliedPlan.sessions[1].runnerSupportDose)
        }
    }

    @Test fun plyoContactProgressionUsesConfirmedLandingsOnly() {
        val (plan, receipt) = withLog(null, goal = "Plyometrics")
        val proposal = Engine15PlanAdaptationEngine.evaluateRunnerSupport(plan, emptyList(), start) {
            receipt.takeIf { saved -> saved.sessionId == it.id }
        }!!
        val after = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, start).appliedPlan.sessions[1]
        assertEquals("16 contacts", RunnerSupportDose.label(after))
        val (partial, partialReceipt) = withLog(null, partial = true, goal = "Plyometrics")
        assertNull(Engine15PlanAdaptationEngine.evaluateRunnerSupport(partial, emptyList(), start) { partialReceipt })
    }
}
