package io.stridelab.privatealpha

import kotlin.math.abs

/**
 * M5C arithmetic-only asserted distances for the timed M5B preview.
 * The review reference is a caller assertion, NOT authenticated FIT/Garmin
 * distance, quality classification, pace evidence, or a coaching approval.
 */
internal enum class Engine15M5DistanceClaimProvenance {
    EXTERNAL_REVIEW_REFERENCE_ASSERTED,
    USER_ASSERTED_EQUIVALENT
}

internal data class Engine15M5SegmentDistanceClaim(
    val weekNumber: Int,
    val slotNumber: Int,
    val assertedMetersByTimedSegment: List<Double>,
    val externalReviewReference: String?,
    val provenance: Engine15M5DistanceClaimProvenance =
        Engine15M5DistanceClaimProvenance.EXTERNAL_REVIEW_REFERENCE_ASSERTED
) {
    init {
        require(weekNumber > 0 && slotNumber > 0)
        require(externalReviewReference == null || externalReviewReference.length <= 256)
        require(assertedMetersByTimedSegment.size <= 400)
        require(provenance != Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT ||
            externalReviewReference == null)
    }

    fun encode() = org.json.JSONObject()
        .put("weekNumber", weekNumber)
        .put("slotNumber", slotNumber)
        .put("assertedMetersByTimedSegment", org.json.JSONArray(assertedMetersByTimedSegment))
        .put("externalReviewReference", externalReviewReference)
        .put("provenance", provenance.name)

    companion object {
        fun decode(json: org.json.JSONObject) = Engine15M5SegmentDistanceClaim(
            weekNumber = json.getInt("weekNumber"),
            slotNumber = json.getInt("slotNumber"),
            assertedMetersByTimedSegment = json.getJSONArray("assertedMetersByTimedSegment").let { values ->
                (0 until values.length()).map { values.getDouble(it) }
            },
            externalReviewReference = json.optString("externalReviewReference").takeIf { it.isNotBlank() },
            provenance = json.optString("provenance",
                Engine15M5DistanceClaimProvenance.EXTERNAL_REVIEW_REFERENCE_ASSERTED.name).let {
                Engine15M5DistanceClaimProvenance.valueOf(it)
            }
        )
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
    val segments: List<Engine15M5TimedDistanceSegment>,
    val distanceClaimProvenance: Engine15M5DistanceClaimProvenance =
        Engine15M5DistanceClaimProvenance.EXTERNAL_REVIEW_REFERENCE_ASSERTED
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

internal sealed interface Engine15M5SegmentDistanceDecision {
    data class Deferred(val reasonCode: String, val explanation: String) :
        Engine15M5SegmentDistanceDecision

    data class ArithmeticOnly(val match: Engine15M5ArithmeticMatch) :
        Engine15M5SegmentDistanceDecision
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

    /**
     * Reuses M5C's per-segment arithmetic for an existing timed structure and
     * its unchanged easy-slot budget. A user assertion remains unauthenticated.
     */
    fun reconcileSegmentClaim(
        weekNumber: Int,
        slotNumber: Int,
        family: IndependentQualityFamily,
        originalEasySlotKm: Double,
        workout: IndependentWorkoutComposition,
        totalSeconds: Int,
        workSeconds: Int,
        claim: Engine15M5SegmentDistanceClaim
    ): Engine15M5SegmentDistanceDecision {
        fun defer(code: String, reason: String) = Engine15M5SegmentDistanceDecision.Deferred(code, reason)
        if (weekNumber != claim.weekNumber || slotNumber != claim.slotNumber)
            return defer("CLAIM_SLOT_MISMATCH", "The segment-distance claim does not match this week and run slot.")
        if (!originalEasySlotKm.isFinite() || originalEasySlotKm <= 0.0)
            return defer("INVALID_EASY_SLOT_BUDGET", "The original easy-run slot has no valid distance budget.")
        if (claim.provenance == Engine15M5DistanceClaimProvenance.EXTERNAL_REVIEW_REFERENCE_ASSERTED &&
            claim.externalReviewReference.isNullOrBlank())
            return defer("SEGMENT_DISTANCE_REVIEW_MISSING",
                "An external review reference is required for this claim type.")
        val phases = flatten(workout)
            ?: return defer("INVALID_TIMED_COMPOSITION", "Every workout element must be a bounded timed step.")
        if (phases.size < 3 || phases.first().purpose != WorkoutPurpose.WARM_UP ||
            phases.first().target != WorkoutTarget.EASY ||
            phases.last().purpose != WorkoutPurpose.COOL_DOWN ||
            phases.last().target != WorkoutTarget.EASY)
            return defer("INVALID_TIMED_PHASE_SEQUENCE", "The timed quality structure needs easy warm-up and cooldown phases.")
        val expectedTarget = when (family) {
            IndependentQualityFamily.THRESHOLD -> WorkoutTarget.THRESHOLD
            IndependentQualityFamily.TEMPO -> WorkoutTarget.TEMPO
            IndependentQualityFamily.INTERVALS -> WorkoutTarget.INTERVALS
            IndependentQualityFamily.HILLS -> WorkoutTarget.HILLS
            IndependentQualityFamily.FARTLEK -> WorkoutTarget.FARTLEK
            IndependentQualityFamily.PROGRESSION -> WorkoutTarget.PROGRESSION
        }
        val interior = phases.drop(1).dropLast(1)
        if (interior.none { it.purpose == WorkoutPurpose.WORK } || interior.any {
                when (it.purpose) {
                    WorkoutPurpose.WORK -> it.target != expectedTarget
                    WorkoutPurpose.RECOVERY -> it.target != WorkoutTarget.EASY
                    else -> true
                }
            })
            return defer("QUALITY_PHASE_STRUCTURE_MISMATCH",
                "Work steps must match the selected family and recoveries must be easy.")
        if (kotlin.math.abs(phases.sumOf { it.seconds } - totalSeconds) > 1e-7 ||
            kotlin.math.abs(phases.filter { it.purpose == WorkoutPurpose.WORK }
                .sumOf { it.seconds } - workSeconds) > 1e-7)
            return defer("TIMED_DOSE_MISMATCH", "Expanded steps do not match the supplied structure totals.")
        if (claim.assertedMetersByTimedSegment.size != phases.size)
            return defer("SEGMENT_COUNT_MISMATCH", "Provide one distance equivalent for every expanded timed segment.")
        if (claim.assertedMetersByTimedSegment.any { !it.isFinite() || it <= 0.0 })
            return defer("INVALID_ASSERTED_METERS", "Segment distance equivalents must be finite and positive.")
        val sumMeters = claim.assertedMetersByTimedSegment.sum()
        if (!sumMeters.isFinite() || kotlin.math.abs(sumMeters - originalEasySlotKm * 1000.0) > 0.01)
            return defer("SESSION_DISTANCE_SUM_MISMATCH",
                "Segment distance equivalents must sum to the unchanged easy-run distance budget.")
        return Engine15M5SegmentDistanceDecision.ArithmeticOnly(
            Engine15M5ArithmeticMatch(
                weekNumber = weekNumber,
                slotNumber = slotNumber,
                family = family,
                originalEasySlotKm = originalEasySlotKm,
                sumAssertedMeters = sumMeters,
                workSeconds = workSeconds,
                totalSeconds = totalSeconds,
                segments = phases.zip(claim.assertedMetersByTimedSegment).map { (phase, meters) ->
                    Engine15M5TimedDistanceSegment(phase.purpose, phase.target, phase.seconds, meters)
                },
                distanceClaimProvenance = claim.provenance
            )
        )
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
            if (claim.provenance != Engine15M5DistanceClaimProvenance.EXTERNAL_REVIEW_REFERENCE_ASSERTED)
                return defer("SEGMENT_DISTANCE_REVIEW_MISSING",
                    "M5C reviewed previews require a caller-asserted external review reference.",
                    preview.weekNumber)
            when (val segment = reconcileSegmentClaim(
                preview.weekNumber, preview.slotNumber, preview.family,
                preview.originalEasyDistanceKm, preview.workout, preview.totalSeconds,
                preview.workSeconds, claim
            )) {
                is Engine15M5SegmentDistanceDecision.Deferred ->
                    return defer(segment.reasonCode, segment.explanation, preview.weekNumber)
                is Engine15M5SegmentDistanceDecision.ArithmeticOnly -> matches += segment.match
            }
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

/** Compare implied averages only. The margin allows small assertion errors without setting a pace target. */
internal fun Engine15M5ArithmeticMatch.qualityPaceWarning(): String? {
    val familyName = family.name.lowercase().replaceFirstChar(Char::uppercase)
    val work = segments.filter { it.purpose == WorkoutPurpose.WORK }
    fun Engine15M5TimedDistanceSegment.pace() = seconds * 1000 / assertedMeters
    val slowestWork = work.maxOf { it.pace() }
    return segments.filter { it.target == WorkoutTarget.EASY && it.pace() * 1.05 < slowestWork }
        .joinToString(" ") { easy ->
            val phase = easy.purpose.name.lowercase().replace('_', ' ')
            "The asserted $phase implies ${formatTempoImpliedPace(easy.pace())}, faster than $familyName work at ${formatTempoImpliedPace(slowestWork)}. Check the metres for both steps."
        }.ifEmpty { null }
}

internal fun formatTempoImpliedPace(secondsPerKm: Double) =
    "%.2f min/km".format(java.util.Locale.US, secondsPerKm / 60)
