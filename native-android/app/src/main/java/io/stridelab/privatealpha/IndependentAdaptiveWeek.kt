package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

/** Explicit research-fixture feedback; neither source authentication nor inferred readiness. */
internal enum class IndependentWeekResponse { UNKNOWN, TOLERATED, DIFFICULT }

/**
 * Synthetic-only next-week experiment. No Android caller, persistence or quality authorization.
 * Separates an evidence decision from constrained allocation, retaining M4's external gates.
 * All inputs remain caller assertions. A feasible allocation is NOT athlete-safe validation.
 */
internal object IndependentAdaptiveWeek {
    const val POLICY_ID = "stridelab.engine15.next-week-experiment-v1"
    private fun defer(id: String, why: String) = IndependentWeeklyMileageDecision.Deferred(id, why)
    private fun units(km: Double) = floor(km * 100.0 + 1e-8).toInt()

    fun respond(
        input: IndependentWeeklyMileageInput,
        limits: IndependentMileageFeasibilityLimits,
        history: IndependentRunningHistoryEvidence,
        planningDate: LocalDate,
        response: IndependentWeekResponse
    ): IndependentWeeklyMileageDecision {
        val original = IndependentWeeklyMileagePlanner.generate(input)
        if (original is IndependentWeeklyMileageDecision.Deferred) return original
        val monday = planningDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        if (planningDate != monday)
            return defer("COMPLETE_WEEK_REQUIRED", "This experiment plans a whole Monday-start week only.")
        if (history.completedWeeks.last().monday != monday.minusWeeks(1))
            return defer("STALE_HISTORY", "Refresh evidence through the last completed week.")
        if (response == IndependentWeekResponse.DIFFICULT)
            return defer("RECOVERY_REVIEW", "Difficult training needs a reviewed recovery decision, not an invented reduction.")
        val recent = history.completedWeeks.takeLast(2).map { it.confirmedWeeklyKm }
        if (recent.any { it == null })
            return defer("RECENT_COVERAGE_UNKNOWN", "Missing coverage cannot establish completed mileage or missed sessions.")
        val repeatable = recent.filterNotNull().minOrNull()!!
        if (repeatable == 0.0)
            return defer("RETURN_TO_RUN_REVIEW", "A complete zero week requires a separately reviewed starting load.")
        if (response == IndependentWeekResponse.UNKNOWN)
            return defer("RESPONSE_UNKNOWN", "Completed distance alone does not establish workout tolerance.")
        val approved = input.approvedStartingWeeklyKm
            ?: return defer("STARTING_VOLUME_NOT_APPROVED", "History cannot approve its own starting load.")
        // shortcut: two-week minimum can underload after a planned recovery week; review before adding progression.
        val result = allocate(input.copy(approvedStartingWeeklyKm=minOf(approved, repeatable)), limits)
        return if (result is IndependentWeeklyMileageDecision.Candidate) result.copy(
            ruleIds=(result.ruleIds + listOf("ASSERTED_TOLERANCE_NOT_READINESS", "RECENT_TWO_WEEK_REPEAT_ENVELOPE",
                "NO_FUTURE_COMPLETION_ASSUMED", "NO_MISSED_DISTANCE_CATCHUP")).distinct()
        ) else result
    }

    /** Allocator-only comparator: preserve the requested total, search within supplied day limits. */
    fun allocate(
        input: IndependentWeeklyMileageInput,
        limits: IndependentMileageFeasibilityLimits,
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentWeeklyMileageDecision {
        if (input.weeks != 1)
            return defer("NEXT_WEEK_ONLY", "Reassess after completion; future response is unknown.")
        val baseline = when (val generated = IndependentWeeklyMileagePlanner.generate(input, policy)) {
            is IndependentWeeklyMileageDecision.Deferred -> return generated
            is IndependentWeeklyMileageDecision.Candidate -> generated
        }
        val days = input.runningDays.sorted()
        val caps = limits.approvedMaxKmByDay
            ?: return defer("DAY_CEILINGS_NOT_SUPPLIED", "Every scheduled day needs an explicit distance ceiling.")
        if (caps.keys != input.runningDays)
            return defer("DAY_CEILINGS_INCOMPLETE", "Day limits must match available running days exactly.")
        val total = units(baseline.weeks.single().weeklyKilometers)
        if (total < days.size)
            return defer("POSITIVE_SESSION_DISTANCE_REQUIRED", "Do not generate zero-distance scheduled runs.")
        val longDay = input.preferredLongRunDay
        val others = days.filter { it != longDay }
        val hasLong = days.size >= 3
        if (hasLong && listOf(limits.approvedPreviousLongRunKm, limits.approvedLongRunCeilingKm,
                limits.approvedMaxBuildLongRunIncreaseKm).any { it == null })
            return defer("LONG_RUN_LIMITS_INCOMPLETE", "Prior long run, absolute ceiling and increment must be supplied.")
        val minimum = if (hasLong) (total + days.size - 1) / days.size else 1
        val maximum = if (hasLong) minOf(units(caps.getValue(longDay)),
            units(limits.approvedLongRunCeilingKm!!),
            units(limits.approvedPreviousLongRunKm!! + limits.approvedMaxBuildLongRunIncreaseKm!!),
            floor(total * policy.maximumLongRunShare + 1e-8).toInt())
        else minOf(total - others.size, units(caps.getValue(longDay)))
        val preferred = units(baseline.weeks.single().dayAllocations.single { it.isoDay == longDay }.kilometers)
        // Closest feasible long-run dose first; smaller dose breaks ties deterministically.
        val longUnits = (minimum..maximum).sortedWith(compareBy<Int> { kotlin.math.abs(it-preferred) }.thenBy { it })
            .firstOrNull { long ->
                val remaining = total-long
                remaining >= others.size && others.all { units(caps.getValue(it)) >= 1 } &&
                    others.sumOf { minOf(units(caps.getValue(it)), if (hasLong) long else total) } >= remaining
            } ?: return defer("NO_FEASIBLE_DISTANCE_ALLOCATION", "Requested total cannot fit day and long-run limits; no silent target reduction.")
        val allocated = days.associateWith { if (it == longDay) longUnits else 1 }.toMutableMap()
        var remaining = total - allocated.values.sum()
        while (remaining > 0) {
            val day = others.filter { allocated.getValue(it) < minOf(units(caps.getValue(it)), if (hasLong) longUnits else total) }
                .minWithOrNull(compareBy<Int> { allocated.getValue(it) }.thenBy { it })!!
            allocated[day] = allocated.getValue(day) + 1
            remaining--
        }
        val week = baseline.weeks.single().copy(dayAllocations=days.map {
            IndependentDayMileageAllocation(it, allocated.getValue(it)/100.0, hasLong && it==longDay)
        }, ruleIds=listOf("EXTERNALLY_APPROVED_START_ASSERTED", "CONSTRAINT_SEARCH", "NO_QUALITY_AUTHORIZATION"))
        val candidate = baseline.copy(weeks=listOf(week), ruleIds=(baseline.ruleIds + "CONSTRAINT_SEARCH").distinct())
        return when (val checked = IndependentMileageFeasibility.check(candidate, limits, policy)) {
            is IndependentMileageFeasibilityResult.Checked -> candidate
            is IndependentMileageFeasibilityResult.Conflict -> defer(checked.ruleId, checked.explanation)
            is IndependentMileageFeasibilityResult.Unchecked -> defer(checked.ruleId, checked.explanation)
        }
    }
}
