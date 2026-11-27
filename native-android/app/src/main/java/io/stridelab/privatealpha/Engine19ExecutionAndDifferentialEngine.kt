package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

internal sealed interface DimensionValue<out T> {
    data class Measured<out T>(val value: T) : DimensionValue<T>
    data class Unknown(val reason: String) : DimensionValue<Nothing>
    data object NotApplicable : DimensionValue<Nothing>
}

internal enum class SymptomStatus {
    CONFIRMED_NONE,
    MILD_EXPECTED_SORENESS,
    CONCERNING_SYMPTOM_FLAG,
    MISSING_UNREPORTED
}

internal enum class RecoveryEvidenceStatus {
    VERIFIED_ACCEPTABLE,
    ADVERSE_FATIGUE,
    MISSING_UNREPORTED
}

internal enum class TechniqueObservationStatus {
    COACH_VERIFIED_CLEAN,
    USER_REPORTED_CLEAN,
    BORDERLINE,
    BREAKDOWN,
    UNOBSERVED
}

internal enum class SessionCompletionState {
    COMPLETE,
    PARTIAL,
    ABORTED,
    MISSED,
    SUBSTITUTED
}

internal enum class IntensityAdherenceBand {
    BELOW,
    ON_TARGET,
    EXCESSIVE_ABOVE
}

internal enum class StimulusOutcome {
    STIMULUS_ACHIEVED,
    STIMULUS_ACHIEVED_ENV_ADJUSTED,
    OVERSHOT_INTENSITY_RISK,
    PARTIAL_STIMULUS,
    ABORTED_OR_FAILED,
    INSUFFICIENT_EVIDENCE
}

/**
 * 8-Layer & 12-Dimension Multidimensional Execution Assessment (Track 7 Refinements §4).
 *
 * Strictly bans scalar "% adherence" collapsing and opaque 0-100 readiness scores (`EXCL-D`).
 * Missing reports NEVER become CONFIRMED_NONE or VERIFIED_ACCEPTABLE, and completed reps NEVER
 * infer clean technique when unobserved (`CD-13`).
 */
internal data class MultidimensionalExecutionAssessment(
    val activityId: String?,
    val plannedSessionId: String?,
    val discipline: String,
    // Layer 1-3: Prescribed vs Observed vs Domain Achievement
    val completion: SessionCompletionState,
    val stimulusOutcome: StimulusOutcome,
    val domainNote: String,
    // Layer 4-8: Confounding, Telemetry Confidence, RPE, Symptom, Recovery
    val envHeatConfounded: Boolean,
    val envAltitudeOrTerrainConfounded: Boolean,
    val telemetryCorruptedOrQuarantined: Boolean,
    val reportedRpe: Int?,
    val targetRpeRange: IntRange?,
    val reportedRir: Int?,
    val symptomStatus: SymptomStatus,
    val recoveryEvidence: RecoveryEvidenceStatus,
    val techniqueStatus: TechniqueObservationStatus,
    // 12 Structurally Separated Descriptive Dimensions
    val durationAdherence: DimensionValue<Double>,
    val distanceAdherence: DimensionValue<Double>,
    val intensityAdherence: DimensionValue<IntensityAdherenceBand>,
    val repetitionStructureAdherence: DimensionValue<Double>,
    val recoveryIntervalAdherence: DimensionValue<Boolean>,
    val strengthLoadRepAdherence: DimensionValue<Boolean>,
    val calisthenicsNodeAndTechAdherence: DimensionValue<TechniqueObservationStatus>,
    val conditioningDensityAdherence: DimensionValue<Boolean>,
    val subjectiveDifficultyAligned: DimensionValue<Boolean>,
    val lateRepOrFinishFadeDetected: Boolean,
    val difficultyFromRepOne: Boolean,
    val objectiveHrOrPaceAnomalyFlag: Boolean
)

internal enum class HrvProtocol {
    WAKING_SUPINE,
    WAKING_SEATED,
    NOCTURNAL_SWS
}

internal enum class HrvMetricType {
    RMSSD_MS,
    SDNN_MS
}

