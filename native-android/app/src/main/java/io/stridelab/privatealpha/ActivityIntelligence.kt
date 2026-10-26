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
}
