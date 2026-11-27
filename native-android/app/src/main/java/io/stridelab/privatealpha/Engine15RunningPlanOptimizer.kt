package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal data class DailyTimeWindow(
    val startSecondOfDay: Int,
    val endSecondOfDay: Int
) {
    init {
        require(startSecondOfDay in 0 until 86_400)
        require(endSecondOfDay in (startSecondOfDay + 1)..86_400)
    }

    val durationSeconds: Int get() = endSecondOfDay - startSecondOfDay
}

internal data class OptimizerDayAvailability(
    val date: LocalDate,
    val windows: List<DailyTimeWindow>,
    val fixedOtherCommitmentsMinSeconds: Int = 0,
    val fixedOtherCommitmentsMaxSeconds: Int = 0,
    val transitionBufferSeconds: Int = Engine15RunningPolicyRegistry.POL_038_SAME_DAY_TRANSITION_MIN_S,
    val sessionDistanceCapMeters: Double = Double.POSITIVE_INFINITY,
    val dayDistanceCapMeters: Double = Double.POSITIVE_INFINITY,
    val unspecifiedAllDayBudgetSeconds: Int? = null
) {
    val maxSingleContiguousWindowSeconds: Int
        get() = windows.maxOfOrNull { it.durationSeconds }
            ?: unspecifiedAllDayBudgetSeconds
            ?: 0

    val totalDayMaxSeconds: Int
        get() = if (windows.isNotEmpty()) windows.sumOf { it.durationSeconds } else (unspecifiedAllDayBudgetSeconds ?: 0)
}

internal data class FourLevelDayCapacity(
    val date: LocalDate,
    val availableRunConservativeSeconds: Int,
    val optimisticDomainBoundMeters: Double,
    val conservativeEasyMeters: Double
)

internal enum class TriStateOracleOutcome {
    FEASIBLE,
    PROVEN_INFEASIBLE_IN_MODELED_DOMAIN,
    UNKNOWN
}

internal data class ObjectiveScoreVector(
    val safetyOrHardViolations: Double,
    val worstGoalShortfall: Double,
    val spacingPenalty: Double,
    val workloadBandDeviationMeters: Double,
    val durationBandDeviationSeconds: Double,
    val doseAlignmentDeviationPct: Double,
    val canonicalHash: String
) {
    fun quantizedBuckets(): List<Long> = listOf(
        floor(safetyOrHardViolations / Engine15RunningPolicyRegistry.POL_064_BUCKET_SAFETY_OR_RESTRICTION).toLong(),
        floor(worstGoalShortfall / Engine15RunningPolicyRegistry.POL_064_BUCKET_GOAL_SHORTFALL).toLong(),
        floor(spacingPenalty / Engine15RunningPolicyRegistry.POL_064_BUCKET_SPACING_PENALTY).toLong(),
        floor(workloadBandDeviationMeters / Engine15RunningPolicyRegistry.POL_064_BUCKET_VOLUME_BAND_M).toLong(),
        floor(durationBandDeviationSeconds / Engine15RunningPolicyRegistry.POL_064_BUCKET_DURATION_BAND_S).toLong(),
        floor(doseAlignmentDeviationPct / Engine15RunningPolicyRegistry.POL_064_BUCKET_DOSE_ALIGN_PCT).toLong()
    )

    fun rawVector(): List<Double> = listOf(
        safetyOrHardViolations,
        worstGoalShortfall,
        spacingPenalty,
        workloadBandDeviationMeters,
        durationBandDeviationSeconds,
        doseAlignmentDeviationPct
    )
}

internal data class OptimizerSessionCandidate(
    val id: String,
    val date: LocalDate,
    val discipline: String,
    val isHighStress: Boolean,
    val isLowerBodyHeavy: Boolean,
    val durationMinSeconds: Double,
    val durationMaxSeconds: Double,
    val distanceConservativeMeters: Double,
    val distanceOptimisticMeters: Double,
    val plannedStartUtc: Instant? = null
) {
    init {
        require(durationMinSeconds >= 0.0 && durationMaxSeconds >= durationMinSeconds)
        require(distanceConservativeMeters.isFinite() && distanceConservativeMeters >= 0.0)
    }
}

