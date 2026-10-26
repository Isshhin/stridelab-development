package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** SI values are retained; absence is never converted to zero or a recovery score. */
data class ActivityRecord(
    val id: String, val title: String, val type: String, val source: String,
    val startedAt: Instant, val distanceM: Double?, val durationS: Double?,
    val movingTimeS: Double?, val recordedPace: Double?, val averageHr: Double?, val elevationM: Double?
) {
    val dateLabel: String get() = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm").withZone(ZoneId.systemDefault()).format(startedAt)
    val distanceLabel: String get() = distanceM?.let { String.format(Locale.getDefault(), "%.2f km", it / 1000) } ?: "Not recorded"
    val durationLabel: String get() = durationS?.let { duration(it) } ?: "Not recorded"
    // Do not infer running pace from elapsed time: stopped time and discipline can make it misleading.
    val paceLabel: String get() = recordedPace?.let { "${duration(it)} /km" } ?: "Not recorded"
    companion object {
        fun duration(seconds: Double): String {
            val total = kotlin.math.round(seconds).toLong()
            return if (total >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
            else String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
        }
        fun parse(raw: String, account: String): ActivityRecord {
            val j = JSONObject(raw)
            require(j.getString("user_id") == account) { "Activity owner does not match" }
            require(j.getString("source") != "demo") { "Demonstration activities are excluded" }
            fun metric(key: String, zeroValid: Boolean = true): Double? {
                if (j.isNull(key)) return null
                val n = j.getDouble(key)
                require(n.isFinite() && if (zeroValid) n >= 0 else n > 0) { "Invalid $key" }
                return n
            }
            val id = j.getString("id"); RecordPolicy.identifier(id)
            return ActivityRecord(id, j.optString("title").ifBlank { "Untitled activity" }, j.getString("activity_type"),
                j.getString("source"), Instant.parse(j.getString("started_at")), metric("distance_m"), metric("duration_s"),
                metric("moving_time_s"), metric("avg_pace_sec_per_km", false), metric("avg_hr", false), metric("elevation_gain_m"))
        }
    }
}
