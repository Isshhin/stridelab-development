package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal enum class RunningSegmentRole {
    WARM_UP,
    PREP_DRILLS,
    WORK,
    RECOVERY_ACTIVE,
    RECOVERY_FLOAT,
    RECOVERY_WALK_STAT,
    STRIDES_NM,
    COOL_DOWN,
    UNCLASSIFIED
}

internal enum class PhysiologicalIntensityClass {
    Z1_MODERATE,
    Z2_HEAVY,
    Z3_SEVERE,
    NEUROMUSCULAR,
    WALK_STATIONARY,
    UNCLASSIFIED
}

internal enum class RecoveryModality {
    PASSIVE_STAND,
    WALK,
    EASY_JOG,
    FLOAT
}

internal enum class RunningQualityFamily10(
    val displayTitle: String,
    val defaultDomain: PhysiologicalIntensityClass,
    val requiresSevereWarmUpFloor: Boolean,
    val introDescription: String
) {
    EASY_WITH_STRIDES("Easy Run + Strides", PhysiologicalIntensityClass.NEUROMUSCULAR, false, "4x15 s / 60 s walk"),
    AEROBIC_FARTLEK("Aerobic Fartlek", PhysiologicalIntensityClass.Z2_HEAVY, false, "5x2 m / 2 m easy"),
    TEMPO_CONTINUOUS("Continuous Tempo", PhysiologicalIntensityClass.Z2_HEAVY, false, "15 m continuous tempo"),
    TEMPO_CRUISE("Cruise Intervals", PhysiologicalIntensityClass.Z2_HEAVY, false, "3x5 m / 75 s"),
    THRESHOLD_INTERVALS("Threshold Intervals", PhysiologicalIntensityClass.Z2_HEAVY, false, "3x6 m / 75 s"),
    VO2MAX_SHORT_INTERVALS("Short VO2max Intervals", PhysiologicalIntensityClass.Z3_SEVERE, true, "2x(8x30 s / 30 s)"),
    VO2MAX_LONG_INTERVALS("Long VO2max Intervals", PhysiologicalIntensityClass.Z3_SEVERE, true, "4x2 m / 2 m"),
    LONG_AEROBIC_HILLS("Long Aerobic Hills", PhysiologicalIntensityClass.Z2_HEAVY, true, "5x60 s uphill / walk down"),
    SHORT_HILL_SPRINTS("Short Hill Sprints", PhysiologicalIntensityClass.NEUROMUSCULAR, true, "4x10 s max uphill / 90 s walk"),
    MARATHON_SPECIFIC_LR("Marathon-Pace Long Run", PhysiologicalIntensityClass.Z2_HEAVY, false, "Fast-finish marathon pace block")
}

internal enum class RunningProgressionOperator {
    HOLD_CONSOLIDATE,
    EXTEND_REP_DURATION,
    ADD_REP,
    ADD_SET,
    REDUCE_RECOVERY_DURATION,
    SHIFT_RECOVERY_MODALITY,
    MERGE_BROKEN_TO_CONTINUOUS,
    STEP_INTENSITY_WITHIN_DOMAIN,
    INTRODUCE_FAMILY,
    ADD_EMBEDDED_RACE_PACE,
    REDUCE_CUTBACK
}

internal enum class StoppingEnforcementLevel {
    PRESCRIBED_INSTRUCTION,
    ATHLETE_MONITORED,
    APP_ENFORCED,
    RETROSPECTIVELY_VERIFIED
}

internal data class UnknownPaceStoppingPredicate(
    val timeCapSeconds: Int?,
    val distanceCapMeters: Int?,
    val enforcementLevel: StoppingEnforcementLevel,
    val distanceCapCertifiable: Boolean = (enforcementLevel == StoppingEnforcementLevel.APP_ENFORCED ||
        enforcementLevel == StoppingEnforcementLevel.RETROSPECTIVELY_VERIFIED)
) {
    init {
        require(timeCapSeconds == null || timeCapSeconds > 0)
        require(distanceCapMeters == null || distanceCapMeters > 0)
    }

    /** MOD-11 unit-homogeneous stopping predicate: never compares seconds directly against meters. */
    fun shouldStop(elapsedSeconds: Double, coveredMeters: Double): Boolean {
        val hitTime = timeCapSeconds != null && elapsedSeconds >= timeCapSeconds.toDouble()
        val hitDistance = distanceCapMeters != null && coveredMeters >= distanceCapMeters.toDouble()
        return hitTime || hitDistance
    }

    fun grantsGuaranteedDistanceCompliance(): Boolean = distanceCapCertifiable &&
        enforcementLevel == StoppingEnforcementLevel.APP_ENFORCED
}

