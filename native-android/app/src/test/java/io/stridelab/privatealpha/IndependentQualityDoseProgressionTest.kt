package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentQualityDoseProgressionTest {
    private val planMonday = LocalDate.of(2026, 11, 16)
    private val owner = "synthetic-athlete"
    private val request = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 5, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        request.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private val base by lazy {
        val result = IndependentConstrainedMultiweekPlanner.generate(request, limits)
        assertTrue(result.toString(), result is IndependentConstrainedMultiweekDecision.Candidate)
        result as IndependentConstrainedMultiweekDecision.Candidate
    }
    private fun spacing(count: Int = 1): IndependentQualitySpacingDecision.Candidate {
        val result = IndependentQualitySpacingPlanner.propose(
            base, IndependentQualitySpacingRequest(count)
        )
        assertTrue(result.toString(), result is IndependentQualitySpacingDecision.Candidate)
        return result as IndependentQualitySpacingDecision.Candidate
    }
    private fun observed(
        daysBefore: Long,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        workSeconds: Int? = 720,
        response: IndependentWeekResponse = IndependentWeekResponse.TOLERATED,
        source: String = "source-" + daysBefore + "-" + family.name,
        session: String = "session-" + daysBefore + "-" + family.name,
        ownedBy: String = owner
    ) = IndependentQualityWorkObservation(
        assertedOwnerId = ownedBy,
        sessionId = session,
        sourceRecordId = source,
        performedOn = planMonday.minusDays(daysBefore),
        family = family,
        recordedWorkSeconds = workSeconds,
        reportedResponse = response
    )
    private val standardHistory: List<IndependentQualityWorkObservation>
        get() = listOf(observed(11), observed(2))
    private fun slot(
        day: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        ceiling: Int? = 1200,
        increase: Int = 0,
        stepLimit: Int? = null,
        reference: String? = null,
        secondsAvailable: Int? = 3600,
        allowed: Boolean = true
    ) = IndependentQualityDoseSlotRequest(
        isoDay = day, family = family,
        availableSessionSeconds = secondsAvailable,
        approvedSessionWorkCeilingSeconds = ceiling,
        explicitlyApprovedIncreaseSeconds = increase,
        maxReviewedSingleStepIncreaseSeconds = stepLimit,
        increaseReviewReference = reference,
        qualityAllowed = allowed
    )
    private fun input(
        slots: List<IndependentQualityDoseSlotRequest> = listOf(slot()),
        history: List<IndependentQualityWorkObservation> = standardHistory,
        week: Int = 1,
        weeklyCeiling: Int? = 2400,
        weeklyReference: String? = "synthetic-weekly-review",
        hold: Boolean = false
    ) = IndependentQualityDoseReviewInput(
        assertedOwnerId = owner, planningMonday = planMonday,
        requestedWeek = week, requestedSlots = slots,
        observedHistory = history,
        approvedWeeklyQualityWorkCeilingSeconds = weeklyCeiling,
        weeklyReviewReference = weeklyReference,
        explicitRecoveryOrReturnHold = hold,
        completeQualityWeekMondays = setOf(planMonday.minusWeeks(1),planMonday.minusWeeks(2))
    )
    private fun inspect(
        review: IndependentQualityDoseReviewInput = input(),
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing()
    ) = IndependentQualityDoseProgression.review(base, arrangement, review)
    private fun preview(
        review: IndependentQualityDoseReviewInput = input(),
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing()
    ): IndependentQualityDoseReviewDecision.Preview {
        val result = inspect(review, arrangement)
        assertTrue(result.toString(), result is IndependentQualityDoseReviewDecision.Preview)
        return result as IndependentQualityDoseReviewDecision.Preview
    }
    private fun reject(
        rule: String,
        review: IndependentQualityDoseReviewInput = input(),
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing()
    ) {
        val result = inspect(review, arrangement)
        assertTrue(result.toString(), result is IndependentQualityDoseReviewDecision.Deferred)
        assertEquals(rule, (result as IndependentQualityDoseReviewDecision.Deferred).ruleId)
    }

    @Test fun twoToleratedThresholdSessionsAllowRepeatButNoUnapprovedProgression() {
        val result = preview()
        val opportunity = result.opportunities.single()
        assertEquals(2, opportunity.isoDay)
        assertEquals(IndependentQualityFamily.THRESHOLD, opportunity.family)
        assertEquals(720, opportunity.assertedRepeatedWorkSeconds)
        assertEquals(720, opportunity.selected.workSeconds)
        assertEquals(0, opportunity.approvedIncreaseSeconds)
        assertEquals(720, result.totalProposedWeeklyWorkSeconds)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.authorizedForProduction)
        assertFalse(opportunity.selected.executable)
    }

    @Test fun noUserTimeCapDoesNotTurnMissingAvailabilityIntoAnArbitraryOneHourLimit() {
        val longHistory = listOf(
            observed(11, workSeconds = 48 * 60),
            observed(2, workSeconds = 48 * 60)
        )
        val result = preview(input(
            slots = listOf(slot(ceiling = 60 * 60, secondsAvailable = null)),
            history = longHistory,
            weeklyCeiling = 60 * 60
        ))
        val workout = result.opportunities.single().selected
        assertEquals(48 * 60, workout.workSeconds)
        assertTrue(workout.totalSeconds > 60 * 60)
        assertTrue("USER_SESSION_TIME_LIMIT_NOT_PROVIDED" in workout.ruleIds)
    }
    @Test fun explicitReviewedIncreaseAllowsSmallProgressionWithoutExceedingCeiling() {
        val result = preview(input(slots = listOf(slot(
            increase = 120, stepLimit = 180, reference = "synthetic-increase-reviewed"
        ))))
        val selected = result.opportunities.single()
        assertEquals(840, selected.selected.workSeconds)
        assertEquals(840, result.totalProposedWeeklyWorkSeconds)
        assertTrue(selected.selected.totalSeconds <= 3600)
    }
    @Test fun lowerOfTwoRecentExposureClaimsAnchorsDose() {
        val result = preview(input(history = listOf(
            observed(11, workSeconds = 1200), observed(2, workSeconds = 720)
        )))
        assertEquals(720, result.opportunities.single().assertedRepeatedWorkSeconds)
        assertEquals(720, result.opportunities.single().selected.workSeconds)
    }
    @Test fun recentExposureIsSelectedByDateNotInputOrderingOrOldPeak() {
        val result = preview(input(history = listOf(
            observed(10, workSeconds = 720),
            observed(20, workSeconds = 1320),
            observed(1, workSeconds = 720),
            observed(2, workSeconds = 720)
        )))
        assertEquals(720, result.opportunities.single().assertedRepeatedWorkSeconds)
    }
    @Test fun twoRequestedFamiliesRequireIndependentHistoryAndWeeklyBudget() {
        val exposure = standardHistory + listOf(
            observed(12, family = IndependentQualityFamily.TEMPO),
            observed(1, family = IndependentQualityFamily.TEMPO)
        )
        val result = preview(
            input(
                slots = listOf(slot(day = 1), slot(day = 3,
                    family = IndependentQualityFamily.TEMPO)),
                history = exposure,
                weeklyCeiling = 1440
            ),
            spacing(2)
        )
        assertEquals(listOf(1, 3), result.opportunities.map { it.isoDay })
        assertEquals(1440, result.totalProposedWeeklyWorkSeconds)
        assertEquals(720, result.opportunities[0].selected.workSeconds)
        assertEquals(720, result.opportunities[1].selected.workSeconds)
        assertTrue(result.opportunities.all { !it.selected.distanceReconciled })
    }
    @Test fun combinedTwoQualityWorkDosesCannotExceedExplicitWeeklyCeiling() {
        val exposure = standardHistory + listOf(
            observed(12, family = IndependentQualityFamily.TEMPO),
            observed(1, family = IndependentQualityFamily.TEMPO)
        )
        reject(
            "WEEKLY_QUALITY_WORK_CEILING_EXCEEDED",
            input(
                slots = listOf(
                    slot(day = 1, increase = 120, stepLimit = 120, reference = "review-1"),
                    slot(day = 3, family = IndependentQualityFamily.TEMPO,
                        increase = 120, stepLimit = 120, reference = "review-2")
                ),
                history = exposure, weeklyCeiling = 1440
            ), spacing(2)
        )
    }
    @Test fun noRecentComparableSessionsOrMissingWorkTimeNeverInventsExposure() {
        reject("TWO_RECENT_QUALITY_EXPOSURES_REQUIRED",
            input(history = listOf(observed(10))))
        reject("TWO_RECENT_QUALITY_EXPOSURES_REQUIRED",
            input(history = listOf(observed(29), observed(2))))
        reject("RECENT_QUALITY_WORK_DURATION_UNKNOWN",
            input(history = listOf(observed(11), observed(2, workSeconds = null))))
    }
    @Test fun unreviewedOrAboveStepLimitIncreaseIsRejected() {
        reject("DOSE_INCREASE_REVIEW_MISSING",
            input(slots = listOf(slot(increase = 120, stepLimit = 180))))
        reject("MAX_SINGLE_STEP_INCREASE_UNKNOWN",
            input(slots = listOf(slot(increase = 120, reference = "review"))))
        reject("APPROVED_INCREASE_EXCEEDS_STEP_LIMIT",
            input(slots = listOf(slot(increase = 180, stepLimit = 120,
                reference = "review"))))
    }
    @Test fun unknownOrDifficultReportedResponseBlocksHardTraining() {
        reject("QUALITY_TOLERANCE_UNKNOWN",
            input(history = listOf(observed(11),
                observed(2, response = IndependentWeekResponse.UNKNOWN))))
        reject("QUALITY_RESPONSE_DIFFICULT_REVIEW",
            input(history = listOf(observed(11),
                observed(2, response = IndependentWeekResponse.DIFFICULT))))
    }
    @Test fun recoveryOrReturnHoldOverridesAllDoseClaims() {
        reject("RECOVERY_OR_RETURN_HOLD", input(hold = true))
    }
    @Test fun futureWeekCannotCopyTheSameTwoOldSessionsAsProof() {
        reject("FUTURE_WEEK_DOSE_NOT_SUPPORTED", input(week = 2))
        reject("FUTURE_WEEK_DOSE_NOT_SUPPORTED", input(week = 4))
    }
    @Test fun crossOwnerSourceDuplicatesAndFutureClaimsFailClosed() {
        reject("HISTORY_OWNER_SCOPE_CONFLICT",
            input(history = listOf(observed(11), observed(2, ownedBy = "other"))))
        reject("DUPLICATE_QUALITY_SOURCE_OR_SESSION",
            input(history = listOf(observed(11), observed(11), observed(2))))
        reject("FUTURE_OR_CURRENT_HISTORY_RECORD",
            input(history = listOf(observed(11), observed(0))))
    }
    @Test fun sameDayIsNotTwoIndependentExposures() {
        reject("SAME_DAY_QUALITY_CLAIMS_NEED_REVIEW",
            input(history = listOf(
                observed(2, session = "a", source = "source-a"),
                observed(2, session = "b", source = "source-b")
            )))
    }
    @Test fun missingWeeklyAndSessionApprovalCeilingsPreventWorkouts() {
        reject("WEEKLY_QUALITY_CEILING_UNKNOWN", input(weeklyCeiling = null))
        reject("WEEKLY_QUALITY_REVIEW_REQUIRED", input(weeklyReference = null))
        reject("SESSION_QUALITY_CEILING_UNKNOWN",
            input(slots = listOf(slot(ceiling = null))))
        reject("OBSERVED_DOSE_EXCEEDS_APPROVED_CEILING",
            input(slots = listOf(slot(ceiling = 600))))
    }
    @Test fun selectedQualityMustMatchM6bDayAndExistingEasyBudget() {
        reject("M6B_QUALITY_SLOT_NOT_SELECTED", input(slots = listOf(slot(day = 3))))
        reject("LONG_RUN_QUALITY_CONFLICT", input(slots = listOf(slot(day = 6))))
        val result = preview()
        val expected = base.baseWorkouts.weeks.first().sessions
            .single { it.isoDay == 2 }.allocatedKilometers
        assertEquals(expected, result.opportunities.single().selected.baseAllocatedKilometers, 0.00001)
        assertEquals(60.0, base.mileage.weeks.first().weeklyKilometers, 0.00001)
    }
    @Test fun duplicateDayAndUnauthorizedQualityAreExplicitHolds() {
        reject("DUPLICATE_REQUESTED_QUALITY_DAY",
            input(slots = listOf(slot(day = 2), slot(day = 2))))
        reject("QUALITY_NOT_ALLOWED", input(slots = listOf(slot(allowed = false))))
        reject("NO_QUALITY_REQUESTED", input(slots = emptyList()))
    }
    @Test fun smallAvailableDurationAndInsufficientDoseRejectWithoutTimeInference() {
        reject("M2_DOSE_OR_TIME_CONFLICT",
            input(slots = listOf(slot(secondsAvailable = 1200))))
        val preview = preview()
        assertNull(preview.opportunities.single().selected.qualityWorkout.total(WorkoutUnit.METERS))
    }
    @Test fun deterministicReadOnlyPreviewDoesNotAuthorizeMeasuredThreshold() {
        val request = input()
        val schedule = spacing()
        val a = inspect(request, schedule)
        val b = inspect(request, schedule)
        assertEquals(a, b)
        val preview = a as IndependentQualityDoseReviewDecision.Preview
        assertFalse(preview.sourceEvidenceVerified)
        assertFalse(preview.intensityVerified)
        assertFalse(preview.distanceReconciled)
        assertFalse(preview.authorizedForProduction)
        assertEquals(60.0, base.mileage.weeks.first().weeklyKilometers, 0.00001)
    }
    @Test fun staleCompleteWeeksCannotReplaceUnknownRecentWeeks() {
        reject("QUALITY_WEEK_COVERAGE_UNKNOWN", input().copy(
            completeQualityWeekMondays=setOf(planMonday.minusWeeks(2),planMonday.minusWeeks(3))))
    }
}
