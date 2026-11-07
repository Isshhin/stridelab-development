package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * UI-only percent-of-maximum-HR zones. No activity summary peak is ever
 * substituted for an athlete's known maximum HR. The input is provided
 * explicitly in Activity Detail; this does not change coaching or storage.
 */
internal data class HeartRateZoneBucket(
    val zone: Int,
    val lowerBpm: Int?,
    val upperBpm: Int?,
    val seconds: Double,
    val fraction: Double
)

internal data class HeartRateZoneDistribution(
    val maximumHr: Int,
    val buckets: List<HeartRateZoneBucket>,
    val classifiedSeconds: Double,
    val classifiedIntervals: Int,
    val excludedIntervals: Int
)

internal object HeartRateZoneAnalysis {
    private const val MAX_RECORDED_GAP_SECONDS = 30.0
    // Conventional five-zone %HRmax scheme; the lower half of Z1 includes
    // values below 50% so covered measurements are not silently discarded.
    private val upperCutoffFractions = listOf(0.60, 0.70, 0.80, 0.90)

    fun distribute(
        samples: List<ActivitySample>,
        knownMaximumHr: Int
    ): HeartRateZoneDistribution? {
        if (knownMaximumHr !in 100..240 || samples.size < 2) return null
        val cutoffs = upperCutoffFractions.map { ceil(knownMaximumHr * it).toInt() }
        val secondsPerZone = DoubleArray(5)
        var included = 0
        var excluded = 0
        samples.zipWithNext().forEach { (from, to) ->
            val seconds = to.seconds - from.seconds
            val fromHr = from.hr
            val toHr = to.hr
            if (!seconds.isFinite() || seconds <= 0.0 ||
                seconds > MAX_RECORDED_GAP_SECONDS ||
                fromHr == null || !fromHr.isFinite() || fromHr <= 0.0 ||
                toHr == null || !toHr.isFinite() || toHr <= 0.0
            ) {
                excluded++
            } else {
                // The first observed HR represents the bounded interval to
                // the next real measurement. This is an estimate, not
                // fabricated interpolated HR or full-session time.
                val index = cutoffs.indexOfFirst { fromHr < it }
                    .takeIf { it >= 0 } ?: 4
                secondsPerZone[index] += seconds
                included++
            }
        }
        val total = secondsPerZone.sum()
        if (total <= 0.0) return null
        val buckets = secondsPerZone.mapIndexed { index, seconds ->
            HeartRateZoneBucket(
                zone = index + 1,
                lowerBpm = if (index == 0) null else cutoffs[index - 1],
                upperBpm = cutoffs.getOrNull(index)?.minus(1),
                seconds = seconds,
                fraction = seconds / total
            )
        }
        return HeartRateZoneDistribution(
            maximumHr = knownMaximumHr,
            buckets = buckets,
            classifiedSeconds = total,
            classifiedIntervals = included,
            excludedIntervals = excluded
        )
    }

    fun presentation(distribution: HeartRateZoneDistribution): ActivityInsightPresentation {
        val dominant = distribution.buckets.maxByOrNull { it.seconds }!!
        val dominantPct = (dominant.fraction * 100).roundToInt()
        val evidence = distribution.buckets.map { bucket ->
            "Zone ${bucket.zone}: ${ActivityRecord.duration(bucket.seconds)} " +
                "(${(bucket.fraction * 100).roundToInt()}% of classified time)."
        } + listOf(
            "Zones use the manually entered ${distribution.maximumHr} bpm maximum: " +
                "Z1 below 60%, Z2 60–69%, Z3 70–79%, Z4 80–89%, Z5 at least 90%. " +
                "These are not the athlete's imported Garmin zone settings.",
            "Only ${distribution.classifiedIntervals} intervals between recorded HR " +
                "samples lasting 30 seconds or less were classified; " +
                "${distribution.excludedIntervals} missing, duplicate or longer " +
                "intervals were excluded. Time in zones is approximate.",
            "This distribution cannot determine training readiness, recovery " +
                "or a specific workout benefit."
        )
        return ActivityInsightPresentation(
            headline = "Most classified time in Zone ${dominant.zone}",
            summary = String.format(
                Locale.getDefault(),
                "About %d%% of %s classified recording time was in Zone %d. " +
                    "These estimates use the maximum heart rate you entered, " +
                    "not Garmin's configured zones.",
                dominantPct,
                ActivityRecord.duration(distribution.classifiedSeconds),
                dominant.zone
            ),
            evidence = evidence,
            hasComparison = false
        )
    }
}
