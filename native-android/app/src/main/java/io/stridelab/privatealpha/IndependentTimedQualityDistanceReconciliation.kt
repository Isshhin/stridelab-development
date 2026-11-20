package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M9a: research-only *arithmetic reconciliation* of a time-based quality
 * preview with its EXISTING M6a mileage allocation. All segment distances are
 * explicit caller assertions. No running pace, quality dose, threshold zone,
 * completed workout, or training readiness is inferred from time.
 *
 * A reconciled candidate is still NOT an executable workout: M3 source
 * provenance and dose approval remain external unresolved gates.
 */
internal data class IndependentTimedSegmentDistance(
    val purpose: WorkoutPurpose,
    val target: WorkoutTarget,
    val seconds: Double,
    val assertedDistanceMeters: Double
) {
    init {
        require(seconds.isFinite() && seconds > 0.0)
        require(assertedDistanceMeters.isFinite() && assertedDistanceMeters > 0.0)
    }
}

internal sealed interface IndependentTimedDistanceDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentTimedDistanceDecision

    data class MathematicallyReconciled(
        val week: Int,
        val isoDay: Int,
        val sessionDistanceKm: Double,
        val weekDistanceKmUnchanged: Double,
        val segmentDistances: List<IndependentTimedSegmentDistance>,
        val workSeconds: Double,
        val totalSeconds: Double,
        val ruleIds: List<String>,
        val sourceDistancesVerified: Boolean = false,
        val intensityVerified: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentTimedDistanceDecision {
        init {
            require(week > 0 && isoDay in 1..7)
            require(sessionDistanceKm.isFinite() && sessionDistanceKm > 0.0)
            require(weekDistanceKmUnchanged.isFinite() && weekDistanceKmUnchanged > 0.0)
            require(segmentDistances.isNotEmpty())
            require(workSeconds.isFinite() && workSeconds > 0.0)
            require(totalSeconds.isFinite() && totalSeconds >= workSeconds)
            require(abs(segmentDistances.sumOf { it.seconds } - totalSeconds) <= 0.00001)
            require(abs(segmentDistances.sumOf { it.assertedDistanceMeters } -
                sessionDistanceKm * 1000.0) <= 0.01)
            require(ruleIds.distinct().size == ruleIds.size)
            require(!sourceDistancesVerified && !intensityVerified &&
                !executable && !authorizedForProduction)
        }
    }
}

internal object IndependentTimedQualityDistanceReconciliation {
    const val POLICY_ID = "stridelab.engine15.m9a-timed-quality-reconciliation-v1"

    private fun defer(id: String, detail: String) =
        IndependentTimedDistanceDecision.Deferred(id, detail)

    private data class TimedStep(
        val purpose: WorkoutPurpose,
        val target: WorkoutTarget,
        val seconds: Double
    )

    /**
     * Flatten the M2 workout contract in actual chronological order. For
     * interval groups, recovery is BETWEEN work repetitions only; there is
     * no fictitious final recovery.
     */
    private fun expand(workout: IndependentWorkoutComposition): List<TimedStep>? {
        val output = mutableListOf<TimedStep>()
        fun add(step: WorkoutStep): Boolean {
            if (step.unit != WorkoutUnit.SECONDS) return false
            output += TimedStep(step.purpose, step.target, step.amount)
            return true
        }
        for (element in workout.elements) when (element) {
            is IndependentWorkoutElement.Step ->
                repeat(element.value.repetitions) {
                    if (!add(element.value)) return null
                }
            is IndependentWorkoutElement.Intervals -> {
                val group = element.value
                repeat(group.repetitions) { n ->
                    if (!add(group.work)) return null
                    if (n < group.repetitions - 1 && !add(group.recovery)) return null
                }
            }
        }
        return output
    }

