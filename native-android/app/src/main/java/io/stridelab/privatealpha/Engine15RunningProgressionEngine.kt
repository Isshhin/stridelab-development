package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

internal enum class RunningWeeklyMode {
    INCREASE,
    MAINTAIN,
    REDISTRIBUTE,
    CUTBACK,
    REBUILD,
    REDUCE
}

internal enum class StressorStepClass {
    NONE,
    MINOR_COORDINATED,
    MAJOR_STEP
}

internal enum class RunningStressorAxis(val isLowerBodyAxis: Boolean) {
    WEEKLY_DISTANCE(true),
    WEEKLY_DURATION(true),
    RUNNING_FREQUENCY(true),
    LONG_RUN_R30(true),
    THRESHOLD_TEMPO_WORK(true),
    SEVERE_VO2_WORK(true),
    NEUROMUSCULAR_EXPOSURE(false),
    EMBEDDED_RACE_SPECIFIC_LR(true),
    STRUCTURAL_BOUT_DENSITY(false)
}

internal data class RunningVolumeDeltaDecision(
    val mode: RunningWeeklyMode,
    val deltaMeters: Double,
    val nextWeekMeters: Double,
    val routedToConfirmedZeroStarter: Boolean = false,
    val reasonCode: String
)

internal data class DatedRunExposureAnchor(
    val date: LocalDate,
    val anchorDistanceMeters: Double,
    val isObserved: Boolean
) {
    init {
        require(anchorDistanceMeters.isFinite() && anchorDistanceMeters >= 0.0)
    }
}

internal enum class Rolling30Status {
    NOT_APPLICABLE_BELOW_FLOOR,
    WITHIN_GUARDRAIL,
    ABOVE_GUARDRAIL_SIGNAL
}

internal data class Rolling30Evaluation(
    val targetDate: LocalDate,
    val dMax30Meters: Double?,
    val plannedUpperMeters: Double,
    val r30Ratio: Double?,
    val status: Rolling30Status,
    val exceedsGuardrailL4B: Boolean,
    val observationalRiskSignalL7: Boolean
)

internal data class RunningWeekTransitionInput(
    val baseWeeklyMeters: Double,
    val nextWeeklyMeters: Double,
    val baseWeeklyDurationSeconds: Double? = null,
    val nextWeeklyDurationSeconds: Double? = null,
    val deltaRunDays: Int = 0,
    val longRunR30: Double? = null,
    val thresholdWorkRelChange: Double = 0.0,
    val isUnfamiliarThresholdFamily: Boolean = false,
    val severeWorkRelChange: Double = 0.0,
    val isUnfamiliarSevereFamily: Boolean = false,
    val neuromuscularAddedStridesOrHills: Int = 0,
    val isNewNeuromuscularFamily: Boolean = false,
    val embeddedRacePaceRelChange: Double = 0.0,
    val isFirstEmbeddedRacePace: Boolean = false,
    val structuralBoutShiftAtZeroWork: Boolean = false,
    val structuralCompressionWithWorkIncrease: Boolean = false
)

internal data class MultiStressorEvaluation(
    val classifications: Map<RunningStressorAxis, StressorStepClass>,
    val majorCount: Int,
    val minorCount: Int,
    val valid: Boolean,
    val recommendedRemediation: String?
)

internal enum class ReturnExitState {
    ACTIVE_CONSISTENT,
    CONDITIONAL_EXIT_PENDING_OBSERVED_VERIFICATION,
    REMAIN_IN_REBUILD
}

internal data class ReturnExitEvaluation(
    val state: ReturnExitState,
    val pathTriggered: String?,
    val rolling28dRatio: Double,
    val currentWeekRatio: Double,
    val reasonCode: String
)

/**
 * M2-D Running Progression Intelligence:
 * - MOD-12 6-mode weekly volume delta with confirmed-zero bypass and headroom precedence
 * - MOD-10 exact ISO [d-30 .. d-1] single-session r_30 evaluation (D_lo for projected anchors)
 * - POL-027 MultiStressorPolicyV1 9-axis weekly transition guard (<= 1 MAJOR, <= 3 MINOR)
 * - POL-033 / POL-057 gapless 6-bucket return ramp & POL-034 dual conditional return exit
 */
internal object Engine15RunningProgressionEngine {

