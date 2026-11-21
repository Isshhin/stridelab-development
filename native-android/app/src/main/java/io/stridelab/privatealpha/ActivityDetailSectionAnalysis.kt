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
        if (title == "Heart rate" && values.size >= 4) {
            val change = late - early
            val finding = CoachingFinding(
                observation = when {
                    abs(change) < 5 -> "Heart rate was similar early and late"
                    change > 0 -> "Heart rate was higher late in the run"
                    else -> "Heart rate was lower late in the run"
                },
                interpretation = "The early readings averaged ${early.roundToInt()} bpm and the late readings averaged ${late.roundToInt()} bpm. " +
                    if (abs(change) < 5) "That’s a small difference between those portions."
                    else "Check whether the pace or terrain changed around the same time.",
                evidence = listOf("Early and late use the first and last quarters of the saved heart-rate readings.",
                    "Across the recording, the readings averaged ${average.roundToInt()} bpm."),
                recommendation = "On your next easy run, compare heart rate at a similar pace on similar terrain.",
                confidence = "Heart rate alone can’t show whether effort stayed the same or establish cardiac drift. Gaps and pauses can affect this comparison."
            )
            return ActivityInsightPresentation(finding.observation,
                finding.interpretation + " " + finding.recommendation,
                finding.evidence + finding.confidence, hasComparison = false)
        }
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
            "Pace" -> when {
                abs(late - early) < 8 -> "Similar pace early and late"
                late > early -> "Your pace was slower late in the run"
                else -> "Your pace was quicker late in the run"
            }
            "Heart rate" -> "Too little heart-rate data to compare"
            "Elevation" -> "See where the route climbed and dropped"
            else -> if (abs(late - early) < 5) "Step rate was similar early and late"
                else "Your step rate changed as the run went on"
        }
        val summary = when (title) {
            "Pace" -> "Early and late readings averaged ${display(early)} and ${display(late)}. " +
                "Check the route and any planned recoveries before treating the change as a pacing issue."
            "Heart rate" -> "There are only a few readings. Use how the run felt as your main guide."
            "Elevation" -> "Use the climbs to put slower splits in context. On an easy run, keeping effort steady matters more than holding pace uphill."
            else -> "Compare step rate with pace on similar terrain. If the pace changed too, there’s no reason to chase a different step rate from this chart alone."
        }
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
