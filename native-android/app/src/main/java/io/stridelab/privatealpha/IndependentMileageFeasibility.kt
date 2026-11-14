package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M4 Batch 2: research-only feasibility checks for an already generated
 * M4 weekly-volume candidate. This does not change the schedule or authorize
 * any workout. Caller approval of day/long-run limits remains an assertion,
 * NOT an independently verified athlete-safety boundary.
 *
 * Time budgets are intentionally optional and NOT inferred from pace. This
 * check only evaluates explicitly supplied per-day kilometer ceilings and
 * long-run continuity; it cannot prove training safety.
 */
internal data class IndependentMileageFeasibilityLimits(
    val approvedMaxKmByDay: Map<Int, Double>?,
    val approvedPreviousLongRunKm: Double? = null,
    val approvedLongRunCeilingKm: Double? = null,
    val approvedMaxBuildLongRunIncreaseKm: Double? = null
) {
    init {
        require(approvedMaxKmByDay == null ||
            approvedMaxKmByDay.keys.all { it in 1..7 } &&
            approvedMaxKmByDay.values.all { it.isFinite() && it in 0.0..1000.0 })
        require(listOfNotNull(approvedPreviousLongRunKm,
            approvedLongRunCeilingKm, approvedMaxBuildLongRunIncreaseKm)
            .all { it.isFinite() && it in 0.0..1000.0 })
    }
}

internal sealed interface IndependentMileageFeasibilityResult {
    data class Unchecked(val ruleId: String, val explanation: String) :
        IndependentMileageFeasibilityResult

    data class Conflict(
        val week: Int,
        val ruleId: String,
        val explanation: String
    ) : IndependentMileageFeasibilityResult

    data class Checked(
        val checkedWeeks: Int,
        val ruleIds: List<String>,
        val policyEvidence: String = "HEURISTIC_UNVALIDATED",
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentMileageFeasibilityResult {
        init {
            require(checkedWeeks > 0)
            require(ruleIds.distinct() == ruleIds)
            require(policyEvidence == "HEURISTIC_UNVALIDATED")
            require(!sourceEvidenceVerified && !authorizedForProduction)
        }
    }
}

/**
 * Non-mutating constraint review. It does NOT redistribute runs, invent
 * acceptable training paces, impose default minutes, or automatically lower
 * weekly targets. Failures are visible for an independent planning step.
 */
internal object IndependentMileageFeasibility {
    const val POLICY_ID = "stridelab.engine15.m4-feasibility-research-v1"
    private const val EPS = 0.00001

