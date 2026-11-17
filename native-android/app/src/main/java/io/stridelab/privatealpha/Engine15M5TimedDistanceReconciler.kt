package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M5C arithmetic-only asserted distances for the timed M5B preview.
 * The review reference is a caller assertion, NOT authenticated FIT/Garmin
 * distance, quality classification, pace evidence, or a coaching approval.
 */
internal data class Engine15M5SegmentDistanceClaim(
    val weekNumber: Int,
    val slotNumber: Int,
    val assertedMetersByTimedSegment: List<Double>,
    val externalReviewReference: String?
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(externalReviewReference == null || externalReviewReference.length <= 256)
    }
}

internal data class Engine15M5TimedDistanceSegment(
    val purpose: WorkoutPurpose,
    val target: WorkoutTarget,
    val seconds: Double,
    val assertedMeters: Double
) {
    init {
        require(seconds.isFinite() && seconds > 0)
        require(assertedMeters.isFinite() && assertedMeters > 0)
    }
}

internal data class Engine15M5ArithmeticMatch(
    val weekNumber: Int,
    val slotNumber: Int,
    val family: IndependentQualityFamily,
    val originalEasySlotKm: Double,
    val sumAssertedMeters: Double,
    val workSeconds: Int,
    val totalSeconds: Int,
    val segments: List<Engine15M5TimedDistanceSegment>
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(originalEasySlotKm.isFinite() && originalEasySlotKm > 0)
        require(sumAssertedMeters.isFinite() && sumAssertedMeters > 0)
        require(segments.isNotEmpty())
        require(abs(sumAssertedMeters - originalEasySlotKm * 1000.0) <= 0.01)
        require(abs(segments.sumOf { it.assertedMeters } - sumAssertedMeters) <= 1e-7)
        require(abs(segments.sumOf { it.seconds } - totalSeconds) <= 1e-7)
        require(abs(segments.filter { it.purpose == WorkoutPurpose.WORK }
            .sumOf { it.seconds } - workSeconds) <= 1e-7)
    }
    val sourceDistancesVerified: Boolean get() = false
    val qualityClassificationVerified: Boolean get() = false
    val executable: Boolean get() = false
}

internal sealed interface Engine15M5DistanceDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M5DistanceDecision

    data class ArithmeticOnly(
        val policyId: String,
        val originalBaseWeeks: List<Engine15M5WeekPreview>,
        val originalQualityPreviews: List<Engine15M5QualitySlotPreview>,
        val matches: List<Engine15M5ArithmeticMatch>,
        val ruleIds: List<String>,
        val sourceDistancesVerified: Boolean = false,
        val qualityClassificationVerified: Boolean = false,
        val scheduled: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M5DistanceDecision {
        init {
            require(originalBaseWeeks.isNotEmpty())
            require(matches.isNotEmpty() && matches.size == originalQualityPreviews.size)
            require(matches.map { it.weekNumber to it.slotNumber }.toSet().size == matches.size)
            require(ruleIds.distinct() == ruleIds)
            require(!sourceDistancesVerified && !qualityClassificationVerified &&
                !scheduled && !executable && !authorizedForProduction)
        }
    }
}

/**
 * Independent M4B -> M5A -> M5B -> M5C arithmetic-only pipeline.
 * No inference from time to distance. Caller must supply each segment metre
 * claim; equality of asserted totals does not authenticate these assertions.
 */
internal object Engine15M5TimedDistanceReconciler {
    const val POLICY_ID = "engine15.m5c.m4b-timed-arithmetic-shadow-20261117.1"
    private const val METER_EPS = 0.01

    private data class TimedPhase(
        val purpose: WorkoutPurpose,
        val target: WorkoutTarget,
        val seconds: Double
    )

    private fun flatten(workout: IndependentWorkoutComposition): List<TimedPhase>? {
        val phases = mutableListOf<TimedPhase>()
        fun add(step: WorkoutStep): Boolean {
            if (step.unit != WorkoutUnit.SECONDS || !step.amount.isFinite() || step.amount <= 0.0)
                return false
            phases += TimedPhase(step.purpose, step.target, step.amount)
            return phases.size <= 400
        }
        for (element in workout.elements) when (element) {
            is IndependentWorkoutElement.Step ->
                repeat(element.value.repetitions) {
                    if (!add(element.value)) return null
                }
            is IndependentWorkoutElement.Intervals -> {
                val group = element.value
                repeat(group.repetitions) { i ->
                    if (!add(group.work)) return null
                    if (i < group.repetitions - 1 && !add(group.recovery)) return null
                }
            }
        }
        return phases
    }

