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

/**
 * Suggested baseline from recent *completed calendar weeks* with recorded running.
 *
 * No evidence of a run on a day is not evidence of rest. A week with fewer
 * than three distinct runs is not used; the result is still an estimate and
 * can be overridden by the athlete. These are imported/saved actual distances,
 * never planned targets. Current incomplete week is excluded.
 */
internal data class AutomaticRunningBaseline(
    val weeklyKm: Double,
    val weeks: List<ObservedRunningWeek>
)

internal fun automaticRunningBaseline(
    history: List<ActivityRecord>,
    now: Instant = Instant.now(),
    plans: List<PlanSnapshot> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault()
): AutomaticRunningBaseline? {
    val today=now.atZone(zone).toLocalDate()
    val eligible=observedRunningWeeks(history,now,plans,zone)
        .filter { week ->
            week.start.plusDays(6)<today &&
                week.runs>=3 &&
                week.distanceKm?.let { it.isFinite() && it>0.0 }==true
        }
        .sortedByDescending { it.start }
        .take(3)
    if(eligible.size<2)return null
    val weekly=eligible.sumOf { requireNotNull(it.distanceKm) }/eligible.size
    if(!weekly.isFinite() || weekly<=0.0)return null
    val rounded=kotlin.math.round(weekly*10.0)/10.0
    return AutomaticRunningBaseline(rounded,eligible)
}