    fun computeWeeklyVolumeDelta(
        mode: RunningWeeklyMode,
        baseWeeklyMeters: Double,
        runDays: Int,
        confirmedZeroHistory: Boolean = false,
        bindingCeilingMeters: Double? = null,
        growthRate: Double? = null,
        cutbackFraction: Double = Engine15RunningPolicyRegistry.POL_031_VOLUME_CUTBACK_FRACTION,
        rebuildDeltaMeters: Double = 0.0,
        reduceDeltaMeters: Double = 0.0
    ): RunningVolumeDeltaDecision {
        require(baseWeeklyMeters.isFinite() && baseWeeklyMeters >= 0.0) { "baseWeeklyMeters must be finite >= 0" }
        require(runDays in 0..7) { "runDays must be in 0..7" }
        if (confirmedZeroHistory || baseWeeklyMeters == 0.0) {
            return RunningVolumeDeltaDecision(
                mode = RunningWeeklyMode.REBUILD,
                deltaMeters = 0.0,
                nextWeekMeters = 0.0,
                routedToConfirmedZeroStarter = true,
                reasonCode = "POL_055_CONFIRMED_ZERO_TIME_PRIMARY"
            )
        }
        return when (mode) {
            RunningWeeklyMode.MAINTAIN, RunningWeeklyMode.REDISTRIBUTE -> RunningVolumeDeltaDecision(
                mode = mode,
                deltaMeters = 0.0,
                nextWeekMeters = baseWeeklyMeters,
                reasonCode = "MOD_12_ZERO_FORCED_GROWTH"
            )
            RunningWeeklyMode.CUTBACK -> {
                val frac = cutbackFraction.coerceIn(0.10, 0.50)
                val delta = -(baseWeeklyMeters * frac)
                RunningVolumeDeltaDecision(
                    mode = RunningWeeklyMode.CUTBACK,
                    deltaMeters = delta,
                    nextWeekMeters = (baseWeeklyMeters + delta).coerceAtLeast(0.0),
                    reasonCode = "MOD_12_PLANNED_CUTBACK"
                )
            }
            RunningWeeklyMode.REDUCE -> {
                val delta = -reduceDeltaMeters.coerceAtLeast(0.0).coerceAtMost(baseWeeklyMeters)
                RunningVolumeDeltaDecision(
                    mode = RunningWeeklyMode.REDUCE,
                    deltaMeters = delta,
                    nextWeekMeters = (baseWeeklyMeters + delta).coerceAtLeast(0.0),
                    reasonCode = "MOD_12_EXPLICIT_REDUCTION"
                )
            }
            RunningWeeklyMode.REBUILD -> {
                val cappedStep = minOf(
                    rebuildDeltaMeters.coerceAtLeast(0.0),
                    Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_ABS_CAP_M
                )
                RunningVolumeDeltaDecision(
                    mode = RunningWeeklyMode.REBUILD,
                    deltaMeters = cappedStep,
                    nextWeekMeters = baseWeeklyMeters + cappedStep,
                    reasonCode = "MOD_12_REBUILD_RAMP"
                )
            }
            RunningWeeklyMode.INCREASE -> {
                val isLowVol = baseWeeklyMeters < Engine15RunningPolicyRegistry.POL_024_LOW_VOL_THRESHOLD_M
                val defaultAlpha = if (isLowVol) 0.08 else 0.055
                val alpha = (growthRate ?: defaultAlpha).coerceIn(
                    0.0,
                    if (isLowVol) Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_REL_CAP
                    else Engine15RunningPolicyRegistry.POL_024_EST_MINOR_REL_CAP
                )
                val deltaMax = if (isLowVol) {
                    minOf(
                        baseWeeklyMeters * Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_REL_CAP,
                        Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_ABS_CAP_M
                    )
                } else {
                    minOf(
                        baseWeeklyMeters * Engine15RunningPolicyRegistry.POL_024_EST_MINOR_REL_CAP,
                        Engine15RunningPolicyRegistry.POL_024_EST_MINOR_ABS_CAP_M
                    )
                }
                val deltaMin = if (runDays <= 3) {
                    Engine15RunningPolicyRegistry.POL_024_MIN_WEEK_STEP_LOW_DAYS_M
                } else {
                    Engine15RunningPolicyRegistry.POL_024_MIN_WEEK_STEP_HIGH_DAYS_M
                }
                val headroom = bindingCeilingMeters?.let { it - baseWeeklyMeters }
                if (headroom != null && headroom < 0.0) {
                    return RunningVolumeDeltaDecision(
                        mode = RunningWeeklyMode.REDUCE,
                        deltaMeters = headroom,
                        nextWeekMeters = (baseWeeklyMeters + headroom).coerceAtLeast(0.0),
                        reasonCode = "MOD_12_CEILING_EXCEEDED_REDUCE"
                    )
                }
                val avail = headroom?.let { min(it, deltaMax) } ?: deltaMax
                val desired = baseWeeklyMeters * alpha
                val finalDelta = when {
                    avail <= 0.0 -> 0.0
                    avail >= deltaMin -> max(min(desired, avail), deltaMin)
                    else -> min(desired, avail) // deltaMin NEVER overrides a tighter ceiling
                }
                RunningVolumeDeltaDecision(
                    mode = if (finalDelta > 0.0) RunningWeeklyMode.INCREASE else RunningWeeklyMode.MAINTAIN,
                    deltaMeters = finalDelta,
                    nextWeekMeters = baseWeeklyMeters + finalDelta,
                    reasonCode = if (finalDelta > 0.0) "MOD_12_COORDINATED_INCREASE" else "MOD_12_HEADROOM_HOLD"
                )
            }
        }
    }

