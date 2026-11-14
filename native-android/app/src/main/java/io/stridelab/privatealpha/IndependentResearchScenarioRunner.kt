package io.stridelab.privatealpha

/**
 * M5a integration gate: deterministic, synthetic-only orchestration of the
 * existing M4 -> M5a preview stages. This is a RESEARCH fixture runner, not
 * an Android-facing generator or a route for live athlete records.
 *
 * Earlier M3 claims of source coverage and segment classification remain
 * unverified. Caller approvals of limits/dose are NOT provenance attestations.
 * A completed pipeline means "structurally assembled for review", NEVER
 * "safe or executable as a real athlete's training plan".
 */
internal data class IndependentResearchScenario(
    val name: String,
    val volume: IndependentWeeklyMileageInput,
    val feasibility: IndependentMileageFeasibilityLimits,
    val qualityRequests: List<IndependentQualityDayRequest> = emptyList(),
    val stridesRequests: List<IndependentStridesDayRequest> = emptyList(),
    val volumePolicy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
) {
    init {
        require(name.isNotBlank() && name.length <= 120)
    }
}

internal enum class IndependentResearchGate {
    WEEKLY_VOLUME, BASE_WORKOUTS, REVIEWED_OVERLAYS
}

internal enum class IndependentResearchReconciliation {
    BASE_DISTANCE_ONLY,
    TIMED_OVERLAY_DISTANCE_UNKNOWN
}

internal sealed interface IndependentResearchScenarioResult {
    data class Deferred(
        val scenarioName: String,
        val stage: IndependentResearchGate,
        val ruleId: String,
        val explanation: String
    ) : IndependentResearchScenarioResult

    data class Preview(
        val scenarioName: String,
        val mileage: IndependentWeeklyMileageDecision.Candidate,
        val base: IndependentRunWeekCompositionDecision.Candidate,
        val overlays: IndependentQualityOverlayDecision.Candidate?,
        val reconciliation: IndependentResearchReconciliation,
        val unresolvedRuleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentResearchScenarioResult {
        init {
            require(scenarioName.isNotBlank())
            require(mileage.weeks.size == base.weeks.size)
            require(mileage.weeks.map { it.week } == base.weeks.map { it.week })
            require(overlays == null || overlays.baseWeeks == base.weeks)
            require(unresolvedRuleIds.distinct() == unresolvedRuleIds)
            require(unresolvedRuleIds.isNotEmpty())
            require(!sourceEvidenceVerified && !authorizedForProduction)
            require(reconciliation == if (overlays == null)
                IndependentResearchReconciliation.BASE_DISTANCE_ONLY
            else IndependentResearchReconciliation.TIMED_OVERLAY_DISTANCE_UNKNOWN)
        }

        val baseSessionCount: Int get() = base.weeks.sumOf { it.sessions.size }
        val reviewedQualityPreviewCount: Int get() = overlays?.qualityPreviews?.size ?: 0
        val reviewedStridesPreviewCount: Int get() = overlays?.stridesPreviews?.size ?: 0

        /** M4 base distance, NOT the distance of timed alternatives. */
        val baseWeeklyDistancesKm: List<Double>
            get() = mileage.weeks.map { it.weeklyKilometers }

        val timedOverlayDistanceKnown: Boolean get() = false
    }
}

/**
 * Fail closed at the earliest stage. Do not promote the last successful stage
 * to an executable plan if a later stage defers or conflicts.
 */
internal object IndependentResearchScenarioRunner {
    const val POLICY_ID = "stridelab.engine15.m5a-synthetic-pipeline-v1"

    fun run(scenario: IndependentResearchScenario): IndependentResearchScenarioResult {
        fun defer(stage: IndependentResearchGate, rule: String, reason: String) =
            IndependentResearchScenarioResult.Deferred(
                scenario.name, stage, rule, reason
            )

        val mileage = when (val result =
            IndependentWeeklyMileagePlanner.generate(
                scenario.volume, scenario.volumePolicy
            )) {
            is IndependentWeeklyMileageDecision.Deferred ->
                return defer(IndependentResearchGate.WEEKLY_VOLUME,
                    result.ruleId, result.explanation)
            is IndependentWeeklyMileageDecision.Candidate -> result
        }

        val base = when (val result =
            IndependentRunWeekComposer.generate(
                mileage, scenario.feasibility, scenario.volumePolicy
            )) {
            is IndependentRunWeekCompositionDecision.Deferred ->
                return defer(IndependentResearchGate.BASE_WORKOUTS,
                    result.ruleId, result.explanation)
            is IndependentRunWeekCompositionDecision.Candidate -> result
        }

        val wantsOverlay = scenario.qualityRequests.isNotEmpty() ||
            scenario.stridesRequests.isNotEmpty()

        val overlays = if (wantsOverlay) {
            when (val result = IndependentQualityOverlayComposer.generate(
                allocation = mileage,
                limits = scenario.feasibility,
                qualityRequests = scenario.qualityRequests,
                stridesRequests = scenario.stridesRequests,
                policy = scenario.volumePolicy
            )) {
                is IndependentQualityOverlayDecision.Deferred ->
                    return defer(IndependentResearchGate.REVIEWED_OVERLAYS,
                        result.ruleId, result.explanation)
                is IndependentQualityOverlayDecision.Candidate -> result
            }
        } else null

        // Neither M3 coverage nor original-device intensity authentication
        // is available. Even without overlays, a candidate has not met
        // verified athlete evidence or clinical safety requirements.
        val unresolved = mutableListOf(
            "M3_SOURCE_COVERAGE_UNVERIFIED",
            "M3_INTENSITY_CLASSIFICATION_UNVERIFIED",
            "APPROVED_LIMITS_CALLER_ASSERTED",
            "HEURISTIC_PROGRESSION_NOT_SAFETY_VALIDATED",
            "NO_PRODUCTION_ACTIVATION"
        )
        if (overlays != null) unresolved +=
            "TIMED_OVERLAY_KILOMETERS_UNRECONCILED"

        return IndependentResearchScenarioResult.Preview(
            scenarioName = scenario.name,
            mileage = mileage,
            base = base,
            overlays = overlays,
            reconciliation = if (overlays == null)
                IndependentResearchReconciliation.BASE_DISTANCE_ONLY
            else IndependentResearchReconciliation.TIMED_OVERLAY_DISTANCE_UNKNOWN,
            unresolvedRuleIds = unresolved
        )
    }
}
