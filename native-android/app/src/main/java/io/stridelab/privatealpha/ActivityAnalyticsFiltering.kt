package io.stridelab.privatealpha

import java.util.Locale

/** Filters only recorded activity summaries already visible to the current owner. */
internal enum class ActivityAnalyticsKind(val label: String) {
    ALL("All runs"), ROAD("Road"), TRAIL("Trail"), TREADMILL("Treadmill")
}
internal enum class ActivityAnalyticsDistance(val label: String) {
    ALL("Any distance"), SHORT("Under 5 km"), MID("5–10 km"), LONG("10 km+")
}
internal data class ActivityAnalyticsFilter(
    val source: String? = null,
    val kind: ActivityAnalyticsKind = ActivityAnalyticsKind.ALL,
    val distance: ActivityAnalyticsDistance = ActivityAnalyticsDistance.ALL
)

internal object ActivityAnalyticsFiltering {
    private fun normalized(record: ActivityRecord) =
        record.type.lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")

    private fun runKind(record: ActivityRecord): ActivityAnalyticsKind? = when (normalized(record)) {
        "run", "running" -> ActivityAnalyticsKind.ROAD
        "trailrun", "trailrunning" -> ActivityAnalyticsKind.TRAIL
        "treadmill", "virtualrun" -> ActivityAnalyticsKind.TREADMILL
        else -> null
    }

    fun availableSources(activities: List<ActivityRecord>): List<String> =
        activities.asSequence().filter { runKind(it) != null }.map { it.source }
            .distinct().sorted().toList()

    fun apply(activities: List<ActivityRecord>, filter: ActivityAnalyticsFilter): List<ActivityRecord> =
        activities.filter { record ->
            val kind = runKind(record) ?: return@filter false
            if (filter.source != null && record.source != filter.source) return@filter false
            if (filter.kind != ActivityAnalyticsKind.ALL && kind != filter.kind) return@filter false
            if (filter.distance == ActivityAnalyticsDistance.ALL) return@filter true
            val meters = record.distanceM?.takeIf { it.isFinite() && it >= 0.0 }
                ?: return@filter false
            when (filter.distance) {
                ActivityAnalyticsDistance.ALL -> true
                ActivityAnalyticsDistance.SHORT -> meters < 5000.0
                ActivityAnalyticsDistance.MID -> meters in 5000.0..<10000.0
                ActivityAnalyticsDistance.LONG -> meters >= 10000.0
            }
        }
}
