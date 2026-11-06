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
    /**
     * Historical Garmin imports can coexist with older backend Garmin rows
     * under different local record IDs. Collapse only an exact-time,
     * same-provider running event whose distance and duration agree.
     * No records are deleted, no cross-provider rows are merged, and the
     * additional original IDs remain available for source traceability.
     */
    private fun sameGarminRun(a:ActivityRecord,b:ActivityRecord):Boolean {
        if(!a.source.equals("garmin",ignoreCase=true) ||
            !b.source.equals("garmin",ignoreCase=true))return false
        if(!DashboardSummary.isRun(a) || !DashboardSummary.isRun(b))return false
        if(a.startedAt!=b.startedAt)return false
        if(!a.title.trim().equals(b.title.trim(),ignoreCase=true))return false
        val aDuration=a.durationS ?: return false
        val bDuration=b.durationS ?: return false
        val aDistance=a.distanceM ?: return false
        val bDistance=b.distanceM ?: return false
        return kotlin.math.abs(aDuration-bDuration)<=2.0 &&
            kotlin.math.abs(aDistance-bDistance)<=10.0
    }

    fun groups(history:List<ActivityRecord>):List<DisplayActivity> {
        val ordered=ActiveActivitySources.filter(history)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenBy { it.id })
        val buckets=LinkedHashMap<Pair<String,java.time.Instant>,MutableList<MutableList<ActivityRecord>>>()
        for(record in ordered) {
            val key=record.source.lowercase(java.util.Locale.ROOT) to record.startedAt
            val groups=buckets.getOrPut(key) { mutableListOf() }
            val related=groups.firstOrNull { sameGarminRun(it.first(),record) }
            if(related!=null)related.add(record)
            else groups.add(mutableListOf(record))
        }
        return buckets.values.flatMap { clusters ->
            clusters.map { records ->
                // The local provider-owned hash ID is backed by the original
                // Garmin external_id / numericActivityId metadata for FIT.
                val canonical=records.firstOrNull {
                    it.source.equals("garmin",ignoreCase=true) &&
                        it.id.matches(Regex("garmin-[0-9a-f]{64}"))
                } ?: records.first()
                DisplayActivity(canonical,records.filter { it.id!=canonical.id })
            }
        }
    }

    fun visible(history:List<ActivityRecord>):List<ActivityRecord> =
        groups(history).map { it.primary }
}
