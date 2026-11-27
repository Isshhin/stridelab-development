package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Nine bounded strength progression operators (Track 6 §4.1 / POL-060).
 */
internal enum class StrengthProgressionOperator {
    CONSOLIDATE_MAINTAIN,
    PROGRESS_ONE_SET,
    PROGRESS_PARTIAL_SETS,
    PROGRESS_ALL_SETS,
    PROGRESS_LOAD_RESET_REPS,
    PROGRESS_SET_VOLUME,
    REDUCE_WORKING_SETS,
    REDUCE_LOAD,
    SUBSTITUTE_MOVEMENT
}

/**
 * Nine ordered non-equivalent levers when fixed equipment steps exceed safe threshold (POL-045 / MD-03).
 */
internal enum class CoarseBridgeOption {
    EXTEND_REP_CEILING,
    MICROLOAD_IF_AVAILABLE,
    ALTERNATE_EQUIPMENT_WITH_RECALIBRATION,
    CONSOLIDATE_CONTROL,
    CONTROLLED_LOWER_REP_TRIAL,
    ADD_WORKING_SET,
    EXPLICIT_TEMPO_OR_PAUSE_VARIANT,
    LEVERAGE_VARIANT,
    UNILATERAL_NEW_EXERCISE_DISCOVERY
}

internal data class CoarseIncrementBridgePlan(
    val nextLoadStepRatio: Double,
    val orderedOptions: List<CoarseBridgeOption>,
    val selectedOption: CoarseBridgeOption,
    val requiresRecalibrationTag: Boolean,
    val coachingNote: String
)

internal enum class StrengthInterruptionBucket(
    val loadRetentionRange: ClosedFloatingPointRange<Double>,
    val setRetentionRange: ClosedFloatingPointRange<Double>,
    val defaultLoadFactor: Double,
    val defaultSetFactor: Double,
    val requiresRirRediscovery: Boolean
) {
    DAYS_0_TO_6(1.0..1.0, 1.0..1.0, 1.0, 1.0, false),
    DAYS_7_TO_13(0.90..0.95, 0.70..0.80, 0.925, 0.75, false),
    DAYS_14_TO_27(0.80..0.90, 0.60..0.70, 0.85, 0.65, false),
    DAYS_28_TO_55(0.70..0.80, 0.50..0.60, 0.75, 0.55, false),
    DAYS_56_TO_83(0.60..0.70, 0.40..0.60, 0.65, 0.50, true),
    DAYS_84_PLUS(0.50..0.65, 0.35..0.55, 0.60, 0.50, true);

    companion object {
        fun fromConfirmedBreakDays(days: Int): StrengthInterruptionBucket = when {
            days <= Engine16StrengthPolicyRegistry.RETURN_BUCKET_BRIEF_MAX_DAYS -> DAYS_0_TO_6
            days <= Engine16StrengthPolicyRegistry.RETURN_BUCKET_SHORT_MAX_DAYS -> DAYS_7_TO_13
            days <= Engine16StrengthPolicyRegistry.RETURN_BUCKET_MODERATE_MAX_DAYS -> DAYS_14_TO_27
            days <= Engine16StrengthPolicyRegistry.RETURN_BUCKET_EXTENDED_MAX_DAYS -> DAYS_28_TO_55
            days <= Engine16StrengthPolicyRegistry.RETURN_BUCKET_LONG_MAX_DAYS -> DAYS_56_TO_83
            else -> DAYS_84_PLUS
        }
    }
}

internal data class StrengthReturnReentryDirective(
    val bucket: StrengthInterruptionBucket,
    val loadMultiplier: Double,
    val setMultiplier: Double,
    val maxStartingSetsPerExercise: Int?,
    val targetRir: IntRange,
    val disallowNovelEccentrics: Boolean,
    val evidenceGatedExitRequired: Boolean
)

internal data class StrengthSetExecutionSummary(
    val completedReps: Int,
    val loadKg: Double?,
    val reportedRir: Int? = null,
    val techniqueQuality: TechniqueQuality = TechniqueQuality.UNOBSERVED
)

