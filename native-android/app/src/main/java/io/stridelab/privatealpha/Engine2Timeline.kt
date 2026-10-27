package io.stridelab.privatealpha

import java.time.Instant

/** One window of known observations, not a readiness score or a training prescription. */
internal data class Engine2DisciplineWindow(
    val confirmedWorkouts: Int,
    val workoutsWithDuration: Int,
    val workoutsMissingDuration: Int,
    /** Null when no measured duration exists; never interpret partial time as full training time. */
    val knownDurationSeconds: Double?,
    val completeDurationCoverage: Boolean
)
internal data class Engine2TrainingWindow(
    val startInclusive: Instant,
    val endExclusive: Instant,
    val byDiscipline: Map<Engine2Discipline, Engine2DisciplineWindow>,
    val running: Engine2RunningCoverage,
    val hasProvenanceCollisions: Boolean
)

/**
 * Actual-only summaries. Unknown and incomplete provider imports cannot be treated as rest
 * or absence of workouts. Different physical source IDs are not automatically deduplicated.
 */
internal object Engine2Timeline {
    fun summarize(context: Engine2AthleteContext, startInclusive: Instant,
                  endExclusive: Instant): Engine2TrainingWindow {
        require(startInclusive.isBefore(endExclusive))
        require(!endExclusive.isAfter(context.asOf.plusNanos(1))) {
            "The window cannot include dates later than the available context"
        }
        val confirmed = context.workouts.filter {
            it.status == Engine2RecordStatus.CONFIRMED &&
                !it.occurredAt.isBefore(startInclusive) && it.occurredAt.isBefore(endExclusive)
        }
        val map = Engine2Discipline.entries.associateWith { discipline ->
            val workouts = confirmed.filter { it.discipline == discipline }
            val measured = workouts.mapNotNull { workout ->
                workout.measurements.firstOrNull { it.metric == Engine2Metric.DURATION_SECONDS }?.value
            }
            Engine2DisciplineWindow(
                confirmedWorkouts = workouts.size,
                workoutsWithDuration = measured.size,
                workoutsMissingDuration = workouts.size - measured.size,
                knownDurationSeconds = measured.takeIf { it.isNotEmpty() }?.sum(),
                completeDurationCoverage = workouts.isNotEmpty() && measured.size == workouts.size
            )
        }
        val runs = confirmed.filter { it.discipline == Engine2Discipline.RUNNING }
        val distances = runs.mapNotNull { workout ->
            workout.measurements.firstOrNull { it.metric == Engine2Metric.DISTANCE_KM }?.value
        }
        return Engine2TrainingWindow(
            startInclusive,
            endExclusive,
            map,
            Engine2RunningCoverage(
                runs.size, distances.size, runs.size - distances.size,
                distances.takeIf { it.isNotEmpty() }?.sum(),
                runs.isNotEmpty() && distances.size == runs.size
            ),
            Engine2ProvenanceAudit.inspect(context).sourceCollisions.isNotEmpty()
        )
    }
}
