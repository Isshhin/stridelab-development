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
            "Your quickest split was number ${fastest.first.index + 1} at ${ActivityRecord.duration(fastest.second)}/km.",
            "Split ${slowest.first.index + 1} was your slowest at ${ActivityRecord.duration(slowest.second)}/km.",
            "There were ${difference} seconds per kilometer between those two splits."
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
            evidence.add("The split distances may differ, so I’d use the individual laps to review the run.")
        }
        val splitHeartRates = laps.mapNotNull { it.hr?.takeIf { hr -> hr.isFinite() && hr > 0.0 } }
        if (splitHeartRates.size >= 2) {
            evidence.add("Recorded lap-average heart rates ranged from " +
                "${splitHeartRates.min().roundToInt()} to ${splitHeartRates.max().roundToInt()} bpm.")
        }
        return ActivityInsightPresentation(
            headline = if (difference < 3) "You held a steady pace across these splits"
                else "Your pace varied across the splits",
            summary = "Split ${fastest.first.index + 1} was the fastest recorded at " +
                "${ActivityRecord.duration(fastest.second)}/km. " +
                "Use the lap distances to see which splits make a fair comparison.",
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
            "The $name readings went from ${display(low)} to ${display(high)}.",
            "Early readings averaged ${display(early)}. Late readings averaged " +
                "${display(late)}.",
            "The average uses ${values.size} saved readings. " +
                "It leaves out gaps in the recording."
        )
        evidence.add(when (title) {
            "Pace" -> "If your pace changed, check where it happened. A hill, a turn, or a planned recovery can explain the change."
            "Heart rate" -> "Check where your heart rate changed against the pace and hills. These readings alone do not establish cardiac drift."
            "Elevation" -> "Look for where the climbs fell in the run. The lowest and highest points don’t tell you how much you climbed overall."
            else -> "Check whether your step rate changed when your pace changed. There’s no target step rate to chase here."
        })
        val headline = when (title) {
            "Pace" -> "Recorded pace ranged from ${display(low)} to ${display(high)}"
            "Heart rate" -> "Recorded heart rate ranged from ${display(low)} to ${display(high)}"
            "Elevation" -> "Elevation readings ranged from ${display(low)} to ${display(high)}"
            else -> "Recorded cadence ranged from ${display(low)} to ${display(high)}"
        }
        val summary = "Your saved readings averaged " +
            "${display(average)}. Tap the chart to see where the changes happened."
        return ActivityInsightPresentation(headline, summary, evidence, hasComparison = false)
    }
}

/** Feedback belongs to the linked completed Engine 1.5 run, never to a duplicate activity record. */
internal fun findLinkedCompletedActivityRun(
    plans: List<PlanSnapshot>,
    matchedSourceIds: Set<String>
): Pair<PlanSnapshot, PlannedSession>? {
    if (matchedSourceIds.isEmpty()) return null
    val matches = plans.asSequence()
        .filter { it.engineVersion == ENGINE15_VERSION && it.status in setOf("approved", "paused") }
        .flatMap { plan ->
            plan.sessions.asSequence()
                .filter {
                    it.discipline == "run" && it.status == "completed" &&
                        it.linkedActivityId in matchedSourceIds
                }
                .map { plan to it }
        }
        .take(2)
        .toList()
    return matches.singleOrNull()
}
