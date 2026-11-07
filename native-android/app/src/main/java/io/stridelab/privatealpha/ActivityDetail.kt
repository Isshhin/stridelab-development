package io.stridelab.privatealpha

import org.json.JSONObject

internal data class ActivitySample(val index: Int, val seconds: Double, val pace: Double?, val hr: Double?, val altitude: Double?, val latitude: Double?, val longitude: Double?, val cadenceSpm: Double? = null)
internal data class ActivityLap(val index: Int, val distance: Double?, val seconds: Double?, val pace: Double?, val hr: Double?)
internal data class ActivityDetail(val activityId: String, val samples: List<ActivitySample>, val laps: List<ActivityLap>, val fetchedAt: Long) {
    companion object {
        fun parse(raw: JSONObject, account: String, id: String): ActivityDetail {
            require(raw.getString("user_id") == account && raw.getString("activity_id") == id)
            fun metric(row: JSONObject, key: String, minimum: Double = 0.0): Double? {
                if(row.isNull(key)) return null
                val value = row.getDouble(key)
                require(value.isFinite() && value >= minimum) { "Invalid sample metric" }
                return value
            }
            val samples = raw.getJSONArray("samples")
            require(samples.length() <= 20000)
            val points = (0 until samples.length()).map { i ->
                val row = samples.getJSONObject(i)
                require(row.getString("activity_id") == id)
                val index = row.getInt("sample_index"); require(index >= 0)
                fun coordinate(key: String, limit: Double): Double? {
                    if(row.isNull(key)) return null
                    return row.getDouble(key).also { require(it.isFinite() && it in -limit..limit) }
                }
                ActivitySample(index, metric(row,"elapsed_s")!!, metric(row,"pace_sec_per_km",.001), metric(row,"heart_rate",.001),
                    metric(row,"altitude_m",-12000.0), coordinate("latitude",90.0),coordinate("longitude",180.0),metric(row,"cadence_spm"))
            }
            require(points.zipWithNext().all { (a,b) -> b.index > a.index && b.seconds >= a.seconds })
            val laps = raw.getJSONArray("laps"); require(laps.length() <= 1000)
            val splits = (0 until laps.length()).map { i ->
                val row = laps.getJSONObject(i); require(row.getString("activity_id") == id)
                ActivityLap(row.getInt("lap_index").also { require(it>=0) },metric(row,"distance_m"),metric(row,"duration_s"),metric(row,"avg_pace_sec_per_km",.001),metric(row,"avg_hr",.001))
            }
            require(splits.zipWithNext().all { (a,b) -> b.index > a.index })
            return ActivityDetail(id,points,splits,raw.getLong("fetched_at"))
        }
    }
}