internal data class RunningAstLeaf(
    val role: RunningSegmentRole,
    val intensityClass: PhysiologicalIntensityClass,
    val durationMinSeconds: Double,
    val durationMaxSeconds: Double,
    val distanceLowMeters: Double?,
    val distanceHighMeters: Double?
) {
    init {
        require(durationMinSeconds >= 0.0 && durationMaxSeconds >= durationMinSeconds)
    }
}

internal data class OrthogonalTidAccount(
    val totalSeconds: Double,
    val byRoleSeconds: Map<RunningSegmentRole, Double>,
    val byClassSeconds: Map<PhysiologicalIntensityClass, Double>,
    val activeClassifiedSeconds: Double,
    val conservationResidualSeconds: Double
) {
    val activeZ1Share: Double
        get() = if (activeClassifiedSeconds > 0.0) (byClassSeconds[PhysiologicalIntensityClass.Z1_MODERATE] ?: 0.0) / activeClassifiedSeconds else 0.0
    val activeZ2Share: Double
        get() = if (activeClassifiedSeconds > 0.0) (byClassSeconds[PhysiologicalIntensityClass.Z2_HEAVY] ?: 0.0) / activeClassifiedSeconds else 0.0
    val activeZ3Share: Double
        get() = if (activeClassifiedSeconds > 0.0) (byClassSeconds[PhysiologicalIntensityClass.Z3_SEVERE] ?: 0.0) / activeClassifiedSeconds else 0.0
}

internal data class RunningWorkoutAst(
    val family: RunningQualityFamily10,
    val sets: Int,
    val repsPerSet: Int,
    val repWorkSeconds: Int,
    val innerRecoverySeconds: Int,
    val betweenSetRecoverySeconds: Int,
    val recoveryModality: RecoveryModality,
    val warmUpSeconds: Int,
    val prepDrillsSeconds: Int,
    val supplementaryStridesSeconds: Int,
    val coolDownSeconds: Int,
    val leaves: List<RunningAstLeaf>
) {
    val totalWorkSeconds: Int get() = sets * repsPerSet * repWorkSeconds
    val innerRecoveryTotalSeconds: Int get() = sets * max(0, repsPerSet - 1) * innerRecoverySeconds
    val betweenSetRecoveryTotalSeconds: Int get() = max(0, sets - 1) * betweenSetRecoverySeconds
    val totalRecoverySeconds: Int get() = innerRecoveryTotalSeconds + betweenSetRecoveryTotalSeconds
    val totalMinSeconds: Double get() = leaves.sumOf { it.durationMinSeconds }
    val totalMaxSeconds: Double get() = leaves.sumOf { it.durationMaxSeconds }
    val expectedDistanceLowMeters: Double?
        get() = if (leaves.all { it.distanceLowMeters != null }) leaves.sumOf { it.distanceLowMeters!! } else null
    val expectedDistanceHighMeters: Double?
        get() = if (leaves.all { it.distanceHighMeters != null }) leaves.sumOf { it.distanceHighMeters!! } else null
    val workToRecoveryDensity: Double
        get() {
            val denom = totalWorkSeconds + totalRecoverySeconds
            return if (denom > 0) totalWorkSeconds.toDouble() / denom else 1.0
        }

    /** MOD-15 / CON-15 orthogonal TID accounting summing strictly to totalDurationSeconds. */
    fun computeTidAccount(): OrthogonalTidAccount {
        val roleMap = LinkedHashMap<RunningSegmentRole, Double>()
        val classMap = LinkedHashMap<PhysiologicalIntensityClass, Double>()
        for (leaf in leaves) {
            val d = leaf.durationMaxSeconds
            roleMap[leaf.role] = (roleMap[leaf.role] ?: 0.0) + d
            classMap[leaf.intensityClass] = (classMap[leaf.intensityClass] ?: 0.0) + d
        }
        val roleSum = roleMap.values.sum()
        val classSum = classMap.values.sum()
        val activeClassified = (classMap[PhysiologicalIntensityClass.Z1_MODERATE] ?: 0.0) +
            (classMap[PhysiologicalIntensityClass.Z2_HEAVY] ?: 0.0) +
            (classMap[PhysiologicalIntensityClass.Z3_SEVERE] ?: 0.0)
        return OrthogonalTidAccount(
            totalSeconds = roleSum,
            byRoleSeconds = roleMap,
            byClassSeconds = classMap,
            activeClassifiedSeconds = activeClassified,
            conservationResidualSeconds = abs(roleSum - classSum)
        )
    }
}

