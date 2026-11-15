package io.stridelab.privatealpha
import java.time.*

internal data class ObservedRunningWeek(val start:LocalDate,val distanceKm:Double?,val runs:Int)
internal fun observedRunningWeeks(history:List<ActivityRecord>,now:Instant=Instant.now(),plans:List<PlanSnapshot> = emptyList(),zone:ZoneId=ZoneId.systemDefault()):List<ObservedRunningWeek> {
    val monday=now.atZone(zone).toLocalDate().with(DayOfWeek.MONDAY)
    val provider=ActivityDisplayConsolidation.visible(history).filter { DashboardSummary.isRun(it) && it.startedAt<=now }.map { it.startedAt.atZone(zone).toLocalDate() to it.distanceM }
    val native=DashboardSummary.confirmedWorkouts(plans,history,now).filter { it.discipline=="run" }.map { it.date to it.distanceM }
    return (0L..4L).map { back ->
        val start=monday.minusWeeks(back);val rows=(provider+native).filter { it.first in start..start.plusDays(6) }
        ObservedRunningWeek(start,if(rows.any { it.second==null || !it.second!!.isFinite() || it.second!!<0 })null else rows.sumOf { it.second!! }/1000,rows.size)
    }
}
internal fun historyWeeklyKm(history:List<ActivityRecord>,now:Instant=Instant.now(),plans:List<PlanSnapshot> = emptyList(),zone:ZoneId=ZoneId.systemDefault(),confirmedWeeks:Set<LocalDate> = emptySet()):Double? {
    if(confirmedWeeks.isEmpty())return null
    val today=now.atZone(zone).toLocalDate()
    val selected=observedRunningWeeks(history,now,plans,zone).filter { it.start in confirmedWeeks && it.start.plusDays(6)<today }
    if(selected.size!=confirmedWeeks.size || selected.any { it.distanceKm==null })return null
    return selected.sumOf { it.distanceKm!! }/selected.size
}
