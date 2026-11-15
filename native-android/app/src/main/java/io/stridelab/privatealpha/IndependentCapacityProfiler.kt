package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M3 Batch 2: provisional capacity evidence for a training-history review.
 *
 * An external caller's confirmedCompleteMondays is NOT proof of provider coverage.
 * This assessment is therefore NEVER a verified physiological capacity, a
 * quality-work authorization, or an executable plan input.
 */
internal enum class IndependentCapacityConfidence {
    UNAVAILABLE,
    SELF_REPORTED_ONLY,
    RECENT_TWO_WEEKS_ASSERTED,
    FOUR_WEEKS_ASSERTED,
    CONFLICT_REVIEW
}

internal enum class IndependentCapacityTrend {
    UNKNOWN,
    NO_MATERIAL_DECLINE,
    RECENT_DECLINE
}

/** Versioned candidate comparison thresholds, not evidence of safe training load. */
internal data class IndependentCapacityHeuristics(
    val materialDeclineFraction: Double = 0.10,
    val declarationDifferenceKm: Double = 5.0,
    val declarationDifferenceFraction: Double = 0.20
) {
    init {
        require(materialDeclineFraction.isFinite() && materialDeclineFraction in 0.0..1.0)
        require(declarationDifferenceKm.isFinite() && declarationDifferenceKm > 0.0)
        require(declarationDifferenceFraction.isFinite() && declarationDifferenceFraction in 0.0..1.0)
    }
}

internal data class IndependentCapacityAssessment(
    /** For research comparison only; never an automatically approved training load. */
    val proposedStartingKm: Double?,
    val completeWeekCount: Int,
    val observedFourWeekMeanKm: Double?,
    val observedRecentTwoWeekMeanKm: Double?,
    val selfReportedWeeklyKm: Double?,
    val trend: IndependentCapacityTrend,
    val confidence: IndependentCapacityConfidence,
    val ruleIds: List<String>,
    val provenance: String = "CALLER_ASSERTED_WEEK_COVERAGE",
    val policyEvidence: String = "HEURISTIC"
) {
    init {
        require(completeWeekCount in 0..4)
        require(listOfNotNull(proposedStartingKm, observedFourWeekMeanKm,
            observedRecentTwoWeekMeanKm, selfReportedWeeklyKm)
            .all { it.isFinite() && it >= 0.0 })
        require(provenance == "CALLER_ASSERTED_WEEK_COVERAGE")
        require(policyEvidence == "HEURISTIC")
        require(ruleIds.distinct() == ruleIds)
    }

    /** No caller assertions may be silently promoted to source-verified history. */
    val sourceCoverageVerified: Boolean get() = false
}

/**
 * Computes transparent comparison evidence from M3's previous-four-complete-weeks
 * summary. A verified zero week remains zero; missing coverage is never zero.
 *
 * Recent decline is compared with the PREVIOUS two completed weeks, not with
 * the already partially overlapping four-week average.
 *
 * A self-report is kept separate. Substantive disagreement raises a review
 * flag and the lower observation is used for hypothetical calculations only.
 */
internal object IndependentCapacityProfiler {
    const val POLICY_ID = "stridelab.engine15.m3-capacity-comparison-v1"

    fun assess(
        history: IndependentRunningHistoryEvidence,
        declaredWeeklyKm: Double? = null,
        policy: IndependentCapacityHeuristics = IndependentCapacityHeuristics()
    ): IndependentCapacityAssessment {
        require(declaredWeeklyKm == null ||
            declaredWeeklyKm.isFinite() && declaredWeeklyKm >= 0.0)

        val ids = mutableListOf<String>()
        val completeCount = history.completeWeekCount
        val four = history.confirmedFourWeekMeanKm
        val recent = history.confirmedRecentTwoWeekMeanKm
        val earlierTwo = history.completedWeeks.take(2)
            .map { it.confirmedWeeklyKm }
            .takeIf { weeks -> weeks.all { it != null } }
            ?.filterNotNull()?.average()

        if (completeCount != 4) ids += "INCOMPLETE_FOUR_WEEK_COVERAGE"
        if (four != null) ids += "FOUR_WEEK_COVERAGE_ASSERTED"
        if (recent != null) ids += "RECENT_TWO_WEEK_COVERAGE_ASSERTED"

        val decline = earlierTwo != null && recent != null && earlierTwo > 0.0 &&
            recent < earlierTwo * (1.0 - policy.materialDeclineFraction)
        val trend = when {
            earlierTwo == null || recent == null -> IndependentCapacityTrend.UNKNOWN
            decline -> IndependentCapacityTrend.RECENT_DECLINE
            else -> IndependentCapacityTrend.NO_MATERIAL_DECLINE
        }
        if (decline) ids += "MATERIAL_RECENT_DECLINE"

        var observed = when {
            four != null && recent != null && decline -> recent
            four != null -> four
            recent != null -> recent
            else -> null
        }
        if (observed != null) ids += "OBSERVED_PROVISIONAL_BASELINE"

        var confidence = when {
            four != null -> IndependentCapacityConfidence.FOUR_WEEKS_ASSERTED
            recent != null -> IndependentCapacityConfidence.RECENT_TWO_WEEKS_ASSERTED
            declaredWeeklyKm != null -> IndependentCapacityConfidence.SELF_REPORTED_ONLY
            else -> IndependentCapacityConfidence.UNAVAILABLE
        }

        if (declaredWeeklyKm != null) {
            ids += "SELF_REPORT_SEPARATE"
            if (observed == null) {
                observed = declaredWeeklyKm
                ids += "SELF_REPORT_FALLBACK"
            } else {
                val difference = abs(declaredWeeklyKm - observed)
                val meaningful = difference >= policy.declarationDifferenceKm ||
                    (observed > 0.0 && difference / observed >= policy.declarationDifferenceFraction)
                if (meaningful) {
                    ids += "DECLARATION_DISAGREEMENT_REVIEW"
                    confidence = IndependentCapacityConfidence.CONFLICT_REVIEW
                }
                if (declaredWeeklyKm < observed) {
                    observed = declaredWeeklyKm
                    ids += "CONSERVATIVE_DECLARED_LOWER"
                }
            }
        }

        if (observed == null) ids += "NO_USABLE_BASELINE"
        ids += "SOURCE_COVERAGE_UNVERIFIED"
        return IndependentCapacityAssessment(
            proposedStartingKm = observed,
            completeWeekCount = completeCount,
            observedFourWeekMeanKm = four,
            observedRecentTwoWeekMeanKm = recent,
            selfReportedWeeklyKm = declaredWeeklyKm,
            trend = trend,
            confidence = confidence,
            ruleIds = ids
        )
    }
}