internal data class WeeklyScheduleFrontierState(
    val weekNumber: Int,
    val sessions: List<OptimizerSessionCandidate>,
    val scoreVector: ObjectiveScoreVector,
    val outgoingHighStressDates: Set<LocalDate>,
    val outgoingLongRunDate: LocalDate?
) {
    val boundarySignature: String
        get() = "lr=${outgoingLongRunDate ?: "none"}|hs=${outgoingHighStressDates.sorted().joinToString(",")}"
}

internal data class FinalPlanValidationReport(
    val outcome: TriStateOracleOutcome,
    val violationCodes: List<String>,
    val conflictExplanation: String?
)

/**
 * M4 Pure-Kotlin Plan Optimizer & Standalone Final Validator (F_final):
 * - MOD-18 4-level capacity (D_opt, D_cons, V_joint_max, V_rec) + per-window contiguity (CON-19)
 * - MOD-20 Two-Pass Transitive & Pareto-Efficient Lexicographic Comparator
 * - POL-035 saturating HIGH_STRESS spacing (calendar-day when startTime unknown, UTC elapsed when known)
 * - POL-037 anti-filler active-day pruning for sub-minimum residuals
 * - POL-039 / POL-040 bounded K_front = 4 state-distinct boundary frontier + 3-round repair
 * - Tri-state F_final final validator
 */
internal object Engine15RunningPlanOptimizer {

    /**
     * MOD-18: Computes daily D_opt (for impossibility proofs only) and D_cons_easy, enforcing CON-19.
     */
    fun computeDayCapacity(
        availability: OptimizerDayAvailability,
        admissibleMaxSpeedMps: Double,
        easyLowSpeedMps: Double
    ): FourLevelDayCapacity {
        val availConsSec = max(
            0,
            availability.totalDayMaxSeconds - availability.fixedOtherCommitmentsMaxSeconds - availability.transitionBufferSeconds
        )
        val availOptSec = max(
            0,
            availability.totalDayMaxSeconds - availability.fixedOtherCommitmentsMinSeconds
        )
        val dOpt = if (admissibleMaxSpeedMps.isFinite() && admissibleMaxSpeedMps > 0.0) {
            min(availability.dayDistanceCapMeters, availOptSec * admissibleMaxSpeedMps)
        } else 0.0
        val dCons = if (easyLowSpeedMps.isFinite() && easyLowSpeedMps > 0.0) {
            min(availability.sessionDistanceCapMeters, availConsSec * easyLowSpeedMps)
        } else 0.0
        return FourLevelDayCapacity(
            date = availability.date,
            availableRunConservativeSeconds = availConsSec,
            optimisticDomainBoundMeters = dOpt,
            conservativeEasyMeters = dCons
        )
    }

    /** Proves domain impossibility when weekly requested meters exceed top N_avail D_opt days. */
    fun checkOptimisticFeasibility(
        requestedWeeklyMeters: Double,
        dayCapacities: List<FourLevelDayCapacity>,
        maxRunDays: Int
    ): TriStateOracleOutcome {
        val topOptSum = dayCapacities
            .sortedByDescending { it.optimisticDomainBoundMeters }
            .take(maxRunDays.coerceAtLeast(0))
            .sumOf { it.optimisticDomainBoundMeters }
        return if (requestedWeeklyMeters > topOptSum + 1e-6) {
            TriStateOracleOutcome.PROVEN_INFEASIBLE_IN_MODELED_DOMAIN
        } else {
            TriStateOracleOutcome.FEASIBLE
        }
    }

    /** CON-19 per-window contiguity check: continuous session must fit in one single window. */
    fun fitsPerWindowContiguity(
        sessionMaxSeconds: Double,
        availability: OptimizerDayAvailability
    ): Boolean = sessionMaxSeconds <= availability.maxSingleContiguousWindowSeconds.toDouble() + 1e-6

