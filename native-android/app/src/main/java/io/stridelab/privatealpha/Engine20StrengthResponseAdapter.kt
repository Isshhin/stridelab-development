package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

internal data class StrengthSetObservation(
    val exerciseId: String,
    val loadKg: Double,
    val reps: Int,
    val rir: Int,
    val techniqueClean: Boolean,
    val observedOn: LocalDate
)

internal data class BayesianE1rmState(
    val exerciseId: String,
    val meanLogE1rm: Double,
    val varianceLogE1rm: Double,
    val observationCount: Int,
    val lastObservedOn: LocalDate
) {
    val sdLog: Double get() = sqrt(varianceLogE1rm)
    val medianE1rmKg: Double get() = exp(meanLogE1rm)
    val conservativeLowerKg: Double
        get() = exp(meanLogE1rm - Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_LOWER_Z_CONSERVATIVE * sdLog)
    val lower90Kg: Double get() = exp(meanLogE1rm - 1.64485 * sdLog)
    val upper90Kg: Double get() = exp(meanLogE1rm + 1.64485 * sdLog)
}

internal data class WeeklyMuscleOutcome(
    val muscleId: String,
    val directHardSets: Int,
    val indirectHardSets: Int,
    val sorenessHigh: Boolean,
    val repsStalledOrRegressed: Boolean
)

internal enum class MuscleVolumeToleranceTier {
    LOW_TOLERANCE,
    STANDARD_TOLERANCE,
    HIGH_TOLERANCE,
    UNOBSERVED
}

internal data class MuscleRecoverableBand(
    val muscleId: String,
    val tier: MuscleVolumeToleranceTier,
    val directSetsLatest: Int,
    val indirectSetsLatest: Int,
    val fractionalWeeklySetsLatest: Double,
    val recommendedMinFractionalSets: Double,
    val recommendedMaxFractionalSets: Double,
    val rationale: String
)

/**
 * Engine 2.0 Individual Strength & Recoverable Muscle Volume Response Adapter (`E2-03`, `MOD-B4`).
 */
internal object Engine20StrengthResponseAdapter {

    /**
     * Updates per-exercise log-space Bayesian e1RM posterior using `Halperin (2022)` RIR-scaled
     * observation noise, Huber outlier damping (`kappa = 1.5`), temporal diffusion `Q_k`, and a strict
     * variance floor protecting barbell working loads against `0 RIR` failure spikes.
     */
    fun updateBayesianE1rm(
        prior: BayesianE1rmState?,
        obs: StrengthSetObservation
    ): BayesianE1rmState? {
        if (!obs.techniqueClean ||
            !obs.loadKg.isFinite() || obs.loadKg <= 0.0 ||
            obs.reps !in 1..Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_MAX_EFFECTIVE_REPS ||
            obs.rir !in 0..4 ||
            (obs.reps + obs.rir) > Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_MAX_EFFECTIVE_REPS
        ) {
            return prior
        }

        val effReps = obs.reps + obs.rir
        val rawE1rm = when {
            effReps == 1 -> obs.loadKg
            effReps <= 10 -> obs.loadKg * (1.0 + effReps / 30.0)
            else -> obs.loadKg * (36.0 / (37.0 - effReps)) // Brzycki domain <= 12 safe
        }
        val yObs = ln(rawE1rm)
        val rirSd = 0.025 + obs.rir * Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_RIR_SD_PER_STEP +
            if (effReps > 10) 0.025 else 0.0
        val baseObsVar = rirSd * rirSd

        if (prior == null) {
            val initVar = maxOf(
                baseObsVar,
                Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_SIGMA_FLOOR * Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_SIGMA_FLOOR
            )
            return BayesianE1rmState(
                exerciseId = obs.exerciseId,
                meanLogE1rm = yObs,
                varianceLogE1rm = initVar,
                observationCount = 1,
                lastObservedOn = obs.observedOn
            )
        }

        val dtDays = ChronoUnit.DAYS.between(prior.lastObservedOn, obs.observedOn).coerceAtLeast(0L)
        val diffusedVar = prior.varianceLogE1rm + dtDays * Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_DAILY_DIFFUSION_VAR

        // Huber damping protects against single-day spike / uncalibrated load log
        val residual = yObs - prior.meanLogE1rm
        val totalSd = sqrt(diffusedVar + baseObsVar)
        val z = abs(residual) / totalSd
        val huberInflate = if (z > Engine20AdaptivePolicyRegistry.HUBER_KAPPA) {
            z / Engine20AdaptivePolicyRegistry.HUBER_KAPPA
        } else 1.0
        val effectiveObsVar = baseObsVar * huberInflate

        val kalmanGain = diffusedVar / (diffusedVar + effectiveObsVar)
        val nextMean = prior.meanLogE1rm + kalmanGain * residual
        val minVar = Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_SIGMA_FLOOR * Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_SIGMA_FLOOR
        val nextVar = ((1.0 - kalmanGain) * diffusedVar).coerceAtLeast(minVar)

        return BayesianE1rmState(
            exerciseId = obs.exerciseId,
            meanLogE1rm = nextMean,
            varianceLogE1rm = nextVar,
            observationCount = prior.observationCount + 1,
            lastObservedOn = if (obs.observedOn.isAfter(prior.lastObservedOn)) obs.observedOn else prior.lastObservedOn
        )
    }

