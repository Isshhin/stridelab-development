package io.stridelab.privatealpha

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Locally cached Garmin summary data, not a synthetic workout or a server sync.
 * Stable provider-scoped IDs ensure that Intervals.icu and other source records
 * are not overwritten or silently cross-provider merged.
 */
internal object GarminHistoryImport {
    fun recordId(externalId:String):String {
        require(externalId.isNotBlank() && externalId.length<=512)
        val hash=MessageDigest.getInstance("SHA-256")
            .digest(externalId.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return "garmin-$hash"
    }

    fun payload(input:WearableActivityInput):JSONObject {
        require(input.provider=="garmin") { "Only Garmin source data belongs in this importer" }
        val id=recordId(input.externalId)
        // Garmin averageSpeed is a source measurement in meters/second.
        // Convert only for pace-bearing sports; NEVER substitute stopped/elapsed
        // time for moving time or call a calculated moving pace "average pace".
        val paceSport=input.activityType.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[ _-]"),"") in setOf(
                "run","running","trailrun","trailrunning","virtualrun","treadmill",
                "walk","walking","hike","hiking")
        val recordedPace=if(paceSport) input.averageSpeedMetersPerSecond
            ?.takeIf { it.isFinite() && it>0 }?.let { 1000.0/it } else null
        val raw=JSONObject()
            .put("id",id)
            .put("user_id",input.accountId)
            .put("source","garmin")
            .put("external_id",input.externalId)
            .put("title",input.title?.takeIf { it.isNotBlank() } ?: "Garmin activity")
            .put("activity_type",input.activityType)
            .put("started_at",input.startedAt.toString())
            .put("distance_m",input.distanceMeters ?: JSONObject.NULL)
            .put("duration_s",input.durationSeconds ?: JSONObject.NULL)
            .put("moving_time_s",input.movingDurationSeconds ?: JSONObject.NULL)
            .put("avg_hr",input.averageHeartRate ?: JSONObject.NULL)
            .put("avg_pace_sec_per_km",recordedPace ?: JSONObject.NULL)
        // Validate using the actual canonical Activity tab parser before a DB write.
        ActivityRecord.parse(raw.toString(),input.accountId)
        return raw
    }
}
