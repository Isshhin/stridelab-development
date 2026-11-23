package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Engine15Batch3AuditFixesTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val createdAt = Instant.parse("2026-11-16T08:00:00Z")

    private fun completedEasy(
        id: String,
        date: LocalDate,
        effort: Int? = 3,
        minutes: Int = 45,
        km: Double = 7.0,
        linkedActivityId: String? = null
    ) = PlannedSession(
        id = id,
        date = date,
        discipline = "run",
        title = "Easy run",
        minutes = minutes,
        distanceKm = km,
        kind = "easy",
        status = "completed",
        logRevision = 1,
        linkedActivityId = linkedActivityId,
        log = WorkoutLog(
            date.atTime(9, 0).toInstant(ZoneOffset.UTC),
            minutes = minutes.toDouble(),
            distanceKm = km,
            effort = effort,
            performedOn = date
        )
    )

    private fun sixEfficiencyRuns(asOf: LocalDate): List<ActivityRecord> = listOf(
        ActivityRecord("ef-e1", "Easy", "run", "garmin", asOf.minusWeeks(3).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 3040.0, 3040.0, 380.0, 152.0, 20.0),
        ActivityRecord("ef-e2", "Easy", "run", "garmin", asOf.minusWeeks(2).minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 3040.0, 3040.0, 380.0, 151.0, 20.0),
        ActivityRecord("ef-e3", "Easy", "run", "garmin", asOf.minusWeeks(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 3040.0, 3040.0, 380.0, 150.0, 20.0),
        ActivityRecord("ef-l1", "Easy", "run", "garmin", asOf.minusWeeks(1).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2800.0, 2800.0, 350.0, 141.0, 20.0),
        ActivityRecord("ef-l2", "Easy", "run", "garmin", asOf.minusDays(2).atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2760.0, 2760.0, 345.0, 140.0, 20.0),
        ActivityRecord("ef-l3", "Easy", "run", "garmin", asOf.atTime(12, 0).toInstant(ZoneOffset.UTC), 8000.0, 2720.0, 2720.0, 340.0, 139.0, 20.0)
    )

    @Test
    fun weeklyReviewNeverProposesProgressionDuringRecoveryOrTaperWeek() {
        val request = PlanRequest(
            goal = "Marathon",
            start = monday,
            weeks = 12,
            days = setOf(1, 3, 5, 7),
            runDays = 4,
            longDay = 7,
            longestKm = 18.0,
            policyChoice = "engine15",
            timeBudgetEnabled = false
        )

        // Week 2 loading week -> allowed to progress
        val w1AsOf = monday.plusDays(6)
        val w1Completed = listOf(
            completedEasy("w1-c1", monday, effort = 3),
            completedEasy("w1-c2", monday.plusDays(2), effort = 3),
            completedEasy("w1-c3", monday.plusDays(4), effort = 4)
        )
        val w2Upcoming = listOf(
            PlannedSession("w2-e1", w1AsOf.plusDays(1), "run", "Easy run", 45, 7.0, "easy"),
            PlannedSession("w2-l1", w1AsOf.plusDays(7), "run", "Long run", 80, 14.0, "long")
        )
        val w2Plan = PlanSnapshot("p-w2", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, createdAt, request, w1Completed + w2Upcoming, emptyList(), status = "approved")
        val w2Review = Engine15WeeklyReview.preview(w2Plan, w1AsOf, Engine15WeeklyReview.Input(fatigue = 1), now = w1AsOf.atTime(18, 0).toInstant(ZoneOffset.UTC))
        assertTrue(w2Review.changes.isNotEmpty())
        assertTrue(w2Review.changes.all { it.direction == Engine15WeeklyReview.Direction.PROGRESS })

        // Week 4 scheduled recovery week (weekIndex = 3) -> blocked
        val w3AsOf = monday.plusWeeks(2).plusDays(6)
        val w3Completed = listOf(
            completedEasy("w3-c1", w3AsOf.minusDays(5), effort = 3),
            completedEasy("w3-c2", w3AsOf.minusDays(3), effort = 3),
            completedEasy("w3-c3", w3AsOf.minusDays(1), effort = 3)
        )
        val w4Upcoming = listOf(
            PlannedSession("w4-e1", w3AsOf.plusDays(1), "run", "Easy run", 40, 6.0, "easy"),
            PlannedSession("w4-l1", w3AsOf.plusDays(7), "run", "Long run", 75, 13.0, "long")
        )
        val w4Plan = w2Plan.copy(id = "p-w4", sessions = w3Completed + w4Upcoming)
        val w4Review = Engine15WeeklyReview.preview(w4Plan, w3AsOf, Engine15WeeklyReview.Input(fatigue = 1), now = w3AsOf.atTime(18, 0).toInstant(ZoneOffset.UTC))
        assertTrue("Scheduled recovery week must not propose progression, got ${w4Review.changes}", w4Review.changes.isEmpty())
        assertTrue(w4Review.explanations.any { it.contains("recovery or race taper", ignoreCase = true) })

        // Week 10 Marathon taper week (weekIndex = 9 in 12w plan) -> blocked
        val w9AsOf = monday.plusWeeks(8).plusDays(6)
        val w9Completed = listOf(
            completedEasy("w9-c1", w9AsOf.minusDays(5), effort = 3),
            completedEasy("w9-c2", w9AsOf.minusDays(3), effort = 3),
            completedEasy("w9-c3", w9AsOf.minusDays(1), effort = 3)
        )
        val w10Upcoming = listOf(
            PlannedSession("w10-e1", w9AsOf.plusDays(1), "run", "Easy run", 45, 7.0, "easy"),
            PlannedSession("w10-l1", w9AsOf.plusDays(7), "run", "Long run", 90, 16.0, "long")
        )
        val w10Plan = w2Plan.copy(id = "p-w10", sessions = w9Completed + w10Upcoming)
        val w10Review = Engine15WeeklyReview.preview(w10Plan, w9AsOf, Engine15WeeklyReview.Input(fatigue = 1), now = w9AsOf.atTime(18, 0).toInstant(ZoneOffset.UTC))
        assertTrue("Marathon taper week must not propose progression, got ${w10Review.changes}", w10Review.changes.isEmpty())
        assertTrue(w10Review.explanations.any { it.contains("recovery or race taper", ignoreCase = true) })
    }

    @Test
    fun weeklyReviewDoesNotProgressFromAerobicEfficiencyWithoutRpeCorroboration() {
        val asOf = monday.plusDays(6)
        val evalNow = asOf.atTime(18, 0).toInstant(ZoneOffset.UTC)
        val activities = sixEfficiencyRuns(asOf)
        val upcoming = listOf(PlannedSession("u-long", asOf.plusDays(5), "run", "Long run", 80, 14.0, "long"))
        val baseRequest = PlanRequest(goal = "Aerobic foundation", start = monday, weeks = 8, policyChoice = "engine15", timeBudgetEnabled = false)

        val unratedCompleted = listOf(
            completedEasy("c1", monday, effort = null, linkedActivityId = "ef-l2"),
            completedEasy("c2", monday.plusDays(3), effort = null, linkedActivityId = "ef-l3")
        )
        val unratedPlan = PlanSnapshot("p-no-rpe", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, createdAt, baseRequest, unratedCompleted + upcoming, emptyList(), status = "approved")
        val noRpeReview = Engine15WeeklyReview.preview(unratedPlan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow, activities = activities)
        assertNotNull(noRpeReview.aerobicEfficiencyPercentChange)
        assertTrue("Missing RPE must block efficiency progression even with Garmin link", noRpeReview.changes.isEmpty())

        val corroboratedCompleted = unratedCompleted.map { it.copy(log = it.log!!.copy(effort = 4)) }
        val corroboratedPlan = unratedPlan.copy(id = "p-rpe", sessions = corroboratedCompleted + upcoming)
        val okReview = Engine15WeeklyReview.preview(corroboratedPlan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow, activities = activities)
        assertEquals(1, okReview.changes.size)
        assertEquals(Engine15WeeklyReview.Direction.PROGRESS, okReview.changes.single().direction)
    }

    @Test
    fun aerobicEfficiencyRequiresMinimumThreeRunsPerBlockBeforeReportingTrend() {
        val now = Instant.parse("2026-11-21T06:00:00Z")
        fun act(id: String, daysAgo: Long, pace: Double, hr: Double) = ActivityRecord(
            id = id, title = "Easy", type = "running", source = "garmin",
            startedAt = now.minusSeconds(daysAgo * 86400),
            distanceM = 8000.0, durationS = 8.0 * pace, movingTimeS = 8.0 * pace,
            recordedPace = pace, averageHr = hr, elevationM = 10.0
        )

        // 1 early + 3 late (4 total across 4 weeks) -> must defer
        val oneEarlyThreeLate = listOf(
            act("e1", 49, 410.0, 150.0),
            act("l1", 14, 385.0, 140.0),
            act("l2", 10, 385.0, 140.0),
            act("l3", 7, 385.0, 140.0)
        )
        val summary4 = RunningAerobicEfficiency.analyze(oneEarlyThreeLate, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertNull(summary4.percentChange)
        assertTrue(summary4.caveat.contains("Building baseline"))

        // 2 early + 3 late (5 total across 4 weeks) -> still defer
        val twoEarlyThreeLate = oneEarlyThreeLate + act("e2", 42, 410.0, 150.0)
        val summary5 = RunningAerobicEfficiency.analyze(twoEarlyThreeLate, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertNull(summary5.percentChange)
        assertTrue(summary5.caveat.contains("Building baseline"))

        // 3 early + 3 late (6 total across 4 weeks) -> reports trend
        val threeEarlyThreeLate = twoEarlyThreeLate + act("e3", 47, 410.0, 150.0)
        val summary6 = RunningAerobicEfficiency.analyze(threeEarlyThreeLate, windowWeeks = 8, asOf = now, zone = ZoneOffset.UTC)
        assertNotNull(summary6.percentChange)
        assertTrue(summary6.percentChange!! > 5.0)
    }

    @Test
    fun weeklyReviewProgressionRespectsLongRunSpikeCap() {
        val asOf = monday.plusDays(6)
        val evalNow = asOf.atTime(18, 0).toInstant(ZoneOffset.UTC)
        val request = PlanRequest(
            goal = "Aerobic foundation",
            start = monday,
            weeks = 8,
            days = setOf(1, 3, 5, 7),
            runDays = 4,
            longDay = 7,
            longestKm = 10.0,
            policyChoice = "engine15",
            timeBudgetEnabled = false
        )
        val completed = listOf(
            completedEasy("c1", monday, effort = 3, km = 6.0),
            completedEasy("c2", monday.plusDays(2), effort = 3, km = 6.0),
            completedEasy("c3", monday.plusDays(4), effort = 3, km = 6.0)
        )

        // Planned 10.8 km @ 75 min (+4 min -> 79 min would be 11.3 km) must clamp to <= 11.0 km (<= 76 min)
        val clampedUpcoming = listOf(
            PlannedSession("u-long", asOf.plusDays(7), "run", "Long run", 75, 10.8, "long"),
            PlannedSession("u-easy", asOf.plusDays(3), "run", "Easy run", 45, 6.5, "easy")
        )
        val clampedPlan = PlanSnapshot("p-clamp", "owner", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, createdAt, request, completed + clampedUpcoming, emptyList(), status = "approved")
        val clampedReview = Engine15WeeklyReview.preview(clampedPlan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow)
        val longChange = clampedReview.changes.single { it.sessionId == "u-long" }
        assertTrue("Long run progression distance ${longChange.suggestedDistanceKm} must not exceed 11.0 km", longChange.suggestedDistanceKm!! <= 11.0 + 1e-9)
        assertEquals(76, longChange.minutes)

        // Already at 11.0 km (1.10 * 10.0) -> +1+ min would exceed 11.0 km, so long progression is blocked while easy run still progresses
        val atCapUpcoming = listOf(
            PlannedSession("u-long", asOf.plusDays(7), "run", "Long run", 75, 11.0, "long"),
            PlannedSession("u-easy", asOf.plusDays(3), "run", "Easy run", 45, 6.5, "easy")
        )
        val atCapPlan = clampedPlan.copy(id = "p-cap", sessions = completed + atCapUpcoming)
        val atCapReview = Engine15WeeklyReview.preview(atCapPlan, asOf, Engine15WeeklyReview.Input(fatigue = 1), now = evalNow)
        assertEquals(listOf("u-easy"), atCapReview.changes.map { it.sessionId })
        assertEquals(Engine15WeeklyReview.Direction.PROGRESS, atCapReview.changes.single().direction)
    }
}
