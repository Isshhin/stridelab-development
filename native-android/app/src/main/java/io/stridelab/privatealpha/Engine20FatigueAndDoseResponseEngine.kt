package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class FatigueStateKind {
    FRESH_ADAPTING,
    FUNCTIONAL_OVERLOAD,
    NON_FUNCTIONAL_FATIGUE_RISK
}

internal data class FatigueFilterInput(
    val subjectiveFatigue1To5: Int? = null,
    val subjectiveSoreness1To5: Int? = null,
    val executionDriftAdverse: Boolean? = null,
    val hrvEvaluation: HrvEvaluationResult? = null,
    val activeHealthHoldPresent: Boolean = false
)

internal data class FatiguePosteriorState(
    val probFresh: Double,
    val probFunctionalOverload: Double,
    val probNonFunctionalRisk: Double,
    val normalizedEntropy: Double,
    val abstainedInsufficientSignals: Boolean,
    val overrideBlockedByHealthHold: Boolean,
    val explanation: String
) {
    val dominantState: FatigueStateKind
        get() = when {
            probNonFunctionalRisk >= probFunctionalOverload && probNonFunctionalRisk >= probFresh -> FatigueStateKind.NON_FUNCTIONAL_FATIGUE_RISK
            probFunctionalOverload >= probFresh -> FatigueStateKind.FUNCTIONAL_OVERLOAD
            else -> FatigueStateKind.FRESH_ADAPTING
        }
}

internal data class DailyImpulseRecord(
    val date: LocalDate,
    val trainingImpulseLoad: Double,
    val performanceAnchorScore: Double? = null
)

internal data class IndividualKineticsProfile(
    val identifiable: Boolean,
    val tau1FitnessDays: Double,
    val tau2FatigueDays: Double,
    val recommendedTaperDays: Int,
    val currentFitnessState: Double?,
    val currentFatigueState: Double?,
    val netPreparednessDelta: Double?,
    val anchorCount: Int,
    val spanDays: Int,
    val fallbackReason: String? = null
)

/**
 * Engine 2.0 3-State Probabilistic Fatigue Filter (`E2-05`) & Identifiability-Gated
 * Dose-Response Kinetics (`MOD-C1`, `Busso 2003`, `Hellard 2006`).
 */
internal object Engine20FatigueAndDoseResponseEngine {

    /**
     * Transparent Bayes filter over {FRESH_ADAPTING, FUNCTIONAL_OVERLOAD, NON_FUNCTIONAL_FATIGUE_RISK}.
     * NEVER outputs an opaque 0-100 composite readiness score (`EXCL-D`), NEVER overrides
     * active health holds (`H1-H3`), and treats missing HRV safely (`Saw 2016`; `MOD-21`).
     */
    fun updateBayesianFatigueFilter(
        prior: FatiguePosteriorState? = null,
        input: FatigueFilterInput
    ): FatiguePosteriorState {
        // Gentle daily transition mixing so stale extreme states decay toward moderate uncertainty
        val p0 = prior?.let {
            doubleArrayOf(
                0.85 * it.probFresh + 0.12 * it.probFunctionalOverload + 0.05 * it.probNonFunctionalRisk,
                0.12 * it.probFresh + 0.80 * it.probFunctionalOverload + 0.15 * it.probNonFunctionalRisk,
                0.03 * it.probFresh + 0.08 * it.probFunctionalOverload + 0.80 * it.probNonFunctionalRisk
            )
        } ?: doubleArrayOf(0.55, 0.35, 0.10)

        var lFresh = p0[0]
        var lFunc = p0[1]
        var lNonFunc = p0[2]
        var signalCount = 0

        val subjScore = listOfNotNull(input.subjectiveFatigue1To5, input.subjectiveSoreness1To5)
            .takeIf { it.isNotEmpty() }?.average()
        if (subjScore != null) {
            signalCount++
            when {
                subjScore >= 4.0 -> {
                    lFresh *= 0.10; lFunc *= 0.45; lNonFunc *= 0.85
                }
                subjScore >= 2.8 -> {
                    lFresh *= 0.35; lFunc *= 0.70; lNonFunc *= 0.35
                }
                else -> {
                    lFresh *= 0.80; lFunc *= 0.35; lNonFunc *= 0.10
                }
            }
        }

        if (input.executionDriftAdverse != null) {
            signalCount++
            if (input.executionDriftAdverse) {
                lFresh *= 0.18; lFunc *= 0.55; lNonFunc *= 0.82
            } else {
                lFresh *= 0.82; lFunc *= 0.50; lNonFunc *= 0.15
            }
        }

        if (input.hrvEvaluation is HrvEvaluationResult.Valid) {
            signalCount++
            if (input.hrvEvaluation.belowSmallestWorthwhileChange) {
                lFresh *= 0.25; lFunc *= 0.60; lNonFunc *= 0.75
            } else {
                lFresh *= 0.78; lFunc *= 0.50; lNonFunc *= 0.20
            }
        }

        if (signalCount == 0) {
            val sum = (lFresh + lFunc + lNonFunc).coerceAtLeast(1e-9)
            val pf = lFresh / sum; val po = lFunc / sum; val pn = lNonFunc / sum
            return FatiguePosteriorState(
                probFresh = pf,
                probFunctionalOverload = po,
                probNonFunctionalRisk = pn,
                normalizedEntropy = entropyNormalized(pf, po, pn),
                abstainedInsufficientSignals = true,
                overrideBlockedByHealthHold = input.activeHealthHoldPresent,
                explanation = "No standardized subjective, execution, or HRV signals observed; retaining wide prior without forcing changes."
            )
        }

        val total = (lFresh + lFunc + lNonFunc).coerceAtLeast(1e-9)
        val pf = lFresh / total
        val po = lFunc / total
        val pn = lNonFunc / total
        val hNorm = entropyNormalized(pf, po, pn)

        val msg = if (input.activeHealthHoldPresent) {
            "Active symptom or clinical hold overrides fatigue readiness modeling cleanly."
        } else {
            "Posterior states: Fresh=${(pf * 100).roundToInt()}%, Functional=${(po * 100).roundToInt()}%, Non-Functional Risk=${(pn * 100).roundToInt()}%."
        }
        return FatiguePosteriorState(
            probFresh = pf,
            probFunctionalOverload = po,
            probNonFunctionalRisk = pn,
            normalizedEntropy = hNorm,
            abstainedInsufficientSignals = false,
            overrideBlockedByHealthHold = input.activeHealthHoldPresent,
            explanation = msg
        )
    }