    /**
     * MOD-10: Exact ISO calendar-date 30-day rolling single-session ratio [d - 30 .. d - 1].
     * Uses observed runs on or before anchor and conservative lower bound (D_lo) for prior
     * projected weeks; skips dates outside the 30-day window.
     */
    fun evaluateRolling30DayRatio(
        targetDate: LocalDate,
        plannedUpperMeters: Double,
        precedingAnchors: List<DatedRunExposureAnchor>,
        userOverrideUpTo90dMaxMeters: Double? = null
    ): Rolling30Evaluation {
        require(plannedUpperMeters.isFinite() && plannedUpperMeters >= 0.0) { "plannedUpperMeters must be finite >= 0" }
        val windowStart = targetDate.minusDays(Engine15RunningPolicyRegistry.POL_022_ROLLING_WINDOW_DAYS)
        val windowEndInclusive = targetDate.minusDays(1)
        val dMax30 = precedingAnchors
            .asSequence()
            .filter { !it.date.isBefore(windowStart) && !it.date.isAfter(windowEndInclusive) }
            .maxOfOrNull { it.anchorDistanceMeters }
            ?: 0.0

        if (dMax30 < Engine15RunningPolicyRegistry.POL_023_R30_BASELINE_FLOOR_M) {
            return Rolling30Evaluation(
                targetDate = targetDate,
                dMax30Meters = dMax30,
                plannedUpperMeters = plannedUpperMeters,
                r30Ratio = null,
                status = Rolling30Status.NOT_APPLICABLE_BELOW_FLOOR,
                exceedsGuardrailL4B = false,
                observationalRiskSignalL7 = false
            )
        }
        val ratio = plannedUpperMeters / dMax30
        val aboveCap = ratio > Engine15RunningPolicyRegistry.POL_022_SINGLE_SESSION_R30_CAP + 1e-9
        val exceedsOverride = userOverrideUpTo90dMaxMeters == null ||
            plannedUpperMeters > userOverrideUpTo90dMaxMeters + 1e-6
        return Rolling30Evaluation(
            targetDate = targetDate,
            dMax30Meters = dMax30,
            plannedUpperMeters = plannedUpperMeters,
            r30Ratio = ratio,
            status = if (aboveCap) Rolling30Status.ABOVE_GUARDRAIL_SIGNAL else Rolling30Status.WITHIN_GUARDRAIL,
            exceedsGuardrailL4B = aboveCap && exceedsOverride,
            observationalRiskSignalL7 = aboveCap
        )
    }

