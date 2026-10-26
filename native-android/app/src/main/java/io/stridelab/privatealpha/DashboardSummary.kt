package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class TrainingWindow(val start: LocalDate,val end: LocalDate,val records: List<ActivityRecord>,val activeDays: Int,
    val durationSeconds: Double?,val missingDuration: Int,val runningDistanceM: Double?,val missingRunDistance: Int,val distribution: Map<String,Int>)
internal object DashboardSummary {
    fun isRun(a: ActivityRecord) = a.type.lowercase(Locale.ROOT).replace(Regex("[ _-]"),"") in setOf("run","running","trailrun","trailrunning","virtualrun","treadmill")
    fun window(history: List<ActivityRecord>, now: Instant, zone: ZoneId, daysAgo: Int = 0): TrainingWindow {
        val end=now.atZone(zone).toLocalDate().minusDays(daysAgo.toLong()); val start=end.minusDays(6)
        val records=history.distinctBy { it.id }.filter { it.startedAt<=now && it.startedAt.atZone(zone).toLocalDate() in start..end }
        val durations=records.mapNotNull { it.durationS }
        val runs=records.filter(::isRun); val distances=runs.mapNotNull { it.distanceM }
        fun group(a: ActivityRecord) = when {
            isRun(a) -> "Running"
            a.type.lowercase(Locale.ROOT) in setOf("strength","training","weighttraining","weight training") -> "Strength"
            a.type.contains("calisthenics",true) -> "Calisthenics"
            else -> a.type.ifBlank { "Other" }
        }
        return TrainingWindow(start,end,records,records.map { it.startedAt.atZone(zone).toLocalDate() }.distinct().size,
            durations.takeIf { it.isNotEmpty() }?.sum(),records.size-durations.size,distances.takeIf { it.isNotEmpty() }?.sum(),runs.size-distances.size,records.groupingBy(::group).eachCount())
    }
}
