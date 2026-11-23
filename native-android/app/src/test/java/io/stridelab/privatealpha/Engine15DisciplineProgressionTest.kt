package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Engine15DisciplineProgressionTest {
    private val start = LocalDate.of(2026, 11, 16)

    private fun request(discipline: String, goal: String) = PlanRequest(
        goal = goal, start = start, weeks = 8, days = (1..7).toSet(), runDays = 0,
        strengthDays = if (discipline == "str") 2 else 0,
        calDays = if (discipline == "cal") 2 else 0,
        conditioningDays = if (discipline == "cond") 2 else 0,
        strengthExperience = 1, calLevel = 1, equipment = mapOf("bar" to 1)
    )

    private fun plan(discipline: String, goal: String): PlanSnapshot {
        val request = request(discipline, goal)
        val policy = when (discipline) {
            "str" -> ENGINE15_INDEPENDENT_STRENGTH_POLICY
            "cal" -> ENGINE15_INDEPENDENT_CALISTHENICS_POLICY
            else -> ENGINE15_INDEPENDENT_CONDITIONING_POLICY
        }
        return PlanSnapshot("test", "owner", ENGINE15_VERSION, policy, Instant.EPOCH, request,
            Engine15DisciplineProgression.compose(request, "test", discipline, false), emptyList(), "approved")
    }

    @Test fun strengthTemplatesKeepTheirOwnUnitsAndDeload() {
        for (goal in Engine15DisciplineProgression.goals.getValue("str")) {
            val plan = plan("str", goal)
            val baseline = plan.sessions.first()
            assertEquals(goal != "Upper Body & Posture", baseline.lower)
            assertTrue(baseline.exercises.all { it.sets in 3..4 })
            val recovery = plan.sessions.first { it.date >= start.plusWeeks(3) }
            assertTrue(recovery.exercises.all { it.sets < baseline.exercises.first().sets })
            if (goal == "Isometrics") assertTrue(baseline.exercises.all { it.seconds != null && it.reps == null })
            Engine15IndependentPlanGenerator.validateSaved(PlanSnapshot.decode(plan.encode(), plan.owner))
        }
    }

    @Test fun jumpContactsAreBoundedAndCannotStackWithSpeed() {
        val plan = plan("str", "Plyometrics")
        val jump = plan.sessions.first()
        assertTrue(plan.sessions.filter { it.quality }.all { session ->
            session.exercises.sumOf { it.sets * (it.reps ?: 0) } in 24..36
        })
        val speed = PlannedSession("speed", jump.date.plusDays(1), "run", "Speed", 30, 5.0, "quality", quality = true)
        assertTrue(Engine15DisciplineProgression.spacingConflict(jump, speed))
        val upper = plan("str", "Upper Body & Posture").sessions.first()
        assertFalse(Engine15DisciplineProgression.spacingConflict(upper, speed))
        val returning = Engine15DisciplineProgression.compose(plan.request.copy(returning = true), plan.id, "str", false)
        assertTrue(returning.filter { it.quality }.all { session -> session.exercises.sumOf { it.sets * (it.reps ?: 0) } == 12 })
        assertTrue(returning.filterNot { it.quality }.all { session -> session.exercises.none { it.id in setOf("pogo-hop", "line-bound") } })
    }

    @Test fun allSkillAndConditioningFormatsHaveStructuredWorkTargets() {
        for (discipline in listOf("cal", "cond")) {
            for (goal in Engine15DisciplineProgression.goals.getValue(discipline)) {
                val plan = plan(discipline, goal)
                assertTrue(plan.sessions.all { it.exercises.size >= 2 && it.distanceKm == null })
                assertTrue(plan.sessions.first().exercises.all { it.reps != null || it.seconds != null })
                if (goal == "EMOM work capacity") {
                    assertTrue(plan.sessions.first().exercises.all { it.seconds!! + it.rest == 60 })
                }
                Engine15IndependentPlanGenerator.validateSaved(PlanSnapshot.decode(plan.encode(), plan.owner))
            }
        }
    }

    @Test fun missedSessionRecoveryKeepsDatesAndRestoresPreviousPrescription() {
        val baseline = plan("str", "Isometrics")
        val original = baseline.copy(sessions = baseline.sessions.mapIndexed { index, session ->
            if (index < 2) session.copy(status = "missed", missedReason = "Time") else session
        })
        val today = original.sessions[1].date
        val diagnostic = Engine15AthleteDiagnosticsEngine.evaluate(original, emptyList(), emptyMap(), emptyMap(), asOfDate = today)
        val proposal = requireNotNull(Engine15PlanAdaptationEngine.evaluateProposal(
            original, diagnostic, emptyList(), emptyMap(), asOfDate = today))
        assertEquals(AdaptationType.MISSED_WEEK_RECOVERY, proposal.type)
        val applied = Engine15PlanAdaptationEngine.applyProposal(original, proposal, today)
        assertEquals(original.sessions.map { it.date }, applied.appliedPlan.sessions.map { it.date })
        assertEquals(original.sessions.take(2), applied.appliedPlan.sessions.take(2))
        assertTrue(applied.appliedPlan.sessions.zip(original.sessions).any { (after, before) ->
            after.exercises.sumOf { it.sets * (it.seconds ?: 0) } < before.exercises.sumOf { it.sets * (it.seconds ?: 0) }
        })
        val restored = Engine15PlanAdaptationEngine.revertRevision(applied.appliedPlan, applied.revision, today)
        assertEquals(original.sessions, restored.restoredPlan.sessions)
        Engine15IndependentPlanGenerator.validateSaved(restored.restoredPlan)
    }

    @Test fun concurrentTracksFitAroundRunningAndPreserveTheExistingPlan() {
        val library = ExerciseLibraryV2.load { File("src/main/assets", it).inputStream() }
        val runs = (0L..7L).flatMap { week ->
            val first = start.plusWeeks(week)
            listOf(
                PlannedSession("speed-$week", first, "run", "Speed", 30, 5.0, "quality", quality = true),
                PlannedSession("long-$week", first.plusDays(4), "run", "Long run", 60, 10.0, "long")
            )
        }
        val active = PlanSnapshot("active-run", "owner", ENGINE15_VERSION,
            ENGINE15_INDEPENDENT_RUNNING_POLICY, Instant.EPOCH,
            PlanRequest(start = start, weeks = 8, runDays = 2), runs, emptyList(), "approved")
        for (goal in listOf("Plyometrics", "Isometrics", "Strength for Runners")) {
            val requested = request("str", goal).copy(strengthDays = 1)
            val preview = Engine15IndependentPlanGenerator.generateWithHistory(
                requested, "owner", listOf(active), "support", Instant.EPOCH, library)
            assertEquals(goal, plannedDisciplineLabel(preview.sessions.first()))
            assertTrue(preview.sessions.none { candidate ->
                runs.any { Engine15IndependentPlanGenerator.conflict(candidate, it) }
            })
            assertEquals("approved", PlanOperations.approve(preview, listOf(active)).status)
            assertEquals(runs, active.sessions)
        }
        val base = request("str", "General strength")
        val plyo = chooseStrengthTrack(base, "plyo", false)
        assertEquals("plyo", strengthTrack(plyo))
        assertEquals("iso", strengthTrack(chooseStrengthTrack(plyo, "iso", false)))
    }

    @Test fun generationRoutesNewGoalsAndKeepsLegacySnapshotsReadable() {
        val library = ExerciseLibraryV2.load { File("src/main/assets", it).inputStream() }
        for (goal in listOf("Conditioning", "Threshold intervals")) {
            val request = request("cond", goal)
            val draft = Engine15IndependentPlanGenerator.generateWithHistory(
                request, "owner", emptyList(), "test", Instant.EPOCH, library)
            Engine15IndependentPlanGenerator.validateSaved(PlanSnapshot.decode(draft.encode(), draft.owner))
            assertEquals(goal == "Threshold intervals", draft.sessions.first().quality)
        }
        val request = request("str", "Upper Body & Posture").copy(
            disciplineGoals = mapOf("str" to "Upper Body & Posture", "cond" to "Threshold intervals"),
            conditioningDays = 1, weeklySlots = mapOf("str" to listOf(1, 4), "cond" to listOf(2)))
        val combined = Engine15IndependentPlanGenerator.generateWithHistory(
            request, "owner", emptyList(), "combined", Instant.EPOCH, library)
        Engine15IndependentPlanGenerator.validateSaved(combined)
        assertTrue(combined.sessions.any { it.discipline == "cond" && it.quality })
    }

    @Test fun fixedPlyometricsAndIsometricsActivateIndependentlyWithoutStrengthExperience() {
        val library = ExerciseLibraryV2.load { File("src/main/assets", it).inputStream() }
        fun draft(goal: String, others: List<PlanSnapshot>) = Engine15IndependentPlanGenerator.generateWithHistory(
            request("str", goal).copy(strengthExperience = 0, days = setOf(1, 4),
                weeklySlots = mapOf("str" to listOf(1, 4))),
            "owner", others, goal.lowercase(), Instant.EPOCH, library
        )
        val plyo = PlanOperations.approve(draft("Plyometrics", emptyList()), emptyList())
        val iso = PlanOperations.approve(draft("Isometrics", listOf(plyo)), listOf(plyo))
        assertEquals(plyo.sessions.map { it.date }, iso.sessions.map { it.date })
        assertEquals(12, plyo.sessions.first().exercises.sumOf { it.sets * requireNotNull(it.reps) })
        assertTrue(iso.sessions.first().exercises.all { it.seconds != null })
        assertNotNull(PlanScheduleValidation.crossPlanRecoveryNote(iso, listOf(plyo)))
        PlanScheduleValidation.validateRestoration(listOf(plyo, iso))
    }
}
