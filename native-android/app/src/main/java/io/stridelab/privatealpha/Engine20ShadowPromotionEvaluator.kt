package io.stridelab.privatealpha

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sqrt

internal enum class ModelPromotionVerdict {
    ACTIVE_PERSONALIZED,
    SHADOW_CALIBRATING,
    DEMOTED_FALLBACK_TO_TIER_A
}

internal data class DistributionalHoldoutPoint(
    val observed: Double,
    val predictedMean: Double,
    val predictedSd: Double,
    val tierABaselinePrediction: Double
) {
    init {
        require(observed.isFinite() && predictedMean.isFinite() && predictedSd.isFinite() && predictedSd > 0.0 && tierABaselinePrediction.isFinite())
    }
}

internal data class ShadowGateReport(
    val modelId: String,
    val verdict: ModelPromotionVerdict,
    val sampleCount: Int,
    val maeModel: Double?,
    val maeTierA: Double?,
    val crps: Double?,
    val nll: Double?,
    val coverage90: Double?,
    val overestimationTailRate: Double?,
    val tierAOverestimationTailRate: Double?,
    val reason: String
)

/**
 * Track 8 Addendum §11 Model-Specific Shadow-Model Promotion & Non-Inferiority Gate Harness.
 *
 * - Distributional models (`MOD-B1` CS/D', `E2-03` Bayesian e1RM): exact Gaussian CRPS + NLL + 90%
 *   empirical coverage + safety tail non-inferiority (`upper tail <= Tier A + delta_safe`).
 * - Deterministic decision models (`MOD-B4` 0.5 fractional sets): decision-usefulness overload comparison
 *   without misapplying CRPS/NLL (`Track-8-refinement.md` §11).
 */
internal object Engine20ShadowPromotionEvaluator {

    fun evaluateDistributionalModel(
        modelId: String,
        points: List<DistributionalHoldoutPoint>
    ): ShadowGateReport {
        if (points.size < Engine20AdaptivePolicyRegistry.PROMOTION_MIN_HELDOUT_SAMPLES) {
            return ShadowGateReport(
                modelId = modelId,
                verdict = ModelPromotionVerdict.SHADOW_CALIBRATING,
                sampleCount = points.size,
                maeModel = null,
                maeTierA = null,
                crps = null,
                nll = null,
                coverage90 = null,
                overestimationTailRate = null,
                tierAOverestimationTailRate = null,
                reason = "SHADOW_CALIBRATING: ${points.size} held-out anchor(s) collected; need >= ${Engine20AdaptivePolicyRegistry.PROMOTION_MIN_HELDOUT_SAMPLES} before promotion."
            )
        }

        val maeModel = points.map { abs(it.observed - it.predictedMean) }.average()
        val maeTierA = points.map { abs(it.observed - it.tierABaselinePrediction) }.average()
        val crps = points.map { gaussianCrps(it.observed, it.predictedMean, it.predictedSd) }.average()
        val nll = points.map { gaussianNll(it.observed, it.predictedMean, it.predictedSd) }.average()
        val coverage90 = points.count { abs(it.observed - it.predictedMean) <= 1.64485 * it.predictedSd }.toDouble() / points.size

        // Optimistic overestimation (> 5% above observed actual) threatens over-prescription / injury
        val modelOverRate = points.count { it.predictedMean > it.observed * 1.05 }.toDouble() / points.size
        val tierAOverRate = points.count { it.tierABaselinePrediction > it.observed * 1.05 }.toDouble() / points.size
        val safetyTailOk = modelOverRate <= tierAOverRate + Engine20AdaptivePolicyRegistry.PROMOTION_NON_INFERIORITY_DELTA_SAFE

        val coverageOk = coverage90 in Engine20AdaptivePolicyRegistry.PROMOTION_MIN_COVERAGE_90..Engine20AdaptivePolicyRegistry.PROMOTION_MAX_COVERAGE_90
        val beatsTierA = maeModel < maeTierA

        val (verdict, reason) = when {
            !safetyTailOk -> ModelPromotionVerdict.DEMOTED_FALLBACK_TO_TIER_A to
                "DEMOTED_SAFETY_NON_INFERIORITY: Optimistic overestimation rate (${(modelOverRate * 100).toInt()}%) exceeds Tier A margin."
            !coverageOk -> ModelPromotionVerdict.DEMOTED_FALLBACK_TO_TIER_A to
                "DEMOTED_MISCALIBRATED_COVERAGE: Empirical 90% coverage (${(coverage90 * 100).toInt()}%) is outside acceptable [80%, 98%] band."
            !beatsTierA -> ModelPromotionVerdict.SHADOW_CALIBRATING to
                "NO_PRACTICAL_EDGE_OVER_TIER_A: Model MAE ($maeModel) does not beat Tier A ($maeTierA); retaining Tier A."
            else -> ModelPromotionVerdict.ACTIVE_PERSONALIZED to
                "PROMOTED_ACTIVE: Beats Tier A MAE ($maeModel < $maeTierA) with ${(coverage90 * 100).toInt()}% coverage and safe tail rate."
        }

        return ShadowGateReport(
            modelId = modelId,
            verdict = verdict,
            sampleCount = points.size,
            maeModel = maeModel,
            maeTierA = maeTierA,
            crps = crps,
            nll = nll,
            coverage90 = coverage90,
            overestimationTailRate = modelOverRate,
            tierAOverestimationTailRate = tierAOverRate,
            reason = reason
        )
    }

