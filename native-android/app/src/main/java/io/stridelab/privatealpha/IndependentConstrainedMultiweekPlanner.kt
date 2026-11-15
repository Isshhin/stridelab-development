package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M6a synthetic-only, forward-feasible multiweek mileage planner.
 *
 * M4 chooses conservative *weekly totals* with periodic recovery. Astra's
 * single-week constrained allocator then chooses *daily distances* under
 * reviewed per-day and long-run limits, while preserving every M4 total.
 * This fills the "distribute first, reject later" gap across the entire
 * week sequence. It does NOT observe future workouts or infer athlete safety.
 *
 * No Android caller, plan storage, source credential or production authority.
 */
internal sealed interface IndependentConstrainedMultiweekDecision {
    data class Deferred(
        val week: Int?,
        val ruleId: String,
        val explanation: String
    ) : IndependentConstrainedMultiweekDecision

    data class Candidate(
        val mileage: IndependentWeeklyMileageDecision.Candidate,
        val baseWorkouts: IndependentRunWeekCompositionDecision.Candidate,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentConstrainedMultiweekDecision {
        init {
            require(mileage.weeks.isNotEmpty())
            require(mileage.weeks.size == baseWorkouts.weeks.size)
            require(mileage.weeks.map { it.week } == baseWorkouts.weeks.map { it.week })
            require(ruleIds.distinct() == ruleIds)
            require(!sourceEvidenceVerified && !authorizedForProduction)
            for ((week, typed) in mileage.weeks.zip(baseWorkouts.weeks)) {
                require(abs(week.weeklyKilometers - typed.weeklyKilometers) < 0.00001)
                require(week.dayAllocations.size == typed.sessions.size)
                require(week.dayAllocations.zip(typed.sessions).all { (d, s) ->
                    d.isoDay == s.isoDay && abs(d.kilometers - s.allocatedKilometers) < 0.00001
                })
            }
        }
    }
}

internal object IndependentConstrainedMultiweekPlanner {
    const val POLICY_ID = "stridelab.engine15.m6a-constrained-multiweek-research-v1"

