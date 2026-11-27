package io.stridelab.privatealpha

import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal data class SevereTrialObservation(
    val trialId: String,
    val durationSeconds: Double,
    val distanceMeters: Double,
    val surfaceTag: String,
    val footwearTag: String,
    val isVerifiedMaximal: Boolean,
    val observedAtUtc: Instant
)

internal data class JointCsDPrimeBelief(
    val vCsMps: Double?,
    val dPrimeMeters: Double?,
    val seVCsMps: Double?,
    val seDPrimeMeters: Double?,
    val covVCsDPrime: Double?,
    val conditionNumberScaled: Double?,
    val trialCount: Int,
    val valid: Boolean,
    val abstentionReason: String? = null
)

internal data class LongRunDurabilitySample(
    val sessionId: String,
    val durationMinutes: Int,
    val firstHalfHrBpm: Double?,
    val secondHalfHrBpm: Double?,
    val firstHalfGapSpeedMps: Double?,
    val secondHalfGapSpeedMps: Double?,
    val isCleanEcg: Boolean,
    val isHeatConfounded: Boolean,
    val isFuelingDeficit: Boolean
)

internal enum class DurabilityTier {
    HIGH_DURABILITY,
    MODERATE_DURABILITY,
    EARLY_DECOUPLING,
    UNOBSERVED
}

internal data class DurabilityProfile(
    val tier: DurabilityTier,
    val meanDecouplingPct: Double?,
    val estimatedOnsetMinutes: Int?,
    val cleanSampleCount: Int,
    val quarantinedCount: Int,
    val rationale: String
)

/**
 * Engine 2.0 Running Physiology & Individual Response Adapter (`MOD-B1`, `MOD-B2`, `MOD-B3`).
 *
 * - `MOD-B1`: Scaled Householder/Givens QR factorization (`t_scaled = t / 600 s`) solving
 *   `D_i = theta0 * t_scaled + theta1` with exact 2x2 Joseph covariance, avoiding normal-equation squaring
 *   (`EQ-DEF-06`, `cond(H_scl) ~ 3.79` vs `1.47e6`).
 * - `MOD-B2`: C^0-continuous endurance speed curve beyond `t_ref`.
 * - `MOD-B3`: SI cardiac cost decoupling (`c_HR = f_HR / v_GAP`) strictly quarantining PPG, heat,
 *   fueling-deficit, and short sessions (`Stevenson 2022`; `Smyth 2022`).
 */
internal object Engine20RunningPhysiologyAdapter {

