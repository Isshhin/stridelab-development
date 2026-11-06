package io.stridelab.privatealpha

import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * One UI card per enabled source event. Retained cache records are not altered.
 * Independently imported activities remain separate without evidence they
 * represent the same original Garmin event.
 */
internal data class DisplayActivity(
    val primary: ActivityRecord,
    val additionalSources: List<ActivityRecord> = emptyList()
) {
    val sourceLabel: String get() = primary.sourceLabel
    val detailedSource: ActivityRecord get() = primary
}

internal object ActivityDisplayConsolidation {
    /**
     * Import pipelines may store one original Garmin run twice: a legacy
     * backend row and a newer source-owned Garmin row. Their UTC timestamps
     * and source metrics may differ slightly in rounding. Require several
     * independently agreeing fields rather than exact timestamp equality.
     *
     * This is PRESENTATION consolidation only. It does not delete entries,
     * alter source IDs, or combine records from separate providers.
     */
    private const val MAX_START_DIFFERENCE_SECONDS = 5 * 60L

    private fun sameGarminRun(a: ActivityRecord, b: ActivityRecord): Boolean {
        if (!a.source.equals("garmin", ignoreCase = true) ||
            !b.source.equals("garmin", ignoreCase = true)) return false
        if (!DashboardSummary.isRun(a) || !DashboardSummary.isRun(b)) return false
        if (abs(Duration.between(a.startedAt, b.startedAt).seconds) >
            MAX_START_DIFFERENCE_SECONDS) return false

        fun normalizedTitle(title: String): String =
            title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

        if (normalizedTitle(a.title) != normalizedTitle(b.title)) return false
        val aDuration = a.durationS?.takeIf { it.isFinite() && it > 0 } ?: return false
        val bDuration = b.durationS?.takeIf { it.isFinite() && it > 0 } ?: return false
        val aDistance = a.distanceM?.takeIf { it.isFinite() && it > 0 } ?: return false
        val bDistance = b.distanceM?.takeIf { it.isFinite() && it > 0 } ?: return false

        // Different Garmin summary versions may round elapsed time and metres
        // differently, but unrelated activities must not match by proximity.
        if (abs(aDuration - bDuration) > max(5.0, max(aDuration, bDuration) * .002))
            return false
        if (abs(aDistance - bDistance) > max(15.0, max(aDistance, bDistance) * .002))
            return false
        return true
    }

    fun groups(history: List<ActivityRecord>): List<DisplayActivity> {
        val ordered = ActiveActivitySources.filter(history)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<ActivityRecord> { it.startedAt }
                    .thenBy { it.id }
            )
        val consolidated = mutableListOf<MutableList<ActivityRecord>>()
        for (record in ordered) {
            // Records are descending by start time. Stop searching once an
            // earlier entry falls outside the possible duplicate window.
            val matches = consolidated.asReversed().asSequence()
                .takeWhile { group ->
                    Duration.between(record.startedAt, group.first().startedAt).seconds <=
                        MAX_START_DIFFERENCE_SECONDS
                }
                .filter { group -> sameGarminRun(group.first(), record) }
                .take(2)
                .toList()
            // Ambiguity must not silently suppress a potentially real run.
            if (matches.size == 1) matches.single().add(record)
            else consolidated.add(mutableListOf(record))
        }
        return consolidated.map { records ->
            // Prefer a source-owned Garmin ID with preserved provider metadata;
            // other original IDs remain intact as alternative source records.
            val canonical = records.firstOrNull {
                it.source.equals("garmin", ignoreCase = true) &&
                    it.id.matches(Regex("garmin-[0-9a-f]{64}"))
            } ?: records.first()
            DisplayActivity(canonical, records.filter { it.id != canonical.id })
        }
    }

    fun visible(history: List<ActivityRecord>): List<ActivityRecord> =
        groups(history).map { it.primary }
}