    /**
     * Identifiability-gated Banister/Busso impulse-response kinetics (`MOD-C1`, `Hellard 2006`).
     * Requires >= 56 days dense training log and >= 3 distinct standardized performance anchors;
     * otherwise falls back explicitly (`identifiable = false`) to Track 4 static tapers.
     */
    fun fitIndividualDoseResponse(history: List<DailyImpulseRecord>): IndividualKineticsProfile {
        val sorted = history.sortedBy { it.date }.distinctBy { it.date }
        val anchors = sorted.mapNotNull { rec ->
            rec.performanceAnchorScore?.takeIf { it.isFinite() && it > 0.0 }?.let { rec.date to it }
        }
        val spanDays = if (sorted.size >= 2) {
            (ChronoUnit.DAYS.between(sorted.first().date, sorted.last().date).toInt() + 1).coerceAtLeast(1)
        } else 0

        if (sorted.size < Engine20AdaptivePolicyRegistry.IMPULSE_MIN_HISTORY_DAYS ||
            spanDays < Engine20AdaptivePolicyRegistry.IMPULSE_MIN_HISTORY_DAYS ||
            anchors.size < Engine20AdaptivePolicyRegistry.IMPULSE_MIN_PERFORMANCE_ANCHORS
        ) {
            return IndividualKineticsProfile(
                identifiable = false,
                tau1FitnessDays = Engine20AdaptivePolicyRegistry.TAU1_FITNESS_PRIOR_DAYS,
                tau2FatigueDays = Engine20AdaptivePolicyRegistry.TAU2_FATIGUE_PRIOR_DAYS,
                recommendedTaperDays = 14,
                currentFitnessState = null,
                currentFatigueState = null,
                netPreparednessDelta = null,
                anchorCount = anchors.size,
                spanDays = spanDays,
                fallbackReason = "INSUFFICIENT_LONGITUDINAL_ANCHORS: Need >= ${Engine20AdaptivePolicyRegistry.IMPULSE_MIN_HISTORY_DAYS} days and >= ${Engine20AdaptivePolicyRegistry.IMPULSE_MIN_PERFORMANCE_ANCHORS} standardized anchors (Hellard 2006); using deterministic Track 4 taper."
            )
        }

        // Fill missing intermediate calendar days with 0 load to maintain exact chronological impulse spacing
        val loadByDate = sorted.associate { it.date to it.trainingImpulseLoad.coerceAtLeast(0.0) }
        val startDate = sorted.first().date
        val totalDays = ChronoUnit.DAYS.between(startDate, sorted.last().date).toInt() + 1
        val dailyLoads = DoubleArray(totalDays) { d -> loadByDate[startDate.plusDays(d.toLong())] ?: 0.0 }
        val loadStd = stddev(dailyLoads)
        if (loadStd < 1e-6) {
            return IndividualKineticsProfile(
                identifiable = false,
                tau1FitnessDays = Engine20AdaptivePolicyRegistry.TAU1_FITNESS_PRIOR_DAYS,
                tau2FatigueDays = Engine20AdaptivePolicyRegistry.TAU2_FATIGUE_PRIOR_DAYS,
                recommendedTaperDays = 14,
                currentFitnessState = null,
                currentFatigueState = null,
                netPreparednessDelta = null,
                anchorCount = anchors.size,
                spanDays = spanDays,
                fallbackReason = "DEGENERATE_CONSTANT_LOAD: Near-zero impulse variance renders fitness/fatigue time constants non-identifiable."
            )
        }

        val anchorDayIndices = anchors.map { (d, y) -> ChronoUnit.DAYS.between(startDate, d).toInt() to y }

        var bestLoss = Double.POSITIVE_INFINITY
        var bestTau1 = Engine20AdaptivePolicyRegistry.TAU1_FITNESS_PRIOR_DAYS
        var bestTau2 = Engine20AdaptivePolicyRegistry.TAU2_FATIGUE_PRIOR_DAYS
        var bestGEnd = 0.0
        var bestHEnd = 0.0
        var bestNetEnd = 0.0

        val tau1Candidates = listOf(30.0, 36.0, 42.0, 48.0, 56.0)
        val tau2Candidates = listOf(6.0, 9.0, 11.0, 14.0, 17.0)

        for (t1 in tau1Candidates) for (t2 in tau2Candidates) {
            if (t2 >= t1 - 10.0) continue
            val gSeries = DoubleArray(totalDays)
            val hSeries = DoubleArray(totalDays)
            val d1 = exp(-1.0 / t1)
            val d2 = exp(-1.0 / t2)
            for (d in 0 until totalDays) {
                val prevG = if (d > 0) gSeries[d - 1] else 0.0
                val prevH = if (d > 0) hSeries[d - 1] else 0.0
                gSeries[d] = prevG * d1 + dailyLoads[d]
                hSeries[d] = prevH * d2 + dailyLoads[d]
            }

            // Ridge MAP fit of p0 + k1 * g - k2 * h to anchors with strong prior regularization around (42 d, 11 d)
            val gObs = DoubleArray(anchorDayIndices.size) { gSeries[anchorDayIndices[it].first] }
            val hObs = DoubleArray(anchorDayIndices.size) { hSeries[anchorDayIndices[it].first] }
            val yObs = DoubleArray(anchorDayIndices.size) { anchorDayIndices[it].second }
            val netFeatures = DoubleArray(anchorDayIndices.size) { gObs[it] - 2.0 * hObs[it] }
            val yMean = yObs.average()
            val xMean = netFeatures.average()
            val denom = netFeatures.sumOf { (it - xMean) * (it - xMean) }.coerceAtLeast(1e-6)
            val slope = ((0 until yObs.size).sumOf { (netFeatures[it] - xMean) * (yObs[it] - yMean) } / denom)
                .coerceIn(0.0, 5.0)
            val intercept = yMean - slope * xMean
            val mse = (0 until yObs.size).sumOf {
                val err = yObs[it] - (intercept + slope * netFeatures[it])
                err * err
            } / yObs.size
            val ridgePriorPenalty = 0.002 * (
                ((t1 - Engine20AdaptivePolicyRegistry.TAU1_FITNESS_PRIOR_DAYS) / 14.0).pow(2) +
                    ((t2 - Engine20AdaptivePolicyRegistry.TAU2_FATIGUE_PRIOR_DAYS) / 6.0).pow(2)
                )
            val totalLoss = mse + ridgePriorPenalty
            if (totalLoss < bestLoss) {
                bestLoss = totalLoss
                bestTau1 = t1
                bestTau2 = t2
                bestGEnd = gSeries.last()
                bestHEnd = hSeries.last()
                bestNetEnd = slope * (bestGEnd - 2.0 * bestHEnd)
            }
        }

        // Translate individualized fatigue clearance constant tau2 into bounded taper length [7, 21] days
        val taperDays = (bestTau2 * 1.25).roundToInt().coerceIn(7, 21)
        return IndividualKineticsProfile(
            identifiable = true,
            tau1FitnessDays = bestTau1,
            tau2FatigueDays = bestTau2,
            recommendedTaperDays = taperDays,
            currentFitnessState = bestGEnd,
            currentFatigueState = bestHEnd,
            netPreparednessDelta = bestNetEnd,
            anchorCount = anchors.size,
            spanDays = spanDays
        )
    }

    private fun entropyNormalized(p1: Double, p2: Double, p3: Double): Double {
        val probs = listOf(p1, p2, p3).map { it.coerceIn(1e-12, 1.0) }
        val h = -probs.sumOf { it * ln(it) }
        return (h / ln(3.0)).coerceIn(0.0, 1.0)
    }

    private fun stddev(values: DoubleArray): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val v = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(v)
    }
}
