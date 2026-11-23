package io.stridelab.privatealpha

/**
 * M5a Batch 2: research-only reviewed quality/strides overlays on M4/M5a.
 *
 * A distance-based easy-session budget cannot be converted into the length of
 * a time-based threshold/tempo/strides workout without pace evidence. These
 * are ALTERNATIVE PREVIEWS attached to a calendar day, not additional workouts
 * counted toward weekly kilometers and not executable prescriptions.
 *
 * External dose approval is a caller assertion; M3's provisional source-lap
 * comparisons NEVER authorize these requests. No saved plan or player changes.
 */
internal data class IndependentQualityDayRequest(
    val week: Int,
    val isoDay: Int,
    val family: IndependentQualityFamily,
    val input: IndependentQualityFitnessInput,
    val explicitlyReviewedDose: Boolean,
    val reviewReference: String?
) {
    init {
        require(week > 0 && isoDay in 1..7)
        require(reviewReference == null || reviewReference.length <= 256)
    }
}

internal data class IndependentStridesDayRequest(
    val week: Int,
    val isoDay: Int,
    val repetitions: Int,
    val workSeconds: Int,
    val recoverySeconds: Int,
    val explicitlyReviewed: Boolean,
    val reviewReference: String?
) {
    init {
        require(week > 0 && isoDay in 1..7)
        require(repetitions in 2..8)
        require(workSeconds in 10..30)
        require(recoverySeconds in 30..180)
        require(reviewReference == null || reviewReference.length <= 256)
    }
}

