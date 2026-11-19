package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class HeartRateZoneBucket(
    val zone: Int,
    val lowerBpm: Int?,
    val upperBpm: Int?,
    val seconds: Double,
    val fraction: Double
)

internal data class HeartRateZoneDistribution(
    val method: HeartRateZoneMethod,
    val settings: AthleteHeartRateZoneSettings,
    val buckets: List<HeartRateZoneBucket>,
    val classifiedSeconds: Double,
    val classifiedIntervals: Int,
    val excludedIntervals: Int,
    val importedGarminProfile: GarminHeartRateZoneProfile? = null
)

internal object HeartRateZoneAnalysis {
    private const val MAX_RECORDED_GAP_SECONDS = 30.0

    fun distribute(
        samples: List<ActivitySample>,
        settings: AthleteHeartRateZoneSettings
    ): HeartRateZoneDistribution? {
        if (!settings.ready) return null
        val cutoffs = cutoffs(settings) ?: return null
        return distributeUsingCutoffs(samples, cutoffs, settings, null)
    }

    fun distribute(
        samples: List<ActivitySample>,
        profile: GarminHeartRateZoneProfile
    ): HeartRateZoneDistribution? =
        distributeUsingCutoffs(
            samples, profile.upperTransitionsBpm, AthleteHeartRateZoneSettings(), profile
        )

    private fun distributeUsingCutoffs(
        samples: List<ActivitySample>,
        cutoffs: List<Int>,
        settings: AthleteHeartRateZoneSettings,
        imported: GarminHeartRateZoneProfile?
    ): HeartRateZoneDistribution? {
        if (samples.size < 2 || cutoffs.size != 4 ||
            cutoffs.zipWithNext().any { (a, b) -> b <= a }) return null
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
                val index = cutoffs.indexOfFirst { fromHr < it }
                    .takeIf { it >= 0 } ?: 4
                secondsPerZone[index] += seconds
                included++
            }
        }

        val total = secondsPerZone.sum()
        if (total <= 0.0) return null
        return HeartRateZoneDistribution(
            method = settings.method,
            settings = settings,
            buckets = secondsPerZone.mapIndexed { index, seconds ->
                HeartRateZoneBucket(
                    zone = index + 1,
                    lowerBpm = if (index == 0) null else cutoffs[index - 1],
                    upperBpm = cutoffs.getOrNull(index)?.minus(1),
                    seconds = seconds,
                    fraction = seconds / total
                )
            },
            classifiedSeconds = total,
            classifiedIntervals = included,
            excludedIntervals = excluded,
            importedGarminProfile = imported
        )
    }

    fun distribute(
        samples: List<ActivitySample>,
        knownMaximumHr: Int
    ): HeartRateZoneDistribution? =
        distribute(
            samples,
            AthleteHeartRateZoneSettings(
                method = HeartRateZoneMethod.MAX_BPM,
                maximumHr = knownMaximumHr
            )
        )

    private fun cutoffs(settings: AthleteHeartRateZoneSettings): List<Int>? =
        when (settings.method) {
            HeartRateZoneMethod.MAX_BPM -> {
                val max = settings.maximumHr ?: return null
                listOf(.60, .70, .80, .90).map {
                    ceil(max * it).toInt()
                }
            }
            HeartRateZoneMethod.LTHR_PERCENT -> {
                val threshold = settings.lactateThresholdHr ?: return null
                listOf(.85, .90, .95, 1.00).map {
                    ceil(threshold * it).toInt()
                }
            }
            HeartRateZoneMethod.HRR_PERCENT -> {
                val max = settings.maximumHr ?: return null
                val resting = settings.restingHr ?: return null
                val reserve = max - resting
                listOf(.60, .70, .80, .90).map {
                    ceil(resting + reserve * it).toInt()
                }
            }
        }

    fun methodSummary(settings: AthleteHeartRateZoneSettings): String =
        when (settings.method) {
            HeartRateZoneMethod.MAX_BPM ->
                "MAX BPM · ${settings.maximumHr ?: "—"} bpm maximum"
            HeartRateZoneMethod.LTHR_PERCENT ->
                "LTHR% · ${settings.lactateThresholdHr ?: "—"} bpm threshold"
            HeartRateZoneMethod.HRR_PERCENT ->
                "HRR% · ${settings.maximumHr ?: "—"} max / " +
                    "${settings.restingHr ?: "—"} resting"
        }

    fun methodDefinition(settings: AthleteHeartRateZoneSettings): String =
        when (settings.method) {
            HeartRateZoneMethod.MAX_BPM ->
                "Z1 below 60% Max HR, Z2 60–69%, Z3 70–79%, " +
                    "Z4 80–89%, Z5 at least 90%."
            HeartRateZoneMethod.LTHR_PERCENT ->
                "Z1 below 85% LTHR, Z2 85–89%, Z3 90–94%, " +
                    "Z4 95–99%, Z5 at least 100%."
            HeartRateZoneMethod.HRR_PERCENT ->
                "Z1 below 60% HRR, Z2 60–69%, Z3 70–79%, " +
                    "Z4 80–89%, Z5 at least 90% HRR."
        }

    fun presentation(
        distribution: HeartRateZoneDistribution
    ): ActivityInsightPresentation {
        val dominant = distribution.buckets.maxByOrNull { it.seconds }!!
        val tied = distribution.buckets.count {
            kotlin.math.abs(it.seconds - dominant.seconds) < 0.001
        } > 1
        val dominantPct = (dominant.fraction * 100).roundToInt()
        val imported = distribution.importedGarminProfile
        val sourceLabel = if (imported != null)
            "Garmin Connect ${imported.sport.lowercase(Locale.ROOT)} · actual BPM floors"
        else methodSummary(distribution.settings)
        val evidence = distribution.buckets.map { bucket ->
            "Zone ${bucket.zone}: ${ActivityRecord.duration(bucket.seconds)} " +
                "(${(bucket.fraction * 100).roundToInt()}% of classified time)."
        } + listOf(
            if (imported != null)
                "Source: Garmin Connect ${imported.sport}; BPM floors ${imported.floorsBpm.joinToString(", ")}. Actual imported boundaries; sample durations remain approximate."
            else
                "Method: ${methodSummary(distribution.settings)}. " +
                    methodDefinition(distribution.settings) +
                    " These are StrideLab estimates, not imported Garmin zone boundaries.",
            "Only ${distribution.classifiedIntervals} intervals between recorded HR " +
                "samples lasting 30 seconds or less were classified; " +
                "${distribution.excludedIntervals} missing, duplicate or longer " +
                "intervals were excluded. Time in zones is approximate.",
            "This distribution cannot determine training readiness, recovery " +
                "or a specific workout benefit."
        )
        return ActivityInsightPresentation(
            headline = if (tied) "Time was spread across multiple heart-rate zones"
                else "Most classified time in Zone ${dominant.zone}",
            summary = if (tied)
                "Multiple zones share the largest portion of " +
                    "${ActivityRecord.duration(distribution.classifiedSeconds)} classified " +
                    "recording time using ${sourceLabel}."
            else String.format(
                Locale.getDefault(),
                "About %d%% of %s classified recording time was in Zone %d using %s.",
                dominantPct,
                ActivityRecord.duration(distribution.classifiedSeconds),
                dominant.zone,
                sourceLabel
            ),
            evidence = evidence,
            hasComparison = false
        )
    }
}