internal data class StrengthProgressionInput(
    val exercise: StrengthExerciseMetadata,
    val objective: StrengthObjective,
    val currentSets: Int,
    val currentTargetReps: Int,
    val repRange: IntRange,
    val targetRir: IntRange = Engine16StrengthPolicyRegistry.defaultsFor(objective, 1).targetRir,
    val completedSets: List<StrengthSetExecutionSummary>,
    val consecutiveCompletedCleanSessions: Int = 1,
    val verifiedSubsequentContinuity: Boolean = true,
    val techniqueProvenance: TechniqueProvenance = TechniqueProvenance.UNOBSERVED_TECHNIQUE,
    val symptomStatus: SymptomSafetyStatus = SymptomSafetyStatus.MISSING_UNREPORTED,
    val confirmedStrengthBreakDays: Int = 0,
    val runningMajorStepActive: Boolean = false,
    val fatigueTriggeredDeload: Boolean = false,
    val equipmentBlockedOrPainful: Boolean = false,
    val minAvailableIncrementKg: Double = exercise.minIncrementKg,
    val executionFactors: Set<ExecutionFactor> = emptySet(),
    val calibrationResult: StrengthCalibrationResult? = null,
    val currentWeeklyDirectMuscleSets: Int = 6,
    val maxWeeklyDirectMuscleSets: Int = Engine16StrengthPolicyRegistry.DEFAULT_MAX_WEEKLY_DIRECT_SETS_PER_MUSCLE
)

internal data class StrengthProgressionDecision(
    val operator: StrengthProgressionOperator,
    val perSetTargetReps: List<Int>,
    val recommendedSets: Int,
    val recommendedLoadKg: Double?,
    val targetRir: IntRange,
    val executionConflictDetected: Boolean = false,
    val coarseBridgePlan: CoarseIncrementBridgePlan? = null,
    val returnReentry: StrengthReturnReentryDirective? = null,
    val uncertaintyTags: Set<String> = emptySet(),
    val rationale: String
)

/**
 * Pure deterministic Engine 1.6 progressive overload engine implementing:
 * - 9 bounded progression operators (POL-060)
 * - Coarse-increment bridging (POL-045 / MD-03)
 * - Minimal-input progression without false verification claims (POL-046 / MD-04)
 * - Execution-conflict guard & clinical-free symptom holdbacks
 * - Gapless 6-bucket return reentry (POL-033 / POL-058 / MD-13)
 * - Running MAJOR_STEP lower-body hold (POL-059 / MD-15)
 * - RP-style exercise-distribution-preserving set volume allocation (batch-6)
 */
internal object Engine16StrengthProgressionEngine {

    fun evaluateReturnReentry(confirmedStrengthBreakDays: Int): StrengthReturnReentryDirective {
        require(confirmedStrengthBreakDays >= 0) { "confirmedStrengthBreakDays must be >= 0" }
        val bucket = StrengthInterruptionBucket.fromConfirmedBreakDays(confirmedStrengthBreakDays)
        return when (bucket) {
            StrengthInterruptionBucket.DAYS_0_TO_6 -> StrengthReturnReentryDirective(
                bucket = bucket,
                loadMultiplier = 1.0,
                setMultiplier = 1.0,
                maxStartingSetsPerExercise = null,
                targetRir = 2..3,
                disallowNovelEccentrics = false,
                evidenceGatedExitRequired = false
            )
            StrengthInterruptionBucket.DAYS_7_TO_13 -> StrengthReturnReentryDirective(
                bucket = bucket,
                loadMultiplier = bucket.defaultLoadFactor,
                setMultiplier = bucket.defaultSetFactor,
                maxStartingSetsPerExercise = null,
                targetRir = 3..4,
                disallowNovelEccentrics = false,
                evidenceGatedExitRequired = true
            )
            StrengthInterruptionBucket.DAYS_14_TO_27,
            StrengthInterruptionBucket.DAYS_28_TO_55 -> StrengthReturnReentryDirective(
                bucket = bucket,
                loadMultiplier = bucket.defaultLoadFactor,
                setMultiplier = bucket.defaultSetFactor,
                maxStartingSetsPerExercise = null,
                targetRir = 3..4,
                disallowNovelEccentrics = true,
                evidenceGatedExitRequired = true
            )
            StrengthInterruptionBucket.DAYS_56_TO_83,
            StrengthInterruptionBucket.DAYS_84_PLUS -> StrengthReturnReentryDirective(
                bucket = bucket,
                loadMultiplier = bucket.defaultLoadFactor,
                setMultiplier = bucket.defaultSetFactor,
                maxStartingSetsPerExercise = 2,
                targetRir = 3..4,
                disallowNovelEccentrics = true,
                evidenceGatedExitRequired = true
            )
        }
    }

