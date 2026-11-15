package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentTimedQualityDistanceReconciliationTest {
    private val request = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 5, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        request.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private val base by lazy {
        val outcome = IndependentConstrainedMultiweekPlanner.generate(request, limits)
        assertTrue(outcome.toString(), outcome is IndependentConstrainedMultiweekDecision.Candidate)
        outcome as IndependentConstrainedMultiweekDecision.Candidate
    }
    private val spacing by lazy {
        val outcome = IndependentQualitySpacingPlanner.propose(
            base, IndependentQualitySpacingRequest(1)
        )
        assertTrue(outcome.toString(), outcome is IndependentQualitySpacingDecision.Candidate)
        outcome as IndependentQualitySpacingDecision.Candidate
    }
    private fun preview(
        week: Int = 1, day: Int = 2,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        allocated: Double? = null
    ): IndependentQualityDayPreview {
        val template = IndependentQualityTemplates.get(
            family, IndependentQualityStage.INTRO
        )
        val budget = allocated ?: base.baseWorkouts.weeks[week - 1]
            .sessions.single { it.isoDay == day }.allocatedKilometers
        return IndependentQualityDayPreview(
            week = week,
            isoDay = day,
            baseAllocatedKilometers = budget,
            family = family,
            qualityWorkout = template.workout,
            totalSeconds = template.totalSeconds().toInt(),
            workSeconds = template.workSeconds().toInt(),
            ruleIds = listOf("SYNTHETIC_TEMPLATE_ONLY")
        )
    }
    private fun distances(quality: IndependentQualityDayPreview = preview()): List<Double> {
        val total = quality.baseAllocatedKilometers * 1000.0
        return if (quality.family == IndependentQualityFamily.THRESHOLD)
            listOf(500.0, 500.0, 300.0, 500.0, 300.0, 500.0, total - 2600.0)
        else listOf(500.0, 1000.0, total - 1500.0)
    }
    private fun inspect(
        q: IndependentQualityDayPreview = preview(),
        d: List<Double> = distances(q),
        s: IndependentQualitySpacingDecision.Candidate = spacing,
        reference: String? = "explicit-synthetic-segment-review"
    ) = IndependentTimedQualityDistanceReconciliation.review(
        base, s, q, d, reference
    )
    private fun checked(
        q: IndependentQualityDayPreview = preview(),
        d: List<Double> = distances(q)
    ): IndependentTimedDistanceDecision.MathematicallyReconciled {
        val result = inspect(q, d)
        assertTrue(result.toString(), result is IndependentTimedDistanceDecision.MathematicallyReconciled)
        return result as IndependentTimedDistanceDecision.MathematicallyReconciled
    }
    private fun reject(
        rule: String,
        q: IndependentQualityDayPreview = preview(),
        d: List<Double> = distances(q),
        reference: String? = "synthetic-reviewed"
    ) {
        val r = inspect(q, d, reference = reference)
        assertTrue(r.toString(), r is IndependentTimedDistanceDecision.Deferred)
        assertEquals(rule, (r as IndependentTimedDistanceDecision.Deferred).ruleId)
    }

    @Test fun thresholdThreeByFourMinutesUsesOnlyTwoRecoveries() {
        val out = checked()
        assertEquals(7, out.segmentDistances.size)
        assertEquals(listOf(
            WorkoutPurpose.WARM_UP,
            WorkoutPurpose.WORK,
            WorkoutPurpose.RECOVERY,
            WorkoutPurpose.WORK,
            WorkoutPurpose.RECOVERY,
            WorkoutPurpose.WORK,
            WorkoutPurpose.COOL_DOWN
        ), out.segmentDistances.map { it.purpose })
        assertEquals(12.0 * 60, out.workSeconds, 0.00001)
        assertEquals(500.0 + 500.0 + 300.0 + 500.0 + 300.0 + 500.0 +
            out.segmentDistances.last().assertedDistanceMeters,
            out.sessionDistanceKm * 1000.0, 0.01)
        assertFalse(out.executable)
        assertFalse(out.sourceDistancesVerified)
        assertFalse(out.intensityVerified)
        assertFalse(out.authorizedForProduction)
    }

    @Test fun exactOriginalM6aWeeklyDistanceIsRetainedNotAddedToTheOverlay() {
        val out = checked()
        assertEquals(base.mileage.weeks.first().weeklyKilometers,
            out.weekDistanceKmUnchanged, 0.00001)
        assertEquals(base.baseWorkouts.weeks.first().sessions.single { it.isoDay == 2 }.allocatedKilometers,
            out.sessionDistanceKm, 0.00001)
        assertEquals(60.0, out.weekDistanceKmUnchanged, 0.00001)
    }

    @Test fun tempoWarmWorkCoolThreeSegmentsReconcileWithoutInferringPace() {
        val out = checked(preview(family = IndependentQualityFamily.TEMPO))
        assertEquals(3, out.segmentDistances.size)
        assertEquals(listOf(
            WorkoutTarget.EASY,
            WorkoutTarget.TEMPO,
            WorkoutTarget.EASY
        ), out.segmentDistances.map { it.target })
        assertEquals(720.0, out.workSeconds, 0.00001)
        assertTrue("SOURCE_AND_INTENSITY_NOT_VERIFIED" in out.ruleIds)
    }

    @Test fun segmentCountMismatchDoesNotFillDistancesFromSeconds() {
        val q = preview()
        reject("SEGMENT_DISTANCE_COUNT_MISMATCH", q, distances(q).dropLast(1))
    }

    @Test fun explicitDistanceMustBePositiveFiniteAndNonmissing() {
        val q = preview()
        val d = distances(q)
        reject("INVALID_SEGMENT_DISTANCE", q, d.dropLast(1) + Double.NaN)
        reject("INVALID_SEGMENT_DISTANCE", q, d.dropLast(1) + 0.0)
        reject("INVALID_SEGMENT_DISTANCE", q, d.dropLast(1) + Double.POSITIVE_INFINITY)
    }

    @Test fun mismatchedDailyDistancesDoNotChangeMileageOrPassTheReview() {
        val q = preview()
        val d = distances(q)
        reject("QUALITY_DISTANCE_SUM_MISMATCH", q, d.dropLast(1) + (d.last() + 1000.0))
        reject("QUALITY_DISTANCE_SUM_MISMATCH", q, d.dropLast(1) + (d.last() - 100.0))
    }

    @Test fun missingExternalReviewRefCannotPass() {
        reject("EXTERNAL_SEGMENT_REVIEW_MISSING", reference = null)
        reject("EXTERNAL_SEGMENT_REVIEW_MISSING", reference = " ")
    }

    @Test fun doseFromDifferentDailyBudgetIsNotQuietlyReassigned() {
        reject("QUALITY_DISTANCE_BUDGET_MISMATCH",
            q = preview(allocated = preview().baseAllocatedKilometers + 1.0))
    }

    @Test fun impossibleLongAndRecoveryDayQualityChangesAreRejected() {
        val long = preview(day = 6)
        reject("LONG_RUN_QUALITY_CONFLICT", q = long)
        val recovery = preview(week = 4)
        reject("RECOVERY_WEEK_QUALITY_HOLD", q = recovery)
    }

    @Test fun scheduledButUnselectedQualityDayIsNotEligible() {
        val q = preview(day = 3)
        reject("SPACING_SLOT_NOT_SELECTED", q = q)
    }

    @Test fun fakeFamilyLabelCannotChangeTheWorkIntensityClassification() {
        val threshold = preview()
        val wrong = threshold.copy(family = IndependentQualityFamily.TEMPO)
        reject("QUALITY_WORK_TARGET_MISMATCH", q = wrong)
    }

    @Test fun mixedMeterAndSecondWorkoutStepsRequireIndependentEvidence() {
        val original = preview()
        val mixed = original.copy(qualityWorkout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(
                WorkoutStep(WorkoutPurpose.WARM_UP, WorkoutUnit.METERS, 2000.0, 1, WorkoutTarget.EASY)
            ),
            IndependentWorkoutElement.Step(
                WorkoutStep(WorkoutPurpose.WORK, WorkoutUnit.SECONDS, 720.0, 1, WorkoutTarget.THRESHOLD)
            ),
            IndependentWorkoutElement.Step(
                WorkoutStep(WorkoutPurpose.COOL_DOWN, WorkoutUnit.SECONDS, 480.0, 1, WorkoutTarget.EASY)
            )
        )))
        reject("MIXED_UNITS_CANNOT_RECONCILE", q = mixed, d = distances(original))
    }

    @Test fun noMileageOrWorkoutChangesOccurAcrossRepeatedCalls() {
        val q = preview()
        val d = distances(q)
        val a = inspect(q, d)
        val b = inspect(q, d)
        assertEquals(a, b)
        assertEquals(60.0, base.mileage.weeks.first().weeklyKilometers, 0.00001)
        assertTrue(base.baseWorkouts.weeks.first().sessions.all { !it.qualityWorkAuthorized })
    }
}
