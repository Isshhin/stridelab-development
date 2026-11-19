package io.stridelab.privatealpha

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Recorded running-history aggregation only. Never interprets training benefit,
 * invents missing distance, substitutes moving time for elapsed time or mutates a plan.
 */
internal enum class ActivityAnalyticsPeriod(val buckets: Int) {
    WEEK(8), MONTH(12), YEAR(5);

    fun start(date: LocalDate): LocalDate = when (this) {
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }

    fun previous(start: LocalDate, count: Long): LocalDate = when (this) {
        WEEK -> start.minusWeeks(count)
        MONTH -> start.minusMonths(count)
        YEAR -> start.minusYears(count)
    }
}

internal data class ActivityAnalyticsBucket(
    val start: LocalDate,
    val activityCount: Int,
    val recordedDistanceCount: Int,
    val recordedDistanceM: Double?,
    val recordedElapsedCount: Int,
    val recordedElapsedS: Double?
)

/**
 * Only the consolidated, visible owner-scoped activity summaries should be supplied.
 * Time boundaries use the selected local zone, not UTC calendar dates.
 */
internal object ActivityHistoryAnalytics {
    private val runningTypes = setOf(
        "run", "running", "trailrun", "trailrunning", "treadmill", "virtualrun"
    )

    fun series(
        activities: List<ActivityRecord>,
        now: Instant,
        zone: ZoneId,
        period: ActivityAnalyticsPeriod
    ): List<ActivityAnalyticsBucket> {
        val current = period.start(now.atZone(zone).toLocalDate())
        val starts = (period.buckets - 1 downTo 0).map { period.previous(current, it.toLong()) }
        val eligible = activities.asSequence()
            .filter { it.startedAt <= now }
            .filter {
                it.type.lowercase(Locale.ROOT)
                    .replace(Regex("[ _-]"), "") in runningTypes
            }
            // Suppress exact repeated summaries, but do not collapse unrelated providers.
            .distinctBy { it.source to it.id }
            .groupBy { period.start(it.startedAt.atZone(zone).toLocalDate()) }

        return starts.map { start ->
            val rows = eligible[start].orEmpty()
            val distances = rows.mapNotNull { it.distanceM?.takeIf { value -> value.isFinite() && value >= 0.0 } }
            val elapsed = rows.mapNotNull { it.durationS?.takeIf { value -> value.isFinite() && value >= 0.0 } }
            ActivityAnalyticsBucket(
                start = start,
                activityCount = rows.size,
                recordedDistanceCount = distances.size,
                recordedDistanceM = distances.takeIf { it.isNotEmpty() }?.sum(),
                recordedElapsedCount = elapsed.size,
                recordedElapsedS = elapsed.takeIf { it.isNotEmpty() }?.sum()
            )
        }
    }
}
