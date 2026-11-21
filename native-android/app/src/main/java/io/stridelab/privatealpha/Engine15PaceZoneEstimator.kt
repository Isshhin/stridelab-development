package io.stridelab.privatealpha

import java.time.Instant
import java.time.temporal.ChronoUnit
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
 * Auditable provisional pace-band estimator and automatic segment-distance
 * reconciler for timed Engine 1.5 quality workouts.
 *
 * Uses a 3-tier cascade:
 * 1. Explicit user benchmark (5K/10K/21K/42K race or time trial).
 * 2. Real historical running data (blending the athlete's strongest Riegel-normalized
 *    sustained effort and observed aerobic/easy run pace distribution).
 * 3. Fallback to request.planningPace when no history exists.
 */
internal object Engine15PaceZoneEstimator {
    const val POLICY_ID = "stridelab.engine15.provisional-pace-zones-v1"
    private const val RIEGEL_EXPONENT = 1.06

    private data class HistoricalRunSample(
        val startedAt: Instant,
        val distanceMeters: Double,
        val paceSecondsPerKm: Double,
        val averageHr: Double?
    )

    private fun extractRecentRunSamples(
        activities: List<ActivityRecord>,
        minDistanceMeters: Double
    ): List<HistoricalRunSample> {
        val all = ActivityDisplayConsolidation.visible(activities)
            .filter { DashboardSummary.isRun(it) }
            .mapNotNull { run ->
                val distM = run.distanceM?.takeIf { it.isFinite() && it >= minDistanceMeters } ?: return@mapNotNull null
                val rawPace = (run.recordedPace
                    ?: run.garminMovingPaceSPerKm
                    ?: run.durationS?.takeIf { it.isFinite() && it > 0.0 }?.let { it * 1000.0 / distM })
                    ?.takeIf { it.isFinite() && it in 165.0..750.0 } ?: return@mapNotNull null
                HistoricalRunSample(run.startedAt, distM, rawPace, run.averageHr)
            }
            .sortedByDescending { it.startedAt }
        if (all.isEmpty()) return emptyList()
        val latest = all.first().startedAt
        val cutoff56 = latest.minus(56, ChronoUnit.DAYS)
        val in56Days = all.filter { !it.startedAt.isBefore(cutoff56) }
        if (in56Days.size >= 3) return in56Days
        val cutoff90 = latest.minus(90, ChronoUnit.DAYS)
        val in90Days = all.filter { !it.startedAt.isBefore(cutoff90) }
        return if (in90Days.size >= 3) in90Days else all.take(12)
    }

    private fun recencyWeightedEasyAnchor(samples: List<HistoricalRunSample>): Double? {
        if (samples.isEmpty()) return null
        val latest = samples.first().startedAt
        val weighted = buildList {
            for (sample in samples) {
                val ageDays = ChronoUnit.DAYS.between(sample.startedAt, latest).coerceAtLeast(0L)
                // Weight recent 21d at 3x (1.5x equiv), 22-42d at 2x (1.2x equiv), >42d at 1x
                val copies = when {
                    ageDays <= 21 -> 3
                    ageDays <= 42 -> 2
                    else -> 1
                }
                repeat(copies) { add(sample.paceSecondsPerKm) }
            }
        }.sorted()
        val idx = ((weighted.size - 1) * 0.60).roundToInt().coerceIn(0, weighted.lastIndex)
        return weighted[idx].coerceIn(190.0, 720.0)
    }

    /**
     * Infers the athlete's real aerobic easy planning pace (seconds/km) from recent recorded running history.
     */
    fun inferHistoricalEasyPaceSecondsPerKm(
        activities: List<ActivityRecord>,
        fallbackSecondsPerKm: Double = 360.0
    ): Double {
        val recent = extractRecentRunSamples(activities, minDistanceMeters = 2_000.0)
        return recencyWeightedEasyAnchor(recent)
            ?: fallbackSecondsPerKm.takeIf { it.isFinite() && it in 180.0..900.0 }
            ?: 360.0
    }

