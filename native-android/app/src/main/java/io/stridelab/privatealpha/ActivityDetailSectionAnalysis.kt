package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Presentation-only observations from saved laps and stream samples.
 * Never invents splits, HR zones, physiological drift, training quality, or readiness.
 */
internal object ActivityDetailSectionAnalysis {
    fun splits(laps: List<ActivityLap>, supportsPace: Boolean): ActivityInsightPresentation? {
        if (!supportsPace) return null
        val paced = laps.mapNotNull { lap ->
            lap.pace?.takeIf { it.isFinite() && it > 0.0 }?.let { lap to it }
        }
        if (paced.size < 2) return null
        val fastest = paced.minBy { it.second }
        val slowest = paced.maxBy { it.second }
        val difference = (slowest.second - fastest.second).roundToInt()
        val evidence = mutableListOf(
            "Fastest recorded split: ${fastest.first.index + 1} at ${ActivityRecord.duration(fastest.second)}/km.",
            "Slowest recorded split: ${slowest.first.index + 1} at ${ActivityRecord.duration(slowest.second)}/km.",
            "Recorded split pace range: ${difference} seconds per kilometer across ${paced.size} splits."
        )
        // Half comparisons only make sense for near-equal, full-kilometer laps.
        val fullKilometers = paced.filter { (lap, _) ->
            lap.distance?.let { it.isFinite() && abs(it - 1000.0) <= 50.0 } == true
        }
        if (fullKilometers.size >= 4) {
            val halfSize = fullKilometers.size / 2
            val first = fullKilometers.take(halfSize).map { it.second }.average()
            val last = fullKilometers.takeLast(halfSize).map { it.second }.average()
            val differenceSeconds = abs(first - last)
            evidence.add(
                "First versus last ${halfSize} full-kilometer splits: " +
                    "${ActivityRecord.duration(first)}/km versus ${ActivityRecord.duration(last)}/km. " +
                    (if (differenceSeconds < 3.0) "Average paces were close."
                    else if (last < first) "Later splits were faster on average."
                    else "Later splits were slower on average.")
            )
        } else {
            evidence.add("Split distances may differ; no negative-split conclusion is inferred.")
        }
        val splitHeartRates = laps.mapNotNull { it.hr?.takeIf { hr -> hr.isFinite() && hr > 0.0 } }
        if (splitHeartRates.size >= 2) {
            evidence.add("Recorded lap-average heart rates ranged from " +
                "${splitHeartRates.min().roundToInt()} to ${splitHeartRates.max().roundToInt()} bpm.")
        }
        return ActivityInsightPresentation(
            headline = if (difference < 3) "Consistent pace among recorded splits"
                else "A ${difference}-second spread between split paces",
            summary = "Split ${fastest.first.index + 1} was the fastest recorded at " +
                "${ActivityRecord.duration(fastest.second)}/km. " +
                "These are source-reported laps, not reconstructed kilometer markers.",
            evidence = evidence, hasComparison = false
        )
    }

    fun chart(
        title: String,
        validSamples: List<Pair<ActivitySample, Double>>
    ): ActivityInsightPresentation? {
        if (validSamples.size < 2) return null
        val values = validSamples.map { it.second }
        val low = values.min()
        val high = values.max()
        val average = values.average()
        val quarter = (values.size / 4).coerceAtLeast(1)
        val early = values.take(quarter).average()
        val late = values.takeLast(quarter).average()
        val name = when (title) {
            "Pace" -> "pace"
            "Heart rate" -> "heart rate"
            "Elevation" -> "elevation"
            "Cadence" -> "cadence"
            else -> return null
        }
        fun display(value: Double): String = when (title) {
            "Pace" -> ActivityRecord.duration(value) + "/km"
            "Heart rate" -> "${value.roundToInt()} bpm"
            "Elevation" -> "${value.roundToInt()} m"
            else -> "${value.roundToInt()} spm"
        }
        val evidence = mutableListOf(
            "Recorded $name range: ${display(low)} to ${display(high)}.",
            "First and last ${quarter} measured points averaged ${display(early)} and " +
                "${display(late)}, respectively.",
            "The displayed average is an arithmetic mean of ${values.size} recorded points, " +
                "not a time-weighted average. Missing measurements and pause gaps are not filled."
        )
        evidence.add(when (title) {
            "Pace" -> "Lower pace values are faster. Pace differences alone do not establish a change in fitness."
            "Heart rate" -> "Heart-rate changes alone do not establish cardiac drift, fatigue, or recovery."
            "Elevation" -> "Altitude range is not the same measurement as cumulative elevation gain."
            else -> "Cadence changes alone are not an efficiency or running-form score."
        })
        val headline = when (title) {
            "Pace" -> "Recorded pace ranged from ${display(low)} to ${display(high)}"
            "Heart rate" -> "Recorded heart rate ranged from ${display(low)} to ${display(high)}"
            "Elevation" -> "Elevation readings ranged from ${display(low)} to ${display(high)}"
            else -> "Recorded cadence ranged from ${display(low)} to ${display(high)}"
        }
        val summary = "Across ${values.size} saved measurements, the sampled average " +
            "was ${display(average)}. Explore the chart to inspect the actual recorded points."
        return ActivityInsightPresentation(headline, summary, evidence, hasComparison = false)
    }
}
