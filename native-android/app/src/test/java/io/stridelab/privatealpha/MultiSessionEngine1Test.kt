package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MultiSessionEngine1Test {
    private val start = LocalDate.parse("2026-11-02")
    private val now = Instant.parse("2026-10-27T00:00:00Z")

    private fun request() = PlanRequest(
        goal = "Improve 5K",
        start = start,
        weeks = 4,
        minutes = 45,
        days = (1..7).toSet(),
        runDays = 5,
        calDays = 3,
        longDay = 6,
        weeklyKm = 30.0,
        longestKm = 12.0,
        dailyMinutes = 90,
        policyChoice = "multi-session"
    )

    private fun session(
        id: String,
        date: LocalDate,
        discipline: String,
        kind: String = discipline,
        quality: Boolean = false,
        lower: Boolean = false
    ) = PlannedSession(id, date, discipline, id, 30, null, kind, lower, quality)

    @Test fun eightWeeklySessionsUseSevenAvailableTrainingDaysAndKeepCountsDistinct() {
        val plan = MultiSessionEngine1.generate(request(), "synthetic-owner", "multi", now)
        assertEquals(MULTI_SESSION_POLICY, plan.policyId)
        assertEquals(32, plan.sessions.size)
        val week = plan.sessions.filter { it.date in start..start.plusDays(6) }
        assertEquals(8, week.size)
        assertEquals(7, week.map { it.date }.distinct().size)
        assertEquals(5, week.count { it.discipline == "run" })
        assertEquals(3, week.count { it.discipline == "cal" })
        val paired = week.groupBy { it.date }.values.filter { it.size > 1 }
        assertEquals(1, paired.size)
        assertEquals(setOf("run", "cal"), paired.single().map { it.discipline }.toSet())
        assertTrue(paired.single().any { it.kind != "long" && !it.quality })
        assertTrue(plan.sessions.groupBy { it.date }.all { (_, sessions) ->
            sessions.sumOf { it.effectiveMinutes } <= plan.request.dailyMinutes
        })
    }

    @Test fun onlyEasyNonLongRunningAndCalisthenicsCanShareADay() {
        val date = start.plusDays(1)
        val easy = session("easy", date, "run", "easy")
        val cal = session("cal", date, "cal", "calisthenics")
        assertTrue(MultiSessionScheduling.sameDayCompatible(easy, cal))
        assertFalse(MultiSessionScheduling.conflict(easy, cal))

        assertTrue(MultiSessionScheduling.conflict(session("quality", date, "run", "quality", true), cal))
        assertTrue(MultiSessionScheduling.conflict(session("recovery-quality", date, "run", "quality"), cal))
        assertTrue(MultiSessionScheduling.conflict(session("long", date, "run", "long"), cal))
        assertTrue(MultiSessionScheduling.conflict(session("str", date, "str"), cal))
        assertTrue(MultiSessionScheduling.conflict(cal, session("cal-2", date, "cal", "calisthenics")))
        assertTrue(Engine1.conflict(easy, cal))
    }

    @Test fun weeklyRecoverySpacingRejectsAdjacentQualityLowerAndLongRunWorkloads() {
        val tuesday = start.plusDays(1)
        val friday = start.plusDays(4)
        val saturday = start.plusDays(5)
        val quality = session("quality", tuesday, "run", "quality", quality = true)
        val lower = session("lower", tuesday.plusDays(1), "str", lower = true)
        val long = session("long", saturday, "run", "long")
        val lowerBeforeLong = session("lower-before-long", friday, "str", lower = true)
        assertTrue(MultiSessionScheduling.conflict(quality, lower))
        assertTrue(MultiSessionScheduling.conflict(lowerBeforeLong, long))

        val plan = MultiSessionEngine1.generate(request().copy(
            runDays = 4,
            calDays = 0,
            strengthDays = 2,
            qualityCount = 1,
            qualityDays = setOf(2)
        ), "synthetic-owner", "recovery", now)
        for (i in plan.sessions.indices) for (j in i + 1 until plan.sessions.size) {
            assertFalse(MultiSessionScheduling.conflict(plan.sessions[i], plan.sessions[j]))
        }
    }

    @Test fun dailyBudgetRejectsAnOverBudgetMoveAndAnOversizedSingleWorkout() {
        val plan = MultiSessionEngine1.generate(request().copy(
            weeks = 1,
            runDays = 1,
            calDays = 1,
            minutes = 35,
            weeklyKm = 5.0,
            longestKm = 3.0,
            dailyMinutes = 90
        ), "synthetic-owner", "budget", now)
        val run = plan.sessions.single { it.discipline == "run" }
        val cal = plan.sessions.single { it.discipline == "cal" }
        val pairBudget = maxOf(run.minutes, cal.minutes)
        assertTrue(run.minutes + cal.minutes > pairBudget)
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(plan.copy(request = plan.request.copy(dailyMinutes = pairBudget)), cal.id, run.date)
        }
        assertThrows(PlanConstraint::class.java) {
            MultiSessionEngine1.generate(request().copy(
                weeks = 1, runDays = 1, calDays = 0, minutes = 45,
                weeklyKm = 0.0, dailyMinutes = 15
            ), "synthetic-owner", "too-long", now)
        }
    }

    @Test fun calendarMovementAllowsCompatiblePairAndRejectsIncompatiblePair() {
        val plan = MultiSessionEngine1.generate(request(), "synthetic-owner", "move", now)
        val week = plan.sessions.filter { it.date in start..start.plusDays(6) }
        val soloCal = week.filter { it.discipline == "cal" }.first { cal ->
            week.none { it.date == cal.date && it.discipline == "run" }
        }
        val soloEasyRun = week.first { run ->
            run.discipline == "run" && run.kind != "long" && !run.quality &&
                week.none { it.date == run.date && it.discipline == "cal" }
        }
        val moved = PlanOperations.move(plan, soloCal.id, soloEasyRun.date)
        assertEquals(2, moved.sessions.count { it.date == soloEasyRun.date })
        assertFalse(MultiSessionScheduling.conflict(
            moved.sessions.first { it.id == soloCal.id },
            moved.sessions.first { it.id == soloEasyRun.id }
        ))

        val longDay = week.first { it.discipline == "run" && it.kind == "long" }
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(plan, soloCal.id, longDay.date)
        }
    }

    @Test fun calendarMovementChecksOtherApprovedPlansUnderBothPolicies() {
        val selected = MultiSessionEngine1.generate(request().copy(
            weeks = 1, runDays = 0, calDays = 1
        ), "synthetic-owner", "selected", now)
        val baselineRequest = PlanRequest(
            goal = "Improve 5K", start = start, weeks = 1, days = (1..7).toSet(),
            runDays = 1, longDay = 6, policyChoice = "baseline"
        )
        val other = Engine1.generate(baselineRequest, "synthetic-owner", "other", now)
        val approvedSelected = PlanOperations.approve(selected, emptyList())
        val approvedOther = PlanOperations.approve(other, listOf(approvedSelected))
        val cal = approvedSelected.sessions.single()
        val longRun = approvedOther.sessions.single { it.kind == "long" }
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(approvedSelected, cal.id, longRun.date, listOf(approvedSelected, approvedOther))
        }

        val easyRunPlan = MultiSessionEngine1.generate(request().copy(
            weeks = 1, runDays = 2, calDays = 0
        ), "synthetic-owner", "easy-approved", now)
        val easyRun = easyRunPlan.sessions.single { it.kind != "long" }
        val compatibleCal = selected.sessions.single().copy(date = easyRun.date)
        val compatiblePlan = approvedSelected.copy(sessions = listOf(compatibleCal))
        val approvedEasy = PlanOperations.approve(easyRunPlan, emptyList())
        val moved = PlanOperations.move(compatiblePlan, compatibleCal.id, easyRun.date, listOf(compatiblePlan, approvedEasy))
        assertEquals(easyRun.date, moved.sessions.single().date)
    }

    @Test fun movementEnforcesSharedDailyBudgetAgainstApprovedCompatiblePlan() {
        val generated=MultiSessionEngine1.generate(request().copy(
            weeks=1,runDays=2,calDays=1,dailyMinutes=120
        ),"synthetic-owner","budget-cross",now)
        val cal=generated.sessions.single { it.discipline=="cal" }
        val easy=generated.sessions.single { it.discipline=="run"&&it.kind!="long" }
        assertNotEquals(cal.date,easy.date)
        val budget=cal.minutes+easy.minutes-1
        val moving=generated.copy(
            sessions=listOf(cal),status="approved",
            request=generated.request.copy(dailyMinutes=budget)
        )
        val other=generated.copy(
            id="approved-easy",sessions=listOf(easy),status="approved",
            request=generated.request.copy(dailyMinutes=budget)
        )
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.move(moving,cal.id,easy.date,listOf(moving,other))
        }
    }

    @Test fun approvalAllowsCompatibleCrossPlanPairButRejectsIncompatibilityAndSharedBudgetOverflow() {
        val plan = MultiSessionEngine1.generate(request().copy(
            weeks = 1, runDays = 2, calDays = 1
        ), "synthetic-owner", "cross-plan", now)
        val run = plan.sessions.first { it.discipline == "run" && it.kind != "long" }
        val cal = plan.sessions.single { it.discipline == "cal" }
        val existing = plan.copy(id = "approved-run", sessions = listOf(run), status = "approved")
        val draft = plan.copy(id = "draft-cal", sessions = listOf(cal.copy(date = run.date)))
        assertEquals("approved", PlanOperations.approve(draft, listOf(existing)).status)

        val longRun = plan.sessions.first { it.discipline == "run" && it.kind == "long" }
        val incompatible = draft.copy(sessions = listOf(cal.copy(date = longRun.date)))
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(incompatible, listOf(existing.copy(sessions = listOf(longRun))))
        }

        val sharedBudget = maxOf(run.minutes, cal.minutes)
        val budgetExisting = existing.copy(
            request = existing.request.copy(dailyMinutes = sharedBudget),
            sessions = listOf(run.copy(minutes = run.minutes))
        )
        val overBudget = draft.copy(
            request = draft.request.copy(dailyMinutes = sharedBudget),
            sessions = listOf(cal.copy(date = run.date))
        )
        assertThrows(PlanConstraint::class.java) {
            PlanOperations.approve(overBudget, listOf(budgetExisting))
        }
    }

    @Test fun eachSameDayWorkoutKeepsItsOwnLogAndApprovedSnapshotAcrossRoundtrip() {
        val draft = MultiSessionEngine1.generate(request(), "synthetic-owner", "logging", now)
        val approved = PlanOperations.approve(draft, emptyList())
        val pair = approved.sessions.groupBy { it.date }.values.first { it.size == 2 }
        val run = pair.single { it.discipline == "run" }
        val cal = pair.single { it.discipline == "cal" }
        val withRun = PlanOperations.record(
            approved, run.id, WorkoutLog(now, minutes = 27.0, distanceKm = 4.2), true
        )
        val withBoth = PlanOperations.record(
            withRun, cal.id, WorkoutLog(now.plusSeconds(60), minutes = 24.0, effort = 4), true
        )
        assertEquals(2, withBoth.sessions.count { it.status == "completed" })
        assertEquals(4.2, withBoth.sessions.single { it.id == run.id }.log!!.distanceKm!!, 0.0)
        assertNull(withBoth.sessions.single { it.id == cal.id }.log!!.distanceKm)
        val restored = PlanSnapshot.decode(withBoth.encode(), "synthetic-owner")
        assertEquals(withBoth, restored)
        assertEquals("approved", restored.status)
    }

    @Test fun policySelectionAndOldSnapshotsPreserveTheVerifiedFallback() {
        val baselineRequest = PlanRequest(start = start)
        val baseline = Engine1.generate(baselineRequest, "synthetic-owner", "baseline", now)
        assertFalse(baseline.encode().getJSONObject("request").has("dailyMinutes"))
        assertEquals(90, MultiSessionEngine1.generate(request(), "synthetic-owner", "multi-request", now)
            .encode().getJSONObject("request").getInt("dailyMinutes"))
        assertSame(Engine1, Engine1Engines.resolve(baselineRequest))
        assertEquals(baseline, Engine1Engines.resolve(baselineRequest).generate(
            baselineRequest, "synthetic-owner", "baseline", now
        ))
        assertThrows(PlanConstraint::class.java) {
            Engine1.generate(baselineRequest.copy(runDays = 5, calDays = 3), "synthetic-owner", "legacy")
        }
        assertSame(MultiSessionEngine1, Engine1Engines.resolve(request()))
        Engine1Engines.validate(request())
        assertThrows(PlanConstraint::class.java) { Engine1.validate(request()) }
        assertThrows(PlanConstraint::class.java) {
            Engine1Engines.validate(baselineRequest.copy(runDays = 5, calDays = 3))
        }

        val oldJson = baseline.encode()
        oldJson.getJSONObject("request").remove("dailyMinutes")
        val old = PlanSnapshot.decode(oldJson, "synthetic-owner")
        assertEquals(90, old.request.dailyMinutes)
        assertEquals(ENGINE1_POLICY, old.policyId)
        assertEquals(baseline.sessions, old.sessions)
    }
}
