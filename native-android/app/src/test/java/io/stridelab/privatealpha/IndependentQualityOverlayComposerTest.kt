package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentQualityOverlayComposerTest {
    private fun allocation(
        weeks: Int = 5,
        days: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 6
    ): IndependentWeeklyMileageDecision.Candidate {
        val outcome = IndependentWeeklyMileagePlanner.generate(
            IndependentWeeklyMileageInput(
                approvedStartingWeeklyKm = 60.0,
                approvedWeeklyCeilingKm = 80.0,
                requestedTargetWeeklyKm = 80.0,
                weeks = weeks,
                runningDays = days,
                preferredLongRunDay = longDay
            )
        )
        assertTrue(outcome.toString(),
            outcome is IndependentWeeklyMileageDecision.Candidate)
        return outcome as IndependentWeeklyMileageDecision.Candidate
    }

    private fun limits() = IndependentMileageFeasibilityLimits(
        approvedMaxKmByDay = setOf(1, 2, 3, 5, 6, 7).associateWith { 25.0 },
        approvedPreviousLongRunKm = 15.0,
        approvedLongRunCeilingKm = 25.0,
        approvedMaxBuildLongRunIncreaseKm = 2.0
    )

    private fun quality(
        week: Int = 1,
        day: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true,
        reference: String? = "synthetic-external-review",
        authorization: Boolean = true,
        observedSeconds: Int? = 720,
        recoveryHold: Boolean = false
    ) = IndependentQualityDayRequest(
        week, day, family,
        IndependentQualityFitnessInput(
            confirmedRecentWorkSeconds = observedSeconds,
            approvedWorkCeilingSeconds = 1200,
            availableSessionSeconds = 3600,
            qualityAuthorized = authorization,
            recoveryOrReturnHold = recoveryHold
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = reference
    )

    private fun strides(
        week: Int = 1,
        day: Int = 3,
        reviewed: Boolean = true
    ) = IndependentStridesDayRequest(
        week, day, 4, 20, 60, reviewed,
        reviewReference = if (reviewed) "synthetic-strides-review" else null
    )

    private fun compose(
        q: List<IndependentQualityDayRequest> = emptyList(),
        s: List<IndependentStridesDayRequest> = emptyList(),
        allocation: IndependentWeeklyMileageDecision.Candidate = allocation(),
        limits: IndependentMileageFeasibilityLimits = limits()
    ) = IndependentQualityOverlayComposer.generate(
        allocation, limits, q, s
    )

    private fun deferred(
        rule: String,
        result: IndependentQualityOverlayDecision
    ) {
        assertTrue(result.toString(), result is IndependentQualityOverlayDecision.Deferred)
        assertEquals(rule, (result as IndependentQualityOverlayDecision.Deferred).ruleId)
    }

    @Test fun reviewedThresholdGetsTimeBasedPreviewWithoutInventedDistance() {
        val outcome = compose(q = listOf(quality()))
        assertTrue(outcome.toString(), outcome is IndependentQualityOverlayDecision.Candidate)
        val candidate = outcome as IndependentQualityOverlayDecision.Candidate
        val session = candidate.qualityPreviews.single()
        assertEquals(IndependentQualityFamily.THRESHOLD, session.family)
        assertEquals(720, session.workSeconds)
        assertEquals(2040, session.totalSeconds)
        assertNull(session.qualityWorkout.total(WorkoutUnit.METERS))
        assertFalse(session.distanceReconciled)
        assertFalse(session.executable)
        assertFalse(candidate.kilometerReconciliationComplete)
        assertFalse(candidate.authorizedForProduction)
    }

    @Test fun tempoUsesContinuousWorkAndDoesNotSilentlyChangeWeeklyKm() {
        val outcome = compose(q = listOf(quality(family = IndependentQualityFamily.TEMPO)))
        val result = outcome as IndependentQualityOverlayDecision.Candidate
        val preview = result.qualityPreviews.single()
        assertEquals(720, preview.workSeconds)
        assertEquals(1800, preview.totalSeconds)
        assertEquals(60.0, result.baseWeeks.first().weeklyKilometers, 0.00001)
        assertEquals(result.baseWeeks.first().weeklyKilometers,
            result.baseWeeks.first().sessions.sumOf { it.allocatedKilometers }, 0.00001)
        assertFalse(result.sourceEvidenceVerified)
    }

    @Test fun unknownOrUnauthorizedDoseCannotBecomeQualityPreview() {
        deferred("QUALITY_QUALITY_EXPOSURE_UNKNOWN", compose(q = listOf(
            quality(observedSeconds = null)
        )))
        deferred("QUALITY_QUALITY_NOT_AUTHORIZED", compose(q = listOf(
            quality(authorization = false)
        )))
        deferred("QUALITY_RECOVERY_HOLD", compose(q = listOf(
            quality(recoveryHold = true)
        )))
    }

    @Test fun missingExplicitReviewPreventsQualityCreation() {
        deferred("QUALITY_DOSE_REVIEW_MISSING", compose(q = listOf(
            quality(reviewed = false)
        )))
        deferred("QUALITY_DOSE_REVIEW_MISSING", compose(q = listOf(
            quality(reference = "")
        )))
    }

    @Test fun qualityOnLongDayOrRecoveryWeekIsDeferred() {
        deferred("QUALITY_LONG_RUN_DAY_CONFLICT", compose(q = listOf(
            quality(day = 6)
        )))
        deferred("QUALITY_RECOVERY_WEEK_HOLD", compose(q = listOf(
            quality(week = 4)
        )))
    }

    @Test fun multipleQualityWorkoutsInOneWeekNeedSeparateReview() {
        deferred("MULTIPLE_QUALITY_PER_WEEK_UNREVIEWED", compose(q = listOf(
            quality(day = 2), quality(day = 3, family = IndependentQualityFamily.TEMPO)
        )))
    }

    @Test fun qualityDayMustExistAndCannotBeARecoveryWeek() {
        deferred("QUALITY_DAY_UNAVAILABLE", compose(q = listOf(
            quality(day = 4)
        )))
        deferred("QUALITY_DAY_UNAVAILABLE", compose(q = listOf(
            quality(week = 99)
        )))
    }

    @Test fun stridesCanBeRepresentedAsTimeOnlyIntervalPreview() {
        val candidate = compose(s = listOf(strides()))
            as IndependentQualityOverlayDecision.Candidate
        val session = candidate.stridesPreviews.single()
        assertEquals(80, session.workSeconds)
        assertEquals(260, session.totalSeconds)
        val middle = session.stridesWorkout.elements.single()
            as IndependentWorkoutElement.Intervals
        assertEquals(4, middle.value.repetitions)
        assertEquals(WorkoutTarget.STRIDES, middle.value.work.target)
        assertEquals(WorkoutTarget.EASY, middle.value.recovery.target)
        assertFalse(session.distanceReconciled)
        assertFalse(session.executable)
        assertNull(session.stridesWorkout.total(WorkoutUnit.METERS))
    }

    @Test fun stridesRequireReviewAndCannotBeLayeredOnQualityDay() {
        deferred("STRIDES_REVIEW_MISSING", compose(s = listOf(
            strides(reviewed = false)
        )))
        deferred("STRIDES_QUALITY_DAY_CONFLICT", compose(
            q = listOf(quality(day = 3)), s = listOf(strides(day = 3))
        ))
    }

    @Test fun recoveryAndLongDayAlsoHoldStrides() {
        deferred("STRIDES_RECOVERY_WEEK_HOLD", compose(s = listOf(
            strides(week = 4)
        )))
        deferred("STRIDES_LONG_RUN_DAY_CONFLICT", compose(s = listOf(
            strides(day = 6)
        )))
    }

    @Test fun duplicateStridesRequestsCannotDoubleCountDose() {
        deferred("DUPLICATE_STRIDES_DAY", compose(s = listOf(
            strides(), strides()
        )))
    }

    @Test fun missingM4LimitsCannotBeBypassedByReviewedQualityRequests() {
        deferred("BASE_FEASIBILITY_UNCHECKED_DAY_CEILINGS_NOT_SUPPLIED",
            compose(q = listOf(quality()),
                limits = limits().copy(approvedMaxKmByDay = null)))
    }

    @Test fun theNoRequestCaseDoesNotInventQualityWorkouts() {
        deferred("NO_REVIEWED_OVERLAY_REQUESTS", compose())
    }

    @Test fun outputIsDeterministicAndNeverMutatesTheOriginalM4Weeks() {
        val plan = allocation()
        val snapshot = plan.copy(weeks = plan.weeks.map {
            it.copy(dayAllocations = it.dayAllocations.toList())
        })
        val a = compose(q = listOf(quality()), s = listOf(strides()), allocation = plan)
        val b = compose(q = listOf(quality()), s = listOf(strides()), allocation = plan)
        assertEquals(a, b)
        assertEquals(snapshot, plan)
        val result = a as IndependentQualityOverlayDecision.Candidate
        assertEquals(1, result.qualityPreviews.size)
        assertEquals(1, result.stridesPreviews.size)
    }
}