    fun estimate(
        request: PlanRequest,
        effortBaseline: Engine15AthleteEffortBaseline? = null,
        activities: List<ActivityRecord> = emptyList()
    ): Engine15ProvisionalPaceZones {
        val benchDist = request.benchmarkDistanceMeters
        val benchSec = request.benchmarkDurationSeconds
        if (benchDist != null && benchSec != null && benchDist in 1000..100000 && benchSec in 120..43200) {
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

        // Recent historical activity analysis (prioritizes last 56 days relative to latest run)
        val recentRuns = extractRecentRunSamples(activities, minDistanceMeters = 2_500.0)
        val easyAnchorPace = recencyWeightedEasyAnchor(recentRuns)

        if (recentRuns.isNotEmpty() && easyAnchorPace != null) {
            val thresholdFromEasy = (easyAnchorPace / 1.22).coerceIn(175.0, 620.0)

            // Sustained recent efforts >= 4.0 km normalized to 10K equivalent via Riegel
            val sustainedEfforts = recentRuns
                .filter { it.distanceMeters >= 4_000.0 }
                .map { sample ->
                    val durationSec = sample.paceSecondsPerKm * (sample.distanceMeters / 1000.0)
                    val eq10kSec = durationSec * (10_000.0 / sample.distanceMeters).pow(RIEGEL_EXPONENT)
                    (eq10kSec / 10.0).coerceIn(170.0, 620.0)
                }
                .sorted()

            val recentSustainedThreshold = when {
                sustainedEfforts.size >= 4 -> sustainedEfforts[1] // 2nd-best protects against GPS spikes/outliers
                sustainedEfforts.size >= 2 -> sustainedEfforts.take(2).average()
                sustainedEfforts.size == 1 -> sustainedEfforts.first()
                else -> null
            }

            val thresholdPace = if (recentSustainedThreshold != null) {
                // 50/50 blend between recent sustained effort anchor and recent aerobic easy anchor
                (0.50 * minOf(recentSustainedThreshold, thresholdFromEasy) + 0.50 * thresholdFromEasy)
                    .coerceIn(175.0, 620.0)
            } else {
                thresholdFromEasy
            }
            val fiveKPace = (thresholdPace * 0.945).coerceIn(165.0, 600.0)
            return buildFromThresholdAndFiveK(
                basis = Engine15PaceBandBasis.CONFIRMED_EASY_BASELINE_ESTIMATE,
                thresholdPace = thresholdPace,
                fiveKPace = fiveKPace,
                observedEasyAnchor = easyAnchorPace
            )
        }

        val confirmedEasy = effortBaseline?.easyEffort?.medianPaceSecondsPerKm
        if (confirmedEasy != null && confirmedEasy.isFinite() && confirmedEasy in 200.0..900.0) {
            val thresholdPace = (confirmedEasy / 1.22).coerceIn(180.0, 650.0)
            val fiveKPace = (thresholdPace * 0.945).coerceIn(170.0, 620.0)
            return buildFromThresholdAndFiveK(
                basis = Engine15PaceBandBasis.CONFIRMED_EASY_BASELINE_ESTIMATE,
                thresholdPace = thresholdPace,
                fiveKPace = fiveKPace,
                observedEasyAnchor = confirmedEasy
            )
        }

        val planningEasy = request.planningPace.takeIf { it.isFinite() && it in 180.0..900.0 } ?: 360.0
        val thresholdPace = (planningEasy / 1.21).coerceIn(180.0, 650.0)
        val fiveKPace = (thresholdPace * 0.945).coerceIn(170.0, 620.0)
        return buildFromThresholdAndFiveK(
            basis = Engine15PaceBandBasis.PLANNING_PACE_ESTIMATE,
            thresholdPace = thresholdPace,
            fiveKPace = fiveKPace
        )
    }

    private fun buildFromThresholdAndFiveK(
        basis: Engine15PaceBandBasis,
        thresholdPace: Double,
        fiveKPace: Double,
        observedEasyAnchor: Double? = null
    ): Engine15ProvisionalPaceZones {
        val easyFast = if (observedEasyAnchor != null) {
            minOf(thresholdPace * 1.18, observedEasyAnchor * 0.96).coerceAtLeast(thresholdPace * 1.13)
        } else thresholdPace * 1.19
        val easySlow = if (observedEasyAnchor != null) {
            maxOf(easyFast + 22.0, observedEasyAnchor * 1.05).coerceAtMost(thresholdPace * 1.35)
        } else thresholdPace * 1.31
        return Engine15ProvisionalPaceZones(
            policyId = POLICY_ID,
            basis = basis,
            easy = Engine15PaceRange(easyFast, easySlow),
            steady = Engine15PaceRange(thresholdPace * 1.09, thresholdPace * 1.16),
            tempo = Engine15PaceRange(thresholdPace * 1.02, thresholdPace * 1.07),
            threshold = Engine15PaceRange(thresholdPace * 0.98, thresholdPace * 1.02),
            intervals = Engine15PaceRange(fiveKPace * 0.95, fiveKPace * 1.00)
        )
    }

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
