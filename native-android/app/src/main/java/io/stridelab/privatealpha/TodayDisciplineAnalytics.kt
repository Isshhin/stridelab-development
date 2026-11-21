package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Presentation-only Today dashboard. No invented volume, pace, intensity,
 * load, targets or recovery scores. Every displayed value comes from a
 * visible source event or a dated, unlinked, user-confirmed workout.
 */
internal enum class TodayDiscipline(val key: String, val title: String) {
    RUNNING("running", "Running"),
    STRENGTH("strength", "Strength"),
    CALISTHENICS("calisthenics", "Calisthenics"),
    CONDITIONING("conditioning", "Conditioning");

    companion object {
        fun fromActivity(type: String): TodayDiscipline? {
            val normalized = type.lowercase(Locale.ROOT)
                .replace(Regex("[ _-]"), "")
            return when {
                normalized in setOf("run", "running", "trailrun", "trailrunning",
                    "treadmill", "virtualrun") -> RUNNING
                normalized.contains("calisthenic") || normalized.contains("bodyweight") ->
                    CALISTHENICS
                normalized.contains("strength") || normalized.contains("weighttrain") ||
                    normalized.contains("weightlift") || normalized == "gym" ->
                    STRENGTH
                normalized.contains("conditioning") || normalized.contains("hiit") ||
                    normalized.contains("circuit") -> CONDITIONING
                else -> null // e.g. walks and generic "training" are not inferred.
            }
        }

        fun fromLog(code: String): TodayDiscipline? = when (
            code.lowercase(Locale.ROOT)
        ) {
            "run", "running" -> RUNNING
            "str", "strength" -> STRENGTH
            "cal", "calisthenics", "body", "bodyweight" -> CALISTHENICS
            "cond", "conditioning" -> CONDITIONING
            else -> null
        }
    }
}

internal data class TodayDisciplineWeek(
    val start: LocalDate,
    val sessionsByDay: List<Int>,
    val secondsByDay: List<Double>,
    val distanceByDay: List<Double>,
    val sessions: Int,
    val activeDays: Int,
    val recordedSeconds: Double?,
    val recordedDistanceM: Double?,
    val missingDuration: Int,
    val missingDistance: Int,
    val averageRecordedHr: Double?,
    val hrCoverage: Int,
    val sourceActivities: List<ActivityRecord>,
    val nativeWorkouts: List<ConfirmedWorkout>
)

/** Pure, device-local aggregation. Eligible tabs require 3 DISTINCT training
 * dates in EACH of the two most recently completed Monday-Sunday weeks.
 * The still-in-progress current week never prematurely unlocks a discipline.
 */
internal object TodayDisciplineAnalytics {
    fun monday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun available(
        history: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        now: Instant,
        zone: ZoneId
    ): List<TodayDiscipline> {
        val currentMonday = monday(now.atZone(zone).toLocalDate())
        val last = week(history, plans, now, zone, currentMonday.minusWeeks(1))
        val previous = week(history, plans, now, zone, currentMonday.minusWeeks(2))
        return TodayDiscipline.entries.filter { discipline ->
            (last[discipline]?.activeDays ?: 0) >= 3 &&
                (previous[discipline]?.activeDays ?: 0) >= 3
        }
    }

    /** Single preparation pass for the four discipline pages of a selected week. */
    fun week(
        history: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        now: Instant,
        zone: ZoneId,
        weekStart: LocalDate
    ): Map<TodayDiscipline, TodayDisciplineWeek> {
        val start = monday(weekStart)
        val end = start.plusDays(6)
        val visible = ActivityDisplayConsolidation.visible(history).filter {
            !it.startedAt.isAfter(now) && it.startedAt.atZone(zone).toLocalDate() in start..end
        }
        val confirmed = DashboardSummary.confirmedWorkouts(plans, history, now)
            .filter { it.date in start..end && !it.date.isAfter(now.atZone(zone).toLocalDate()) }
        return TodayDiscipline.entries.associateWith { discipline ->
            val activities = visible.filter {
                TodayDiscipline.fromActivity(it.type) == discipline
            }
            val native = confirmed.filter {
                TodayDiscipline.fromLog(it.discipline) == discipline
            }
            val allDates = activities.map { it.startedAt.atZone(zone).toLocalDate() } +
                native.map { it.date }
            val durations = activities.mapNotNull { it.durationS?.takeIf { v -> v.isFinite() && v >= 0 } } +
                native.mapNotNull { it.durationSeconds?.takeIf { v -> v.isFinite() && v >= 0 } }
            val distances = activities.mapNotNull { it.distanceM?.takeIf { v -> v.isFinite() && v >= 0 } } +
                native.mapNotNull { it.distanceM?.takeIf { v -> v.isFinite() && v >= 0 } }
            val hr = activities.mapNotNull { it.averageHr?.takeIf { v -> v.isFinite() && v > 0 } }
            TodayDisciplineWeek(
                start = start,
                sessionsByDay = (0L..6L).map { offset ->
                    allDates.count { it == start.plusDays(offset) }
                },
                secondsByDay = (0L..6L).map { offset ->
                    val date = start.plusDays(offset)
                    activities.filter { it.startedAt.atZone(zone).toLocalDate() == date }
                        .mapNotNull { it.durationS?.takeIf { v -> v.isFinite() && v >= 0 } }.sum() +
                        native.filter { it.date == date }.mapNotNull {
                            it.durationSeconds?.takeIf { v -> v.isFinite() && v >= 0 }
                        }.sum()
                },
                distanceByDay = (0L..6L).map { offset ->
                    val date = start.plusDays(offset)
                    activities.filter { it.startedAt.atZone(zone).toLocalDate() == date }
                        .mapNotNull { it.distanceM?.takeIf { v -> v.isFinite() && v >= 0 } }.sum() +
                        native.filter { it.date == date }.mapNotNull {
                            it.distanceM?.takeIf { v -> v.isFinite() && v >= 0 }
                        }.sum()
                },
                sessions = activities.size + native.size,
                activeDays = allDates.distinct().size,
                recordedSeconds = durations.takeIf { it.isNotEmpty() }?.sum(),
                recordedDistanceM = distances.takeIf { it.isNotEmpty() }?.sum(),
                missingDuration = activities.size + native.size - durations.size,
                missingDistance = activities.size + native.size - distances.size,
                averageRecordedHr = hr.takeIf { it.isNotEmpty() }?.average(),
                hrCoverage = hr.size,
                sourceActivities = activities.sortedByDescending { it.startedAt },
                nativeWorkouts = native.sortedByDescending { it.date }
            )
        }
    }

    fun trend(
        history: List<ActivityRecord>,
        plans: List<PlanSnapshot>,
        now: Instant,
        zone: ZoneId,
        selectedWeek: LocalDate,
        discipline: TodayDiscipline
    ): List<TodayDisciplineWeek> =
        ((if (discipline == TodayDiscipline.RUNNING) 7 else 3) downTo 0).map { weeksAgo ->
            week(history, plans, now, zone, monday(selectedWeek).minusWeeks(weeksAgo.toLong()))
                .getValue(discipline)
        }
}
