package io.stridelab.privatealpha

import java.time.Duration
import java.util.Locale
import kotlin.math.abs

/** Descriptive comparisons of source summaries, never a fitness or readiness estimate. */
internal data class RunningComparison(val count: Int, val medianPace: Double, val changePercent: Double,
    val oldest: java.time.Instant, val newest: java.time.Instant)

/**
 * UI-ready, deterministic activity summary. Unlike generative athlete
 * intelligence, these statements are assembled only from verified source
 * metrics and the conservative same-source historical comparison.
 */
internal data class ActivityInsightPresentation(
    val headline: String,
    val summary: String,
    val evidence: List<String>,
    val hasComparison: Boolean
)

internal object ActivityIntelligence {
    private fun discipline(a: ActivityRecord) = a.type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
    private fun running(a: ActivityRecord) = discipline(a) in setOf("run", "running", "trailrun", "trailrunning", "treadmill", "virtualrun")
    private fun valid(a: ActivityRecord) = running(a) && a.distanceM?.let { it.isFinite() && it >= 1000 } == true &&
        a.recordedPace?.let { it.isFinite() && it > 0 } == true

    fun comparison(activity: ActivityRecord, history: List<ActivityRecord>): RunningComparison? {
        if (!valid(activity)) return null
        // Same provider and exact recorded discipline reduce, but cannot remove, context differences.
        val candidates = history.distinctBy { it.id }.filter {
            it.id != activity.id && valid(it) && it.source == activity.source && discipline(it) == discipline(activity) &&
                it.startedAt < activity.startedAt && Duration.between(it.startedAt, activity.startedAt) <= Duration.ofDays(90) &&
                abs(it.distanceM!! - activity.distanceM!!) / activity.distanceM <= .10
        }.sortedByDescending { it.startedAt }.take(12)
        if (candidates.size < 3) return null
        val paces = candidates.map { it.recordedPace!! }.sorted()
        val middle = paces.size / 2
        val median = if (paces.size % 2 == 0) (paces[middle - 1] + paces[middle]) / 2 else paces[middle]
        return RunningComparison(candidates.size, median, (activity.recordedPace!! / median - 1) * 100,
            candidates.minOf { it.startedAt }, candidates.maxOf { it.startedAt })
    }

    fun narrative(activity: ActivityRecord, history: List<ActivityRecord>): String {
        if (!running(activity)) return "Running comparisons do not apply to this discipline. Recorded heart rate and duration describe this session; they do not measure strength progress or workout quality."
        val comparison = comparison(activity, history) ?: return "There is not enough comparable recorded history for a pace comparison. This requires at least three earlier runs from the same source and recorded discipline, within 10% of this distance and the preceding 90 days. Keep recording sessions; no fitness change is inferred."
        val direction = when {
            abs(comparison.changePercent) < 1 -> "within 1% of"
            comparison.changePercent < 0 -> String.format(Locale.getDefault(), "%.1f%% faster than", abs(comparison.changePercent))
            else -> String.format(Locale.getDefault(), "%.1f%% slower than", comparison.changePercent)
        }
        return "Your recorded average pace is $direction the usual pace across ${comparison.count} similar earlier runs (${ActivityRecord.duration(comparison.medianPace)} /km). This describes pace, not improved or reduced fitness: effort, terrain, weather and stops may differ. Review those conditions and how the run felt before changing your next session."
    }
    /**
     * Athlete-intelligence-style presentation without pretending to use a
     * generative AI model or inventing achievements, zones or readiness.
     * The existing comparison logic and descriptive detail lines are reused.
     */
    fun presentation(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        history: List<ActivityRecord>
    ): ActivityInsightPresentation? {
        val matched = comparison(activity, history) ?: return null
        val pace = activity.recordedPace ?: return null
        val context = briefInsights(activity, recorded, history)

        val headline: String
        val summary: String
        run {
            val change = abs(matched.changePercent)
            headline = when {
                change < 1.0 -> "A pace close to your comparable runs"
                matched.changePercent < 0.0 ->
                    String.format(Locale.getDefault(),
                        "A %.1f%% faster pace than similar runs", change)
                else -> String.format(Locale.getDefault(),
                    "A %.1f%% slower pace than similar runs", change)
            }
            summary = "You averaged " + ActivityRecord.duration(pace) +
                "/km, compared with " + ActivityRecord.duration(matched.medianPace) +
                "/km across " + matched.count + " similar runs."
        }
        return ActivityInsightPresentation(headline, summary, context,
            hasComparison = true)
    }

