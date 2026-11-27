package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal data class HybridInterferenceSession(
    val id: String,
    val date: LocalDate,
    val discipline: String,
    val isRunningQualityOrLong: Boolean,
    val isHeavyOrEccentricLowerResistance: Boolean,
    val isUpperAxialCompound: Boolean,
    val isHighStressConditioning: Boolean,
    val workingSetsPerLowerPattern: Int = 3,
    val minRir: Int = 2,
    val plannedStartUtc: Instant? = null,
    val durationMaxSeconds: Long = 3600L
) {
    val isDemandingLower: Boolean
        get() = isRunningQualityOrLong || isHeavyOrEccentricLowerResistance || isHighStressConditioning
}

internal data class BidirectionalInterferenceVerdict(
    val conflict: Boolean,
    val permittedViaHighLowConcentration: Boolean,
    val recommendedOrderNote: String?,
    val reasonCode: String
)

internal data class FixedCommitmentResolution(
    val bidirectionalCollisionImpossible: Boolean,
    val zeroConflictHardRunWeekdays: Set<Int>,
    val optionAZeroConflict: String,
    val optionBPrimaryException: String?,
    val optionCScheduleEdit: String
)

internal data class HybridWeekTransitionInput(
    val runTransition: RunningWeekTransitionInput? = null,
    // Axis 4: Correlated Lower-Body Strength Bundle (load + tonnage + %1RM counted ONCE)
    val lowerStrengthLoadRelDelta: Double = 0.0,
    val lowerStrengthUsedOneSetOrPartialStep: Boolean = false,
    val lowerStrengthMinRir: Int = 2,
    // Axis 5: Lower-Body Strength Volume / Novel Eccentric (independent of Axis 4!)
    val lowerStrengthAddedDirectSets: Int = 0,
    val introducedNovelLowerEccentric: Boolean = false,
    // Axis 6: Upper Strength / Calisthenics
    val upperAddedSets: Int = 0,
    val upperSmallStep: Boolean = false,
    val newDifficultCalisthenicsDagNode: Boolean = false,
    val upperPushedNearFailureWithBigVolume: Boolean = false,
    // Axis 7: Conditioning Density / New Family
    val conditioningDurationRelDelta: Double = 0.0,
    val conditioningAddedRounds: Int = 0,
    val isNewHiitOrMixedModalFamily: Boolean = false,
    val isLowerBodyConditioningChange: Boolean = false,
    // Axis 8: Skill Difficulty
    val addedSkillHoldSeconds: Int = 0,
    val isNewHighCoordinationSkillNode: Boolean = false,
    // Axis 9: Global Frequency & Total Time
    val deltaTotalDaysPerWeek: Int = 0,
    val globalTotalTimeRelDelta: Double = 0.0
)

internal data class HybridMultiStressorEvaluation(
    val lowerBodyMajorCount: Int,
    val systemicMajorCount: Int,
    val upperBodyMinorActive: Boolean,
    val valid: Boolean,
    val violationReason: String?
)

internal enum class LowerStrengthTaperAction {
    NORMAL_PROGRESSION,
    HOLD_CONSOLIDATE_MAINTAIN, // MD-15 when running is MAJOR_STEP
    DROP_HEAVY_LOWER_FOR_TAPER // S09 inside final 10-14d marathon/HM taper
}

internal data class PerDisciplineContinuityReport(
    val regimeByDiscipline: Map<String, String>,
    val crossModalRunningTissueMismatchWarning: String?
)

/**
 * Engine 1.8 Hybrid Coordination Engine (Track 6 §§10, 12–14):
 * - POL-036 bidirectional interference + configurable AM/PM High/Low consolidation
 * - S18 fixed-commitment conflict precedence (Option A / Option B / Option C)
 * - POL-059 unified 9-axis cross-discipline stressor policy (load+tonnage+%1RM counted ONCE!)
 * - MD-15 running MAJOR_STEP lower-body hold & S09 marathon taper lower-body drop
 * - MD-06 aggregated cross-discipline UnifiedLocalMuscleLedger (zero hidden overlap)
 * - MD-13 independent per-discipline ContinuityRegime & S17 cross-modal tissue mismatch
 */
