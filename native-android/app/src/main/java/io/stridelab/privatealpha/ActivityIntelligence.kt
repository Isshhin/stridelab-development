package io.stridelab.privatealpha

import java.time.Duration
import java.util.Locale
import kotlin.math.abs

/** Descriptive comparisons of source summaries, never a fitness or readiness estimate. */
internal data class RunningComparison(val count: Int, val medianPace: Double, val changePercent: Double,
    val oldest: java.time.Instant, val newest: java.time.Instant)

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
        return "Your recorded average pace is $direction the median of ${comparison.count} comparable earlier runs (${ActivityRecord.duration(comparison.medianPace)} /km). This describes pace, not improved or reduced fitness: effort, terrain, weather and stops may differ. Review those conditions and how the run felt before changing your next session."
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
                "Recorded average pace is " + difference + direction +
                " the median of " + similar.count + " comparable earlier runs (" +
                ActivityRecord.duration(similar.medianPace) + "/km). " +
                "Different effort, terrain or weather can affect this comparison."
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
                "Recorded heart rate ranged from " + hr.min().toInt() +
                " to " + hr.max().toInt() + " bpm across " + hr.size +
                " measured samples. This does not determine fatigue or recovery."
            )
        }
        val cadence=recorded?.samples.orEmpty()
            .mapNotNull { it.cadenceSpm?.takeIf { value -> value.isFinite() && value > 0.0 } }
        if (cadence.size >= 2) {
            lines.add(
                "Recorded cadence ranged from " + cadence.min().toInt() +
                " to " + cadence.max().toInt() +
                " spm. This is not an efficiency score."
            )
        }
        return lines
    }

}
