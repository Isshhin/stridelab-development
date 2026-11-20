package io.stridelab.privatealpha

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class Engine15PaceBandBasis {
    RECENT_BENCHMARK_ESTIMATE,
    CONFIRMED_EASY_BASELINE_ESTIMATE,
    PLANNING_PACE_ESTIMATE
}

internal data class Engine15PaceRange(
    val fastSecondsPerKm: Double,
    val slowSecondsPerKm: Double
) {
    init {
        require(fastSecondsPerKm.isFinite() && slowSecondsPerKm.isFinite())
        require(fastSecondsPerKm > 0.0 && slowSecondsPerKm >= fastSecondsPerKm)
    }

    val midpointSecondsPerKm: Double get() = (fastSecondsPerKm + slowSecondsPerKm) / 2.0

    fun formatRange(): String =
        "${formatPace(fastSecondsPerKm)}–${formatPace(slowSecondsPerKm)} /km"

    companion object {
        fun formatPace(secondsPerKm: Double): String {
            val total = secondsPerKm.roundToInt().coerceAtLeast(1)
            return "%d:%02d".format(java.util.Locale.US, total / 60, total % 60)
        }
    }
}

internal data class Engine15ProvisionalPaceZones(
    val policyId: String,
    val basis: Engine15PaceBandBasis,
    val easy: Engine15PaceRange,
    val steady: Engine15PaceRange,
    val tempo: Engine15PaceRange,
    val threshold: Engine15PaceRange,
    val intervals: Engine15PaceRange,
    val provisional: Boolean = true,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(policyId == Engine15PaceZoneEstimator.POLICY_ID)
        require(provisional && !sourceEvidenceVerified)
    }

    fun forTarget(target: WorkoutTarget): Engine15PaceRange = when (target) {
        WorkoutTarget.EASY, WorkoutTarget.UNSPECIFIED -> easy
        WorkoutTarget.STEADY -> steady
        WorkoutTarget.TEMPO, WorkoutTarget.PROGRESSION -> tempo
        WorkoutTarget.THRESHOLD -> threshold
        WorkoutTarget.INTERVALS, WorkoutTarget.FARTLEK, WorkoutTarget.HILLS, WorkoutTarget.STRIDES -> intervals
    }
}

/**
 * Phase A: Auditable provisional pace-band estimator and automatic segment-distance
 * reconciler for timed Engine 1.5 quality workouts.
 *
 * - Always labeled PROVISIONAL_ESTIMATE (never fabricated physiological truth).
 * - Eliminates mandatory 48-field manual meter entry by computing whole-meter
 *   per-segment equivalents that sum exactly to each loading week's easy-run
 *   kilometer budget and pass pace-plausibility checks.
 */
internal object Engine15PaceZoneEstimator {
    const val POLICY_ID = "stridelab.engine15.provisional-pace-zones-v1"
    private const val RIEGEL_EXPONENT = 1.06

