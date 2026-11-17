package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Derived from the active owner's cached activities; recorded totals are not complete training totals. */
internal data class ActivityAthleteEvidenceProfile(
    val version: Int = VERSION,
    val owner: String,
    val asOf: Instant,
    val recordedRunCount: Int,
    val recordedDistanceByWeekKm: Map<LocalDate, Double>,
    val recordedRunDaysByWeek: Map<LocalDate, Int>,
    val longestRecordedRunLast28DaysKm: Double?,
    val userReportedRunCharacteristics: Map<ActivityWorkoutKind, ActivityRunCharacteristics>,
    val evidenceActivityIds: List<String>,
    val provenance: String = "OWNER_SCOPED_ACTIVITY_CACHE",
    val sourceCoverage: String = "UNKNOWN"
) {
    init {
        require(version == VERSION)
        require(owner.isNotBlank() && provenance == "OWNER_SCOPED_ACTIVITY_CACHE")
        require(sourceCoverage == "UNKNOWN")
        require(evidenceActivityIds.distinct() == evidenceActivityIds)
    }

    companion object { const val VERSION = 1 }
}

internal data class ActivityRunCharacteristics(
    val activityCount: Int,
    val medianRecordedPaceSecondsPerKm: Double?,
    val medianRecordedAverageHr: Double?,
    val sourceActivityIds: List<String>
)

internal object ActivityAthleteEvidence {
    fun profile(
        owner: String,
        activities: List<ActivityRecord>,
        allowedActivityIds: Set<String>,
        userLabels: Map<String, UserActivityWorkoutLabel>,
        asOf: Instant,
        historyWeeks: Long = 16
    ): ActivityAthleteEvidenceProfile {
        require(owner.isNotBlank() && historyWeeks in 1..52)
        val start = asOf.minusSeconds(historyWeeks * 7 * 86400)
        val visible = ActivityDisplayConsolidation.groups(activities)
            .map { it.primary }
            .filter { it.id in allowedActivityIds && DashboardSummary.isRun(it) &&
                !it.startedAt.isAfter(asOf) && !it.startedAt.isBefore(start) }
            .sortedBy { it.startedAt }
        fun week(activity: ActivityRecord): LocalDate = activity.startedAt.atZone(ZoneOffset.UTC)
            .toLocalDate().with(java.time.DayOfWeek.MONDAY)
        val distances = visible.mapNotNull { row ->
            row.distanceM?.takeIf { it.isFinite() && it > 0 }?.let { week(row) to it / 1000.0 }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.sum() }
        val days = visible.groupBy(::week).mapValues { (_, rows) ->
            rows.map { it.startedAt.atZone(ZoneOffset.UTC).toLocalDate() }.distinct().size
        }
        val longRun = visible.filter { !it.startedAt.isBefore(asOf.minusSeconds(28 * 86400)) }
            .mapNotNull { it.distanceM?.takeIf { distance -> distance.isFinite() && distance > 0 } }
            .maxOrNull()?.div(1000.0)
        val characteristics = visible.groupBy { userLabels[it.id]?.kind }
            .filterKeys { it == ActivityWorkoutKind.EASY_RUN || it == ActivityWorkoutKind.RECOVERY_RUN }
            .mapKeys { it.key!! }
            .mapValues { (_, rows) ->
                ActivityRunCharacteristics(
                    rows.size,
                    median(rows.mapNotNull { it.recordedPace?.takeIf(Double::isFinite) }),
                    median(rows.mapNotNull { it.averageHr?.takeIf(Double::isFinite) }),
                    rows.map { it.id }.sorted()
                )
            }
        return ActivityAthleteEvidenceProfile(
            owner = owner,
            asOf = asOf,
            recordedRunCount = visible.size,
            recordedDistanceByWeekKm = distances,
            recordedRunDaysByWeek = days,
            longestRecordedRunLast28DaysKm = longRun,
            userReportedRunCharacteristics = characteristics,
            evidenceActivityIds = visible.map { it.id }.sorted()
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