    /**
     * RP-style distribution-preserving set reallocation within a muscle group (batch-6):
     * preserves existing proportional distribution across exercises before adding/removing sets.
     */
    fun allocateMuscleGroupSets(
        priorSetsByExercise: Map<String, Int>,
        deltaSets: Int
    ): Map<String, Int> {
        if (priorSetsByExercise.isEmpty()) return emptyMap()
        if (deltaSets == 0) return priorSetsByExercise.toMap()
        val orderedKeys = priorSetsByExercise.keys.sorted()
        val result = priorSetsByExercise.toMutableMap()
        val totalPrior = priorSetsByExercise.values.sum().coerceAtLeast(1)

        if (deltaSets > 0) {
            var remaining = deltaSets
            while (remaining > 0) {
                val targetKey = orderedKeys.minByOrNull { key ->
                    val current = result.getValue(key)
                    val ideal = priorSetsByExercise.getValue(key).toDouble() / totalPrior *
                        (totalPrior + deltaSets)
                    current - ideal
                }!!
                result[targetKey] = result.getValue(targetKey) + 1
                remaining--
            }
        } else {
            var remaining = -deltaSets
            while (remaining > 0) {
                val eligible = orderedKeys.filter { result.getValue(it) > 1 }
                if (eligible.isEmpty()) break
                val targetKey = eligible.maxByOrNull { key ->
                    val current = result.getValue(key)
                    val targetTotal = max(orderedKeys.size, totalPrior + deltaSets)
                    val ideal = priorSetsByExercise.getValue(key).toDouble() / totalPrior * targetTotal
                    current - ideal
                }!!
                result[targetKey] = result.getValue(targetKey) - 1
                remaining--
            }
        }
        return result
    }

