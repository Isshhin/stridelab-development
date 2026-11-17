package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

/** M6B shadow-only quality OPPORTUNITY spacing; no executable prescriptions. */
class Engine15M6IndependentQualitySpacingTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        recent: Double = 75.0,
        target: Double = 80.0,
        longest: Double = 20.0,
        days: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        recoveryHold: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE"
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = weeks,
            days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = days, longDay = 7, qualityCount = 0,
            weeklyKm = recent, targetWeeklyKm = target,
            longestKm = longest, returning = returning,
            timeBudgetEnabled = false, policyChoice = "engine15",
            trainYourWay = true, recentMileageBasis = basis,
            runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, target, returning, recoveryHold
            )
        )
    }

    private fun calendar(
        available: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 7,
        blockedDays: Set<LocalDate> = emptySet(),
        blockedQuality: Set<LocalDate> = emptySet(),
        otherHard: Set<LocalDate>? = null,
        policy: Engine15M6QualitySpacingPolicy = Engine15M6QualitySpacingPolicy()
    ) = Engine15M6QualitySpacingInput(
        calendar = Engine15M6CalendarInput(
            startMonday = monday,
            availableIsoDays = available,
            preferredLongIsoDay = longDay,
            blockedDates = blockedDays
        ),
        blockedQualityDates = blockedQuality,
        assertedOtherHardDates = otherHard,
        policy = policy
    )

    private fun quality(
        week: Int = 1, slot: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week, slotNumber = slot,
        family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = 1200,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-review"
    )

    private fun candidate(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6QualitySpacingInput = calendar(),
        qualityRequests: List<Engine15M5QualityPreviewRequest> = listOf(quality())
    ): Engine15M6QualitySpacingDecision.Candidate {
        val decision = Engine15M6IndependentQualitySpacing.propose(
            source, input, qualityRequests
        )
        assertTrue("Expected M6B Candidate, got $decision",
            decision is Engine15M6QualitySpacingDecision.Candidate)
        return decision as Engine15M6QualitySpacingDecision.Candidate
    }

    private fun deferred(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6QualitySpacingInput = calendar(),
        qualityRequests: List<Engine15M5QualityPreviewRequest> = listOf(quality()),
        code: String
    ) {
        val decision = Engine15M6IndependentQualitySpacing.propose(
            source, input, qualityRequests
        )
        assertTrue("Expected $code deferral, got $decision",
            decision is Engine15M6QualitySpacingDecision.Deferred)
        assertEquals(code,
            (decision as Engine15M6QualitySpacingDecision.Deferred).reasonCode)
    }

    private fun assertUnchanged(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6QualitySpacingInput,
        requests: List<Engine15M5QualityPreviewRequest>,
        result: Engine15M6QualitySpacingDecision.Candidate
    ) {
        val expectedCalendar = Engine15M6IndependentCalendar.schedule(source, input.calendar)
            as Engine15M6CalendarDecision.Candidate
        val expectedQuality = Engine15M5QualityPreviewComposer.compose(source, requests)
            as Engine15M5QualityDecision.Candidate
        assertEquals(expectedCalendar.weeks, result.baseCalendar)
        assertEquals(expectedQuality.qualityPreviews, result.originalQualityPreviews)
        assertEquals(source.weeks.map { it.proposedKm },
            result.baseCalendar.map { it.budgetKm })
        assertTrue(result.opportunities.all { opp ->
            !opp.scheduledQualityWorkout && !opp.executable &&
                opp.existingRun.role == Engine15M5RunRole.EASY &&
                opp.existingRun.date == opp.proposedDate
        })
        assertFalse(result.priorTrainingHistoryReviewed)
        assertFalse(result.externalHardTrainingEvidenceComplete)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.qualityDoseApproved)
        assertFalse(result.distanceReconciled)
        assertFalse(result.qualityScheduled)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun tuesdayOpportunitiesAcrossWeeksPreserveThursdayOffAndSundayLong() {
        val s = source()
        val c = calendar()
        val requests = listOf(quality(week = 1), quality(week = 2,
            family = IndependentQualityFamily.TEMPO))
        val result = candidate(s, c, requests)
        assertEquals(listOf(LocalDate.of(2026, 11, 17),
            LocalDate.of(2026, 11, 24)),
            result.opportunities.map { it.proposedDate })
        assertEquals(2L, ChronoUnit.DAYS.between(
            result.baseCalendar[0].runs.single {
                it.role == Engine15M5RunRole.LONG
            }.date, result.opportunities[1].proposedDate))
        assertTrue(result.baseCalendar.all { week ->
            week.runs.none { it.isoDay == 4 } &&
                week.runs.single { it.role == Engine15M5RunRole.LONG }.isoDay == 7
        })
        assertUnchanged(s, c, requests, result)
    }

    @Test fun saturdayQualityConflictsWithCurrentSundayLong() {
        deferred(source(), qualityRequests = listOf(quality(slot = 5)),
            code = "QUALITY_TOO_CLOSE_TO_LONG")
    }

    @Test fun mondayQualityConflictsWithPreviousWeekSundayLong() {
        deferred(source(), qualityRequests = listOf(quality(week = 2, slot = 1)),
            code = "QUALITY_TOO_CLOSE_TO_LONG")
    }

    @Test fun crossWeekQualityOpportunitiesHonorThreeDaySpacing() {
        val c = calendar(available = setOf(1, 2, 3, 4, 5, 6),
            longDay = 3, policy = Engine15M6QualitySpacingPolicy(
                minCalendarDaysFromLong = 2,
                minCalendarDaysBetweenQuality = 3
            ))
        deferred(source(), input = c, qualityRequests = listOf(
            quality(week = 1, slot = 5),
            quality(week = 2, slot = 1)
        ), code = "QUALITY_TOO_CLOSE_TO_QUALITY")
    }

    @Test fun explicitlyBlockedQualityDateDoesNotBlockTheEasyRunItself() {
        val blocked = LocalDate.of(2026, 11, 17)
        deferred(source(), input = calendar(blockedQuality = setOf(blocked)),
            code = "QUALITY_DAY_BLOCKED")
        val baseline = Engine15M6IndependentCalendar.schedule(source(),
            calendar().calendar) as Engine15M6CalendarDecision.Candidate
        assertTrue(baseline.weeks.first().runs.any { it.date == blocked })
    }

    @Test fun knownOtherHardSessionDatesAreEnforcedAndUnknownEvidenceRemainsUnknown() {
        val mondayStrength = LocalDate.of(2026, 11, 16)
        deferred(source(), input = calendar(otherHard = setOf(mondayStrength)),
            code = "QUALITY_TOO_CLOSE_TO_OTHER_HARD")
        val c = calendar(otherHard = emptySet())
        val requests = listOf(quality())
        val result = candidate(source(), c, requests)
        assertUnchanged(source(), c, requests, result)
    }

    @Test fun threeDayCalendarSupportsOneReviewedEasyOpportunityWithoutExtraMileage() {
        val s = source(days = 3)
        val c = calendar(available = (1..7).toSet())
        val requests = listOf(quality(slot = 1))
        val result = candidate(s, c, requests)
        assertEquals(LocalDate.of(2026, 11, 17),
            result.opportunities.single().proposedDate)
        assertEquals(16.8,
            result.opportunities.single().existingRun.distanceKm, 1e-8)
        assertEquals(54.6, result.baseCalendar.first().budgetKm, 1e-8)
        assertUnchanged(s, c, requests, result)
    }

    @Test fun recoveryLongSlotUnreviewedDoseAndDuplicateWeekFailUpstream() {
        deferred(source(), qualityRequests = listOf(quality(week = 4)),
            code = "QUALITY_QUALITY_RECOVERY_WEEK_HOLD")
        deferred(source(), qualityRequests = listOf(quality(slot = 6)),
            code = "QUALITY_QUALITY_ON_LONG_SLOT")
        deferred(source(), qualityRequests = listOf(quality(reviewed = false)),
            code = "QUALITY_QUALITY_EXTERNAL_REVIEW_MISSING")
        deferred(source(), qualityRequests = listOf(
            quality(slot = 1), quality(slot = 2)),
            code = "QUALITY_MULTIPLE_QUALITY_REQUESTS_IN_WEEK")
    }

    @Test fun unavailableCalendarOrUnapprovedM4bCannotBypassQualitySpacing() {
        val s = source()
        deferred(s, input = calendar(blockedDays = setOf(
            LocalDate.of(2026, 11, 22)
        )), code = "CALENDAR_LONG_DAY_BLOCKED")
        val altered = s.copy(weeks = s.weeks.mapIndexed { index, week ->
            if (index == 0) week.copy(proposedKm = 100.0) else week
        })
        deferred(altered, code = "CALENDAR_BASE_M4B_RESULT_MISMATCH")
        deferred(source(returning = true, recent = 40.0,
            longest = 12.0, days = 4),
            qualityRequests = listOf(quality(slot = 1)),
            code = "QUALITY_RETURNING_RUNNER_QUALITY_HOLD")
        deferred(source(recoveryHold = true),
            code = "CALENDAR_BASE_M4B_NON_DISTANCE_STATUS")
    }

    @Test fun deterministicRepeatedProposalPreservesOriginalTypedWorkouts() {
        val s = source()
        val c = calendar(otherHard = setOf(LocalDate.of(2026, 11, 19)))
        val requests = listOf(quality(week = 1), quality(week = 2))
        val first = candidate(s, c, requests)
        val second = candidate(s, c, requests)
        assertEquals(first, second)
        assertUnchanged(s, c, requests, first)
    }
}
