package io.stridelab.privatealpha

/**
 * One UI card per currently enabled source activity. Intervals.icu was retired
 * from the Android integration; do not pair its old offline records with Garmin.
 * Historical source records remain in SQLite without deletion.
 */
internal data class DisplayActivity(
    val primary:ActivityRecord,
    val additionalSources:List<ActivityRecord> = emptyList()
) {
    val sourceLabel:String get()=primary.sourceLabel
    val detailedSource:ActivityRecord get()=primary
}

internal object ActivityDisplayConsolidation {
    fun groups(history:List<ActivityRecord>):List<DisplayActivity> =
        ActiveActivitySources.filter(history)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }
                .thenBy { it.id })
            .map { DisplayActivity(it) }

    fun visible(history:List<ActivityRecord>):List<ActivityRecord> =
        groups(history).map { it.primary }
}
