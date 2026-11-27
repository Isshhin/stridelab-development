package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

internal data class WellnessRecord(
    val id: String,
    val type: String,
    val value: Double,
    val unit: String,
    val recordedAt: Instant,
    val source: String,
    val metadata: JSONObject? = null
) {
    fun encode(account: String): JSONObject = JSONObject().apply {
        put("id", id)
        put("user_id", account)
        put("metric_type", type)
        put("value", value)
        put("unit", unit)
        put("recorded_at", recordedAt.toString())
        put("source", source)
        metadata?.let { put("metadata", it) }
    }

    companion object {
        fun canonicalId(source: String, metricType: String, date: LocalDate): String =
            "$source-wellness-$metricType-$date"

        fun parse(row: JSONObject, account: String): WellnessRecord? {
            require(row.getString("user_id") == account)
            val type = row.getString("metric_type")
            val expected = mapOf(
                "resting_hr" to "bpm",
                "hrv" to "ms",
                "hrv_sdnn" to "ms",
                "sleep_duration" to "hours",
                "steps" to "steps"
            )
            val unit = expected[type] ?: return null
            require(row.getString("unit") == unit)
            val value = row.getDouble("value")
            require(value.isFinite() && value >= 0)
            if (type in setOf("resting_hr", "hrv", "hrv_sdnn")) require(value > 0)
            if (type == "sleep_duration") require(value <= 24)
            val source = row.getString("source")
            require(source in setOf("intervals", "health_connect", "manual", "garmin"))
            val id = row.getString("id")
            RecordPolicy.identifier(id)
            return WellnessRecord(
                id = id,
                type = type,
                value = value,
                unit = unit,
                recordedAt = Instant.parse(row.getString("recorded_at")),
                source = source,
                metadata = row.optJSONObject("metadata")
            )
        }
    }
}
