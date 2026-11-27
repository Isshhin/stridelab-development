package io.stridelab.privatealpha

/**
 * Centralized numerical policy registry for M2-C Exact-Date Multidimensional Workload Intelligence.
 * Baseline checkpoint: 13d0675e75d91e85920e058ce74f639869f96d27 (frozen M1, M2-A R3, M2-B v3).
 *
 * Provenance classes are strictly separated (zero scattered magic numbers):
 * 1. EXACT_DEFINITIONS: Exact SI and calendar definitions.
 * 2. PUBLISHED_DEFINITIONS_AND_OBSERVATIONAL_CUTPOINTS: Verbatim literature constants
 *    (Foster et al. 2001 CR-10 sRPE; Frandsen et al. 2025 PMC12421110 observational cutpoints).
 * 3. RESEARCH_APPROVED_POLICIES: Frozen COACHING-POLICY-REGISTRY.md v1.1 + CANONICAL-ERRATA.md
 *    constants with zero-duplication delegation to frozen M1/M2-A/M2-B registries.
 * 4. LOCAL_STRUCTURAL_GUARDS: Narrow IEEE-754 conservation, zero-mean, and overflow guards.
 * 5. UNAPPROVED_EMPIRICAL_PARAMETERS: Explicitly non-authoritative pending calibration/ratification.
 */
internal object Engine15M2CPolicyRegistry {
    const val SPEC_VERSION: String = Engine15M2APolicyRegistry.SPEC_VERSION
    const val POLICY_VERSION: String = "policy-registry-1.5.0-v1.1-m2c-v2"

    // =========================================================================
    // 1. EXACT_DEFINITIONS (SI & Calendar Identities)
    // =========================================================================
    const val SECONDS_PER_MINUTE: Double = 60.0
    const val METERS_PER_KILOMETER: Double = 1_000.0
    const val DAYS_PER_WEEK: Int = 7
    const val TRAILING_FOUR_WEEK_COUNT: Int = 4

    // =========================================================================
    // 2. PUBLISHED_DEFINITIONS_AND_OBSERVATIONAL_CUTPOINTS ([A])
    // =========================================================================

    // MOD-09: Foster et al. (2001) CR-10 Session-RPE domain [0, 10]
    const val MOD_09_CR10_MIN_RPE: Double = 0.0
    const val MOD_09_CR10_MAX_RPE: Double = 10.0

    // MOD-10: Schuster Brandt Frandsen et al. (2025), Br J Sports Med 59(17):1203-1210 (PMC12421110)
    // Observational cohort exposure boundaries (non-causal; never proof of safety or individual probability)
    const val MOD_10_OBSERVATIONAL_SMALL_SPIKE_MIN_RATIO: Double = 1.10
    const val MOD_10_OBSERVATIONAL_SMALL_SPIKE_MAX_RATIO: Double = 1.30
    const val MOD_10_OBSERVATIONAL_MODERATE_SPIKE_MAX_RATIO: Double = 2.00

    // =========================================================================
    // 3. RESEARCH_APPROVED_POLICIES (COACHING-POLICY-REGISTRY.md v1.1 + Errata)
    // =========================================================================

    // Rolling workload & quality lookback windows (exact local ISO calendar days)
    const val WINDOW_7D_DAYS: Int = 7
    const val WINDOW_28D_DAYS: Int = 28
    const val WINDOW_30D_DAYS: Int = 30
    const val WINDOW_56D_QUALITY_DAYS: Int = 56
    const val WINDOW_90D_OVERRIDE_LOOKBACK_DAYS: Int = 90

    // POL-011..013: History coverage & continuous-vs-self-report conflict arbitration (delegated to M2-A R3)
    val POL_011_SELF_REPORT_REPLACE_MIN_SPAN_DAYS: Int
        get() = Engine15M2APolicyRegistry.POL_011_SELF_REPORT_REPLACE_MIN_SPAN_DAYS

    val POL_012_SELF_REPORT_REPLACE_MIN_SESSIONS: Int
        get() = Engine15M2APolicyRegistry.POL_012_SELF_REPORT_REPLACE_MIN_SESSIONS

    val POL_012_SELF_REPORT_REPLACE_MAX_GAP_DAYS: Int
        get() = Engine15M2APolicyRegistry.POL_012_SELF_REPORT_REPLACE_MAX_GAP_DAYS

    val POL_013_CONFLICT_LOW_RATIO: Double
        get() = Engine15M2APolicyRegistry.POL_013_CONFLICT_LOW_RATIO

    val POL_013_CONFLICT_HIGH_RATIO: Double
        get() = Engine15M2APolicyRegistry.POL_013_CONFLICT_HIGH_RATIO

    val POL_013_CONSERVATIVE_SELF_REPORT_FACTOR: Double
        get() = Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_SELF_REPORT_FACTOR

    val POL_013_CONSERVATIVE_RECORDED_FACTOR: Double
        get() = Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_RECORDED_FACTOR

    val POL_013_CONSERVATIVE_RECORDED_BONUS_METERS: Double
        get() = Engine15M2APolicyRegistry.POL_013_CONSERVATIVE_RECORDED_BONUS_METERS

