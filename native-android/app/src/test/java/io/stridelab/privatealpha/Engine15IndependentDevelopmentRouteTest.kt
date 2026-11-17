package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15IndependentDevelopmentRouteTest {
    private val owner = "independent-route-owner"
    private val now = Instant.parse("2026-11-17T00:00:00Z")
    private fun request() = PlanRequest(
        goal = "Aerobic foundation", start = LocalDate.parse("2026-11-16"), weeks = 8,
        days = setOf(1, 2, 3, 5, 6, 7), runDays = 6, longDay = 6,
        weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0,
        timeBudgetEnabled = false, policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    @Test fun onlyTheDevelopmentOptInSelectsTheIndependentRunningGenerator() {
        val resolved = Engine1Engines.resolve(request())
        assertSame(if (BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
            Engine15IndependentPlanGenerator else Engine15, resolved)
    }

    @Test fun developmentRouteValidatesAndBuildsAManagedRunningDraftWithoutFallback() {
        if (!BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE) return
        val setup = request().copy(policyChoice = "coordinated")
        val selected = TracksideCoaching.request(setup, engine15Active = true)
        assertEquals("engine15", selected.policyChoice)
        assertSame(Engine15IndependentPlanGenerator, Engine1Engines.resolve(selected))

        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            selected, owner, emptyList(), "route-draft", now
        )
        assertEquals(ENGINE15_INDEPENDENT_RUNNING_POLICY, plan.policyId)
        assertEquals("draft", plan.status)
        assertEquals(48, plan.sessions.size)
        assertTrue(Engine1Engines.canManage(plan))
        assertTrue(plan.sessions.all { it.discipline == "run" && !it.quality })

        val unsupported = assertThrows(PlanConstraint::class.java) {
            Engine1Engines.validate(selected.copy(strengthDays = 1))
        }
        assertTrue(unsupported.message.orEmpty().contains("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT"))
    }

    @Test fun independentRouteAndSavedConflictValidationHaveNoLegacyPlannerCalls() {
        val root = java.io.File("src/main/java/io/stridelab/privatealpha")
        val forbidden = Regex("\\b(?:Engine1\\.(?:generate|validate)|MultiSessionEngine1|CoordinatedEngine1|Engine15InterferenceScheduling)\\b")
        val independentCallGraph = listOf(
            "Engine15IndependentPlanGenerator.kt", "Engine15AthleteCapacity.kt",
            "Engine15IndependentVolumePlanner.kt", "Engine15M5WorkoutComposer.kt",
            "Engine15M6IndependentCalendar.kt", "Engine15M6IndependentInterferenceReview.kt",
            "Engine15M6IndependentPeriodizationReview.kt", "Engine15StrengthWorkoutComposer.kt"
        )
        independentCallGraph.forEach { source ->
            assertFalse("Legacy planning dependency in $source",
                forbidden.containsMatchIn(java.io.File(root, source).readText()))
        }
        val validation = java.io.File(root, "PlanScheduleValidation.kt").readText()
        val independentBranch = validation.substringAfter("if(policyA in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY) ||")
            .substringBefore("else if(policyA==ENGINE15_INTERFERENCE_POLICY")
        assertTrue(independentBranch.contains("Engine15IndependentPlanGenerator.conflict"))
        assertFalse(independentBranch.contains("Engine1."))
        val repository = java.io.File(root, "TrainingRepository.kt").readText()
        assertTrue(repository.contains("Engine15IndependentPlanGenerator.generateWithHistory("))
        val routing = java.io.File(root, "Engine1Engines.kt").readText()
        assertTrue(routing.contains("if(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE) Engine15IndependentPlanGenerator else Engine15"))
    }

    @Test fun strengthRequestValidationDefersMixedPlansAndUnreviewedNotes() {
        val strength = PlanRequest(
            goal = "General strength", start = LocalDate.parse("2026-11-16"), weeks = 8,
            days = setOf(1, 3, 5, 7), runDays = 0, strengthDays = 2,
            strengthExperience = 1, policyChoice = "engine15"
        )
        Engine15StrengthWorkoutComposer.validateRequest(strength)
        val mixed = assertThrows(PlanConstraint::class.java) {
            Engine15StrengthWorkoutComposer.validateRequest(strength.copy(runDays = 2))
        }
        assertTrue(mixed.message.orEmpty().contains("STRENGTH_REQUEST_UNSUPPORTED"))
        val notes = assertThrows(PlanConstraint::class.java) {
            Engine15StrengthWorkoutComposer.validateRequest(strength.copy(limitations = "shoulder pain"))
        }
        assertTrue(notes.message.orEmpty().contains("STRENGTH_NOTES_REQUIRE_REVIEW"))
    }
}
