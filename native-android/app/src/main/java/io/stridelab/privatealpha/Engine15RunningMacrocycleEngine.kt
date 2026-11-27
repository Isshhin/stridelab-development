package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp

internal enum class MacrocycleDayPhase {
    REENTRY,
    FOUNDATION,
    GENERAL_PREP,
    SPECIFIC_BUILD,
    PEAK_PREPARATION,
    TAPER,
    RACE_EXECUTION,
    POST_RACE_TRANSITION,
    MINI_FOUNDATION_RUN_THROUGH
}

internal enum class TaperCurveKind {
    EXPONENTIAL,
    LINEAR,
    STEP
}

internal data class PhaseSpan(
    val phase: MacrocycleDayPhase,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val isPartialMicrocycleStart: Boolean = false
) {
    val dayCount: Int get() = (ChronoUnit.DAYS.between(startDate, endDateInclusive).toInt() + 1).coerceAtLeast(0)
}

internal data class MacrocycleDayPartition(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val totalDays: Int,
    val spans: List<PhaseSpan>,
    val postRaceExtension: PhaseSpan?,
    val decomposedSequentialBlocks: Int,
    val daySumConserved: Boolean
)

/** 7-field separated taper & calendar-week race exposure account (MOD-15, CON-14). */
internal data class RaceWeekAndTaperAccount(
    val preTaperReferenceMeters: Double,
    val referenceWeekNumbersUsed: List<Int>,
    val referenceBasis: String,
    val plannedPreRaceTrainingMeters: Double,
    val raceDistanceMeters: Double,
    val raceExpectedDurationRangeSeconds: Pair<Double, Double>?,
    val raceWarmUpAndCooldownMeters: Double,
    val plannedPostRaceTrainingMeters: Double,
    val plannedTaperReductionPct: Double,
    val postRaceRecoveryDaysRecommended: IntRange
) {
    val totalRaceWeekExposureMeters: Double
        get() = plannedPreRaceTrainingMeters + raceDistanceMeters + raceWarmUpAndCooldownMeters
    val calendarWeekTotalExposureMeters: Double
        get() = totalRaceWeekExposureMeters + plannedPostRaceTrainingMeters
}

internal enum class CalendarScheduleFeasibility {
    FEASIBLE, CONSTRAINED_ALTERNATIVES, INFEASIBLE_ABSTAIN
}

internal enum class WorkloadProgressionFeasibility {
    RECOMMENDED_REACHABLE, CONSERVATIVE_PARTIAL_BUILD, UNSAFE_COMPRESSION_REJECTED
}

internal enum class EventCompletionPreparation {
    WELL_PREPARED, COMPLETION_POSSIBLE_WITH_STRATEGY, INSUFFICIENT_PREPARATION, UNVERIFIED_PACE_UNKNOWN
}

internal enum class CurrentPerformancePlausibility {
    WITHIN_CURRENT_BAND, MODEST_STRETCH, LARGE_GAP_UNVERIFIED, UNASSESSABLE_NO_BENCHMARK
}

internal enum class FutureImprovementUncertainty {
    MODERATE, HIGH
}

/** 5-dimension goal feasibility classification (Track 4 Section 4). */
internal data class FiveDimGoalFeasibility(
    val calendarFeasibility: CalendarScheduleFeasibility,
    val workloadProgressionFeasibility: WorkloadProgressionFeasibility,
    val eventCompletionPreparation: EventCompletionPreparation,
    val currentPerformancePlausibility: CurrentPerformancePlausibility,
    val futureImprovementUncertainty: FutureImprovementUncertainty,
    val notes: List<String>
)

internal enum class MultiRaceConflictKind {
    NONE,
    DUAL_A_RACE_TOO_CLOSE,
    MARATHONS_TOO_CLOSE,
    RACE_COLLIDES_WITH_LONG_OR_QUALITY,
    C_RACE_MAXIMAL_OBSERVED
}

internal data class MultiRaceConflictDecision(
    val conflictKind: MultiRaceConflictKind,
    val blockedDualPeak: Boolean,
    val recoveryDaysAssigned: IntRange,
    val guidance: String
)

