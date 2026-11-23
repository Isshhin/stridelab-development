package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Prespecified M5B shadow gates. No test certifies executable workouts. */
class Engine15M5QualityPreviewComposerTest {
    private fun source(
        recentKm: Double = 75.0,
        longestKm: Double = 20.0,
        targetKm: Double = 80.0,
        days: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        recoveryHold: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE",
        experience: Int = 2
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = experience,
            start = LocalDate.of(2026, 11, 16),
            weeks = weeks, days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = days, longDay = 7, qualityCount = 0,
            weeklyKm = recentKm, targetWeeklyKm = targetKm,
            longestKm = longestKm, returning = returning,
            timeBudgetEnabled = false, policyChoice = "engine15",
            trainYourWay = true, recentMileageBasis = basis,
            runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                weeks, days, targetKm, returning, recoveryHold
            )
        )
    }

    private fun request(
        week: Int = 1,
        slot: Int = 1,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        reviewed: Boolean = true,
        reference: String? = "synthetic-reviewed-dose-001",
        recentWorkSeconds: Int? = 1200,
        ceilingSeconds: Int? = 1200,
        approvedIncreaseSeconds: Int = 0,
        availableSeconds: Int = 3000,
        authorized: Boolean = true,
        recoveryHold: Boolean = false
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week, slotNumber = slot, family = family,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = recentWorkSeconds,
            approvedWorkCeilingSeconds = ceilingSeconds,
            approvedIncreaseSeconds = approvedIncreaseSeconds,
            availableSessionSeconds = availableSeconds,
            qualityAuthorized = authorized,
            recoveryOrReturnHold = recoveryHold
        ),
        explicitlyReviewedDose = reviewed, reviewReference = reference
    )

    private fun accepted(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>
    ): Engine15M5QualityDecision.Candidate {
        val result = Engine15M5QualityPreviewComposer.compose(source, requests)
        assertTrue("Expected preview candidate, got $result",
            result is Engine15M5QualityDecision.Candidate)
        return result as Engine15M5QualityDecision.Candidate
    }

    private fun deferred(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>,
        code: String
    ) {
        val result = Engine15M5QualityPreviewComposer.compose(source, requests)
        assertTrue("Expected $code deferral, got $result",
            result is Engine15M5QualityDecision.Deferred)
        assertEquals(code, (result as Engine15M5QualityDecision.Deferred).reasonCode)
    }

    @Test fun reviewedThresholdPreviewKeepsAllBaseKilometresUnchanged() {
        val m4b = source()
        val base = Engine15M5WorkoutComposer.compose(m4b)
            as Engine15M5CompositionDecision.Candidate
        val candidate = accepted(m4b, listOf(request()))
        assertEquals(base.weeks, candidate.baseWeeks)
        assertEquals(m4b.weeks.map { it.proposedKm },
            candidate.baseWeeks.map { it.budgetKm })
        assertFalse(candidate.scheduled)
        assertFalse(candidate.sourceEvidenceVerified)
        assertFalse(candidate.distanceReconciled)
        assertFalse(candidate.executable)
        assertFalse(candidate.authorizedForProduction)
        val quality = candidate.qualityPreviews.single()
        assertEquals(1, quality.weekNumber)
        assertEquals(1, quality.slotNumber)
        assertEquals(base.weeks.first().slots.first().distanceKm,
            quality.originalEasyDistanceKm, 1e-8)
        assertEquals(IndependentQualityFamily.THRESHOLD, quality.family)
        assertEquals(1200, quality.workSeconds)
        assertTrue(quality.totalSeconds <= 3000)
        assertEquals(quality.workSeconds.toDouble(),
            quality.workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)!!, 1e-8)
        assertEquals(quality.totalSeconds.toDouble(),
            quality.workout.total(WorkoutUnit.SECONDS)!!, 1e-8)
        assertNull(quality.workout.total(WorkoutUnit.METERS))
        assertFalse(quality.distanceReconciled)
        assertFalse(quality.sourceEvidenceVerified)
        assertFalse(quality.executable)
    }

    @Test fun reviewedTempoPreviewUsesTimeEvidenceAndNoInferredKm() {
        val candidate = accepted(source(), listOf(request(
            family = IndependentQualityFamily.TEMPO,
            recentWorkSeconds = 900,
            ceilingSeconds = 900,
            availableSeconds = 2400
        )))
        val quality = candidate.qualityPreviews.single()
        assertEquals(IndependentQualityFamily.TEMPO, quality.family)
        assertEquals(900, quality.workSeconds)
        assertTrue(quality.totalSeconds <= 2400)
        assertNull(quality.workout.total(WorkoutUnit.METERS))
        assertEquals(75.0, candidate.baseWeeks[2].budgetKm, 1e-8)
    }

    @Test fun beginnerAndEstablishedRunnerPreviewsFollowTheirReportedFamilyExposure() {
        val beginner = source(recentKm = 25.0, longestKm = 10.0,
            targetKm = 30.0, days = 4, experience = 0)
        val established = source(experience = 2)
        fun request(exposureSeconds: Int) = request(
            recentWorkSeconds = exposureSeconds,
            ceilingSeconds = exposureSeconds,
            availableSeconds = 3600
        )

        val beginnerPreview = accepted(beginner, listOf(request(8 * 60)))
            .qualityPreviews.single()
        val establishedPreview = accepted(established, listOf(request(20 * 60)))
            .qualityPreviews.single()
        assertEquals(8 * 60, beginnerPreview.workSeconds)
        assertEquals(20 * 60, establishedPreview.workSeconds)
        assertTrue(beginnerPreview.workSeconds <= 8 * 60)
        assertTrue(establishedPreview.workSeconds <= 20 * 60)
        assertFalse(beginnerPreview.executable)
        assertFalse(establishedPreview.executable)
        // Experience labels do not manufacture dose; explicit reported exposure binds each preview.
    }

    @Test fun unknownOrUnapprovedQualityEvidenceCannotSelectDose() {
        val m4b = source()
        deferred(m4b, listOf(request(reviewed = false)),
            "QUALITY_EXTERNAL_REVIEW_MISSING")
        deferred(m4b, listOf(request(reference = null)),
            "QUALITY_EXTERNAL_REVIEW_MISSING")
        deferred(m4b, listOf(request(authorized = false)),
            "QUALITY_QUALITY_NOT_AUTHORIZED")
        deferred(m4b, listOf(request(recentWorkSeconds = null)),
            "QUALITY_QUALITY_EXPOSURE_UNKNOWN")
        deferred(m4b, listOf(request(ceilingSeconds = null)),
            "QUALITY_WORK_CEILING_UNKNOWN")
        deferred(m4b, listOf(request(recoveryHold = true)),
            "QUALITY_RECOVERY_HOLD")
        deferred(m4b, listOf(request(recentWorkSeconds = 0)),
            "QUALITY_NO_CONFIRMED_QUALITY_DOSE")
    }

    @Test fun unavailableTimeDefersRatherThanFabricatingDose() {
        deferred(source(), listOf(request(availableSeconds = 600)),
            "QUALITY_DOSE_OR_TIME_CONFLICT")
    }

    @Test fun recoveryWeekAndLongRunSlotNeverReceiveQuality() {
        val m4b = source()
        deferred(m4b, listOf(request(week = 4)),
            "QUALITY_RECOVERY_WEEK_HOLD")
        deferred(m4b, listOf(request(slot = 6)),
            "QUALITY_ON_LONG_SLOT")
    }

    @Test fun returningOrTwoDayCandidateRequiresSeparateQualityReview() {
        deferred(source(returning = true), listOf(request()),
            "RETURNING_RUNNER_QUALITY_HOLD")
        val twoDays = source(recentKm = 20.0, targetKm = 25.0,
            days = 2, weeks = 1)
        deferred(twoDays, listOf(request()),
            "INSUFFICIENT_RUN_DAYS_FOR_QUALITY")
    }

    @Test fun noRequestDuplicateWeekAndInvalidSlotDefer() {
        val m4b = source()
        deferred(m4b, emptyList(), "NO_EXPLICIT_QUALITY_REQUESTS")
        deferred(m4b, listOf(request(), request(slot = 2)),
            "MULTIPLE_QUALITY_REQUESTS_IN_WEEK")
        deferred(m4b, listOf(request(week = 9)),
            "QUALITY_WEEK_NOT_FOUND")
        deferred(m4b, listOf(request(slot = 9)),
            "QUALITY_SLOT_NOT_FOUND")
    }

    @Test fun m4bTamperingAndUnknownBaselineCannotBypassM5aGate() {
        val m4b = source()
        deferred(m4b.copy(weeks = m4b.weeks.mapIndexed { i, week ->
            if (i == 0) week.copy(proposedKm = 100.0) else week
        }), listOf(request()), "BASE_M4B_RESULT_MISMATCH")
        deferred(source(recentKm = 0.0, longestKm = 0.0,
            basis = "CONFIRMED_ZERO", days = 3, experience = 0),
            listOf(request()), "BASE_M4B_NON_DISTANCE_STATUS")
        deferred(source(recoveryHold = true),
            listOf(request()), "BASE_M4B_NON_DISTANCE_STATUS")
    }

    @Test fun multipleReviewedBuildWeeksPreserveOriginalWeeksWithoutScheduling() {
        val m4b = source()
        val candidate = accepted(m4b, listOf(
            request(week = 1),
            request(week = 2, family = IndependentQualityFamily.TEMPO),
            request(week = 3),
            request(week = 5),
            request(week = 6, family = IndependentQualityFamily.TEMPO),
            request(week = 7)
        ))
        assertEquals(listOf(1, 2, 3, 5, 6, 7),
            candidate.qualityPreviews.map { it.weekNumber })
        assertEquals(m4b.weeks.map { it.proposedKm },
            candidate.baseWeeks.map { it.budgetKm })
        assertTrue(candidate.qualityPreviews.all {
            !it.distanceReconciled && !it.executable && !it.sourceEvidenceVerified
        })
        assertFalse(candidate.authorizedForProduction)
        assertFalse(candidate.scheduled)
    }
}