    /** POL-027 MultiStressorPolicyV1 across all 9 running axes. */
    fun evaluateMultiStressorTransition(input: RunningWeekTransitionInput): MultiStressorEvaluation {
        val classes = LinkedHashMap<RunningStressorAxis, StressorStepClass>(9)

        // Axis 1: Weekly Distance
        val volDelta = input.nextWeeklyMeters - input.baseWeeklyMeters
        val volRel = if (input.baseWeeklyMeters > 0.0) volDelta / input.baseWeeklyMeters else 0.0
        val lowVol = input.baseWeeklyMeters < Engine15RunningPolicyRegistry.POL_024_LOW_VOL_THRESHOLD_M
        classes[RunningStressorAxis.WEEKLY_DISTANCE] = when {
            volDelta <= 1e-6 -> StressorStepClass.NONE
            lowVol && volRel <= Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_REL_CAP + 1e-6 &&
                volDelta <= Engine15RunningPolicyRegistry.POL_024_LOW_VOL_MINOR_ABS_CAP_M + 1e-6 ->
                StressorStepClass.MINOR_COORDINATED
            !lowVol && volRel <= Engine15RunningPolicyRegistry.POL_024_EST_MINOR_REL_CAP + 1e-6 &&
                volDelta <= Engine15RunningPolicyRegistry.POL_024_EST_MINOR_ABS_CAP_M + 1e-6 ->
                StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.MAJOR_STEP
        }

        // Axis 2: Weekly Duration (when TIME_PRIMARY)
        val durClass = if (input.baseWeeklyDurationSeconds != null &&
            input.nextWeeklyDurationSeconds != null &&
            input.baseWeeklyDurationSeconds > 0.0
        ) {
            val durDelta = input.nextWeeklyDurationSeconds - input.baseWeeklyDurationSeconds
            val durRel = durDelta / input.baseWeeklyDurationSeconds
            when {
                durDelta <= 1e-6 -> StressorStepClass.NONE
                durRel <= 0.10 + 1e-6 && durDelta <= 900.0 + 1e-6 -> StressorStepClass.MINOR_COORDINATED
                else -> StressorStepClass.MAJOR_STEP
            }
        } else StressorStepClass.NONE
        classes[RunningStressorAxis.WEEKLY_DURATION] = durClass

        // Axis 3: Frequency
        classes[RunningStressorAxis.RUNNING_FREQUENCY] =
            if (input.deltaRunDays >= 1) StressorStepClass.MAJOR_STEP else StressorStepClass.NONE

        // Axis 4: Long Run r30
        val r30 = input.longRunR30
        classes[RunningStressorAxis.LONG_RUN_R30] = when {
            r30 == null || r30 <= 1.0 + 1e-9 -> StressorStepClass.NONE
            r30 <= Engine15RunningPolicyRegistry.POL_025_LR_MINOR_UPPER_R30 + 1e-9 -> StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.MAJOR_STEP
        }

        // Axis 5: Threshold / Tempo
        classes[RunningStressorAxis.THRESHOLD_TEMPO_WORK] = when {
            input.isUnfamiliarThresholdFamily -> StressorStepClass.MAJOR_STEP
            input.thresholdWorkRelChange <= 1e-6 && !input.structuralBoutShiftAtZeroWork -> StressorStepClass.NONE
            input.thresholdWorkRelChange <= Engine15RunningPolicyRegistry.POL_026_QUALITY_MINOR_REL_CAP + 1e-6 ->
                StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.MAJOR_STEP
        }

        // Axis 6: Severe / VO2max
        classes[RunningStressorAxis.SEVERE_VO2_WORK] = when {
            input.isUnfamiliarSevereFamily -> StressorStepClass.MAJOR_STEP
            input.severeWorkRelChange <= 1e-6 -> StressorStepClass.NONE
            input.severeWorkRelChange <= Engine15RunningPolicyRegistry.POL_026_QUALITY_MINOR_REL_CAP + 1e-6 ->
                StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.MAJOR_STEP
        }

        // Axis 7: Neuromuscular
        classes[RunningStressorAxis.NEUROMUSCULAR_EXPOSURE] = when {
            input.isNewNeuromuscularFamily -> StressorStepClass.MAJOR_STEP
            input.neuromuscularAddedStridesOrHills <= 0 -> StressorStepClass.NONE
            else -> StressorStepClass.MINOR_COORDINATED
        }

        // Axis 8: Embedded Race-Specific Long Run
        classes[RunningStressorAxis.EMBEDDED_RACE_SPECIFIC_LR] = when {
            input.isFirstEmbeddedRacePace -> StressorStepClass.MAJOR_STEP
            input.embeddedRacePaceRelChange <= 1e-6 -> StressorStepClass.NONE
            input.embeddedRacePaceRelChange <= 0.10 + 1e-6 -> StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.MAJOR_STEP
        }

        // Axis 9: Structural Rep-Length / Density Step
        classes[RunningStressorAxis.STRUCTURAL_BOUT_DENSITY] = when {
            input.structuralCompressionWithWorkIncrease -> StressorStepClass.MAJOR_STEP
            input.structuralBoutShiftAtZeroWork -> StressorStepClass.MINOR_COORDINATED
            else -> StressorStepClass.NONE
        }

        val majorCount = classes.values.count { it == StressorStepClass.MAJOR_STEP }
        val minorCount = classes.values.count { it == StressorStepClass.MINOR_COORDINATED }
        val valid = majorCount <= Engine15RunningPolicyRegistry.POL_027_MAX_MAJOR_STEPS_PER_WEEK &&
            minorCount <= Engine15RunningPolicyRegistry.POL_027_MAX_MINOR_STEPS_PER_WEEK

        val remediation = if (!valid) {
            when {
                majorCount > 1 ->
                    "Stagger MAJOR progression steps across weeks (e.g., hold primary quality at MAINTAIN when ramping long-run or weekly volume)."
                else ->
                    "Too many simultaneous minor stressors ($minorCount > ${Engine15RunningPolicyRegistry.POL_027_MAX_MINOR_STEPS_PER_WEEK}); hold secondary axes steady."
            }
        } else null

        return MultiStressorEvaluation(
            classifications = classes,
            majorCount = majorCount,
            minorCount = minorCount,
            valid = valid,
            recommendedRemediation = remediation
        )
    }