/**
 * M4 Running Macrocycle Periodization Engine:
 * - Exact disjoint day-conserved phase partition (PartialMicrocycle, dedicated 1-day RaceExecution, <4w & >24w)
 * - MOD-14 Exponential / Linear / Step formal taper decay off V_peak_ref = mean(last 2 loading weeks)
 * - 7-field Race-Week vs. Taper exposure conservation (CON-14, MOD-15)
 * - 5-dimension Goal Feasibility & POL-031 2:1/3:1 cutback selector
 * - POL-062 Multi-Race Conflict Matrix & observed-effort C-race recovery
 */
internal object Engine15RunningMacrocycleEngine {

    fun recommendedCutbackCadence(
        experience: Int,
        returning: Boolean,
        isHybridOrMultiDiscipline: Boolean
    ): Int = if (experience == 0 || returning || isHybridOrMultiDiscipline) {
        Engine15RunningPolicyRegistry.POL_031_NOVICE_OR_HYBRID_CYCLE_WEEKS // 2:1
    } else {
        Engine15RunningPolicyRegistry.POL_031_CONSISTENT_CYCLE_WEEKS // 3:1
    }

    fun recommendedTaperDays(raceGoal: String?, weeklyMeters: Double, experience: Int): Int = when (raceGoal) {
        "Marathon" -> if (weeklyMeters < 35_000.0 || experience == 0) 10
        else if (experience == 1) Engine15RunningPolicyRegistry.POL_032_MARATHON_TAPER_MIN_DAYS
        else 18
        "Half marathon" -> if (weeklyMeters < 30_000.0 || experience == 0) 10
        else Engine15RunningPolicyRegistry.POL_032_EST_SHORT_MID_TAPER_MAX_DAYS
        "Improve 10K" -> if (weeklyMeters < 25_000.0 || experience == 0) 8 else 12
        "Improve 5K" -> if (experience == 0) Engine15RunningPolicyRegistry.POL_032_LOW_VOL_TAPER_MIN_DAYS else 10
        else -> 0
    }

    fun computePreTaperReferenceMeters(
        priorWeeks: List<Pair<Int, Double>>,
        isRecoveryFlags: List<Boolean>
    ): Pair<Double, List<Int>> {
        val loadingPairs = priorWeeks.filterIndexed { idx, _ -> !isRecoveryFlags.getOrElse(idx) { false } }
        val chosen = when {
            loadingPairs.size >= 2 -> loadingPairs.takeLast(2)
            loadingPairs.size == 1 -> loadingPairs
            else -> priorWeeks.takeLast(2)
        }
        val ref = if (chosen.isEmpty()) 0.0 else chosen.map { it.second }.average()
        return ref to chosen.map { it.first }
    }

    /**
     * MOD-14 formal taper sequence for weeks k = 1 .. taperWeeks.
     * Enforces strictly non-increasing positive budgets anchored on V_peak_ref.
     */
    fun evaluateFormalTaperSequence(
        curve: TaperCurveKind,
        vPeakRefMeters: Double,
        taperWeeks: Int,
        parameter: Double
    ): List<Double> {
        require(vPeakRefMeters.isFinite() && vPeakRefMeters > 0.0) { "V_peak_ref must be > 0" }
        require(taperWeeks in 1..4) { "taperWeeks must be in 1..4" }
        require(parameter.isFinite() && parameter > 0.0) { "Taper parameter must be > 0" }

        val values = (1..taperWeeks).map { k ->
            val v = when (curve) {
                TaperCurveKind.EXPONENTIAL -> vPeakRefMeters * exp(-parameter * k)
                TaperCurveKind.LINEAR -> {
                    require(parameter <= 1.0 / taperWeeks + 1e-9) { "Linear alpha must satisfy alpha <= 1/K" }
                    vPeakRefMeters * (1.0 - parameter * k)
                }
                TaperCurveKind.STEP -> {
                    require(parameter <= 1.0) { "Step beta must satisfy beta <= 1.0" }
                    vPeakRefMeters * (1.0 - parameter)
                }
            }
            v.coerceAtLeast(0.0)
        }
        for (i in 0 until values.lastIndex) {
            require(values[i + 1] <= values[i] + 1e-6 && values[i] < vPeakRefMeters + 1e-6) {
                "MOD-14 taper sequence must remain non-increasing below V_peak_ref"
            }
        }
        return values
    }

