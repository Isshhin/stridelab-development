package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Execution context identity for a strength exercise reference (Track 6 §2.2).
 * Any material shift invalidates verbatim load transfer and downgrades to RIR rediscovery.
 */
internal data class StrengthLoadContext(
    val exerciseId: String,
    val loadSemanticsKind: ExternalLoadSemanticsKind,
    val romTag: StrengthRomTag = StrengthRomTag.FULL_CONTROLLED,
    val tempoTag: StrengthTempoTag = StrengthTempoTag.CONTROLLED_STANDARD,
    val laterality: StrengthLaterality = StrengthLaterality.BILATERAL,
    val isAssistedBodyweight: Boolean = false,
    val recordedBodyMassKg: Kilograms? = null,
    val machineOrBandId: String? = null
) {
    fun mismatchReasonsAgainst(previous: StrengthLoadContext): List<String> = buildList {
        if (exerciseId != previous.exerciseId) add("EXERCISE_CHANGED")
        if (loadSemanticsKind != previous.loadSemanticsKind) add("EQUIPMENT_OR_LOAD_SEMANTICS_CHANGED")
        if (machineOrBandId != previous.machineOrBandId) add("MACHINE_OR_BAND_IDENTITY_CHANGED")
        if (romTag != previous.romTag) add("ROM_CHANGED")
        if (tempoTag != previous.tempoTag) add("TEMPO_CHANGED")
        if (laterality != previous.laterality) add("LATERALITY_CHANGED")
        if (isAssistedBodyweight != previous.isAssistedBodyweight) add("ASSISTANCE_MODE_CHANGED")
        val prevBm = previous.recordedBodyMassKg?.value
        val curBm = recordedBodyMassKg?.value
        if (prevBm != null && curBm != null && prevBm > 0.0) {
            if (abs(curBm - prevBm) / prevBm >= Engine16StrengthPolicyRegistry.BODY_MASS_TRANSFER_SHIFT_RATIO) {
                add("BODY_MASS_SHIFT_GE_5_PCT")
            }
        }
    }
}

internal enum class E1RmValidityTier {
    TIER_A_PROVISIONAL,
    TIER_B_SHADOW_ONLY
}

/**
 * Strictly separated 6 strength calibration constructs (Track 6 §2.2 & MOD-16).
 */
internal data class MeasuredMaxStrength(
    val exerciseId: String,
    val load: ExternalLoadSemantics,
    val performedOn: LocalDate,
    val context: StrengthLoadContext
)

internal data class EstimatedMaxStrength(
    val epleyKg: Kilograms,
    val brzyckiKg: Kilograms,
    val effectiveReps: Int,
    val modelDisagreementKg: Kilograms,
    val tier: E1RmValidityTier,
    val isMeasuredMaxStrength: Boolean = false
) {
    init {
        require(!isMeasuredMaxStrength) {
            "INV-05 violation: EstimatedMaxStrength must never claim to be measured 1RM"
        }
    }
}

internal data class ObservedSubmaximalPerformance(
    val exerciseId: String,
    val performedOn: LocalDate,
    val context: StrengthLoadContext,
    val load: ExternalLoadSemantics,
    val completedReps: Int,
    val reportedRir: Int? = null,
    val techniqueQuality: TechniqueQuality = TechniqueQuality.UNOBSERVED,
    val techniqueProvenance: TechniqueProvenance = TechniqueProvenance.UNOBSERVED_TECHNIQUE
) {
    init {
        require(completedReps >= 0) { "completedReps must be >= 0" }
        require(reportedRir == null || reportedRir in 0..10) { "reportedRir must be in 0..10" }
    }
}

internal sealed interface ProvisionalTrainingLoadRef {
    data class AnchoredToSubmax(
        val referenceLoad: ExternalLoadSemantics,
        val targetReps: Int,
        val sourceDate: LocalDate,
        val uncertaintyNote: String
    ) : ProvisionalTrainingLoadRef

