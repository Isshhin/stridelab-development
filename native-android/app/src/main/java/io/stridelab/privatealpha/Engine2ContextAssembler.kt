package io.stridelab.privatealpha

import java.time.Instant

/**
 * Pure ingestion boundary for synthetic or already-authorized provider records.
 * It never contacts a provider, estimates a missing value, or merges different source IDs.
 */
internal data class Engine2RawMetric(
    val metric: Engine2Metric,
    val value: Double,
    val exerciseId: String? = null,
    val setNumber: Int? = null
)

internal data class Engine2IncomingWorkout(
    val id: String,
    val discipline: Engine2Discipline,
    val occurredAt: Instant,
    val status: Engine2RecordStatus,
    val source: Engine2Source?,
    val measurements: List<Engine2RawMetric> = emptyList()
)

internal data class Engine2IncomingWellness(
    val id: String,
    val source: Engine2Source,
    val metric: Engine2Metric,
    val value: Double
)

internal data class Engine2ImportIssue(
    val recordId: String,
    val description: String
)

internal data class Engine2ImportResult(
    val context: Engine2AthleteContext,
    /** Fail-closed invalid rows are rejected rather than repairing raw telemetry. */
    val rejected: List<Engine2ImportIssue>,
    val provenance: Engine2ProvenanceReport
)

/**
 * Converts source-labeled input into validated, immutable Engine 2.0 context.
 * A missing source may describe a PLANNED workout only. Actual entries must have
 * an explicitly identified manual/device/provider source. Conflicting records
 * fail independently without discarding other valid, source-backed entries.
 */
internal object Engine2ContextAssembler {
    fun build(
        ownerId: String,
        asOf: Instant,
        workouts: List<Engine2IncomingWorkout>,
        wellness: List<Engine2IncomingWellness>,
        equipment: Map<String, Int> = emptyMap(),
        goals: List<String> = emptyList()
    ): Engine2ImportResult {
        require(ownerId.isNotBlank())
        val acceptedWorkouts = mutableListOf<Engine2WorkoutEvidence>()
        val acceptedWellness = mutableListOf<Engine2WellnessEvidence>()
        val issues = mutableListOf<Engine2ImportIssue>()
        val workoutIds = hashSetOf<String>()
        val wellnessIds = hashSetOf<String>()
        for (row in workouts) {
            try {
                require(row.id.isNotBlank() && row.id !in workoutIds)
                require(row.status == Engine2RecordStatus.PLANNED ||
                    row.source != null) { "Actual workout needs an explicit source" }
                require(row.source == null || row.source.accountId == ownerId)
                require(row.status == Engine2RecordStatus.PLANNED || !row.occurredAt.isAfter(asOf))
                require(row.source == null || (!row.source.observedAt.isAfter(asOf) &&
                    row.source.receivedAt?.isAfter(asOf) != true))
                require(row.status != Engine2RecordStatus.PLANNED || row.measurements.isEmpty())
                val source = row.source
                val measures = row.measurements.map { raw ->
                    require(source != null) { "Measured data needs source provenance" }
                    Engine2Measurement(raw.metric, raw.value, source,
                        raw.exerciseId, raw.setNumber)
                }
                val normalized = Engine2WorkoutEvidence(
                    row.id, row.discipline, row.occurredAt, row.status, measures
                )
                // Validate membership without throwing away unrelated rows.
                require(normalized.measurements.none {
                    it.metric in setOf(Engine2Metric.HRV_MS,
                        Engine2Metric.RESTING_HR_BPM, Engine2Metric.SLEEP_SECONDS)
                })
                acceptedWorkouts += normalized
                workoutIds += row.id
            } catch (error: IllegalArgumentException) {
                issues += Engine2ImportIssue(row.id,
                    error.message ?: "Unsupported workout row")
            }
        }
        for (row in wellness) {
            try {
                require(row.id.isNotBlank() && row.id !in wellnessIds)
                require(row.source.accountId == ownerId)
                require(!row.source.observedAt.isAfter(asOf) &&
                    row.source.receivedAt?.isAfter(asOf) != true)
                val normalized = Engine2WellnessEvidence(row.id,
                    Engine2Measurement(row.metric, row.value, row.source))
                acceptedWellness += normalized
                wellnessIds += row.id
            } catch (error: IllegalArgumentException) {
                issues += Engine2ImportIssue(row.id,
                    error.message ?: "Unsupported wellness row")
            }
        }
        val context = Engine2AthleteContext(
            ownerId, asOf,
            acceptedWorkouts.sortedWith(compareBy<Engine2WorkoutEvidence> {
                it.occurredAt
            }.thenBy { it.id }),
            acceptedWellness.sortedWith(compareBy<Engine2WellnessEvidence> {
                it.metric.source.observedAt
            }.thenBy { it.id }),
            equipment.toMap(), goals.toList()
        )
        return Engine2ImportResult(context, issues.toList(),
            Engine2ProvenanceAudit.inspect(context))
    }
}