    fun review(
        base: IndependentConstrainedMultiweekDecision.Candidate,
        spacing: IndependentQualitySpacingDecision.Candidate,
        quality: IndependentQualityDayPreview,
        assertedSegmentMeters: List<Double>,
        reviewReference: String?
    ): IndependentTimedDistanceDecision {
        if (reviewReference.isNullOrBlank() || reviewReference.length > 256)
            return defer("EXTERNAL_SEGMENT_REVIEW_MISSING",
                "Every segment distance must have an explicitly recorded external review reference.")
        val week = base.baseWorkouts.weeks.singleOrNull { it.week == quality.week }
            ?: return defer("WEEK_NOT_FOUND", "The reviewed week does not exist in M6a.")
        val originalWeek = base.mileage.weeks.singleOrNull { it.week == quality.week }
            ?: return defer("WEEK_NOT_FOUND", "The original mileage week does not exist.")
        if (week.recoveryWeek)
            return defer("RECOVERY_WEEK_QUALITY_HOLD", "No quality replacement is reviewed in a recovery week.")
        val session = week.sessions.singleOrNull { it.isoDay == quality.isoDay }
            ?: return defer("DAY_NOT_FOUND", "The quality day is not a scheduled running day.")
        if (session.kind != IndependentRunSessionKind.EASY)
            return defer("LONG_RUN_QUALITY_CONFLICT", "A long run cannot be replaced with a quality preview.")
        val selectedWeek = spacing.weeks.singleOrNull { it.week == quality.week }
            ?: return defer("SPACING_WEEK_NOT_FOUND", "M6b has no reviewed calendar for the quality week.")
        val opportunity = selectedWeek.opportunities.singleOrNull { it.isoDay == quality.isoDay }
            ?: return defer("SPACING_SLOT_NOT_SELECTED",
                "Quality must match a specifically selected non-recovery M6b opportunity.")
        if (abs(opportunity.preexistingEasyRunKm - session.allocatedKilometers) > 0.00001)
            return defer("SPACING_SESSION_MISMATCH", "M6b refers to a different original easy-run allocation.")
        if (abs(quality.baseAllocatedKilometers - session.allocatedKilometers) > 0.00001)
            return defer("QUALITY_DISTANCE_BUDGET_MISMATCH",
                "The quality preview must use the exact original M6a distance budget.")
        if (originalWeek.dayAllocations.singleOrNull { it.isoDay == quality.isoDay }
                ?.kilometers != session.allocatedKilometers)
            return defer("BASE_DISTANCE_RECONCILIATION_MISMATCH",
                "M6a's mileage and M5a typed workout disagree.")

        val phases = expand(quality.qualityWorkout)
            ?: return defer("MIXED_UNITS_CANNOT_RECONCILE",
                "Every element needs one timed step; no time-to-distance inference is permitted.")
        if (phases.size < 3 || phases.first().purpose != WorkoutPurpose.WARM_UP ||
            phases.last().purpose != WorkoutPurpose.COOL_DOWN ||
            phases.first().target != WorkoutTarget.EASY ||
            phases.last().target != WorkoutTarget.EASY ||
            phases.drop(1).dropLast(1).any {
                it.purpose !in setOf(WorkoutPurpose.WORK, WorkoutPurpose.RECOVERY) ||
                    (it.purpose == WorkoutPurpose.RECOVERY && it.target != WorkoutTarget.EASY)
            })
            return defer("INVALID_TIMED_PHASE_STRUCTURE", "A quality preview needs warm-up, work, optional easy recoveries, and cool-down.")
        val expectedTarget = when (quality.family) {
            IndependentQualityFamily.THRESHOLD -> WorkoutTarget.THRESHOLD
            IndependentQualityFamily.TEMPO -> WorkoutTarget.TEMPO
            IndependentQualityFamily.INTERVALS -> WorkoutTarget.INTERVALS
        }
        if (phases.none { it.purpose == WorkoutPurpose.WORK } ||
            phases.any { it.purpose == WorkoutPurpose.WORK && it.target != expectedTarget })
            return defer("QUALITY_WORK_TARGET_MISMATCH",
                "Work segments must have the declared quality family; no intensity classification is inferred.")
        val seconds = phases.sumOf { it.seconds }
        val workSeconds = phases.filter { it.purpose == WorkoutPurpose.WORK }.sumOf { it.seconds }
        if (abs(seconds - quality.totalSeconds) > 0.00001 ||
            abs(workSeconds - quality.workSeconds) > 0.00001)
            return defer("QUALITY_DURATION_CONTRACT_MISMATCH",
                "The individual segment times do not reconcile with the quality preview.")
        if (assertedSegmentMeters.size != phases.size)
            return defer("SEGMENT_DISTANCE_COUNT_MISMATCH",
                "Every warm-up, work, recovery and cool-down segment needs its own asserted distance.")
        if (assertedSegmentMeters.any { !it.isFinite() || it <= 0.0 })
            return defer("INVALID_SEGMENT_DISTANCE",
                "Missing, invalid or nonpositive segment distances cannot be inferred from seconds.")
        val distances = phases.zip(assertedSegmentMeters).map { (p, meters) ->
            IndependentTimedSegmentDistance(p.purpose, p.target, p.seconds, meters)
        }
        if (abs(distances.sumOf { it.assertedDistanceMeters } -
                session.allocatedKilometers * 1000.0) > 0.01)
            return defer("QUALITY_DISTANCE_SUM_MISMATCH",
                "Segment distances must sum to the unchanged original daily distance budget.")
        // Replacement, not addition. No new distance enters the M6a week.
        return IndependentTimedDistanceDecision.MathematicallyReconciled(
            week = quality.week,
            isoDay = quality.isoDay,
            sessionDistanceKm = session.allocatedKilometers,
            weekDistanceKmUnchanged = originalWeek.weeklyKilometers,
            segmentDistances = distances,
            workSeconds = workSeconds,
            totalSeconds = seconds,
            ruleIds = listOf(
                "M6A_ORIGINAL_WEEK_AND_DAY_MILEAGE_RETAINED",
                "M6B_QUALITY_SPACING_MATCH",
                "M5A_TIMED_SEGMENTS_EXPANDED_WITHOUT_PACE_INFERENCE",
                "CALLER_ASSERTED_SEGMENT_DISTANCES_SUM_TO_BUDGET",
                "SOURCE_AND_INTENSITY_NOT_VERIFIED",
                "NO_EXECUTABLE_OR_PRODUCTION_AUTHORIZATION"
            )
        )
    }
}
