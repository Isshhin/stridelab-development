package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/**
 * M3 Batch 1: source-aware, read-only running-history evidence for the four
 * fully elapsed ISO calendar weeks before the current local week.
 *
 * This is NOT an athlete-capacity score, quality eligibility decision,
 * prescription, or a claim of complete training history. A weekly mileage
 * total is confirmed only if an external source explicitly attests that its
 * coverage is complete and every recorded run has a known valid distance.
 */
internal data class IndependentRunningWeekEvidence(
    val monday: LocalDate,
    val coverageConfirmed: Boolean,
    val recordedRunCount: Int,
    val recordedDays: Int,
    val knownDistanceKm: Double,
    val runsWithoutDistance: Int,
    val longestKnownRunKm: Double?,
    val ambiguousSourceIds: Int,
    val confirmedWeeklyKm: Double?
) {
    init {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        require(recordedRunCount >= 0 && recordedDays >= 0 &&
            runsWithoutDistance >= 0 && ambiguousSourceIds >= 0)
        require(knownDistanceKm.isFinite() && knownDistanceKm >= 0.0)
        require(longestKnownRunKm == null ||
            longestKnownRunKm.isFinite() && longestKnownRunKm >= 0.0)
        require(confirmedWeeklyKm == null || (coverageConfirmed &&
            runsWithoutDistance == 0 && ambiguousSourceIds == 0 &&
            confirmedWeeklyKm == knownDistanceKm))
    }
}

internal data class IndependentRunningHistoryEvidence(
    val completedWeeks: List<IndependentRunningWeekEvidence>
) {
    init {
        require(completedWeeks.size == 4)
        require(completedWeeks.zipWithNext().all { (a, b) -> a.monday.plusWeeks(1) == b.monday })
    }

    val completeWeekCount: Int get() = completedWeeks.count { it.confirmedWeeklyKm != null }
    /** A partial known-distance sum is NEVER treated as a complete week. */
    val confirmedFourWeekMeanKm: Double? get() =
        completedWeeks.map { it.confirmedWeeklyKm }.takeIf { list -> list.all { it != null } }
            ?.filterNotNull()?.average()
    val confirmedRecentTwoWeekMeanKm: Double? get() =
        completedWeeks.takeLast(2).map { it.confirmedWeeklyKm }
            .takeIf { list -> list.all { it != null } }?.filterNotNull()?.average()
    val confirmedMeanRunsPerWeek: Double? get() =
        completedWeeks.takeIf { weeks -> weeks.all { it.confirmedWeeklyKm != null } }
            ?.map { it.recordedRunCount }?.average()
    val confirmedWeeklyDistanceRangeKm: Double? get() =
        completedWeeks.map { it.confirmedWeeklyKm }.takeIf { list -> list.all { it != null } }
            ?.filterNotNull()?.let { it.maxOrNull()!! - it.minOrNull()!! }
}

/**
 * Caller-supplied confirmedCompleteMondays MUST be backed by source coverage
 * evidence. Presence of any number of activities does not establish that a
 * week was fully captured. Distinct provider IDs are NOT silently merged on
 * similar time/distance; that requires a future provenance reconciliation.
 *
 * Duplicate identical IDs are counted once; incompatible records sharing
 * an ID are excluded and mark their weeks uncertain. No recorded HR, RPE,
 * quality-work minutes, or fitness score is inferred from summary mileage.
 */
internal object IndependentRunningHistory {
    fun summarize(
        history: List<ActivityRecord>,
        asOf: LocalDate,
        zone: ZoneId,
        confirmedCompleteMondays: Set<LocalDate> = emptySet()
    ): IndependentRunningHistoryEvidence {
        val currentMonday = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mondays = (4 downTo 1).map { currentMonday.minusWeeks(it.toLong()) }
        require(confirmedCompleteMondays.all { it in mondays }) {
            "Only the four elapsed week starts may be marked complete"
        }
        val first = mondays.first()
        val candidates = ActiveActivitySources.filter(history)
            .filter(DashboardSummary::isRun)
            .filter { record ->
                val date = record.startedAt.atZone(zone).toLocalDate()
                date >= first && date < currentMonday
            }
        require(candidates.all { record ->
            record.id.isNotBlank() &&
                (record.distanceM == null || record.distanceM.isFinite() && record.distanceM >= 0.0)
        }) { "Invalid source running evidence" }

        val accepted = mutableListOf<ActivityRecord>()
        val conflictingDates = mutableListOf<LocalDate>()
        for ((_, records) in candidates.groupBy { it.id }) {
            if (records.distinct().size == 1) accepted += records.first()
            else conflictingDates += records.map {
                it.startedAt.atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }.distinct()
        }
        val weeks = mondays.map { monday ->
            val runs = accepted.filter { record ->
                val date = record.startedAt.atZone(zone).toLocalDate()
                date >= monday && date < monday.plusWeeks(1)
            }
            val distance = runs.mapNotNull { it.distanceM }
            val known = distance.sum() / 1000.0
            require(known.isFinite()) { "Running distance total overflow" }
            val missing = runs.size - distance.size
            val ambiguous = conflictingDates.count { it == monday }
            val complete = monday in confirmedCompleteMondays
            IndependentRunningWeekEvidence(
                monday = monday,
                coverageConfirmed = complete,
                recordedRunCount = runs.size,
                recordedDays = runs.map { it.startedAt.atZone(zone).toLocalDate() }.distinct().size,
                knownDistanceKm = known,
                runsWithoutDistance = missing,
                longestKnownRunKm = distance.maxOrNull()?.div(1000.0),
                ambiguousSourceIds = ambiguous,
                confirmedWeeklyKm = if (complete && missing == 0 && ambiguous == 0) known else null
            )
        }
        return IndependentRunningHistoryEvidence(weeks)
    }
}
