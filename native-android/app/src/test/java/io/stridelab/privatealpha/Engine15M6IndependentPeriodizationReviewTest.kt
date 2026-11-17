package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** M6D reports provisional M4B multiweek patterns; no adaptive coaching or approval. */
class Engine15M6IndependentPeriodizationReviewTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun source(
        recent: Double = 75.0,
        target: Double = 80.0,
        longest: Double = 20.0,
        days: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        recoveryHold: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE",
        experience: Int = 2
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = experience,
            start = monday, weeks = weeks,
            days = (1..7).toSet(), runDays = days,
            longDay = 7, qualityCount = 0,
            weeklyKm = recent, targetWeeklyKm = target,
            longestKm = longest, returning = returning,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false, recentMileageBasis = basis,
            runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, target, returning, recoveryHold
            )
        )
    }

    private fun upperThursday() = Engine15M6CExternalSession(
        "thursday-upper", LocalDate.of(2026, 11, 19),
        Engine15M6CDiscipline.STRENGTH, Engine15M6CLoad.UPPER_BODY
    )

    private fun request(week: Int, slot: Int = 2) =
        Engine15M5QualityPreviewRequest(
            weekNumber = week,
            slotNumber = slot,
            family = IndependentQualityFamily.THRESHOLD,
            fitnessInput = IndependentQualityFitnessInput(
                confirmedRecentWorkSeconds = 1200,
                approvedWorkCeilingSeconds = 1200,
                availableSessionSeconds = 3600,
                qualityAuthorized = true
            ),
            explicitlyReviewedDose = true,
            reviewReference = "synthetic-external-quality-review"
        )

    private fun input(
        days: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        external: List<Engine15M6CExternalSession>? = emptyList(),
        claimed: Boolean = true,
        quality: List<Engine15M5QualityPreviewRequest> = emptyList(),
        blockedDates: Set<LocalDate> = emptySet()
    ) = Engine15M6CReviewInput(
        calendar = Engine15M6CalendarInput(
            startMonday = monday,
            availableIsoDays = days,
            preferredLongIsoDay = 7,
            blockedDates = blockedDates
        ),
        externalSessions = external,
        externalCalendarCoverageAsserted = claimed,
        qualityRequests = quality
    )

    private fun candidate(
        s: Engine15IndependentVolumePlanner.Result,
        i: Engine15M6CReviewInput
    ): Engine15M6DReviewDecision.Candidate {
        val got = Engine15M6IndependentPeriodizationReview.audit(s, i)
        assertTrue("Expected M6D Candidate, got $got",
            got is Engine15M6DReviewDecision.Candidate)
        return got as Engine15M6DReviewDecision.Candidate
    }

    private fun deferred(
        s: Engine15IndependentVolumePlanner.Result,
        i: Engine15M6CReviewInput,
        expected: String
    ) {
        val got = Engine15M6IndependentPeriodizationReview.audit(s, i)
        assertTrue("Expected $expected, got $got",
            got is Engine15M6DReviewDecision.Deferred)
        assertEquals(expected,
            (got as Engine15M6DReviewDecision.Deferred).reasonCode)
    }

    private fun assertProvisional(
        s: Engine15IndependentVolumePlanner.Result,
        i: Engine15M6CReviewInput,
        result: Engine15M6DReviewDecision.Candidate
    ) {
        val m6c = Engine15M6IndependentInterferenceReview.review(s, i)
            as Engine15M6CReviewDecision.Candidate
        assertEquals(m6c, result.originalConflictReview)
        assertEquals(s.weeks.map { it.proposedKm },
            result.weeks.map { it.proposedKm })
        assertTrue(result.allFutureWeeksProvisional)
        assertFalse(result.verifiedAthleteResponse)
        assertFalse(result.qualityIntensityAuthenticated)
        assertFalse(result.actualDistanceVerified)
        assertFalse(result.timeBudgetValidated)
        assertFalse(result.productionReady)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        for (week in result.weeks) {
            val runs = m6c.runningWeeks[week.weekNumber - 1].runs
            assertEquals(runs.size, week.runningSessionCount)
            assertEquals(week.proposedKm, runs.sumOf { it.distanceKm }, 1e-8)
            assertEquals(runs.singleOrNull {
                it.role == Engine15M5RunRole.LONG
            }?.distanceKm, week.longRunKm)
            assertEquals(m6c.qualityOpportunities.count {
                it.weekNumber == week.weekNumber
            }, week.qualityOpportunityCount)
            assertEquals(week.weekNumber == s.reviewAfterCompletedWeek,
                week.requiresCompletedWeekReassessment)
        }
    }

    @Test fun eightWeekPatternIsReportedWithoutRaisingAnyMileage() {
        val s = source()
        val i = input()
        val result = candidate(s, i)
        assertEquals(listOf(67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9),
            result.weeks.map { it.proposedKm })
        assertEquals(listOf(
            Engine15M6DPhase.BUILD_1,
            Engine15M6DPhase.BUILD_2,
            Engine15M6DPhase.BUILD_3,
            Engine15M6DPhase.RECOVERY,
            Engine15M6DPhase.BUILD_1,
            Engine15M6DPhase.BUILD_2,
            Engine15M6DPhase.BUILD_3,
            Engine15M6DPhase.RECOVERY
        ), result.weeks.map { it.phase })
        assertEquals(561.5, result.proposedTotalKm, 1e-8)
        assertEquals(78.7, result.proposedPeakKm, 1e-8)
        assertEquals(80.0, result.requestedTargetKm!!, 1e-8)
        assertEquals(1.3, result.targetBudgetShortfallKm!!, 1e-8)
        assertEquals(2, result.reviewAfterCompletedWeek)
        assertEquals(listOf(2), result.weeks.filter {
            it.requiresCompletedWeekReassessment
        }.map { it.weekNumber })
        assertProvisional(s, i, result)
    }

    @Test fun recoveryWeeksUsePreviousBudgetNotReboundCatchup() {
        val result = candidate(source(), input())
        for (index in listOf(3, 7)) {
            val previous = result.weeks[index - 1]
            val recovery = result.weeks[index]
            assertEquals(Engine15M6DPhase.RECOVERY, recovery.phase)
            assertTrue(recovery.proposedKm <= previous.proposedKm * 0.8 + 1e-8)
            assertTrue(recovery.longRunKm!! <= previous.longRunKm!! + 1e-8)
            assertEquals(0, recovery.qualityOpportunityCount)
        }
        // Following a recovery week is the next independently bounded M4B
        // loading budget; M6D does not add skipped kilometres.
        assertEquals(71.2, result.weeks[4].proposedKm, 1e-8)
    }

    @Test fun reviewedQualityAndClaimedThursdayStrengthRemainPreviewOnly() {
        val s = source()
        val i = input(external = listOf(upperThursday()),
            quality = listOf(request(1), request(2)))
        val result = candidate(s, i)
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 0, 0),
            result.weeks.map { it.qualityOpportunityCount })
        assertEquals(1, result.weeks[0].externalSessionCount)
        assertEquals(0, result.weeks[1].externalSessionCount)
        assertEquals(listOf(LocalDate.of(2026, 11, 17),
            LocalDate.of(2026, 11, 24)),
            result.originalConflictReview.qualityOpportunities.map { it.proposedDate })
        assertTrue(result.originalConflictReview.qualityOpportunities.all {
            !it.scheduledQualityWorkout && !it.executable
        })
        assertProvisional(s, i, result)
    }

    @Test fun fourWeekTimelineRetainsSingleRecoveryAndReassessment() {
        val s = source(weeks = 4)
        val i = input()
        val result = candidate(s, i)
        assertEquals(4, result.weeks.size)
        assertEquals(Engine15M6DPhase.RECOVERY, result.weeks.last().phase)
        assertEquals(60.0, result.weeks.last().proposedKm, 1e-8)
        assertEquals(2, result.reviewAfterCompletedWeek)
        assertProvisional(s, i, result)
    }

    @Test fun returningRunnerStillRequiresCompletionReviewAndNoQuality() {
        val s = source(recent = 40.0, target = 50.0,
            longest = 12.0, days = 4, returning = true)
        val i = input()
        val result = candidate(s, i)
        assertEquals(24.0, result.weeks.first().proposedKm, 1e-8)
        assertEquals(2, result.reviewAfterCompletedWeek)
        assertTrue(result.weeks.all { it.qualityOpportunityCount == 0 })
        assertTrue(result.weeks.all { it.runningSessionCount == 4 })
        assertProvisional(s, i, result)
    }

    @Test fun feasibleTwoDayFoundationPreservesEasyOnlyWeek() {
        val s = source(recent = 20.0, target = 25.0,
            longest = 20.0, days = 2, weeks = 1)
        val i = input(days = setOf(3, 7))
        val result = candidate(s, i)
        assertEquals(18.0, result.proposedPeakKm, 1e-8)
        assertEquals(7.0, result.targetBudgetShortfallKm!!, 1e-8)
        assertNull(result.weeks.single().longRunKm)
        assertEquals(2, result.weeks.single().runningSessionCount)
        assertProvisional(s, i, result)
    }

    @Test fun missingExternalCalendarOrBlockedLongDefersUpstream() {
        val s = source()
        deferred(s, input(external = null), "UPSTREAM_EXTERNAL_CALENDAR_MISSING")
        deferred(s, input(claimed = false), "UPSTREAM_EXTERNAL_CALENDAR_NOT_ASSERTED")
        deferred(s, input(blockedDates = setOf(LocalDate.of(2026, 11, 22))),
            "UPSTREAM_CALENDAR_LONG_DAY_BLOCKED")
    }

    @Test fun tamperedM4BAndIncompleteBaselineStayFailClosed() {
        val s = source()
        val changed = s.copy(weeks = s.weeks.mapIndexed { index, week ->
            if (index == 0) week.copy(proposedKm = 100.0) else week
        })
        deferred(changed, input(), "UPSTREAM_CALENDAR_BASE_M4B_RESULT_MISMATCH")
        deferred(source(recent = 0.0, longest = 0.0,
            basis = "CONFIRMED_ZERO", days = 3, experience = 0),
            input(), "UPSTREAM_CALENDAR_BASE_M4B_NON_DISTANCE_STATUS")
        deferred(source(recoveryHold = true), input(),
            "UPSTREAM_CALENDAR_BASE_M4B_NON_DISTANCE_STATUS")
    }

    @Test fun periodizationAuditIsDeterministicWithoutCreatingAPlan() {
        val s = source()
        val i = input(external = listOf(upperThursday()),
            quality = listOf(request(1)))
        val a = candidate(s, i)
        val b = candidate(s, i)
        assertEquals(a, b)
        assertEquals(s.weeks.size, a.weeks.size)
        assertEquals(s.peakProposedKm!!, a.proposedPeakKm, 1e-8)
        assertProvisional(s, i, a)
    }
}