internal object Engine18HybridCoordinationEngine {

    /** POL-036 / MD-11 bidirectional spacing and configurable High/Low evaluation. */
    fun evaluateBidirectionalInterference(
        a: HybridInterferenceSession,
        b: HybridInterferenceSession,
        allowHighLowConcentration: Boolean = false,
        primaryDiscipline: String = "run",
        missingOrUnreportedRecovery: Boolean = false
    ): BidirectionalInterferenceVerdict {
        if (a.id == b.id || a.discipline == b.discipline) {
            return BidirectionalInterferenceVerdict(false, false, null, "NO_CROSS_DISCIPLINE_CONFLICT")
        }
        // Upper-body heavy axial/grip lifts still carry real systemic fatigue (not zero interference)
        if ((a.isUpperAxialCompound && b.isRunningQualityOrLong) ||
            (b.isUpperAxialCompound && a.isRunningQualityOrLong)
        ) {
            if (a.date == b.date && allowHighLowConcentration) {
                val first = if (a.discipline == primaryDiscipline) a.discipline else b.discipline
                return BidirectionalInterferenceVerdict(
                    conflict = false,
                    permittedViaHighLowConcentration = true,
                    recommendedOrderNote = "Order primary discipline ($first) first; separate axial strength and key run by >= 6 h.",
                    reasonCode = "POL_036_AXIAL_HIGH_LOW_ORDERED"
                )
            }
        }

        if (!a.isDemandingLower || !b.isDemandingLower) {
            return BidirectionalInterferenceVerdict(false, false, null, "COMPATIBLE_NON_LOWER_PAIRING")
        }

        val (first, second) = if (a.date <= b.date) a to b else b to a
        val sameDay = first.date == second.date
        val strPeer = listOf(first, second).firstOrNull { it.discipline in setOf("str", "cal") }
        val isMedMaintStrength = strPeer != null &&
            strPeer.workingSetsPerLowerPattern <= 2 &&
            strPeer.minRir >= 3

        if (sameDay && allowHighLowConcentration && !missingOrUnreportedRecovery && isMedMaintStrength) {
            val start1 = first.plannedStartUtc
            val start2 = second.plannedStartUtc
            val gapOk = if (start1 != null && start2 != null) {
                val end1 = start1.plusSeconds(first.durationMaxSeconds)
                abs(ChronoUnit.SECONDS.between(end1, start2)) >= Engine18HybridPolicyRegistry.POL_036_HIGH_LOW_MIN_GAP_SECONDS
            } else true
            if (gapOk) {
                return BidirectionalInterferenceVerdict(
                    conflict = false,
                    permittedViaHighLowConcentration = true,
                    recommendedOrderNote = "Hard-Day Concentration (High/Low): run $primaryDiscipline FIRST in AM, maintenance strength >= 6 h later.",
                    reasonCode = "POL_036_HIGH_LOW_PERMITTED"
                )
            }
        }

        val calGap = abs(ChronoUnit.DAYS.between(first.date, second.date)).toInt()
        val elapsedGapOk = if (first.plannedStartUtc != null && second.plannedStartUtc != null) {
            val end1 = first.plannedStartUtc.plusSeconds(first.durationMaxSeconds)
            ChronoUnit.SECONDS.between(end1, second.plannedStartUtc) >= Engine18HybridPolicyRegistry.POL_036_BIDIRECTIONAL_MIN_ELAPSED_SECONDS
        } else {
            calGap >= Engine18HybridPolicyRegistry.POL_036_BIDIRECTIONAL_MIN_CAL_DAYS
        }

        return if (!elapsedGapOk) {
            BidirectionalInterferenceVerdict(
                conflict = true,
                permittedViaHighLowConcentration = false,
                recommendedOrderNote = null,
                reasonCode = "POL_036_BIDIRECTIONAL_CONFLICT"
            )
        } else {
            BidirectionalInterferenceVerdict(false, false, null, "POL_036_SPACING_SATISFIED")
        }
    }

