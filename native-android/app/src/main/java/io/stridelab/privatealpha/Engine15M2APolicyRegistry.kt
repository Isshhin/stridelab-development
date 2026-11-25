package io.stridelab.privatealpha

/**
 * Centralized numerical policy registry for M2-A Performance Calibration (Accepted Pass R3).
 * Target checkpoint: bef05a7c60683ff03bf309884db19d44bd3718b4 (feat/freeform-logger).
 *
 * Provenance classes are explicitly separated (DEF-10):
 * 1. EXACT_DEFINITIONS: SI unit conversions and official road-race distances.
 * 2. PUBLISHED_PRIMARY_OR_CONFIRMED: Verbatim Vickers & Vertosick (2016) regression coefficients
 *    confirmed against the primary publication / Keogh et al. (2019). Never modified to tune fits.
 * 3. SECONDARY_UNVERIFIED_COURSE_DELTAS: Course-difficulty velocity deltas from secondary citations
 *    of Additional File 1 (13102_2016_52_MOESM1_ESM.docx); disabled pending direct binary
 *    .docx verification (R2-07).
 * 4. RESEARCH_APPROVED_POLICIES: Frozen constants from COACHING-POLICY-REGISTRY.md v1.1
 *    (POL-011..013, POL-015..018, POL-020..021).
 * 5. LOCAL_STRUCTURAL_GUARDS: Narrow mathematical/kinematic software bounds (zero duplication of
 *    Engine15M1PolicyRegistry constants, zero invented physiological conflict percentages).
 */
internal object Engine15M2APolicyRegistry {
    const val SPEC_VERSION: String = "1.5.0-spec-consolidated"
    const val POLICY_VERSION: String = "policy-registry-1.5.0-v1.1-m2a-r3"

    // =========================================================================
    // 1. EXACT_DEFINITIONS (SI Conversions & Standard Race Distances)
    // =========================================================================
    const val METERS_PER_MILE: Double = 1_609.344
    const val DISTANCE_5K_METERS: Double = 5_000.0
    const val DISTANCE_5MI_METERS: Double = 8_046.72
    const val DISTANCE_10K_METERS: Double = 10_000.0
    const val DISTANCE_10MI_METERS: Double = 16_093.44
    const val DISTANCE_HALF_MARATHON_METERS: Double = 21_097.5
    const val DISTANCE_MARATHON_METERS: Double = 42_195.0

    // =========================================================================
    // 2. PUBLISHED_PRIMARY_OR_CONFIRMED (Vickers & Vertosick 2016)
    // =========================================================================
    const val VV1_INTERCEPT_MPS: Double = 0.16018617
    const val VV1_RIEGEL_VELOCITY_COEFF: Double = 0.83076202
    const val VV1_MILEAGE_COEFF_PER_10MI: Double = 0.06423826

    const val VV2_INTERCEPT: Double = 1.4510756
    const val VV2_K_BETWEEN_COEFF: Double = -0.23797948
    const val VV2_MILEAGE_COEFF_PER_10MI: Double = -0.01410023

    // =========================================================================
    // 3. SECONDARY_UNVERIFIED_COURSE_DELTAS (Disabled by Default — DEF-10)
    //    Reproduced from secondary summaries of Additional File 1 (.docx);
    //    only 10-mile Fast (-0.1358) independently confirmed in Keogh et al. (2019).
    // =========================================================================
    const val VV_COURSE_ADJUSTMENTS_PRIMARY_VERIFIED: Boolean = false
    const val VV_COURSE_5K_DIFFICULT_MPS: Double = 0.1129
    const val VV_COURSE_5K_FAST_MPS: Double = -0.0238
    const val VV_COURSE_5MI_DIFFICULT_MPS: Double = 0.1090
    const val VV_COURSE_5MI_FAST_MPS: Double = -0.1550
    const val VV_COURSE_10K_DIFFICULT_MPS: Double = 0.0246
    const val VV_COURSE_10K_FAST_MPS: Double = -0.0781
    const val VV_COURSE_10MI_DIFFICULT_MPS: Double = 0.1031
    const val VV_COURSE_10MI_FAST_MPS: Double = -0.1358
    const val VV_COURSE_HM_DIFFICULT_MPS: Double = 0.0336
    const val VV_COURSE_HM_FAST_MPS: Double = -0.0978

