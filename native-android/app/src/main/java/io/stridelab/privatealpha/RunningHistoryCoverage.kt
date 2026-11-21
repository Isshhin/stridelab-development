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

/**
 * Automatically infers the athlete's running experience tier (0 = Beginner, 1 = Intermediate, 2 = Advanced)
 * from recorded running history (weekly volume, longest run, weekly run frequency, and sustained run pace).
 * Returns null only when no recorded running history is present.
 */
internal fun automaticRunningExperience(
    history: List<ActivityRecord>,
    plans: List<PlanSnapshot> = emptyList(),
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Int? {
    val runs = ActivityDisplayConsolidation.visible(history)
        .filter { DashboardSummary.isRun(it) && !it.startedAt.isAfter(now) }
    val confirmedRuns = DashboardSummary.confirmedWorkouts(plans, history, now)
        .filter { it.discipline == "run" }
    if (runs.isEmpty() && confirmedRuns.isEmpty()) return null

    val recentWeeks = observedRunningWeeks(history, now, plans, zone)
    val activeWeeks = recentWeeks.filter { it.runs > 0 }
    val avgWeeklyKm = automaticRunningBaseline(history, now, plans, zone)?.weeklyKm
        ?: activeWeeks.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.average()
        ?: 0.0
    val avgRunsPerWeek = activeWeeks.takeIf { it.isNotEmpty() }?.map { it.runs }?.average() ?: 0.0
    val longestKm = (runs.mapNotNull { it.distanceM } + confirmedRuns.mapNotNull { it.distanceM })
        .filter { it.isFinite() && it > 0.0 }
        .maxOrNull()?.div(1000.0) ?: 0.0
    val fastestSustainedPace = runs.mapNotNull { r ->
        val distM = r.distanceM?.takeIf { it.isFinite() && it >= 5_000.0 } ?: return@mapNotNull null
        val pace = (r.recordedPace ?: r.garminMovingPaceSPerKm ?: r.durationS?.let { it * 1000.0 / distM })
            ?.takeIf { it.isFinite() && it in 150.0..600.0 }
        pace
    }.minOrNull()

    return when {
        avgWeeklyKm >= 48.0 ||
            (avgWeeklyKm >= 34.0 && longestKm >= 16.0 && avgRunsPerWeek >= 3.5) ||
            longestKm >= 21.0 ||
            (fastestSustainedPace != null && fastestSustainedPace <= 265.0 && avgWeeklyKm >= 25.0) -> 2
        avgWeeklyKm >= 20.0 ||
            longestKm >= 10.0 ||
            avgRunsPerWeek >= 2.8 ||
            (fastestSustainedPace != null && fastestSustainedPace <= 330.0) -> 1
        else -> 0
    }
}