internal data class IndependentQualityDayPreview(
    val week: Int,
    val isoDay: Int,
    val baseAllocatedKilometers: Double,
    val family: IndependentQualityFamily,
    val qualityWorkout: IndependentWorkoutComposition,
    val totalSeconds: Int,
    val workSeconds: Int,
    val ruleIds: List<String>
) {
    init {
        require(week > 0 && isoDay in 1..7)
        require(baseAllocatedKilometers.isFinite() && baseAllocatedKilometers > 0.0)
        require(totalSeconds > 0 && workSeconds > 0 && workSeconds <= totalSeconds)
        require(ruleIds.distinct() == ruleIds)
        require(qualityWorkout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
        require(qualityWorkout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
    }

    /** No pace source connects this session's seconds to M4's kilometer budget. */
    val distanceReconciled: Boolean get() = false
    val executable: Boolean get() = false
}

internal data class IndependentStridesDayPreview(
    val week: Int,
    val isoDay: Int,
    val baseAllocatedKilometers: Double,
    val stridesWorkout: IndependentWorkoutComposition,
    val workSeconds: Int,
    val totalSeconds: Int
) {
    init {
        require(week > 0 && isoDay in 1..7)
        require(baseAllocatedKilometers.isFinite() && baseAllocatedKilometers > 0.0)
        require(workSeconds > 0 && totalSeconds > workSeconds)
        require(stridesWorkout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
    }

    val distanceReconciled: Boolean get() = false
    val executable: Boolean get() = false
}

internal sealed interface IndependentQualityOverlayDecision {
    data class Deferred(val ruleId: String, val explanation: String) :
        IndependentQualityOverlayDecision

    data class Candidate(
        val baseWeeks: List<IndependentRunWeekPreview>,
        val qualityPreviews: List<IndependentQualityDayPreview>,
        val stridesPreviews: List<IndependentStridesDayPreview>,
        val ruleIds: List<String>,
        val sourceEvidenceVerified: Boolean = false,
        val kilometerReconciliationComplete: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : IndependentQualityOverlayDecision {
        init {
            require(baseWeeks.isNotEmpty())
            require(qualityPreviews.isNotEmpty() || stridesPreviews.isNotEmpty())
            require(ruleIds.distinct() == ruleIds)
            require(!sourceEvidenceVerified && !kilometerReconciliationComplete &&
                !authorizedForProduction)
        }
    }
}

/**
 * No automatic dose selection from M3, and no automatic upgrade of an EASY
 * distance-based run to a quality session. The base M4 totals remain unchanged.
 * Quality sessions only appear with explicit separate evidence review and M2
 * authorization. They are intentionally NOT merged into mixed-unit workouts.
 */
internal object IndependentQualityOverlayComposer {
    const val POLICY_ID = "stridelab.engine15.m5a-quality-overlay-research-v1"

    fun generate(
        allocation: IndependentWeeklyMileageDecision.Candidate,
        limits: IndependentMileageFeasibilityLimits,
        qualityRequests: List<IndependentQualityDayRequest> = emptyList(),
        stridesRequests: List<IndependentStridesDayRequest> = emptyList(),
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentQualityOverlayDecision {
        fun defer(id: String, reason: String) =
            IndependentQualityOverlayDecision.Deferred(id, reason)

        val base = when (val result = IndependentRunWeekComposer.generate(
            allocation, limits, policy
        )) {
            is IndependentRunWeekCompositionDecision.Deferred ->
                return defer("BASE_" + result.ruleId, result.explanation)
            is IndependentRunWeekCompositionDecision.Candidate -> result
        }
        if (qualityRequests.isEmpty() && stridesRequests.isEmpty())
            return defer("NO_REVIEWED_OVERLAY_REQUESTS",
                "No explicitly reviewed quality or strides previews were requested.")

        if (qualityRequests.groupBy { it.week }.any { it.value.size > 1 })
            return defer("MULTIPLE_QUALITY_PER_WEEK_UNREVIEWED",
                "This minimal research model accepts at most one quality day per week.")
        if (stridesRequests.groupBy { it.week to it.isoDay }.any { it.value.size > 1 })
            return defer("DUPLICATE_STRIDES_DAY",
                "The same day cannot receive multiple strides overlays.")

        val lookup = base.weeks.flatMap { week ->
            week.sessions.map { (week.week to it.isoDay) to (week to it) }
        }.toMap()

        fun eligibleDay(week: Int, day: Int): Pair<IndependentRunWeekPreview,
            IndependentRunSessionCandidate>? = lookup[week to day]

        val quality = mutableListOf<IndependentQualityDayPreview>()
        for (request in qualityRequests.sortedWith(compareBy({ it.week }, { it.isoDay }))) {
            val pair = eligibleDay(request.week, request.isoDay)
                ?: return defer("QUALITY_DAY_UNAVAILABLE",
                    "Quality preview day is not in the checked running calendar.")
            val (week, baseSession) = pair
            if (week.recoveryWeek)
                return defer("QUALITY_RECOVERY_WEEK_HOLD",
                    "This research candidate holds all quality previews during recovery weeks.")
            if (week.sessions.size < 3)
                return defer("QUALITY_TWO_DAY_WEEK_UNREVIEWED",
                    "A two-day running week requires a separate quality scheduling review.")
            if (baseSession.kind == IndependentRunSessionKind.LONG)
                return defer("QUALITY_LONG_RUN_DAY_CONFLICT",
                    "This minimal model keeps quality off the designated long-run day.")
            if (!request.explicitlyReviewedDose ||
                request.reviewReference.isNullOrBlank())
                return defer("QUALITY_DOSE_REVIEW_MISSING",
                    "Externally reviewed work-dose evidence must be explicitly recorded.")
            val selected = IndependentQualityFitnessAdapter.generate(
                request.family, request.input
            )
            when (selected) {
                is IndependentQualityFitnessDecision.Deferred ->
                    return defer("QUALITY_" + selected.ruleId, selected.explanation)
                is IndependentQualityFitnessDecision.Candidate ->
                    quality += IndependentQualityDayPreview(
                        week = request.week,
                        isoDay = request.isoDay,
                        baseAllocatedKilometers = baseSession.allocatedKilometers,
                        family = selected.family,
                        qualityWorkout = selected.workout,
                        totalSeconds = selected.totalSeconds,
                        workSeconds = selected.workSeconds,
                        ruleIds = selected.ruleIds + listOf(
                            "EXTERNAL_DOSE_REVIEW_CALLER_ASSERTED",
                            "QUALITY_KILOMETERS_UNRECONCILED",
                            "NOT_EXECUTABLE"
                        )
                    )
            }
        }

        val strides = mutableListOf<IndependentStridesDayPreview>()
        for (request in stridesRequests.sortedWith(compareBy({ it.week }, { it.isoDay }))) {
            val pair = eligibleDay(request.week, request.isoDay)
                ?: return defer("STRIDES_DAY_UNAVAILABLE",
                    "Strides preview day is not in the checked running calendar.")
            val (week, baseSession) = pair
            if (week.recoveryWeek)
                return defer("STRIDES_RECOVERY_WEEK_HOLD",
                    "No automatic strides are added in a recovery week.")
            if (baseSession.kind == IndependentRunSessionKind.LONG)
                return defer("STRIDES_LONG_RUN_DAY_CONFLICT",
                    "This research model does not layer strides onto a long run.")
            if (quality.any { it.week == request.week && it.isoDay == request.isoDay })
                return defer("STRIDES_QUALITY_DAY_CONFLICT",
                    "Do not stack separate quality and strides candidates on one day.")
            if (!request.explicitlyReviewed || request.reviewReference.isNullOrBlank())
                return defer("STRIDES_REVIEW_MISSING",
                    "Strides require explicit scheduling and dose review.")

            val interval = IndependentIntervalGroup(
                repetitions = request.repetitions,
                work = WorkoutStep(
                    purpose = WorkoutPurpose.WORK,
                    unit = WorkoutUnit.SECONDS,
                    amount = request.workSeconds.toDouble(),
                    repetitions = 1,
                    target = WorkoutTarget.STRIDES
                ),
                recovery = WorkoutStep(
                    purpose = WorkoutPurpose.RECOVERY,
                    unit = WorkoutUnit.SECONDS,
                    amount = request.recoverySeconds.toDouble(),
                    repetitions = 1,
                    target = WorkoutTarget.EASY
                )
            )
            val preview = IndependentWorkoutComposition(
                listOf(IndependentWorkoutElement.Intervals(interval))
            )
            strides += IndependentStridesDayPreview(
                week = request.week,
                isoDay = request.isoDay,
                baseAllocatedKilometers = baseSession.allocatedKilometers,
                stridesWorkout = preview,
                workSeconds = request.workSeconds * request.repetitions,
                totalSeconds = requireNotNull(preview.total(WorkoutUnit.SECONDS)).toInt()
            )
        }
        return IndependentQualityOverlayDecision.Candidate(
            baseWeeks = base.weeks,
            qualityPreviews = quality,
            stridesPreviews = strides,
            ruleIds = listOf(
                "M4_AND_M5A_BASE_FEASIBILITY_RECHECKED",
                "EXPLICIT_EXTERNAL_REVIEW_ASSERTED",
                "RECOVERY_AND_LONG_DAY_GUARDS",
                "NO_KM_CONVERSION_FROM_TIMED_SEGMENTS",
                "NO_PRODUCTION_PLAN_MUTATION"
            )
        )
    }
}
