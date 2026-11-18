package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.abs

/**
 * Read-only comparison of two recorded running summaries.
 * Neither source is edited, and a pace change is never called a fitness change.
 */
internal data class RecordedRunPair(
    val recent: ActivityRecord,
    val earlier: ActivityRecord,
    val recordedPaceChangePercent: Double?,
    val recordedHeartRateDifferenceBpm: Double?
)

internal object ActivityRunComparison {
    private val types = setOf("run", "running", "trailrun", "trailrunning", "treadmill", "virtualrun")
    private fun kind(record: ActivityRecord) =
        record.type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")

    private fun valid(record: ActivityRecord): Boolean =
        kind(record) in types &&
            record.distanceM?.let { it.isFinite() && it >= 1000.0 } == true

    fun recentRuns(history: List<ActivityRecord>): List<ActivityRecord> =
        history.asSequence().filter(::valid)
            .distinctBy { it.source to it.id }
            .sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenBy { it.id })
            .toList()

    private fun matching(recent: ActivityRecord, earlier: ActivityRecord): Boolean {
        if (!valid(recent) || !valid(earlier)) return false
        if (recent.source != earlier.source || kind(recent) != kind(earlier)) return false
        if (recent.id == earlier.id || earlier.startedAt >= recent.startedAt) return false
        // Same-source, same-sport and similar recorded distance do not establish
        // equivalent effort, terrain, sensor accuracy, or environmental conditions.
        return abs(recent.distanceM!! - earlier.distanceM!!) / recent.distanceM <= .10
    }

    fun earlierCandidates(recent: ActivityRecord, history: List<ActivityRecord>): List<ActivityRecord> =
        recentRuns(history).filter { matching(recent, it) }

    fun compare(recent: ActivityRecord, earlier: ActivityRecord): RecordedRunPair? {
        if (!matching(recent, earlier)) return null
        val recentPace = recent.recordedPace?.takeIf { it.isFinite() && it > 0.0 }
        val earlierPace = earlier.recordedPace?.takeIf { it.isFinite() && it > 0.0 }
        val recentHeartRate = recent.averageHr?.takeIf { it.isFinite() && it > 0.0 }
        val earlierHeartRate = earlier.averageHr?.takeIf { it.isFinite() && it > 0.0 }
        return RecordedRunPair(
            recent, earlier,
            if (recentPace != null && earlierPace != null)
                (recentPace / earlierPace - 1.0) * 100.0 else null,
            if (recentHeartRate != null && earlierHeartRate != null)
                recentHeartRate - earlierHeartRate else null
        )
    }
}