    data class DiscoverByRir(
        val targetRir: IntRange,
        val reasons: List<String>
    ) : ProvisionalTrainingLoadRef
}

internal data class StrengthCalibrationResult(
    val measuredMax: MeasuredMaxStrength?,
    val estimatedMax: EstimatedMaxStrength?,
    val trainingLoadRef: ProvisionalTrainingLoadRef,
    val observedHistory: ObservedSubmaximalPerformance?,
    val uncertaintyFlags: Set<String>
)

/**
 * Pure deterministic Tier A strength calibration engine implementing MOD-16 and POL-042.
 */
internal object Engine16StrengthCalibrationEngine {

    /**
     * Exact MOD-16 piecewise Epley & Brzycki provisional e1RM.
     *
     * Tier A validity predicate (`POL-042`):
     * `reps in 1..10 && rir in 0..4 && (reps + rir) <= 10 && technique == CLEAN`.
     *
     * Note: When [rir] is null, Tier A requires [assumeFailureIfRirNull] = true only for explicit
     * failure-tested sets (`reps in 1..10`); otherwise returns null unless an explicit RIR <= 4 is
     * supplied (never fabricating 0 RIR silently over ambiguous submax sets, while callers such as
     * `StrengthProgressionProjector` may evaluate known failure sets explicitly).
     */
    fun estimateE1Rm(
        weightKg: Double,
        reps: Int,
        rir: Int? = null,
        techniqueQuality: TechniqueQuality = TechniqueQuality.CLEAN,
        allowTierBShadowWindow: Boolean = false,
        assumeFailureIfRirNull: Boolean = false
    ): EstimatedMaxStrength? {
        if (!weightKg.isFinite() || weightKg <= 0.0) return null
        if (techniqueQuality != TechniqueQuality.CLEAN) return null
        if (reps !in Engine16StrengthPolicyRegistry.E1RM_MIN_REPS..Engine16StrengthPolicyRegistry.E1RM_MAX_REPS) {
            return null
        }
        val effectiveRir = when {
            rir == null && assumeFailureIfRirNull -> 0
            rir == null -> return null
            rir !in 0..Engine16StrengthPolicyRegistry.E1RM_MAX_RIR -> return null
            else -> rir
        }
        val effectiveReps = reps + effectiveRir
        val tier = when {
            effectiveReps in 1..Engine16StrengthPolicyRegistry.E1RM_TIER_A_MAX_EFFECTIVE_REPS ->
                E1RmValidityTier.TIER_A_PROVISIONAL
            allowTierBShadowWindow &&
                effectiveReps in (Engine16StrengthPolicyRegistry.E1RM_TIER_A_MAX_EFFECTIVE_REPS + 1)..
                Engine16StrengthPolicyRegistry.E1RM_TIER_B_SHADOW_MAX_EFFECTIVE_REPS ->
                E1RmValidityTier.TIER_B_SHADOW_ONLY
            else -> return null
        }

        val epleyRaw = if (effectiveReps == 1) {
            weightKg
        } else {
            weightKg * (1.0 + effectiveReps / Engine16StrengthPolicyRegistry.EPLEY_DIVISOR)
        }
        val brzyckiDen = Engine16StrengthPolicyRegistry.BRZYCKI_INTERCEPT -
            Engine16StrengthPolicyRegistry.BRZYCKI_SLOPE * effectiveReps
        if (!epleyRaw.isFinite() || !brzyckiDen.isFinite() || brzyckiDen <= 0.0) return null
        val brzyckiRaw = weightKg / brzyckiDen
        if (!brzyckiRaw.isFinite() || brzyckiRaw <= 0.0) return null

        return EstimatedMaxStrength(
            epleyKg = Kilograms.of(epleyRaw),
            brzyckiKg = Kilograms.of(brzyckiRaw),
            effectiveReps = effectiveReps,
            modelDisagreementKg = Kilograms.of(abs(epleyRaw - brzyckiRaw)),
            tier = tier
        )
    }

