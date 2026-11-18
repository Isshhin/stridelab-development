package io.stridelab.privatealpha

import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * Distance-matched activity summaries, not certified race personal records.
 * No interpolation of splits, moving-time calculation or invented pace data.
 */
internal data class RecordedDistanceBest(
    val targetMeters: Double,
    val activity: ActivityRecord
)

internal object RecordedDistanceBests {
    val distanceTargets = listOf(
        "5 km" to 5000.0,
        "10 km" to 10000.0,
        "Half marathon" to 21097.5,
        "Marathon" to 42195.0
    )
    private val running = setOf("run","running","trailrun","trailrunning","treadmill","virtualrun")

    fun find(history: List<ActivityRecord>, targetMeters: Double,
        asOf: Instant = Instant.now()): RecordedDistanceBest? {
        require(targetMeters > 0.0 && targetMeters.isFinite())
        val best = history.asSequence()
            .filter { it.startedAt <= asOf }
            .filter {
                it.type.lowercase(Locale.ROOT)
                    .replace(Regex("[ _-]"), "") in running
            }
            .distinctBy { it.source to it.id }
            .filter {
                it.distanceM?.let { distance ->
                    distance.isFinite() && distance >= 1000.0 &&
                        abs(distance - targetMeters) / targetMeters <= 0.02
                } == true
            }
            .filter { it.recordedPace?.let { pace -> pace.isFinite() && pace > 0.0 } == true }
            .minWithOrNull(compareBy<ActivityRecord> { it.recordedPace!! }
                .thenBy { it.startedAt }.thenBy { it.id })
        return best?.let { RecordedDistanceBest(targetMeters, it) }
    }
}
