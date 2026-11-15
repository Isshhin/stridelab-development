package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M5a Batch 1: research-only connection from M4's checked weekly allocations
 * into M2's typed, versioned workout step contract.
 *
 * Only distance-based EASY and LONG candidate sessions are represented.
 * LONG means an easy-intensity run on the designated long day, NOT a
 * separate intensity zone. All time, HR, pace and physiological questions
 * remain unknown. No quality workout or strides are invented.
 *
 * Caller-provided M4 approval inputs are not independently attested.
 * This module never mutates plans, persists records, or changes the app.
 */
internal enum class IndependentRunSessionKind { EASY, LONG }

internal data class IndependentRunSessionCandidate(
    val week: Int,
    val isoDay: Int,
    val kind: IndependentRunSessionKind,
    val allocatedKilometers: Double,
    val workout: IndependentWorkoutComposition,
    val ruleIds: List<String>
) {
    init {
        require(week >= 1 && isoDay in 1..7)
        require(allocatedKilometers.isFinite() && allocatedKilometers > 0.0)
        require(ruleIds.distinct() == ruleIds)
        require(workout.elements.size == 1)
        val step = (workout.elements.single() as? IndependentWorkoutElement.Step)?.value
            ?: throw IllegalArgumentException("Base easy/long session must be a single distance step.")
        require(step.purpose == WorkoutPurpose.WORK &&
            step.unit == WorkoutUnit.METERS &&
            step.target == WorkoutTarget.EASY &&
            step.repetitions == 1)
        val observedDistance = requireNotNull(workout.total(WorkoutUnit.METERS))
        require(abs(observedDistance - allocatedKilometers * 1000.0) < 0.00001)
    }

    val estimatedDurationSeconds: Int? get() = null
    val qualityWorkAuthorized: Boolean get() = false
}

internal data class IndependentRunWeekPreview(
    val week: Int,
    val recoveryWeek: Boolean,
    val weeklyKilometers: Double,
    val sessions: List<IndependentRunSessionCandidate>
) {
    init {
        require(week >= 1 && weeklyKilometers.isFinite() && weeklyKilometers >= 0.0)
        require(sessions.isNotEmpty() && sessions.all { it.week == week })
        require(sessions.map { it.isoDay }.distinct().size == sessions.size)
        require(abs(sessions.sumOf { it.allocatedKilometers } - weeklyKilometers) < 0.00001)
    }
}

internal sealed interface IndependentRunWeekCompositionDecision {
    data class Deferred(
        val ruleId: String,
        val explanation: String
    ) : IndependentRunWeekCompositionDecision

    data class Candidate(
        val weeks: List<IndependentRunWeekPreview>,
        val ruleIds: List<String>,
        val policyEvidence: String = "HEURISTIC_UNVALIDATED",
        val sourceEvidenceVerified: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentRunWeekCompositionDecision {
        init {
            require(weeks.isNotEmpty())
            require(weeks.map { it.week }.distinct().size == weeks.size)
            require(ruleIds.distinct() == ruleIds)
            require(policyEvidence == "HEURISTIC_UNVALIDATED")
            require(!sourceEvidenceVerified && !authorizedForProduction)
        }
    }
}

/**
 * Re-check feasibility INSIDE the composer; never trust an externally constructed
 * "Checked" object to authorize sessions. A constraint failure or absent
 * evidence defers the whole candidate rather than silently dropping a day.
 */
internal object IndependentRunWeekComposer {
    const val POLICY_ID = "stridelab.engine15.m5a-basic-composition-v1"

    fun generate(
        allocation: IndependentWeeklyMileageDecision.Candidate,
        limits: IndependentMileageFeasibilityLimits,
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentRunWeekCompositionDecision {
        fun defer(rule: String, why: String) =
            IndependentRunWeekCompositionDecision.Deferred(rule, why)
        when (val checked = IndependentMileageFeasibility.check(allocation, limits, policy)) {
            is IndependentMileageFeasibilityResult.Unchecked ->
                return defer("FEASIBILITY_UNCHECKED_${checked.ruleId}", checked.explanation)
            is IndependentMileageFeasibilityResult.Conflict ->
                return defer("FEASIBILITY_CONFLICT_${checked.ruleId}", checked.explanation)
            is IndependentMileageFeasibilityResult.Checked -> Unit
        }

        if (allocation.weeks.any { w -> w.dayAllocations.any { it.kilometers <= 0.0 } })
            return defer("ZERO_DISTANCE_RUN_DAY",
                "The reviewed week contains a zero-distance run; an executable workout cannot be invented.")

        val weeks = allocation.weeks.map { week ->
            val sessions = week.dayAllocations.map { day ->
                val kind = if (day.longRunCandidate)
                    IndependentRunSessionKind.LONG else IndependentRunSessionKind.EASY
                // M4 uses hundredth-kilometer units. Convert without rounding
                // again and retain exactly one M2 distance step per run.
                val step = WorkoutStep(
                    purpose = WorkoutPurpose.WORK,
                    unit = WorkoutUnit.METERS,
                    amount = day.kilometers * 1000.0,
                    repetitions = 1,
                    target = WorkoutTarget.EASY
                )
                IndependentRunSessionCandidate(
                    week = week.week,
                    isoDay = day.isoDay,
                    kind = kind,
                    allocatedKilometers = day.kilometers,
                    workout = IndependentWorkoutComposition(
                        listOf(IndependentWorkoutElement.Step(step))
                    ),
                    ruleIds = listOf("M4_DAY_ALLOCATION_RECONCILED",
                        "DISTANCE_ONLY_BASE_SESSION", "INTENSITY_AND_DURATION_NOT_INFERRED")
                )
            }
            IndependentRunWeekPreview(
                week = week.week,
                recoveryWeek = week.recoveryWeek,
                weeklyKilometers = week.weeklyKilometers,
                sessions = sessions
            )
        }
        return IndependentRunWeekCompositionDecision.Candidate(
            weeks = weeks,
            ruleIds = listOf("M4_FEASIBILITY_RECHECKED",
                "M2_TYPED_WORKOUT_COMPOSITION",
                "NO_UNVERIFIED_QUALITY_EXPOSURE",
                "NO_PRODUCTION_PLAN_MUTATION",
                "NO_DEFAULT_SESSION_TIME_LIMIT")
        )
    }
}