/** 13-field observed workout tolerance contract replacing legacy global Q0-Q3 tiers. */
internal data class FamilySpecificWorkoutDose(
    val family: RunningQualityFamily10,
    val totalWorkSeconds: Int,
    val longestWorkBoutSeconds: Int,
    val repCount: Int,
    val setCount: Int,
    val intensityAuthority: String,
    val recoverySeconds: Int,
    val recoveryModality: RecoveryModality,
    val workToRecoveryDensity: Double,
    val mechanicalExposureMeters: Double?,
    val sessionDurationSeconds: Int,
    val terrain: String,
    val environment: String,
    val recoveryEvidenceVerifiedAcceptable: Boolean?
) {
    fun isStructurallyComparableTo(candidate: RunningWorkoutAst): Boolean {
        val boutRatio = if (longestWorkBoutSeconds > 0) {
            candidate.repWorkSeconds.toDouble() / longestWorkBoutSeconds.toDouble()
        } else 1.0
        val densityDiff = abs(candidate.workToRecoveryDensity - workToRecoveryDensity)
        return boutRatio in 0.70..1.35 &&
            densityDiff <= 0.12 &&
            recoveryModality == candidate.recoveryModality
    }
}

internal data class RunningWorkoutCandidateResult(
    val usedEasyStridesFallback: Boolean,
    val selectedAst: RunningWorkoutAst?,
    val fallbackTitle: String?,
    val tidAccount: OrthogonalTidAccount?,
    val candidateCountEvaluated: Int,
    val stoppingPredicate: UnknownPaceStoppingPredicate?
)

/**
 * M3 Bounded Running Workout Constructor & Exact Recursive Accounting:
 * - MOD-13 exact (n-1) inner & (S-1) between-set recovery with zero phantom final recovery
 * - Full [n_lo, n_hi] enumeration (CON-12) instead of {n_max-1, n_max} pruning
 * - POL-028 / POL-029 non-overridable L4A WU/Prep/CD safety floors
 * - POL-056 intro doses for all 10 running quality families
 * - Easy + Strides empty-set fallback when constraints block safe quality dosing
 */
internal object Engine15RunningWorkoutConstructor {

