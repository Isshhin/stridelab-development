package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Dedicated cloud snapshots; NEVER insert the offline source ID into public.activities UUIDs. */
internal data class GarminCloudBackupRow(
    val kind: String, val recordId: String, val payload: String
)

internal class GarminCloudBackupGateway(
    private val transport: BackendTransport = HttpsBackendTransport()
) {
    companion object {
        private const val TABLE = "/rest/v1/stridelab_garmin_backups"
        private const val MAX_PAYLOAD_BYTES = 2_500_000
        internal fun fitsUpload(payload: String): Boolean =
            payload.toByteArray(StandardCharsets.UTF_8).size <= MAX_PAYLOAD_BYTES
        internal fun sha256(payload: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        internal fun kind(kind: String) {
            require(kind == "activity" || kind == "activity_detail")
        }
    }

    private fun validate(owner: String, kind: String, id: String, payload: String) {
        kind(kind)
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        require(payload.toByteArray(StandardCharsets.UTF_8).size <= MAX_PAYLOAD_BYTES) {
            "Recording exceeds the current cloud-backup size limit"
        }
        val json = JSONObject(payload)
        require(json.getString("user_id") == owner) { "Garmin backup owner mismatch" }
        if (kind == "activity") {
            val record = ActivityRecord.parse(payload, owner)
            require(record.id == id && record.source == "garmin")
        } else {
            ActivityDetail.parse(json, owner, id)
        }
        // These fields belong exclusively in Android Keystore, never in snapshots.
        for (key in listOf("apiKey", "bearerToken", "access_token", "refresh_token")) {
            require(!json.has(key)) { "Credential fields are forbidden in cloud backup" }
        }
    }

    /** Idempotent, immutable insertion: no overwrites of earlier cloud snapshots. */
    fun upload(session: NativeSession, kind: String, id: String, payload: String) {
        validate(session.account, kind, id, payload)
        val body = JSONObject()
            .put("user_id", session.account)
            .put("record_kind", kind)
            .put("record_id", id)
            .put("payload_sha256", sha256(payload))
            .put("payload", JSONObject(payload))
        transport.executeWithPreference(
            TABLE + "?on_conflict=user_id,record_kind,record_id,payload_sha256",
            session.accessToken,
            body,
            "resolution=ignore-duplicates,return=minimal"
        )
    }

    /** Explicitly requested restoration only; RLS also isolates owner rows. */
    fun readPage(session: NativeSession, kind: String, offset: Int, limit: Int): List<GarminCloudBackupRow> {
        kind(kind)
        require(offset >= 0 && limit in 1..50)
        val owner = URLEncoder.encode(session.account, "UTF-8")
        val path = TABLE +
            "?select=user_id,record_kind,record_id,payload" +
            "&user_id=eq." + owner + "&record_kind=eq." + kind +
            "&order=created_at.desc,record_id.asc,payload_sha256.asc" +
            "&limit=" + limit + "&offset=" + offset
        val rows = JSONArray(transport.execute(path, session.accessToken, null))
        require(rows.length() <= limit)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            if (row.getString("user_id") != session.account ||
                row.getString("record_kind") != kind) {
                throw IOException("Cloud backup owner or type could not be verified")
            }
            val id = row.getString("record_id")
            val payload = row.getJSONObject("payload").toString()
            validate(session.account, kind, id, payload)
            GarminCloudBackupRow(kind, id, payload)
        }
    }
}
