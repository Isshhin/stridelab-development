package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class Engine15Batch2AuditFixesTest {
    private val owner = "batch2-audit-runner"
    private val now = Instant.parse("2026-11-21T08:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }
    private val fullEquipment = mapOf(
        "bands" to 1, "anchor" to 1, "dumbbells" to 1, "barbell" to 1, "bench" to 1, "pullup_bar" to 1
    )

    @Test
    fun lowBaselineMarathonLongRunRespectsDualTenPercentCap() {
        val req = PlanRequest(
            goal = "Marathon",
            experience = 1,
            start = monday,
            weeks = 12,
            days = setOf(1, 2, 4, 6, 7),
            runDays = 5,
            longDay = 6,
            qualityCount = 0,
            weeklyKm = 28.0,
            longestKm = 8.0,
            targetWeeklyKm = 50.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val decision = Engine15IndependentPlanGenerator.preview(req, owner, "batch2-low-marathon", now)
            as Engine15IndependentPlanDecision.Candidate
        val caps = decision.volume.weeks.map { it.longRunExposureCapKm!! }
        // Week 1 cap is min(8 + 1.0, 8 * 1.10) = 8.8 km
        assertEquals(8.8, caps[0], 1e-8)

        var prevCap = caps[0]
        for (i in 1 until caps.size) {
            assertTrue(
                "Week ${i + 1} longRunExposureCapKm ${caps[i]} spiked > 10% over $prevCap",
                caps[i] <= prevCap * 1.10 + 1e-6
            )
            prevCap = caps[i]
        }

        val longRuns = decision.plan.sessions.filter { it.kind == "long" }.sortedBy { it.date }
        assertTrue(longRuns[0].distanceKm!! <= 8.8 + 1e-6)
        var peakLong = req.longestKm
        for (i in longRuns.indices) {
            val cur = longRuns[i].distanceKm!!
            assertTrue("Week ${i + 1} long run $cur km spiked > 10% over peak $peakLong km", cur <= peakLong * 1.10 + 1e-6)
            peakLong = maxOf(peakLong, cur)
        }
    }

    @Test
    fun returningRunnerLongRunCapScalesWithReturnEnvelope() {
        val req = PlanRequest(
            goal = "Half marathon",
            experience = 2,
            start = monday,
            weeks = 8,
            days = setOf(1, 3, 5, 7),
            runDays = 4,
            longDay = 7,
            qualityCount = 0,
            weeklyKm = 50.0,
            longestKm = 18.0,
            targetWeeklyKm = 55.0,
            returning = true,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val decision = Engine15IndependentPlanGenerator.preview(req, owner, "batch2-returning", now)
            as Engine15IndependentPlanDecision.Candidate
        val w1Cap = decision.volume.weeks[0].longRunExposureCapKm!!
        assertEquals(13.5, w1Cap, 1e-8)
        val w1Long = decision.plan.sessions.first { it.kind == "long" }.distanceKm!!
        assertTrue("Returning runner Week-1 long run $w1Long km exceeded scaled cap 13.5 km", w1Long <= 13.5 + 1e-6)
    }

    @Test
    fun fifteenWeekMarathonAndTenWeekHalfAvoidDoubleDeloadBeforeTaper() {
        // 15-week Marathon: taper is W13..W15; W12 (index 11) must NOT be a 0.80x recovery double-deload
        val m15Req = PlanRequest(
            goal = "Marathon",
            experience = 2,
            start = monday,
            weeks = 15,
            days = setOf(1, 2, 4, 5, 6),
            runDays = 5,
            longDay = 6,
            qualityCount = 0,
            weeklyKm = 65.0,
            longestKm = 22.0,
            targetWeeklyKm = 75.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val m15 = Engine15IndependentPlanGenerator.preview(m15Req, owner, "batch2-m15", now)
            as Engine15IndependentPlanDecision.Candidate
        assertEquals(Engine15IndependentVolumePlanner.WeekKind.LOADING, m15.volume.weeks[11].kind)
        assertEquals(Engine15M6DPhase.BUILD_3, m15.periodization.weeks[11].phase)
        val m15Kms = m15.volume.weeks.map { it.proposedKm!! }
        // Taper 1 (W13, index 12) should be ~0.85x of peak W12 (index 11), NOT double-deloaded below 0.75x
        assertTrue("Expected W12 (${m15Kms[11]}) > W13 (${m15Kms[12]}) > W14 (${m15Kms[13]}) > W15 (${m15Kms[14]})",
            m15Kms[11] > m15Kms[12] && m15Kms[12] > m15Kms[13] && m15Kms[13] > m15Kms[14])
        assertTrue("Taper 1 (${m15Kms[12]}) should be >= 80% of W12 peak (${m15Kms[11]})",
            m15Kms[12] >= m15Kms[11] * 0.80)

        // 10-week Half marathon: taper is W9..W10; W8 (index 7) must stay LOADING
        val h10Req = m15Req.copy(goal = "Half marathon", weeks = 10, weeklyKm = 45.0, targetWeeklyKm = 55.0, longestKm = 16.0)
        val h10 = Engine15IndependentPlanGenerator.preview(h10Req, owner, "batch2-h10", now)
            as Engine15IndependentPlanDecision.Candidate
        assertEquals(Engine15IndependentVolumePlanner.WeekKind.LOADING, h10.volume.weeks[7].kind)
        val h10Kms = h10.volume.weeks.map { it.proposedKm!! }
        assertTrue(h10Kms[7] > h10Kms[8] && h10Kms[8] > h10Kms[9])
        assertTrue(h10Kms[8] >= h10Kms[7] * 0.78)
    }

    @Test
    fun taperWeeksCapLongRunShareAndQualityDose() {
        assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val req = PlanRequest(
            goal = "Marathon",
            experience = 2,
            start = monday,
            weeks = 12,
            days = setOf(1, 2, 3, 4, 5, 6),
            runDays = 6,
            longDay = 6,
            qualityCount = 2,
            qualityDays = setOf(2, 4),
            weeklyKm = 65.0,
            longestKm = 22.0,
            targetWeeklyKm = 75.0,
            planningPace = 330.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch2-taper-cap", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val byWeek = plan.sessions.groupBy { ChronoUnit.DAYS.between(monday, it.date).toInt() / 7 + 1 }
        val w9Long = byWeek.getValue(9).single { it.kind == "long" }.distanceKm!!
        for (wk in 10..12) {
            val rows = byWeek.getValue(wk)
            val weekKm = rows.sumOf { it.distanceKm!! }
            val longKm = rows.single { it.kind == "long" }.distanceKm!!
            val qCount = rows.count { it.quality }
            assertTrue("Taper week $wk quality count $qCount exceeded 1", qCount <= 1)
            assertTrue("Taper week $wk long run share ${longKm / weekKm} exceeded 0.31", longKm <= weekKm * 0.31 + 1e-6)
            assertTrue("Taper week $wk long run $longKm km exceeded W9 peak $w9Long km", longKm <= w9Long + 1e-6)
        }
        assertTrue(byWeek.getValue(10).single { it.quality }.reason.contains("Taper hold"))
    }

    @Test
    fun combinedTier3RespectsTimeBudgetAndMaxTwoSessionsPerDay() {
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 2, 4, 5, 6),
            runDays = 4,
            strengthDays = 2,
            conditioningDays = 1,
            longDay = 6,
            weeklyKm = 24.0,
            longestKm = 8.0,
            targetWeeklyKm = 24.0,
            planningPace = 360.0,
            minutes = 55,
            dailyMinutes = 75,
            timeBudgetEnabled = true,
            equipment = fullEquipment,
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength", "cond" to "Conditioning"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch2-tier3-budget", now, exerciseLibrary = library
        )
        val byDate = plan.sessions.groupBy { it.date }
        assertTrue("Expected <= 2 sessions/day", byDate.values.all { it.size <= 2 })
        assertTrue("Expected dailyMinutes <= 75", byDate.values.all { day -> day.sumOf { it.effectiveMinutes } <= 75 })

        val approved = PlanOperations.approve(plan, listOf(plan))
        PlanScheduleValidation.validatePlan(approved)
        Engine15IndependentPlanGenerator.validateSaved(approved)

        // Impossible dailyMinutes (e.g. 40 min with 8 km long run @ 6:00/km = 48 min) must defer cleanly
        val impossible = req.copy(dailyMinutes = 40, minutes = 40)
        assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(
                impossible, owner, emptyList(), "batch2-tier3-impossible", now, exerciseLibrary = library
            )
        }
    }

    @Test
    fun combinedTier3PrefersEasyPlusUpperOverLongPlusLower() {
        // 3 runs + 2 strength (one Lower, one Upper) across 4 available days => 1 double day required
        val req = PlanRequest(
            goal = "Aerobic foundation",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5, 6),
            runDays = 3,
            strengthDays = 2,
            longDay = 6,
            weeklyKm = 21.0,
            longestKm = 9.0,
            targetWeeklyKm = 21.0,
            timeBudgetEnabled = false,
            equipment = fullEquipment,
            disciplineGoals = mapOf("run" to "Aerobic foundation", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch2-easy-upper", now, exerciseLibrary = library
        )
        val byDate = plan.sessions.groupBy { it.date }
        val stackedDays = byDate.values.filter { it.size > 1 }
        assertTrue(stackedDays.isNotEmpty())
        assertTrue(
            "Long run must not be stacked with Lower-Body strength when Easy+Upper slot exists",
            stackedDays.none { day ->
                day.any { it.kind == "long" } && day.any { it.discipline == "str" && it.lower }
            }
        )
        val approved = PlanOperations.approve(plan, listOf(plan))
        PlanScheduleValidation.validatePlan(approved)
        Engine15IndependentPlanGenerator.validateSaved(approved)
    }

    @Test
    fun combinedPlanShapesRunningRecoveryAfterFinalDatesAndLowerStrength() {
        val req = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 2, 3, 4, 5, 6),
            runDays = 4,
            strengthDays = 2,
            longDay = 6,
            qualityCount = 0,
            weeklyKm = 36.0,
            longestKm = 13.0,
            targetWeeklyKm = 36.0,
            planningPace = 360.0,
            timeBudgetEnabled = false,
            equipment = fullEquipment,
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "batch2-combined-shaper", now, exerciseLibrary = library
        )
        val hardExtDates = plan.sessions.filter { it.discipline != "run" && it.lower }.map { it.date }.toSet()
        val runsAfterHardExt = plan.sessions.filter {
            it.discipline == "run" && it.kind != "long" && it.date.minusDays(1) in hardExtDates
        }
        assertTrue("Expected at least one run placed the day after lower-body strength", runsAfterHardExt.isNotEmpty())
        assertTrue(
            "Runs immediately following lower-body strength must be shaped as Recovery runs",
            runsAfterHardExt.all { it.title == "Recovery run" && it.runningPrescription?.role == "RECOVERY" }
        )

        val approved = PlanOperations.approve(plan, listOf(plan))
        val decoded = PlanSnapshot.decode(approved.encode(), owner)
        assertEquals(approved, decoded)
        PlanScheduleValidation.validatePlan(decoded)
        Engine15IndependentPlanGenerator.validateSaved(decoded)
    }
}
