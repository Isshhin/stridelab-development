package io.stridelab.privatealpha

/**
 * Authoritative central policy registry for Engine 1.5 Tier A Running Intelligence (M2-D, M3, M4).
 * Resolves POL-015 through POL-041, POL-055 through POL-057, POL-062, and POL-064 without
 * scattered magic constants.
 */
internal object Engine15RunningPolicyRegistry {
    const val POLICY_VERSION = "policy-registry-1.5.0-v2-running"

    // -------------------------------------------------------------------------
    // Calibration & Pace Band Guidance Policies (POL-015..POL-021, MOD-03..06)
    // -------------------------------------------------------------------------
    const val POL_017_RIEGEL_EXPONENT = 1.07
    const val POL_017_MIN_DISTANCE_M = 3_000.0
    const val POL_017_MAX_DISTANCE_M = 21_097.5
    const val POL_017_MAX_DISTANCE_RATIO = 4.25

    const val POL_018_FRESH_MAX_DAYS = 90L
    const val POL_018_AGING_MAX_DAYS = 180L

    const val POL_020_OPERATIONAL_THRESHOLD_REF_S = 3_240.0 // 54 min operational reference
    val POL_020_DURATION_SCALING_GAMMA: Double = 1.0 - (1.0 / POL_017_RIEGEL_EXPONENT)

    const val POL_021_GUIDANCE_BAND_FRESH = 1.00
    const val POL_021_GUIDANCE_BAND_AGING = 1.25
    const val POL_021_GUIDANCE_BAND_PROVISIONAL = 1.35

    // -------------------------------------------------------------------------
    // Workload & Multi-Stressor Policies (POL-022..POL-030)
    // -------------------------------------------------------------------------
    const val POL_022_SINGLE_SESSION_R30_CAP = 1.10
    const val POL_022_ROLLING_WINDOW_DAYS = 30L
    const val POL_023_R30_BASELINE_FLOOR_M = 4_000.0

    const val POL_024_LOW_VOL_THRESHOLD_M = 30_000.0
    const val POL_024_LOW_VOL_MINOR_REL_CAP = 0.10
    const val POL_024_LOW_VOL_MINOR_ABS_CAP_M = 3_000.0
    const val POL_024_EST_MINOR_REL_CAP = 0.06
    const val POL_024_EST_MINOR_ABS_CAP_M = 4_000.0
    const val POL_024_MIN_WEEK_STEP_LOW_DAYS_M = 1_000.0
    const val POL_024_MIN_WEEK_STEP_HIGH_DAYS_M = 1_500.0

    const val POL_025_LR_MINOR_UPPER_R30 = 1.06
    const val POL_025_LR_MAJOR_UPPER_R30 = 1.10

    const val POL_026_QUALITY_MINOR_REL_CAP = 0.10
    const val POL_026_QUALITY_MAJOR_REL_CAP = 0.20

    const val POL_027_MAX_MAJOR_STEPS_PER_WEEK = 1
    const val POL_027_MAX_MINOR_STEPS_PER_WEEK = 3

    // Non-Overridable L4A Warm-Up and Cool-Down Safety Floors (POL-028..029)
    const val POL_028_TEMPO_THRESHOLD_MIN_WU_S = 600   // 10 min
    const val POL_028_SEVERE_HILLS_FAST_MIN_WU_S = 720 // 12 min
    const val POL_028_SEVERE_HILLS_PREP_S = 180        // 3 min drills/strides prep
    const val POL_029_QUALITY_MIN_CD_S = 300           // 5 min

    const val POL_030_SOFT_LR_DURATION_CAP_S = 10_800         // 180 min
    const val POL_030_EXPERIENCED_SLOW_LR_CAP_S = 11_700      // 195 min
    const val POL_030_USER_OVERRIDE_LR_MAX_S = 12_600         // 210 min

    // -------------------------------------------------------------------------
    // Periodization, Taper & Return Policies (POL-031..POL-034, POL-055..057, POL-062)
    // -------------------------------------------------------------------------
    const val POL_031_NOVICE_OR_HYBRID_CYCLE_WEEKS = 3 // 2:1 (2 load, 1 cutback)
    const val POL_031_CONSISTENT_CYCLE_WEEKS = 4       // 3:1 (3 load, 1 cutback)
    const val POL_031_VOLUME_CUTBACK_FRACTION = 0.25   // 20-30% midpoint
    const val POL_031_QUALITY_CUTBACK_FRACTION = 0.40  // 30-50% midpoint

