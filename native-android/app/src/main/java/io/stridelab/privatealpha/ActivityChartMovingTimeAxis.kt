package io.stridelab.privatealpha

/**
 * Moving-time x-axis for the Activity Detail charts.
 *
 * Paused intervals are removed from the horizontal timeline only. Source elapsed
 * timestamps, FIT samples, splits, GPS, and pause evidence are never rewritten.
 * Adjacent active measurements can be visually joined without inventing samples.
 */
internal class ActivityChartMovingTimeAxis private constructor(
    private val firstSeconds: Double,
    private val lastSeconds: Double,
    private val pauses: List<ActivityPauseInterval>
) {
    val durationSeconds: Double get() = position(lastSeconds)

    /** Elapsed source seconds mapped to a zero-based, pause-compressed x-axis. */
    fun position(seconds: Double): Double {
        val bounded = seconds.coerceIn(firstSeconds, lastSeconds)
        var pausedSeconds = 0.0
        for (pause in pauses) {
            if (bounded <= pause.startSeconds) break
            pausedSeconds += (minOf(bounded, pause.endSeconds) - pause.startSeconds)
                .coerceAtLeast(0.0)
        }
        return (bounded - firstSeconds - pausedSeconds).coerceAtLeast(0.0)
    }

    /** Paused sensor points stay in source data, but are not plotted as moving samples. */
    fun isPaused(seconds: Double): Boolean =
        pauses.any { seconds >= it.startSeconds && seconds < it.endSeconds }

    companion object {
        fun from(
            samples: List<ActivitySample>,
            pauseIntervals: List<ActivityPauseInterval>
        ): ActivityChartMovingTimeAxis {
            val first = samples.firstOrNull()?.seconds ?: 0.0
            val last = samples.lastOrNull()?.seconds?.coerceAtLeast(first) ?: first
            val merged = mutableListOf<ActivityPauseInterval>()
            pauseIntervals.asSequence()
                .filter {
                    it.startSeconds.isFinite() && it.endSeconds.isFinite() &&
                        it.endSeconds > it.startSeconds
                }
                .mapNotNull { pause ->
                    val start = pause.startSeconds.coerceIn(first, last)
                    val end = pause.endSeconds.coerceIn(first, last)
                    if (end > start) ActivityPauseInterval(start, end) else null
                }
                .sortedBy { it.startSeconds }
                .forEach { pause ->
                    val previous = merged.lastOrNull()
                    if (previous != null && pause.startSeconds <= previous.endSeconds) {
                        merged[merged.lastIndex] = previous.copy(
                            endSeconds = maxOf(previous.endSeconds, pause.endSeconds)
                        )
                    } else merged.add(pause)
                }
            return ActivityChartMovingTimeAxis(first, last, merged)
        }
    }
}
