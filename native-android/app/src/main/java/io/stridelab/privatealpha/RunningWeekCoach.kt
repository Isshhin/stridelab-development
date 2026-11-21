package io.stridelab.privatealpha

import java.time.LocalDate
import java.util.Locale

internal enum class WeeklyRunType(val label: String) {
    EASY("Easy / recovery"), LONG("Long run"), QUALITY("Quality"), OTHER("Other"), UNKNOWN("Unclassified")
}

internal data class RunningWeekCoach(
    val week: TodayDisciplineWeek,
    val types: Map<WeeklyRunType, Int>,
    val qualityTypes: Map<String, Int>,
    val longestMeters: Double?
) {
    val mostPerformed: WeeklyRunType?
        get() {
            val known = types.filterKeys { it != WeeklyRunType.UNKNOWN }
            val top = known.values.maxOrNull() ?: return null
            return known.filterValues { it == top }.keys.singleOrNull()
        }
    val completeDistance: Double?
        get() = week.recordedDistanceM?.takeIf { week.missingDistance == 0 }
}

internal fun summarizeRunningWeek(
    week: TodayDisciplineWeek,
    evidence: (ActivityRecord) -> UserActivityWorkoutLabel?
): RunningWeekCoach {
    val labels = week.sourceActivities.mapNotNull(evidence)
    val counts = labels.groupingBy {
        when (it.kind) {
            ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.RECOVERY_RUN -> WeeklyRunType.EASY
            ActivityWorkoutKind.LONG_RUN -> WeeklyRunType.LONG
            ActivityWorkoutKind.QUALITY_RUN -> WeeklyRunType.QUALITY
            ActivityWorkoutKind.MIXED_SESSION -> WeeklyRunType.OTHER
            ActivityWorkoutKind.UNKNOWN -> WeeklyRunType.UNKNOWN
        }
    }.eachCount().toMutableMap()
    val unknown = week.sessions - counts.filterKeys { it != WeeklyRunType.UNKNOWN }.values.sum()
    if (unknown > 0) counts[WeeklyRunType.UNKNOWN] = unknown
    val quality = labels.filter { it.kind == ActivityWorkoutKind.QUALITY_RUN }
        .groupingBy { it.qualitySubtype?.label ?: "Quality run" }.eachCount()
    val longest = (week.sourceActivities.mapNotNull { it.distanceM } +
        week.nativeWorkouts.mapNotNull { it.distanceM }).filter { it.isFinite() && it > 0 }.maxOrNull()
    return RunningWeekCoach(week, counts, quality, longest)
}

internal fun runningWeekVolumeLabel(
    current: RunningWeekCoach,
    previous: RunningWeekCoach?,
    today: LocalDate
): String? {
    if (!current.week.start.plusDays(6).isBefore(today)) return null
    val now = current.completeDistance ?: return null
    val before = previous?.completeDistance?.takeIf { it > 0 } ?: return null
    return when {
        now > before * 1.10 -> "Increasing"
        now < before * .90 -> "Reduced volume"
        else -> "Consistent"
    }
}

internal fun runningPlanWeekLabel(start: LocalDate, plans: List<PlanSnapshot>): String? {
    val end = start.plusDays(6)
    val active = plans.filter { it.status in setOf("approved", "paused") }
    val plan = active.singleOrNull { candidate ->
        candidate.sessions.any { it.discipline == "run" && it.date in start..end }
    } ?: return null
    val runs = plan.sessions.filter { it.discipline == "run" && it.status != "unassigned" }
    val selected = runs.filter { it.date in start..end }
    if (selected.any { it.kind.equals("race", true) }) return "Plan · Race"
    if (selected.isEmpty()) return "StrideLab plan"
    if (plan.engineVersion == ENGINE1_VERSION && plan.policyId == ENGINE1_POLICY &&
        plan.foreignSnapshot == null && plan.request.start.dayOfWeek == start.dayOfWeek &&
        !start.isBefore(plan.request.start)
    ) {
        val index = java.time.temporal.ChronoUnit.WEEKS.between(plan.request.start, start).toInt()
        if (index < plan.request.weeks) {
            val taper = plan.request.goal in setOf("Half marathon", "Marathon") &&
                plan.request.weeks >= 8 && index >= plan.request.weeks - 2
            when {
                taper -> return "Plan · Taper"
                index % 4 == 3 && plan.request.weeklyKm > 0 -> return "Plan · Deload"
                index < 2 && plan.request.returning -> return "Plan · Recovery"
                index == 0 -> return "Plan · Base"
            }
        }
    }
    val past = runs.filter { it.date in start.minusWeeks(1)..start.minusDays(1) }
    if (past.isEmpty() || (selected + past).any { it.distanceKm == null }) return "StrideLab plan"
    val currentKm = selected.sumOf { it.distanceKm!! }
    val priorKm = past.sumOf { it.distanceKm!! }
    return when {
        currentKm > priorKm * 1.10 -> "Plan volume · Build"
        currentKm < priorKm * .90 -> "Plan volume · Lighter"
        else -> "Plan volume · Steady"
    }
}

internal fun runningKm(meters: Double): String =
    String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)

internal fun runningPercentChange(latest: Double, earlier: Double): Double? =
    if (earlier > 0) ((latest - earlier) / earlier * 100).takeIf { it.isFinite() } else null

internal fun runningPercentLabel(value: Double): String =
    if (kotlin.math.abs(value) < .5) "0%" else String.format(Locale.getDefault(), "%+.0f%%", value)
