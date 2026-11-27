package io.stridelab.privatealpha

/**
 * Authoritative central policy registry for Engine 1.7 Calisthenics Intelligence (Track 6).
 * Resolves POL-033, POL-044, POL-046, POL-047, POL-049, POL-058, and POL-061.
 */
internal object Engine17CalisthenicsPolicyRegistry {
    const val POLICY_VERSION = "policy-registry-1.7.0-v1-calisthenics"

    // -------------------------------------------------------------------------
    // POL-047 Calisthenics DAG Mastery & Eccentric Tendon Safety (McHugh 2003)
    // -------------------------------------------------------------------------
    const val POL_047_REQUIRED_CONSECUTIVE_CLEAN_SESSIONS = 2
    const val POL_047_MIN_RIR_FOR_PROMOTION = 1
    const val POL_047_MAX_BEGINNER_ECCENTRIC_SETS = 2
    const val POL_047_MAX_BEGINNER_ECCENTRIC_REPS = 3
    const val POL_047_MAX_BEGINNER_ECCENTRIC_WEEKLY_SESSIONS = 2

    // -------------------------------------------------------------------------
    // POL-061 Entry Bypass & Fresh Skill Separation
    // -------------------------------------------------------------------------
    const val POL_061_ENTRY_BYPASS_MIN_CLEAN_REPS = 3
    const val POL_061_FRESH_SKILL_MINUTES_MIN = 5
    const val POL_061_FRESH_SKILL_MINUTES_MAX = 10

    // -------------------------------------------------------------------------
    // Isometric Hold Progression (Oranchuk 2019)
    // -------------------------------------------------------------------------
    const val ISOMETRIC_STAGE_1_SECONDS = 15
    const val ISOMETRIC_STAGE_2_SECONDS = 30
    const val ISOMETRIC_STAGE_3_SECONDS = 45
    const val ISOMETRIC_TREMOR_REGRESSION_FACTOR = 0.70 // ~30% reduction on tremor/form collapse

    // -------------------------------------------------------------------------
    // POL-044 Conservative Duration Accounting Bounds (SI seconds)
    // -------------------------------------------------------------------------
    const val DYNAMIC_CADENCE_LOW_S = 3.0
    const val DYNAMIC_CADENCE_HIGH_S = 4.5
    const val ECCENTRIC_CADENCE_LOW_S = 4.5
    const val ECCENTRIC_CADENCE_HIGH_S = 6.5
    const val UNILATERAL_SWITCH_LOW_S = 10
    const val UNILATERAL_SWITCH_HIGH_S = 15
    const val EXERCISE_SETUP_LOW_S = 40
    const val EXERCISE_SETUP_HIGH_S = 65
    const val WARM_UP_MIN_S = 240
    const val WARM_UP_MAX_S = 420
    const val COOL_DOWN_MIN_S = 120
    const val COOL_DOWN_MAX_S = 240

    // -------------------------------------------------------------------------
    // POL-046 Minimal-Input Continuity & POL-049 Conditional Deload
    // -------------------------------------------------------------------------
    const val POL_046_MIN_COMPLETED_SESSIONS = 2
    const val POL_049_DELOAD_SET_CUT_FRACTION = 0.40 // 30-50% set cut
    const val POL_049_DELOAD_TARGET_RIR_MIN = 3
    const val POL_049_DELOAD_TARGET_RIR_MAX = 4

    // -------------------------------------------------------------------------
    // POL-033 + POL-058 Unified Gapless Return Reentry
    // -------------------------------------------------------------------------
    val RETURN_ENTRY_FRACTIONS: List<Pair<IntRange, Double>> = listOf(
        0..6 to 1.00,
        7..13 to 0.75,
        14..27 to 0.65,
        28..55 to 0.55,
        56..83 to 0.45,
        84..10000 to 0.40
    )

    fun specFor(objective: CalisthenicsObjective): CalisthenicsObjectiveSpec = when (objective) {
        CalisthenicsObjective.CALISTHENICS_FOUNDATION -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 6..10,
            defaultWorkingSets = 3,
            defaultRestSeconds = 90,
            targetRir = 2..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 6,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.PULL_UP_PROGRESSION -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 5..8,
            defaultWorkingSets = 3,
            defaultRestSeconds = 120,
            targetRir = 2..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 6,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.PUSH_UP_PROGRESSION -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 6..12,
            defaultWorkingSets = 3,
            defaultRestSeconds = 90,
            targetRir = 1..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 5,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.DIP_AND_UPPER_STRENGTH -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 5..10,
            defaultWorkingSets = 3,
            defaultRestSeconds = 120,
            targetRir = 2..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 5,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.CORE_AND_COMPRESSION -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 8..14,
            defaultWorkingSets = 3,
            defaultRestSeconds = 75,
            targetRir = 2..3,
            isometricSeconds = 15..35,
            skillBlockMinutes = 6,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.HANDSTAND_FOUNDATIONS -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 5..8,
            defaultWorkingSets = 3,
            defaultRestSeconds = 90,
            targetRir = 2..3,
            isometricSeconds = 15..45,
            skillBlockMinutes = 8,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.SINGLE_LEG_CONTROL -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 5..8,
            defaultWorkingSets = 3,
            defaultRestSeconds = 105,
            targetRir = 2..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 5,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.SKILL_AND_STATIC_HOLDS -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 4..8,
            defaultWorkingSets = 3,
            defaultRestSeconds = 105,
            targetRir = 2..3,
            isometricSeconds = 15..35,
            skillBlockMinutes = 8,
            deloadIntervalWeeks = 4
        )
        CalisthenicsObjective.WEIGHTED_CALISTHENICS -> CalisthenicsObjectiveSpec(
            objective = objective,
            defaultRepRange = 4..8,
            defaultWorkingSets = 3,
            defaultRestSeconds = 150,
            targetRir = 2..3,
            isometricSeconds = 20..30,
            skillBlockMinutes = 5,
            deloadIntervalWeeks = 4
        )
    }
}

internal data class CalisthenicsObjectiveSpec(
    val objective: CalisthenicsObjective,
    val defaultRepRange: IntRange,
    val defaultWorkingSets: Int,
    val defaultRestSeconds: Int,
    val targetRir: IntRange,
    val isometricSeconds: IntRange,
    val skillBlockMinutes: Int,
    val deloadIntervalWeeks: Int
)