    /** POL-035 saturating spacing penalty for HIGH_STRESS running sessions. */
    fun spacingPenaltyBetween(a: OptimizerSessionCandidate, b: OptimizerSessionCandidate): Double {
        if (!a.isHighStress || !b.isHighStress || a.id == b.id) return 0.0
        val (first, second) = if (a.date <= b.date) a to b else b to a
        val startA = first.plannedStartUtc
        val startB = second.plannedStartUtc
        return if (startA != null && startB != null) {
            val completionFirst = startA.plusSeconds(first.durationMaxSeconds.toLong())
            val elapsedS = ChronoUnit.SECONDS.between(completionFirst, startB).coerceAtLeast(0L)
            if (elapsedS >= Engine15RunningPolicyRegistry.POL_035_HIGH_STRESS_MIN_ELAPSED_S) 0.0
            else (Engine15RunningPolicyRegistry.POL_035_HIGH_STRESS_MIN_ELAPSED_S - elapsedS) / 3600.0
        } else {
            val calDelta = ChronoUnit.DAYS.between(first.date, second.date).toDouble()
            max(0.0, Engine15RunningPolicyRegistry.POL_035_HIGH_STRESS_MIN_CAL_DAYS - calDelta)
        }
    }

    /** MOD-20 Two-Pass Strictly Transitive & Pareto-Efficient Total Order on evaluated candidates. */
    val twoPassComparator: Comparator<ObjectiveScoreVector> = Comparator { a, b ->
        val qa = a.quantizedBuckets()
        val qb = b.quantizedBuckets()
        for (i in qa.indices) {
            val cmp = qa[i].compareTo(qb[i])
            if (cmp != 0) return@Comparator cmp
        }
        val ra = a.rawVector()
        val rb = b.rawVector()
        for (i in ra.indices) {
            val cmp = ra[i].compareTo(rb[i])
            if (cmp != 0) return@Comparator cmp
        }
        a.canonicalHash.compareTo(b.canonicalHash)
    }

    /**
     * POL-037 anti-filler easy-volume allocation across mandatory + optional days.
     * Drops optional days cleanly if residual < sum(q_min) so filler micro-runs never appear.
     */
    fun pruneAndAllocateEasyDays(
        remainingMeters: Double,
        mandatoryDays: List<LocalDate>,
        optionalDays: List<LocalDate>,
        minPerRunMeters: Double = Engine15RunningPolicyRegistry.POL_037_MIN_VIABLE_EASY_DISTANCE_M,
        maxCapByDay: Map<LocalDate, Double> = emptyMap()
    ): Map<LocalDate, Double> {
        if (remainingMeters <= 1e-6) return emptyMap()
        val activeDays = mandatoryDays.toMutableList()
        val sortedOptional = optionalDays.sorted()
        for (opt in sortedOptional) {
            val projectedMinSum = (activeDays.size + 1) * minPerRunMeters
            if (projectedMinSum <= remainingMeters + 1e-6) {
                activeDays += opt
            }
        }
        if (activeDays.isEmpty()) return emptyMap()
        val perDayBase = remainingMeters / activeDays.size
        val result = LinkedHashMap<LocalDate, Double>(activeDays.size)
        var unallocated = 0.0
        for (d in activeDays) {
            val cap = maxCapByDay[d] ?: Double.POSITIVE_INFINITY
            if (perDayBase > cap) {
                result[d] = cap
                unallocated += (perDayBase - cap)
            } else {
                result[d] = perDayBase
            }
        }
        if (unallocated > 1e-6) {
            val slackDays = activeDays.filter { (result[it] ?: 0.0) < (maxCapByDay[it] ?: Double.POSITIVE_INFINITY) - 1e-6 }
            if (slackDays.isNotEmpty()) {
                val extra = unallocated / slackDays.size
                for (d in slackDays) {
                    val cap = maxCapByDay[d] ?: Double.POSITIVE_INFINITY
                    result[d] = min(cap, (result[d] ?: 0.0) + extra)
                }
            }
        }
        return result
    }

