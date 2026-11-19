package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The five actual BPM floors returned for a Garmin sport profile.
 * These are measurements of user-defined settings, not percentages inferred
 * from StrideLab's max HR, and never modify coaching engine prescriptions.
 */
internal data class GarminHeartRateZoneProfile(
    val sport: String,
    val floorsBpm: List<Int>,
    val trainingMethod: String? = null,
    val maxHeartRateUsed: Int? = null
) {
    init {
        require(sport.isNotBlank())
        require(floorsBpm.size == 5)
        require(floorsBpm.all { it in 30..255 })
        require(floorsBpm.zipWithNext().all { (a, b) -> b > a })
        require(maxHeartRateUsed == null ||
            maxHeartRateUsed in floorsBpm.last()..255)
    }

    /** The lower limits of zones 2 through 5, for five ordered buckets. */
    val upperTransitionsBpm: List<Int> get() = floorsBpm.drop(1)
}

internal object GarminHeartRateZones {
    /**
     * Parse only documented BPM floor fields from Garmin's read-only
     * biometric-service/heartRateZones/v2 response. An unknown schema is not
     * an invitation to fabricate zone boundaries from max HR.
     */
    fun parse(body: String): List<GarminHeartRateZoneProfile> {
        if (body.length > 128_000 || body.isBlank())
            throw GarminProtocolException("Garmin returned missing or oversized heart-rate zones")
        val array = try {
            when (body.trimStart().first()) {
                '[' -> JSONArray(body)
                '{' -> {
                    val obj = JSONObject(body)
                    obj.optJSONArray("heartRateZones") ?: obj.optJSONArray("zones")
                        ?: throw GarminProtocolException("Garmin heart-rate zones have an unsupported format")
                }
                else -> throw GarminProtocolException("Garmin heart-rate zones have an unsupported format")
            }
        } catch (error: GarminException) {
            throw error
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin returned malformed heart-rate zones")
        }
        if (array.length() > 32) throw GarminProtocolException("Garmin returned too many heart-rate profiles")
        val result = mutableListOf<GarminHeartRateZoneProfile>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val sport = row.optString("sport").trim().uppercase(Locale.ROOT)
            if (sport.isBlank()) continue
            val values = (1..5).map { zone ->
                val field = "zone${zone}Floor"
                val raw = row.opt(field)
                when (raw) {
                    is Number -> raw.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toInt()
                    else -> null
                }
            }
            if (values.any { it == null }) continue
            val floors = values.filterNotNull()
            if (floors.any { it !in 30..255 } ||
                floors.zipWithNext().any { (a, b) -> b <= a }) continue
            val maxHeartRateUsed = row.opt("maxHeartRateUsed").let { value ->
                if (value is Number && value.toDouble().isFinite() &&
                    value.toDouble() % 1.0 == 0.0) value.toInt() else null
            }
            if (maxHeartRateUsed != null && maxHeartRateUsed !in floors.last()..255) continue
            result += GarminHeartRateZoneProfile(
                sport, floors,
                row.optString("trainingMethod").trim().takeIf {
                    it.isNotEmpty() && it != "null"
                },
                maxHeartRateUsed
            )
        }
        return result
    }

    /** Never silently use cycling zones as running zones. Conflicts are unresolved. */
    fun forRunning(profiles: List<GarminHeartRateZoneProfile>): GarminHeartRateZoneProfile? {
        fun unique(sport: String): GarminHeartRateZoneProfile? {
            val matching = profiles.filter { it.sport.equals(sport, ignoreCase = true) }
            return matching.firstOrNull()?.takeIf { candidate ->
                matching.all { it.floorsBpm == candidate.floorsBpm }
            }
        }
        val running = profiles.filter { it.sport.equals("RUNNING", ignoreCase = true) }
        if (running.isNotEmpty()) return unique("RUNNING")
        return unique("DEFAULT")
    }
}
