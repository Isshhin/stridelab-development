package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class ConfirmedWorkout(val planId:String,val sessionId:String,val date:LocalDate,val discipline:String,val durationSeconds:Double?,val distanceM:Double?)
internal data class DailyTraining(val date:LocalDate,val sessions:Int,val durations:List<Double>)
internal data class TrainingWindow(val start: LocalDate,val end: LocalDate,val records: List<ActivityRecord>,val activeDays: Int,
    val durationSeconds: Double?,val missingDuration: Int,val runningDistanceM: Double?,val missingRunDistance: Int,val distribution: Map<String,Int>,val nativeLogs:List<ConfirmedWorkout> = emptyList(),val omittedUndatedLogs:Int=0) {
    val sessionCount get()=records.size+nativeLogs.size
    fun daily(zone:ZoneId)=(0L..6L).map { offset -> val date=start.plusDays(offset);val sources=records.filter { it.startedAt.atZone(zone).toLocalDate()==date };val native=nativeLogs.filter { it.date==date };DailyTraining(date,sources.size+native.size,sources.mapNotNull { it.durationS }+native.mapNotNull { it.durationSeconds }) }
}
internal object DashboardSummary {
    fun isRun(a: ActivityRecord) = a.type.lowercase(Locale.ROOT).replace(Regex("[ _-]"),"") in setOf("run","running","trailrun","trailrunning","virtualrun","treadmill")
    fun window(history: List<ActivityRecord>, now: Instant, zone: ZoneId, daysAgo: Int = 0, plans:List<PlanSnapshot> = emptyList()): TrainingWindow {
        val end=now.atZone(zone).toLocalDate().minusDays(daysAgo.toLong()); val start=end.minusDays(6)
        val records=history.distinctBy { it.id }.filter { it.startedAt<=now && it.startedAt.atZone(zone).toLocalDate() in start..end }
        val sources=history.map { it.id }.toSet()
        val confirmed=confirmedWorkouts(plans,history,now)
        val native=confirmed.filter { it.date in start..end }
        val undated=plans.flatMap { it.sessions }.count { it.status=="completed" && it.log!=null && it.log.performedOn==null && it.linkedActivityId !in sources }
        val durations=records.mapNotNull { it.durationS }+native.mapNotNull { it.durationSeconds }
        val runs=records.filter(::isRun); val nativeRuns=native.filter { it.discipline=="run" }; val distances=runs.mapNotNull { it.distanceM }+nativeRuns.mapNotNull { it.distanceM }
        fun group(a: ActivityRecord) = when {
            isRun(a) -> "Running"
            a.type.lowercase(Locale.ROOT) in setOf("strength","training","weighttraining","weight training") -> "Strength"
            a.type.contains("calisthenics",true) -> "Calisthenics"
            else -> a.type.ifBlank { "Other" }
        }
        val distribution=records.groupingBy(::group).eachCount().toMutableMap()
        native.groupingBy { when(it.discipline){"run"->"Running";"str"->"Strength";"cal"->"Calisthenics";"cond"->"Conditioning";else->it.discipline.ifBlank { "Other" }} }.eachCount().forEach { (key,count)->distribution[key]=(distribution[key] ?: 0)+count }
        return TrainingWindow(start,end,records,(records.map { it.startedAt.atZone(zone).toLocalDate() }+native.map { it.date }).distinct().size,
            durations.takeIf { it.isNotEmpty() }?.sum(),records.size+native.size-durations.size,distances.takeIf { it.isNotEmpty() }?.sum(),runs.size+nativeRuns.size-distances.size,distribution,native,undated)
    }
    /** Date-only confirmations remain date-only; no start time or sensor activity is invented. */
    fun confirmedWorkouts(plans:List<PlanSnapshot>,history:List<ActivityRecord>,now:Instant):List<ConfirmedWorkout> {
        val sourceIds=history.map { it.id }.toSet()
        return plans.flatMap { p -> p.sessions.mapNotNull { s ->
            val log=s.log;val date=log?.performedOn
            if(s.status!="completed" || log==null || date==null || log.at>now || s.linkedActivityId in sourceIds)null
            else ConfirmedWorkout(p.id,s.id,date,s.discipline,log.minutes?.takeIf { it.isFinite()&&it>=0 }?.times(60),log.distanceKm?.takeIf { it.isFinite()&&it>=0 }?.times(1000))
        } }.distinctBy { it.planId to it.sessionId }
    }
}