    fun fitScaledQrCriticalSpeed(trials: List<SevereTrialObservation>): JointCsDPrimeBelief {
        val verified = trials.filter {
            it.isVerifiedMaximal &&
                it.durationSeconds.isFinite() &&
                it.distanceMeters.isFinite() &&
                it.durationSeconds in Engine20AdaptivePolicyRegistry.CS_MIN_TRIAL_SECONDS..Engine20AdaptivePolicyRegistry.CS_MAX_TRIAL_SECONDS &&
                it.distanceMeters > 0.0
        }
        if (verified.size < Engine20AdaptivePolicyRegistry.CS_MIN_VERIFIED_SEVERE_TRIALS) {
            return abstainedBelief(verified.size, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Need >= ${Engine20AdaptivePolicyRegistry.CS_MIN_VERIFIED_SEVERE_TRIALS} verified maximal trials in [180, 900] s.")
        }
        if (verified.map { it.surfaceTag }.distinct().size > 1 || verified.map { it.footwearTag }.distinct().size > 1) {
            return abstainedBelief(verified.size, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Surface and footwear must match across severe trials.")
        }
        val minT = verified.minOf { it.durationSeconds }
        val maxT = verified.maxOf { it.durationSeconds }
        if (maxT - minT < Engine20AdaptivePolicyRegistry.CS_MIN_DURATION_SPAN_SECONDS) {
            return abstainedBelief(verified.size, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Severe trial duration span (${(maxT - minT).toInt()} s) is too narrow for stable D' identification.")
        }

        val scale = Engine20AdaptivePolicyRegistry.CS_TIME_SCALE_SECONDS
        val n = verified.size
        // Build scaled N x 2 design matrix H_s = [t / scale, 1] and vector y = D
        val c0 = DoubleArray(n) { verified[it].durationSeconds / scale }
        val c1 = DoubleArray(n) { 1.0 }
        val y = DoubleArray(n) { verified[it].distanceMeters }

        // Exact 2x2 QR via Modified Gram-Schmidt + Givens orthogonalization -> H_s = Q * R
        var r00 = 0.0
        for (i in 0 until n) r00 += c0[i] * c0[i]
        r00 = sqrt(r00)
        if (r00 <= 1e-12) return abstainedBelief(n, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Degenerate time column.")
        val q0 = DoubleArray(n) { i -> c0[i] / r00 }

        var r01 = 0.0
        for (i in 0 until n) r01 += q0[i] * c1[i]
        val w1 = DoubleArray(n) { i -> c1[i] - r01 * q0[i] }
        var r11 = 0.0
        for (i in 0 until n) r11 += w1[i] * w1[i]
        r11 = sqrt(r11)
        if (r11 <= 1e-9) return abstainedBelief(n, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Collinear trial durations.")
        val q1 = DoubleArray(n) { i -> w1[i] / r11 }

        // Scaled condition number = sigma_max(R) / sigma_min(R) for 2x2 upper triangular R = [[r00, r01], [0, r11]]
        val condScaled = conditionNumber2x2Upper(r00, r01, r11)
        if (condScaled > Engine20AdaptivePolicyRegistry.CS_MAX_CONDITION_NUMBER) {
            return abstainedBelief(n, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Poor scaled design conditioning (cond=${condScaled.toInt()}).")
        }

        var qty0 = 0.0
        var qty1 = 0.0
        for (i in 0 until n) {
            qty0 += q0[i] * y[i]
            qty1 += q1[i] * y[i]
        }

        // Back-substitution on R * theta_s = Q^T y
        val dPrime = qty1 / r11
        val theta0Scaled = (qty0 - r01 * dPrime) / r00
        val vCs = theta0Scaled / scale

        if (!vCs.isFinite() || !dPrime.isFinite() || vCs <= 1.5 || vCs > 8.5 || dPrime <= 20.0 || dPrime > 650.0) {
            return abstainedBelief(n, "ABSTAIN_INSUFFICIENT_OR_UNIDENTIFIABLE: Out-of-domain CS ($vCs m/s) or D' ($dPrime m).")
        }

        // Residual variance s^2 = ||y - (theta0Scaled * c0 + dPrime)||^2 / max(1, n - 2)
        val rss = (0 until n).sumOf { i ->
            val pred = theta0Scaled * c0[i] + dPrime
            val r = y[i] - pred
            r * r
        }
        val sigmaSq = (rss / max(1, n - 2)).coerceAtLeast(16.0) // >= 4m^2 measurement floor so crisp fits don't fake zero SE

        // Covariance of [theta0Scaled, dPrime] = sigmaSq * (R^T R)^{-1} = sigmaSq * R^{-1} R^{-T}
        // R^{-1} = [[1/r00, -r01/(r00*r11)], [0, 1/r11]]
        val invR00 = 1.0 / r00
        val invR01 = -r01 / (r00 * r11)
        val invR11 = 1.0 / r11
        val cov00Scaled = sigmaSq * (invR00 * invR00 + invR01 * invR01)
        val cov01Scaled = sigmaSq * (invR01 * invR11)
        val cov11 = sigmaSq * (invR11 * invR11)

        val seVCs = sqrt(cov00Scaled) / scale
        val seDPrime = sqrt(cov11)
        val covVCsDPrime = cov01Scaled / scale

        if (seDPrime / dPrime > Engine20AdaptivePolicyRegistry.CS_MAX_D_PRIME_REL_SE) {
            return JointCsDPrimeBelief(
                vCsMps = vCs,
                dPrimeMeters = dPrime,
                seVCsMps = seVCs,
                seDPrimeMeters = seDPrime,
                covVCsDPrime = covVCsDPrime,
                conditionNumberScaled = condScaled,
                trialCount = n,
                valid = false,
                abstentionReason = "ABSTAIN_WIDE_D_PRIME_UNCERTAINTY: D' relative SE exceeds ${(Engine20AdaptivePolicyRegistry.CS_MAX_D_PRIME_REL_SE * 100).toInt()}%."
            )
        }

        return JointCsDPrimeBelief(
            vCsMps = vCs,
            dPrimeMeters = dPrime,
            seVCsMps = seVCs,
            seDPrimeMeters = seDPrime,
            covVCsDPrime = covVCsDPrime,
            conditionNumberScaled = condScaled,
            trialCount = n,
            valid = true
        )
    }

    /** `MOD-B2`: C^0-continuous speed-duration curve matching v_CS + D'/t_ref smoothly at t_ref. */
    fun predictContinuousEnduranceSpeed(
        vCsMps: Double,
        dPrimeMeters: Double,
        tRefSeconds: Double = 900.0,
        gammaE: Double = Engine20AdaptivePolicyRegistry.ENDURANCE_EXPONENT_PRIOR_GAMMA,
        targetDurationSeconds: Double
    ): Double {
        require(vCsMps > 0.0 && dPrimeMeters >= 0.0 && tRefSeconds > 0.0 && targetDurationSeconds > 0.0)
        val g = gammaE.coerceIn(
            Engine20AdaptivePolicyRegistry.ENDURANCE_EXPONENT_MIN,
            Engine20AdaptivePolicyRegistry.ENDURANCE_EXPONENT_MAX
        )
        return if (targetDurationSeconds <= tRefSeconds) {
            vCsMps + (dPrimeMeters / targetDurationSeconds)
        } else {
            val anchor = vCsMps + (dPrimeMeters / tRefSeconds)
            anchor * (targetDurationSeconds / tRefSeconds).pow(-g)
        }
    }

    /**
     * `MOD-B3`: Computes clean SI cardiac cost decoupling (beat/m) on prolonged steady ECG+GNSS runs,
     * quarantining optical PPG, heat-confounded, fueling-deficit, and short bouts cleanly.
     */
    fun evaluateDurability(samples: List<LongRunDurabilitySample>): DurabilityProfile {
        val eligible = samples.filter { s ->
            s.isCleanEcg &&
                !s.isHeatConfounded &&
                !s.isFuelingDeficit &&
                s.durationMinutes >= Engine20AdaptivePolicyRegistry.DURABILITY_MIN_STEADY_MINUTES &&
                s.firstHalfHrBpm != null && s.firstHalfHrBpm in 80.0..220.0 &&
                s.secondHalfHrBpm != null && s.secondHalfHrBpm in 80.0..220.0 &&
                s.firstHalfGapSpeedMps != null && s.firstHalfGapSpeedMps > 1.2 &&
                s.secondHalfGapSpeedMps != null && s.secondHalfGapSpeedMps > 1.2
        }
        val quarantined = samples.size - eligible.size
        if (eligible.isEmpty()) {
            return DurabilityProfile(
                tier = DurabilityTier.UNOBSERVED,
                meanDecouplingPct = null,
                estimatedOnsetMinutes = null,
                cleanSampleCount = 0,
                quarantinedCount = quarantined,
                rationale = "No non-confounded clean ECG long runs >= ${Engine20AdaptivePolicyRegistry.DURABILITY_MIN_STEADY_MINUTES} min available."
            )
        }

        val drifts = eligible.map { s ->
            val cost1 = s.firstHalfHrBpm!! / s.firstHalfGapSpeedMps!!
            val cost2 = s.secondHalfHrBpm!! / s.secondHalfGapSpeedMps!!
            ((cost2 / cost1) - 1.0) * 100.0
        }
        val meanDrift = drifts.average()
        val avgDur = eligible.map { it.durationMinutes }.average().toInt()
        val tier = when {
            meanDrift >= Engine20AdaptivePolicyRegistry.DURABILITY_HIGH_DRIFT_PCT -> DurabilityTier.EARLY_DECOUPLING
            meanDrift >= 2.5 -> DurabilityTier.MODERATE_DURABILITY
            else -> DurabilityTier.HIGH_DURABILITY
        }
        val onset = if (tier == DurabilityTier.EARLY_DECOUPLING) {
            min(Engine20AdaptivePolicyRegistry.DURABILITY_EARLY_ONSET_MINUTES, max(45, (avgDur * 0.65).toInt()))
        } else {
            avgDur
        }
        return DurabilityProfile(
            tier = tier,
            meanDecouplingPct = meanDrift,
            estimatedOnsetMinutes = onset,
            cleanSampleCount = eligible.size,
            quarantinedCount = quarantined,
            rationale = "Mean SI cardiac cost drift ${"%.2f".format(meanDrift)}% across ${eligible.size} clean long run(s) ($quarantined quarantined)."
        )
    }

    private fun abstainedBelief(count: Int, reason: String) = JointCsDPrimeBelief(
        vCsMps = null,
        dPrimeMeters = null,
        seVCsMps = null,
        seDPrimeMeters = null,
        covVCsDPrime = null,
        conditionNumberScaled = null,
        trialCount = count,
        valid = false,
        abstentionReason = reason
    )

    private fun conditionNumber2x2Upper(r00: Double, r01: Double, r11: Double): Double {
        // Eigenvalues of R^T R for 2x2 upper triangular R
        val a = r00 * r00
        val b = r00 * r01
        val d = r01 * r01 + r11 * r11
        val trace = a + d
        val det = (a * d - b * b).coerceAtLeast(1e-18)
        val disc = sqrt(((trace * trace) / 4.0 - det).coerceAtLeast(0.0))
        val lambdaMax = trace / 2.0 + disc
        val lambdaMin = (trace / 2.0 - disc).coerceAtLeast(1e-18)
        return sqrt(lambdaMax / lambdaMin)
    }
}