    /** Exact disjoint day-conserved macrocycle partition from [startDate] to [endDateInclusive]. */
    fun buildDayConservedPartition(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        raceGoal: String?,
        weeklyMeters: Double,
        experience: Int,
        returning: Boolean,
        includePostRaceExtensionDays: Int = 0
    ): MacrocycleDayPartition {
        require(!endDateInclusive.isBefore(startDate)) { "endDate must be on or after startDate" }
        val totalDays = ChronoUnit.DAYS.between(startDate, endDateInclusive).toInt() + 1
        val hasRace = raceGoal in setOf("Improve 5K", "Improve 10K", "Half marathon", "Marathon")
        val spans = mutableListOf<PhaseSpan>()

        val sequentialBlocks = if (totalDays > 168) 2 else 1
        val taperDaysRaw = if (hasRace) recommendedTaperDays(raceGoal, weeklyMeters, experience) else 0
        val raceExecDays = if (hasRace) 1 else 0
        val taperDays = if (hasRace) taperDaysRaw.coerceAtMost((totalDays - raceExecDays - 1).coerceAtLeast(0)) else 0
        val prepDays = totalDays - raceExecDays - taperDays

        var cursor = startDate
        val firstChunkPartial = startDate.dayOfWeek.value != 1
        if (totalDays < 28) {
            if (prepDays > 0) {
                val endPrep = cursor.plusDays(prepDays - 1L)
                val phase = if (experience == 0 || returning) MacrocycleDayPhase.MINI_FOUNDATION_RUN_THROUGH else MacrocycleDayPhase.PEAK_PREPARATION
                spans += PhaseSpan(phase, cursor, endPrep, isPartialMicrocycleStart = firstChunkPartial)
                cursor = endPrep.plusDays(1)
            }
        } else {
            val reentryDays = if (returning && prepDays >= 21) minOf(14, prepDays / 3) else 0
            val remAfterReentry = prepDays - reentryDays
            val foundationDays = if ((experience == 0 || weeklyMeters < 20_000.0) && remAfterReentry >= 21) {
                (remAfterReentry * 0.40).toInt().coerceAtLeast(7)
            } else 0
            val remAfterFound = remAfterReentry - foundationDays
            val specificDays = if (hasRace && remAfterFound >= 21) (remAfterFound * 0.45).toInt() else 0
            val peakDays = if (hasRace && remAfterFound >= 35 && experience > 0 && !returning) {
                minOf(14, (remAfterFound * 0.15).toInt())
            } else 0
            val generalDays = (remAfterFound - specificDays - peakDays).coerceAtLeast(0)

            fun pushSpan(phase: MacrocycleDayPhase, days: Int) {
                if (days <= 0) return
                val end = cursor.plusDays(days - 1L)
                spans += PhaseSpan(phase, cursor, end, isPartialMicrocycleStart = (cursor == startDate && firstChunkPartial))
                cursor = end.plusDays(1)
            }
            pushSpan(MacrocycleDayPhase.REENTRY, reentryDays)
            pushSpan(MacrocycleDayPhase.FOUNDATION, foundationDays)
            pushSpan(MacrocycleDayPhase.GENERAL_PREP, generalDays)
            pushSpan(MacrocycleDayPhase.SPECIFIC_BUILD, specificDays)
            pushSpan(MacrocycleDayPhase.PEAK_PREPARATION, peakDays)
        }

        if (taperDays > 0) {
            val endTaper = cursor.plusDays(taperDays - 1L)
            spans += PhaseSpan(MacrocycleDayPhase.TAPER, cursor, endTaper)
            cursor = endTaper.plusDays(1)
        }
        if (raceExecDays > 0 && !cursor.isAfter(endDateInclusive)) {
            spans += PhaseSpan(MacrocycleDayPhase.RACE_EXECUTION, endDateInclusive, endDateInclusive)
        }

        val postExt = if (hasRace && includePostRaceExtensionDays > 0) {
            PhaseSpan(
                phase = MacrocycleDayPhase.POST_RACE_TRANSITION,
                startDate = endDateInclusive.plusDays(1),
                endDateInclusive = endDateInclusive.plusDays(includePostRaceExtensionDays.toLong())
            )
        } else null

        val coveredDays = spans.sumOf { it.dayCount }
        return MacrocycleDayPartition(
            startDate = startDate,
            endDateInclusive = endDateInclusive,
            totalDays = totalDays,
            spans = spans,
            postRaceExtension = postExt,
            decomposedSequentialBlocks = sequentialBlocks,
            daySumConserved = (coveredDays == totalDays)
        )
    }