    fun evaluate(input: StrengthProgressionInput): StrengthProgressionDecision {
        val currentLoad = input.completedSets.mapNotNull { it.loadKg?.takeIf { k -> k > 0.0 } }.maxOrNull()
        val sets = input.currentSets.coerceAtLeast(1)
        val uniformCurrentReps = List(sets) { input.currentTargetReps }
        val uncertainty = linkedSetOf<String>()
        if (input.techniqueProvenance == TechniqueProvenance.UNOBSERVED_TECHNIQUE) {
            uncertainty += "UNOBSERVED_TECHNIQUE"
        }
        if (input.completedSets.none { it.reportedRir != null }) {
            uncertainty += "RIR_UNREPORTED"
        }
        if (input.symptomStatus == SymptomSafetyStatus.MISSING_UNREPORTED) {
            uncertainty += "RECOVERY_MISSING_UNREPORTED"
        }

        // 1. Symptom or equipment substitution gate (never diagnoses clinical conditions).
        if (input.symptomStatus == SymptomSafetyStatus.CONCERNING_SYMPTOM_FLAG || input.equipmentBlockedOrPainful) {
            val op = if (input.equipmentBlockedOrPainful) {
                StrengthProgressionOperator.SUBSTITUTE_MOVEMENT
            } else {
                StrengthProgressionOperator.REDUCE_LOAD
            }
            val reducedLoad = currentLoad?.let { snapDown(it * (1.0 - Engine16StrengthPolicyRegistry.BACKOFF_LOAD_MAX_FRACTION), input.minAvailableIncrementKg) }
            return StrengthProgressionDecision(
                operator = op,
                perSetTargetReps = List(max(1, sets - 1)) { input.repRange.first },
                recommendedSets = max(1, sets - 1),
                recommendedLoadKg = reducedLoad,
                targetRir = 3..4,
                uncertaintyTags = uncertainty + "SYMPTOM_OR_ACCESS_HOLDBACK",
                rationale = if (op == StrengthProgressionOperator.SUBSTITUTE_MOVEMENT) {
                    "Switch to an accessible, comfortable variation and recalibrate at 3 to 4 RIR."
                } else {
                    "Back off load and sets conservatively until symptoms settle."
                }
            )
        }

        // 2. Gapless strength interruption return curve (POL-033 / POL-058).
        if (input.confirmedStrengthBreakDays > Engine16StrengthPolicyRegistry.RETURN_BUCKET_BRIEF_MAX_DAYS) {
            val reentry = evaluateReturnReentry(input.confirmedStrengthBreakDays)
            val reentrySets = reentry.maxStartingSetsPerExercise?.let { min(it, sets) }
                ?: max(1, (sets * reentry.setMultiplier).roundToInt())
            val reentryLoad = if (reentry.bucket.requiresRirRediscovery) {
                currentLoad?.let { snapDown(it * reentry.loadMultiplier, input.minAvailableIncrementKg) }
            } else {
                currentLoad?.let { snapDown(it * reentry.loadMultiplier, input.minAvailableIncrementKg) }
            }
            return StrengthProgressionDecision(
                operator = if (reentryLoad != null && currentLoad != null && reentryLoad < currentLoad) {
                    StrengthProgressionOperator.REDUCE_LOAD
                } else {
                    StrengthProgressionOperator.REDUCE_WORKING_SETS
                },
                perSetTargetReps = List(reentrySets) { input.repRange.first },
                recommendedSets = reentrySets,
                recommendedLoadKg = reentryLoad,
                targetRir = reentry.targetRir,
                returnReentry = reentry,
                uncertaintyTags = uncertainty + "EVIDENCE_GATED_RETURN_EXIT",
                rationale = "Returning from a ${input.confirmedStrengthBreakDays}-day strength break: ease back in at ${reentry.targetRir.first} to ${reentry.targetRir.last} RIR and confirm readiness before advancing."
            )
        }

        // 3. Execution-conflict check: failed target reps while simultaneously claiming high RIR (Track 6 §4.2).
        val conflict = input.completedSets.any { s ->
            s.completedReps < input.repRange.first &&
                (s.reportedRir ?: 0) >= Engine16StrengthPolicyRegistry.EXECUTION_CONFLICT_HIGH_RIR_THRESHOLD
        }
        if (conflict) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = uniformCurrentReps,
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                executionConflictDetected = true,
                uncertaintyTags = uncertainty + "EXECUTION_CONFLICT",
                rationale = "Completed repetitions fell short of target despite high reported RIR; clarify RIR anchor and repeat current targets."
            )
        }

        // 4. Conditional deload (POL-049 / Bell 2023): cut 30-50% sets @ >= 3-4 RIR, keep moderate-heavy load.
        if (input.fatigueTriggeredDeload) {
            val deloadSets = max(
                1,
                floor(sets * Engine16StrengthPolicyRegistry.DELOAD_DEFAULT_SET_RETAIN_FRACTION).toInt()
                    .coerceAtMost(sets - if (sets >= 2) 1 else 0)
            )
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.REDUCE_WORKING_SETS,
                perSetTargetReps = List(deloadSets) { input.currentTargetReps.coerceIn(input.repRange) },
                recommendedSets = deloadSets,
                recommendedLoadKg = currentLoad,
                targetRir = Engine16StrengthPolicyRegistry.DELOAD_TARGET_RIR_MIN..Engine16StrengthPolicyRegistry.DELOAD_TARGET_RIR_MAX,
                uncertaintyTags = uncertainty + "CONDITIONAL_DELOAD",
                rationale = "Conditional fatigue deload: trim working sets while maintaining current load at 3 to 4 RIR."
            )
        }

        // 5. Concurrent running MAJOR_STEP guard (MD-15 / POL-059): protect lower-body strength, allow upper-body.
        if (input.runningMajorStepActive && input.exercise.isLowerBody) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = uniformCurrentReps,
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = 2..3,
                uncertaintyTags = uncertainty + "CONCURRENT_RUNNING_MAJOR_STEP_HOLD",
                rationale = "Lower-body strength holds steady while running load steps up; maintain load intensity at 2 to 3 RIR."
            )
        }

        // 6. Maintenance objective (Spiering 2021): hold heavy load, zero forced overload.
        if (input.objective == StrengthObjective.STRENGTH_MAINTENANCE) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = uniformCurrentReps,
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty,
                rationale = "Strength maintenance: hold load and controlled effort without unnecessary fatigue."
            )
        }

        // 7. Calibration transfer mismatch (Equipment/ROM/Laterality changed): rediscover by RIR cleanly.
        if (input.calibrationResult?.trainingLoadRef is ProvisionalTrainingLoadRef.DiscoverByRir &&
            currentLoad == null
        ) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = uniformCurrentReps,
                recommendedSets = sets,
                recommendedLoadKg = null,
                targetRir = Engine16StrengthPolicyRegistry.RIR_BAND_REDISCOVERY,
                uncertaintyTags = uncertainty + "DISCOVER_BY_RIR",
                rationale = "Establish a baseline working load cleanly with 3 to 4 repetitions in reserve."
            )
        }

        // 8. Technique breakdown or missed bottom reps -> backoff or consolidate.
        val hasBreakdown = input.completedSets.any { it.techniqueQuality == TechniqueQuality.BREAKDOWN }
        val minCompleted = input.completedSets.minOfOrNull { it.completedReps } ?: 0
        val minRir = input.completedSets.mapNotNull { it.reportedRir }.minOrNull()
        val timeAborted = ExecutionFactor.TIME_CONSTRAINT in input.executionFactors

        if (hasBreakdown || (input.completedSets.size >= sets && minCompleted < input.repRange.first - 1)) {
            val backedOffLoad = currentLoad?.let {
                snapDown(it * (1.0 - Engine16StrengthPolicyRegistry.BACKOFF_LOAD_MIN_FRACTION), input.minAvailableIncrementKg)
            }
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.REDUCE_LOAD,
                perSetTargetReps = List(sets) { input.repRange.first },
                recommendedSets = sets,
                recommendedLoadKg = backedOffLoad,
                targetRir = 2..3,
                uncertaintyTags = uncertainty,
                rationale = "Missed bottom of target rep range or form broke down; step load back slightly to rebuild clean technique."
            )
        }

        if (input.completedSets.size < sets && timeAborted) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = uniformCurrentReps,
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty + "PARTIAL_TIME_CONSTRAINED",
                rationale = "Prior session ended early due to time; repeat current targets cleanly."
            )
        }

        val axialZeroRirBlocked = input.exercise.isAxialCompound && (minRir == 0)
        val hasBorderlineTech = input.completedSets.any { it.techniqueQuality == TechniqueQuality.BORDERLINE }
        if (input.completedSets.size < sets || minCompleted < input.repRange.first || axialZeroRirBlocked || hasBorderlineTech) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = List(sets) { input.currentTargetReps.coerceIn(input.repRange) },
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty,
                rationale = "Consolidate current weight and reps until every working set completes cleanly in target RIR."
            )
        }

        // 9. Minimal-input / messy-evidence guard (POL-046):
        // Missing RIR or unobserved technique -> require >= 2 completed sessions + continuity, MINOR step only.
        val missingRirOrRecovery = input.completedSets.none { it.reportedRir != null } ||
            input.techniqueProvenance == TechniqueProvenance.UNOBSERVED_TECHNIQUE ||
            input.symptomStatus == SymptomSafetyStatus.MISSING_UNREPORTED
        if (missingRirOrRecovery &&
            (input.consecutiveCompletedCleanSessions < Engine16StrengthPolicyRegistry.MINIMAL_INPUT_CONSECUTIVE_SESSIONS ||
                !input.verifiedSubsequentContinuity)
        ) {
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
                perSetTargetReps = List(sets) { input.currentTargetReps.coerceIn(input.repRange) },
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty + "AWAITING_SECOND_CONFIRMED_SESSION",
                rationale = "Complete two steady sessions at this target with unreported RIR/recovery before advancing."
            )
        }

        val allAtTop = input.completedSets.size >= sets &&
            input.completedSets.all { it.completedReps >= input.repRange.last }
        val rirReadyForLoadJump = minRir == null || minRir >= input.targetRir.first

        // 10. Top-of-range mastery -> Load progression vs Coarse-Increment Bridge (POL-045) vs Hypertrophy Set Volume.
        if (allAtTop && rirReadyForLoadJump) {
            if (currentLoad == null || input.exercise.loadSemanticsKind == ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT) {
                val canAddSet = input.objective == StrengthObjective.HYPERTROPHY &&
                    input.currentWeeklyDirectMuscleSets < input.maxWeeklyDirectMuscleSets &&
                    sets < 5 &&
                    !missingRirOrRecovery
                if (canAddSet) {
                    return StrengthProgressionDecision(
                        operator = StrengthProgressionOperator.PROGRESS_SET_VOLUME,
                        perSetTargetReps = List(sets + 1) { input.repRange.last },
                        recommendedSets = sets + 1,
                        recommendedLoadKg = null,
                        targetRir = input.targetRir,
                        uncertaintyTags = uncertainty,
                        rationale = "Add 1 working set within weekly muscle volume targets."
                    )
                }
                val newReps = (input.repRange.last + 1)
                return StrengthProgressionDecision(
                    operator = StrengthProgressionOperator.PROGRESS_ALL_SETS,
                    perSetTargetReps = List(sets) { newReps },
                    recommendedSets = sets,
                    recommendedLoadKg = null,
                    targetRir = input.targetRir,
                    uncertaintyTags = uncertainty,
                    rationale = "Extend bodyweight repetition target cleanly."
                )
            }

            // Minimal-input safety rule (POL-046 / MD-04): never make aggressive axial compound load jumps
            // with unobserved technique; cap at PROGRESS_ONE_SET or require verification first!
            if (input.exercise.isAxialCompound &&
                input.techniqueProvenance == TechniqueProvenance.UNOBSERVED_TECHNIQUE
            ) {
                val oneSetBumped = listOf(input.repRange.last + 1) + List(sets - 1) { input.repRange.last }
                return StrengthProgressionDecision(
                    operator = StrengthProgressionOperator.PROGRESS_ONE_SET,
                    perSetTargetReps = oneSetBumped,
                    recommendedSets = sets,
                    recommendedLoadKg = currentLoad,
                    targetRir = input.targetRir,
                    uncertaintyTags = uncertainty + "AXIAL_UNOBSERVED_TECH_NO_LOAD_JUMP",
                    rationale = "Add 1 repetition on the opening set while confirming clean axial technique before raising load."
                )
            }

            val nextStepRatio = input.minAvailableIncrementKg / currentLoad
            if (nextStepRatio > Engine16StrengthPolicyRegistry.COARSE_INCREMENT_TRIGGER_RATIO) {
                // POL-045 / MD-03 / T6-S13: Coarse fixed equipment step (> 12%)!
                val alreadyExtended = input.currentTargetReps >=
                    input.repRange.last + Engine16StrengthPolicyRegistry.COARSE_BRIDGE_REP_EXTENSION_STEP
                val selectedOption = if (alreadyExtended && !missingRirOrRecovery) {
                    CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL
                } else {
                    CoarseBridgeOption.EXTEND_REP_CEILING
                }
                val ordered = listOf(
                    CoarseBridgeOption.EXTEND_REP_CEILING,
                    CoarseBridgeOption.MICROLOAD_IF_AVAILABLE,
                    CoarseBridgeOption.ALTERNATE_EQUIPMENT_WITH_RECALIBRATION,
                    CoarseBridgeOption.CONSOLIDATE_CONTROL,
                    CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL,
                    CoarseBridgeOption.ADD_WORKING_SET,
                    CoarseBridgeOption.EXPLICIT_TEMPO_OR_PAUSE_VARIANT,
                    CoarseBridgeOption.LEVERAGE_VARIANT,
                    CoarseBridgeOption.UNILATERAL_NEW_EXERCISE_DISCOVERY
                )
                val trialLoad = if (selectedOption == CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL) {
                    snapToStep(currentLoad + input.minAvailableIncrementKg, input.minAvailableIncrementKg)
                } else {
                    currentLoad
                }
                val targetRepsList = if (selectedOption == CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL) {
                    List(sets) { Engine16StrengthPolicyRegistry.COARSE_BRIDGE_TRIAL_REPS_MIN }
                } else {
                    List(sets) { min(input.currentTargetReps + 1, input.repRange.last + 4) }
                }
                val pctLabel = (nextStepRatio * 100.0).roundToInt()
                return StrengthProgressionDecision(
                    operator = if (selectedOption == CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL) {
                        StrengthProgressionOperator.PROGRESS_LOAD_RESET_REPS
                    } else {
                        StrengthProgressionOperator.PROGRESS_ALL_SETS
                    },
                    perSetTargetReps = targetRepsList,
                    recommendedSets = sets,
                    recommendedLoadKg = trialLoad,
                    targetRir = if (selectedOption == CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL) 3..4 else input.targetRir,
                    coarseBridgePlan = CoarseIncrementBridgePlan(
                        nextLoadStepRatio = nextStepRatio,
                        orderedOptions = ordered,
                        selectedOption = selectedOption,
                        requiresRecalibrationTag = true,
                        coachingNote = if (selectedOption == CoarseBridgeOption.CONTROLLED_LOWER_REP_TRIAL) {
                            "Next increment (+$pctLabel%) is coarse: run a controlled ${Engine16StrengthPolicyRegistry.COARSE_BRIDGE_TRIAL_REPS_MIN} to ${Engine16StrengthPolicyRegistry.COARSE_BRIDGE_TRIAL_REPS_MAX} rep trial at 3 to 4 RIR."
                        } else {
                            "Next increment (+$pctLabel%) exceeds 12%: bridge by climbing reps above range before stepping up load."
                        }
                    ),
                    uncertaintyTags = uncertainty + "COARSE_INCREMENT_BRIDGE",
                    rationale = "Coarse equipment jump detected (+$pctLabel%); bridge smoothly instead of forcing an abrupt load increase."
                )
            }

            // Standard safe incremental load increase + reset to bottom of rep range!
            val bumpedLoad = snapToStep(currentLoad + input.minAvailableIncrementKg, input.minAvailableIncrementKg)
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.PROGRESS_LOAD_RESET_REPS,
                perSetTargetReps = List(sets) { input.repRange.first },
                recommendedSets = sets,
                recommendedLoadKg = bumpedLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty,
                rationale = "All sets reached ${input.repRange.last} reps cleanly; step load to ${formatKg(bumpedLoad)} kg and reset to ${input.repRange.first} reps."
            )
        }

        // 11. Smooth intra-range repetition progression (Track 6 §4.1 mathematical jump guard!).
        // Low-rep compounds (range <= 6 or currentReps <= 4): +1 rep on ONE set or PARTIAL sets!
        val baseRep = minCompleted.coerceIn(input.repRange.first, input.repRange.last)
        if (baseRep < input.repRange.last) {
            val isLowRepHeavy = input.repRange.last <= 6 || input.currentTargetReps <= 5
            if (isLowRepHeavy && sets >= 2) {
                val alreadyBumpedCount = input.completedSets.take(sets).count { it.completedReps > baseRep }
                return if (alreadyBumpedCount == 0 || missingRirOrRecovery) {
                    val targetList = listOf(baseRep + 1) + List(sets - 1) { baseRep }
                    StrengthProgressionDecision(
                        operator = StrengthProgressionOperator.PROGRESS_ONE_SET,
                        perSetTargetReps = targetList,
                        recommendedSets = sets,
                        recommendedLoadKg = currentLoad,
                        targetRir = input.targetRir,
                        uncertaintyTags = uncertainty,
                        rationale = "Low-rep heavy progression: add 1 repetition to the first set cleanly without jumping total volume abruptly."
                    )
                } else {
                    val nextPartialCount = min(sets - 1, alreadyBumpedCount + 1)
                    val targetList = List(sets) { idx -> if (idx < nextPartialCount) baseRep + 1 else baseRep }
                    StrengthProgressionDecision(
                        operator = StrengthProgressionOperator.PROGRESS_PARTIAL_SETS,
                        perSetTargetReps = targetList,
                        recommendedSets = sets,
                        recommendedLoadKg = currentLoad,
                        targetRir = input.targetRir,
                        uncertaintyTags = uncertainty,
                        rationale = "Advance another set by 1 repetition while keeping work controlled inside target RIR."
                    )
                }
            }

            if (missingRirOrRecovery && sets >= 2) {
                val targetList = listOf(baseRep + 1) + List(sets - 1) { baseRep }
                return StrengthProgressionDecision(
                    operator = StrengthProgressionOperator.PROGRESS_ONE_SET,
                    perSetTargetReps = targetList,
                    recommendedSets = sets,
                    recommendedLoadKg = currentLoad,
                    targetRir = input.targetRir,
                    uncertaintyTags = uncertainty,
                    rationale = "Minor single-set rep step with unreported RIR/recovery."
                )
            }

            val targetNext = (baseRep + 1).coerceAtMost(input.repRange.last)
            return StrengthProgressionDecision(
                operator = StrengthProgressionOperator.PROGRESS_ALL_SETS,
                perSetTargetReps = List(sets) { targetNext },
                recommendedSets = sets,
                recommendedLoadKg = currentLoad,
                targetRir = input.targetRir,
                uncertaintyTags = uncertainty,
                rationale = "Climb repetitions across working sets toward ${input.repRange.last} reps cleanly."
            )
        }

        // Top reps reached while waiting for RIR reserve improvement -> consolidate.
        return StrengthProgressionDecision(
            operator = StrengthProgressionOperator.CONSOLIDATE_MAINTAIN,
            perSetTargetReps = List(sets) { input.repRange.last },
            recommendedSets = sets,
            recommendedLoadKg = currentLoad,
            targetRir = input.targetRir,
            uncertaintyTags = uncertainty,
            rationale = "Consolidate top-of-range reps until target RIR reserve is comfortable before adding load."
        )
    }

    private fun snapToStep(rawKg: Double, stepKg: Double): Double {
        val step = stepKg.coerceAtLeast(0.5)
        return (ceil(rawKg / step - 1e-6) * step).coerceAtLeast(step)
    }

    private fun snapDown(rawKg: Double, stepKg: Double): Double {
        val step = stepKg.coerceAtLeast(0.5)
        return (floor(rawKg / step + 1e-6) * step).coerceAtLeast(step)
    }

    private fun formatKg(kg: Double): String =
        if (abs(kg - kg.roundToInt()) < 1e-4) kg.roundToInt().toString()
        else String.format(Locale.US, "%.1f", kg)
}
