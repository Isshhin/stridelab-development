package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Research-only intent annotations for completed weeks.
 *
 * These labels, completion totals, and tolerance reports are CALLER ASSERTIONS,
 * not authenticated source evidence or a physiological readiness estimate.
 */
internal enum class IndependentObservedWeekIntent {
    BUILD,
    PLANNED_RECOVERY,
    UNKNOWN
}

internal data class IndependentObservedTrainingWeek(
    val monday: LocalDate,
    val plannedKilometers: Double,
    val observedKilometers: Double?,
    val coverageAssertedComplete: Boolean,
    val intent: IndependentObservedWeekIntent,
    val intentExplicitlyReviewed: Boolean,
    val response: IndependentWeekResponse
) {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        require(plannedKilometers.isFinite() && plannedKilometers > 0.0 &&
            plannedKilometers <= 1000.0)
        require(observedKilometers == null ||
            observedKilometers.isFinite() && observedKilometers in 0.0..1000.0)
    }
}

internal data class IndependentMultiweekResponseInput(
    val volume: IndependentWeeklyMileageInput,
    val limits: IndependentMileageFeasibilityLimits,
    val planningMonday: LocalDate,
    val completedWeeks: List<IndependentObservedTrainingWeek>,
    /** An EXTERNAL approval, not inferred from workout history. */
    val explicitlyApprovedIncreaseKm: Double = 0.0,
    val increaseApprovalReference: String? = null
) {
    init {
        require(planningMonday.dayOfWeek == DayOfWeek.MONDAY)
        require(completedWeeks.size in 2..8)
        require(completedWeeks.zipWithNext().all { (a, b) ->
            a.monday.plusWeeks(1) == b.monday
        })
        require(explicitlyApprovedIncreaseKm.isFinite() &&
            explicitlyApprovedIncreaseKm in 0.0..50.0)
        require(increaseApprovalReference == null ||
            increaseApprovalReference.length <= 256)
    }
}

internal sealed interface IndependentMultiweekResponseDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentMultiweekResponseDecision

    data class Preview(
        val volume: IndependentWeeklyMileageDecision.Candidate,
        val comparedBuildWeeks: Int,
        val reason: String,
        val unresolvedRuleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentMultiweekResponseDecision {
        init {
            require(volume.weeks.size == 1)
            require(comparedBuildWeeks in 1..2)
            require(reason.isNotBlank() && unresolvedRuleIds.isNotEmpty())
            require(unresolvedRuleIds.distinct() == unresolvedRuleIds)
            require(!sourceEvidenceVerified && !authorizedForProduction)
        }
    }
}

/**
 * Responds to a sequence of *complete* synthetic weeks.
 *
 * - Deliberate recovery is not equivalent to an unplanned missed week.
 * - Two consecutive markedly undercompleted BUILD weeks propose a lower
 *   repeat-only volume; mixed/partial responses require explicit review.
 * - Two confirmed, tolerated, completed BUILD weeks can suggest an increase
 *   ONLY when a separate upstream increment and reference are supplied.
 * - After reviewed recovery, resume no higher than the last completed
 *   pre-recovery BUILD volume; no automatic extra increment.
 *
 * The 95% and 75% completion cutoffs are exploratory classification
 * heuristics, not safety thresholds. Every candidate is nonexecutable.
 */
internal object IndependentMultiweekResponse {
    const val POLICY_ID = "stridelab.engine15.multiweek-research-v1"
    private const val COMPLETE_RATIO = 0.95
    private const val MARKEDLY_MISSED_RATIO = 0.75
    private const val EXCESS_RATIO = 1.05

    private fun defer(id: String, why: String) =
        IndependentMultiweekResponseDecision.Deferred(id, why)