    /** S18 fixed-commitment conflict analysis (e.g. fixed Mon & Thu heavy lower vs. 2 hard runs). */
    fun resolveFixedCommitmentConflict(
        fixedLowerStrengthWeekdays: Set<Int>,
        desiredHardRunsPerWeek: Int,
        availableWeekdays: Set<Int>
    ): FixedCommitmentResolution {
        val cleanDays = availableWeekdays.filter { dow ->
            fixedLowerStrengthWeekdays.none { fixed ->
                val diff = kotlin.math.min(abs(dow - fixed), 7 - abs(dow - fixed))
                diff < Engine18HybridPolicyRegistry.POL_036_BIDIRECTIONAL_MIN_CAL_DAYS
            }
        }.toSet()
        val impossible = cleanDays.size < desiredHardRunsPerWeek
        return FixedCommitmentResolution(
            bidirectionalCollisionImpossible = impossible,
            zeroConflictHardRunWeekdays = cleanDays,
            optionAZeroConflict = if (cleanDays.isNotEmpty()) {
                "Option A (Zero-Conflict): schedule 1 hard run on clean day(s) ${cleanDays.sorted()} with easy aerobic runs elsewhere."
            } else {
                "Option A: convert running days to easy aerobic until a clean buffer opens."
            },
            optionBPrimaryException = if (impossible) {
                "Option B (Primary-Goal Exception): keep wed/sat hard runs and trim fixed secondary strength sets by 30% at 3 RIR."
            } else null,
            optionCScheduleEdit = "Option C (Schedule Edit): shift fixed heavy lower-body strength to maximize 48 h gaps (e.g., Mon and Fri)."
        )
    }

    /** POL-059 Nine-Axis Cross-Discipline Stressor Policy (zero double-counting of load+tonnage+%1RM). */
    fun evaluateMultiDisciplineTransition(input: HybridWeekTransitionInput): HybridMultiStressorEvaluation {
        var lowerMajor = 0
        var systemicMajor = 0

        if (input.runTransition != null) {
            val runEval = Engine15RunningProgressionEngine.evaluateMultiStressorTransition(input.runTransition)
            for ((axis, cls) in runEval.classifications) {
                if (cls == StressorStepClass.MAJOR_STEP) {
                    systemicMajor++
                    if (axis.isLowerBodyAxis) lowerMajor++
                }
            }
        }

        // Axis 4: Correlated Lower Strength Bundle (load/tonnage/%1RM counted ONCE!)
        val axis4Major = input.lowerStrengthLoadRelDelta > Engine18HybridPolicyRegistry.POL_059_LOWER_STR_MINOR_LOAD_REL_CAP + 1e-6 ||
            input.lowerStrengthMinRir <= 1
        if (axis4Major) {
            lowerMajor++
            systemicMajor++
        }

        // Axis 5: Lower Strength Volume / Novel Eccentric (independent of Axis 4!)
        val axis5Major = input.lowerStrengthAddedDirectSets >= 2 || input.introducedNovelLowerEccentric
        if (axis5Major) {
            lowerMajor++
            systemicMajor++
        }

        // Axis 6: Upper Strength / Calisthenics
        val axis6Major = input.newDifficultCalisthenicsDagNode || input.upperPushedNearFailureWithBigVolume
        val upperMinor = !axis6Major && (input.upperAddedSets == 1 || input.upperSmallStep)
        if (axis6Major) systemicMajor++

        // Axis 7: Conditioning
        val axis7Major = input.isNewHiitOrMixedModalFamily ||
            input.conditioningDurationRelDelta > Engine18HybridPolicyRegistry.POL_059_COND_MINOR_DUR_REL_CAP + 1e-6
        if (axis7Major) {
            systemicMajor++
            if (input.isLowerBodyConditioningChange) lowerMajor++
        }

        // Axis 8: Skill
        if (input.isNewHighCoordinationSkillNode) systemicMajor++

        // Axis 9: Global Frequency / Time
        if (input.deltaTotalDaysPerWeek >= 1 ||
            input.globalTotalTimeRelDelta > Engine18HybridPolicyRegistry.POL_059_GLOBAL_TIME_MINOR_REL_CAP + 1e-6
        ) {
            systemicMajor++
        }

        val valid = lowerMajor <= Engine18HybridPolicyRegistry.POL_059_MAX_LOWER_BODY_MAJOR_STEPS_PER_WEEK &&
            systemicMajor <= Engine18HybridPolicyRegistry.POL_059_MAX_SYSTEMIC_MAJOR_STEPS_PER_WEEK
        val reason = if (!valid) {
            "POL-059 limit exceeded: lowerMajor=$lowerMajor (max 1), systemicMajor=$systemicMajor (max 1)."
        } else null
        return HybridMultiStressorEvaluation(lowerMajor, systemicMajor, upperMinor, valid, reason)
    }