    const val POL_032_LOW_VOL_TAPER_MIN_DAYS = 7
    const val POL_032_LOW_VOL_TAPER_MAX_DAYS = 10
    const val POL_032_EST_SHORT_MID_TAPER_MIN_DAYS = 10
    const val POL_032_EST_SHORT_MID_TAPER_MAX_DAYS = 14
    const val POL_032_MARATHON_TAPER_MIN_DAYS = 14
    const val POL_032_MARATHON_TAPER_MAX_DAYS = 21

    // POL-033 + POL-057 gapless return buckets
    val POL_057_RETURN_ENTRY_FRACTIONS: List<Pair<IntRange, Double>> = listOf(
        0..6 to 1.00,
        7..13 to 0.90,   // 85-95%
        14..27 to 0.72,  // 65-80%
        28..55 to 0.52,  // 45-60%
        56..83 to 0.40,  // 35-45%
        84..10000 to 0.30 // 25-35%
    )

    // POL-034 Return Exit Thresholds
    const val POL_034_PATH_A_ROLLING_28D_FRACTION = 0.85
    const val POL_034_PATH_A_CURRENT_WEEK_FRACTION = 0.90
    const val POL_034_PATH_A_MIN_REBUILD_WEEKS = 3
    const val POL_034_PATH_B_MIN_STEADY_WEEKS = 4
    const val POL_034_POST_ILLNESS_MIN_QUARANTINE_DAYS = 14

    // POL-055 Confirmed-Zero Starter Ramp
    const val POL_055_W1_WU_S = 240
    const val POL_055_W1_CD_S = 240
    const val POL_055_W1_RUN_REPS = 6
    const val POL_055_W1_RUN_STEP_S = 120
    const val POL_055_W1_WALK_STEP_S = 120
    const val POL_055_NOVICE_MAX_IMPACT_RUN_DAYS = 4

    // -------------------------------------------------------------------------
    // Optimizer, Spacing & Reconciliation Budgets (POL-035..POL-041, POL-064)
    // -------------------------------------------------------------------------
    const val POL_035_HIGH_STRESS_MIN_CAL_DAYS = 2
    const val POL_035_HIGH_STRESS_MIN_ELAPSED_S = 48L * 3_600L // 48 h completion-to-start
    const val POL_035_POST_MP_LR_PREF_S = 72L * 3_600L

    const val POL_037_MIN_VIABLE_EASY_DURATION_S = 900    // 15 min
    const val POL_037_MIN_VIABLE_EASY_DISTANCE_M = 2_500.0 // 2.5 km

    const val POL_038_SAME_DAY_TRANSITION_MIN_S = 900     // 15 min
    const val POL_038_HIGH_LOW_SEPARATION_MIN_S = 21_600  // 6 h

    const val POL_039_R_WEEK_MAX = 3
    const val POL_039_B_GLOBAL_CAP = 24

    const val POL_040_K_FRONT_SIZE = 4

    // POL-064 Quantized Objective Bucket Widths for MOD-20 Two-Pass Lexicographic Comparator
    const val POL_064_BUCKET_SAFETY_OR_RESTRICTION = 1.0
    const val POL_064_BUCKET_GOAL_SHORTFALL = 0.05      // 5% goal shortfall steps
    const val POL_064_BUCKET_SPACING_PENALTY = 1.0      // 1 day / spacing step
    const val POL_064_BUCKET_VOLUME_BAND_M = 2_000.0    // 2 km band
    const val POL_064_BUCKET_DURATION_BAND_S = 600.0    // 10 min band
    const val POL_064_BUCKET_DOSE_ALIGN_PCT = 0.05      // 5% dose alignment

    fun postRaceRecoveryDays(raceGoal: String?, priority: Char = 'A', maximalObserved: Boolean = true): IntRange {
        val effectivePriority = if (priority == 'C' && maximalObserved) 'B' else priority
        return when (effectivePriority) {
            'A' -> when (raceGoal) {
                "Improve 5K" -> 3..5
                "Improve 10K" -> 5..7
                "Half marathon" -> 7..10
                "Marathon" -> 14..21
                else -> 5..7
            }
            'B' -> when (raceGoal) {
                "Marathon" -> 10..14
                "Half marathon" -> 7..10
                else -> 4..7
            }
            else -> 2..3 // controlled C-race
        }
    }
}
