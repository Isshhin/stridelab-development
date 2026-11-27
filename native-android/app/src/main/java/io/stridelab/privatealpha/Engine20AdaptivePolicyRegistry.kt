package io.stridelab.privatealpha

/**
 * Authoritative policy and hyperparameter registry for Engine 2.0 Adaptive Intelligence
 * (`MOD-B1..B4`, `MOD-C1`, `POL-B01..B06`, `E2-01..E2-09`, Track 8 §11).
 *
 * All adaptive models operate behind Track 8 model-specific calibration & non-inferiority gates
 * and fall back cleanly to deterministic Engine 1.5-1.9 Tier A rules when data is sparse or noisy.
 */
internal object Engine20AdaptivePolicyRegistry {
    const val POLICY_VERSION = "policy-registry-2.0.0-v1-adaptive"

    // -------------------------------------------------------------------------
    // MOD-B1 / POL-B02: Scaled QR Critical Speed & D' (`Anderson 2026`, EQ-DEF-06)
    // -------------------------------------------------------------------------
    const val CS_TIME_SCALE_SECONDS = 600.0
    const val CS_MIN_TRIAL_SECONDS = 180.0
    const val CS_MAX_TRIAL_SECONDS = 900.0
    const val CS_MIN_VERIFIED_SEVERE_TRIALS = 3
    const val CS_MIN_DURATION_SPAN_SECONDS = 240.0
    const val CS_MAX_D_PRIME_REL_SE = 0.25
    const val CS_MAX_CONDITION_NUMBER = 25.0
    const val HUBER_KAPPA = 1.5

    // -------------------------------------------------------------------------
    // MOD-B2 / MOD-B3 / POL-B03: Endurance Extension & SI Cardiac Decoupling Durability
    // -------------------------------------------------------------------------
    const val DURABILITY_MIN_STEADY_MINUTES = 60
    const val DURABILITY_EARLY_ONSET_MINUTES = 75
    const val DURABILITY_HIGH_DRIFT_PCT = 5.0
    const val ENDURANCE_EXPONENT_PRIOR_GAMMA = 0.06
    const val ENDURANCE_EXPONENT_MIN = 0.03
    const val ENDURANCE_EXPONENT_MAX = 0.14

    // -------------------------------------------------------------------------
    // E2-03 / POL-B04: Log-Space Bayesian RIR-Adjusted e1RM (`Halperin 2022`)
    // -------------------------------------------------------------------------
    const val BAYESIAN_E1RM_MAX_EFFECTIVE_REPS = 12
    const val BAYESIAN_E1RM_SIGMA_FLOOR = 0.035
    const val BAYESIAN_E1RM_DAILY_DIFFUSION_VAR = 0.00004
    const val BAYESIAN_E1RM_RIR_SD_PER_STEP = 0.018
    const val BAYESIAN_E1RM_LOWER_Z_CONSERVATIVE = 1.0

    // -------------------------------------------------------------------------
    // MOD-B4 / POL-B05: Fractional Indirect-Set Accounting (`Pelland 2025/2026`)
    // -------------------------------------------------------------------------
    const val FRACTIONAL_INDIRECT_MULTIPLIER = 0.5
    const val DEFAULT_MUSCLE_MEV_FRACTIONAL_SETS = 6.0
    const val DEFAULT_MUSCLE_MRV_FRACTIONAL_SETS = 16.0
    const val LOW_TOLERANCE_MRV_SETS = 10.0
    const val HIGH_TOLERANCE_MRV_SETS = 20.0

    // -------------------------------------------------------------------------
    // E2-05 & MOD-C1 / POL-B06: Fatigue Filter & Dose-Response Kinetics (`Hellard 2006`)
    // -------------------------------------------------------------------------
    const val IMPULSE_MIN_HISTORY_DAYS = 56
    const val IMPULSE_MIN_PERFORMANCE_ANCHORS = 3
    const val TAU1_FITNESS_PRIOR_DAYS = 42.0
    const val TAU2_FATIGUE_PRIOR_DAYS = 11.0
    const val TAU1_MIN_DAYS = 28.0
    const val TAU1_MAX_DAYS = 60.0
    const val TAU2_MIN_DAYS = 5.0
    const val TAU2_MAX_DAYS = 18.0
    const val NON_FUNCTIONAL_FATIGUE_ACTION_THRESHOLD = 0.65

    // -------------------------------------------------------------------------
    // E2-09: Opt-In Bounded Autonomy Safety Ceilings
    // -------------------------------------------------------------------------
    const val AUTONOMY_MIN_CONSISTENT_DAYS = 28
    const val AUTONOMY_MAX_EASY_REDUCTION_REL = 0.10
    const val AUTONOMY_MAX_SET_BACKOFF = 1

    // -------------------------------------------------------------------------
    // Track 8 §11 Shadow-Model Promotion & Non-Inferiority Gates
    // -------------------------------------------------------------------------
    const val PROMOTION_MIN_HELDOUT_SAMPLES = 5
    const val PROMOTION_MIN_COVERAGE_90 = 0.80
    const val PROMOTION_MAX_COVERAGE_90 = 1.00
    const val PROMOTION_NON_INFERIORITY_DELTA_SAFE = 0.03
}
