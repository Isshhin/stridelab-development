package io.stridelab.privatealpha

import kotlin.math.ceil
import kotlin.math.floor

/**
 * M4 Batch 1: research-only weekly running-volume allocations.
 *
 * M3's proposedStartingKm is NOT a verified or approved training load and MUST
 * NOT be automatically mapped to approvedStartingWeeklyKm. Both the baseline
 * and the growth ceiling require separate, external approval before invocation.
 * Caller "approved" fields remain unverified assertions in this isolated module.
 *
 * No existing plan, repository record, saved workout or Android screen is changed.
 * These kilometers are candidate allocations, NOT executable prescriptions or
 * proof that a percentage-based progression is safe for a specific athlete.
 */
internal data class IndependentWeeklyMileageInput(
    val approvedStartingWeeklyKm: Double?,
    val approvedWeeklyCeilingKm: Double?,
    val requestedTargetWeeklyKm: Double?,
    val weeks: Int,
    val runningDays: Set<Int>,
    val preferredLongRunDay: Int
) {
    init {
        require(weeks in 1..52)
        require(runningDays.all { it in 1..7 })
        require(preferredLongRunDay in 1..7)
        require(listOfNotNull(
            approvedStartingWeeklyKm, approvedWeeklyCeilingKm, requestedTargetWeeklyKm
        ).all { it.isFinite() && it in 0.0..1000.0 })
    }
}

/** Illustrative research settings; neither 5% growth nor a recovery week is clinical evidence. */
internal data class IndependentWeeklyMileagePolicy(
    val maxBuildIncreaseFraction: Double = 0.05,
    val recoveryEveryWeeks: Int = 4,
    val recoveryReductionFraction: Double = 0.15,
    val preferredLongRunShare: Double = 0.28,
    val maximumLongRunShare: Double = 0.35
) {
    init {
        require(maxBuildIncreaseFraction.isFinite() &&
            maxBuildIncreaseFraction in 0.0..0.20)
        require(recoveryEveryWeeks in 2..12)
        require(recoveryReductionFraction.isFinite() &&
            recoveryReductionFraction in 0.0..0.50)
        require(preferredLongRunShare.isFinite() &&
            preferredLongRunShare in 0.0..0.50)
        require(maximumLongRunShare.isFinite() &&
            maximumLongRunShare in 0.0..0.50 &&
            preferredLongRunShare <= maximumLongRunShare)
    }
}

internal data class IndependentDayMileageAllocation(
    val isoDay: Int,
    val kilometers: Double,
    val longRunCandidate: Boolean
) {
    init {
        require(isoDay in 1..7)
        require(kilometers.isFinite() && kilometers >= 0.0)
    }
}

internal data class IndependentWeekMileageCandidate(
    val week: Int,
    val weeklyKilometers: Double,
    val buildReferenceKilometers: Double,
    val recoveryWeek: Boolean,
    val dayAllocations: List<IndependentDayMileageAllocation>,
    val ruleIds: List<String>
) {
    init {
        require(week > 0)
        require(weeklyKilometers.isFinite() && weeklyKilometers >= 0.0)
        require(buildReferenceKilometers.isFinite() && buildReferenceKilometers >= 0.0)
        require(dayAllocations.map { it.isoDay }.distinct().size == dayAllocations.size)
        require(ruleIds.distinct() == ruleIds)
    }
    val longRunKilometers: Double? get() =
        dayAllocations.singleOrNull { it.longRunCandidate }?.kilometers
}

internal sealed interface IndependentWeeklyMileageDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentWeeklyMileageDecision

    data class Candidate(
        val weeks: List<IndependentWeekMileageCandidate>,
        val requestedTargetWeeklyKm: Double?,
        val targetReachedOnBuildWeek: Boolean?,
        val ruleIds: List<String>,
        val policyEvidence: String = "HEURISTIC_UNVALIDATED",
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentWeeklyMileageDecision {
        init {
            require(weeks.isNotEmpty())
            require(ruleIds.distinct() == ruleIds)
            require(policyEvidence == "HEURISTIC_UNVALIDATED")
            require(!sourceEvidenceVerified && !authorizedForProduction)
        }
    }
}

/**
 * Model:
 * - Week 1 begins at an externally approved starting volume (or a lower target).
 * - Each BUILD week can increase at most one policy increment, bounded by BOTH
 *   the requested target and an independently approved absolute weekly ceiling.
 * - Each recovery week reduces that week's mileage. The subsequent build resumes
 *   from the pre-recovery reference: missed kilometers are NEVER made up.
 * - Daily allocations are rounded down to whole 0.01 km units and sum exactly.
 * - With two running days there is no distinguished long run (even split);
 *   with 3+ days, the long-day candidate is bounded by the configured share.
 * - No speed, intensity, training eligibility, quality work or session duration
 *   is inferred. A later M5 planner must validate individual sessions.
 */
internal object IndependentWeeklyMileagePlanner {
    const val POLICY_ID = "stridelab.engine15.m4-weekly-mileage-research-v1"
    private const val HUNDRED = 100.0

