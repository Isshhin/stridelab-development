package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * I5 input is an asserted, manually reviewed mathematical speed ENVELOPE.
 * It is not an authenticated Garmin/FIT speed source or a runner's measured
 * threshold, and cannot be promoted to a pace prescription.
 */
internal data class Engine15I5PhaseSpeedWindow(
    val phaseIndex: Int,
    val purpose: WorkoutPurpose,
    val target: WorkoutTarget,
    val minimumMetersPerSecond: Double,
    val maximumMetersPerSecond: Double
)

internal data class Engine15I5DistanceFeasibilityInput(
    val calendar: Engine15M6QualitySpacingInput,
    val phaseSpeedWindows: List<Engine15I5PhaseSpeedWindow>,
    val assertedExternalSpeedReviewReference: String?
)

internal data class Engine15I5TimedPhaseFeasibility(
    val phaseIndex: Int,
    val purpose: WorkoutPurpose,
    val target: WorkoutTarget,
    val seconds: Double,
    val minimumMeters: Double,
    val maximumMeters: Double
) {
    init {
        require(phaseIndex >= 0 && seconds.isFinite() && seconds > 0.0)
        require(minimumMeters.isFinite() && minimumMeters > 0.0)
        require(maximumMeters.isFinite() && maximumMeters >= minimumMeters)
    }
}

internal sealed interface Engine15I5DistanceFeasibilityDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String
    ) : Engine15I5DistanceFeasibilityDecision

    /**
     * Mathematical possibility under caller-asserted speed windows ONLY.
     * The original EASY slot remains the only independent distance workout.
     */
    data class EnvelopeReview(
        val policyId: String,
        val originalI4: Engine15I4HrSignalDecision.SignalReview,
        val originalM6B: Engine15M6QualitySpacingDecision.Candidate,
        val selectedOpportunity: Engine15M6QualityOpportunity,
        val assertedExternalSpeedReviewReference: String,
        val phaseBounds: List<Engine15I5TimedPhaseFeasibility>,
        val minimumPossibleMeters: Double,
        val maximumPossibleMeters: Double,
        val originalEasySlotMeters: Double,
        val ruleIds: List<String>,
        val boundsExternallyAsserted: Boolean = true,
        val paceCalibrationVerified: Boolean = false,
        val qualityIntensityVerified: Boolean = false,
        val sourceDistancesVerified: Boolean = false,
        val actualSegmentDistancesVerified: Boolean = false,
        val athleteResponseVerified: Boolean = false,
        val doseAuthorized: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15I5DistanceFeasibilityDecision {
        init {
            require(assertedExternalSpeedReviewReference.isNotBlank())
            require(phaseBounds.size in 3..400)
            require(minimumPossibleMeters.isFinite() &&
                minimumPossibleMeters > 0.0)
            require(maximumPossibleMeters.isFinite() &&
                maximumPossibleMeters >= minimumPossibleMeters)
            require(originalEasySlotMeters.isFinite() &&
                originalEasySlotMeters > 0.0)
            require(originalEasySlotMeters >= minimumPossibleMeters - 0.01 &&
                originalEasySlotMeters <= maximumPossibleMeters + 0.01)
            require(abs(phaseBounds.sumOf { it.minimumMeters } -
                minimumPossibleMeters) <= 1e-7)
            require(abs(phaseBounds.sumOf { it.maximumMeters } -
                maximumPossibleMeters) <= 1e-7)
            require(selectedOpportunity.existingRun.role == Engine15M5RunRole.EASY)
            require(abs(selectedOpportunity.existingRun.distanceKm * 1000.0 -
                originalEasySlotMeters) <= 0.01)
            require(originalM6B.opportunities.size == 1)
            require(originalM6B.opportunities.single() == selectedOpportunity)
            require(ruleIds.distinct() == ruleIds)
            require(boundsExternallyAsserted && !paceCalibrationVerified &&
                !qualityIntensityVerified && !sourceDistancesVerified &&
                !actualSegmentDistancesVerified && !athleteResponseVerified &&
                !doseAuthorized && !executable && !authorizedForProduction)
        }
    }
}

/**
 * Independent shadow check:
 *   M4B -> M5B -> I3 -> I4 AND M4B -> M5B -> M6A -> M6B.
 *
 * No speed is derived from HR or overall elapsed/distance summaries. No
 * per-phase distance is invented. A feasible range is not a safe or
 * executable workout and it does not authenticate its caller-supplied bounds.
 */