    /**
     * Exact deterministic decision-usefulness gate for non-distributional `MOD-B4` (`CRPS/NLL = N/A`).
     */
    fun evaluateDeterministicDecisionUsefulness(
        modelId: String,
        modelOverloadOrStallIncidents: Int,
        tierAOverloadOrStallIncidents: Int,
        weeksEvaluated: Int
    ): ShadowGateReport {
        if (weeksEvaluated < Engine20AdaptivePolicyRegistry.PROMOTION_MIN_HELDOUT_SAMPLES) {
            return ShadowGateReport(
                modelId = modelId,
                verdict = ModelPromotionVerdict.SHADOW_CALIBRATING,
                sampleCount = weeksEvaluated,
                maeModel = null,
                maeTierA = null,
                crps = null,
                nll = null,
                coverage90 = null,
                overestimationTailRate = null,
                tierAOverestimationTailRate = null,
                reason = "SHADOW_CALIBRATING: Need >= ${Engine20AdaptivePolicyRegistry.PROMOTION_MIN_HELDOUT_SAMPLES} evaluated training weeks."
            )
        }
        val (verdict, reason) = if (modelOverloadOrStallIncidents <= tierAOverloadOrStallIncidents) {
            ModelPromotionVerdict.ACTIVE_PERSONALIZED to
                "PROMOTED_ACTIVE: Reduced or matched local overload/stall incidence ($modelOverloadOrStallIncidents <= $tierAOverloadOrStallIncidents)."
        } else {
            ModelPromotionVerdict.DEMOTED_FALLBACK_TO_TIER_A to
                "DEMOTED_INFERIOR_DECISION_USEFULNESS: Fractional accounting showed higher overload incidents ($modelOverloadOrStallIncidents > $tierAOverloadOrStallIncidents); reverting to separate Direct/Indirect ledgers."
        }
        return ShadowGateReport(
            modelId = modelId,
            verdict = verdict,
            sampleCount = weeksEvaluated,
            maeModel = null,
            maeTierA = null,
            crps = null,
            nll = null,
            coverage90 = null,
            overestimationTailRate = null,
            tierAOverestimationTailRate = null,
            reason = reason
        )
    }

    /** Exact closed-form Continuous Ranked Probability Score for Normal(mean, sd^2). */
    fun gaussianCrps(observed: Double, mean: Double, sd: Double): Double {
        val z = (observed - mean) / sd
        val phi = exp(-0.5 * z * z) / sqrt(2.0 * PI)
        val bigPhi = 0.5 * (1.0 + erfApprox(z / sqrt(2.0)))
        return sd * (z * (2.0 * bigPhi - 1.0) + 2.0 * phi - 1.0 / sqrt(PI))
    }

    /** Exact Negative Log-Likelihood for Normal(mean, sd^2). */
    fun gaussianNll(observed: Double, mean: Double, sd: Double): Double {
        val z = (observed - mean) / sd
        return 0.5 * ln(2.0 * PI * sd * sd) + 0.5 * z * z
    }

    /** Abramowitz and Stegun 7.1.26 rational approximation (max error < 1.5e-7). */
    private fun erfApprox(x: Double): Double {
        val s = sign(x)
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val poly = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
        return s * (1.0 - poly * exp(-ax * ax))
    }
}
