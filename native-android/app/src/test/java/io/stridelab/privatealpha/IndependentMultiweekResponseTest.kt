package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentMultiweekResponseTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val request = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 1, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        request.runningDays.associateWith { 25.0 }, 20.0, 25.0, 2.0
    )

    private fun week(
        weeksAgo: Long,
        planned: Double,
        observed: Double? = planned,
        intent: IndependentObservedWeekIntent = IndependentObservedWeekIntent.BUILD,
        reviewed: Boolean = false,
        response: IndependentWeekResponse = IndependentWeekResponse.TOLERATED,
        complete: Boolean = true
    ) = IndependentObservedTrainingWeek(
        monday.minusWeeks(weeksAgo), planned, observed, complete,
        intent, reviewed, response
    )

    private fun input(
        history: List<IndependentObservedTrainingWeek> = listOf(
            week(2, 60.0), week(1, 60.0)
        ),
        increase: Double = 0.0,
        approval: String? = null,
        volume: IndependentWeeklyMileageInput = request
    ) = IndependentMultiweekResponseInput(
        volume, limits, monday, history, increase, approval
    )

    private fun candidate(value: IndependentMultiweekResponseInput):
        IndependentMultiweekResponseDecision.Preview {
        val result = IndependentMultiweekResponse.preview(value)
        assertTrue(result.toString(), result is IndependentMultiweekResponseDecision.Preview)
        return result as IndependentMultiweekResponseDecision.Preview
    }
    private fun reject(id: String, value: IndependentMultiweekResponseInput) {
        val result = IndependentMultiweekResponse.preview(value)
        assertTrue(result.toString(), result is IndependentMultiweekResponseDecision.Deferred)
        assertEquals(id, (result as IndependentMultiweekResponseDecision.Deferred).ruleId)
    }

    @Test fun repeatedBuildCompletionHoldsApprovedLoadWithoutIncrement() {
        val out = candidate(input())
        assertEquals(60.0, out.volume.weeks.single().weeklyKilometers, 0.00001)
        assertEquals("REPEAT_APPROVED_BUILD_WITHOUT_INCREMENT", out.reason)
        assertEquals(2, out.comparedBuildWeeks)
        assertFalse(out.sourceEvidenceVerified)
        assertFalse(out.authorizedForProduction)
    }

    @Test fun twoCompletedBuildsCanIncreaseOnlyWithSeparateReview() {
        reject("INCREMENT_APPROVAL_REFERENCE_MISSING", input(increase = 2.0))
        val out = candidate(input(increase = 2.0, approval = "synthetic-reviewed-increment"))
        assertEquals(62.0, out.volume.weeks.single().weeklyKilometers, 0.00001)
        assertEquals("TWO_COMPLETED_BUILD_WEEKS_WITH_APPROVED_INCREMENT", out.reason)
        assertTrue("SOURCE_COVERAGE_NOT_AUTHENTICATED" in out.unresolvedRuleIds)
    }

    @Test fun intentionalRecoveryDoesNotBecomeReducedCapacity() {
        val evidence = listOf(
            week(3, 60.0), week(2, 60.0),
            week(1, 40.0, 40.0, IndependentObservedWeekIntent.PLANNED_RECOVERY, reviewed = true)
        )
        val out = candidate(input(evidence, increase = 2.0, approval = "synthetic"))
        assertEquals(60.0, out.volume.weeks.single().weeklyKilometers, 0.00001)
        assertEquals(1, out.comparedBuildWeeks)
        assertEquals("REVIEWED_RECOVERY_RESUME_PRIOR_BUILD_NO_CATCHUP", out.reason)
    }

    @Test fun repeatedThirtyOfSixtyBuildWeeksProposesReducedRepeatOnly() {
        val out = candidate(input(listOf(
            week(2, 60.0, 30.0), week(1, 60.0, 30.0)
        )))
        assertEquals(30.0, out.volume.weeks.single().weeklyKilometers, 0.00001)
        assertEquals("REPEATED_UNDERCOMPLETION_REDUCED_REPEAT_ONLY", out.reason)
    }

    @Test fun successfulThirtyKmRebuildDoesNotJumpBackToOriginalSixty() {
        // This follows two missed 60 km weeks, a reviewed reduction to 30 km,
        // and then two fully completed 30 km build weeks.
        val rebuild = input(history = listOf(
            week(2, 30.0, 30.0), week(1, 30.0, 30.0)
        ))
        val repeated = candidate(rebuild)
        assertEquals(30.0, repeated.volume.weeks.single().weeklyKilometers, 0.00001)

        val incremental = candidate(rebuild.copy(
            explicitlyApprovedIncreaseKm = 2.0,
            increaseApprovalReference = "synthetic-reviewed-rebuild"
        ))
        assertEquals(32.0, incremental.volume.weeks.single().weeklyKilometers, 0.00001)
        assertTrue(incremental.volume.weeks.single().weeklyKilometers < 60.0)
    }

    @Test fun oneMissedBuildAndOneCompleteBuildNeedsExplicitReview() {
        reject("MIXED_OR_PARTIAL_COMPLETION_REVIEW", input(listOf(
            week(2, 60.0, 60.0), week(1, 60.0, 30.0)
        )))
        reject("MIXED_OR_PARTIAL_COMPLETION_REVIEW", input(listOf(
            week(2, 60.0, 50.0), week(1, 60.0, 50.0)
        )))
    }

    @Test fun unknownCompletionAndIntentNeverBecomeZeroOrRecovery() {
        reject("COMPLETE_WEEK_EVIDENCE_UNKNOWN", input(listOf(
            week(2, 60.0), week(1, 60.0, null)
        )))
        reject("COMPLETE_WEEK_EVIDENCE_UNKNOWN", input(listOf(
            week(2, 60.0), week(1, 60.0, 30.0, complete = false)
        )))
        reject("TRAINING_INTENT_UNKNOWN", input(listOf(
            week(2, 60.0),
            week(1, 40.0, 40.0, IndependentObservedWeekIntent.UNKNOWN)
        )))
    }

    @Test fun unreconciledRecoveryHistoryDefersInsteadOfRestoringLoad() {
        val recovery = week(
            1, 40.0, 40.0, IndependentObservedWeekIntent.PLANNED_RECOVERY, reviewed = true
        )
        reject("RECOVERY_INTENT_UNREVIEWED", input(listOf(
            week(2, 60.0), recovery.copy(intentExplicitlyReviewed = false)
        )))
        reject("PRE_RECOVERY_BUILD_INCOMPLETE", input(listOf(
            week(2, 60.0, 30.0), recovery
        )))
        reject("RECOVERY_OVER_TARGET_REVIEW", input(listOf(
            week(2, 60.0), recovery.copy(observedKilometers = 45.0)
        )))
    }

    @Test fun difficultOrUnknownResponseBlocksSyntheticProgression() {
        reject("DIFFICULT_RESPONSE_REVIEW", input(listOf(
            week(2, 60.0), week(1, 60.0, response = IndependentWeekResponse.DIFFICULT)
        )))
        reject("TOLERANCE_RESPONSE_UNKNOWN", input(listOf(
            week(2, 60.0), week(1, 60.0, response = IndependentWeekResponse.UNKNOWN)
        )))
    }

    @Test fun explicitZeroCompletionRequiresSeparateReturnToRunReview() {
        reject("RETURN_TO_RUN_REVIEW", input(listOf(
            week(2, 60.0, 0.0), week(1, 60.0, 0.0)
        )))
    }

    @Test fun staleHistoryOrMultiweekPredictionFailsClosed() {
        reject("STALE_COMPLETION_HISTORY", input(listOf(
            week(3, 60.0), week(2, 60.0)
        )))
        reject("NEXT_WEEK_ONLY", input(volume = request.copy(weeks = 3)))
    }

    @Test fun originalContradictoryApprovalCannotBeRepairedByReducingLoad() {
        reject("BASELINE_EXCEEDS_APPROVED_CEILING", input(
            history = listOf(week(2, 60.0, 30.0), week(1, 60.0, 30.0)),
            volume = request.copy(approvedWeeklyCeilingKm = 40.0)
        ))
    }

    @Test fun requestedLowerMileageRemainsRespected() {
        val out = candidate(input(volume = request.copy(requestedTargetWeeklyKm = 30.0),
            increase = 2.0, approval = "synthetic-approved"))
        assertEquals(30.0, out.volume.weeks.single().weeklyKilometers, 0.00001)
    }

    @Test fun noRealSourceEvidenceOrProductionAuthorizationIsInvented() {
        val original = input(increase = 2.0, approval = "synthetic")
        val a = IndependentMultiweekResponse.preview(original)
        val b = IndependentMultiweekResponse.preview(original)
        assertEquals(a, b)
        val out = a as IndependentMultiweekResponseDecision.Preview
        assertTrue(IndependentMileageFeasibility.check(out.volume, original.limits)
            is IndependentMileageFeasibilityResult.Checked)
        assertFalse(out.authorizedForProduction)
        assertEquals(60.0, original.volume.approvedStartingWeeklyKm!!, 0.00001)
    }
}