    fun generate(
        input: IndependentWeeklyMileageInput,
        limits: IndependentMileageFeasibilityLimits,
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentConstrainedMultiweekDecision {
        fun defer(week: Int?, id: String, detail: String) =
            IndependentConstrainedMultiweekDecision.Deferred(week, id, detail)

        // Validate the original caller approvals before making any day-level
        // adjustments. Do not make incompatible approvals "pass" by lowering
        // or changing the original starting volume.
        val baseline = when (val outcome = IndependentWeeklyMileagePlanner.generate(input, policy)) {
            is IndependentWeeklyMileageDecision.Deferred ->
                return defer(null, outcome.ruleId, outcome.explanation)
            is IndependentWeeklyMileageDecision.Candidate -> outcome
        }

        val days = input.runningDays
        if (limits.approvedMaxKmByDay == null)
            return defer(null, "DAY_CEILINGS_NOT_SUPPLIED",
                "No explicitly reviewed per-day kilometer limits were supplied.")
        if (limits.approvedMaxKmByDay.keys != days)
            return defer(null, "DAY_CEILINGS_INCOMPLETE",
                "Per-day kilometer limits must match every available run day.")
        if (days.size >= 3 && listOf(
                limits.approvedPreviousLongRunKm,
                limits.approvedLongRunCeilingKm,
                limits.approvedMaxBuildLongRunIncreaseKm
            ).any { it == null })
            return defer(null, "LONG_RUN_LIMITS_INCOMPLETE",
                "Long-run continuity needs an explicit previous run, ceiling and increment.")

        val revisedWeeks = mutableListOf<IndependentWeekMileageCandidate>()
        var previousBuildLong = limits.approvedPreviousLongRunKm
        var previousWeekLong: Double? = null
        for (week in baseline.weeks) {
            // The weekly total is M4's original proposal. Reallocating the
            // *distance* is permitted; silently reducing or adding mileage is not.
            val exactWeekInput = input.copy(
                approvedStartingWeeklyKm = week.weeklyKilometers,
                requestedTargetWeeklyKm = week.weeklyKilometers,
                weeks = 1
            )
            val prior = previousBuildLong
            val effectiveLimits = if (week.recoveryWeek && days.size >= 3) {
                // The M4 feasibility contract forbids a recovery-week long
                // run above both the preceding build and previous week.
                limits.copy(
                    approvedPreviousLongRunKm = prior,
                    approvedLongRunCeilingKm = minOf(
                        requireNotNull(limits.approvedLongRunCeilingKm),
                        requireNotNull(prior),
                        previousWeekLong ?: requireNotNull(prior)
                    ),
                    approvedMaxBuildLongRunIncreaseKm = 0.0
                )
            } else limits.copy(approvedPreviousLongRunKm = prior)

            val single = when (val decision =
                IndependentAdaptiveWeek.allocate(exactWeekInput, effectiveLimits, policy)
            ) {
                is IndependentWeeklyMileageDecision.Deferred ->
                    return defer(week.week, decision.ruleId, decision.explanation)
                is IndependentWeeklyMileageDecision.Candidate ->
                    decision.weeks.single()
            }

            if (abs(single.weeklyKilometers - week.weeklyKilometers) >= 0.00001)
                return defer(week.week, "WEEKLY_VOLUME_CHANGED",
                    "Daily constraint repair must preserve the M4 weekly volume exactly.")
            val rebuilt = week.copy(
                dayAllocations = single.dayAllocations,
                ruleIds = (week.ruleIds + "M6A_CONSTRAINT_AWARE_DAY_ALLOCATION").distinct()
            )
            revisedWeeks += rebuilt
            val provisional = baseline.copy(
                weeks = revisedWeeks.toList(),
                ruleIds = (baseline.ruleIds + "M6A_MULTI_WEEK_CONSTRAINT_SEARCH").distinct()
            )
            // Rechecking the entire prefix detects both continuity errors
            // and any week boundary mismatch before continuing the sequence.
            when (val checked = IndependentMileageFeasibility.check(provisional, limits, policy)) {
                is IndependentMileageFeasibilityResult.Checked -> Unit
                is IndependentMileageFeasibilityResult.Conflict ->
                    return defer(week.week, checked.ruleId, checked.explanation)
                is IndependentMileageFeasibilityResult.Unchecked ->
                    return defer(week.week, checked.ruleId, checked.explanation)
            }
            val selectedLong = rebuilt.longRunKilometers
            if (selectedLong != null) {
                if (!week.recoveryWeek) previousBuildLong = selectedLong
                previousWeekLong = selectedLong
            }
        }

        val completeMileage = baseline.copy(
            weeks = revisedWeeks.toList(),
            ruleIds = (baseline.ruleIds + listOf(
                "M6A_MULTI_WEEK_CONSTRAINT_SEARCH",
                "M6A_NO_AUTOMATIC_FUTURE_FEEDBACK",
                "M6A_SOURCE_AND_APPROVALS_UNVERIFIED"
            )).distinct()
        )
        val baseWorkouts = when (val result =
            IndependentRunWeekComposer.generate(completeMileage, limits, policy)
        ) {
            is IndependentRunWeekCompositionDecision.Deferred ->
                return defer(null, result.ruleId, result.explanation)
            is IndependentRunWeekCompositionDecision.Candidate -> result
        }

        return IndependentConstrainedMultiweekDecision.Candidate(
            mileage = completeMileage,
            baseWorkouts = baseWorkouts,
            ruleIds = listOf(
                "M4_WEEKLY_PROGRESSION_RETAINED",
                "CONSTRAINED_DAILY_ALLOCATION_ALL_WEEKS",
                "LONG_RUN_CONTINUITY_RECHECKED",
                "M5A_TYPED_DISTANCE_WORKOUTS_RECONCILED",
                "HEURISTIC_RESEARCH_ONLY",
                "NO_QUALITY_OR_INTENSITY_AUTHORIZATION",
                "NO_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
