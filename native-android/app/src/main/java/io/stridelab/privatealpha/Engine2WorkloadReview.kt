package io.stridelab.privatealpha

import java.time.Duration

/** Descriptive workload deltas; not a validated load, injury or readiness score. */
internal data class Engine2ObservedTrend(
    val recent: Engine2TrainingWindow,
    val previous: Engine2TrainingWindow,
    val comparableRunningDistance: Boolean,
    val runningDistanceChangeKm: Double?,
    val warnings: List<String>
)

internal object Engine2WorkloadReview {
    fun compare(context: Engine2AthleteContext, windowDays: Long = 28): Engine2ObservedTrend {
        require(windowDays in 7..90)
        val currentEnd = context.asOf
        val currentStart = currentEnd.minus(Duration.ofDays(windowDays))
        val previousStart = currentStart.minus(Duration.ofDays(windowDays))
        val current = Engine2Timeline.summarize(context, currentStart, currentEnd)
        val previous = Engine2Timeline.summarize(context, previousStart, currentStart)
        val comparable = current.running.complete && previous.running.complete &&
            !current.hasProvenanceCollisions && !previous.hasProvenanceCollisions
        val change = if (comparable)
            current.running.knownDistanceKm!! - previous.running.knownDistanceKm!!
            else null
        val warnings = mutableListOf<String>()
        if (!current.running.complete || !previous.running.complete)
            warnings += "Running distance observations are incomplete; no full-volume comparison is claimed."
        if (current.hasProvenanceCollisions || previous.hasProvenanceCollisions)
            warnings += "Source identity collisions require review before comparing training volume."
        if (current.byDiscipline.any { (_, data) -> data.workoutsMissingDuration > 0 })
            warnings += "Some training durations are unavailable; totals include measured time only."
        if (current.byDiscipline.values.sumOf { it.confirmedWorkouts } == 0)
            warnings += "No confirmed recent workouts were observed; this does not prove inactivity."
        return Engine2ObservedTrend(current, previous, comparable, change, warnings)
    }
}