    /**
     * Compact, source-only activity descriptors for the Overall Analysis card.
     * These describe recorded pacing/HR/cadence patterns, not training benefit.
     */
    fun workoutCharacter(
        activity: ActivityRecord,
        recorded: ActivityDetail?
    ): String? {
        if (!running(activity) || recorded == null) return null
        val tags = mutableListOf<String>()

        val fullKilometerPaces = recorded.laps.mapNotNull { lap ->
            val distance = lap.distance
            val pace = lap.pace
            if (distance != null && pace != null &&
                distance.isFinite() && pace.isFinite() && pace > 0.0 &&
                kotlin.math.abs(distance - 1000.0) <= 50.0
            ) pace else null
        }
        if (fullKilometerPaces.size >= 4) {
            val mean = fullKilometerPaces.average()
            val variance = fullKilometerPaces.sumOf { (it - mean) * (it - mean) } /
                fullKilometerPaces.size
            val cv = kotlin.math.sqrt(variance) / mean
            when {
                cv <= .04 -> tags += "Steady pacing"
                cv >= .10 -> tags += "Variable pacing"
            }
            val half = fullKilometerPaces.size / 2
            val early = fullKilometerPaces.take(half).average()
            val late = fullKilometerPaces.takeLast(half).average()
            when {
                late <= early * .97 -> tags += "Faster finish"
                late >= early * 1.03 -> tags += "Slower finish"
            }
        }

        val cadence = recorded.samples.mapNotNull {
            it.cadenceSpm?.takeIf { value -> value.isFinite() && value > 0.0 }
        }
        if (tags.size < 3 && cadence.size >= 20) {
            val mean = cadence.average()
            val variance = cadence.sumOf { (it - mean) * (it - mean) } / cadence.size
            val cv = kotlin.math.sqrt(variance) / mean
            if (cv <= .035) tags += "Stable cadence"
        }

        val heartRate = recorded.samples.mapNotNull {
            it.hr?.takeIf { value -> value.isFinite() && value > 0.0 }
        }
        if (tags.size < 3 && heartRate.size >= 20) {
            val quarter = (heartRate.size / 4).coerceAtLeast(1)
            val early = heartRate.take(quarter).average()
            val late = heartRate.takeLast(quarter).average()
            when {
                late >= early + 8.0 -> tags += "Heart rate rose late"
                late <= early - 8.0 -> tags += "Heart rate eased late"
            }
        }

        return tags.take(3).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Read-only Activity Analysis 1.0. Describe measured evidence, not
     * fitness, training readiness, recovery or a recommended next workout.
     * The existing conservative same-source historical filter is unchanged.
     */
    fun briefInsights(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        history: List<ActivityRecord>
    ): List<String> {
        if (!running(activity)) return emptyList()
        val lines=mutableListOf<String>()
        val similar=comparison(activity, history)
        if (similar != null) {
            val percent=abs(similar.changePercent)
            val direction=when {
                percent < 1.0 -> "within 1% of"
                similar.changePercent < 0.0 -> "faster than"
                else -> "slower than"
            }
            val difference=if (percent < 1.0) "" else
                String.format(Locale.getDefault(), "%.1f%% ", percent)
            lines.add(
                "You ran " + difference + direction +
                " the usual pace across " + similar.count + " similar earlier runs (" +
                ActivityRecord.duration(similar.medianPace) + "/km). " +
                "Think back to the route and how hard you worked on those days."
            )
        } else {
            lines.add(
                "No reliable pace comparison yet. At least three earlier runs " +
                "of the same source, discipline and similar distance are required."
            )
        }
        val hr=recorded?.samples.orEmpty()
            .mapNotNull { it.hr?.takeIf { value -> value.isFinite() && value > 0.0 } }
        if (hr.size >= 2) {
            lines.add(
                "Your heart rate went from " + hr.min().toInt() +
                " to " + hr.max().toInt() + " bpm. Check the chart to see where it rose."
            )
        }
        val cadence=recorded?.samples.orEmpty()
            .mapNotNull { it.cadenceSpm?.takeIf { value -> value.isFinite() && value > 0.0 } }
        if (cadence.size >= 2) {
            lines.add(
                "Your step rate went from " + cadence.min().toInt() +
                " to " + cadence.max().toInt() +
                " steps per minute."
            )
        }
        return lines
    }

}