    fun reconcile(
        source: Engine15IndependentVolumePlanner.Result,
        requests: List<Engine15M5QualityPreviewRequest>,
        claims: List<Engine15M5SegmentDistanceClaim>
    ): Engine15M5DistanceDecision {
        fun defer(code: String, explanation: String, week: Int? = null) =
            Engine15M5DistanceDecision.Deferred(code, explanation, week)
        val quality = when (val decided = Engine15M5QualityPreviewComposer.compose(source, requests)) {
            is Engine15M5QualityDecision.Deferred ->
                return defer("UPSTREAM_" + decided.reasonCode,
                    decided.explanation, decided.weekNumber)
            is Engine15M5QualityDecision.Candidate -> decided
        }
        if (claims.size != quality.qualityPreviews.size)
            return defer("CLAIM_COVERAGE_MISMATCH",
                "Every independently selected quality preview needs exactly one asserted-distance claim.")
        val keys = claims.map { it.weekNumber to it.slotNumber }
        if (keys.toSet().size != keys.size)
            return defer("DUPLICATE_SEGMENT_CLAIM",
                "A quality slot cannot have competing segment-distance claims.")
        val validKeys = quality.qualityPreviews.map { it.weekNumber to it.slotNumber }.toSet()
        if (keys.toSet() != validKeys)
            return defer("CLAIM_SLOT_MISMATCH",
                "Asserted-distance claims must match exactly the M5B preview slots.")

        val matches = mutableListOf<Engine15M5ArithmeticMatch>()
        for (preview in quality.qualityPreviews) {
            val claim = claims.single {
                it.weekNumber == preview.weekNumber && it.slotNumber == preview.slotNumber
            }
            if (claim.externalReviewReference.isNullOrBlank())
                return defer("SEGMENT_DISTANCE_REVIEW_MISSING",
                    "Explicit external review reference is required for every segment claim.",
                    preview.weekNumber)
            val phases = flatten(preview.workout)
                ?: return defer("INVALID_TIMED_COMPOSITION",
                    "The preview must contain a bounded sequence of timed workout steps.",
                    preview.weekNumber)
            if (phases.size < 3 ||
                phases.first().purpose != WorkoutPurpose.WARM_UP ||
                phases.first().target != WorkoutTarget.EASY ||
                phases.last().purpose != WorkoutPurpose.COOL_DOWN ||
                phases.last().target != WorkoutTarget.EASY)
                return defer("INVALID_TIMED_PHASE_SEQUENCE",
                    "A quality session must have warm-up, work, and cool-down phases.",
                    preview.weekNumber)
            val expectedTarget = when (preview.family) {
                IndependentQualityFamily.THRESHOLD -> WorkoutTarget.THRESHOLD
                IndependentQualityFamily.TEMPO -> WorkoutTarget.TEMPO
            }
            val interior = phases.drop(1).dropLast(1)
            if (interior.none { it.purpose == WorkoutPurpose.WORK } ||
                interior.any {
                    when (it.purpose) {
                        WorkoutPurpose.WORK -> it.target != expectedTarget
                        WorkoutPurpose.RECOVERY -> it.target != WorkoutTarget.EASY
                        else -> true
                    }
                })
                return defer("QUALITY_PHASE_STRUCTURE_MISMATCH",
                    "All work phases must match the selected family; recoveries are easy.",
                    preview.weekNumber)
            if (abs(phases.sumOf { it.seconds } - preview.totalSeconds) > 1e-7 ||
                abs(phases.filter { it.purpose == WorkoutPurpose.WORK }
                    .sumOf { it.seconds } - preview.workSeconds) > 1e-7)
                return defer("TIMED_DOSE_MISMATCH",
                    "Expanded timed segments disagree with the reviewed M5B dose.",
                    preview.weekNumber)
            if (claim.assertedMetersByTimedSegment.size != phases.size)
                return defer("SEGMENT_COUNT_MISMATCH",
                    "Provide one independent meter assertion for each expanded timed segment.",
                    preview.weekNumber)
            if (claim.assertedMetersByTimedSegment.any { !it.isFinite() || it <= 0.0 })
                return defer("INVALID_ASSERTED_METERS",
                    "Nonfinite, missing, zero or negative asserted segment distances are invalid.",
                    preview.weekNumber)
            val sessionMeters = preview.originalEasyDistanceKm * 1000.0
            val sumMeters = claim.assertedMetersByTimedSegment.sum()
            if (!sumMeters.isFinite() || abs(sumMeters - sessionMeters) > METER_EPS)
                return defer("SESSION_DISTANCE_SUM_MISMATCH",
                    "Asserted segment metres do not sum to the unchanged M5A easy slot.",
                    preview.weekNumber)
            matches += Engine15M5ArithmeticMatch(
                weekNumber = preview.weekNumber,
                slotNumber = preview.slotNumber,
                family = preview.family,
                originalEasySlotKm = preview.originalEasyDistanceKm,
                sumAssertedMeters = sumMeters,
                workSeconds = preview.workSeconds,
                totalSeconds = preview.totalSeconds,
                segments = phases.zip(claim.assertedMetersByTimedSegment).map { (phase, meters) ->
                    Engine15M5TimedDistanceSegment(
                        phase.purpose, phase.target, phase.seconds, meters
                    )
                }
            )
        }
        // Output includes untouched M5B and M5A contracts. This is not a new
        // distance-prescribed quality session or an additional weekly workout.
        return Engine15M5DistanceDecision.ArithmeticOnly(
            policyId = POLICY_ID,
            originalBaseWeeks = quality.baseWeeks,
            originalQualityPreviews = quality.qualityPreviews,
            matches = matches,
            ruleIds = listOf(
                "M4B_M5A_M5B_RECOMPUTED",
                "EVERY_TIMED_SEGMENT_HAS_ASSERTED_METERS",
                "INTERVAL_RECOVERY_BETWEEN_REPS_ONLY",
                "UNCHANGED_M5A_SLOT_AND_WEEK_DISTANCE",
                "ASSERTED_ARITHMETIC_NOT_SOURCE_AUTHENTICATION",
                "NO_VERIFIED_PACE_INTENSITY_SCHEDULING_OR_EXECUTION"
            )
        )
    }
}
