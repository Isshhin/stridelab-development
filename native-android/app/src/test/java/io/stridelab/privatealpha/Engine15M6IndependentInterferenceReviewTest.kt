package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** M6C is a synthetic conflict-review gate, NOT verified multidisciplinary coaching. */
class Engine15M6IndependentInterferenceReviewTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        recentKm: Double = 75.0,
        targetKm: Double = 80.0,
        longestKm: Double = 20.0,
        runDays: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        recoveryHold: Boolean = false
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = weeks,
            days = (1..7).toSet(), runDays = runDays,
            longDay = 7, qualityCount = 0,
            weeklyKm = recentKm, targetWeeklyKm = targetKm,
            longestKm = longestKm, returning = returning,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, runDays, targetKm, returning, recoveryHold
            )
        )
    }

    private fun other(
        date: LocalDate,
        load: Engine15M6CLoad,
        id: String = "external-1",
        discipline: Engine15M6CDiscipline = Engine15M6CDiscipline.STRENGTH
    ) = Engine15M6CExternalSession(id, date, discipline, load)

    private fun quality(
        week: Int = 1,
        slot: Int = 2,
        reviewed: Boolean = true
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week,
        slotNumber = slot,
        family = IndependentQualityFamily.THRESHOLD,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = 1200,
            approvedWorkCeilingSeconds = 1200,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "caller-only-synthetic-dose-review"
    )

    private fun input(
        available: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        others: List<Engine15M6CExternalSession>? = emptyList(),
        claimed: Boolean = true,
        qualityRequests: List<Engine15M5QualityPreviewRequest> = emptyList(),
        blockedDates: Set<LocalDate> = emptySet(),
        allowEasyUpperPair: Boolean = false
    ) = Engine15M6CReviewInput(
        calendar = Engine15M6CalendarInput(
            startMonday = monday,
            availableIsoDays = available,
            preferredLongIsoDay = 7,
            blockedDates = blockedDates
        ),
        externalSessions = others,
        externalCalendarCoverageAsserted = claimed,
        qualityRequests = qualityRequests,
        allowEasyUpperPair = allowEasyUpperPair
    )

    private fun accepted(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6CReviewInput
    ): Engine15M6CReviewDecision.Candidate {
        val result = Engine15M6IndependentInterferenceReview.review(source, input)
        assertTrue("Expected candidate; got $result",
            result is Engine15M6CReviewDecision.Candidate)
        return result as Engine15M6CReviewDecision.Candidate
    }

    private fun deferred(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6CReviewInput,
        code: String
    ) {
        val result = Engine15M6IndependentInterferenceReview.review(source, input)
        assertTrue("Expected $code, got $result",
            result is Engine15M6CReviewDecision.Deferred)
        assertEquals(code,
            (result as Engine15M6CReviewDecision.Deferred).reasonCode)
    }

    private fun preserved(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6CReviewInput,
        result: Engine15M6CReviewDecision.Candidate
    ) {
        val base = Engine15M6IndependentCalendar.schedule(source, input.calendar)
            as Engine15M6CalendarDecision.Candidate
        assertEquals(base.weeks, result.runningWeeks)
        assertEquals(source.weeks.map { it.proposedKm },
            result.runningWeeks.map { it.budgetKm })
        assertEquals(input.externalSessions, result.externalSessions)
        assertFalse(result.externalCalendarVerified)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.timeBudgetValidated)
        assertFalse(result.qualityScheduled)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        for (week in result.runningWeeks)
            for (run in week.runs) {
                assertFalse(run.executable)
                assertFalse(run.qualityAuthorized)
                assertEquals(run.distanceKm * 1000.0,
                    run.slot.workout.total(WorkoutUnit.METERS)!!, 1e-6)
            }
    }

    @Test fun nonrunningThursdayUpperStrengthDoesNotModifySixDayCalendar() {
        val s = source()
        val upper = other(LocalDate.of(2026, 11, 19), Engine15M6CLoad.UPPER_BODY)
        val config = input(others = listOf(upper))
        val result = accepted(s, config)
        assertTrue(result.qualityOpportunities.isEmpty())
        assertTrue(result.runningWeeks.all { it.runs.none { run -> run.isoDay == 4 } })
        assertTrue(result.runningWeeks.all {
            it.runs.single { run -> run.role == Engine15M5RunRole.LONG }.isoDay == 7
        })
        preserved(s, config, result)
    }

    @Test fun sameDayUpperPairRequiresExplicitOptInAndNeverCertifiesSafety() {
        val s = source()
        val upper = other(LocalDate.of(2026, 11, 17), Engine15M6CLoad.UPPER_BODY)
        deferred(s, input(others = listOf(upper)), "RUN_EXTERNAL_SAME_DAY_CONFLICT")
        val config = input(others = listOf(upper), allowEasyUpperPair = true)
        val result = accepted(s, config)
        assertFalse(result.timeBudgetValidated)
        preserved(s, config, result)
    }

    @Test fun sameDayQualityAndUpperStrengthCannotPairEvenWithOptIn() {
        val upper = other(LocalDate.of(2026, 11, 17), Engine15M6CLoad.UPPER_BODY)
        deferred(source(), input(others = listOf(upper),
            qualityRequests = listOf(quality()), allowEasyUpperPair = true),
            "RUN_EXTERNAL_SAME_DAY_CONFLICT")
    }

    @Test fun claimedMondayLowerBodyOneDayBeforeTuesdayQualityDefers() {
        // Monday is not a running day in this five-day variant.
        val mondayLower = other(monday, Engine15M6CLoad.LOWER_BODY)
        deferred(source(runDays = 5), input(
            available = setOf(2, 3, 4, 5, 7),
            others = listOf(mondayLower),
            qualityRequests = listOf(quality(slot = 1))
        ), "QUALITY_QUALITY_TOO_CLOSE_TO_OTHER_HARD")
    }

    @Test fun lowerBodyOnMondayFollowingSundayLongIsRejectedAcrossWeeks() {
        val lower = other(monday.plusWeeks(1), Engine15M6CLoad.LOWER_BODY)
        deferred(source(runDays = 5), input(
            available = setOf(2, 3, 4, 5, 7), others = listOf(lower)
        ), "EXTERNAL_HARD_TOO_CLOSE_TO_LONG")
    }

    @Test fun saturdayLowerBodyBeforeSundayLongIsRejectedWithoutSameDayCollision() {
        val saturday = other(LocalDate.of(2026, 11, 21), Engine15M6CLoad.LOWER_BODY)
        deferred(source(runDays = 3), input(
            available = setOf(2, 4, 7), others = listOf(saturday)
        ), "EXTERNAL_HARD_TOO_CLOSE_TO_LONG")
    }

    @Test fun adjacentExternalHardSessionsAreNotAcceptedAsReviewed() {
        // Two-day EASY-only running week has no LONG role.
        val s = source(recentKm = 20.0, targetKm = 25.0,
            runDays = 2, weeks = 1)
        val friday = other(LocalDate.of(2026, 11, 20),
            Engine15M6CLoad.LOWER_BODY, "lower-fri")
        val saturday = other(LocalDate.of(2026, 11, 21),
            Engine15M6CLoad.FULL_BODY_HARD, "hard-sat",
            Engine15M6CDiscipline.CONDITIONING)
        deferred(s, input(available = setOf(3, 7),
            others = listOf(friday, saturday)),
            "EXTERNAL_HARD_BACK_TO_BACK")
    }

    @Test fun twoExternalSessionsOnSameDayRequireSeparateReview() {
        val day = LocalDate.of(2026, 11, 19)
        deferred(source(), input(others = listOf(
            other(day, Engine15M6CLoad.UPPER_BODY, "str"),
            other(day, Engine15M6CLoad.EASY_GENERAL, "cond",
                Engine15M6CDiscipline.CONDITIONING)
        )), "MULTIPLE_EXTERNAL_SAME_DATE")
    }

    @Test fun missingUnassertedDuplicateAndOutOfRangeCalendarFailClosed() {
        val s = source()
        deferred(s, input(others = null), "EXTERNAL_CALENDAR_MISSING")
        deferred(s, input(claimed = false), "EXTERNAL_CALENDAR_NOT_ASSERTED")
        deferred(s, input(others = listOf(
            other(LocalDate.of(2026, 11, 19),
                Engine15M6CLoad.UPPER_BODY, "dupe"),
            other(LocalDate.of(2026, 11, 26),
                Engine15M6CLoad.UPPER_BODY, "dupe")
        )), "DUPLICATE_EXTERNAL_ID")
        deferred(s, input(others = listOf(
            other(monday.minusDays(2),
                Engine15M6CLoad.UPPER_BODY)
        )), "EXTERNAL_DATE_OUTSIDE_REVIEW_WINDOW")
        deferred(s, input(others = listOf(
            other(LocalDate.of(2026, 11, 19),
                Engine15M6CLoad.UPPER_BODY, " ")
        )), "INVALID_EXTERNAL_ID")
    }

    @Test fun upstreamAvailabilityTamperAndUnreviewedQualityRemainBlocked() {
        val s = source()
        deferred(s, input(blockedDates = setOf(LocalDate.of(2026, 11, 22))),
            "CALENDAR_LONG_DAY_BLOCKED")
        val tampered = s.copy(weeks = s.weeks.mapIndexed { index, week ->
            if (index == 0) week.copy(proposedKm = 100.0) else week
        })
        deferred(tampered, input(), "CALENDAR_BASE_M4B_RESULT_MISMATCH")
        deferred(s, input(qualityRequests = listOf(quality(reviewed = false))),
            "QUALITY_QUALITY_QUALITY_EXTERNAL_REVIEW_MISSING")
        deferred(source(recoveryHold = true), input(),
            "CALENDAR_BASE_M4B_NON_DISTANCE_STATUS")
    }

    @Test fun qualityAndNoncollidingUpperThursdayKeepExistingWorkoutsUntouched() {
        val s = source()
        val upper = other(LocalDate.of(2026, 11, 19), Engine15M6CLoad.UPPER_BODY)
        val config = input(others = listOf(upper),
            qualityRequests = listOf(quality()))
        val first = accepted(s, config)
        val second = accepted(s, config)
        assertEquals(first, second)
        assertEquals(LocalDate.of(2026, 11, 17),
            first.qualityOpportunities.single().proposedDate)
        assertFalse(first.qualityOpportunities.single().scheduledQualityWorkout)
        assertFalse(first.qualityOpportunities.single().executable)
        preserved(s, config, first)
    }
}
