package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * M6D is a READ-ONLY audit of an independent M4B -> M6C calendar.
 *
 * The M4B loading/recovery pattern remains the only progression policy.
 * Tags here explain existing weeks; they are not evidence that a runner can
 * tolerate the proposed load, nor a second periodization algorithm.
 */
internal enum class Engine15M6DPhase { BUILD_1, BUILD_2, BUILD_3, RECOVERY }

internal data class Engine15M6DWeekAudit(
    val weekNumber: Int,
    val phase: Engine15M6DPhase,
    val proposedKm: Double,
    val longRunKm: Double?,
    val runningSessionCount: Int,
    val qualityOpportunityCount: Int,
    val externalSessionCount: Int,
    val requiresCompletedWeekReassessment: Boolean,
    val ceilingConstraintCodes: List<String>
) {
    init {
        require(weekNumber > 0)
        require(proposedKm.isFinite() && proposedKm > 0.0)
        require(longRunKm == null || longRunKm.isFinite() && longRunKm > 0.0)
        require(runningSessionCount >= 1)
        require(qualityOpportunityCount in 0..1 && externalSessionCount >= 0)
        require(phase != Engine15M6DPhase.RECOVERY || qualityOpportunityCount == 0)
        require(ceilingConstraintCodes.distinct() == ceilingConstraintCodes)
    }
}

/** All candidate approval flags stay FALSE, even when all arithmetic matches. */
internal sealed interface Engine15M6DReviewDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M6DReviewDecision

    data class Candidate(
        val policyId: String,
        val originalConflictReview: Engine15M6CReviewDecision.Candidate,
        val weeks: List<Engine15M6DWeekAudit>,
        val proposedTotalKm: Double,
        val proposedPeakKm: Double,
        val requestedTargetKm: Double?,
        val targetBudgetShortfallKm: Double?,
        val reviewAfterCompletedWeek: Int?,
        val ruleIds: List<String>,
        val allFutureWeeksProvisional: Boolean = true,
        val verifiedAthleteResponse: Boolean = false,
        val qualityIntensityAuthenticated: Boolean = false,
        val actualDistanceVerified: Boolean = false,
        val timeBudgetValidated: Boolean = false,
        val productionReady: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M6DReviewDecision {
        init {
            require(weeks.isNotEmpty() &&
                weeks.map { it.weekNumber } == (1..weeks.size).toList())
            require(proposedTotalKm.isFinite() && proposedTotalKm > 0.0)
            require(proposedPeakKm.isFinite() && proposedPeakKm > 0.0)
            require(targetBudgetShortfallKm == null ||
                targetBudgetShortfallKm.isFinite() && targetBudgetShortfallKm >= 0.0)
            require(reviewAfterCompletedWeek == null ||
                reviewAfterCompletedWeek in 1..weeks.size)
            require(ruleIds.distinct() == ruleIds)
            require(allFutureWeeksProvisional && !verifiedAthleteResponse &&
                !qualityIntensityAuthenticated && !actualDistanceVerified &&
                !timeBudgetValidated && !productionReady && !executable &&
                !authorizedForProduction)
        }
    }
}

/**
 * Recomputes M6C, which independently rechecks the original M4B/M5A/M6A
 * weekly totals, dates, quality-spacing and caller-asserted external conflicts.
 * No PlanSnapshot, legacy generator, coaching automation or persistence.
 */
internal object Engine15M6IndependentPeriodizationReview {
    const val POLICY_ID = "engine15.m6d.m4b-periodization-audit-shadow-20261117.1"
    private const val EPS = 1e-6

    private fun phase(week: Int): Engine15M6DPhase = when ((week - 1) % 4) {
        0 -> Engine15M6DPhase.BUILD_1
        1 -> Engine15M6DPhase.BUILD_2
        2 -> Engine15M6DPhase.BUILD_3
        else -> Engine15M6DPhase.RECOVERY
    }

    private fun checkCap(actual: Double, ceiling: Double?): Boolean =
        ceiling == null || actual <= ceiling + EPS