    fun introCandidate(
        family: RunningQualityFamily10,
        speedLowMps: Double? = null,
        speedHighMps: Double? = null,
        easySpeedLowMps: Double? = null,
        easySpeedHighMps: Double? = null
    ): RunningWorkoutAst = when (family) {
        RunningQualityFamily10.EASY_WITH_STRIDES -> buildAst(
            family = family, sets = 1, reps = 4, repWorkS = 15, innerRecS = 60, setRecS = 0,
            modality = RecoveryModality.WALK, warmUpS = 1_200, prepS = 0, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.AEROBIC_FARTLEK -> buildAst(
            family = family, sets = 1, reps = 5, repWorkS = 120, innerRecS = 120, setRecS = 0,
            modality = RecoveryModality.EASY_JOG, warmUpS = 600, prepS = 0, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.TEMPO_CONTINUOUS -> buildAst(
            family = family, sets = 1, reps = 1, repWorkS = 900, innerRecS = 0, setRecS = 0,
            modality = RecoveryModality.WALK, warmUpS = 600, prepS = 0, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.TEMPO_CRUISE -> buildAst(
            family = family, sets = 1, reps = 3, repWorkS = 300, innerRecS = 75, setRecS = 0,
            modality = RecoveryModality.WALK, warmUpS = 600, prepS = 0, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.THRESHOLD_INTERVALS -> buildAst(
            family = family, sets = 1, reps = 3, repWorkS = 360, innerRecS = 75, setRecS = 0,
            modality = RecoveryModality.EASY_JOG, warmUpS = 600, prepS = 0, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.VO2MAX_SHORT_INTERVALS -> buildAst(
            family = family, sets = 2, reps = 8, repWorkS = 30, innerRecS = 30, setRecS = 180,
            modality = RecoveryModality.WALK, warmUpS = 720, prepS = 180, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.VO2MAX_LONG_INTERVALS -> buildAst(
            family = family, sets = 1, reps = 4, repWorkS = 120, innerRecS = 120, setRecS = 0,
            modality = RecoveryModality.EASY_JOG, warmUpS = 720, prepS = 180, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.LONG_AEROBIC_HILLS -> buildAst(
            family = family, sets = 1, reps = 5, repWorkS = 60, innerRecS = 90, setRecS = 0,
            modality = RecoveryModality.WALK, warmUpS = 720, prepS = 180, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.SHORT_HILL_SPRINTS -> buildAst(
            family = family, sets = 1, reps = 4, repWorkS = 10, innerRecS = 90, setRecS = 0,
            modality = RecoveryModality.WALK, warmUpS = 720, prepS = 180, suppS = 0, coolDownS = 300,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
        RunningQualityFamily10.MARATHON_SPECIFIC_LR -> buildAst(
            family = family, sets = 1, reps = 1, repWorkS = 1_200, innerRecS = 0, setRecS = 0,
            modality = RecoveryModality.EASY_JOG, warmUpS = 2_400, prepS = 0, suppS = 0, coolDownS = 600,
            speedLowMps = speedLowMps, speedHighMps = speedHighMps,
            easySpeedLowMps = easySpeedLowMps, easySpeedHighMps = easySpeedHighMps
        )
    }

    fun constructBoundedCandidate(
        family: RunningQualityFamily10,
        targetWorkSeconds: Int,
        maxWorkSeconds: Int,
        dayMaxSeconds: Int,
        priorDose: FamilySpecificWorkoutDose? = null,
        preferredRepLengthSeconds: Int? = null,
        speedLowMps: Double? = null,
        speedHighMps: Double? = null,
        easySpeedLowMps: Double? = null,
        easySpeedHighMps: Double? = null,
        bindingDistanceCapMeters: Int? = null,
        paceUnknown: Boolean = (speedLowMps == null || speedHighMps == null)
    ): RunningWorkoutCandidateResult {
        val wuMin = if (family.requiresSevereWarmUpFloor) {
            Engine15RunningPolicyRegistry.POL_028_SEVERE_HILLS_FAST_MIN_WU_S
        } else {
            Engine15RunningPolicyRegistry.POL_028_TEMPO_THRESHOLD_MIN_WU_S
        }
        val prepMin = if (family.requiresSevereWarmUpFloor) {
            Engine15RunningPolicyRegistry.POL_028_SEVERE_HILLS_PREP_S
        } else 0
        val cdMin = Engine15RunningPolicyRegistry.POL_029_QUALITY_MIN_CD_S

        val gridRepSeconds = canonicalRepGrid(family)
        val recSpec = canonicalRecoverySpec(family)
        val setOptions = if (family == RunningQualityFamily10.VO2MAX_SHORT_INTERVALS) listOf(2, 3) else listOf(1)
        val minWorkFamily = familyMinWorkSeconds(family)
        val effectiveWorkMax = maxWorkSeconds.coerceAtMost(
            ((priorDose?.totalWorkSeconds ?: targetWorkSeconds) * (1.0 + Engine15RunningPolicyRegistry.POL_026_QUALITY_MAJOR_REL_CAP))
                .toInt()
                .coerceAtLeast(targetWorkSeconds)
        )

        val validCandidates = mutableListOf<RunningWorkoutAst>()
        var evaluated = 0
        for (sets in setOptions) {
            for (tRep in gridRepSeconds) {
                val nHi = floor(effectiveWorkMax.toDouble() / (sets * tRep).toDouble()).toInt()
                val nLo = if (family == RunningQualityFamily10.VO2MAX_SHORT_INTERVALS) 6 else 1
                if (nHi < nLo) continue
                // CON-12: iterate full bounded [n_lo, n_hi], never {n_max-1, n_max} pruning
                for (n in nLo..nHi) {
                    val ast = buildAst(
                        family = family,
                        sets = sets,
                        reps = n,
                        repWorkS = tRep,
                        innerRecoverySeconds = recSpec.first,
                        setRecS = if (sets > 1) recSpec.second else 0,
                        modality = recSpec.third,
                        warmUpS = wuMin,
                        prepS = prepMin,
                        suppS = 0,
                        coolDownS = cdMin,
                        speedLowMps = speedLowMps,
                        speedHighMps = speedHighMps,
                        easySpeedLowMps = easySpeedLowMps,
                        easySpeedHighMps = easySpeedHighMps
                    )
                    evaluated++
                    if (ast.totalWorkSeconds in minWorkFamily..effectiveWorkMax &&
                        ast.warmUpSeconds >= wuMin &&
                        ast.prepDrillsSeconds >= prepMin &&
                        ast.coolDownSeconds >= cdMin &&
                        ast.totalMaxSeconds <= dayMaxSeconds.toDouble() + 1e-6
                    ) {
                        validCandidates += ast
                    }
                }
            }
        }

        if (validCandidates.isEmpty()) {
            return RunningWorkoutCandidateResult(
                usedEasyStridesFallback = true,
                selectedAst = null,
                fallbackTitle = "Easy Aerobic Run + Optional Strides",
                tidAccount = null,
                candidateCountEvaluated = evaluated,
                stoppingPredicate = if (paceUnknown && bindingDistanceCapMeters != null) {
                    UnknownPaceStoppingPredicate(dayMaxSeconds, bindingDistanceCapMeters, StoppingEnforcementLevel.PRESCRIBED_INSTRUCTION)
                } else null
            )
        }

        val targetPref = preferredRepLengthSeconds ?: (priorDose?.longestWorkBoutSeconds ?: gridRepSeconds.first())
        val comparator = compareBy<RunningWorkoutAst>(
            { abs(targetWorkSeconds - it.totalWorkSeconds) },
            { if (priorDose == null || priorDose.isStructurallyComparableTo(it)) 0 else 1 },
            { abs(targetPref - it.repWorkSeconds) },
            { it.sets },
            { it.totalMaxSeconds }
        )
        val best = validCandidates.minWith(comparator)
        return RunningWorkoutCandidateResult(
            usedEasyStridesFallback = false,
            selectedAst = best,
            fallbackTitle = null,
            tidAccount = best.computeTidAccount(),
            candidateCountEvaluated = evaluated,
            stoppingPredicate = if (paceUnknown && bindingDistanceCapMeters != null) {
                UnknownPaceStoppingPredicate(
                    timeCapSeconds = dayMaxSeconds,
                    distanceCapMeters = bindingDistanceCapMeters,
                    enforcementLevel = StoppingEnforcementLevel.PRESCRIBED_INSTRUCTION
                )
            } else null
        )
    }

    /** 7-step stimulus-first reconciliation operator selector when tight budget or fatigue blocks default. */
    fun selectStimulusFirstOperator(
        needsCutback: Boolean,
        densityTooHigh: Boolean,
        hasPreviousExposure: Boolean,
        canExtendRepWithoutBreakingWorkCap: Boolean
    ): RunningProgressionOperator = when {
        needsCutback -> RunningProgressionOperator.REDUCE_CUTBACK
        !hasPreviousExposure -> RunningProgressionOperator.HOLD_CONSOLIDATE
        densityTooHigh -> RunningProgressionOperator.HOLD_CONSOLIDATE
        canExtendRepWithoutBreakingWorkCap -> RunningProgressionOperator.EXTEND_REP_DURATION
        else -> RunningProgressionOperator.ADD_REP
    }

    fun buildAst(
        family: RunningQualityFamily10,
        sets: Int,
        reps: Int,
        repWorkS: Int,
        innerRecS: Int = 0,
        innerRecoverySeconds: Int = innerRecS,
        setRecS: Int,
        modality: RecoveryModality,
        warmUpS: Int,
        prepS: Int,
        suppS: Int,
        coolDownS: Int,
        speedLowMps: Double? = null,
        speedHighMps: Double? = null,
        easySpeedLowMps: Double? = null,
        easySpeedHighMps: Double? = null
    ): RunningWorkoutAst {
        val totalWorkS = (sets * reps * repWorkS).toDouble()
        val innerRecTotalS = (sets * max(0, reps - 1) * innerRecoverySeconds).toDouble()
        val betweenSetRecTotalS = (max(0, sets - 1) * setRecS).toDouble()

        val recClass = when (modality) {
            RecoveryModality.PASSIVE_STAND, RecoveryModality.WALK -> PhysiologicalIntensityClass.WALK_STATIONARY
            RecoveryModality.EASY_JOG, RecoveryModality.FLOAT -> PhysiologicalIntensityClass.Z1_MODERATE
        }
        val recRole = when (modality) {
            RecoveryModality.PASSIVE_STAND, RecoveryModality.WALK -> RunningSegmentRole.RECOVERY_WALK_STAT
            RecoveryModality.EASY_JOG -> RunningSegmentRole.RECOVERY_ACTIVE
            RecoveryModality.FLOAT -> RunningSegmentRole.RECOVERY_FLOAT
        }

        fun distLo(sec: Double, vLo: Double?) = if (vLo != null && vLo > 0.0) sec * vLo else null
        fun distHi(sec: Double, vHi: Double?) = if (vHi != null && vHi > 0.0) sec * vHi else null

        val leaves = buildList {
            if (warmUpS > 0) {
                add(RunningAstLeaf(RunningSegmentRole.WARM_UP, PhysiologicalIntensityClass.Z1_MODERATE, warmUpS.toDouble(), warmUpS.toDouble(), distLo(warmUpS.toDouble(), easySpeedLowMps), distHi(warmUpS.toDouble(), easySpeedHighMps)))
            }
            if (prepS > 0) {
                add(RunningAstLeaf(RunningSegmentRole.PREP_DRILLS, PhysiologicalIntensityClass.NEUROMUSCULAR, prepS.toDouble(), prepS.toDouble(), null, null))
            }
            add(RunningAstLeaf(RunningSegmentRole.WORK, family.defaultDomain, totalWorkS, totalWorkS, distLo(totalWorkS, speedLowMps), distHi(totalWorkS, speedHighMps)))
            val totalRecS = innerRecTotalS + betweenSetRecTotalS
            if (totalRecS > 0.0) {
                val recLo = if (modality == RecoveryModality.PASSIVE_STAND) 0.0 else distLo(totalRecS, easySpeedLowMps)
                val recHi = if (modality == RecoveryModality.PASSIVE_STAND) 0.0 else distHi(totalRecS, easySpeedHighMps)
                add(RunningAstLeaf(recRole, recClass, totalRecS, totalRecS, recLo, recHi))
            }
            if (suppS > 0) {
                add(RunningAstLeaf(RunningSegmentRole.STRIDES_NM, PhysiologicalIntensityClass.NEUROMUSCULAR, suppS.toDouble(), suppS.toDouble(), null, null))
            }
            if (coolDownS > 0) {
                add(RunningAstLeaf(RunningSegmentRole.COOL_DOWN, PhysiologicalIntensityClass.Z1_MODERATE, coolDownS.toDouble(), coolDownS.toDouble(), distLo(coolDownS.toDouble(), easySpeedLowMps), distHi(coolDownS.toDouble(), easySpeedHighMps)))
            }
        }
        return RunningWorkoutAst(
            family = family,
            sets = sets,
            repsPerSet = reps,
            repWorkSeconds = repWorkS,
            innerRecoverySeconds = innerRecoverySeconds,
            betweenSetRecoverySeconds = setRecS,
            recoveryModality = modality,
            warmUpSeconds = warmUpS,
            prepDrillsSeconds = prepS,
            supplementaryStridesSeconds = suppS,
            coolDownSeconds = coolDownS,
            leaves = leaves
        )
    }

    private fun canonicalRepGrid(family: RunningQualityFamily10): List<Int> = when (family) {
        RunningQualityFamily10.EASY_WITH_STRIDES -> listOf(15, 20)
        RunningQualityFamily10.AEROBIC_FARTLEK -> listOf(120, 180)
        RunningQualityFamily10.TEMPO_CONTINUOUS -> listOf(720, 900, 1_200, 1_500, 1_800, 2_100, 2_400)
        RunningQualityFamily10.TEMPO_CRUISE -> listOf(240, 300, 360, 420, 480, 600)
        RunningQualityFamily10.THRESHOLD_INTERVALS -> listOf(240, 300, 330, 360, 420, 480, 600)
        RunningQualityFamily10.VO2MAX_SHORT_INTERVALS -> listOf(30)
        RunningQualityFamily10.VO2MAX_LONG_INTERVALS -> listOf(120, 150, 180, 240)
        RunningQualityFamily10.LONG_AEROBIC_HILLS -> listOf(60, 90, 120)
        RunningQualityFamily10.SHORT_HILL_SPRINTS -> listOf(8, 10, 12)
        RunningQualityFamily10.MARATHON_SPECIFIC_LR -> listOf(900, 1_200, 1_800, 2_400)
    }

    private fun canonicalRecoverySpec(family: RunningQualityFamily10): Triple<Int, Int, RecoveryModality> = when (family) {
        RunningQualityFamily10.EASY_WITH_STRIDES -> Triple(60, 0, RecoveryModality.WALK)
        RunningQualityFamily10.AEROBIC_FARTLEK -> Triple(120, 0, RecoveryModality.EASY_JOG)
        RunningQualityFamily10.TEMPO_CONTINUOUS -> Triple(0, 0, RecoveryModality.WALK)
        RunningQualityFamily10.TEMPO_CRUISE -> Triple(75, 0, RecoveryModality.EASY_JOG)
        RunningQualityFamily10.THRESHOLD_INTERVALS -> Triple(75, 0, RecoveryModality.EASY_JOG)
        RunningQualityFamily10.VO2MAX_SHORT_INTERVALS -> Triple(30, 180, RecoveryModality.WALK)
        RunningQualityFamily10.VO2MAX_LONG_INTERVALS -> Triple(120, 0, RecoveryModality.EASY_JOG)
        RunningQualityFamily10.LONG_AEROBIC_HILLS -> Triple(90, 0, RecoveryModality.WALK)
        RunningQualityFamily10.SHORT_HILL_SPRINTS -> Triple(90, 0, RecoveryModality.WALK)
        RunningQualityFamily10.MARATHON_SPECIFIC_LR -> Triple(0, 0, RecoveryModality.EASY_JOG)
    }

    private fun familyMinWorkSeconds(family: RunningQualityFamily10): Int = when (family) {
        RunningQualityFamily10.EASY_WITH_STRIDES -> 60
        RunningQualityFamily10.SHORT_HILL_SPRINTS -> 32
        RunningQualityFamily10.LONG_AEROBIC_HILLS -> 300
        RunningQualityFamily10.VO2MAX_SHORT_INTERVALS -> 480
        RunningQualityFamily10.VO2MAX_LONG_INTERVALS -> 480
        RunningQualityFamily10.AEROBIC_FARTLEK -> 600
        RunningQualityFamily10.TEMPO_CRUISE -> 720
        RunningQualityFamily10.THRESHOLD_INTERVALS -> 720
        RunningQualityFamily10.TEMPO_CONTINUOUS -> 720
        RunningQualityFamily10.MARATHON_SPECIFIC_LR -> 900
    }
}
