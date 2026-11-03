package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Deterministic scheduling and persistence contract, not an efficacy benchmark. */
class Engine15MultiScheduleTest {
    private val start = LocalDate.parse("2026-11-09")
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private fun request() = PlanRequest(
        goal = "Improve 5K", policyChoice = "engine15", start = start,
        weeks = 4, minutes = 45, days = setOf(1,2,3,4,5,7),
        runDays = 5, calDays = 2, longDay = 7, qualityDays = setOf(2),
        qualityCount = 1, experience = 2, weeklyKm = 60.0,
        longestKm = 16.0, planningPace = 400.0, dailyMinutes = 100
    )

    @Test fun sevenSessionsOnSixDaysRespectCountsBudgetAndCalendar() {
        val req = request()
        assertThrows(PlanConstraint::class.java) {
            Engine1.generate(req.copy(policyChoice = "baseline"), "owner", "old", now)
        }
        val plan = Engine15.generate(req, "owner", "candidate", now)
        assertEquals(ENGINE15_MULTI_POLICY, plan.policyId)
        assertEquals(plan, Engine15.generate(req, "owner", "candidate", now))
        assertEquals(plan, PlanSnapshot.decode(plan.encode(), "owner"))
        assertEquals(28, plan.sessions.size)
        assertEquals(28, plan.sessions.map { it.id }.distinct().size)
        for (week in 0..3) {
            val first = start.plusWeeks(week.toLong())
            val scheduled = plan.sessions.filter { it.date >= first && it.date < first.plusWeeks(1) }
            assertEquals(7, scheduled.size)
            assertEquals(5, scheduled.count { it.discipline == "run" })
            assertEquals(2, scheduled.count { it.discipline == "cal" })
            assertEquals(1, scheduled.count { it.kind == "long" && it.date.dayOfWeek.value == 7 })
            assertTrue(scheduled.all { it.date.dayOfWeek.value in req.days })
            assertTrue(scheduled.groupBy { it.date }.values.all { day ->
                day.size <= 2 && day.sumOf { it.effectiveMinutes } <= req.dailyMinutes
            })
            assertTrue(scheduled.filter { it.discipline == "run" }.sumOf { it.distanceKm ?: 0.0 }
                <= Engine15.runningCeiling(week, req.weeklyKm) + 1e-6)
        }
        for (i in plan.sessions.indices) for (j in i+1 until plan.sessions.size)
            assertFalse(MultiSessionScheduling.conflict(plan.sessions[i], plan.sessions[j]))
        PlanScheduleValidation.validatePlan(PlanOperations.approve(plan, emptyList()))
    }

    @Test fun impossibleMixedCalendarAndInsufficientDailyBudgetFailGracefully() {
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(
                strengthDays = 2, days = setOf(2,3,4,5,6,7),
                dailyMinutes = 120), "owner", "over", now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(dailyMinutes = 15), "owner", "budget", now)
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15.generate(request().copy(qualityDays = setOf(7)), "owner", "quality", now)
        }
    }

    @Test fun movingCalisthenicsToCompatibleEasyRunKeepsBudgetAndAvailability() {
        val plan = PlanOperations.approve(
            Engine15.generate(request(), "owner", "moving", now), emptyList())
        val week = plan.sessions.filter { it.date >= start && it.date < start.plusWeeks(1) }
        val cal = week.first { s -> s.discipline == "cal" &&
            week.none { it.discipline == "run" && it.date == s.date } }
        val easy = week.first { s -> s.discipline == "run" && s.kind == "easy" &&
            week.none { it.discipline == "cal" && it.date == s.date } }
        val moved = PlanOperations.move(plan, cal.id, easy.date)
        assertEquals(2, moved.sessions.count { it.date == easy.date })
        assertEquals(ENGINE15_MULTI_POLICY, moved.policyId)
        PlanScheduleValidation.validatePlan(moved)
        assertEquals(moved, PlanSnapshot.decode(moved.encode(), "owner"))
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(plan, cal.id, start.plusDays(5))
        }
        val two = cal.minutes + easy.minutes
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(
                plan.copy(request = plan.request.copy(dailyMinutes = two - 1)),
                cal.id, easy.date)
        }
    }

    @Test fun legacyFallbackPolicyAndDailyBudgetRemainDurable() {
        val plan = Engine15.generate(request(), "owner", "saved", now)
        assertEquals(100, plan.encode().getJSONObject("request").getInt("dailyMinutes"))
        assertTrue(Engine1Engines.canManage(plan))
        assertFalse(Engine1Engines.supports(ENGINE1_VERSION, ENGINE15_MULTI_POLICY))
        val legacy = Engine1.generate(
            request().copy(policyChoice = "baseline", runDays = 4, calDays = 0,
                dailyMinutes = 90),
            "owner", "legacy", now)
        assertEquals(ENGINE1_POLICY, legacy.policyId)
        assertFalse(legacy.encode().getJSONObject("request").has("dailyMinutes"))
        assertEquals(legacy, PlanSnapshot.decode(legacy.encode(), "owner"))
        val approved = PlanOperations.approve(plan, emptyList())
        assertThrows(PlanConstraint::class.java) {
            PlanScheduleValidation.validatePlan(
                approved.copy(request = approved.request.copy(dailyMinutes = 20)))
        }
    }
}
