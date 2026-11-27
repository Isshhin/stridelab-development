package io.stridelab.privatealpha

/**
 * Authoritative central policy registry for Engine 1.8 Hybrid Intelligence &
 * 8-Family Conditioning (Track 6 §§8, 10–14).
 */
internal object Engine18HybridPolicyRegistry {
    const val POLICY_VERSION = "policy-registry-1.8.0-v1-hybrid"

    // -------------------------------------------------------------------------
    // POL-036 / POL-038 Bidirectional Interference & High/Low Consolidation
    // -------------------------------------------------------------------------
    const val POL_036_BIDIRECTIONAL_MIN_CAL_DAYS = 2
    const val POL_036_BIDIRECTIONAL_MIN_ELAPSED_SECONDS = 24L * 3_600L // minimum lower bound
    const val POL_036_PREFERRED_ELAPSED_SECONDS = 48L * 3_600L
    const val POL_036_HIGH_LOW_MIN_GAP_SECONDS = 6L * 3_600L        // 6-8 h split
    const val POL_038_SAME_DAY_TRANSITION_MIN_SECONDS = 900          // 15 min

    // -------------------------------------------------------------------------
    // POL-048 / MOD-17 Repaired EMOM Rest Floor & Conditioning Safety
    // -------------------------------------------------------------------------
    const val POL_048_EMOM_MIN_REST_SECONDS_PER_MIN = 20.0
    const val POL_048_EMOM_MAX_WORK_SECONDS_PER_MIN = 40.0 // repaired from 45.0 per CON-22 erratum!
    const val MOD_17_EMOM_DEFAULT_TARGET_REST_SECONDS = 25.0
    const val MOD_17_EMOM_MIN_TARGET_REST_SECONDS = 20.0
    const val MOD_17_EMOM_MAX_TARGET_REST_SECONDS = 45.0
    const val POL_048_AMRAP_TARGET_RPE_MIN = 7
    const val POL_048_AMRAP_TARGET_RPE_MAX = 8

    // -------------------------------------------------------------------------
    // POL-059 Nine-Axis Cross-Discipline Stressor Limits
    // -------------------------------------------------------------------------
    const val POL_059_MAX_LOWER_BODY_MAJOR_STEPS_PER_WEEK = 1
    const val POL_059_MAX_SYSTEMIC_MAJOR_STEPS_PER_WEEK = 1
    const val POL_059_LOWER_STR_MINOR_LOAD_REL_CAP = 0.05
    const val POL_059_LOWER_STR_MAJOR_LOAD_REL_CAP = 0.10
    const val POL_059_COND_MINOR_DUR_REL_CAP = 0.10
    const val POL_059_GLOBAL_TIME_MINOR_REL_CAP = 0.15
    const val S09_MARATHON_TAPER_DROP_LOWER_STR_DAYS = 14

    // -------------------------------------------------------------------------
    // MOD-19 Leximin Shortfall Buckets & Cross-Discipline Muscle Guard
    // -------------------------------------------------------------------------
    const val MOD_19_LEXIMIN_BUCKET_WIDTH = 0.05 // 5% quantized shortfall bucket
    const val UNIFIED_LEDGER_HIGH_OVERLAP_THRESHOLD_SETS = 10
}
