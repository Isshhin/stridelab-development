package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs

/**
 * M8a synthetic-only comparison. M7b observes some linked source metrics but
 * cannot authenticate owner/source completeness or physiological tolerance.
 * Nothing in this component can mutate an Android plan or authorize training.
 */
internal data class IndependentLongitudinalShadowInput(
    val planId: String,
    val longitudinal: IndependentMultiweekResponseInput,
    val ledger: IndependentWeeklyEvidenceLedgerDecision
) {
    init { require(planId.isNotBlank() && planId.length <= 256) }
}

internal sealed interface IndependentLongitudinalShadowDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentLongitudinalShadowDecision

    data class Comparison(
        val planId: String,
        val planningMonday: LocalDate,
        val m6aBaselineKm: Double,
        val assertedResponseKm: Double,
        val deltaKm: Double,
        val responseReason: String,
        val partialLinkedKmByWeek: Map<LocalDate, Double>,
        val unresolvedRuleIds: List<String>,
        val evidenceVerified: Boolean = false,
        val authorizedForPlanMutation: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentLongitudinalShadowDecision {
        init {
            require(planId.isNotBlank())
            require(m6aBaselineKm.isFinite() && m6aBaselineKm > 0)
            require(assertedResponseKm.isFinite() && assertedResponseKm > 0)
            require(abs(deltaKm - (assertedResponseKm - m6aBaselineKm)) < 0.00001)
            require(responseReason.isNotBlank())
            require(unresolvedRuleIds.distinct().size == unresolvedRuleIds.size)
            require(!evidenceVerified && !authorizedForPlanMutation && !authorizedForProduction)
        }
    }
}

internal object IndependentLongitudinalShadow {
    const val POLICY_ID = "stridelab.engine15.m8a-longitudinal-shadow-v1"

    private fun defer(id: String, why: String) =
        IndependentLongitudinalShadowDecision.Deferred(id, why)

    fun compare(input: IndependentLongitudinalShadowInput): IndependentLongitudinalShadowDecision {
        val audit = when (val result = input.ledger) {
            is IndependentWeeklyEvidenceLedgerDecision.Deferred ->
                return defer("M7_LEDGER_UNAVAILABLE", result.ruleId + ": " + result.explanation)
            is IndependentWeeklyEvidenceLedgerDecision.Candidate -> result
        }
        if (audit.planId != input.planId)
            return defer("LEDGER_PLAN_MISMATCH", "A plan cannot reuse another plan's evidence ledger.")
        val history = input.longitudinal
        if (history.volume.weeks != 1)
            return defer("NEXT_WEEK_ONLY", "Future completed training must not be invented.")
        if (history.completedWeeks.last().monday != history.planningMonday.minusWeeks(1))
            return defer("STALE_COMPLETION_HISTORY", "Evidence must precede planning week directly.")
        val recent = history.completedWeeks.takeLast(2)
        val indexed = audit.weeks.associateBy { it.monday }
        if (recent.any { it.monday !in indexed })
            return defer("M7_RECENT_WEEK_NOT_AUDITED", "Both recent weeks require source-link review rows.")
        if (recent.any {
                it.observedKilometers != null &&
                    indexed.getValue(it.monday).observedLinkedKmPartial > it.observedKilometers + 0.01
            })
            return defer("LINKED_DISTANCE_EXCEEDS_ASSERTED_TOTAL",
                "Plan-linked source kilometers exceed the claimed full-week sum.")

        // The comparator is M6a, not the current Android Engine 1.5.
        val baseline = when (val result = IndependentConstrainedMultiweekPlanner.generate(
            history.volume, history.limits
        )) {
            is IndependentConstrainedMultiweekDecision.Deferred ->
                return defer("M6A_BASELINE_" + result.ruleId, result.explanation)
            is IndependentConstrainedMultiweekDecision.Candidate -> result
        }
        val response = when (val result = IndependentMultiweekResponse.preview(history)) {
            is IndependentMultiweekResponseDecision.Deferred ->
                return defer("RESPONSE_" + result.ruleId, result.explanation)
            is IndependentMultiweekResponseDecision.Preview -> result
        }
        val firstKm = baseline.mileage.weeks.single().weeklyKilometers
        val secondKm = response.volume.weeks.single().weeklyKilometers
        val gaps = recent.any { monday ->
            indexed.getValue(monday.monday).deferredOrUnlinkedSessionCount > 0 ||
                indexed.getValue(monday.monday).attributedButMissingDistanceCount > 0
        }
        return IndependentLongitudinalShadowDecision.Comparison(
            planId = input.planId,
            planningMonday = history.planningMonday,
            m6aBaselineKm = firstKm,
            assertedResponseKm = secondKm,
            deltaKm = secondKm - firstKm,
            responseReason = response.reason,
            partialLinkedKmByWeek = recent.associate {
                it.monday to indexed.getValue(it.monday).observedLinkedKmPartial
            },
            unresolvedRuleIds = (listOf(
                "SYNTHETIC_REPLAY_NOT_TRAINING_ADVICE",
                "M7_OWNER_AND_SOURCE_COVERAGE_UNAUTHENTICATED",
                "M7_PARTIAL_LINKED_KM_NOT_COMPLETE_WEEK",
                "TOLERANCE_AND_TRAINING_INTENT_CALLER_ASSERTED",
                "NO_PRODUCTION_PLAN_MUTATION"
            ) + (if (gaps) listOf("M7_PARTIAL_OR_DISPUTED_SESSION_LINKS")
                else emptyList()) + response.unresolvedRuleIds).distinct()
        )
    }

    /** M7b cannot currently prove full source-week coverage, even if linked. */
    fun productionAdmission(
        decision: IndependentLongitudinalShadowDecision
    ): IndependentLongitudinalShadowDecision.Deferred = when (decision) {
        is IndependentLongitudinalShadowDecision.Deferred ->
            defer("SHADOW_ONLY_DEFERRED", decision.ruleId + ": " + decision.explanation)
        is IndependentLongitudinalShadowDecision.Comparison ->
            defer("M7_SOURCE_AND_COVERAGE_NOT_AUTHENTICATED",
                "A complete source history and athlete response have not been verified.")
    }
}