internal data class HrvReading(
    val date: LocalDate,
    val valueMs: Double,
    val protocol: HrvProtocol,
    val metricType: HrvMetricType
)

internal sealed interface HrvEvaluationResult {
    data class Valid(
        val protocol: HrvProtocol,
        val valid7DayCount: Int,
        val valid28DayCount: Int,
        val meanLn7: Double,
        val meanLn28: Double,
        val sdLn28: Double,
        val deltaLnZScore: Double,
        val belowSmallestWorthwhileChange: Boolean,
        val lowSampleCaveat: Boolean
    ) : HrvEvaluationResult

    data class InsufficientOrInvalid(
        val reasonCode: String,
        val explanation: String
    ) : HrvEvaluationResult
}

internal enum class DifferentialHypothesisKind {
    ORDINARY_DAILY_VARIATION,
    ACUTE_FATIGUE,
    ACCUMULATED_FATIGUE_OVERREACHING,
    EXCESSIVE_WORKOUT_DOSE,
    INCORRECT_INTENSITY_CALIBRATION,
    INADEQUATE_RECOVERY_SPACING,
    ENVIRONMENTAL_CONFOUNDING,
    FUELING_HYDRATION_LIMITATION,
    MEASUREMENT_ERROR,
    REDUCED_TRAINING_CONSISTENCY,
    GENUINE_PERFORMANCE_DETERIORATION
}

internal enum class PlausibilityTier {
    HIGH,
    MODERATE,
    LOW
}

internal data class ScoredExplanationHypothesis(
    val kind: DifferentialHypothesisKind,
    val plausibility: PlausibilityTier,
    val supportingEvidence: List<String>,
    val conflictingEvidence: List<String>,
    val missingInputs: List<String>
)

internal data class DifferentialContextInput(
    val assessment: MultidimensionalExecutionAssessment,
    val priorPoorRestedNonConfoundedCount: Int = 0,
    val consecutiveHighRpeEasyCount: Int = 0,
    val recent24To48hHardStressPresent: Boolean = false,
    val postRecoveryWeekNoRebound: Boolean = false,
    val spacedBelow48hAfterHeavyLowerOrLong: Boolean = false,
    val durationOver90MinWithoutFueling: Boolean = false,
    val ercOrPpgCadenceLockDetected: Boolean = false,
    val recent28DayConsistencyLow: Boolean = false,
    val sleepsPoorReported: Boolean? = null,
    val subjectiveFeltFineDespiteObjectiveAnomaly: Boolean = false,
    val hrvResult: HrvEvaluationResult? = null
)

internal data class DifferentialEvaluationResult(
    val hypotheses: List<ScoredExplanationHypothesis>,
    val conflictingRecoveryEvidenceFlag: Boolean,
    val descriptivePattern: String
)

/**
 * Engine 1.9 Execution Assessment, `MOD-21` Standardized HRV, and 11-Hypothesis Non-Causal
 * Differential Model (Track 7 §§4, 6, 11).
 */
internal object Engine19ExecutionAndDifferentialEngine {