    fun generate(
        input: IndependentWeeklyMileageInput,
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentWeeklyMileageDecision {
        fun defer(id: String, message: String) =
            IndependentWeeklyMileageDecision.Deferred(id, message)

        if (input.runningDays.size < 2)
            return defer("INSUFFICIENT_RUN_DAYS", "At least two running days are required for this research allocation.")
        if (input.preferredLongRunDay !in input.runningDays)
            return defer("LONG_DAY_UNAVAILABLE", "The requested long-run day is not an available running day.")

        val baseline = input.approvedStartingWeeklyKm
            ?: return defer("STARTING_VOLUME_NOT_APPROVED",
                "M3's provisional capacity estimate is not an approved weekly training load.")
        val ceiling = input.approvedWeeklyCeilingKm
            ?: return defer("WEEKLY_CEILING_NOT_APPROVED",
                "No independently approved weekly-volume ceiling was supplied.")
        if (baseline <= 0.0)
            return defer("RETURN_TO_RUN_BASELINE_REQUIRED",
                "Zero or missing recent training requires a separately reviewed return-to-run starting volume.")
        if (ceiling < baseline)
            return defer("BASELINE_EXCEEDS_APPROVED_CEILING",
                "The stated starting volume is already above the approved ceiling.")

        val desired = input.requestedTargetWeeklyKm ?: baseline
        val goal = minOf(desired, ceiling)
        var buildReference = minOf(baseline, goal)
        val ceilingUnits = floor(ceiling * HUNDRED + 1e-8).toLong()
        val days = input.runningDays.sorted()
        val allWeeks = mutableListOf<IndependentWeekMileageCandidate>()

        repeat(input.weeks) { index ->
            val number = index + 1
            val recovery = number % policy.recoveryEveryWeeks == 0
            if (number > 1 && !recovery) {
                // Recovery weeks leave the build anchor unchanged; the next
                // build has one bounded increment, never two catch-up jumps.
                buildReference = minOf(
                    goal,
                    buildReference * (1.0 + policy.maxBuildIncreaseFraction)
                )
            }
            val candidateKm = if (recovery)
                buildReference * (1.0 - policy.recoveryReductionFraction)
            else buildReference
            val units = floor(candidateKm * HUNDRED + 1e-8).toLong()
                .coerceIn(0L, ceilingUnits)
            val distribution = distribute(units, days, input.preferredLongRunDay, policy)
                ?: return defer("LONG_RUN_SHARE_CONFLICT",
                    "Mileage is too small to distribute within the selected long-run share.")
            val policies = mutableListOf("EXTERNALLY_APPROVED_START_ASSERTED",
                "EXTERNALLY_APPROVED_CEILING_ASSERTED", "NO_QUALITY_AUTHORIZATION")
            if (number > 1 && !recovery) policies += "HEURISTIC_BUILD_INCREMENT"
            if (recovery) policies += "HEURISTIC_RECOVERY_WITHOUT_CATCHUP"
            if (days.size == 2) policies += "TWO_DAY_EVEN_SPLIT_NO_LONG_RUN"
            else policies += "HEURISTIC_LONG_RUN_SHARE_BOUND"
            allWeeks += IndependentWeekMileageCandidate(
                week = number,
                weeklyKilometers = units / HUNDRED,
                buildReferenceKilometers = buildReference,
                recoveryWeek = recovery,
                dayAllocations = distribution,
                ruleIds = policies
            )
        }
        val reached = input.requestedTargetWeeklyKm?.let { target ->
            allWeeks.any { !it.recoveryWeek && it.weeklyKilometers + 0.01 >= target }
        }
        val ruleIds = mutableListOf(
            "INDEPENDENT_MILEAGE_RESEARCH_ONLY",
            "CALLER_APPROVED_INPUTS_NOT_SOURCE_VERIFIED",
            "NO_PRODUCTION_PLAN_MUTATION"
        )
        if (input.requestedTargetWeeklyKm != null && desired > ceiling)
            ruleIds += "TARGET_EXCEEDS_APPROVED_CEILING"
        if (input.requestedTargetWeeklyKm != null && desired < baseline)
            ruleIds += "LOWER_TARGET_OVERRIDES_START"
        if (reached == false)
            ruleIds += "REQUESTED_TARGET_NOT_REACHED"
        return IndependentWeeklyMileageDecision.Candidate(
            weeks = allWeeks,
            requestedTargetWeeklyKm = input.requestedTargetWeeklyKm,
            targetReachedOnBuildWeek = reached,
            ruleIds = ruleIds
        )
    }

    /** Returns null if the selected weekly load cannot meet the long-run cap. */
    private fun distribute(
        units: Long,
        days: List<Int>,
        longDay: Int,
        policy: IndependentWeeklyMileagePolicy
    ): List<IndependentDayMileageAllocation>? {
        val n = days.size
        val split = if (n == 2) (units + 1) / 2 else {
            // Long-day minimum must not be smaller than every other day.
            val minimum = ceil(units.toDouble() / n).toLong()
            val preferred = floor(units * maxOf(policy.preferredLongRunShare, 1.0 / n)).toLong()
            val maximum = floor(units * policy.maximumLongRunShare + 1e-8).toLong()
            if (minimum > maximum) return null
            maxOf(minimum, preferred).coerceAtMost(maximum)
        }
        val others = days.filter { it != longDay }
        val remaining = units - split
        val quotient = remaining / others.size
        val extra = (remaining % others.size).toInt()
        val allocations = mutableMapOf<Int, Long>(longDay to split)
        for ((i, d) in others.withIndex())
            allocations[d] = quotient + if (i < extra) 1L else 0L
        check(allocations.values.sum() == units)
        return days.map { d ->
            IndependentDayMileageAllocation(
                isoDay = d,
                kilometers = requireNotNull(allocations[d]) / HUNDRED,
                longRunCandidate = n >= 3 && d == longDay
            )
        }
    }
}
