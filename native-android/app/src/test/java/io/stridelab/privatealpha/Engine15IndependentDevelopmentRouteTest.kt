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

        // Adding strength on top of 6 running days adapts automatically via multi-discipline double days.
        val adaptedCombined = Engine15IndependentPlanGenerator.generateWithHistory(
            selected.copy(strengthDays = 1, strengthExperience = 1, equipment = mapOf("db" to 2, "bands" to 1, "anchor" to 1)),
            owner, emptyList(), "adapted-multi", now,
            ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, adaptedCombined.policyId)
        assertEquals(56, adaptedCombined.sessions.size)
        Engine15IndependentPlanGenerator.validateSaved(adaptedCombined)
    }

    @Test fun independentRouteAndSavedConflictValidationHaveNoLegacyPlannerCalls() {
        val root = java.io.File("src/main/java/io/stridelab/privatealpha")
        val forbidden = Regex("\\b(?:Engine1\\.(?:generate|validate)|MultiSessionEngine1|CoordinatedEngine1|Engine15InterferenceScheduling)\\b")
        val independentCallGraph = listOf(
            "Engine15IndependentPlanGenerator.kt", "Engine15AthleteCapacity.kt",
            "Engine15IndependentVolumePlanner.kt", "Engine15M5WorkoutComposer.kt",
            "Engine15M6IndependentCalendar.kt", "Engine15M6IndependentInterferenceReview.kt",
            "Engine15M6IndependentPeriodizationReview.kt", "Engine15StrengthWorkoutComposer.kt",
            "Engine15CalisthenicsWorkoutComposer.kt", "Engine15ConditioningWorkoutComposer.kt",
            "Engine15StaticMultiDisciplinePlanner.kt", "Engine15QualityFamilySelection.kt"
        )
        independentCallGraph.forEach { source ->
            assertFalse("Legacy planning dependency in $source",
                forbidden.containsMatchIn(java.io.File(root, source).readText()))
        }
        val validation = java.io.File(root, "PlanScheduleValidation.kt").readText()
        val independentBranch = validation.substringAfter("if(policyA in setOf(ENGINE15_INDEPENDENT_RUNNING_POLICY, ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, ENGINE15_INDEPENDENT_CONDITIONING_POLICY, ENGINE15_INDEPENDENT_COMBINED_POLICY) ||")
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
        assertTrue(mixed.message.orEmpty().contains("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT"))
        val notes = assertThrows(PlanConstraint::class.java) {
            Engine15StrengthWorkoutComposer.validateRequest(strength.copy(limitations = "shoulder pain"))
        }
        assertTrue(notes.message.orEmpty().contains("STRENGTH_NOTES_REQUIRE_REVIEW"))
    }

    @Test fun calisthenicsAndConditioningRequestsDeferUnsupportedCombinations() {
        val foundation = PlanRequest(
            goal = "Calisthenics foundation", start = LocalDate.parse("2026-11-16"), weeks = 8,
            days = setOf(1, 3, 5, 7), runDays = 0, calDays = 2, calLevel = 0,
            equipment = mapOf("bands" to 1, "anchor" to 1), policyChoice = "engine15"
        )
        Engine15CalisthenicsWorkoutComposer.validateRequest(foundation)
        val mixed = assertThrows(PlanConstraint::class.java) {
            Engine15CalisthenicsWorkoutComposer.validateRequest(foundation.copy(conditioningDays = 1))
        }
        assertTrue(mixed.message.orEmpty().contains("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT"))
        val handstand = assertThrows(PlanConstraint::class.java) {
            Engine15CalisthenicsWorkoutComposer.validateRequest(foundation.copy(goal = "Handstand foundations"))
        }
        assertTrue(handstand.message.orEmpty().contains("CALISTHENICS_HANDSTAND_EVIDENCE_UNAVAILABLE"))
        val mixedConditioning = assertThrows(PlanConstraint::class.java) {
            Engine15ConditioningWorkoutComposer.validateRequest(PlanRequest(
                goal = "Conditioning", start = foundation.start, weeks = 8, days = foundation.days,
                runDays = 1, conditioningDays = 1, policyChoice = "engine15"
            ))
        }
        assertTrue(mixedConditioning.message.orEmpty().contains("MIXED_DISCIPLINE_GENERATION_NOT_INDEPENDENT"))
    }

    @Test fun calisthenicsComposesCompleteEightWeekCatalogBackedDraft() {
        val request = PlanRequest(
            goal = "Calisthenics foundation", start = LocalDate.parse("2026-11-18"), weeks = 8,
            days = setOf(1, 3, 5, 7), runDays = 0, calDays = 2,
            equipment = mapOf("bands" to 1, "anchor" to 1), policyChoice = "engine15"
        )
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(request, owner, emptyList(), "cal-eight", now, library)
        assertEquals(16, plan.sessions.size)
        assertEquals(ENGINE15_INDEPENDENT_CALISTHENICS_POLICY, plan.policyId)
        assertTrue(plan.sessions.all { session ->
            session.discipline == "cal" && session.exercises.map { it.id }.containsAll(listOf("e4", "br", "fc"))
        })
        assertTrue(plan.sessions.filter { it.date >= request.start.plusWeeks(3) && it.date < request.start.plusWeeks(4) }
            .all { it.exercises.all { exercise -> exercise.sets == 1 } })
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test fun conditioningComposesCompleteEightWeekTimedDraftWithRecoveryBlocks() {
        val request = PlanRequest(
            goal = "Conditioning", start = LocalDate.parse("2026-11-18"), weeks = 8,
            days = setOf(1, 3, 5, 7), runDays = 0, conditioningDays = 2,
            policyChoice = "engine15"
        )
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(request, owner, emptyList(), "cond-eight", now, library)
        assertEquals(16, plan.sessions.size)
        assertEquals(ENGINE15_INDEPENDENT_CONDITIONING_POLICY, plan.policyId)
        assertTrue(plan.sessions.all { session ->
            session.discipline == "cond" && session.kind == "conditioning" &&
                session.exercises.single().id == "cond-step-jack" && session.steps.size == 3
        })
        val minutesByWeek = (0..7).map { week ->
            plan.sessions.first { it.date >= request.start.plusWeeks(week.toLong()) && it.date < request.start.plusWeeks(week + 1L) }.minutes
        }
        assertEquals(listOf(15, 17, 19, 15, 15, 17, 19, 15), minutesByWeek)
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test fun combinedRunningAndCalisthenicsPlanPreservesDoubleDayRulesAndLocks() {
        val start = LocalDate.parse("2026-11-18")
        val request = PlanRequest(
            goal = "Aerobic foundation", start = start, weeks = 8,
            days = setOf(2, 4, 6), runDays = 3, calDays = 1, longDay = 6,
            weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0,
            timeBudgetEnabled = false, policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            equipment = mapOf("bands" to 1, "anchor" to 1),
            disciplineGoals = mapOf("cal" to "Calisthenics foundation"),
            weeklySlots = mapOf("run" to listOf(6, 2, 4), "cal" to listOf(2)),
            lockedSessionDays = mapOf("combined-eight-w1-cal0" to start.plusDays(6).toString()),
            allowDoubleDays = true
        )
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(request, owner, emptyList(), "combined-eight", now, library)
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
        assertEquals(32, plan.sessions.size)
        for (week in 0 until 8) {
            val monday = start.plusWeeks(week.toLong())
            val sessions = plan.sessions.filter { it.date >= monday && it.date < monday.plusWeeks(1) }
            assertEquals(4, sessions.size)
            val sameDay = sessions.groupBy { it.date }.filterValues { it.size > 1 }
            assertEquals(1, sameDay.size)
            assertEquals(setOf("run", "cal"), sameDay.values.single().map { it.discipline }.toSet())
            assertEquals(6, sessions.single { it.kind == "long" }.date.dayOfWeek.value)
        }
        Engine15IndependentPlanGenerator.validateSaved(plan)
        val withoutFlag = Engine15IndependentPlanGenerator.generateWithHistory(
            request.copy(allowDoubleDays = false), owner, emptyList(), "combo-auto-adapt", now, library
        )
        assertEquals(32, withoutFlag.sessions.size)
        Engine15IndependentPlanGenerator.validateSaved(withoutFlag)
    }

    @Test fun combinedStrengthCalisthenicsAndConditioningAutoScheduleWithoutHardDayCollisions() {
        val request = PlanRequest(
            goal = "General strength", start = LocalDate.parse("2026-11-18"), weeks = 8,
            days = (1..7).toSet(), runDays = 0, strengthDays = 1, calDays = 1, conditioningDays = 1,
            strengthExperience = 1, calLevel = 0, equipment = mapOf("db" to 2, "bands" to 1, "anchor" to 1),
            timeBudgetEnabled = false, policyChoice = "engine15"
        )
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(request, owner, emptyList(), "static-mix", now, library)
        assertEquals(24, plan.sessions.size)
        for (week in 0 until 8) {
            val first = request.start.plusWeeks(week.toLong())
            val sessions = plan.sessions.filter { it.date >= first && it.date < first.plusWeeks(1) }
            assertEquals(setOf("str", "cal", "cond"), sessions.map { it.discipline }.toSet())
            assertEquals(3, sessions.map { it.date }.distinct().size)
        }
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test fun combinedPlannerHonorsValidLockOutsideComposerDefaultWeekday() {
        val start = LocalDate.parse("2026-11-18")
        val request = PlanRequest(
            goal = "General strength", start = start, weeks = 8,
            days = (1..7).toSet(), runDays = 0, strengthDays = 1, calDays = 1,
            strengthExperience = 1, calLevel = 0, equipment = mapOf("db" to 2, "bands" to 1, "anchor" to 1),
            policyChoice = "engine15",
            disciplineGoals = mapOf("cal" to "Calisthenics foundation"),
            lockedSessionDays = mapOf("locked-mix-w1-str0" to start.toString())
        )
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            request, owner, emptyList(), "locked-mix", now, library
        )
        assertEquals(start, plan.sessions.single { it.id.endsWith("-w1-str0") }.date)
        Engine15IndependentPlanGenerator.validateSaved(plan)
    }

    @Test fun runningCombinesWithStrengthConditioningAndAllSupportedDisciplines() {
        val start = LocalDate.parse("2026-11-18")
        val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
        val requests = listOf(
            PlanRequest(
                goal = "Aerobic foundation", start = start, weeks = 8, days = (1..7).toSet(),
                runDays = 3, strengthDays = 1, longDay = 6, weeklyKm = 30.0,
                targetWeeklyKm = 30.0, longestKm = 10.0, recentMileageBasis = "SELF_REPORTED_AVERAGE", timeBudgetEnabled = false,
                disciplineGoals = mapOf("str" to "General strength"), strengthExperience = 1,
                equipment = mapOf("db" to 2), policyChoice = "engine15"
            ),
            PlanRequest(
                goal = "Aerobic foundation", start = start, weeks = 8, days = (1..7).toSet(),
                runDays = 3, conditioningDays = 1, longDay = 6, weeklyKm = 30.0,
                targetWeeklyKm = 30.0, longestKm = 10.0, recentMileageBasis = "SELF_REPORTED_AVERAGE", timeBudgetEnabled = false,
                disciplineGoals = mapOf("cond" to "Conditioning"), policyChoice = "engine15"
            ),
            PlanRequest(
                goal = "Aerobic foundation", start = start, weeks = 8, days = (1..7).toSet(),
                runDays = 3, strengthDays = 1, calDays = 1, conditioningDays = 1, longDay = 6,
                weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE", timeBudgetEnabled = false, strengthExperience = 1,
                equipment = mapOf("db" to 2, "bands" to 1, "anchor" to 1),
                disciplineGoals = mapOf("str" to "General strength", "cal" to "Calisthenics foundation", "cond" to "Conditioning"),
                policyChoice = "engine15"
            )
        )
        requests.take(2).forEachIndexed { index, request ->
            val plan = try {
                Engine15IndependentPlanGenerator.generateWithHistory(
                    request, owner, emptyList(), "running-mix-$index", now, library
                )
            } catch (e: PlanConstraint) { throw AssertionError("Combination $index deferred: ${e.message}", e) }
            assertEquals(request.weeks * (request.runDays + request.strengthDays + request.calDays + request.conditioningDays), plan.sessions.size)
            assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
            Engine15IndependentPlanGenerator.validateSaved(plan)
        }
        val allFour = Engine15IndependentPlanGenerator.generateWithHistory(
            requests.last(), owner, emptyList(), "all-four-mix", now, library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, allFour.policyId)
        assertEquals(requests.last().weeks * 6, allFour.sessions.size)
        Engine15IndependentPlanGenerator.validateSaved(allFour)
    }
}