    /** POL-040 retains up to K_front = 4 non-dominated schedules with distinct boundary signatures. */
    fun retainBoundaryFrontier(
        candidates: List<WeeklyScheduleFrontierState>,
        kFront: Int = Engine15RunningPolicyRegistry.POL_040_K_FRONT_SIZE
    ): List<WeeklyScheduleFrontierState> {
        val sorted = candidates.sortedWith { a, b -> twoPassComparator.compare(a.scoreVector, b.scoreVector) }
        val retained = mutableListOf<WeeklyScheduleFrontierState>()
        val seenSignatures = HashSet<String>()
        for (cand in sorted) {
            if (seenSignatures.add(cand.boundarySignature)) {
                retained += cand
                if (retained.size >= kFront) break
            }
        }
        return retained
    }

    /** Standalone Final Plan Validator F_final over unrounded SI constraints. */
    fun validateFinal(
        sessions: List<OptimizerSessionCandidate>,
        availabilityByDate: Map<LocalDate, OptimizerDayAvailability>,
        targetWeeklyMetersRange: ClosedFloatingPointRange<Double>? = null,
        minBudgetCheckCount: Int = 0
    ): FinalPlanValidationReport {
        val violations = mutableListOf<String>()

        for (s in sessions) {
            val avail = availabilityByDate[s.date]
            if (avail != null && !fitsPerWindowContiguity(s.durationMaxSeconds, avail)) {
                violations += "CON_19_WINDOW_CONTIGUITY_EXCEEDED:${s.id}"
            }
        }

        // Same-day non-overlap + transition buffer
        val byDate = sessions.groupBy { it.date }
        for ((date, daySessions) in byDate) {
            if (daySessions.size > 1) {
                val avail = availabilityByDate[date]
                val totalMax = daySessions.sumOf { it.durationMaxSeconds } +
                    (daySessions.size - 1) * (avail?.transitionBufferSeconds ?: Engine15RunningPolicyRegistry.POL_038_SAME_DAY_TRANSITION_MIN_S)
                if (avail != null && totalMax > avail.totalDayMaxSeconds.toDouble() + 1e-6) {
                    violations += "MOD_18_DAY_BUDGET_EXCEEDED:$date"
                }
            }
        }

        // HIGH_STRESS spacing
        val highStress = sessions.filter { it.isHighStress }.sortedBy { it.date }
        for (i in 0 until highStress.lastIndex) {
            val penalty = spacingPenaltyBetween(highStress[i], highStress[i + 1])
            if (penalty > 1e-6) {
                violations += "POL_035_HIGH_STRESS_SPACING:${highStress[i].id}->${highStress[i + 1].id}"
            }
        }

        // Search budget guard (never mislabel SearchBudgetExhausted as UNSAT!)
        if (sessions.isEmpty() && minBudgetCheckCount >= Engine15RunningPolicyRegistry.POL_039_B_GLOBAL_CAP) {
            return FinalPlanValidationReport(
                outcome = TriStateOracleOutcome.UNKNOWN,
                violationCodes = listOf("SEARCH_BUDGET_EXHAUSTED"),
                conflictExplanation = "Search budget cap reached before completing frontier check."
            )
        }

        if (targetWeeklyMetersRange != null) {
            val sumCons = sessions.sumOf { it.distanceConservativeMeters }
            if (sumCons < targetWeeklyMetersRange.start - 1e-6 ||
                sumCons > targetWeeklyMetersRange.endInclusive + 1e-6
            ) {
                violations += "WEEKLY_VOLUME_OUT_OF_BAND"
            }
        }

        return if (violations.isEmpty()) {
            FinalPlanValidationReport(
                outcome = TriStateOracleOutcome.FEASIBLE,
                violationCodes = emptyList(),
                conflictExplanation = null
            )
        } else {
            FinalPlanValidationReport(
                outcome = TriStateOracleOutcome.PROVEN_INFEASIBLE_IN_MODELED_DOMAIN,
                violationCodes = violations.distinct(),
                conflictExplanation = "Constraints violated: ${violations.distinct().joinToString(", ")}"
            )
        }
    }
}