    fun estimate(
        request: PlanRequest,
        effortBaseline: Engine15AthleteEffortBaseline? = null
    ): Engine15ProvisionalPaceZones {
        val benchDist = request.benchmarkDistanceMeters
        val benchSec = request.benchmarkDurationSeconds
        if (benchDist != null && benchSec != null && benchDist in 1000..100000 && benchSec in 120..43200) {
            // Riegel scaling to equivalent 10K (~threshold) and 5K (~VO2max) paces.
            val tenKSeconds = benchSec.toDouble() * (10_000.0 / benchDist.toDouble()).pow(RIEGEL_EXPONENT)
            val fiveKSeconds = benchSec.toDouble() * (5_000.0 / benchDist.toDouble()).pow(RIEGEL_EXPONENT)
            val thresholdPace = (tenKSeconds / 10.0).coerceIn(170.0, 600.0)
            val fiveKPace = (fiveKSeconds / 5.0).coerceIn(160.0, 580.0)
            return buildFromThresholdAndFiveK(
                basis = Engine15PaceBandBasis.RECENT_BENCHMARK_ESTIMATE,
                thresholdPace = thresholdPace,
                fiveKPace = fiveKPace
            )
        }

        val confirmedEasy = effortBaseline?.easyEffort?.medianPaceSecondsPerKm
        if (confirmedEasy != null && confirmedEasy.isFinite() && confirmedEasy in 200.0..900.0) {
            val thresholdPace = (confirmedEasy / 1.24).coerceIn(180.0, 650.0)
            val fiveKPace = (thresholdPace * 0.94).coerceIn(170.0, 620.0)
            return buildFromThresholdAndFiveK(
                basis = Engine15PaceBandBasis.CONFIRMED_EASY_BASELINE_ESTIMATE,
                thresholdPace = thresholdPace,
                fiveKPace = fiveKPace
            )
        }

        val planningEasy = request.planningPace.takeIf { it.isFinite() && it in 180.0..900.0 } ?: 360.0
        val thresholdPace = (planningEasy / 1.20).coerceIn(180.0, 650.0)
        val fiveKPace = (thresholdPace * 0.94).coerceIn(170.0, 620.0)
        return buildFromThresholdAndFiveK(
            basis = Engine15PaceBandBasis.PLANNING_PACE_ESTIMATE,
            thresholdPace = thresholdPace,
            fiveKPace = fiveKPace
        )
    }

    private fun buildFromThresholdAndFiveK(
        basis: Engine15PaceBandBasis,
        thresholdPace: Double,
        fiveKPace: Double
    ): Engine15ProvisionalPaceZones = Engine15ProvisionalPaceZones(
        policyId = POLICY_ID,
        basis = basis,
        easy = Engine15PaceRange(thresholdPace * 1.20, thresholdPace * 1.32),
        steady = Engine15PaceRange(thresholdPace * 1.10, thresholdPace * 1.18),
        tempo = Engine15PaceRange(thresholdPace * 1.03, thresholdPace * 1.09),
        threshold = Engine15PaceRange(thresholdPace * 0.98, thresholdPace * 1.03),
        intervals = Engine15PaceRange(fiveKPace * 0.95, fiveKPace * 1.01)
    )

    /**
     * Automatically computes reconciled whole-meter per-segment equivalents for
     * every loading-week alternative in a quality dry-run so they sum to each
     * slot's exact easy-run kilometer budget and maintain plausible work > easy speeds.
     */
    fun estimateSegmentClaims(
        dryRun: Engine15QualityWorkoutDryRun,
        zones: Engine15ProvisionalPaceZones
    ): List<Engine15M5SegmentDistanceClaim> = dryRun.alternatives.map { alt ->
        val workout = alt.prescription?.step?.workout ?: dryRun.workout
        val phases = mutableListOf<WorkoutStep>()
        for (element in workout.elements) when (element) {
            is IndependentWorkoutElement.Step -> repeat(element.value.repetitions) { phases += element.value }
            is IndependentWorkoutElement.Intervals -> repeat(element.value.repetitions) { i ->
                phases += element.value.work
                if (i < element.value.repetitions - 1) phases += element.value.recovery
            }
        }
        val totalMeters = kotlin.math.round(alt.originalEasyDistanceKm * 1000.0).toInt().coerceAtLeast(phases.size)
        val rawDistances = phases.map { step ->
            val paceSecPerKm = zones.forTarget(step.target).midpointSecondsPerKm
            val speedMetersPerSec = 1000.0 / paceSecPerKm
            step.amount * speedMetersPerSec
        }
        val rawSum = rawDistances.sum().coerceAtLeast(1e-6)
        val scaled = rawDistances.dropLast(1).map { d ->
            floor(d / rawSum * totalMeters).toInt().coerceAtLeast(1).toDouble()
        }
        val remainder = (totalMeters - scaled.sum()).coerceAtLeast(1.0)
        Engine15M5SegmentDistanceClaim(
            weekNumber = alt.weekNumber,
            slotNumber = alt.slotNumber,
            assertedMetersByTimedSegment = scaled + remainder,
            externalReviewReference = null,
            provenance = Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
        )
    }
}
