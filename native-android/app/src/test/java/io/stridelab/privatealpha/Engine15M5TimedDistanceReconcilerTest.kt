package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

/** M5C arithmetic-only tests; synthetic distances are NOT authenticated FIT/Garmin evidence. */
class Engine15M5TimedDistanceReconcilerTest {
    private fun source(
        recent: Double = 75.0, target: Double = 80.0,
        longest: Double = 20.0, days: Int = 6,
        returning: Boolean = false, recoveryHold: Boolean = false,
        recentBasis: String = "SELF_REPORTED_AVERAGE"
    ): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = LocalDate.of(2026, 11, 16),
            weeks = 8, days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = days, longDay = 7, qualityCount = 0,
            weeklyKm = recent, targetWeeklyKm = target, longestKm = longest,
            returning = returning, timeBudgetEnabled = false,
            policyChoice = "engine15", trainYourWay = true,
            recentMileageBasis = recentBasis, runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(
                8, days, target, returning, recoveryHold
            )
        )
    }

    private fun request(
        week: Int = 1, slot: Int = 1,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD
    ) = Engine15M5QualityPreviewRequest(
        weekNumber = week, slotNumber = slot, family = family,
        fitnessInput = IndependentQualityFitnessInput(
            confirmedRecentWorkSeconds = 1200,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = true,
        reviewReference = "caller-reviewed-synthetic-dose"
    )

    private fun m5b(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>
    ) = Engine15M5QualityPreviewComposer.compose(source, requests)
        as Engine15M5QualityDecision.Candidate

    private fun expanded(
        workout: IndependentWorkoutComposition
    ): List<WorkoutStep> = buildList {
        for (item in workout.elements) when (item) {
            is IndependentWorkoutElement.Step ->
                repeat(item.value.repetitions) { add(item.value) }
            is IndependentWorkoutElement.Intervals ->
                repeat(item.value.repetitions) { index ->
                    add(item.value.work)
                    if (index < item.value.repetitions - 1) add(item.value.recovery)
                }
        }
    }

    /** Synthetic caller assertions proportional to time; NOT a pace estimator. */
    private fun claim(
        preview: Engine15M5QualitySlotPreview,
        reference: String? = "synthetic-external-segment-review"
    ): Engine15M5SegmentDistanceClaim {
        val phases = expanded(preview.workout)
        val targetMeters = preview.originalEasyDistanceKm * 1000.0
        val totalSeconds = phases.sumOf { it.amount }
        val first = phases.dropLast(1).map { it.amount / totalSeconds * targetMeters }
        return Engine15M5SegmentDistanceClaim(
            weekNumber = preview.weekNumber,
            slotNumber = preview.slotNumber,
            assertedMetersByTimedSegment =
                first + (targetMeters - first.sum()),
            externalReviewReference = reference
        )
    }

    private fun arithmetic(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>,
        claims: List<Engine15M5SegmentDistanceClaim>
    ): Engine15M5DistanceDecision.ArithmeticOnly {
        val result = Engine15M5TimedDistanceReconciler.reconcile(source, requests, claims)
        assertTrue("Expected arithmetic-only result, got $result",
            result is Engine15M5DistanceDecision.ArithmeticOnly)
        return result as Engine15M5DistanceDecision.ArithmeticOnly
    }

    private fun deferred(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>,
        claims: List<Engine15M5SegmentDistanceClaim>,
        expected: String
    ) {
        val result = Engine15M5TimedDistanceReconciler.reconcile(source, requests, claims)
        assertTrue("Expected $expected, got $result",
            result is Engine15M5DistanceDecision.Deferred)
        assertEquals(expected,
            (result as Engine15M5DistanceDecision.Deferred).reasonCode)
    }

    @Test fun thresholdIntervalsHaveExactlyOneFewerRecoveryThanWork() {
        val m4b = source()
        val requests = listOf(request())
        val quality = m5b(m4b, requests)
        val preview = quality.qualityPreviews.single()
        val result = arithmetic(m4b, requests, listOf(claim(preview)))
        val matching = result.matches.single()
        val workCount = matching.segments.count { it.purpose == WorkoutPurpose.WORK }
        val recoveryCount = matching.segments.count { it.purpose == WorkoutPurpose.RECOVERY }
        assertTrue(workCount >= 2)
        assertEquals(workCount - 1, recoveryCount)
        assertEquals(WorkoutPurpose.WARM_UP, matching.segments.first().purpose)
        assertEquals(WorkoutPurpose.COOL_DOWN, matching.segments.last().purpose)
        assertEquals(1200, matching.workSeconds)
        assertTrue(matching.totalSeconds <= 3600)
        assertEquals(preview.originalEasyDistanceKm * 1000.0,
            matching.sumAssertedMeters, 0.01)
        assertEquals(quality.baseWeeks, result.originalBaseWeeks)
        assertEquals(quality.qualityPreviews, result.originalQualityPreviews)
        assertFalse(matching.sourceDistancesVerified)
        assertFalse(matching.qualityClassificationVerified)
        assertFalse(matching.executable)
        assertFalse(result.sourceDistancesVerified)
        assertFalse(result.qualityClassificationVerified)
        assertFalse(result.scheduled)
        assertFalse(result.executable)
        assertFalse(result.authorizedForProduction)
        assertEquals(listOf(67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9),
            result.originalBaseWeeks.map { it.budgetKm })
    }

    @Test fun tempoHasThreeTimedPhasesAndNoInferredDistance() {
        val m4b = source()
        val requests = listOf(request(family = IndependentQualityFamily.TEMPO))
        val quality = m5b(m4b, requests)
        val result = arithmetic(m4b, requests, listOf(claim(quality.qualityPreviews.single())))
        val m = result.matches.single()
        assertEquals(IndependentQualityFamily.TEMPO, m.family)
        assertEquals(listOf(WorkoutPurpose.WARM_UP, WorkoutPurpose.WORK,
            WorkoutPurpose.COOL_DOWN), m.segments.map { it.purpose })
        assertEquals(m.originalEasySlotKm * 1000.0, m.sumAssertedMeters, 0.01)
        assertNull(quality.qualityPreviews.single().workout.total(WorkoutUnit.METERS))
        assertFalse(result.executable)
    }

    @Test fun missingDuplicateWrongSlotAndReviewClaimReject() {
        val m4b = source()
        val r1 = request()
        val q1 = m5b(m4b, listOf(r1)).qualityPreviews.single()
        val c1 = claim(q1)
        deferred(m4b, listOf(r1), emptyList(), "CLAIM_COVERAGE_MISMATCH")
        deferred(m4b, listOf(r1), listOf(c1.copy(slotNumber = 2)),
            "CLAIM_SLOT_MISMATCH")
        deferred(m4b, listOf(r1), listOf(c1.copy(externalReviewReference = " ")),
            "SEGMENT_DISTANCE_REVIEW_MISSING")
        val r2 = request(week = 2)
        deferred(m4b, listOf(r1, r2), listOf(c1, c1),
            "DUPLICATE_SEGMENT_CLAIM")
    }

    @Test fun missingExtraInvalidAndNaNSegmentDistancesReject() {
        val m4b = source()
        val requests = listOf(request())
        val base = claim(m5b(m4b, requests).qualityPreviews.single())
        deferred(m4b, requests,
            listOf(base.copy(assertedMetersByTimedSegment =
                base.assertedMetersByTimedSegment.dropLast(1))),
            "SEGMENT_COUNT_MISMATCH")
        deferred(m4b, requests,
            listOf(base.copy(assertedMetersByTimedSegment =
                base.assertedMetersByTimedSegment + 1.0)),
            "SEGMENT_COUNT_MISMATCH")
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -2.0)) {
            val modified = base.assertedMetersByTimedSegment.toMutableList()
            modified[0] = bad
            deferred(m4b, requests,
                listOf(base.copy(assertedMetersByTimedSegment = modified)),
                "INVALID_ASSERTED_METERS")
        }
    }

    @Test fun shortfallCannotBeFabricatedByExtraDistance() {
        val m4b = source()
        val requests = listOf(request())
        val base = claim(m5b(m4b, requests).qualityPreviews.single())
        val inflated = base.assertedMetersByTimedSegment.toMutableList()
        inflated[0] += 1500.0
        deferred(m4b, requests,
            listOf(base.copy(assertedMetersByTimedSegment = inflated)),
            "SESSION_DISTANCE_SUM_MISMATCH")
        assertEquals(1.3, m4b.shortfallToRequestedTargetKm!!, 1e-8)
        assertEquals(78.7, m4b.peakProposedKm!!, 1e-8)
    }

    @Test fun alteredM4bDataAndUnavailableQualityRemainDeferred() {
        val m4b = source()
        val requests = listOf(request())
        val base = claim(m5b(m4b, requests).qualityPreviews.single())
        val altered = m4b.copy(weeks = m4b.weeks.mapIndexed { i, w ->
            if (i == 0) w.copy(proposedKm = 80.0) else w
        })
        deferred(altered, requests, listOf(base),
            "UPSTREAM_BASE_M4B_RESULT_MISMATCH")
        deferred(source(recoveryHold = true), requests, listOf(base),
            "UPSTREAM_BASE_M4B_NON_DISTANCE_STATUS")
        deferred(source(returning = true), requests, listOf(base),
            "UPSTREAM_RETURNING_RUNNER_QUALITY_HOLD")
        deferred(m4b, listOf(request(week = 4)), listOf(base),
            "UPSTREAM_QUALITY_RECOVERY_WEEK_HOLD")
    }

    @Test fun multipleWeeksRequireUniqueMatchedClaimsAndKeepBaseWorkouts() {
        val m4b = source()
        val requests = listOf(
            request(week = 1),
            request(week = 2, family = IndependentQualityFamily.TEMPO),
            request(week = 5)
        )
        val m5b = m5b(m4b, requests)
        val claims = m5b.qualityPreviews.map { claim(it) }
        val result = arithmetic(m4b, requests, claims.reversed())
        assertEquals(3, result.matches.size)
        assertEquals(listOf(1, 2, 5), result.matches.map { it.weekNumber })
        assertEquals(m5b.baseWeeks, result.originalBaseWeeks)
        assertEquals(m5b.qualityPreviews, result.originalQualityPreviews)
        assertEquals(m4b.weeks.map { it.proposedKm },
            result.originalBaseWeeks.map { it.budgetKm })
        assertTrue(result.matches.all {
            abs(it.sumAssertedMeters - it.originalEasySlotKm * 1000.0) <= 0.01 &&
                !it.sourceDistancesVerified && !it.qualityClassificationVerified && !it.executable
        })
        assertFalse(result.authorizedForProduction)
    }
}
