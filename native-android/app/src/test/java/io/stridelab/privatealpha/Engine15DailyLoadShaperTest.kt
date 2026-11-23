package io.stridelab.privatealpha

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class Engine15DailyLoadShaperTest {
    private val owner = "shaper-runner"
    private val now = Instant.parse("2026-11-21T08:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")
    private val library by lazy {
        ExerciseLibraryV2.load { path -> File("src/main/assets", path).inputStream() }
    }

    private fun baseRunRequest(
        weeks: Int = 8,
        runDays: Int = 6,
        days: Set<Int> = setOf(1, 2, 3, 4, 5, 6),
        longDay: Int = 6,
        qualityCount: Int = 2,
        qualityDays: Set<Int> = setOf(2, 4),
        goal: String = "Aerobic foundation",
        weeklyKm: Double = 71.0,
        targetKm: Double? = 71.0,
        longestKm: Double = 22.0,
        userDirected: Boolean = true,
        allowedQualityTypes: Set<String> = setOf("Tempo", "Threshold")
    ) = PlanRequest(
        goal = goal,
        experience = 2,
        start = monday,
        weeks = weeks,
        days = days,
        runDays = runDays,
        longDay = longDay,
        qualityCount = qualityCount,
        qualityDays = qualityDays,
        allowedQualityTypes = allowedQualityTypes,
        weeklyKm = weeklyKm,
        targetWeeklyKm = targetKm,
        longestKm = longestKm,
        planningPace = 420.0,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE",
        userDirectedPlanning = userDirected
    )

    @Test
    fun sixDayTwoQualityPlanCreatesShorterRecoveryRunsWhileReconcilingExactWeeklyBudget() {
        val req = baseRunRequest()
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "six-day-shaper", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        val week1 = plan.sessions.filter { it.date < monday.plusWeeks(1) }.sortedBy { it.date }
        assertEquals(6, week1.size)
        assertEquals(71.0, week1.sumOf { it.distanceKm!! }, 1e-6)

        val mon = week1[0] // Mon Easy
        val tue = week1[1] // Tue Quality
        val wed = week1[2] // Wed Recovery (sandwiched between Tue Quality & Thu Quality)
        val thu = week1[3] // Thu Quality
        val fri = week1[4] // Fri Recovery (sandwiched between Thu Quality & Sat Long)
        val sat = week1[5] // Sat Long

        assertTrue(tue.quality)
        assertTrue(thu.quality)
        assertEquals("long", sat.kind)
        assertEquals("Recovery run", wed.title)
        assertEquals("Recovery run", fri.title)
        assertEquals("RECOVERY", wed.runningPrescription?.role)
        assertEquals("RECOVERY", fri.runningPrescription?.role)

        // Sandwiched recovery runs must be materially shorter than fresh easy & quality days
        assertTrue("Expected Wed recovery (${wed.distanceKm}) < Mon easy (${mon.distanceKm})", wed.distanceKm!! < mon.distanceKm!!)
        assertTrue("Expected Wed recovery (${wed.distanceKm}) < Tue quality (${tue.distanceKm})", wed.distanceKm!! < tue.distanceKm!!)
        assertTrue("Expected Fri recovery (${fri.distanceKm}) < Thu quality (${thu.distanceKm})", fri.distanceKm!! < thu.distanceKm!!)
        assertTrue("Expected Tue quality (${tue.distanceKm}) >= Mon easy (${mon.distanceKm})", tue.distanceKm!! >= mon.distanceKm!!)
    }

    @Test
    fun crossWeekSundayLongShapesMondayAsRecoveryRun() {
        val req = baseRunRequest(
            weeks = 4,
            runDays = 4,
            days = setOf(1, 3, 5, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 44.0,
            targetKm = 44.0,
            longestKm = 15.0,
            allowedQualityTypes = setOf("Threshold")
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "cross-week-rec", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        // Week 2 Monday immediately follows Week 1 Sunday Long
        val w2Mon = plan.sessions.single { it.date == monday.plusWeeks(1) }
        val w2Fri = plan.sessions.single { it.date == monday.plusWeeks(1).plusDays(4) }
        assertEquals("Recovery run", w2Mon.title)
        assertEquals("RECOVERY", w2Mon.runningPrescription?.role)
        assertTrue(w2Mon.distanceKm!! < w2Fri.distanceKm!!)
    }

    @Test
    fun lowMileageAndTimeBudgetPlansRespectFloorCapsAndNeverExceedLongRun() {
        val req = PlanRequest(
            goal = "Aerobic foundation",
            experience = 1,
            start = monday,
            weeks = 4,
            days = setOf(1, 3, 5, 6),
            runDays = 4,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 16.0,
            targetWeeklyKm = 16.0,
            longestKm = 6.0,
            planningPace = 420.0,
            minutes = 45,
            timeBudgetEnabled = true,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            userDirectedPlanning = false
        )
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            req, owner, emptyList(), "low-mileage-cap", now
        )
        Engine15IndependentPlanGenerator.validateSaved(plan)

        for (w in 0 until 4) {
            val weekStart = monday.plusWeeks(w.toLong())
            val rows = plan.sessions.filter { it.date >= weekStart && it.date < weekStart.plusWeeks(1) }
            val longRun = rows.single { it.kind == "long" }
            val nonLong = rows.filter { it.kind != "long" }
            assertTrue(rows.all { it.minutes <= 45 })
            assertTrue(nonLong.all { it.distanceKm!! >= 1.5 && it.distanceKm!! <= longRun.distanceKm!! + 1e-6 })
        }

        // Recovery week (week 4) still differentiates post-hard/pre-long recovery and keeps exact total
        val week4 = plan.sessions.filter { it.date >= monday.plusWeeks(3) }
        assertTrue(week4.any { it.runningPrescription?.role == "RECOVERY" })
        assertTrue(week4.none { it.quality })
    }

    @Test
    fun track3RaceTapersActivateCleanlyForOddAndEvenMultiWeekHorizons() {
        val marathon13 = baseRunRequest(
            weeks = 13,
            runDays = 5,
            days = setOf(1, 2, 4, 5, 6),
            qualityCount = 1,
            qualityDays = setOf(2),
            goal = "Marathon",
            weeklyKm = 65.0,
            targetKm = 70.0,
            longestKm = 22.0,
            userDirected = false
        )
        val m13Plan = Engine15IndependentPlanGenerator.generateWithHistory(
            marathon13, owner, emptyList(), "marathon-13w", now
        )
        Engine15IndependentPlanGenerator.validateSaved(m13Plan)
        val m13Totals = (0 until 13).map { w ->
            val s = monday.plusWeeks(w.toLong())
            m13Plan.sessions.filter { it.date >= s && it.date < s.plusWeeks(1) }.sumOf { it.distanceKm!! }
        }
        assertTrue(m13Totals[10] > m13Totals[11] && m13Totals[11] > m13Totals[12])
        assertTrue(m13Totals[12] < m13Totals.max() * 0.65)

        val half10 = baseRunRequest(
            weeks = 10,
            runDays = 4,
            days = setOf(1, 2, 4, 6),
            qualityCount = 1,
            qualityDays = setOf(2),
            goal = "Half marathon",
            weeklyKm = 45.0,
            targetKm = 50.0,
            longestKm = 16.0,
            userDirected = false
        )
        val h10Plan = Engine15IndependentPlanGenerator.generateWithHistory(
            half10, owner, emptyList(), "half-10w", now
        )
        Engine15IndependentPlanGenerator.validateSaved(h10Plan)
        val h10Totals = (0 until 10).map { w ->
            val s = monday.plusWeeks(w.toLong())
            h10Plan.sessions.filter { it.date >= s && it.date < s.plusWeeks(1) }.sumOf { it.distanceKm!! }
        }
        assertTrue(h10Totals[8] > h10Totals[9])
        assertTrue(h10Totals[9] < h10Totals.max() * 0.70)
    }

    @Test
    fun track3LiveFeedbackAndWeeklyReviewWorkOnIndependentRunningAndCombinedPlans() {
        val asOf = monday
        val reviewAt = Instant.parse("2026-11-23T08:00:00Z")
        val recentStart = asOf.minusWeeks(1)
        val pastReq = PlanRequest(
            goal = "Aerobic foundation",
            experience = 2,
            start = recentStart,
            weeks = 4,
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = 4,
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            allowedQualityTypes = setOf("Tempo"),
            weeklyKm = 36.0,
            targetWeeklyKm = 36.0,
            longestKm = 14.0,
            planningPace = 420.0,
            timeBudgetEnabled = false,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            userDirectedPlanning = true
        )
        val runDraft = Engine15IndependentPlanGenerator.generateWithHistory(
            pastReq, owner, emptyList(), "live-fb-run", now
        )
        val approvedRun = PlanOperations.approve(runDraft, emptyList())
        val firstRun = approvedRun.sessions.first { it.date <= asOf && it.date >= asOf.minusDays(2) }
        val highEffort = PlanOperations.record(
            approvedRun,
            firstRun.id,
            WorkoutLog(
                at = reviewAt,
                minutes = firstRun.minutes.toDouble(),
                distanceKm = firstRun.distanceKm,
                effort = 9,
                performedOn = firstRun.date,
                completionDifficulty = "Harder"
            ),
            complete = true
        )
        val proposals = Engine15Feedback.preview(highEffort, asOf)
        assertTrue("Expected feedback proposal for independent running plan", proposals.isNotEmpty())
        val staged = Engine15Feedback.stage(highEffort, proposals.first(), asOf)
        Engine15Feedback.requireCurrentPendingAcceptance(staged, proposals.first().targetSessionId, asOf)
        val acceptedSuggestion = PlanOperations.acceptSuggestion(staged, proposals.first().targetSessionId)
        assertTrue(acceptedSuggestion.sessions.any { it.suggestion?.accepted == true })

        val review = Engine15WeeklyReview.preview(highEffort, asOf, Engine15WeeklyReview.Input(fatigue = 4), now = reviewAt)
        assertTrue("Expected weekly review changes for independent running plan", review.changes.isNotEmpty())
        val acceptedReview = Engine15WeeklyReview.accept(highEffort, review, asOf, reviewAt)
        assertTrue(acceptedReview.feedback.isNotEmpty())

        // Also verify ENGINE15_INDEPENDENT_COMBINED_POLICY is enabled for Feedback & WeeklyReview
        val combinedReq = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = recentStart,
            weeks = 4,
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = 3,
            strengthDays = 1,
            longDay = 6,
            weeklyKm = 24.0,
            targetWeeklyKm = 24.0,
            longestKm = 10.0,
            timeBudgetEnabled = false,
            equipment = mapOf("db" to 2, "bench" to 1),
            disciplineGoals = mapOf("run" to "Improve 10K", "str" to "General strength"),
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        val combinedDraft = Engine15IndependentPlanGenerator.generateWithHistory(
            combinedReq, owner, emptyList(), "live-fb-combined", now, exerciseLibrary = library
        )
        val approvedCombined = PlanOperations.approve(combinedDraft, emptyList())
        val combFirstRun = approvedCombined.sessions.first { it.discipline == "run" && it.date <= asOf && it.date >= asOf.minusDays(2) }
        val combLogged = PlanOperations.record(
            approvedCombined,
            combFirstRun.id,
            WorkoutLog(
                at = reviewAt,
                minutes = combFirstRun.minutes.toDouble(),
                distanceKm = combFirstRun.distanceKm,
                effort = 9,
                performedOn = combFirstRun.date,
                discomfort = "Soreness"
            ),
            complete = true
        )
        assertTrue(Engine15Feedback.preview(combLogged, asOf).isNotEmpty())
        assertTrue(Engine15WeeklyReview.preview(combLogged, asOf, now = reviewAt).changes.isNotEmpty())
    }
}
