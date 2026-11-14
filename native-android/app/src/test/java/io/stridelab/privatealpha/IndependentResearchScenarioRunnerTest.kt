package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentResearchScenarioRunnerTest {
    private fun scenario(
        name: String = "synthetic-six-day-60-to-80",
        start: Double? = 60.0,
        ceiling: Double? = 80.0,
        target: Double? = 80.0,
        weeks: Int = 5,
        runDays: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 6,
        dayCaps: Map<Int, Double>? = runDays.associateWith { 25.0 },
        previousLong: Double? = 15.0,
        longCeiling: Double? = 25.0,
        longIncrease: Double? = 2.0,
        quality: List<IndependentQualityDayRequest> = emptyList(),
        strides: List<IndependentStridesDayRequest> = emptyList()
    ) = IndependentResearchScenario(
        name = name,
        volume = IndependentWeeklyMileageInput(
            approvedStartingWeeklyKm = start,
            approvedWeeklyCeilingKm = ceiling,
            requestedTargetWeeklyKm = target,
            weeks = weeks,
            runningDays = runDays,
            preferredLongRunDay = longDay
        ),
        feasibility = IndependentMileageFeasibilityLimits(
            approvedMaxKmByDay = dayCaps,
            approvedPreviousLongRunKm = previousLong,
            approvedLongRunCeilingKm = longCeiling,
            approvedMaxBuildLongRunIncreaseKm = longIncrease
        ),
        qualityRequests = quality,
        stridesRequests = strides
    )

    private fun quality(
        week: Int = 1,
        day: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        doseReviewed: Boolean = true,
        recentWorkSeconds: Int? = 720,
        authorized: Boolean = true
    ) = IndependentQualityDayRequest(
        week = week,
        isoDay = day,
        family = family,
        input = IndependentQualityFitnessInput(
            confirmedRecentWorkSeconds = recentWorkSeconds,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = authorized
        ),
        explicitlyReviewedDose = doseReviewed,
        reviewReference = if (doseReviewed) "synthetic-coach-review" else null
    )

    private fun strides(week: Int = 1, day: Int = 3) =
        IndependentStridesDayRequest(
            week = week, isoDay = day, repetitions = 4,
            workSeconds = 20, recoverySeconds = 60,
            explicitlyReviewed = true, reviewReference = "synthetic-strides-review"
        )

    private fun preview(s: IndependentResearchScenario) =
        IndependentResearchScenarioRunner.run(s).let {
            assertTrue(it.toString(), it is IndependentResearchScenarioResult.Preview)
            it as IndependentResearchScenarioResult.Preview
        }

    private fun defer(
        stage: IndependentResearchGate,
        rule: String,
        s: IndependentResearchScenario
    ) {
        val result = IndependentResearchScenarioRunner.run(s)
        assertTrue(result.toString(), result is IndependentResearchScenarioResult.Deferred)
        val deferred = result as IndependentResearchScenarioResult.Deferred
        assertEquals(stage, deferred.stage)
        assertEquals(rule, deferred.ruleId)
    }

    @Test fun sixDaySyntheticPlanFlowsThroughVolumeAndBaseComposer() {
        val out = preview(scenario())
        assertEquals(listOf(60.0, 63.0, 66.15, 56.22, 69.45),
            out.baseWeeklyDistancesKm)
        assertEquals(30, out.baseSessionCount)
        assertEquals(5, out.base.weeks.size)
        assertEquals(IndependentResearchReconciliation.BASE_DISTANCE_ONLY,
            out.reconciliation)
        assertNull(out.overlays)
        assertFalse(out.authorizedForProduction)
        assertFalse(out.sourceEvidenceVerified)
    }

    @Test fun baseDayDistancesReconcileAcrossEveryWeekIncludingRecovery() {
        val out = preview(scenario())
        for ((volume, week) in out.mileage.weeks.zip(out.base.weeks)) {
            assertEquals(volume.weeklyKilometers, week.weeklyKilometers, 0.00001)
            assertEquals(volume.weeklyKilometers,
                week.sessions.sumOf { it.workout.total(WorkoutUnit.METERS)!! / 1000.0 },
                0.00001)
        }
        assertTrue(out.base.weeks[3].recoveryWeek)
    }

    @Test fun externalBaselineMissingStopsAtTheFirstGate() {
        defer(IndependentResearchGate.WEEKLY_VOLUME, "STARTING_VOLUME_NOT_APPROVED",
            scenario(start = null))
        defer(IndependentResearchGate.WEEKLY_VOLUME, "WEEKLY_CEILING_NOT_APPROVED",
            scenario(ceiling = null))
    }

    @Test fun implausibleWeeklyGoalDoesNotForceOneWeekJump() {
        val out = preview(scenario(start = 60.0, ceiling = 150.0,
            target = 150.0, weeks = 1,
            dayCaps = setOf(1, 2, 3, 5, 6, 7).associateWith { 30.0 },
            longCeiling = 30.0))
        assertEquals(60.0, out.baseWeeklyDistancesKm.single(), 0.00001)
        assertEquals(false, out.mileage.targetReachedOnBuildWeek)
    }

    @Test fun missingDayCeilingsStopsAtBaseComposerRatherThanIssuingSessions() {
        defer(IndependentResearchGate.BASE_WORKOUTS,
            "FEASIBILITY_UNCHECKED_DAY_CEILINGS_NOT_SUPPLIED",
            scenario(dayCaps = null))
    }

    @Test fun conflictingLongRunStopsBeforeAnyOverlays() {
        defer(IndependentResearchGate.BASE_WORKOUTS,
            "FEASIBILITY_CONFLICT_LONG_RUN_BUILD_INCREMENT_EXCEEDED",
            scenario(previousLong = 8.0, longIncrease = 1.0,
                quality = listOf(quality())))
    }

    @Test fun oneReviewedThresholdOverlayRemainsUnreconciledAndNonExecutable() {
        val out = preview(scenario(quality = listOf(quality())))
        assertEquals(1, out.reviewedQualityPreviewCount)
        assertEquals(0, out.reviewedStridesPreviewCount)
        assertEquals(IndependentResearchReconciliation.TIMED_OVERLAY_DISTANCE_UNKNOWN,
            out.reconciliation)
        assertFalse(out.timedOverlayDistanceKnown)
        assertEquals(60.0, out.baseWeeklyDistancesKm.first(), 0.00001)
        assertEquals(720, out.overlays!!.qualityPreviews.single().workSeconds)
        assertTrue("TIMED_OVERLAY_KILOMETERS_UNRECONCILED" in out.unresolvedRuleIds)
        assertFalse(out.authorizedForProduction)
    }

    @Test fun reviewedTempoIsAlsoAnAlternativeNotAddedMileage() {
        val out = preview(scenario(quality = listOf(quality(
            family = IndependentQualityFamily.TEMPO
        ))))
        assertEquals(IndependentQualityFamily.TEMPO,
            out.overlays!!.qualityPreviews.single().family)
        assertEquals(60.0, out.baseWeeklyDistancesKm.first(), 0.00001)
        assertFalse(out.overlays!!.qualityPreviews.single().executable)
    }

    @Test fun reviewedStridesAreKeptSeparateFromWeeklyKilometers() {
        val out = preview(scenario(strides = listOf(strides())))
        assertEquals(0, out.reviewedQualityPreviewCount)
        assertEquals(1, out.reviewedStridesPreviewCount)
        assertEquals(60.0, out.baseWeeklyDistancesKm.first(), 0.00001)
        assertFalse(out.overlays!!.stridesPreviews.single().distanceReconciled)
    }

    @Test fun multipleReviewedOverlaysRemainOneBasePlanNotDoubleCounted() {
        val out = preview(scenario(quality = listOf(quality()),
            strides = listOf(strides())))
        assertEquals(30, out.baseSessionCount)
        assertEquals(1, out.reviewedQualityPreviewCount)
        assertEquals(1, out.reviewedStridesPreviewCount)
        assertEquals(60.0, out.baseWeeklyDistancesKm.first(), 0.00001)
    }

    @Test fun missingQualityReviewIsAThirdStageDeferral() {
        defer(IndependentResearchGate.REVIEWED_OVERLAYS,
            "QUALITY_DOSE_REVIEW_MISSING",
            scenario(quality = listOf(quality(doseReviewed = false))))
    }

    @Test fun unverifiedQualityWorkSecondsCannotBecomeZeroOrAutomaticEligibility() {
        defer(IndependentResearchGate.REVIEWED_OVERLAYS,
            "QUALITY_QUALITY_EXPOSURE_UNKNOWN",
            scenario(quality = listOf(quality(recentWorkSeconds = null))))
        defer(IndependentResearchGate.REVIEWED_OVERLAYS,
            "QUALITY_QUALITY_NOT_AUTHORIZED",
            scenario(quality = listOf(quality(authorized = false))))
    }

    @Test fun qualityOnRecoveryWeekIsAThirdStageDeferral() {
        defer(IndependentResearchGate.REVIEWED_OVERLAYS,
            "QUALITY_RECOVERY_WEEK_HOLD",
            scenario(quality = listOf(quality(week = 4))))
    }

    @Test fun oneDayAndBadLongDayFailBeforeAnyPlanIsAssembled() {
        defer(IndependentResearchGate.WEEKLY_VOLUME,
            "INSUFFICIENT_RUN_DAYS",
            scenario(runDays = setOf(6)))
        defer(IndependentResearchGate.WEEKLY_VOLUME,
            "LONG_DAY_UNAVAILABLE",
            scenario(runDays = setOf(1, 3, 5), longDay = 6))
    }

    @Test fun sameSyntheticInputsHaveIdenticalResultsAndDoNotMutateInputs() {
        val original = scenario(quality = listOf(quality()), strides = listOf(strides()))
        val a = IndependentResearchScenarioRunner.run(original)
        val b = IndependentResearchScenarioRunner.run(original)
        assertEquals(a, b)
        assertEquals(60.0, original.volume.approvedStartingWeeklyKm!!, 0.001)
        assertEquals(2, original.qualityRequests.single().isoDay)
        val result = a as IndependentResearchScenarioResult.Preview
        assertTrue("M3_SOURCE_COVERAGE_UNVERIFIED" in result.unresolvedRuleIds)
        assertTrue("M3_INTENSITY_CLASSIFICATION_UNVERIFIED" in result.unresolvedRuleIds)
    }
}