    fun audit(
        source: Engine15IndependentVolumePlanner.Result,
        input: Engine15M6CReviewInput
    ): Engine15M6DReviewDecision {
        fun defer(code: String, reason: String, week: Int? = null) =
            Engine15M6DReviewDecision.Deferred(code, reason, week)

        val review = when (val decision =
            Engine15M6IndependentInterferenceReview.review(source, input)
        ) {
            is Engine15M6CReviewDecision.Deferred ->
                return defer("UPSTREAM_" + decision.reasonCode, decision.explanation)
            is Engine15M6CReviewDecision.Candidate -> decision
        }
        if (source.weeks.size != review.runningWeeks.size)
            return defer("WEEK_COUNT_MISMATCH",
                "M4B and M6C must contain the same number of independent weeks.")

        val hasRaceTaper = (source.input.raceGoal == "Marathon" && source.weeks.size >= 12) ||
            (source.input.raceGoal in setOf("Half marathon", "Improve 10K") && source.weeks.size >= 10) ||
            (source.input.raceGoal == "Improve 5K" && source.weeks.size >= 6)
        val summary = mutableListOf<Engine15M6DWeekAudit>()
        var previousBudgetKm: Double? = null
        var previousLongKm: Double? = null
        for ((index, item) in review.runningWeeks.withIndex()) {
            val m4b = source.weeks[index]
            val week = index + 1
            val isRaceTaperFinal = hasRaceTaper && index == source.weeks.lastIndex
            val isRaceTaperWeek = hasRaceTaper && "RACE_TAPER_VOLUME_REDUCTION" in m4b.constraintCodes
            val isSuppressedPreTaper = Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(
                source.input.raceGoal, source.weeks.size, index
            )
            val plannedPhase = when {
                isRaceTaperFinal -> Engine15M6DPhase.RECOVERY
                isSuppressedPreTaper -> Engine15M6DPhase.BUILD_3
                else -> phase(week)
            }
            val expectedKind = if (plannedPhase == Engine15M6DPhase.RECOVERY)
                Engine15IndependentVolumePlanner.WeekKind.RECOVERY
            else Engine15IndependentVolumePlanner.WeekKind.LOADING
            val km = m4b.proposedKm
                ?: return defer("MISSING_M4B_WEEK_BUDGET",
                    "A scheduled distance week cannot have an unknown M4B budget.", week)

            if (item.weekNumber != week || m4b.index != index ||
                item.kind != expectedKind || m4b.kind != expectedKind ||
                abs(item.budgetKm - km) > EPS)
                return defer("PERIODIZATION_WEEK_MISMATCH",
                    "Calendar week numbering, phase, and original M4B budget must match.",
                    week)
            if (km <= 0.0 || abs(km * 10.0 - kotlin.math.round(km * 10.0)) > EPS ||
                item.runs.size != source.input.runningDays ||
                abs(item.runs.sumOf { it.distanceKm } - km) > EPS)
                return defer("INVALID_OR_MISMATCHED_WEEKLY_DISTANCE",
                    "Each week needs exact, positive, 100 m-aligned original mileage.",
                    week)

            if (!source.input.userDirected && (!checkCap(km, m4b.existingPolicyCeilingKm) ||
                !checkCap(km, m4b.provisionalSessionCapacityKm) ||
                !checkCap(km, m4b.returnFromBreakCeilingKm) ||
                !checkCap(km, m4b.recoveryCeilingKm)))
                return defer("M4B_VOLUME_CEILING_VIOLATION",
                    "The planned calendar may not exceed any original M4B workload bound.",
                    week)

            if (plannedPhase == Engine15M6DPhase.RECOVERY) {
                val capExceeded = if (isRaceTaperWeek) {
                    previousBudgetKm != null && km > previousBudgetKm + EPS
                } else {
                    previousBudgetKm == null || km > previousBudgetKm * 0.8 + EPS
                }
                if (capExceeded)
                    return defer("RECOVERY_WEEK_EXCEEDS_PREVIOUS_CAP",
                        "Recovery weeks must retain M4B's 80 percent previous-week ceiling.",
                        week)
            }

            val longRuns = item.runs.filter { it.role == Engine15M5RunRole.LONG }
            if (longRuns.size != if (source.input.runningDays >= 3 || source.input.runningDays == 1) 1 else 0)
                return defer("LONG_RUN_ROLE_COUNT_MISMATCH",
                    "Long-run roles must match the independent M5A run-day contract.",
                    week)
            val longKm = longRuns.singleOrNull()?.distanceKm
            if ((plannedPhase == Engine15M6DPhase.RECOVERY || isRaceTaperWeek) &&
                longKm != null && previousLongKm != null &&
                longKm > previousLongKm + EPS)
                return defer("RECOVERY_LONG_RUN_INCREASE",
                    "A recovery or taper week cannot extend its previous long-run exposure.",
                    week)

            val reviewedQuality = review.qualityOpportunities.count {
                it.weekNumber == week
            }
            if (reviewedQuality > 1 ||
                (plannedPhase == Engine15M6DPhase.RECOVERY && reviewedQuality != 0))
                return defer("QUALITY_PERIODIZATION_CONFLICT",
                    "M6D cannot admit multiple quality opportunities or recovery-week quality.",
                    week)

            val startDate = input.calendar.startDate.plusWeeks(index.toLong())
            val weekEnd = startDate.plusWeeks(1)
            if (item.runs.any { it.date < startDate || it.date >= weekEnd })
                return defer("RUN_OUTSIDE_WEEK",
                    "M6C dates must remain within their original calendar week.",
                    week)

            summary += Engine15M6DWeekAudit(
                weekNumber = week,
                phase = plannedPhase,
                proposedKm = km,
                longRunKm = longKm,
                runningSessionCount = item.runs.size,
                qualityOpportunityCount = reviewedQuality,
                externalSessionCount = review.externalSessions.count {
                    it.date >= startDate && it.date < weekEnd
                },
                requiresCompletedWeekReassessment =
                    source.reviewAfterCompletedWeek == week,
                ceilingConstraintCodes = m4b.constraintCodes
            )
            previousBudgetKm = km
            previousLongKm = longKm
        }

        val peak = summary.maxOf { it.proposedKm }
        val target = source.input.targetWeeklyKm
        val gap = target?.let { max(0.0, it - peak) }
        if (abs(peak - (source.peakProposedKm ?: Double.NaN)) > EPS ||
            (gap == null) != (source.shortfallToRequestedTargetKm == null) ||
            (gap != null && abs(gap -
                (source.shortfallToRequestedTargetKm ?: Double.NaN)) > EPS))
            return defer("M4B_TARGET_METRICS_MISMATCH",
                "Reported peak and shortfall must reflect original M4B budgets.")

        return Engine15M6DReviewDecision.Candidate(
            policyId = POLICY_ID,
            originalConflictReview = review,
            weeks = summary,
            proposedTotalKm = summary.sumOf { it.proposedKm },
            proposedPeakKm = peak,
            requestedTargetKm = target,
            targetBudgetShortfallKm = gap,
            reviewAfterCompletedWeek = source.reviewAfterCompletedWeek,
            ruleIds = if(source.input.userDirected) listOf(
                "USER_DIRECTED_BUDGET_RECONCILED_NOT_CAPACITY_VALIDATED",
                "M6C_CALENDAR_AND_CONFLICT_RECOMPUTED",
                "RECOVERY_RETAINED_AT_80_PERCENT_OF_PREVIOUS_WEEK",
                "M5A_SESSION_DISTANCE_AND_LONG_CONTINUITY_RETAINED",
                "QUALITY_OPPORTUNITIES_ARE_NOT_EXECUTABLE_WORKOUTS",
                "BASELINE_REASSESSMENT_CHECKPOINT_NOT_AUTOMATIC_ADVANCEMENT",
                "TARGET_BUDGET_NOT_ACTUAL_MILEAGE",
                "ALL_FUTURE_WEEKS_PROVISIONAL_NO_PRODUCTION_AUTHORITY"
            ) else listOf(
                "ORIGINAL_M4B_PHASES_REPORTED_NOT_REPLANNED",
                "M6C_CALENDAR_AND_CONFLICT_RECOMPUTED",
                "M4B_CEILINGS_AND_RECOVERY_PREVIOUS_WEEK_RECHECKED",
                "M5A_SESSION_DISTANCES_AND_LONG_CONTINUITY_RETAINED",
                "QUALITY_OPPORTUNITIES_ARE_NOT_EXECUTABLE_WORKOUTS",
                "BASELINE_REASSESSMENT_CHECKPOINT_NOT_AUTOMATIC_ADVANCEMENT",
                "TARGET_BUDGET_NOT_ACTUAL_MILEAGE",
                "ALL_FUTURE_WEEKS_PROVISIONAL_NO_PRODUCTION_AUTHORITY"
            )
        )
    }
}
