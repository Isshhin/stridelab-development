package io.stridelab.privatealpha

import java.time.Instant

/** Experimental Engine 2.0 read model. Not a storage migration or an Engine 1.0 replacement. */
internal enum class Engine2Discipline { RUNNING, STRENGTH, CALISTHENICS, CONDITIONING }
internal enum class Engine2RecordStatus { PLANNED, DRAFT, CONFIRMED }
internal enum class Engine2Metric {
    DISTANCE_KM, DURATION_SECONDS, AVERAGE_HR_BPM, REPS, LOAD_KG, HOLD_SECONDS,
    RESTING_HR_BPM, HRV_MS, SLEEP_SECONDS
}

/** Provider record IDs have meaning only within their source and athlete account. */
internal data class Engine2Source(
    val accountId: String,
    val provider: String,
    val recordId: String,
    val observedAt: Instant,
    val receivedAt: Instant? = null
) {
    init {
        require(accountId.isNotBlank() && accountId.length <= 256)
        require(provider.isNotBlank() && provider.length <= 80)
        require(recordId.isNotBlank() && recordId.length <= 256)
        require(receivedAt == null || !receivedAt.isBefore(observedAt))
    }
}

/** Unknown measurements are omitted, never silently replaced by zero. */
internal data class Engine2Measurement(
    val metric: Engine2Metric,
    val value: Double,
    val source: Engine2Source,
    val exerciseId: String? = null,
    val setNumber: Int? = null
) {
    init {
        require(value.isFinite() && value >= 0.0)
        val isSet = metric in setOf(Engine2Metric.REPS, Engine2Metric.LOAD_KG, Engine2Metric.HOLD_SECONDS)
        if (isSet) require(!exerciseId.isNullOrBlank() && setNumber != null && setNumber > 0)
        else require(exerciseId == null && setNumber == null)
    }
}

internal data class Engine2WorkoutEvidence(
    val id: String,
    val discipline: Engine2Discipline,
    val occurredAt: Instant,
    val status: Engine2RecordStatus,
    val measurements: List<Engine2Measurement> = emptyList()
) {
    init {
        require(id.isNotBlank())
        if (status == Engine2RecordStatus.PLANNED) require(measurements.isEmpty())
        require(measurements.map { Triple(it.metric, it.exerciseId, it.setNumber) }.distinct().size == measurements.size)
        require(measurements.none {
            it.metric in setOf(Engine2Metric.RESTING_HR_BPM, Engine2Metric.HRV_MS, Engine2Metric.SLEEP_SECONDS)
        })
    }
}

internal data class Engine2WellnessEvidence(
    val id: String,
    val metric: Engine2Measurement
) {
    init {
        require(id.isNotBlank())
        require(metric.metric in setOf(Engine2Metric.RESTING_HR_BPM, Engine2Metric.HRV_MS, Engine2Metric.SLEEP_SECONDS))
    }
}

internal data class Engine2RunningCoverage(
    val confirmedRuns: Int,
    val runsWithDistance: Int,
    val runsMissingDistance: Int,
    /** Sum of known observations only; null if no distances. Partial is not total weekly volume. */
    val knownDistanceKm: Double?,
    val complete: Boolean
)

/** A read-only, account-isolated athlete snapshot. No derived recovery score is invented. */
internal data class Engine2AthleteContext(
    val ownerId: String,
    val asOf: Instant,
    val workouts: List<Engine2WorkoutEvidence>,
    val wellness: List<Engine2WellnessEvidence>,
    val equipment: Map<String, Int> = emptyMap(),
    val goals: List<String> = emptyList()
) {
    init {
        require(ownerId.isNotBlank() && ownerId.length <= 256)
        require(workouts.map { it.id }.distinct().size == workouts.size)
        require(wellness.map { it.id }.distinct().size == wellness.size)
        require(equipment.all { it.key.isNotBlank() && it.value >= 0 })
        require(goals.all { it.isNotBlank() })
        require(workouts.all {
            it.status == Engine2RecordStatus.PLANNED || !it.occurredAt.isAfter(asOf)
        })
        require((workouts.flatMap { it.measurements } + wellness.map { it.metric }).none {
            it.source.accountId != ownerId || it.source.observedAt.isAfter(asOf) ||
                it.source.receivedAt?.isAfter(asOf) == true
        })
    }

    fun runningCoverage(): Engine2RunningCoverage {
        val runs = workouts.filter {
            it.discipline == Engine2Discipline.RUNNING && it.status == Engine2RecordStatus.CONFIRMED
        }
        val distances = runs.mapNotNull { workout ->
            workout.measurements.firstOrNull { it.metric == Engine2Metric.DISTANCE_KM }?.value
        }
        val unknown = runs.size - distances.size
        return Engine2RunningCoverage(
            confirmedRuns = runs.size,
            runsWithDistance = distances.size,
            runsMissingDistance = unknown,
            knownDistanceKm = if (distances.isEmpty()) null else distances.sum(),
            complete = runs.isNotEmpty() && unknown == 0
        )
    }
}
