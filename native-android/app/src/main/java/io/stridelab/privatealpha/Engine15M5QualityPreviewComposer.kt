package io.stridelab.privatealpha

/**
 * M5 Stage B: unscheduled, non-executable quality *previews* attached to
 * existing M5A EASY slots. Never replaces the distance-only base workout.
 *
 * External review flags are caller assertions; M3 and Q3 do not authenticate
 * quality intensity, fitness, threshold pace or permission to increase dose.
 */
internal data class Engine15M5QualityPreviewRequest(
    val weekNumber: Int,
    val slotNumber: Int,
    val family: IndependentQualityFamily,
    val fitnessInput: IndependentQualityFitnessInput,
    val explicitlyReviewedDose: Boolean,
    val reviewReference: String?
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(reviewReference == null || reviewReference.length <= 256)
    }
}

internal data class Engine15M5QualitySlotPreview(
    val weekNumber: Int,
    val slotNumber: Int,
    val originalEasyDistanceKm: Double,
    val family: IndependentQualityFamily,
    val workout: IndependentWorkoutComposition,
    val totalSeconds: Int,
    val workSeconds: Int,
    val ruleIds: List<String>
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(originalEasyDistanceKm.isFinite() && originalEasyDistanceKm > 0.0)
        require(totalSeconds > 0 && workSeconds > 0 && totalSeconds >= workSeconds)
        require(ruleIds.distinct() == ruleIds)
        require(workout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
        require(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
    }

    /** The timed alternative does not replace or add to the M4B kilometre budget. */
    val distanceReconciled: Boolean get() = false
    val sourceEvidenceVerified: Boolean get() = false
    val executable: Boolean get() = false
}

internal sealed interface Engine15M5QualityDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M5QualityDecision

    data class Candidate(
        val policyId: String,
        val baseWeeks: List<Engine15M5WeekPreview>,
        val qualityPreviews: List<Engine15M5QualitySlotPreview>,
        val ruleIds: List<String>,
        val scheduled: Boolean = false,
        val sourceEvidenceVerified: Boolean = false,
        val distanceReconciled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M5QualityDecision {
        init {
            require(baseWeeks.isNotEmpty() && qualityPreviews.isNotEmpty())
            require(qualityPreviews.groupBy { it.weekNumber }.all { it.value.size == 1 })
            require(ruleIds.distinct() == ruleIds)
            require(!scheduled && !sourceEvidenceVerified && !distanceReconciled &&
                !executable && !authorizedForProduction)
        }
    }
}

/**
 * This module does NOT infer a quality dose from weekly mileage. It only
 * visualizes an already externally reviewed, bounded M2 quality alternative.
 * M6 must decide dates/spacing and a future gate must reconcile timed segments
 * to independent mileage budgets with verified pace/distance source evidence.
 */
internal object Engine15M5QualityPreviewComposer {
    const val POLICY_ID = "engine15.m5b.m4b-reviewed-quality-shadow-20261117.1"

    fun compose(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>
    ): Engine15M5QualityDecision {
        fun defer(code: String, explanation: String, week: Int? = null) =
            Engine15M5QualityDecision.Deferred(code, explanation, week)

        val base = when (val built = Engine15M5WorkoutComposer.compose(source)) {
            is Engine15M5CompositionDecision.Deferred ->
                return defer("BASE_" + built.reasonCode, built.explanation, built.weekNumber)
            is Engine15M5CompositionDecision.Candidate -> built
        }
        if (requests.isEmpty())
            return defer("NO_EXPLICIT_QUALITY_REQUESTS",
                "M5B never infers a quality workout from mileage alone.")
        if (requests.groupBy { it.weekNumber }.any { it.value.size > 1 })
            return defer("MULTIPLE_QUALITY_REQUESTS_IN_WEEK",
                "M5B reviews at most one quality preview per build week.")
        if (source.input.returning)
            return defer("RETURNING_RUNNER_QUALITY_HOLD",
                "Quality composition for a returning runner needs separate progression review.")
        if (source.input.runningDays < 3)
            return defer("INSUFFICIENT_RUN_DAYS_FOR_QUALITY",
                "Two-day weeks need separate quality spacing and recovery review.")

        val previews = mutableListOf<Engine15M5QualitySlotPreview>()
        for (request in requests.sortedWith(compareBy({ it.weekNumber }, { it.slotNumber }))) {
            val week = base.weeks.singleOrNull { it.weekNumber == request.weekNumber }
                ?: return defer("QUALITY_WEEK_NOT_FOUND",
                    "Quality preview requested outside the independent M4B plan horizon.",
                    request.weekNumber)
            if (week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY)
                return defer("QUALITY_RECOVERY_WEEK_HOLD",
                    "The M5B preview policy holds quality in recovery weeks.",
                    request.weekNumber)
            val slot = week.slots.singleOrNull { it.slotNumber == request.slotNumber }
                ?: return defer("QUALITY_SLOT_NOT_FOUND",
                    "The requested slot was not generated by M5A.", request.weekNumber)
            if (slot.role != Engine15M5RunRole.EASY)
                return defer("QUALITY_ON_LONG_SLOT",
                    "M5B does not place quality alternatives on long-run slots.",
                    request.weekNumber)
            if (!request.explicitlyReviewedDose || request.reviewReference.isNullOrBlank())
                return defer("QUALITY_EXTERNAL_REVIEW_MISSING",
                    "A positive external work-dose review and reference are required.",
                    request.weekNumber)
            // A caller-supplied review is not independent source attestation.
            // The bounded, deterministic existing M2 research adapter owns
            // timed workout structure and rejects unknown exposure/ceilings.
            when (val selected = IndependentQualityFitnessAdapter.generate(
                request.family, request.fitnessInput
            )) {
                is IndependentQualityFitnessDecision.Deferred ->
                    return defer("QUALITY_" + selected.ruleId, selected.explanation,
                        request.weekNumber)
                is IndependentQualityFitnessDecision.Candidate ->
                    previews += Engine15M5QualitySlotPreview(
                        weekNumber = request.weekNumber,
                        slotNumber = request.slotNumber,
                        originalEasyDistanceKm = slot.distanceKm,
                        family = selected.family,
                        workout = selected.workout,
                        totalSeconds = selected.totalSeconds,
                        workSeconds = selected.workSeconds,
                        ruleIds = selected.ruleIds + listOf(
                            "EXTERNAL_REVIEW_CALLER_ASSERTED",
                            "BASE_EASY_DISTANCE_UNCHANGED",
                            "QUALITY_DISTANCE_UNRECONCILED",
                            "UNSCHEDULED_NON_EXECUTABLE_SHADOW"
                        )
                    )
            }
        }
        return Engine15M5QualityDecision.Candidate(
            policyId = POLICY_ID,
            baseWeeks = base.weeks,
            qualityPreviews = previews,
            ruleIds = listOf(
                "M4B_AND_M5A_RECOMPUTED",
                "AT_MOST_ONE_REVIEWED_QUALITY_PREVIEW_PER_BUILD_WEEK",
                "NO_AUTOMATIC_QUALITY_FROM_MILEAGE",
                "NO_VERIFIED_INTENSITY_OR_KILOMETRE_CONVERSION",
                "NO_CALENDAR_OR_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