    fun preview(input: IndependentMultiweekResponseInput): IndependentMultiweekResponseDecision {
        // Reject contradictory original approvals BEFORE adapting the starting
        // volume. Lowering an input cannot retroactively validate bad approvals.
        when (val original = IndependentWeeklyMileagePlanner.generate(input.volume)) {
            is IndependentWeeklyMileageDecision.Deferred ->
                return defer(original.ruleId, original.explanation)
            is IndependentWeeklyMileageDecision.Candidate -> Unit
        }
        if (input.volume.weeks != 1)
            return defer("NEXT_WEEK_ONLY", "No future completion evidence exists for multiweek auto-projection.")
        if (input.completedWeeks.last().monday != input.planningMonday.minusWeeks(1))
            return defer("STALE_COMPLETION_HISTORY", "Most recent observed week must directly precede planning Monday.")
        if (input.completedWeeks.any { !it.coverageAssertedComplete ||
                it.observedKilometers == null })
            return defer("COMPLETE_WEEK_EVIDENCE_UNKNOWN",
                "Missing or partial weeks must not be treated as zero completed kilometers.")
        if (input.completedWeeks.any { it.intent == IndependentObservedWeekIntent.UNKNOWN })
            return defer("TRAINING_INTENT_UNKNOWN",
                "A reduced load cannot be classified without an explicit build/recovery intent.")
        if (input.completedWeeks.any { it.response == IndependentWeekResponse.DIFFICULT })
            return defer("DIFFICULT_RESPONSE_REVIEW",
                "Difficult training needs separate recovery review, not an automatic reduction.")
        if (input.completedWeeks.any { it.response == IndependentWeekResponse.UNKNOWN })
            return defer("TOLERANCE_RESPONSE_UNKNOWN",
                "Completed kilometers do not establish workout tolerance.")
        if (input.completedWeeks.any {
                requireNotNull(it.observedKilometers) >
                    it.plannedKilometers * EXCESS_RATIO + 0.001
            })
            return defer("UNREVIEWED_OVER_COMPLETION",
                "Above-plan running requires review before treating this as a progression signal.")
        if (input.explicitlyApprovedIncreaseKm > 0.0 &&
            input.increaseApprovalReference.isNullOrBlank())
            return defer("INCREMENT_APPROVAL_REFERENCE_MISSING",
                "Do not convert a numeric increase without explicit external review.")

        val last = input.completedWeeks.last()
        val proposed: Double
        val reason: String
        val compared: Int

        if (last.intent == IndependentObservedWeekIntent.PLANNED_RECOVERY) {
            if (!last.intentExplicitlyReviewed)
                return defer("RECOVERY_INTENT_UNREVIEWED",
                    "A claimed recovery week needs an explicit original planning decision.")
            val prior = input.completedWeeks.dropLast(1).lastOrNull {
                it.intent == IndependentObservedWeekIntent.BUILD
            } ?: return defer("PRE_RECOVERY_BUILD_MISSING",
                "Cannot determine a pre-recovery reference from recovery weeks alone.")
            if (last.plannedKilometers >= prior.plannedKilometers)
                return defer("RECOVERY_NOT_A_REDUCTION",
                    "The asserted recovery week was not planned below its prior build week.")
            val completedRecovery = requireNotNull(last.observedKilometers)
            if (completedRecovery > last.plannedKilometers + 0.01)
                return defer("RECOVERY_OVER_TARGET_REVIEW",
                    "Recovery week exceeded its declared reduced training load.")
            if (completedRecovery < last.plannedKilometers * MARKEDLY_MISSED_RATIO)
                return defer("RECOVERY_NOT_COMPLETED_REVIEW",
                    "A substantially missed recovery plan needs a separate rebuilding decision.")
            if (requireNotNull(prior.observedKilometers) <
                prior.plannedKilometers * COMPLETE_RATIO)
                return defer("PRE_RECOVERY_BUILD_INCOMPLETE",
                    "A missed build week cannot be restored solely by labeling the next week recovery.")
            proposed = minOf(requireNotNull(input.volume.approvedStartingWeeklyKm),
                prior.plannedKilometers, requireNotNull(prior.observedKilometers))
            reason = "REVIEWED_RECOVERY_RESUME_PRIOR_BUILD_NO_CATCHUP"
            compared = 1
        } else {
            val recent = input.completedWeeks.takeLast(2)
            if (recent.size != 2 ||
                recent.any { it.intent != IndependentObservedWeekIntent.BUILD })
                return defer("MIXED_INTENT_REBUILD_REVIEW",
                    "A recovery/build transition needs an explicit rebuilding decision.")
            val ratios = recent.map {
                requireNotNull(it.observedKilometers) / it.plannedKilometers
            }
            if (ratios.all { it >= COMPLETE_RATIO }) {
                // The original approved start is a ceiling, not evidence that
                // a reduced-build athlete instantly regained that volume.
                // Anchor the next week to both actual and prescribed recent
                // builds, and permit one separately approved increment.
                val demonstrated = minOf(
                    recent.minOf { it.plannedKilometers },
                    recent.minOf { requireNotNull(it.observedKilometers) },
                    requireNotNull(input.volume.approvedStartingWeeklyKm)
                )
                proposed = minOf(
                    demonstrated + input.explicitlyApprovedIncreaseKm,
                    requireNotNull(input.volume.approvedWeeklyCeilingKm),
                    input.volume.requestedTargetWeeklyKm
                        ?: requireNotNull(input.volume.approvedStartingWeeklyKm)
                )
                reason = if (input.explicitlyApprovedIncreaseKm > 0.0)
                    "TWO_COMPLETED_BUILD_WEEKS_WITH_APPROVED_INCREMENT"
                else "REPEAT_APPROVED_BUILD_WITHOUT_INCREMENT"
                compared = 2
            } else if (ratios.all { it < MARKEDLY_MISSED_RATIO }) {
                val repeated = recent.minOf { requireNotNull(it.observedKilometers) }
                if (repeated <= 0.0)
                    return defer("RETURN_TO_RUN_REVIEW",
                        "Zero completed mileage needs a separate return-to-run decision.")
                proposed = minOf(requireNotNull(input.volume.approvedStartingWeeklyKm), repeated)
                reason = "REPEATED_UNDERCOMPLETION_REDUCED_REPEAT_ONLY"
                compared = 2
            } else {
                return defer("MIXED_OR_PARTIAL_COMPLETION_REVIEW",
                    "Do not infer a repeatable load from one missed or partially completed week.")
            }
        }

        if (proposed <= 0.0)
            return defer("RETURN_TO_RUN_REVIEW", "Zero proposal is not an executable running week.")
        val bounded = input.volume.copy(approvedStartingWeeklyKm = proposed)
        // Does not bypass the original gate; both caller claims and the
        // final distribution must satisfy the existing M4 limits.
        return when (val result = IndependentAdaptiveWeek.allocate(bounded, input.limits)) {
            is IndependentWeeklyMileageDecision.Deferred ->
                defer(result.ruleId, result.explanation)
            is IndependentWeeklyMileageDecision.Candidate ->
                IndependentMultiweekResponseDecision.Preview(
                    volume = result,
                    comparedBuildWeeks = compared,
                    reason = reason,
                    unresolvedRuleIds = listOf(
                        "INTENT_AND_TOLERANCE_CALLER_ASSERTED",
                        "SOURCE_COVERAGE_NOT_AUTHENTICATED",
                        "COMPLETION_HEURISTICS_NOT_SAFETY_VALIDATED",
                        "APPROVED_LIMITS_CALLER_ASSERTED",
                        "NO_PRODUCTION_PLAN_MUTATION"
                    )
                )
        }
    }
}