    /** Computes exact 7-field Race-Week & Taper account with strict separation of race distance. */
    fun buildRaceWeekAccount(
        vPeakRefMeters: Double,
        referenceWeekNumbers: List<Int>,
        referenceBasis: String,
        plannedPreRaceTrainingMeters: Double,
        raceDistanceMeters: Double,
        raceWarmUpAndCooldownMeters: Double,
        plannedPostRaceTrainingMeters: Double,
        raceSpeedBandMps: Pair<Double, Double>?,
        raceGoal: String?
    ): RaceWeekAndTaperAccount {
        val reductionPct = if (vPeakRefMeters > 0.0) {
            ((1.0 - (plannedPreRaceTrainingMeters / vPeakRefMeters)) * 100.0).coerceIn(-100.0, 100.0)
        } else 0.0
        val expectedDurRange = raceSpeedBandMps?.let { (vLo, vHi) ->
            if (vLo > 0.0 && vHi >= vLo) (raceDistanceMeters / vHi) to (raceDistanceMeters / vLo) else null
        }
        return RaceWeekAndTaperAccount(
            preTaperReferenceMeters = vPeakRefMeters,
            referenceWeekNumbersUsed = referenceWeekNumbers,
            referenceBasis = referenceBasis,
            plannedPreRaceTrainingMeters = plannedPreRaceTrainingMeters,
            raceDistanceMeters = raceDistanceMeters,
            raceExpectedDurationRangeSeconds = expectedDurRange,
            raceWarmUpAndCooldownMeters = raceWarmUpAndCooldownMeters,
            plannedPostRaceTrainingMeters = plannedPostRaceTrainingMeters,
            plannedTaperReductionPct = reductionPct,
            postRaceRecoveryDaysRecommended = Engine15RunningPolicyRegistry.postRaceRecoveryDays(raceGoal, 'A')
        )
    }

    /** 5-dimension independent goal feasibility classifier (Track 4 Section 4). */
    fun classifyGoalFeasibility(
        request: PlanRequest,
        hasHardClinicalHold: Boolean,
        paceIsUnknown: Boolean,
        projectedPeakWeeklyMeters: Double,
        targetRaceGoalTimeSeconds: Double? = null,
        currentDemonstratedGoalBandSeconds: Pair<Double, Double>? = null
    ): FiveDimGoalFeasibility {
        val notes = mutableListOf<String>()
        val calFeas = when {
            hasHardClinicalHold || request.days.isEmpty() || request.dailyMinutes <= 0 ->
                CalendarScheduleFeasibility.INFEASIBLE_ABSTAIN
            request.timeBudgetEnabled && request.minutes < 30 ->
                CalendarScheduleFeasibility.CONSTRAINED_ALTERNATIVES
            else -> CalendarScheduleFeasibility.FEASIBLE
        }

        val currentMeters = request.weeklyKm * 1_000.0
        val isShortHorizon = (request.goal == "Marathon" && request.weeks < 12) ||
            (request.goal == "Half marathon" && request.weeks < 8)
        val workloadFeas = when {
            calFeas == CalendarScheduleFeasibility.INFEASIBLE_ABSTAIN ->
                WorkloadProgressionFeasibility.UNSAFE_COMPRESSION_REJECTED
            isShortHorizon -> {
                notes += "Short horizon prevents full recommended build; plan protects progressive safety caps."
                WorkloadProgressionFeasibility.CONSERVATIVE_PARTIAL_BUILD
            }
            else -> WorkloadProgressionFeasibility.RECOMMENDED_REACHABLE
        }

        val completionPrep = when {
            paceIsUnknown && request.goal in setOf("Improve 5K", "Improve 10K", "Half marathon", "Marathon") -> {
                notes += "Pace is unknown; run-walk duration targets do not assume fixed kilometer race times."
                EventCompletionPreparation.UNVERIFIED_PACE_UNKNOWN
            }
            request.goal == "Marathon" && (currentMeters < 30_000.0 || projectedPeakWeeklyMeters < 48_000.0) ->
                EventCompletionPreparation.COMPLETION_POSSIBLE_WITH_STRATEGY
            else -> EventCompletionPreparation.WELL_PREPARED
        }

        val perfPlausibility = when {
            targetRaceGoalTimeSeconds == null || currentDemonstratedGoalBandSeconds == null ->
                CurrentPerformancePlausibility.UNASSESSABLE_NO_BENCHMARK
            targetRaceGoalTimeSeconds in currentDemonstratedGoalBandSeconds.first..currentDemonstratedGoalBandSeconds.second ->
                CurrentPerformancePlausibility.WITHIN_CURRENT_BAND
            targetRaceGoalTimeSeconds >= currentDemonstratedGoalBandSeconds.first * 0.94 ->
                CurrentPerformancePlausibility.MODEST_STRETCH
            else -> CurrentPerformancePlausibility.LARGE_GAP_UNVERIFIED
        }

        val uncertainty = if (request.experience == 0 || paceIsUnknown || request.weeks >= 12) {
            FutureImprovementUncertainty.HIGH
        } else {
            FutureImprovementUncertainty.MODERATE
        }

        return FiveDimGoalFeasibility(
            calendarFeasibility = calFeas,
            workloadProgressionFeasibility = workloadFeas,
            eventCompletionPreparation = completionPrep,
            currentPerformancePlausibility = perfPlausibility,
            futureImprovementUncertainty = uncertainty,
            notes = notes
        )
    }

