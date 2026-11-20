package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TracksideCoachingIntegrationTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-05T00:00:00Z")
    // These cases assert historical planner compatibility directly; opt-in routing is covered separately.
    private fun generate(p: PlanRequest) = when (p.policyChoice) {
        "engine15" -> Engine15.generate(p, "athlete", "draft", now)
        "baseline" -> Engine1.generate(p, "athlete", "draft", now)
        "coordinated" -> CoordinatedEngine1.generate(p, "athlete", "draft", now)
        "multi-session" -> MultiSessionEngine1.generate(p, "athlete", "draft", now)
        else -> throw PlanConstraint("Choose a supported coaching engine policy.")
    }
    private fun legacyEngine15Request(p: PlanRequest) = p.copy(
        policyChoice = "engine15", allowDoubleDays = p.policyChoice == "multi-session"
    )

    @Test fun packagedDefaultSelectsTheConfiguredPolicy() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=4, days=setOf(1,2,3,5,6,7),
            runDays=3, weeklyKm=30.0, targetWeeklyKm=30.0, longestKm=10.0,
            recentMileageBasis="SELF_REPORTED_AVERAGE", timeBudgetEnabled=false)
        val packaged = TracksideCoaching.request(setup)
        assertEquals(BuildConfig.ENGINE15_ACTIVE, packaged.policyChoice == "engine15")
        val plan = if (BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
            Engine15IndependentPlanGenerator.generateWithHistory(packaged, "athlete", emptyList(), "draft", now)
        else generate(packaged)
        assertEquals(if (BuildConfig.ENGINE15_ACTIVE) ENGINE15_VERSION else ENGINE1_VERSION, plan.engineVersion)
        assertEquals(when {
            !BuildConfig.ENGINE15_ACTIVE -> CoordinatedEngine1.policyId
            BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE -> ENGINE15_INDEPENDENT_RUNNING_POLICY
            else -> ENGINE15_POLICY
        }, plan.policyId)
        assertEquals("approved", PlanOperations.approve(plan, emptyList()).status)
    }

    @Test fun rollbackRoutesOnlyNewDraftsAndPreservesPersistedEngineAndPrescriptions() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=4,
            experience=1, weeklyKm=15.0, longestKm=7.0, goal="Improve 10K")
        val active = generate(legacyEngine15Request(setup))
        val saved = active.encode()
        val rollback = generate(TracksideCoaching.request(TracksideCoaching.setupFor(active), false))
        assertEquals(ENGINE15_VERSION, active.engineVersion)
        assertEquals(ENGINE1_VERSION, rollback.engineVersion)
        assertEquals(CoordinatedEngine1.policyId, rollback.policyId)
        assertEquals(active, PlanSnapshot.decode(saved, active.owner))
        assertNotEquals(active.sessions.map { it.distanceKm }, rollback.sessions.map { it.distanceKm })
        assertTrue(Engine1Engines.canManage(rollback))
        assertEquals("approved", PlanOperations.approve(active, emptyList()).status)
        assertEquals("approved", PlanOperations.approve(rollback, emptyList()).status)
    }

    @Test fun explicitDoubleDayBudgetSurvivesSpareDaysAndNewDraftReuse() {
        val setup = PlanRequest(policyChoice="multi-session", start=start, weeks=1,
            days=(1..7).toSet(), longDay=7, runDays=3, calDays=1, dailyMinutes=60)
        val selected = legacyEngine15Request(setup)
        val saved = generate(selected)
        assertEquals(ENGINE15_MULTI_POLICY, saved.policyId)
        val decoded = PlanSnapshot.decode(saved.encode(), saved.owner)
        assertTrue(decoded.request.allowDoubleDays)
        assertEquals(setup, TracksideCoaching.setupFor(decoded))
        assertThrows(PlanConstraint::class.java) {
            generate(legacyEngine15Request(setup.copy(dailyMinutes=15)))
        }
        val oldRequest = selected.encode().apply { remove("allowDoubleDays") }
        val historical = PlanRequest.decode(oldRequest)
        assertFalse(historical.allowDoubleDays)
        assertEquals(ENGINE15_POLICY, generate(historical).policyId)
        assertFalse(historical.encode().has("allowDoubleDays"))
    }

    @Test fun fixedUpperStrengthMayShareEasyDayEvenWithUnusedWeekdays() {
        val setup = PlanRequest(policyChoice="multi-session", start=start, weeks=2,
            days=(1..7).toSet(), longDay=7, runDays=3, strengthDays=2,
            weeklySlots=mapOf("run" to listOf(7,2,5), "str" to listOf(3,5)),
            disciplineGoals=mapOf("run" to "Improve 5K", "str" to "Muscle development"))
        val plan = generate(legacyEngine15Request(setup))
        assertEquals(ENGINE15_INTERFERENCE_POLICY, plan.policyId)
        assertEquals(10, plan.sessions.size)
        plan.sessions.forEach { s -> assertEquals(WeeklyPlacement.fixedDay(setup,s), s.date.dayOfWeek.value) }
        assertEquals("approved", PlanOperations.approve(plan, emptyList()).status)
        val autoDouble = TracksideCoaching.request(setup.copy(policyChoice="coordinated"), true)
        assertTrue(autoDouble.allowDoubleDays)
        assertThrows(PlanConstraint::class.java) {
            generate(legacyEngine15Request(setup.copy(weeklySlots=setup.weeklySlots +
                ("str" to listOf(7,5)))))
        }
    }

    @Test fun coordinatedDisciplinePrescriptionSurvivesVersionedGeneration() {
        val setup = PlanRequest(policyChoice="coordinated", start=start, weeks=1,
            days=(1..7).toSet(), runDays=2, calDays=1, goal="Improve 10K",
            disciplineGoals=mapOf("run" to "Improve 10K", "cal" to "Core strength and control"),
            weeklySlots=mapOf("run" to listOf(6,2), "cal" to listOf(4)))
        val plan = generate(legacyEngine15Request(setup))
        val expected = generate(TracksideCoaching.request(setup, false))
        val cal = plan.sessions.single { it.discipline=="cal" }
        assertEquals(expected.sessions.single { it.discipline=="cal" }, cal)
        assertTrue(cal.reason.contains("Core strength and control"))
        assertEquals(4, cal.date.dayOfWeek.value)
    }
}