internal object Engine15I5TimedDistanceFeasibility {
    const val POLICY_ID = "engine15.i5.timed-speed-envelope-shadow-20261117.1"
    private const val METERS_EPS = 0.01

    /** Extreme numeric input sanity bound, NOT a physiological speed ceiling. */
    private const val ABSOLUTE_INPUT_SANITY_MAX_MPS = 15.0

    private data class Phase(
        val purpose: WorkoutPurpose,
        val target: WorkoutTarget,
        val seconds: Double
    )

    /** Recoveries are strictly BETWEEN repeats, never after the last work rep. */
    private fun expand(workout: IndependentWorkoutComposition): List<Phase>? {
        val result = mutableListOf<Phase>()
        fun add(step: WorkoutStep): Boolean {
            if (step.unit != WorkoutUnit.SECONDS ||
                !step.amount.isFinite() || step.amount <= 0.0) return false
            result += Phase(step.purpose, step.target, step.amount)
            return result.size <= 400
        }
        for (element in workout.elements) when (element) {
            is IndependentWorkoutElement.Step -> {
                if (element.value.repetitions !in 1..400) return null
                repeat(element.value.repetitions) {
                    if (!add(element.value)) return null
                }
            }
            is IndependentWorkoutElement.Intervals -> {
                val group = element.value
                if (group.repetitions !in 1..400) return null
                repeat(group.repetitions) { n ->
                    if (!add(group.work)) return null
                    if (n < group.repetitions - 1 && !add(group.recovery)) return null
                }
            }
        }
        return result
    }

