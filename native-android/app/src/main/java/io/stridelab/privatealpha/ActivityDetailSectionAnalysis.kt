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
            "Slowest: split ${slowest.first.index + 1} at ${ActivityRecord.duration(slowest.second)}/km.",
            "Pace spread: $difference sec/km."
        )
        // Half comparisons only make sense for near-equal, full-kilometer laps.
        val fullKilometers = paced.filter { (lap, _) ->
            lap.distance?.let { it.isFinite() && abs(it - 1000.0) <= 50.0 } == true
        }
        var splitComparison: Pair<Double, Double>? = null
        if (fullKilometers.size >= 4) {
            val halfSize = fullKilometers.size / 2
            val first = fullKilometers.take(halfSize).map { it.second }.average()
            val last = fullKilometers.takeLast(halfSize).map { it.second }.average()
            splitComparison = first to last
            val differenceSeconds = abs(first - last)
            evidence.add(
                "First versus last ${halfSize} full-kilometer splits: " +
                    "${ActivityRecord.duration(first)}/km versus ${ActivityRecord.duration(last)}/km. " +
                    (if (differenceSeconds < 3.0) "Average paces were close."
                    else if (last < first) "Later splits were faster on average."
                    else "Later splits were slower on average.")
            )
        }
        val splitHeartRates = laps.mapNotNull { it.hr?.takeIf { hr -> hr.isFinite() && hr > 0.0 } }
        if (splitHeartRates.size >= 2) {
            evidence.add("Recorded lap-average heart rates ranged from " +
                "${splitHeartRates.min().roundToInt()} to ${splitHeartRates.max().roundToInt()} bpm.")
        }
        val halves = splitComparison
        if (halves != null) {
            val (first, last) = halves
            val change = last - first
            if (abs(change).roundToInt() >= 3) {
                val direction = if (change < 0) "faster" else "slower"
                return ActivityInsightPresentation(
                    if (change < 0) "Faster second half" else "Slower second half",
                    "Your later full-kilometer splits averaged ${abs(change).roundToInt()} sec/km $direction " +
                        "(${ActivityRecord.duration(first)} → ${ActivityRecord.duration(last)} /km).",
                    emptyList(), hasComparison = false
                )
            }
        }
        return ActivityInsightPresentation(
            headline = if (difference < 3) "Steady across the splits"
                else "Pace varied across splits",
            summary = "Split ${fastest.first.index + 1} was fastest at " +
                "${ActivityRecord.duration(fastest.second)}/km, " +
                if (difference < 3) "just $difference sec/km from your slowest."
                else "$difference sec/km faster than your slowest.",
            evidence = evidence, hasComparison = false
        )
    }

    fun chart(
        title: String,
        validSamples: List<Pair<ActivitySample, Double>>
    ): ActivityInsightPresentation? {
        if (validSamples.size < 2) return null
        val values = coachingChartValues(title, validSamples.map { it.second })
        if (values.size < 2) return null
        val low = values.min()
        val high = values.max()
        val quarter = (values.size / 4).coerceAtLeast(1)
        val early = values.take(quarter).average()
        val late = values.takeLast(quarter).average()
        if (title == "Heart rate" && values.size >= 4) {
            val change = late - early
            return ActivityInsightPresentation(
                headline = when {
                    abs(change) < 5 -> "Heart rate stayed steady"
                    change > 0 -> "Heart rate rose late"
                    else -> "Heart rate eased late"
                },
                summary = "Your heart rate averaged ${early.roundToInt()} bpm early and " +
                    "${late.roundToInt()} bpm late.",
                evidence = emptyList(),
                hasComparison = false
            )
        }
        if (title !in setOf("Pace", "Heart rate", "Elevation", "Cadence")) return null
        fun display(value: Double): String = when (title) {
            "Pace" -> ActivityRecord.duration(value) + "/km"
            "Heart rate" -> "${value.roundToInt()} bpm"
            "Elevation" -> "${value.roundToInt()} m"
            else -> "${value.roundToInt()} spm"
        }
        val headline = when (title) {
            "Pace" -> when {
                abs(late - early) < 8 -> "Similar pace early and late"
                late > early -> "Pace slowed late"
                else -> "Pace picked up late"
            }
            "Heart rate" -> "Limited heart rate data"
            "Elevation" -> "Climbs shaped the route"
            else -> if (abs(late - early) < 5) "Step rate stayed steady"
                else "Step rate changed"
        }
        val summary = when (title) {
            "Pace" -> "You averaged ${display(early)} early and ${display(late)} late, showing ${if (abs(late - early) < 8) "a steady pace" else "a change in pace"}."
            "Heart rate" -> "There wasn’t enough heart rate data to compare the start and finish."
            "Elevation" -> "The route ranged from ${display(low)} to ${display(high)}, so uphill stretches may have called for a slower pace."
            else -> "You averaged ${display(early)} early and ${display(late)} late, with a typical step rate near ${display(values.average())}."
        }
        return ActivityInsightPresentation(headline, summary, emptyList(), hasComparison = false)
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

/** Filter presentation statistics without modifying the recording or chart cursor. */
internal fun coachingChartValues(title: String, values: List<Double>): List<Double> {
    val plausible = values.filter {
        it.isFinite() && when (title) {
            "Pace" -> it in 120.0..900.0
            "Cadence" -> it in 60.0..260.0
            "Heart rate" -> it > 0.0
            else -> true
        }
    }
    if (plausible.size < 3 || title !in setOf("Pace", "Cadence")) return plausible
    val median = plausible.sorted()[plausible.size / 2]
    return plausible.filter { it in (median * .65)..(median * 1.5) }
}
