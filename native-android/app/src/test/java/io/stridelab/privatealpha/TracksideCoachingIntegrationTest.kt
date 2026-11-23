package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TracksideCoachingIntegrationTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-05T00:00:00Z")
    private val library = ExerciseLibraryV2.load { path -> java.io.File("src/main/assets", path).inputStream() }
    private fun generate(p: PlanRequest) =
        Engine15IndependentPlanGenerator.generateWithHistory(p, "athlete", emptyList(), "draft", now, library)

    @Test fun packagedDefaultAlwaysSelectsEngine15Independent() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=4, days=setOf(1,2,3,5,6,7),
            runDays=3, weeklyKm=30.0, targetWeeklyKm=30.0, longestKm=10.0,
            recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false)
        val packaged = TracksideCoaching.request(setup)
        assertEquals("engine15", packaged.policyChoice)
        assertSame(Engine15IndependentPlanGenerator, Engine1Engines.resolve(packaged))
        val plan = generate(packaged)
        assertEquals(ENGINE15_VERSION, plan.engineVersion)
        assertEquals(ENGINE15_INDEPENDENT_RUNNING_POLICY, plan.policyId)
        assertEquals("approved", PlanOperations.approve(plan, emptyList()).status)
    }

    @Test fun newDraftsFromOldSetupNormalizeToEngine15WithoutFallback() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=4,
            experience=1, weeklyKm=15.0, longestKm=7.0, goal="Improve 10K",
            recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false)
        val active = generate(TracksideCoaching.request(setup))
        val saved = active.encode()
        val rebuilt = TracksideCoaching.request(TracksideCoaching.setupFor(active))
        assertEquals("engine15", rebuilt.policyChoice)
        assertEquals(ENGINE15_VERSION, active.engineVersion)
        assertEquals(ENGINE15_INDEPENDENT_RUNNING_POLICY, active.policyId)
        assertEquals(active, PlanSnapshot.decode(saved, active.owner))
        assertTrue(Engine1Engines.canManage(active))
        assertEquals("approved", PlanOperations.approve(active, emptyList()).status)
    }

    @Test fun explicitDoubleDayBudgetSurvivesSpareDaysAndNewDraftReuse() {
        val setup = PlanRequest(policyChoice="multi-session", start=start, weeks=1,
            days=(1..7).toSet(), longDay=7, runDays=3, calDays=1, dailyMinutes=60,
            weeklyKm=24.0, longestKm=10.0, recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false,
            equipment=mapOf("bands" to 1, "anchor" to 1))
        val selected = TracksideCoaching.request(setup)
        assertEquals("engine15", selected.policyChoice)
        assertTrue(selected.allowDoubleDays)
        val saved = generate(selected)
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, saved.policyId)
        val decoded = PlanSnapshot.decode(saved.encode(), saved.owner)
        assertTrue(decoded.request.allowDoubleDays)
        val nextSetup = TracksideCoaching.setupFor(decoded)
        assertEquals("engine15", nextSetup.policyChoice)
        assertFalse(nextSetup.allowDoubleDays)
    }

    @Test fun fixedUpperStrengthMayShareEasyDayWithRunningInCombinedPlan() {
        val setup = PlanRequest(policyChoice="multi-session", start=start, weeks=2,
            days=(1..7).toSet(), longDay=7, runDays=3, strengthDays=2,
            weeklyKm=24.0, longestKm=10.0, recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false,
            equipment=mapOf("db" to 1, "bands" to 1, "anchor" to 1),
            weeklySlots=mapOf("run" to listOf(7,2,5), "str" to listOf(3,5)),
            disciplineGoals=mapOf("run" to "Improve 5K", "str" to "Muscle development"))
        val plan = generate(TracksideCoaching.request(setup))
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
        assertEquals(10, plan.sessions.size)
        plan.sessions.filter { it.discipline=="run" }.forEach { s ->
            assertEquals(WeeklyPlacement.fixedDay(setup,s), s.date.dayOfWeek.value)
        }
        assertEquals("approved", PlanOperations.approve(plan, emptyList()).status)
        val autoDouble = TracksideCoaching.request(setup.copy(policyChoice="coordinated"))
        assertTrue(autoDouble.allowDoubleDays)
    }

    @Test fun coordinatedDisciplinePrescriptionUsesIndependentCombinedPolicy() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=1,
            days=(1..7).toSet(), runDays=2, calDays=1, goal="Improve 10K",
            weeklyKm=18.0, longestKm=10.0, recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false,
            disciplineGoals=mapOf("run" to "Improve 10K", "cal" to "Core strength and control"),
            weeklySlots=mapOf("run" to listOf(6,2), "cal" to listOf(4)))
        val plan = generate(TracksideCoaching.request(setup))
        val cal = plan.sessions.single { it.discipline=="cal" }
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, plan.policyId)
        assertEquals(4, cal.date.dayOfWeek.value)
        assertEquals(3, plan.sessions.size)
    }

    @Test fun runningAndAnExplicitCombinedPlanIgnoreSeparateStrengthCalendarAsABlocker() {
        val strengthRequest = PlanRequest(goal="Strength for Runners", start=start, weeks=4,
            days=(1..7).toSet(), runDays=0, strengthDays=1, weeklySlots=mapOf("str" to listOf(7)),
            timeBudgetEnabled=false)
        val strength = PlanOperations.approve(generate(strengthRequest), emptyList())
        val runningRequest = PlanRequest(start=start, weeks=4, days=setOf(2, 5, 7), longDay=7,
            runDays=3, weeklyKm=24.0, longestKm=10.0, recentMileageBasis="RECORDED_PARTIAL",
            timeBudgetEnabled=false)

        val run = Engine15IndependentPlanGenerator.generateWithHistory(
            TracksideCoaching.request(runningRequest), "athlete", listOf(strength), "running", now, library
        )
        assertEquals(ENGINE15_INDEPENDENT_RUNNING_POLICY, run.policyId)
        assertEquals(strength.sessions.map { it.date }, run.sessions.filter { it.kind == "long" }.map { it.date })
        assertNotNull(PlanScheduleValidation.crossPlanRecoveryNote(run, listOf(strength)))
        val approvedRun = PlanOperations.approve(run, listOf(strength))
        PlanScheduleValidation.validateRestoration(listOf(strength, approvedRun))
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(run.copy(id = "duplicate-running-plan"), listOf(approvedRun))
        }

        val mixed = Engine15IndependentPlanGenerator.generateWithHistory(
            TracksideCoaching.request(runningRequest.copy(
                calDays=1, disciplineGoals=mapOf("cal" to "Push"), weeklySlots=mapOf("cal" to listOf(2))
            )), "athlete", listOf(strength), "mixed", now, library
        )
        assertEquals(ENGINE15_INDEPENDENT_COMBINED_POLICY, mixed.policyId)
        assertEquals("approved", PlanOperations.approve(mixed, listOf(strength)).status)
    }
}
