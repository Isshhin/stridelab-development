package io.stridelab.privatealpha

import java.time.Duration
import kotlin.math.abs
import kotlin.math.max

/**
 * Read-only presentation grouping of high-confidence Garmin/Intervals.icu
 * representations of one recorded run. Source records remain separate in SQLite
 * and keep their own IDs, metrics, attachments and provenance.
 *
 * This is intentionally stricter than ActivityImportMatcher's manual-review
 * candidates, and never silently links/deletes source records or planned workouts.
 */
internal data class DisplayActivity(
    val primary: ActivityRecord,
    val additionalSources: List<ActivityRecord> = emptyList()
) {
    val sourceLabel:String get()=(listOf(primary) + additionalSources)
        .map { it.sourceLabel }.distinct().joinToString(" + ")
}

internal object ActivityDisplayConsolidation {
    /** Never classify two same-provider records as one event. */
    private fun confidentSameRun(intervals:ActivityRecord, garmin:ActivityRecord):Boolean {
        if(intervals.source!="intervals" || garmin.source!="garmin") return false
        if(!DashboardSummary.isRun(intervals) || !DashboardSummary.isRun(garmin)) return false
        val start=abs(Duration.between(intervals.startedAt,garmin.startedAt).seconds)
        if(start>45L) return false
        val a=intervals.durationS ?: return false
        val b=garmin.durationS ?: return false
        if(!a.isFinite() || !b.isFinite() || a<=0 || b<=0) return false
        if(abs(a-b)>max(30.0,max(a,b)*0.01)) return false
        val x=intervals.distanceM ?: return false
        val y=garmin.distanceM ?: return false
        if(!x.isFinite() || !y.isFinite() || x<=0 || y<=0) return false
        if(abs(x-y)>max(30.0,max(x,y)*0.005)) return false
        return true
    }

    fun groups(history:List<ActivityRecord>):List<DisplayActivity> {
        val rows=history.distinctBy { it.id }
        val intervals=rows.filter { it.source=="intervals" }
        val garmin=rows.filter { it.source=="garmin" }
        // Index start times to avoid an O(Garmin x Intervals) scan during
        // Compose recomposition when a user has many years of activity history.
        val intervalsBySecond=java.util.TreeMap<Long,MutableList<ActivityRecord>>()
        intervals.forEach { row ->
            intervalsBySecond.getOrPut(row.startedAt.epochSecond) { mutableListOf() }.add(row)
        }
        val forward=HashMap<String,List<String>>()
        val reverse=HashMap<String,MutableList<String>>()
        garmin.forEach { g ->
            val second=g.startedAt.epochSecond
            val possible=intervalsBySecond.subMap(second-46,true,second+46,true)
                .values.asSequence().flatMap { it.asSequence() }
                .filter { confidentSameRun(it,g) }.map { it.id }.toList()
            forward[g.id]=possible
            possible.forEach { id -> reverse.getOrPut(id) { mutableListOf() }.add(g.id) }
        }

        // Only unambiguous, one-to-one provider matches may be grouped.
        val partnered=forward.mapNotNull { (garminId,potential) ->
            val intervalsId=potential.singleOrNull() ?: return@mapNotNull null
            if(reverse[intervalsId]?.singleOrNull()!=garminId) return@mapNotNull null
            garminId to intervalsId
        }.toMap()
        val byId=rows.associateBy { it.id }
        val secondaryByPrimary=partnered.entries.groupBy({ it.value },{ it.key })
        return rows.asSequence().filterNot { it.id in partnered }.map { primary ->
            DisplayActivity(primary,secondaryByPrimary[primary.id].orEmpty()
                .mapNotNull(byId::get))
        }.sortedWith(compareByDescending<DisplayActivity> { it.primary.startedAt }
            .thenBy { it.primary.id }).toList()
    }

    /** Logical activity list for UI counts, volume summaries and history hints. */
    fun visible(history:List<ActivityRecord>):List<ActivityRecord> =
        groups(history).map { it.primary }
}