    /** MD-15 / S09 lower-body strength hold during MAJOR_STEP running or final marathon/HM taper. */
    fun shouldHoldOrDropLowerStrength(
        isRunningMajorStepWeek: Boolean,
        daysToPrimaryRace: Int?,
        raceGoal: String?
    ): LowerStrengthTaperAction = when {
        daysToPrimaryRace != null &&
            daysToPrimaryRace in 0..Engine18HybridPolicyRegistry.S09_MARATHON_TAPER_DROP_LOWER_STR_DAYS &&
            raceGoal in setOf("Marathon", "Half marathon") ->
            LowerStrengthTaperAction.DROP_HEAVY_LOWER_FOR_TAPER
        isRunningMajorStepWeek ->
            LowerStrengthTaperAction.HOLD_CONSOLIDATE_MAINTAIN
        else ->
            LowerStrengthTaperAction.NORMAL_PROGRESSION
    }

    /** MD-06 unified cross-discipline muscle aggregation (Strength + Calisthenics + Conditioning). */
    fun aggregateWeeklyUnifiedLedger(
        accountId: String,
        weekStart: LocalDate,
        weekEnd: LocalDate,
        ledgers: List<UnifiedLocalMuscleLedger>
    ): Pair<UnifiedLocalMuscleLedger, Set<String>> {
        val merged = linkedMapOf<String, MuscleGroupExposure>()
        for (ledger in ledgers) {
            for ((muscleId, exp) in ledger.byMuscle) {
                merged[muscleId] = merged[muscleId]?.merge(exp) ?: exp
            }
        }
        val overloaded = merged.filterValues {
            it.directHardSets >= Engine18HybridPolicyRegistry.UNIFIED_LEDGER_HIGH_OVERLAP_THRESHOLD_SETS
        }.keys
        return UnifiedLocalMuscleLedger(accountId, weekStart, weekEnd, isProjected = true, byMuscle = merged) to overloaded
    }

    /** MD-13 / S16 / S17 independent per-discipline return state without cross-discipline contamination. */
    fun evaluatePerDisciplineContinuity(
        gapDaysByDiscipline: Map<String, Int>,
        cyclingMinutesPerWeek: Int = 0,
        runningWeeklyMeters: Double = 0.0
    ): PerDisciplineContinuityReport {
        val regimes = gapDaysByDiscipline.mapValues { (_, gapDays) ->
            when {
                gapDays >= 14 -> "REBUILD_REENTRY_${gapDays}D"
                gapDays >= 7 -> "SHORT_BREAK_REENTRY"
                else -> "ACTIVE_CONSISTENT"
            }
        }
        val mismatch = if (cyclingMinutesPerWeek >= 120 && runningWeeklyMeters < 8_000.0) {
            "CROSS_MODAL_RUNNING_TISSUE_MISMATCH: Aerobic cycling tolerance does not transfer to running bone/tendon impact; cap early run mileage conservatively."
        } else null
        return PerDisciplineContinuityReport(regimes, mismatch)
    }
}
