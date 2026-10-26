package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Instant

/** Only non-secret connection state is retained; never API key material or backend error text. */
internal data class IntervalConnection(val configured: Boolean, val status: String, val lastProviderSync: Instant?, val checkedAt: Long) {
    fun encode(): String = JSONObject().put("configured",configured).put("syncStatus",status)
        .put("lastSyncedAt",lastProviderSync?.toString() ?: JSONObject.NULL).put("checkedAt",checkedAt).toString()
    companion object {
        fun parse(j: JSONObject, now: Long): IntervalConnection {
            require(j.get("configured") is Boolean)
            val configured = j.getBoolean("configured")
            val status = if(!configured) "disconnected" else j.getString("syncStatus")
            require(status in setOf("connected","disconnected","error","syncing"))
            val last = if(j.isNull("lastSyncedAt")) null else Instant.parse(j.getString("lastSyncedAt"))
            return IntervalConnection(configured,status,last,now)
        }
    }
}
