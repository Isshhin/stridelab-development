package io.stridelab.privatealpha

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Display-only choice: Garmin's actual BPM floors or StrideLab's existing manual calculation. */
internal enum class DisplayHeartRateZoneSource { MANUAL, GARMIN }

internal data class CachedGarminHeartRateZones(
    val profile: GarminHeartRateZoneProfile,
    val syncedAtMillis: Long
)

/**
 * Source profiles and choice are device-local and owner-isolated.
 * No Garmin keys/bearers or raw HR stream data are written here.
 * A failed sync cannot erase a previously verified cache or change source choice.
 */
internal class GarminZonePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "garmin-zone-preferences-v1", Context.MODE_PRIVATE
    )

    private fun prefix(owner: String?): String {
        val scope = owner?.takeIf { it.isNotBlank() } ?: "device"
        require(scope.length <= 256)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return "zones:$digest:"
    }

    fun source(owner: String?): DisplayHeartRateZoneSource =
        if (preferences.getString(prefix(owner) + "source", null) == "garmin")
            DisplayHeartRateZoneSource.GARMIN else DisplayHeartRateZoneSource.MANUAL

    fun select(owner: String?, source: DisplayHeartRateZoneSource): Boolean =
        preferences.edit().putString(prefix(owner) + "source",
            source.name.lowercase(java.util.Locale.ROOT)).commit()

    fun load(owner: String?): CachedGarminHeartRateZones? = runCatching {
        val raw = preferences.getString(prefix(owner) + "cached", null) ?: return null
        val json = JSONObject(raw)
        require(json.getInt("version") == 1)
        val syncedAt = json.getLong("synced_at")
        require(syncedAt > 0L)
        val floors = json.getJSONArray("floors")
        require(floors.length() == 5)
        val profile = GarminHeartRateZoneProfile(
            json.getString("sport"),
            (0 until 5).map(floors::getInt),
            json.optString("method").takeIf { it.isNotBlank() }
        )
        CachedGarminHeartRateZones(profile, syncedAt)
    }.getOrNull()

    fun save(owner: String, profile: GarminHeartRateZoneProfile,
             syncedAtMillis: Long = System.currentTimeMillis()): Boolean {
        require(owner.isNotBlank() && syncedAtMillis > 0)
        require(profile.sport.equals("RUNNING", true) ||
            profile.sport.equals("DEFAULT", true))
        val json = JSONObject()
            .put("version", 1)
            .put("sport", profile.sport)
            .put("floors", JSONArray(profile.floorsBpm))
            .put("method", profile.trainingMethod ?: JSONObject.NULL)
            .put("synced_at", syncedAtMillis)
        return preferences.edit().putString(prefix(owner) + "cached", json.toString()).commit()
    }

    /** Explicit user action; Garmin and StrideLab's manual settings are independent. */
    fun clearCache(owner: String?): Boolean =
        preferences.edit().remove(prefix(owner) + "cached").commit()
}
