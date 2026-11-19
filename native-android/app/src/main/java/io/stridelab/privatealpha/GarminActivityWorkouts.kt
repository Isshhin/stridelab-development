package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

/**
 * Garmin's read-only activity workout reference. Its steps describe a
 * workout associated with a recording; only actual FIT laps verify execution.
 */
internal data class GarminActivityWorkoutReference(
    val name: String?,
    val steps: List<GarminActivityWorkoutStep>
)
internal data class GarminActivityWorkoutStep(
    val order: Int?,
    val name: String?,
    val intensity: String?
)

internal object GarminActivityWorkouts {
    fun parse(body: String): List<GarminActivityWorkoutReference> {
        if (body.isBlank() || body.length > 256_000)
            throw GarminProtocolException("Garmin returned empty or oversized activity workouts")
        val rows = try { JSONArray(body) } catch (_: Exception) {
            throw GarminProtocolException("Garmin returned unsupported activity workout data")
        }
        if (rows.length() > 8)
            throw GarminProtocolException("Garmin returned too many activity workouts")
        return (0 until rows.length()).mapNotNull { index ->
            val workout = rows.optJSONObject(index) ?: return@mapNotNull null
            val steps = workout.optJSONArray("steps") ?: workout.optJSONArray("workoutSteps")
            if (steps != null && steps.length() > 200)
                throw GarminProtocolException("Garmin returned too many workout steps")
            val normalized = (0 until (steps?.length() ?: 0)).mapNotNull { i ->
                val row = steps!!.optJSONObject(i) ?: return@mapNotNull null
                val order = when {
                    row.has("stepOrder") && !row.isNull("stepOrder") ->
                        row.optInt("stepOrder", -1)
                    row.has("index") && !row.isNull("index") ->
                        row.optInt("index", -1)
                    else -> -1
                }.takeIf { it in 0..999 }
                val name = row.optString("stepName").takeIf {
                    it.isNotBlank() && it.length <= 128
                }
                val intensity = row.optJSONObject("intensityType")
                    ?.optString("intensityTypeKey")
                    ?.takeIf { it.isNotBlank() && it.length <= 48 }
                    ?: row.optString("intensity").takeIf {
                        it.isNotBlank() && it.length <= 48
                    }
                GarminActivityWorkoutStep(order, name, intensity)
            }
            val name = workout.optString("workoutName")
                .takeIf { it.isNotBlank() && it.length <= 128 }
            GarminActivityWorkoutReference(name, normalized)
        }
    }
}