    fun check(
        candidate: IndependentWeeklyMileageDecision.Candidate,
        limits: IndependentMileageFeasibilityLimits,
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentMileageFeasibilityResult {
        fun unchecked(rule: String, why: String) =
            IndependentMileageFeasibilityResult.Unchecked(rule, why)
        fun conflict(week: Int, rule: String, why: String) =
            IndependentMileageFeasibilityResult.Conflict(week, rule, why)

        val days = candidate.weeks.first().dayAllocations.map { it.isoDay }.toSet()
        if (days.size < 2)
            return conflict(1, "INVALID_RUNNING_DAYS", "Weekly candidates must have at least two distinct running days.")
        if (candidate.weeks.any { it.dayAllocations.map { a -> a.isoDay }.toSet() != days })
            return conflict(0, "RUN_DAYS_CHANGE_WITHOUT_REVIEW", "Weekly running days cannot change without independent availability review.")

        val dailyLimits = limits.approvedMaxKmByDay
            ?: return unchecked("DAY_CEILINGS_NOT_SUPPLIED",
                "No externally approved per-running-day kilometer ceilings were supplied.")
        if (dailyLimits.keys != days)
            return unchecked("DAY_CEILINGS_INCOMPLETE",
                "Every scheduled running day needs one explicit ceiling; extra days must also be reviewed.")

        val hasLongRun = days.size >= 3
        if (hasLongRun && limits.approvedPreviousLongRunKm == null)
            return unchecked("PRIOR_LONG_RUN_NOT_SUPPLIED",
                "The previous approved long-run reference is missing.")
        if (hasLongRun && limits.approvedLongRunCeilingKm == null)
            return unchecked("LONG_RUN_CEILING_NOT_SUPPLIED",
                "The long-run absolute ceiling is missing.")
        if (hasLongRun && limits.approvedMaxBuildLongRunIncreaseKm == null)
            return unchecked("LONG_RUN_INCREMENT_NOT_SUPPLIED",
                "The maximum build-week long-run increment is missing.")

        var previousBuildLong = limits.approvedPreviousLongRunKm ?: 0.0
        var previousWeekLong: Double? = null
        for ((index, week) in candidate.weeks.withIndex()) {
            if (week.week != index + 1)
                return conflict(week.week, "NONSEQUENTIAL_WEEKS",
                    "Candidate weeks must be numbered consecutively from one.")
            val sum = week.dayAllocations.sumOf { it.kilometers }
            if (abs(sum - week.weeklyKilometers) > EPS)
                return conflict(week.week, "WEEKLY_TOTAL_MISMATCH",
                    "Recorded daily allocations do not sum to the weekly candidate.")
            if (week.weeklyKilometers + EPS < 0.0)
                return conflict(week.week, "NEGATIVE_WEEKLY_VOLUME", "Weekly volume cannot be negative.")
            val declaredLong = week.dayAllocations.filter { it.longRunCandidate }
            if (declaredLong.size != if (hasLongRun) 1 else 0)
                return conflict(week.week, "INVALID_LONG_RUN_MARKER",
                    "A 3+ day candidate must have one long run; a two-day candidate must have none.")
            if (hasLongRun && declaredLong.first().isoDay !=
                candidate.weeks.first().dayAllocations.singleOrNull { it.longRunCandidate }?.isoDay)
                return conflict(week.week, "LONG_RUN_DAY_CHANGED",
                    "The preferred long-run day cannot move silently between weeks.")
            for (day in week.dayAllocations) {
                val ceiling = requireNotNull(dailyLimits[day.isoDay])
                if (day.kilometers > ceiling + EPS)
                    return conflict(week.week, "DAY_DISTANCE_CEILING_EXCEEDED",
                        "Day ${day.isoDay} exceeds its supplied kilometer ceiling.")
            }
            if (hasLongRun) {
                val long = declaredLong.single().kilometers
                val maxLong = requireNotNull(limits.approvedLongRunCeilingKm)
                if (long > maxLong + EPS)
                    return conflict(week.week, "LONG_RUN_ABSOLUTE_CEILING_EXCEEDED",
                        "Long-run candidate exceeds the independently supplied absolute ceiling.")
                if (long > week.weeklyKilometers * policy.maximumLongRunShare + 0.01001)
                    return conflict(week.week, "LONG_RUN_SHARE_CEILING_EXCEEDED",
                        "Long-run candidate exceeds the configured weekly share.")
                if (week.dayAllocations.any { it.kilometers > long + EPS })
                    return conflict(week.week, "LONG_RUN_NOT_LONGEST",
                        "Another run would be longer than the designated long run.")
                val maxIncrease = requireNotNull(limits.approvedMaxBuildLongRunIncreaseKm)
                // Deload weeks cannot extend a long run beyond the preceding build
                // reference or immediately preceding week's duration.
                val allowed = if (week.recoveryWeek) {
                    minOf(previousBuildLong, previousWeekLong ?: previousBuildLong)
                } else previousBuildLong + maxIncrease
                if (long > allowed + EPS)
                    return conflict(week.week,
                        if (week.recoveryWeek) "RECOVERY_LONG_RUN_INCREASE" else "LONG_RUN_BUILD_INCREMENT_EXCEEDED",
                        "The long-run candidate exceeds the permitted continuity envelope.")
                if (!week.recoveryWeek) previousBuildLong = long
                previousWeekLong = long
            }
        }

        return IndependentMileageFeasibilityResult.Checked(
            checkedWeeks = candidate.weeks.size,
            ruleIds = listOf(
                "EXTERNALLY_ASSERTED_DAY_CEILINGS_CHECKED",
                if (hasLongRun) "EXTERNALLY_ASSERTED_LONG_RUN_LIMITS_CHECKED"
                    else "TWO_DAY_NO_LONG_RUN",
                "NO_TIME_BUDGET_INFERRED",
                "NO_AUTO_REALLOCATION",
                "NOT_PRODUCTION_AUTHORIZED"
            )
        )
    }
}