    /**
     * `MOD-B4` (`Pelland 2025/2026`): Computes S_frac = S_direct + 0.5 * S_indirect alongside
     * separate direct/indirect totals and adapts recoverable volume bands from soreness & stall signals.
     */
    fun evaluateMuscleRecoverableBand(
        muscleId: String,
        weeklyHistory: List<WeeklyMuscleOutcome>
    ): MuscleRecoverableBand {
        val relevant = weeklyHistory.filter { it.muscleId == muscleId }
        if (relevant.isEmpty()) {
            return MuscleRecoverableBand(
                muscleId = muscleId,
                tier = MuscleVolumeToleranceTier.UNOBSERVED,
                directSetsLatest = 0,
                indirectSetsLatest = 0,
                fractionalWeeklySetsLatest = 0.0,
                recommendedMinFractionalSets = Engine20AdaptivePolicyRegistry.DEFAULT_MUSCLE_MEV_FRACTIONAL_SETS,
                recommendedMaxFractionalSets = Engine20AdaptivePolicyRegistry.DEFAULT_MUSCLE_MRV_FRACTIONAL_SETS,
                rationale = "No multi-week local muscle outcomes yet; using standard Tier A bounds."
            )
        }
        val latest = relevant.last()
        val fractionalList = relevant.map {
            it.directHardSets + Engine20AdaptivePolicyRegistry.FRACTIONAL_INDIRECT_MULTIPLIER * it.indirectHardSets
        }
        val latestFrac = fractionalList.last()
        val soreOrStalledWeeks = relevant.count { it.sorenessHigh || it.repsStalledOrRegressed }
        val highToleranceWeeks = relevant.countIndexed { idx, w ->
            !w.sorenessHigh && !w.repsStalledOrRegressed && fractionalList[idx] >= 14.0
        }

        val (tier, minSets, maxSets) = when {
            soreOrStalledWeeks >= 2 || (relevant.last().sorenessHigh && relevant.last().repsStalledOrRegressed) -> Triple(
                MuscleVolumeToleranceTier.LOW_TOLERANCE,
                4.0,
                minOf(Engine20AdaptivePolicyRegistry.LOW_TOLERANCE_MRV_SETS, (latestFrac - 2.0).coerceAtLeast(6.0))
            )
            highToleranceWeeks >= 2 -> Triple(
                MuscleVolumeToleranceTier.HIGH_TOLERANCE,
                8.0,
                Engine20AdaptivePolicyRegistry.HIGH_TOLERANCE_MRV_SETS
            )
            else -> Triple(
                MuscleVolumeToleranceTier.STANDARD_TOLERANCE,
                Engine20AdaptivePolicyRegistry.DEFAULT_MUSCLE_MEV_FRACTIONAL_SETS,
                Engine20AdaptivePolicyRegistry.DEFAULT_MUSCLE_MRV_FRACTIONAL_SETS
            )
        }

        return MuscleRecoverableBand(
            muscleId = muscleId,
            tier = tier,
            directSetsLatest = latest.directHardSets,
            indirectSetsLatest = latest.indirectHardSets,
            fractionalWeeklySetsLatest = latestFrac,
            recommendedMinFractionalSets = minSets,
            recommendedMaxFractionalSets = maxSets,
            rationale = "Local muscle $muscleId classified ${tier.name} (latest ${"%.1f".format(latestFrac)} fractional sets = ${latest.directHardSets} direct + 0.5x${latest.indirectHardSets} indirect)."
        )
    }

    private inline fun <T> List<T>.countIndexed(predicate: (Int, T) -> Boolean): Int {
        var count = 0
        for (i in indices) if (predicate(i, this[i])) count++
        return count
    }
}
