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
    val recovery: Engine15PaceRange = Engine15PaceRange(easy.slowSecondsPerKm + 15.0, easy.slowSecondsPerKm + 30.0),
    val halfMarathon: Engine15PaceRange = Engine15PaceRange(threshold.midpointSecondsPerKm * 1.03, threshold.midpointSecondsPerKm * 1.07),
    val marathon: Engine15PaceRange = Engine15PaceRange(threshold.midpointSecondsPerKm * 1.09, threshold.midpointSecondsPerKm * 1.15),
    val fastIntervals: Engine15PaceRange = Engine15PaceRange(intervals.fastSecondsPerKm * 0.96, intervals.fastSecondsPerKm * 1.01),
    val hillShort: Engine15PaceRange = Engine15PaceRange(
        maxOf(tempo.fastSecondsPerKm + 12.0, intervals.slowSecondsPerKm + 15.0),
        maxOf(tempo.fastSecondsPerKm + 20.0, intervals.slowSecondsPerKm + 25.0, steady.slowSecondsPerKm + 15.0)
    ),
    val hillLong: Engine15PaceRange = Engine15PaceRange(
        maxOf(steady.fastSecondsPerKm + 12.0, intervals.slowSecondsPerKm + 20.0),
        maxOf(steady.fastSecondsPerKm + 20.0, intervals.slowSecondsPerKm + 30.0, easy.slowSecondsPerKm + 5.0)
    ),
    val provisional: Boolean = true,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(policyId == Engine15PaceZoneEstimator.POLICY_ID)
        require(provisional && !sourceEvidenceVerified)
    }

    fun forTarget(target: WorkoutTarget, archetype: Engine15WorkoutArchetype? = null): Engine15PaceRange = when (target) {
        WorkoutTarget.EASY, WorkoutTarget.UNSPECIFIED -> easy
        WorkoutTarget.STEADY -> steady
        WorkoutTarget.TEMPO, WorkoutTarget.PROGRESSION -> tempo
        WorkoutTarget.THRESHOLD -> threshold
        WorkoutTarget.HILLS -> if (archetype == Engine15WorkoutArchetype.LONG_AEROBIC_HILLS) hillLong else hillShort
        WorkoutTarget.STRIDES -> fastIntervals
        WorkoutTarget.INTERVALS -> if (archetype == Engine15WorkoutArchetype.SHORT_TRACK_REPEATS) fastIntervals else intervals
        WorkoutTarget.FARTLEK -> Engine15PaceRange(intervals.fastSecondsPerKm, tempo.slowSecondsPerKm)
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
        val easyRange = Engine15PaceRange(easyFast, easySlow)
        val steadyRange = Engine15PaceRange(thresholdPace * 1.09, thresholdPace * 1.16)
        val tempoRange = Engine15PaceRange(thresholdPace * 1.02, thresholdPace * 1.07)
        val thresholdRange = Engine15PaceRange(thresholdPace * 0.98, thresholdPace * 1.02)
        val intervalsRange = Engine15PaceRange(fiveKPace * 0.95, fiveKPace * 1.00)
        val recoveryFast = (easySlow + 15.0).coerceAtMost(1150.0)
        val recoverySlow = (easySlow + 30.0).coerceAtLeast(recoveryFast).coerceAtMost(1180.0)
        val hillShortFast = maxOf(tempoRange.fastSecondsPerKm + 12.0, intervalsRange.slowSecondsPerKm + 15.0)
        val hillShortSlow = maxOf(hillShortFast + 12.0, steadyRange.slowSecondsPerKm + 15.0)
        val hillLongFast = maxOf(steadyRange.fastSecondsPerKm + 12.0, hillShortFast + 5.0)
        val hillLongSlow = maxOf(hillLongFast + 12.0, easySlow + 5.0)
        return Engine15ProvisionalPaceZones(
            policyId = POLICY_ID,
            basis = basis,
            easy = easyRange,
            steady = steadyRange,
            tempo = tempoRange,
            threshold = thresholdRange,
            intervals = intervalsRange,
            recovery = Engine15PaceRange(recoveryFast, recoverySlow),
            halfMarathon = Engine15PaceRange(thresholdPace * 1.03, thresholdPace * 1.07),
            marathon = Engine15PaceRange(thresholdPace * 1.09, thresholdPace * 1.15),
            fastIntervals = Engine15PaceRange(intervalsRange.fastSecondsPerKm * 0.96, intervalsRange.fastSecondsPerKm * 1.01),
            hillShort = Engine15PaceRange(hillShortFast, hillShortSlow),
            hillLong = Engine15PaceRange(hillLongFast, hillLongSlow)
        )
    }

    fun stepIntentFor(purpose: WorkoutPurpose, family: IndependentQualityFamily?): RunningStepIntent =
        when (purpose) {
            WorkoutPurpose.WARM_UP -> RunningStepIntent.WARMUP
            WorkoutPurpose.COOL_DOWN -> RunningStepIntent.COOLDOWN
            WorkoutPurpose.RECOVERY -> RunningStepIntent.RECOVERY
            WorkoutPurpose.WORK -> when (family) {
                IndependentQualityFamily.TEMPO -> RunningStepIntent.TEMPO
                IndependentQualityFamily.THRESHOLD -> RunningStepIntent.THRESHOLD
                IndependentQualityFamily.INTERVALS -> RunningStepIntent.VO2_INTERVAL
                IndependentQualityFamily.HILLS -> RunningStepIntent.HILL
                IndependentQualityFamily.FARTLEK -> RunningStepIntent.TEMPO
                IndependentQualityFamily.PROGRESSION -> RunningStepIntent.STEADY
                null -> RunningStepIntent.AEROBIC_EASY
            }
        }

    fun paceBoundsForIntent(
        zones: Engine15ProvisionalPaceZones,
        intent: RunningStepIntent,
        goal: String? = null,
        archetype: Engine15WorkoutArchetype? = null
    ): Pair<Int, Int> {
        val range = when (intent) {
            RunningStepIntent.RECOVERY -> zones.recovery
            RunningStepIntent.WARMUP,
            RunningStepIntent.COOLDOWN,
            RunningStepIntent.AEROBIC_EASY -> zones.easy
            RunningStepIntent.STEADY -> zones.steady
            RunningStepIntent.TEMPO -> zones.tempo
            RunningStepIntent.THRESHOLD -> zones.threshold
            RunningStepIntent.VO2_INTERVAL ->
                if (archetype == Engine15WorkoutArchetype.SHORT_TRACK_REPEATS) zones.fastIntervals else zones.intervals
            RunningStepIntent.STRIDE -> zones.fastIntervals
            RunningStepIntent.HILL ->
                if (archetype == Engine15WorkoutArchetype.LONG_AEROBIC_HILLS) zones.hillLong else zones.hillShort
            RunningStepIntent.RACE_PACE -> when (goal) {
                "Marathon" -> zones.marathon
                "Half marathon" -> zones.halfMarathon
                "Improve 10K" -> zones.threshold
                "Improve 5K" -> zones.intervals
                else -> zones.steady
            }
        }
        val minPace = range.fastSecondsPerKm.roundToInt().coerceIn(120, 1200)
        val maxPace = maxOf(minPace, range.slowSecondsPerKm.roundToInt().coerceIn(120, 1200))
        return minPace to maxPace
    }

    fun paceBoundsSecondsPerKm(
        zones: Engine15ProvisionalPaceZones,
        target: WorkoutTarget
    ): Pair<Int, Int> {
        val range = zones.forTarget(target)
        val minPace = range.fastSecondsPerKm.roundToInt().coerceIn(120, 1200)
        val maxPace = maxOf(minPace, range.slowSecondsPerKm.roundToInt().coerceIn(120, 1200))
        return minPace to maxPace
    }

    /**
     * Derives HR target bounds ONLY from an explicit 5-zone athlete profile.
     * Returns strictly null whenever reliable HR zone evidence is unavailable (never invents 220-age).
     */
    fun hrBoundsForIntent(
        sourceZones: ActivitySourceHeartRateZones?,
        intent: RunningStepIntent
    ): Pair<Int, Int>? {
        if (sourceZones == null || sourceZones.highBpms.size < 5) return null
        val highs = sourceZones.highBpms.take(5).sorted()
        val z1High = highs[0]
        val z2High = highs[1]
        val z3High = highs[2]
        val z4High = highs[3]
        val z5High = maxOf(highs[4], sourceZones.maximumHr ?: highs[4])
        val z1Low = (sourceZones.restingHr ?: (z1High - 25)).coerceIn(60, z1High)
        val (rawMin, rawMax) = when (intent) {
            RunningStepIntent.WARMUP,
            RunningStepIntent.COOLDOWN,
            RunningStepIntent.RECOVERY -> z1Low to z2High
            RunningStepIntent.AEROBIC_EASY -> (z1High + 1) to z2High
            RunningStepIntent.STEADY,
            RunningStepIntent.TEMPO -> (z2High + 1) to z3High
            RunningStepIntent.THRESHOLD,
            RunningStepIntent.RACE_PACE -> (z3High + 1) to z4High
            RunningStepIntent.VO2_INTERVAL,
            RunningStepIntent.HILL,
            RunningStepIntent.STRIDE -> (z3High + 1) to z5High
        }
        val minBpm = rawMin.coerceIn(60, 230)
        val maxBpm = rawMax.coerceIn(minBpm, 230)
        return minBpm to maxBpm
    }

    /**
     * Automatically computes reconciled whole-meter per-segment equivalents for
     * every loading-week alternative in a quality dry-run so they sum to each
     * slot's exact easy-run kilometer budget without warping WORK-segment pace
     * whenever warm-up/cool-down headroom can absorb the distance delta.
     */
    fun estimateSegmentClaims(
        dryRun: Engine15QualityWorkoutDryRun,
        zones: Engine15ProvisionalPaceZones
    ): List<Engine15M5SegmentDistanceClaim> = dryRun.alternatives.map { alt ->
        val archetype = alt.prescription?.step?.archetype
        val workout = alt.prescription?.step?.workout ?: dryRun.workout
        val phases = mutableListOf<WorkoutStep>()
        for (element in workout.elements) when (element) {
            is IndependentWorkoutElement.Step -> repeat(element.value.repetitions) { phases += element.value }
            is IndependentWorkoutElement.Intervals -> repeat(element.value.repetitions) { i ->
                phases += element.value.work
                if (i < element.value.repetitions - 1) phases += element.value.recovery
            }
        }
        val pinned = BooleanArray(phases.size) { phases[it].unit == WorkoutUnit.METERS }
        val pinnedSumMeters = phases.indices.sumOf { if (pinned[it]) kotlin.math.round(phases[it].amount).toInt() else 0 }
        val totalMeters = kotlin.math.round(alt.originalEasyDistanceKm * 1000.0).toInt()
            .coerceAtLeast(pinnedSumMeters + phases.indices.count { !pinned[it] })
        val naturalMeters = DoubleArray(phases.size)
        val minMeters = DoubleArray(phases.size)
        val maxMeters = DoubleArray(phases.size)
        phases.forEachIndexed { idx, step ->
            if (pinned[idx]) {
                val exactM = kotlin.math.round(step.amount)
                naturalMeters[idx] = exactM
                minMeters[idx] = exactM
                maxMeters[idx] = exactM
            } else {
                val range = zones.forTarget(step.target, archetype)
                naturalMeters[idx] = step.amount * (1000.0 / range.midpointSecondsPerKm)
                minMeters[idx] = step.amount * (1000.0 / (range.slowSecondsPerKm * 1.12))
                maxMeters[idx] = step.amount * (1000.0 / (range.fastSecondsPerKm * 0.90))
            }
        }

        val allocated = naturalMeters.copyOf()
        var residual = totalMeters.toDouble() - allocated.sum()

        fun absorbInto(indices: List<Int>) {
            if (indices.isEmpty() || kotlin.math.abs(residual) <= 1e-6) return
            if (residual > 0.0) {
                val headroom = indices.associateWith { i -> (maxMeters[i] - allocated[i]).coerceAtLeast(0.0) }
                val totalHeadroom = headroom.values.sum()
                if (totalHeadroom > 1e-6) {
                    val share = minOf(1.0, residual / totalHeadroom)
                    for ((i, room) in headroom) {
                        val add = room * share
                        allocated[i] += add
                        residual -= add
                    }
                }
            } else {
                val slack = indices.associateWith { i -> (allocated[i] - minMeters[i]).coerceAtLeast(0.0) }
                val totalSlack = slack.values.sum()
                if (totalSlack > 1e-6) {
                    val share = minOf(1.0, -residual / totalSlack)
                    for ((i, room) in slack) {
                        val sub = room * share
                        allocated[i] -= sub
                        residual += sub
                    }
                }
            }
        }

        val easyBookendIndices = phases.indices.filter {
            !pinned[it] && (phases[it].purpose == WorkoutPurpose.WARM_UP || phases[it].purpose == WorkoutPurpose.COOL_DOWN)
        }
        val timedCoreIndices = phases.indices.filter { !pinned[it] && it !in easyBookendIndices }

        absorbInto(easyBookendIndices)
        absorbInto(timedCoreIndices)

        if (kotlin.math.abs(residual) > 1e-6) {
            val unpinnedIndices = phases.indices.filter { !pinned[it] }
            val targetUnpinned = (totalMeters - pinnedSumMeters).coerceAtLeast(unpinnedIndices.size).toDouble()
            val sumUnpinned = unpinnedIndices.sumOf { allocated[it] }.coerceAtLeast(1e-6)
            for (i in unpinnedIndices) {
                allocated[i] = (allocated[i] / sumUnpinned) * targetUnpinned
            }
        }

        val rounded = phases.indices.map { i ->
            if (pinned[i]) kotlin.math.round(allocated[i]).toInt().coerceAtLeast(1)
            else allocated[i].roundToInt().coerceAtLeast(1)
        }.toMutableList()
        val adjustableIndices = phases.indices.filter { !pinned[it] }
        if (adjustableIndices.isNotEmpty()) {
            val delta = totalMeters - rounded.sum()
            val anchorIdx = easyBookendIndices.maxByOrNull { rounded[it] }
                ?: adjustableIndices.maxByOrNull { rounded[it] }
                ?: adjustableIndices.last()
            rounded[anchorIdx] = (rounded[anchorIdx] + delta).coerceAtLeast(1)
            var fix = totalMeters - rounded.sum()
            while (fix != 0) {
                val idx = if (fix > 0) {
                    adjustableIndices.maxByOrNull { rounded[it] } ?: adjustableIndices.first()
                } else {
                    adjustableIndices.filter { rounded[it] > 1 }.maxByOrNull { rounded[it] } ?: break
                }
                if (fix > 0) {
                    rounded[idx]++
                    fix--
                } else {
                    rounded[idx]--
                    fix++
                }
            }
        }

        Engine15M5SegmentDistanceClaim(
            weekNumber = alt.weekNumber,
            slotNumber = alt.slotNumber,
            assertedMetersByTimedSegment = rounded.map { it.toDouble() },
            externalReviewReference = null,
            provenance = Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
        )
    }
}