    // =========================================================================
    // 4. RESEARCH_APPROVED_POLICIES (COACHING-POLICY-REGISTRY.md v1.1)
    // =========================================================================
    const val POL_011_SELF_REPORT_REPLACE_MIN_SPAN_DAYS: Int = 21
    const val POL_012_SELF_REPORT_REPLACE_MIN_SESSIONS: Int = 6
    const val POL_012_SELF_REPORT_REPLACE_MAX_GAP_DAYS: Int = 10

    const val POL_013_CONFLICT_LOW_RATIO: Double = 0.65
    const val POL_013_CONFLICT_HIGH_RATIO: Double = 1.50
    const val POL_013_CONSERVATIVE_SELF_REPORT_FACTOR: Double = 0.85
    const val POL_013_CONSERVATIVE_RECORDED_FACTOR: Double = 1.15
    const val POL_013_CONSERVATIVE_RECORDED_BONUS_METERS: Double = 5_000.0

    const val POL_015_VV1_MIN_DISTANCE_METERS: Double = 5_000.0
    const val POL_015_VV1_MAX_DISTANCE_METERS: Double = 21_097.5
    const val POL_015_VV1_MIN_WEEKLY_MILES: Double = 15.0
    const val POL_015_VV1_MAX_WEEKLY_MILES: Double = 75.0
    const val POL_015_VV1_MAX_SPEED_RATIO: Double = 0.98

    const val POL_016_VV2_MIN_DISTANCE_RATIO: Double = 1.5
    const val POL_016_VV2_MIN_K_BETWEEN: Double = 1.01
    const val POL_016_VV2_MAX_K_BETWEEN: Double = 1.20
    const val POL_016_VV2_MIN_K_MARATHON: Double = 1.02

    const val POL_017_RIEGEL_EXPONENT: Double = 1.07
    const val POL_017_MIN_DISTANCE_METERS: Double = 3_000.0
    const val POL_017_MAX_DISTANCE_METERS: Double = 21_097.5
    const val POL_017_MAX_DISTANCE_RATIO: Double = 4.25

    const val POL_018_BENCHMARK_FRESH_MAX_DAYS: Int = 90
    const val POL_018_BENCHMARK_AGING_MAX_DAYS: Int = 180

    const val POL_020_OPERATIONAL_THRESHOLD_REF_SECONDS: Double = 3_240.0
    const val POL_020_MIN_REFERENCE_DURATION_SECONDS: Double = 2_700.0
    const val POL_020_MAX_REFERENCE_DURATION_SECONDS: Double = 3_600.0
    const val POL_020_MIN_BENCHMARK_DURATION_SECONDS: Double = 720.0
    const val POL_020_MAX_BENCHMARK_DURATION_SECONDS: Double = 7_200.0
    val MOD_06_GAMMA_FROM_POL_017: Double = 1.0 - (1.0 / POL_017_RIEGEL_EXPONENT)

    const val POL_021_GUIDANCE_BAND_FRESH: Double = 1.00
    const val POL_021_GUIDANCE_BAND_AGING: Double = 1.25
    const val POL_021_GUIDANCE_BAND_PROVISIONAL: Double = 1.35

    // =========================================================================
    // 5. LOCAL_STRUCTURAL_GUARDS (Software / Kinematic Integrity Only — DEF-07/10)
    // =========================================================================

    // Exact certified distance match tolerance (1.0 m). GPS shortfalls such as 4,950 m
    // vs. 5,000 m are NEVER treated as DIRECT_ANCHOR; they normalize via MOD-03.
    const val DIRECT_ANCHOR_EXACT_DISTANCE_TOLERANCE_METERS: Double = 1.0

    // Standard distance label tolerance for optional published course-adjustment lookup.
    const val COURSE_TABLE_EXACT_DISTANCE_TOLERANCE_METERS: Double = 1.0

    // Kinematic identity assertion bound: |targetDistance / predictedDuration - predictedSpeed|.
    const val KINEMATIC_IDENTITY_TOLERANCE_MPS: Double = 1e-6

    // Reused directly from M1 POL-007 without duplication.
    val MAX_PLAUSIBLE_RUNNING_SPEED_MPS: Double
        get() = Engine15M1PolicyRegistry.POL_007_MAX_PLAUSIBLE_GNSS_SPEED_MPS

    // Software lower-bound sanity guard (~33:20/km) preventing numerical blowup on corrupted durations.
    const val MIN_PLAUSIBLE_RUNNING_SPEED_MPS: Double = 0.5
}