    // POL-014: Minetti GAP domain & eccentric downhill quarantine (delegated to M2-B v3)
    val POL_014_MIN_GRADIENT: Double
        get() = Engine15M2BPolicyRegistry.GAP_MIN_GRADE

    val POL_014_MAX_GRADIENT: Double
        get() = Engine15M2BPolicyRegistry.GAP_MAX_GRADE

    val POL_014_ECCENTRIC_QUARANTINE_GRADIENT: Double
        get() = Engine15M2BPolicyRegistry.GAP_MIN_METABOLIC_GRADE

    // POL-022 (L4B whitelisted guardrail + L7 observational signal; replaces erroneous CON-10 POL-007 ref)
    const val POL_022_SINGLE_SESSION_GUARDRAIL_RATIO: Double = 1.10

    // POL-023 (L4A binding floor; replaces erroneous MOD-10 POL-021 ref per CANONICAL-ERRATA.md §1)
    const val POL_023_SINGLE_SESSION_BASELINE_FLOOR_METERS: Double = 4_000.0

    // POL-030 (L4B whitelisted soft long-run duration caps for MOD-11 stopping contracts)
    const val POL_030_SOFT_LONG_RUN_DURATION_CAP_SECONDS: Double = 180.0 * 60.0
    const val POL_030_EXPERIENCED_SLOW_MARATHONER_CAP_SECONDS: Double = 195.0 * 60.0
    const val POL_030_USER_OVERRIDE_MAX_DURATION_SECONDS: Double = 210.0 * 60.0

    // POL-033: Unified gapless interruption bucket inclusive upper bounds (days)
    const val POL_033_BUCKET_0_6_MAX_DAYS: Int = 6
    const val POL_033_BUCKET_7_13_MAX_DAYS: Int = 13
    const val POL_033_BUCKET_14_27_MAX_DAYS: Int = 27
    const val POL_033_BUCKET_28_55_MAX_DAYS: Int = 55
    const val POL_033_BUCKET_56_83_MAX_DAYS: Int = 83
    const val POL_033_BUCKET_84_PLUS_MIN_DAYS: Int = 84

    // POL-034 / CON-02: Post-illness HR minimum quarantine >= 14 d + stability exit (delegated to M2-B v3)
    val POL_034_POST_ILLNESS_MIN_HR_QUARANTINE_DAYS: Long
        get() = Engine15M2BPolicyRegistry.POST_ILLNESS_MIN_QUARANTINE_DAYS

    // Delegated M1 / M2-A guards (zero duplication)
    val POL_001_MIN_TEMPORAL_OVERLAP_RATIO: Double
        get() = Engine15M1PolicyRegistry.POL_001_MIN_TEMPORAL_OVERLAP_RATIO

    val POL_002_MAX_DURATION_DIFF_RATIO: Double
        get() = Engine15M1PolicyRegistry.POL_002_MAX_DURATION_DIFF_RATIO

    val POL_002_MAX_DISTANCE_DIFF_RATIO: Double
        get() = Engine15M1PolicyRegistry.POL_002_MAX_DISTANCE_DIFF_RATIO

    val MAX_PLAUSIBLE_RUNNING_SPEED_MPS: Double
        get() = Engine15M2APolicyRegistry.MAX_PLAUSIBLE_RUNNING_SPEED_MPS

    val MIN_PLAUSIBLE_RUNNING_SPEED_MPS: Double
        get() = Engine15M2APolicyRegistry.MIN_PLAUSIBLE_RUNNING_SPEED_MPS

    // =========================================================================
    // 4. LOCAL_STRUCTURAL_GUARDS (IEEE-754 Rounding / Overflow / Zero-Mean Only)
    // =========================================================================
    const val ZERO_MEAN_CV_GUARD_METERS: Double = 1e-12
    const val DURATION_CONSERVATION_TOLERANCE_SECONDS: Double = 1e-6
    const val DISTANCE_CONSERVATION_TOLERANCE_METERS: Double = 1e-6
    const val KINEMATIC_IDENTITY_TOLERANCE_MPS: Double = 1e-6
    const val MAX_PLAUSIBLE_SESSION_DURATION_SECONDS: Double = 7.0 * 86_400.0

    // =========================================================================
    // 5. UNAPPROVED_EMPIRICAL_PARAMETERS (Explicitly Non-Authoritative — [C])
    //    Retained strictly false; M2-C never invents actionable cutoffs for these.
    // =========================================================================
    const val WEEKLY_CV_VOLATILITY_THRESHOLD_APPROVED: Boolean = false
    const val DOWNHILL_ELEVATION_SMOOTHING_POLICY_APPROVED: Boolean = false
    const val DOWNHILL_SAMPLING_GAP_POLICY_APPROVED: Boolean = false
    const val QUALITY_FAMILY_UNLOCK_COUNT_POLICY_APPROVED: Boolean = false
    const val INTERNAL_LOAD_SYSTEMIC_CEILING_POLICY_APPROVED: Boolean = false
    const val TRUSTED_M9_EXECUTION_CUSTODY_ACTIVE: Boolean = false
    const val TRUSTED_M9_RESTRICTION_CLEARANCE_ACTIVE: Boolean = false
}
