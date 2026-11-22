package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class Engine15Phase2CoachingTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = Instant.parse("2026-11-16T12:00:00Z")

    private fun raceRequest(
        goal: String,
        weeks: Int,
        weeklyKm: Double = 50.0,
        targetKm: Double = 72.0,
        longestKm: Double = 16.0,
        runDays: Int = 5
    ) = PlanRequest(
        goal = goal,
        experience = 2,
        start = monday,
        weeks = weeks,
        days = setOf(1, 2, 3, 4, 5, 6, 7),
        runDays = runDays,
        longDay = 7,
        qualityCount = 0,
        weeklyKm = weeklyKm,
        targetWeeklyKm = targetKm,
        longestKm = longestKm,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        trainYourWay = false,
        recentMileageBasis = "SELF_REPORTED_AVERAGE",
        reportedPeakWeeklyKm = maxOf(weeklyKm, targetKm)
    )

    @Test
    fun marathonTwelveWeekProgressesLongRunBeyondWeekOneAndTapers() {
        val req = raceRequest("Marathon", weeks = 12, weeklyKm = 52.0, targetKm = 82.0, longestKm = 16.0, runDays = 5)
        val decision = Engine15IndependentPlanGenerator.preview(req, "owner", "m-plan", now)
        assertTrue("Expected Candidate, got $decision", decision is Engine15IndependentPlanDecision.Candidate)
        val candidate = decision as Engine15IndependentPlanDecision.Candidate

        val longCaps = candidate.volume.weeks.map { it.longRunExposureCapKm!! }
        assertEquals(17.0, longCaps[0], 1e-9)
        assertTrue("Long run cap must progress on peak build weeks: $longCaps", longCaps[8] >= 25.0)
        // Recovery weeks do not extend long-run cap
        assertEquals(longCaps[2], longCaps[3], 1e-9)
        assertEquals(longCaps[6], longCaps[7], 1e-9)
        // Taper weeks preserve cap without extending
        assertEquals(longCaps[8], longCaps[9], 1e-9)
        assertEquals(longCaps[8], longCaps[11], 1e-9)

        val peakWeekLong = candidate.plan.sessions
            .filter { it.kind == "long" && it.date >= monday.plusWeeks(8) && it.date < monday.plusWeeks(9) }
            .single().distanceKm!!
        assertTrue("Peak Marathon long run should exceed Week-1 17 km cap, got $peakWeekLong", peakWeekLong > 17.0)

        // Exact weekly budget equality on every week (Track 1 + Track 4)
        for (w in 0 until 12) {
            val ws = monday.plusWeeks(w.toLong())
            val we = ws.plusWeeks(1)
            val weekSum = candidate.plan.sessions.filter { it.date >= ws && it.date < we }.sumOf { it.distanceKm!! }
            assertEquals("Week ${w + 1} sum mismatch", candidate.volume.weeks[w].proposedKm!!, weekSum, 1e-6)
        }
    }

    @Test
    fun halfMarathonTenWeekProgressesLongRunAndTapersFinalTwoWeeks() {
        val req = raceRequest("Half marathon", weeks = 10, weeklyKm = 42.0, targetKm = 62.0, longestKm = 14.0, runDays = 4)
        val decision = Engine15IndependentPlanGenerator.preview(req, "owner", "hm-plan", now)
        assertTrue("Expected Candidate, got $decision", decision is Engine15IndependentPlanDecision.Candidate)
        val candidate = decision as Engine15IndependentPlanDecision.Candidate

        val caps = candidate.volume.weeks.map { it.longRunExposureCapKm!! }
        assertEquals(15.0, caps[0], 1e-9)
        assertTrue("Half marathon cap should progress beyond 15 km: $caps", caps[6] >= 19.0)
        assertTrue("RACE_TAPER_VOLUME_REDUCTION" in candidate.volume.weeks[8].constraintCodes)
        assertTrue("RACE_TAPER_VOLUME_REDUCTION" in candidate.volume.weeks[9].constraintCodes)
        assertTrue(candidate.volume.weeks[9].proposedKm!! < candidate.volume.weeks[8].proposedKm!!)
    }

    @Test
    fun fiveKSharpeningTaperAppliesOnSixEightAndNineWeekPlans() {
        for (w in listOf(6, 8, 9)) {
            val req = raceRequest("Improve 5K", weeks = w, weeklyKm = 36.0, targetKm = 48.0, longestKm = 12.0, runDays = 4)
            val decision = Engine15IndependentPlanGenerator.preview(req, "owner", "5k-$w", now)
            assertTrue("Expected Candidate for $w-week 5K, got $decision", decision is Engine15IndependentPlanDecision.Candidate)
            val candidate = decision as Engine15IndependentPlanDecision.Candidate
            val lastWeek = candidate.volume.weeks.last()
            val prevWeek = candidate.volume.weeks[candidate.volume.weeks.lastIndex - 1]
            assertEquals(Engine15IndependentVolumePlanner.WeekKind.RECOVERY, lastWeek.kind)
            assertTrue("Expected taper flag in week $w: ${lastWeek.constraintCodes}",
                "RACE_TAPER_VOLUME_REDUCTION" in lastWeek.constraintCodes)
            assertTrue("Final 5K taper week (${lastWeek.proposedKm}) must stay below prior week (${prevWeek.proposedKm})",
                lastWeek.proposedKm!! < prevWeek.proposedKm!!)
            assertEquals(Engine15M6DPhase.RECOVERY, candidate.periodization.weeks.last().phase)
        }
    }

    @Test
    fun nullRaceGoalRetainsLegacyStaticLongRunCeiling() {
        val req = PlanRequest(
            goal = "Aerobic foundation", experience = 2, start = monday, weeks = 8,
            days = setOf(1, 2, 3, 4, 5, 6, 7), runDays = 6, longDay = 7, qualityCount = 0,
            weeklyKm = 75.0, targetWeeklyKm = 80.0, longestKm = 20.0,
            timeBudgetEnabled = false, policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE", reportedPeakWeeklyKm = 85.0
        )
        val vol = Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(req),
            Engine15IndependentVolumePlanner.Input(8, 6, 80.0, raceGoal = null)
        )
        assertTrue(vol.weeks.all { it.longRunExposureCapKm == 21.0 })
        assertTrue(vol.weeks.all { it.provisionalSessionCapacityKm == 105.0 })
    }

    @Test
    fun dailyLoadShaperUsesProgressiveWeeklyEasyCeiling() {
        val req = raceRequest("Marathon", weeks = 12, weeklyKm = 52.0, targetKm = 82.0, longestKm = 16.0, runDays = 5)
        val decision = Engine15IndependentPlanGenerator.preview(req, "owner", "m-shaper", now)
        val candidate = decision as Engine15IndependentPlanDecision.Candidate
        val w9Cap = candidate.volume.weeks[8].longRunExposureCapKm!! * 0.8
        val w9NonLong = candidate.plan.sessions.filter {
            it.kind != "long" && it.date >= monday.plusWeeks(8) && it.date < monday.plusWeeks(9)
        }
        assertTrue(w9NonLong.all { it.distanceKm!! <= w9Cap + 1e-6 })
    }

    private fun completedEasy(id: String, date: LocalDate, effort: Int, minutes: Int = 50, km: Double = 8.0, discomfort: String? = null) =
        PlannedSession(
            id = id, date = date, discipline = "run", title = "Easy run", minutes = minutes, distanceKm = km,
            kind = "easy", status = "completed", logRevision = 1,
            log = WorkoutLog(now.minusSeconds(3600), minutes = minutes.toDouble(), distanceKm = km, effort = effort, performedOn = date, discomfort = discomfort)
        )

    @Test
    fun consistentLowRpeWeekProposesAtomicProgressionAndPreservesBaseline() {
        val asOf = monday.plusDays(6)
        val evalNow = asOf.atTime(18, 0).toInstant(ZoneOffset.UTC)
        val completed = listOf(
            completedEasy("c1", monday, effort = 3),
            completedEasy("c2", monday.plusDays(2), effort = 4),
            completedEasy("c3", monday.plusDays(4), effort = 3)
        )
        val upcoming = listOf(
            PlannedSession("u-rec", asOf.plusDays(1), "run", "Recovery run", 35, 5.0, "easy",
                runningPrescription = RunningPrescription("RECOVERY", null, listOf(RunningSegment("RECOVERY", 5000, "Easy")))),
            PlannedSession("u-qual", asOf.plusDays(2), "run", "Threshold", 50, 9.0, "quality", quality = true),
            PlannedSession("u-easy", asOf.plusDays(4), "run", "Easy run", 50, 8.0, "easy"),
            PlannedSession("u-long", asOf.plusDays(6), "run", "Long run", 88, 15.0, "long")
        )
        val request = PlanRequest(policyChoice = "engine15", timeBudgetEnabled = true, dailyMinutes = 90)
        val plan = PlanSnapshot("p1", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, now, request, completed + upcoming, emptyList(), status = "approved")

        val review = Engine15WeeklyReview.preview(plan, asOf, Engine15WeeklyReview.Input(fatigue = 2), now = evalNow)
        assertEquals(listOf("u-long", "u-easy"), review.changes.map { it.sessionId })
        assertTrue(review.changes.all { it.direction == Engine15WeeklyReview.Direction.PROGRESS })
        // u-long capped by dailyMinutes = 90 (88 + ceil(88*0.05)=5 -> 93 capped to 90)
        assertEquals(90, review.changes.first { it.sessionId == "u-long" }.minutes)
        // u-easy gets 50 + 3 = 53 min
        assertEquals(53, review.changes.first { it.sessionId == "u-easy" }.minutes)

        val accepted = Engine15WeeklyReview.accept(plan, review, asOf, evalNow)
        val updatedEasy = accepted.sessions.single { it.id == "u-easy" }
        assertEquals(8.0, updatedEasy.distanceKm!!, 1e-9)
        assertEquals(53, updatedEasy.effectiveMinutes)

        val target = Engine15SessionPresentation.forSession(accepted, "u-easy")
        assertEquals(Engine15SessionPresentation.State.ACCEPTED_PROGRESSION, target.state)
        assertEquals(53, target.currentMinutes)
        assertEquals(8.4, target.currentDistanceTargetKm!!, 1e-9)

        val decoded = PlanSnapshot.decode(accepted.encode(), "owner")
        assertEquals(accepted, decoded)
    }

    @Test
    fun garminAerobicEfficiencyGainTriggersProgressionAndShowsPercentChange() {
        val asOf = monday.plusDays(6)
        val evalNow = asOf.atTime(18, 0).toInstant(ZoneOffset.UTC)
        val activities = listOf(
            ActivityRecord("a1", "Easy", "run", "garmin", asOf.minusWeeks(3).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2880.0, 2880.0, 360.0, 150.0, 20.0),
            ActivityRecord("a1b", "Easy", "run", "garmin", asOf.minusWeeks(2).minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2880.0, 2880.0, 360.0, 150.0, 20.0),
            ActivityRecord("a2", "Easy", "run", "garmin", asOf.minusWeeks(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2880.0, 2880.0, 360.0, 149.0, 20.0),
            ActivityRecord("a3", "Easy", "run", "garmin", asOf.minusWeeks(1).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2760.0, 2760.0, 345.0, 142.0, 20.0),
            ActivityRecord("a3b", "Easy", "run", "garmin", asOf.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2740.0, 2740.0, 342.5, 141.0, 20.0),
            ActivityRecord("a4", "Easy", "run", "garmin", asOf.atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2720.0, 2720.0, 340.0, 140.0, 20.0)
        )
        val completed = listOf(
            completedEasy("c1", monday, effort = 4),
            completedEasy("c2", monday.plusDays(3), effort = 5)
        )
        val upcoming = listOf(PlannedSession("u-long", asOf.plusDays(5), "run", "Long run", 80, 14.0, "long"))
        val plan = PlanSnapshot("p-ef", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, now, PlanRequest(policyChoice = "engine15", timeBudgetEnabled = false), completed + upcoming, emptyList(), status = "approved")

        val review = Engine15WeeklyReview.preview(plan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow, activities = activities)
        assertNotNull(review.aerobicEfficiencyPercentChange)
        assertTrue(review.aerobicEfficiencyPercentChange!! >= 2.0)
        assertEquals(1, review.changes.size)
        assertEquals(Engine15WeeklyReview.Direction.PROGRESS, review.changes.single().direction)

        val accepted = Engine15WeeklyReview.accept(plan, review, asOf, evalNow, activities = activities)
        assertTrue(accepted.sessions.single { it.id == "u-long" }.suggestion!!.accepted)
    }

    @Test
    fun highFatiguePainOrSingleEasyRunNeverProposesProgression() {
        val asOf = monday.plusDays(6)
        val evalNow = asOf.atTime(18, 0).toInstant(ZoneOffset.UTC)
        val upcoming = listOf(PlannedSession("u-long", asOf.plusDays(5), "run", "Long run", 80, 14.0, "long"))
        val basePlan = PlanSnapshot("p-guard", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, now, PlanRequest(policyChoice = "engine15"), emptyList(), emptyList(), status = "approved")

        // Single easy run -> maintain, no progression
        val singlePlan = basePlan.copy(sessions = listOf(completedEasy("c1", monday, effort = 2)) + upcoming)
        assertTrue(Engine15WeeklyReview.preview(singlePlan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow).changes.isEmpty())

        // Three low-RPE runs but fatigue == 3 -> maintain, no progression
        val lowRuns = listOf(
            completedEasy("c1", monday, effort = 3),
            completedEasy("c2", monday.plusDays(2), effort = 3),
            completedEasy("c3", monday.plusDays(4), effort = 3)
        ) + upcoming
        val modFatiguePlan = basePlan.copy(sessions = lowRuns)
        assertTrue(Engine15WeeklyReview.preview(modFatiguePlan, asOf, Engine15WeeklyReview.Input(fatigue = 3), now = evalNow).changes.isEmpty())

        // Discomfort == Pain blocks progression and triggers rest
        val painPlan = basePlan.copy(sessions = listOf(
            completedEasy("c1", monday, effort = 3),
            completedEasy("c2", monday.plusDays(2), effort = 3),
            completedEasy("c3", monday.plusDays(4), effort = 3, discomfort = "Pain")
        ) + upcoming)
        val painReview = Engine15WeeklyReview.preview(painPlan, asOf, now = evalNow)
        assertTrue(painReview.changes.isNotEmpty())
        assertTrue(painReview.changes.all { it.direction == Engine15WeeklyReview.Direction.REST })
    }
}
