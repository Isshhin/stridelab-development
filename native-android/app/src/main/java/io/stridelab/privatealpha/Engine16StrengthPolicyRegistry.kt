package io.stridelab.privatealpha

/**
 * Centralized numerical policy registry for Engine 1.6: Strength Intelligence.
 *
 * Provenance classes:
 * - [A] Published empirical / definition constants (Epley, Brzycki, ACSM 2026 PMC12965823,
 *       Spiering 2021, Pelland 2025/2026, Bell 2023 Delphi, Coleman 2024).
 * - [B] Transparent versioned StrideLab coaching / timing / safety heuristics.
 * - [SHADOW] Non-authoritative Tier B metrics for longitudinal calibration only.
 */
internal object Engine16StrengthPolicyRegistry {
    const val SPEC_VERSION: String = "1.6.0-strength-intelligence"
    const val POLICY_VERSION: String = "policy-registry-1.6.0-v1-strength"

    // =========================================================================
    // POL-042 / MOD-16: Piecewise Epley & Brzycki Provisional e1RM Predicates
    // =========================================================================
    const val E1RM_MIN_REPS: Int = 1
    const val E1RM_MAX_REPS: Int = 10
    const val E1RM_MAX_RIR: Int = 4
    const val E1RM_TIER_A_MAX_EFFECTIVE_REPS: Int = 10
    const val E1RM_TIER_B_SHADOW_MAX_EFFECTIVE_REPS: Int = 12
    const val EPLEY_DIVISOR: Double = 30.0
    const val BRZYCKI_INTERCEPT: Double = 1.0278
    const val BRZYCKI_SLOPE: Double = 0.0278
    const val BODY_MASS_TRANSFER_SHIFT_RATIO: Double = 0.05

    // =========================================================================
    // POL-043: Non-Failure Proximity Defaults (ACSM 2026 / Refalo 2023)
    // =========================================================================
    val RIR_BAND_MAXIMAL_STRENGTH: IntRange = 1..3
    val RIR_BAND_AXIAL_COMPOUND_SAFE: IntRange = 2..3
    val RIR_BAND_HYPERTROPHY: IntRange = 1..3
    val RIR_BAND_GENERAL_STRENGTH: IntRange = 2..3
    val RIR_BAND_STRENGTH_ENDURANCE: IntRange = 1..3
    val RIR_BAND_MAINTENANCE: IntRange = 2..3
    val RIR_BAND_RUNNING_SUPPORTIVE: IntRange = 2..3
    val RIR_BAND_POWER: IntRange = 3..5
    val RIR_BAND_REDISCOVERY: IntRange = 3..4

    // =========================================================================
    // POL-044 / MOD-13: Conservative SI Duration Intervals [min, max] (s)
    // =========================================================================
    const val DEFAULT_REP_CADENCE_MIN_SECONDS: Double = 3.0
    const val DEFAULT_REP_CADENCE_MAX_SECONDS: Double = 4.5
    const val TEMPO_REP_CADENCE_MIN_SECONDS: Double = 5.0
    const val TEMPO_REP_CADENCE_MAX_SECONDS: Double = 6.0
    const val UNILATERAL_SWITCH_MIN_SECONDS: Double = 10.0
    const val UNILATERAL_SWITCH_MAX_SECONDS: Double = 15.0
    const val SETUP_TRANSITION_MIN_SECONDS: Double = 45.0
    const val SETUP_TRANSITION_MAX_SECONDS: Double = 75.0
    const val RAMP_SET_MIN_SECONDS: Double = 80.0
    const val RAMP_SET_MAX_SECONDS: Double = 120.0
    const val SUPERSET_SWITCH_SECONDS: Double = 20.0
    const val GENERAL_WARMUP_MIN_SECONDS: Double = 240.0
    const val GENERAL_WARMUP_MAX_SECONDS: Double = 480.0
    const val COOLDOWN_MIN_SECONDS: Double = 120.0
    const val COOLDOWN_MAX_SECONDS: Double = 300.0
    const val MED_WARMUP_MIN_SECONDS: Double = 180.0
    const val MED_WARMUP_MAX_SECONDS: Double = 240.0
    const val MED_COOLDOWN_MIN_SECONDS: Double = 120.0
    const val MED_COOLDOWN_MAX_SECONDS: Double = 180.0

    // =========================================================================
    // POL-045: Coarse Equipment Increment Bridge Threshold
    // =========================================================================
    const val COARSE_INCREMENT_TRIGGER_RATIO: Double = 0.12
    const val DEFAULT_BARBELL_INCREMENT_KG: Double = 2.5
    const val DEFAULT_DUMBBELL_INCREMENT_PER_HAND_KG: Double = 2.5
    const val DEFAULT_MACHINE_INCREMENT_KG: Double = 5.0
    const val COARSE_BRIDGE_TRIAL_REPS_MIN: Int = 5
    const val COARSE_BRIDGE_TRIAL_REPS_MAX: Int = 6
    const val COARSE_BRIDGE_REP_EXTENSION_STEP: Int = 3

    // =========================================================================
    // POL-046: Minimal-Input & Messy-Evidence Progression Gates
    // =========================================================================
    const val MINIMAL_INPUT_CONSECUTIVE_SESSIONS: Int = 2
    const val EXECUTION_CONFLICT_HIGH_RIR_THRESHOLD: Int = 3

    // =========================================================================
    // POL-049: Conditional Deload (Bell et al. 2023 Delphi Consensus)
    // =========================================================================
    const val DELOAD_SET_REDUCTION_MIN_FRACTION: Double = 0.30
    const val DELOAD_SET_REDUCTION_MAX_FRACTION: Double = 0.50
    const val DELOAD_DEFAULT_SET_RETAIN_FRACTION: Double = 0.60
    const val DELOAD_TARGET_RIR_MIN: Int = 3
    const val DELOAD_TARGET_RIR_MAX: Int = 4
    const val HIGH_VOLUME_CONDITIONAL_DELOAD_CADENCE_WEEKS: Int = 4

