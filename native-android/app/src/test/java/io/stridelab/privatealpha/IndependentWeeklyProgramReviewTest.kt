package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentWeeklyProgramReviewTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val owner = "synthetic-runner"
    private val mileage = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 5, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        mileage.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private val base: IndependentConstrainedMultiweekDecision.Candidate by lazy {
        val candidate = IndependentConstrainedMultiweekPlanner.generate(mileage, limits)
        assertTrue(candidate.toString(), candidate is IndependentConstrainedMultiweekDecision.Candidate)
        candidate as IndependentConstrainedMultiweekDecision.Candidate
    }
    private fun spacing(count: Int = 1): IndependentQualitySpacingDecision.Candidate {
        val candidate = IndependentQualitySpacingPlanner.propose(
            base, IndependentQualitySpacingRequest(count)
        )
        assertTrue(candidate.toString(), candidate is IndependentQualitySpacingDecision.Candidate)
        return candidate as IndependentQualitySpacingDecision.Candidate
    }
    private fun history(
        daysBefore: Long,
        family: IndependentQualityFamily = IndependentQualityFamily.THRESHOLD,
        seconds: Int = 720
    ) = IndependentQualityWorkObservation(
        assertedOwnerId = owner,
        sessionId = "session-" + family + "-" + daysBefore,
        sourceRecordId = "source-" + family + "-" + daysBefore,
        performedOn = monday.minusDays(daysBefore),
        family = family,
        recordedWorkSeconds = seconds,
        reportedResponse = IndependentWeekResponse.TOLERATED
    )
    private fun slot(day: Int, family: IndependentQualityFamily) =
        IndependentQualityDoseSlotRequest(
            isoDay = day,
            family = family,
            availableSessionSeconds = 3600,
            approvedSessionWorkCeilingSeconds = 1200
        )
    private fun dose(
        arrangement: IndependentQualitySpacingDecision.Candidate,
        two: Boolean = false
    ): IndependentQualityDoseReviewDecision.Preview {
        val input = IndependentQualityDoseReviewInput(
            assertedOwnerId = owner, planningMonday = monday, requestedWeek = 1,
            requestedSlots = if (two) listOf(
                slot(1, IndependentQualityFamily.THRESHOLD),
                slot(3, IndependentQualityFamily.TEMPO)
            ) else listOf(slot(2, IndependentQualityFamily.THRESHOLD)),
            observedHistory = listOf(
                history(18), history(9),
                history(19, IndependentQualityFamily.TEMPO),
                history(8, IndependentQualityFamily.TEMPO)
            ),
            approvedWeeklyQualityWorkCeilingSeconds = 2400,
            weeklyReviewReference = "synthetic-reviewed-week"
        )
        val decision = IndependentQualityDoseProgression.review(base, arrangement, input)
        assertTrue(decision.toString(), decision is IndependentQualityDoseReviewDecision.Preview)
        return decision as IndependentQualityDoseReviewDecision.Preview
    }
    private fun segmentCount(preview: IndependentQualityDayPreview): Int =
        preview.qualityWorkout.elements.sumOf {
            when (it) {
                is IndependentWorkoutElement.Step -> it.value.repetitions
                is IndependentWorkoutElement.Intervals -> it.value.repetitions * 2 - 1
            }
        }
    private fun claim(preview: IndependentQualityDayPreview): IndependentWeeklyQualityDistanceClaim {
        val size = segmentCount(preview)
        val meters = preview.baseAllocatedKilometers * 1000.0
        return IndependentWeeklyQualityDistanceClaim(
            isoDay = preview.isoDay,
            segmentDistancesMeters = List(size) { i ->
                if (i < size - 1) 300.0 else meters - 300.0 * (size - 1)
            },
            reviewReference = "synthetic-distance-review"
        )
    }
    private fun input(
        quality: IndependentQualityDoseReviewDecision.Preview?,
        external: Set<LocalDate> = emptySet(),
        reviewed: Boolean = true,
        gap: Int = 2,
        claimed: List<IndependentWeeklyQualityDistanceClaim> =
            quality?.opportunities?.map { claim(it.selected) }.orEmpty()
    ) = IndependentWeeklyProgramReviewInput(
        planningMonday = monday,
        qualityDistances = claimed,
        otherDisciplineHardDates = external,
        otherDisciplineCalendarReviewed = reviewed,
        reviewedMinimumHardDayGap = gap
    )
    private fun inspect(
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing(),
        review: IndependentQualityDoseReviewDecision? = dose(arrangement),
        criteria: IndependentWeeklyProgramReviewInput =
            input(review as? IndependentQualityDoseReviewDecision.Preview)
    ) = IndependentWeeklyProgramReview.inspect(base, arrangement, review, criteria)
    private fun accepted(
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing(),
        review: IndependentQualityDoseReviewDecision? = dose(arrangement),
        criteria: IndependentWeeklyProgramReviewInput =
            input(review as? IndependentQualityDoseReviewDecision.Preview)
    ): IndependentWeeklyProgramReviewDecision.Preview {
        val outcome = inspect(arrangement, review, criteria)
        assertTrue(outcome.toString(), outcome is IndependentWeeklyProgramReviewDecision.Preview)
        return outcome as IndependentWeeklyProgramReviewDecision.Preview
    }
    private fun reject(
        code: String,
        arrangement: IndependentQualitySpacingDecision.Candidate = spacing(),
        review: IndependentQualityDoseReviewDecision? = dose(arrangement),
        criteria: IndependentWeeklyProgramReviewInput =
            input(review as? IndependentQualityDoseReviewDecision.Preview)
    ) {
        val outcome = inspect(arrangement, review, criteria)
        assertTrue(outcome.toString(), outcome is IndependentWeeklyProgramReviewDecision.Deferred)
        assertEquals(code, (outcome as IndependentWeeklyProgramReviewDecision.Deferred).ruleId)
    }

    @Test fun oneReviewedThresholdReplacesTuesdayEasyWithoutAddingAnyDistance() {
        val result = accepted()
        assertEquals(60.0, result.weeklyKmUnchanged, 0.00001)
        assertEquals(6, result.days.size)
        assertEquals(listOf(2), result.qualityDays)
        assertEquals(6, result.longDay)
        assertEquals(720, result.qualityWorkSeconds)
        assertTrue(result.segmentDistanceArithmeticChecked)
        assertEquals(5.0, result.days.first().originallyAllocatedKm, 0.00001)
        assertEquals(60.0, result.days.sumOf { it.originallyAllocatedKm }, 0.00001)
        assertEquals(listOf(2, 3, 4, 5), result.futureWeeksRemainBaseOnly)
        assertFalse(result.authorizedForPlanMutation)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun twoDifferentFamiliesUseOnlyExistingMondayAndWednesdayBudgets() {
        val arrangement = spacing(2)
        val reviewed = dose(arrangement, two = true)
        val result = accepted(arrangement, reviewed)
        assertEquals(listOf(1, 3), result.qualityDays)
        assertEquals(1440, result.qualityWorkSeconds)
        assertEquals(60.0, result.weeklyKmUnchanged, 0.00001)
        assertTrue(result.days.filter {
            it.role == IndependentProgramDayRole.QUALITY_REPLACEMENT_PREVIEW
        }.all { it.timeToDistanceCheck != null && !it.executable })
        assertFalse(result.ownerAndSourceProvenanceVerified)
        assertFalse(result.qualityIntensityVerified)
    }

    @Test fun noQualityRequestedLeavesEntireEasyLongWeekUnmodified() {
        val arrangement = spacing(0)
        val result = accepted(arrangement, null, input(null))
        assertTrue(result.qualityDays.isEmpty())
        assertEquals(0, result.qualityWorkSeconds)
        assertEquals(60.0, result.days.sumOf { it.originallyAllocatedKm }, 0.00001)
        assertEquals(1, result.days.count { it.role == IndependentProgramDayRole.LONG_BASE })
        assertTrue(result.days.all { !it.executable })
    }

    @Test fun proposedHardWorkoutCannotSkipM9bDoseReview() {
        reject("QUALITY_DOSE_REVIEW_MISSING", review = null, criteria = input(null))
    }

    @Test fun oneReviewedDoseCannotSilentlyDiscardAnotherM6bSelectedDay() {
        val two = spacing(2)
        val oneDose = dose(spacing(1))
        reject("M9B_SPACING_SLOTS_NOT_ALL_REVIEWED",
            arrangement = two, review = oneDose)
    }

    @Test fun aDoseReviewRejectionCannotBecomeAProgram() {
        reject("M9B_QUALITY_TOLERANCE_UNKNOWN",
            review = IndependentQualityDoseReviewDecision.Deferred(
                "QUALITY_TOLERANCE_UNKNOWN", "Synthetic uncertainty"
            ), criteria = input(null))
    }

    @Test fun missingQualityDistanceOrExtraUnselectedDayFailsClosed() {
        val d = dose(spacing())
        reject("QUALITY_DISTANCE_CLAIMS_NOT_EXACT",
            review = d, criteria = input(d, claimed = emptyList()))
        reject("QUALITY_DISTANCE_CLAIMS_NOT_EXACT",
            review = d, criteria = input(d, claimed = listOf(
                claim(d.opportunities.single().selected),
                IndependentWeeklyQualityDistanceClaim(3, listOf(500.0), "unexpected")
            )))
    }

    @Test fun mismatchedQualitySegmentTotalRejectsInsteadOfInventingPace() {
        val d = dose(spacing())
        val original = claim(d.opportunities.single().selected)
        val bad = original.copy(segmentDistancesMeters =
            original.segmentDistancesMeters.dropLast(1) +
                (original.segmentDistancesMeters.last() + 1000.0))
        reject("M9A_QUALITY_DISTANCE_SUM_MISMATCH",
            review = d, criteria = input(d, claimed = listOf(bad)))
    }

    @Test fun unreconciledOrMissingSegmentReviewsDoNotPass() {
        val d = dose(spacing())
        val original = claim(d.opportunities.single().selected)
        reject("M9A_EXTERNAL_SEGMENT_REVIEW_MISSING",
            review = d, criteria = input(d, claimed = listOf(
                original.copy(reviewReference = null)
            )))
        reject("M9A_SEGMENT_DISTANCE_COUNT_MISMATCH",
            review = d, criteria = input(d, claimed = listOf(
                original.copy(segmentDistancesMeters = original.segmentDistancesMeters.dropLast(1))
            )))
    }

    @Test fun unreviewedCrossDisciplineCalendarHoldsEvenAnOtherwiseValidProgram() {
        reject("OTHER_DISCIPLINE_CALENDAR_UNREVIEWED",
            criteria = input(dose(spacing()), reviewed = false))
    }

    @Test fun anotherHardDisciplineOnQualityDayOrAdjacentIsHeldForReview() {
        val d = dose(spacing())
        reject("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
            review = d, criteria = input(d, external = setOf(monday.plusDays(1))))
        reject("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
            review = d, criteria = input(d, external = setOf(monday.plusDays(2))))
    }

    @Test fun externalHardTrainingAdjacentToSaturdayLongRunAlsoHolds() {
        val d = dose(spacing())
        reject("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
            review = d, criteria = input(d, external = setOf(monday.plusDays(4))))
        reject("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
            review = d, criteria = input(d, external = setOf(monday.plusDays(5))))
    }

    @Test fun crossWeekHardSessionIsCheckedWithoutGuessingFatigue() {
        val d = dose(spacing())
        reject("OTHER_DISCIPLINE_HARD_DAY_CONFLICT",
            review = d, criteria = input(d, external = setOf(monday.minusDays(1))))
        val outcome = accepted(criteria = input(d, external = setOf(monday.minusDays(3))))
        assertEquals(60.0, outcome.weeklyKmUnchanged, 0.00001)
    }

    @Test fun hardDayGapCanBeIndependentlyTightenedAndReviewed() {
        val d = dose(spacing())
        reject("RUNNING_HARD_DAY_SPACING_CONFLICT",
            review = d, criteria = input(d, gap = 3))
        val allowed = accepted(criteria = input(d, gap = 2))
        assertTrue(allowed.segmentDistanceArithmeticChecked)
    }

    @Test fun externalActivityOutsideReviewWindowCannotBeUsedAsCompleteCalendar() {
        reject("EXTERNAL_HARD_DATE_OUTSIDE_WINDOW",
            criteria = input(dose(spacing()), external = setOf(monday.minusDays(5))))
    }

    @Test fun duplicateSegmentClaimsAreRejectedWithoutDoubleCounting() {
        val d = dose(spacing())
        val q = claim(d.opportunities.single().selected)
        reject("QUALITY_DISTANCE_CLAIMS_NOT_EXACT",
            review = d, criteria = input(d, claimed = listOf(q, q)))
    }

    @Test fun allOutputsAreDeterministicWithoutSourceOrQualityAuthorization() {
        val d = dose(spacing())
        val criteria = input(d)
        val a = IndependentWeeklyProgramReview.inspect(base, spacing(), d, criteria)
        val b = IndependentWeeklyProgramReview.inspect(base, spacing(), d, criteria)
        assertEquals(a, b)
        val accepted = a as IndependentWeeklyProgramReviewDecision.Preview
        assertFalse(accepted.ownerAndSourceProvenanceVerified)
        assertFalse(accepted.qualityIntensityVerified)
        assertFalse(accepted.otherDisciplineFatigueVerified)
        assertFalse(accepted.authorizedForProduction)
        assertEquals(60.0, base.mileage.weeks.first().weeklyKilometers, 0.00001)
    }
}