    /** POL-062 multi-race conflict evaluation & observed-effort C-race recovery. */
    fun evaluateMultiRaceConflict(
        primaryGoal: String?,
        primaryDate: LocalDate,
        primaryPriority: Char,
        secondaryGoal: String?,
        secondaryDate: LocalDate,
        secondaryPriority: Char,
        secondaryMaximalEffortObserved: Boolean = false
    ): MultiRaceConflictDecision {
        val gapWeeks = abs(ChronoUnit.DAYS.between(primaryDate, secondaryDate)) / 7.0
        val minWeeksForDistance = when (primaryGoal) {
            "Marathon" -> 12.0
            "Half marathon" -> 6.0
            else -> 4.0
        }
        return when {
            primaryGoal == "Marathon" && secondaryGoal == "Marathon" && gapWeeks < 10.0 ->
                MultiRaceConflictDecision(
                    conflictKind = MultiRaceConflictKind.MARATHONS_TOO_CLOSE,
                    blockedDualPeak = true,
                    recoveryDaysAssigned = 14..21,
                    guidance = "Dual peak marathons under 10 weeks apart are blocked; use a reverse taper bridge and frame the second event as recovery limited."
                )
            primaryPriority == 'A' && secondaryPriority == 'A' && gapWeeks < minWeeksForDistance ->
                MultiRaceConflictDecision(
                    conflictKind = MultiRaceConflictKind.DUAL_A_RACE_TOO_CLOSE,
                    blockedDualPeak = true,
                    recoveryDaysAssigned = Engine15RunningPolicyRegistry.postRaceRecoveryDays(primaryGoal, 'A'),
                    guidance = "Two A-races inside ${minWeeksForDistance.toInt()} weeks cannot share full tapers; assign one primary A-race and downgrade the other to B or C."
                )
            secondaryPriority == 'C' && secondaryMaximalEffortObserved ->
                MultiRaceConflictDecision(
                    conflictKind = MultiRaceConflictKind.C_RACE_MAXIMAL_OBSERVED,
                    blockedDualPeak = false,
                    recoveryDaysAssigned = Engine15RunningPolicyRegistry.postRaceRecoveryDays(secondaryGoal, 'C', maximalObserved = true),
                    guidance = "Observed effort in the tune-up race was maximal; apply full post-race recovery before resuming quality sessions."
                )
            else ->
                MultiRaceConflictDecision(
                    conflictKind = MultiRaceConflictKind.NONE,
                    blockedDualPeak = false,
                    recoveryDaysAssigned = Engine15RunningPolicyRegistry.postRaceRecoveryDays(primaryGoal, primaryPriority),
                    guidance = "Race spacing is compatible with planned taper and recovery windows."
                )
        }
    }
}
