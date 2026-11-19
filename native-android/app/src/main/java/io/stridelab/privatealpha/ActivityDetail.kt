package io.stridelab.privatealpha

import org.json.JSONObject

internal data class ActivitySample(val index: Int, val seconds: Double, val pace: Double?, val hr: Double?, val altitude: Double?, val latitude: Double?, val longitude: Double?, val cadenceSpm: Double? = null)
internal data class ActivityLap(
    val index: Int,
    val distance: Double?,
    val seconds: Double?,
    val pace: Double?,
    val hr: Double?,
    val startSeconds: Double? = null,
    val workoutStepIndex: Int? = null
)
internal data class ActivityWorkoutStep(
    val index: Int,
    val name: String?,
    val intensity: String?,
    val durationType: String?,
    val durationValue: Long?,
    val targetType: String?
) {
    val role: String? get() = when (intensity?.lowercase(java.util.Locale.ROOT)) {
        "rest", "recovery" -> "RECOVERY"
        "warmup" -> "WARM_UP"
        "cooldown" -> "COOL_DOWN"
        "active" -> "WORK"
        else -> null
    }
}
internal data class ActivityPauseInterval(val startSeconds: Double, val endSeconds: Double)
internal data class ActivitySourceHeartRateZones(
    val maximumHr: Int?,
    val thresholdHr: Int?,
    val restingHr: Int?,
    val calculationType: String?,
    val highBpms: List<Int>
)
internal data class ActivityDetail(
    val activityId: String,
    val samples: List<ActivitySample>,
    val laps: List<ActivityLap>,
    val fetchedAt: Long,
    val pauseIntervals: List<ActivityPauseInterval> = emptyList(),
    val sourceHeartRateZones: ActivitySourceHeartRateZones? = null,
    val workoutSteps: List<ActivityWorkoutStep> = emptyList(),
    val sourceWorkoutReferences: List<GarminActivityWorkoutReference> = emptyList()
) {
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
                ActivityLap(
                    row.getInt("lap_index").also { require(it>=0) },
                    metric(row,"distance_m"),
                    metric(row,"duration_s"),
                    metric(row,"avg_pace_sec_per_km",.001),
                    metric(row,"avg_hr",.001),
                    metric(row,"start_s"),
                    if (row.isNull("workout_step_index")) null
                    else row.getInt("workout_step_index").also { require(it in 0..999) }
                )
            }
            require(splits.zipWithNext().all { (a,b) -> b.index > a.index })
            val pauses = raw.optJSONArray("pause_intervals")?.let { array ->
                (0 until array.length()).map { i ->
                    val row = array.getJSONObject(i)
                    val start = row.getDouble("start_s")
                    val end = row.getDouble("end_s")
                    require(start.isFinite() && end.isFinite() && start >= 0.0 && end > start) {
                        "Invalid pause interval"
                    }
                    ActivityPauseInterval(start, end)
                }
            }.orEmpty()
            require(pauses.zipWithNext().all { (a,b) ->
                b.startSeconds >= a.endSeconds
            }) { "Overlapping pause intervals" }

            val sourceZones = raw.optJSONObject("source_hr_zones")?.let { zones ->
                fun optionalBpm(key: String, range: IntRange): Int? =
                    if (zones.isNull(key)) null
                    else zones.getInt(key).also { require(it in range) }
                val highs = zones.optJSONArray("high_bpms")?.let { array ->
                    (0 until array.length()).map { i ->
                        array.getInt(i).also { require(it in 30..255) }
                    }
                }.orEmpty()
                require(highs.zipWithNext().all { (a,b) -> b >= a }) {
                    "Heart-rate zone boundaries must be ordered"
                }
                ActivitySourceHeartRateZones(
                    maximumHr = optionalBpm("maximum_hr", 100..240),
                    thresholdHr = optionalBpm("threshold_hr", 100..230),
                    restingHr = optionalBpm("resting_hr", 30..120),
                    calculationType = zones.optString("calculation_type")
                        .takeIf { it.isNotBlank() },
                    highBpms = highs
                )
            }
            val stepArray = raw.optJSONArray("workout_steps")
            require(stepArray == null || stepArray.length() <= 200)
            val steps = (0 until (stepArray?.length() ?: 0)).map { index ->
                val row = stepArray!!.getJSONObject(index)
                fun shortText(key: String): String? = row.optString(key).trim()
                    .takeIf { it.isNotEmpty() && it != "null" && it.length <= 128 }
                ActivityWorkoutStep(
                    index = row.getInt("index").also { require(it in 0..999) },
                    name = shortText("name"),
                    intensity = shortText("intensity"),
                    durationType = shortText("duration_type"),
                    durationValue = if (row.isNull("duration_value")) null
                        else row.getLong("duration_value").also { require(it in 0..100_000_000L) },
                    targetType = shortText("target_type")
                )
            }
            require(steps.map { it.index }.distinct().size == steps.size)
            return ActivityDetail(
                id, points, splits, raw.getLong("fetched_at"),
                pauseIntervals = pauses,
                sourceHeartRateZones = sourceZones,
                workoutSteps = steps,
                sourceWorkoutReferences = raw.optJSONArray("garmin_workouts")?.let {
                    GarminActivityWorkouts.parse(it.toString())
                }.orEmpty()
            )
        }
    }
}