    fun calibrate(
        targetContext: StrengthLoadContext,
        asOfDate: LocalDate,
        latestObserved: ObservedSubmaximalPerformance?,
        freshnessMaxDays: Long = 56L
    ): StrengthCalibrationResult {
        if (latestObserved == null || latestObserved.performedOn.isAfter(asOfDate)) {
            return StrengthCalibrationResult(
                measuredMax = null,
                estimatedMax = null,
                trainingLoadRef = ProvisionalTrainingLoadRef.DiscoverByRir(
                    targetRir = Engine16StrengthPolicyRegistry.RIR_BAND_REDISCOVERY,
                    reasons = listOf("NO_PRIOR_EXERCISE_HISTORY")
                ),
                observedHistory = null,
                uncertaintyFlags = setOf("UNOBSERVED_PRIOR_LOAD")
            )
        }

        val ageDays = ChronoUnit.DAYS.between(latestObserved.performedOn, asOfDate)
        val reasons = mutableListOf<String>()
        reasons += targetContext.mismatchReasonsAgainst(latestObserved.context)
        if (ageDays > freshnessMaxDays) reasons += "STALE_LOAD_REFERENCE"
        if (latestObserved.techniqueQuality == TechniqueQuality.BREAKDOWN) {
            reasons += "PRIOR_TECHNIQUE_BREAKDOWN"
        }

        val loadKg = latestObserved.load.comparableLoadKg?.value
        val measured = if (reasons.isEmpty() &&
            latestObserved.completedReps == 1 &&
            latestObserved.reportedRir == 0 &&
            latestObserved.techniqueQuality == TechniqueQuality.CLEAN &&
            ageDays <= freshnessMaxDays
        ) {
            MeasuredMaxStrength(
                exerciseId = latestObserved.exerciseId,
                load = latestObserved.load,
                performedOn = latestObserved.performedOn,
                context = latestObserved.context
            )
        } else null

        val est = if (reasons.isEmpty() &&
            loadKg != null &&
            latestObserved.techniqueQuality == TechniqueQuality.CLEAN
        ) {
            estimateE1Rm(
                weightKg = loadKg,
                reps = latestObserved.completedReps,
                rir = latestObserved.reportedRir,
                techniqueQuality = latestObserved.techniqueQuality,
                allowTierBShadowWindow = false,
                assumeFailureIfRirNull = false
            )
        } else null

        val flags = linkedSetOf<String>()
        flags += reasons
        if (latestObserved.reportedRir == null) flags += "RIR_UNREPORTED"
        if (latestObserved.techniqueProvenance == TechniqueProvenance.UNOBSERVED_TECHNIQUE) {
            flags += "UNOBSERVED_TECHNIQUE"
        }
        if (est != null) flags += "E1RM_WIDE_PROVISIONAL_REFERENCE_ONLY"

        val trainingRef = if (reasons.isEmpty() && latestObserved.completedReps > 0) {
            ProvisionalTrainingLoadRef.AnchoredToSubmax(
                referenceLoad = latestObserved.load,
                targetReps = latestObserved.completedReps,
                sourceDate = latestObserved.performedOn,
                uncertaintyNote = if (latestObserved.reportedRir == null) {
                    "Anchored to prior completed sets; RIR not recorded."
                } else {
                    "Anchored to recent submaximal sets (${latestObserved.reportedRir} RIR)."
                }
            )
        } else {
            ProvisionalTrainingLoadRef.DiscoverByRir(
                targetRir = Engine16StrengthPolicyRegistry.RIR_BAND_REDISCOVERY,
                reasons = if (reasons.isEmpty()) listOf("ZERO_COMPLETED_REPS") else reasons
            )
        }

        return StrengthCalibrationResult(
            measuredMax = measured,
            estimatedMax = est,
            trainingLoadRef = trainingRef,
            observedHistory = latestObserved,
            uncertaintyFlags = flags
        )
    }
}