    /** POL-033 / POL-057 gapless 6-bucket return entry fraction for running. */
    fun returnEntryFractionForGapDays(confirmedGapDays: Int, illnessOrInjury: Boolean = false): Double {
        require(confirmedGapDays >= 0) { "confirmedGapDays must be >= 0" }
        val idx = Engine15RunningPolicyRegistry.POL_057_RETURN_ENTRY_FRACTIONS
            .indexOfFirst { (range, _) -> confirmedGapDays in range }
            .takeIf { it >= 0 }
            ?: Engine15RunningPolicyRegistry.POL_057_RETURN_ENTRY_FRACTIONS.lastIndex
        val conservativeIdx = if (illnessOrInjury) {
            min(idx + 1, Engine15RunningPolicyRegistry.POL_057_RETURN_ENTRY_FRACTIONS.lastIndex)
        } else idx
        return Engine15RunningPolicyRegistry.POL_057_RETURN_ENTRY_FRACTIONS[conservativeIdx].second
    }

    /** POL-034 dual conditional return exit (Track 4 Section 8, fixing Scenario 11 W7 bug). */
    fun evaluateReturnExit(
        priorSustainableWeeklyMeters: Double,
        rolling4WeekMeters: List<Double>,
        currentWeekMeters: Double,
        consecutiveCompletedRebuildNonCutbackWeeks: Int,
        consecutiveSteadyObservedWeeks: Int,
        hasEscalatingSymptoms: Boolean,
        evaluatedOnObservedLogsOnly: Boolean
    ): ReturnExitEvaluation {
        require(priorSustainableWeeklyMeters.isFinite() && priorSustainableWeeklyMeters > 0.0)
        require(rolling4WeekMeters.size == 4 && rolling4WeekMeters.all { it.isFinite() && it >= 0.0 })
        require(currentWeekMeters.isFinite() && currentWeekMeters >= 0.0)

        val avg28d = rolling4WeekMeters.average()
        val rollingRatio = avg28d / priorSustainableWeeklyMeters
        val currentRatio = currentWeekMeters / priorSustainableWeeklyMeters

        if (hasEscalatingSymptoms) {
            return ReturnExitEvaluation(
                state = ReturnExitState.REMAIN_IN_REBUILD,
                pathTriggered = null,
                rolling28dRatio = rollingRatio,
                currentWeekRatio = currentRatio,
                reasonCode = "POL_034_SYMPTOMS_BLOCK_EXIT"
            )
        }

        val pathAMet = rollingRatio >= Engine15RunningPolicyRegistry.POL_034_PATH_A_ROLLING_28D_FRACTION - 1e-6 &&
            currentRatio >= Engine15RunningPolicyRegistry.POL_034_PATH_A_CURRENT_WEEK_FRACTION - 1e-6 &&
            consecutiveCompletedRebuildNonCutbackWeeks >= Engine15RunningPolicyRegistry.POL_034_PATH_A_MIN_REBUILD_WEEKS

        val pathBMet = consecutiveSteadyObservedWeeks >= Engine15RunningPolicyRegistry.POL_034_PATH_B_MIN_STEADY_WEEKS

        return when {
            pathAMet || pathBMet -> ReturnExitEvaluation(
                state = if (evaluatedOnObservedLogsOnly) {
                    ReturnExitState.ACTIVE_CONSISTENT
                } else {
                    ReturnExitState.CONDITIONAL_EXIT_PENDING_OBSERVED_VERIFICATION
                },
                pathTriggered = if (pathAMet) "PATH_A_PRIOR_RESTORATION" else "PATH_B_NEW_STEADY_BASELINE",
                rolling28dRatio = rollingRatio,
                currentWeekRatio = currentRatio,
                reasonCode = if (evaluatedOnObservedLogsOnly) "POL_034_EXIT_VERIFIED" else "POL_034_CONDITIONAL_EXIT_PENDING"
            )
            else -> ReturnExitEvaluation(
                state = ReturnExitState.REMAIN_IN_REBUILD,
                pathTriggered = null,
                rolling28dRatio = rollingRatio,
                currentWeekRatio = currentRatio,
                reasonCode = "POL_034_BELOW_EXIT_CRITERIA"
            )
        }
    }
}
