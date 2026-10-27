package io.stridelab.privatealpha

/**
 * A provider-local identity, not a universal physical-activity identity.
 * Never merge Garmin and Intervals.icu records by date, distance, or similar-looking IDs.
 */
internal data class Engine2ProviderKey(
    val accountId: String,
    val provider: String,
    val providerRecordId: String
)
internal data class Engine2SourceCollision(
    val key: Engine2ProviderKey,
    val workoutIds: Set<String>
)

internal data class Engine2ProvenanceReport(
    val distinctProviderRecords: Int,
    val workoutRecordsWithSource: Int,
    val sourceCollisions: List<Engine2SourceCollision>,
    val runsWithoutProviderMeasurements: Int
) {
    val safelyIndexed: Boolean get() = sourceCollisions.isEmpty()
}

internal object Engine2ProvenanceAudit {
    fun inspect(context: Engine2AthleteContext): Engine2ProvenanceReport {
        // The collection uses exact provider-local identities. Different providers are
        // deliberately separate even if they report the same physical activity.
        val indexed = mutableMapOf<Engine2ProviderKey, MutableSet<String>>()
        var workoutsWithSources = 0
        for (workout in context.workouts) {
            val keys = workout.measurements.map { m ->
                Engine2ProviderKey(m.source.accountId, m.source.provider, m.source.recordId)
            }.toSet()
            if (keys.isNotEmpty()) workoutsWithSources++
            for (key in keys) indexed.getOrPut(key) { linkedSetOf() }.add(workout.id)
        }
        val collisions = indexed.entries.asSequence()
            .filter { it.value.size > 1 }
            .map { Engine2SourceCollision(it.key, it.value.toSet()) }
            .sortedWith(compareBy<Engine2SourceCollision> { it.key.provider }
                .thenBy { it.key.providerRecordId })
            .toList()
        return Engine2ProvenanceReport(
            distinctProviderRecords = indexed.size,
            workoutRecordsWithSource = workoutsWithSources,
            sourceCollisions = collisions,
            runsWithoutProviderMeasurements = context.workouts.count {
                it.status == Engine2RecordStatus.CONFIRMED &&
                    it.discipline == Engine2Discipline.RUNNING && it.measurements.isEmpty()
            }
        )
    }
}