    // =========================================================================
    // POL-059 / POL-060 / MOD-B4: Progression Operators & Ledger Ceilings
    // =========================================================================
    const val MINOR_MAX_LOAD_STEP_FRACTION: Double = 0.05
    const val MAJOR_LOAD_STEP_FRACTION: Double = 0.10
    const val BACKOFF_LOAD_MIN_FRACTION: Double = 0.05
    const val BACKOFF_LOAD_MAX_FRACTION: Double = 0.10
    const val SET_VOLUME_STEP: Int = 1
    const val DEFAULT_MAX_WEEKLY_DIRECT_SETS_PER_MUSCLE: Int = 20
    const val BEGINNER_MAX_WEEKLY_DIRECT_SETS_PER_MUSCLE: Int = 12
    const val HIGH_EXPOSURE_FAILURE_RIR_CUTOFF: Int = 4
    const val SHADOW_FRACTIONAL_INDIRECT_WEIGHT: Double = 0.5

    // =========================================================================
    // POL-033 & POL-058: Gapless Interruption Return Curves
    // =========================================================================
    const val RETURN_BUCKET_BRIEF_MAX_DAYS: Int = 6
    const val RETURN_BUCKET_SHORT_MAX_DAYS: Int = 13
    const val RETURN_BUCKET_MODERATE_MAX_DAYS: Int = 27
    const val RETURN_BUCKET_EXTENDED_MAX_DAYS: Int = 55
    const val RETURN_BUCKET_LONG_MAX_DAYS: Int = 83
    const val RETURN_EXIT_LOAD_RATIO: Double = 0.90

    // =========================================================================
    // ACSM 2026 & Track 6 Objective Defaults Table
    // =========================================================================
    fun defaultsFor(objective: StrengthObjective, experience: Int): StrengthObjectiveSpec {
        val novice = experience <= 0
        return when (objective) {
            StrengthObjective.MAXIMAL_STRENGTH -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = if (novice) 5..8 else 3..6,
                secondaryRepRange = 6..8,
                isolationRepRange = 8..12,
                compoundRestSeconds = if (novice) 150 else 180,
                secondaryRestSeconds = 120,
                isolationRestSeconds = 75,
                primarySets = if (novice) 3 else 4,
                secondarySets = if (novice) 2 else 3,
                isolationSets = 2,
                weeklyDirectSetBand = if (novice) 6..10 else 6..12,
                targetRir = RIR_BAND_MAXIMAL_STRENGTH,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.HYPERTROPHY -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 8..10,
                secondaryRepRange = 8..12,
                isolationRepRange = 10..15,
                compoundRestSeconds = 120,
                secondaryRestSeconds = 90,
                isolationRestSeconds = 60,
                primarySets = if (novice) 3 else 4,
                secondarySets = 3,
                isolationSets = if (novice) 2 else 3,
                weeklyDirectSetBand = if (novice) 6..10 else 10..20,
                targetRir = RIR_BAND_HYPERTROPHY,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.GENERAL_STRENGTH -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 6..10,
                secondaryRepRange = 8..12,
                isolationRepRange = 10..12,
                compoundRestSeconds = 120,
                secondaryRestSeconds = 90,
                isolationRestSeconds = 60,
                primarySets = if (novice) 3 else 3,
                secondarySets = 3,
                isolationSets = 2,
                weeklyDirectSetBand = if (novice) 4..8 else 6..10,
                targetRir = RIR_BAND_GENERAL_STRENGTH,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.STRENGTH_ENDURANCE -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 12..18,
                secondaryRepRange = 15..20,
                isolationRepRange = 15..25,
                compoundRestSeconds = 75,
                secondaryRestSeconds = 60,
                isolationRestSeconds = 45,
                primarySets = if (novice) 3 else 3,
                secondarySets = 3,
                isolationSets = 2,
                weeklyDirectSetBand = 6..12,
                targetRir = RIR_BAND_STRENGTH_ENDURANCE,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.STRENGTH_MAINTENANCE -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 4..8,
                secondaryRepRange = 6..8,
                isolationRepRange = 8..12,
                compoundRestSeconds = 150,
                secondaryRestSeconds = 120,
                isolationRestSeconds = 75,
                primarySets = 2,
                secondarySets = 2,
                isolationSets = 2,
                weeklyDirectSetBand = 2..4,
                targetRir = RIR_BAND_MAINTENANCE,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.RUNNING_SUPPORTIVE -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 4..6,
                secondaryRepRange = 6..8,
                isolationRepRange = 10..12,
                compoundRestSeconds = 150,
                secondaryRestSeconds = 120,
                isolationRestSeconds = 60,
                primarySets = if (novice) 2 else 3,
                secondarySets = 2,
                isolationSets = 2,
                weeklyDirectSetBand = 4..8,
                targetRir = RIR_BAND_RUNNING_SUPPORTIVE,
                allowSupersetUnderTightCap = true
            )
            StrengthObjective.POWER_DEVELOPMENT -> StrengthObjectiveSpec(
                objective = objective,
                primaryRepRange = 2..5,
                secondaryRepRange = 4..6,
                isolationRepRange = 8..10,
                compoundRestSeconds = 180,
                secondaryRestSeconds = 120,
                isolationRestSeconds = 75,
                primarySets = 3,
                secondarySets = 2,
                isolationSets = 2,
                weeklyDirectSetBand = 6..12,
                targetRir = RIR_BAND_POWER,
                allowSupersetUnderTightCap = false
            )
        }
    }
}