    /**
     * `MOD-21` Strictly standardized ln(rMSSD_ms) HRV assessment (Buchheit 2014; Plews 2014).
     * Never mixes protocols, never mixes SDNN, enforces >= 5.0 ms artifact floor, and never uses
     * unit-dependent CV(ln rMSSD).
     */
    fun evaluateStandardizedHrv(
        readings: List<HrvReading>,
        asOfDate: LocalDate
    ): HrvEvaluationResult {
        if (readings.any { it.metricType == HrvMetricType.SDNN_MS }) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_MIXED_OR_UNSUPPORTED_SDNN",
                explanation = "SDNN readings cannot be mixed with rMSSD for standardized HRV readiness."
            )
        }
        val window28Start = asOfDate.minusDays(27)
        val window7Start = asOfDate.minusDays(6)
        val inWindow = readings.filter { it.date in window28Start..asOfDate }
        val protocols = inWindow.map { it.protocol }.distinct()
        if (protocols.isEmpty()) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_INSUFFICIENT_SAMPLES",
                explanation = "No standardized HRV readings found inside the trailing 28-day window."
            )
        }
        if (protocols.size > 1) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_MIXED_PROTOCOLS",
                explanation = "Multiple measurement postures/protocols detected; standardized baseline requires one uniform protocol."
            )
        }
        if (inWindow.any { !it.valueMs.isFinite() || it.valueMs < Engine19CoachingPolicyRegistry.HRV_MIN_VALID_RMSSD_MS }) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_ARTIFACT_BELOW_FLOOR",
                explanation = "Detected HRV reading below the ${Engine19CoachingPolicyRegistry.HRV_MIN_VALID_RMSSD_MS} ms physical artifact floor."
            )
        }

        val valid28 = inWindow.distinctBy { it.date }.sortedBy { it.date }
        val valid7 = valid28.filter { it.date >= window7Start }
        if (valid7.size < Engine19CoachingPolicyRegistry.HRV_ROLLING_7D_MIN_DAYS) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_INSUFFICIENT_SAMPLES",
                explanation = "Only ${valid7.size} valid reading(s) in the last 7 days; at least ${Engine19CoachingPolicyRegistry.HRV_ROLLING_7D_MIN_DAYS} are required (${Engine19CoachingPolicyRegistry.HRV_ROLLING_7D_PREFERRED_DAYS} preferred)."
            )
        }
        if (valid28.size < Engine19CoachingPolicyRegistry.HRV_BASELINE_28D_MIN_DAYS) {
            return HrvEvaluationResult.InsufficientOrInvalid(
                reasonCode = "HRV_BASELINE_INCOMPLETE",
                explanation = "Only ${valid28.size} valid days in trailing 28-day baseline; need >= ${Engine19CoachingPolicyRegistry.HRV_BASELINE_28D_MIN_DAYS}."
            )
        }

        val ln28 = valid28.map { ln(it.valueMs) }
        val ln7 = valid7.map { ln(it.valueMs) }
        val mean28 = ln28.average()
        val mean7 = ln7.average()
        val variance = ln28.sumOf { (it - mean28) * (it - mean28) } / (ln28.size - 1)
        val sd28 = sqrt(variance.coerceAtLeast(1e-9))
        val z = (mean7 - mean28) / sd28
        return HrvEvaluationResult.Valid(
            protocol = protocols.single(),
            valid7DayCount = valid7.size,
            valid28DayCount = valid28.size,
            meanLn7 = mean7,
            meanLn28 = mean28,
            sdLn28 = sd28,
            deltaLnZScore = z,
            belowSmallestWorthwhileChange = z <= -0.5,
            lowSampleCaveat = valid7.size < Engine19CoachingPolicyRegistry.HRV_ROLLING_7D_PREFERRED_DAYS
        )
    }

    /**
     * Evaluates all 11 non-causal hypotheses independently (Track 7 §§6-7).
     * Never claims validated medical/physiological causation from field telemetry alone.
     */
    fun evaluateDifferentialHypotheses(input: DifferentialContextInput): DifferentialEvaluationResult {
        val a = input.assessment
        val hrvDepressed = input.hrvResult is HrvEvaluationResult.Valid && input.hrvResult.belowSmallestWorthwhileChange
        val conflictingRecovery = input.subjectiveFeltFineDespiteObjectiveAnomaly ||
            (a.objectiveHrOrPaceAnomalyFlag && a.symptomStatus == SymptomStatus.CONFIRMED_NONE && (a.reportedRpe ?: 0) <= 5) ||
            (hrvDepressed && a.recoveryEvidence == RecoveryEvidenceStatus.VERIFIED_ACCEPTABLE && a.stimulusOutcome == StimulusOutcome.STIMULUS_ACHIEVED)

        val isAdverseExecution = a.completion in setOf(SessionCompletionState.ABORTED, SessionCompletionState.PARTIAL) ||
            a.lateRepOrFinishFadeDetected ||
            a.difficultyFromRepOne ||
            a.intensityAdherence == DimensionValue.Measured(IntensityAdherenceBand.EXCESSIVE_ABOVE) ||
            a.objectiveHrOrPaceAnomalyFlag

        val list = mutableListOf<ScoredExplanationHypothesis>()

        // 1. Ordinary Daily Variation
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.ORDINARY_DAILY_VARIATION,
            plausibility = if (isAdverseExecution && input.priorPoorRestedNonConfoundedCount == 0 && !input.postRecoveryWeekNoRebound) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = if (input.priorPoorRestedNonConfoundedCount == 0) listOf("Isolated session without repeated consecutive underperformance.") else emptyList(),
            conflictingEvidence = if (input.priorPoorRestedNonConfoundedCount >= 2) listOf("Pattern repeated across multiple recent sessions.") else emptyList(),
            missingInputs = if (input.sleepsPoorReported == null) listOf("Sleep and daily life stress context") else emptyList()
        )

        // 2. Acute Fatigue
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.ACUTE_FATIGUE,
            plausibility = if (input.recent24To48hHardStressPresent || input.sleepsPoorReported == true) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = buildList {
                if (input.recent24To48hHardStressPresent) add("High training stress occurred within preceding 24 to 48 hours.")
                if (input.sleepsPoorReported == true) add("Athlete reported poor recent sleep.")
            },
            conflictingEvidence = if (!input.recent24To48hHardStressPresent && a.recoveryEvidence == RecoveryEvidenceStatus.VERIFIED_ACCEPTABLE) listOf("No recent heavy session and verified acceptable recovery.") else emptyList(),
            missingInputs = if (input.sleepsPoorReported == null) listOf("Preceding night sleep quality") else emptyList()
        )

        // 3. Accumulated Fatigue / Overreaching
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.ACCUMULATED_FATIGUE_OVERREACHING,
            plausibility = if (input.consecutiveHighRpeEasyCount >= 2 || (hrvDepressed && a.recoveryEvidence == RecoveryEvidenceStatus.ADVERSE_FATIGUE)) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = buildList {
                if (input.consecutiveHighRpeEasyCount >= 2) add("Repeated elevated effort across ${input.consecutiveHighRpeEasyCount} sessions.")
                if (hrvDepressed) add("Standardized 7-day HRV fell below individual smallest worthwhile change.")
            },
            conflictingEvidence = emptyList(),
            missingInputs = if (input.hrvResult !is HrvEvaluationResult.Valid) listOf("Standardized multi-week HRV or full recovery ratings") else emptyList()
        )

        // 4. Excessive Workout Dose
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.EXCESSIVE_WORKOUT_DOSE,
            plausibility = if (a.lateRepOrFinishFadeDetected && !a.difficultyFromRepOne && !a.envHeatConfounded) PlausibilityTier.HIGH else if (a.lateRepOrFinishFadeDetected) PlausibilityTier.MODERATE else PlausibilityTier.LOW,
            supportingEvidence = if (a.lateRepOrFinishFadeDetected) listOf("Started on target then faded sharply in final third or final sets.") else emptyList(),
            conflictingEvidence = if (a.difficultyFromRepOne) listOf("Difficulty began immediately from Rep 1 / Set 1.") else emptyList(),
            missingInputs = emptyList()
        )

        // 5. Incorrect Intensity Calibration
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.INCORRECT_INTENSITY_CALIBRATION,
            plausibility = if ((a.difficultyFromRepOne || a.intensityAdherence == DimensionValue.Measured(IntensityAdherenceBand.BELOW)) && input.priorPoorRestedNonConfoundedCount >= 2 && !a.envHeatConfounded) PlausibilityTier.HIGH else if (a.difficultyFromRepOne) PlausibilityTier.MODERATE else PlausibilityTier.LOW,
            supportingEvidence = if (a.difficultyFromRepOne) listOf("Immediate mismatch from Rep 1 / Set 1.") else emptyList(),
            conflictingEvidence = if (a.envHeatConfounded) listOf("Hot/humid environment explains elevated strain without lower baseline.") else emptyList(),
            missingInputs = if (input.priorPoorRestedNonConfoundedCount < 2) listOf("Multi-session rested non-confounded confirmation") else emptyList()
        )

        // 6. Inadequate Recovery Spacing
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.INADEQUATE_RECOVERY_SPACING,
            plausibility = if (input.spacedBelow48hAfterHeavyLowerOrLong && isAdverseExecution) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = if (input.spacedBelow48hAfterHeavyLowerOrLong) listOf("Session occurred < 48 hours after heavy lower-body or long run load.") else emptyList(),
            conflictingEvidence = emptyList(),
            missingInputs = emptyList()
        )

        // 7. Environmental Effects
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.ENVIRONMENTAL_CONFOUNDING,
            plausibility = if (a.envHeatConfounded || a.envAltitudeOrTerrainConfounded) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = buildList {
                if (a.envHeatConfounded) add("Heat/humidity confounder recorded during execution.")
                if (a.envAltitudeOrTerrainConfounded) add("Altitude/hilly/treadmill terrain confounder present.")
            },
            conflictingEvidence = emptyList(),
            missingInputs = emptyList()
        )

        // 8. Fueling / Hydration Limitation
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.FUELING_HYDRATION_LIMITATION,
            plausibility = if (input.durationOver90MinWithoutFueling && a.lateRepOrFinishFadeDetected) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = if (input.durationOver90MinWithoutFueling) listOf("Late fade beyond 90 minutes without intra-session fueling.") else emptyList(),
            conflictingEvidence = emptyList(),
            missingInputs = if (!input.durationOver90MinWithoutFueling) listOf("Intra-session carbohydrate and fluid log") else emptyList()
        )

        // 9. Measurement Error
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.MEASUREMENT_ERROR,
            plausibility = if (a.telemetryCorruptedOrQuarantined || input.ercOrPpgCadenceLockDetected) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = if (a.telemetryCorruptedOrQuarantined || input.ercOrPpgCadenceLockDetected) listOf("Cadence-locked optical HR or GNSS anomaly detected in telemetry stream.") else emptyList(),
            conflictingEvidence = emptyList(),
            missingInputs = emptyList()
        )

        // 10. Reduced Training Consistency
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.REDUCED_TRAINING_CONSISTENCY,
            plausibility = if (input.recent28DayConsistencyLow) PlausibilityTier.HIGH else PlausibilityTier.LOW,
            supportingEvidence = if (input.recent28DayConsistencyLow) listOf("Irregular training attendance over trailing 28 to 42 days.") else emptyList(),
            conflictingEvidence = emptyList(),
            missingInputs = emptyList()
        )

        // 11. Genuine Performance Deterioration (strictly gated post-deload without illness/pain!)
        list += ScoredExplanationHypothesis(
            kind = DifferentialHypothesisKind.GENUINE_PERFORMANCE_DETERIORATION,
            plausibility = if (input.postRecoveryWeekNoRebound && input.priorPoorRestedNonConfoundedCount >= 2 && a.symptomStatus == SymptomStatus.CONFIRMED_NONE && !a.envHeatConfounded) PlausibilityTier.MODERATE else PlausibilityTier.LOW,
            supportingEvidence = if (input.postRecoveryWeekNoRebound) listOf("Underperformance persisted even after a recovery week.") else emptyList(),
            conflictingEvidence = listOf("Life stress or subclinical illness cannot be excluded from app telemetry alone."),
            missingInputs = listOf("Controlled rested follow-up after verified recovery")
        )

        val patternSummary = buildString {
            append("Pattern (${a.discipline}): completion=${a.completion.name}")
            if (a.lateRepOrFinishFadeDetected) append(", late fade observed")
            if (a.difficultyFromRepOne) append(", high strain from start")
            if (a.envHeatConfounded) append(", heat confounded")
            if (conflictingRecovery) append(", subjective vs objective conflict flagged")
            append(".")
        }

        return DifferentialEvaluationResult(
            hypotheses = list.sortedWith(compareBy<ScoredExplanationHypothesis> { it.plausibility.ordinal }.thenBy { it.kind.ordinal }),
            conflictingRecoveryEvidenceFlag = conflictingRecovery,
            descriptivePattern = patternSummary
        )
    }
}