    fun review(
        source: Engine15IndependentVolumePlanner.Result,
        qualityRequest: Engine15M5QualityPreviewRequest,
        sourceEvidence: Engine15I3QualityDurationInput,
        input: Engine15I5DistanceFeasibilityInput
    ): Engine15I5DistanceFeasibilityDecision {
        fun defer(code: String, reason: String) =
            Engine15I5DistanceFeasibilityDecision.Deferred(code, reason)

        val signal = when (val found = Engine15I4WorkLapHeartRateReview.inspect(
            source, qualityRequest, sourceEvidence
        )) {
            is Engine15I4HrSignalDecision.Deferred ->
                return defer("I4_" + found.reasonCode, found.explanation)
            is Engine15I4HrSignalDecision.SignalReview -> found
        }
        val spaced = when (val found = Engine15M6IndependentQualitySpacing.propose(
            source, input.calendar, listOf(qualityRequest)
        )) {
            is Engine15M6QualitySpacingDecision.Deferred ->
                return defer("M6B_" + found.reasonCode, found.explanation)
            is Engine15M6QualitySpacingDecision.Candidate -> found
        }

        val preview = signal.originalI3.originalM5B.qualityPreviews.single()
        val opportunity = spaced.opportunities.singleOrNull()
            ?: return defer("QUALITY_OPPORTUNITY_NOT_UNIQUE",
                "Exactly one reviewed quality opportunity must be date-placed.")
        if (opportunity.preview != preview ||
            abs(opportunity.existingRun.distanceKm - preview.originalEasyDistanceKm) > 1e-8)
            return defer("I4_M6B_PREVIEW_IDENTITY_MISMATCH",
                "I4 and M6B must use exactly the same original M5B preview.")

        val reference = input.assertedExternalSpeedReviewReference
        if (reference.isNullOrBlank() || reference.length > 256)
            return defer("SPEED_REVIEW_REFERENCE_MISSING",
                "Independent speed-window assertions need a bounded external review reference.")
        val phases = expand(preview.workout)
            ?: return defer("INVALID_OR_UNBOUNDED_TIMED_WORKOUT",
                "M5B must expand into bounded, positive timed phases.")
        if (phases.size !in 3..400 ||
            phases.first().purpose != WorkoutPurpose.WARM_UP ||
            phases.first().target != WorkoutTarget.EASY ||
            phases.last().purpose != WorkoutPurpose.COOL_DOWN ||
            phases.last().target != WorkoutTarget.EASY)
            return defer("TIMED_WORKOUT_STRUCTURE_MISMATCH",
                "Workouts must retain original warm-up, work and cool-down structure.")
        val workTarget = when (preview.family) {
            IndependentQualityFamily.THRESHOLD -> WorkoutTarget.THRESHOLD
            IndependentQualityFamily.TEMPO -> WorkoutTarget.TEMPO
        }
        val middle = phases.drop(1).dropLast(1)
        if (middle.none { it.purpose == WorkoutPurpose.WORK } ||
            middle.any {
                when (it.purpose) {
                    WorkoutPurpose.WORK -> it.target != workTarget
                    WorkoutPurpose.RECOVERY -> it.target != WorkoutTarget.EASY
                    else -> true
                }
            } ||
            abs(phases.sumOf { it.seconds } - preview.totalSeconds) > 1e-7 ||
            abs(phases.filter { it.purpose == WorkoutPurpose.WORK }
                .sumOf { it.seconds } - preview.workSeconds) > 1e-7)
            return defer("TIMED_DOSE_OR_PHASE_MISMATCH",
                "Expanded timed phases must equal the original reviewed M5B dose.")

        val windows = input.phaseSpeedWindows
        if (windows.size != phases.size)
            return defer("PHASE_SPEED_WINDOW_COVERAGE_MISMATCH",
                "Every original timed phase requires exactly one asserted speed interval.")
        if (windows.indices.any { i -> windows[i].phaseIndex != i })
            return defer("PHASE_SPEED_WINDOW_ORDER_MISMATCH",
                "Phase-speed windows must retain chronological phase indices.")
        if (windows.indices.any { i ->
            windows[i].purpose != phases[i].purpose ||
                windows[i].target != phases[i].target
        })
            return defer("PHASE_SPEED_WINDOW_ROLE_MISMATCH",
                "Asserted speed interval identity must match each original timed step.")
        if (windows.any {
            !it.minimumMetersPerSecond.isFinite() ||
                !it.maximumMetersPerSecond.isFinite() ||
                it.minimumMetersPerSecond <= 0.0 ||
                it.maximumMetersPerSecond < it.minimumMetersPerSecond ||
                it.maximumMetersPerSecond > ABSOLUTE_INPUT_SANITY_MAX_MPS
        })
            return defer("INVALID_ASSERTED_PHASE_SPEED",
                "All asserted phase speeds must be finite, positive and within numeric sanity bounds.")

        val bounds = phases.zip(windows).mapIndexed { index, (phase, range) ->
            Engine15I5TimedPhaseFeasibility(
                phaseIndex = index,
                purpose = phase.purpose,
                target = phase.target,
                seconds = phase.seconds,
                minimumMeters = phase.seconds * range.minimumMetersPerSecond,
                maximumMeters = phase.seconds * range.maximumMetersPerSecond
            )
        }
        val minimumMeters = bounds.sumOf { it.minimumMeters }
        val maximumMeters = bounds.sumOf { it.maximumMeters }
        val allocatedMeters = opportunity.existingRun.distanceKm * 1000.0
        if (!minimumMeters.isFinite() || !maximumMeters.isFinite() ||
            !allocatedMeters.isFinite())
            return defer("DISTANCE_RANGE_OVERFLOW",
                "Cannot safely compute the asserted per-segment distance range.")
        if (allocatedMeters < minimumMeters - METERS_EPS)
            return defer("ALLOCATION_BELOW_ASSERTED_MINIMUM",
                "The original slot mileage is below the shortest distance implied by the asserted speeds.")
        if (allocatedMeters > maximumMeters + METERS_EPS)
            return defer("ALLOCATION_ABOVE_ASSERTED_MAXIMUM",
                "The original slot mileage exceeds the maximum distance implied by the asserted speeds.")

        return Engine15I5DistanceFeasibilityDecision.EnvelopeReview(
            policyId = POLICY_ID,
            originalI4 = signal,
            originalM6B = spaced,
            selectedOpportunity = opportunity,
            assertedExternalSpeedReviewReference = reference,
            phaseBounds = bounds,
            minimumPossibleMeters = minimumMeters,
            maximumPossibleMeters = maximumMeters,
            originalEasySlotMeters = allocatedMeters,
            ruleIds = listOf(
                "I4_SOURCE_LAP_SIGNAL_AND_I3_DURATION_RECHECKED",
                "M4B_M5B_M6A_M6B_RECOMPUTED_AND_IDENTICAL",
                "ORIGINAL_TIMED_PHASES_AND_KILOMETRES_UNCHANGED",
                "EXTERNAL_SPEED_INTERVALS_ARE_CALLER_ASSERTIONS",
                "MINIMUM_AND_MAXIMUM_SEGMENT_DISTANCE_BUDGET",
                "NO_SOURCE_PACE_OR_THRESHOLD_INTENSITY_VERIFICATION",
                "NO_EXECUTABLE_OR_PRODUCTION_AUTHORIZATION"
            )
        )
    }
}
