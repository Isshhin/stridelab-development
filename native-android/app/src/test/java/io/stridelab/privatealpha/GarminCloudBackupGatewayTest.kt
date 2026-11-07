package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Source and authentication boundary tests; no network, credentials or real data. */
class GarminCloudBackupGatewayTest {
    private val owner = "00000000-0000-0000-0000-000000000001"
    private val other = "00000000-0000-0000-0000-000000000002"
    private val id = "garmin-" + "a".repeat(64)
    private val session = NativeSession(owner, "test-access", "test-refresh", 2000000000L)

    private fun payload(user: String = owner, source: String = "garmin"): String =
        JSONObject()
            .put("id", id)
            .put("user_id", user)
            .put("source", source)
            .put("activity_type", "running")
            .put("title", "Recorded Run")
            .put("started_at", "2026-11-07T01:00:00Z")
            .put("distance_m", 5000.0)
            .put("duration_s", 1800.0)
            .toString()

    @Test fun uploadIsImmutableSourceOwnedAndNeverSendsCredentials() {
        var attempted = false
        val transport = object : BackendTransport {
            override fun execute(path: String, token: String?, body: JSONObject?): String =
                throw AssertionError("Upload must use conflict-safe method")
            override fun executeWithPreference(
                path: String, token: String?, body: JSONObject?, preference: String
            ): String {
                attempted = true
                assertEquals("test-access", token)
                assertEquals("resolution=ignore-duplicates,return=minimal", preference)
                assertTrue(path.contains("on_conflict=user_id,record_kind,record_id,payload_sha256"))
                val row = body!!
                assertEquals(owner, row.getString("user_id"))
                assertEquals("activity", row.getString("record_kind"))
                assertEquals(id, row.getString("record_id"))
                assertEquals(GarminCloudBackupGateway.sha256(payload()),
                    row.getString("payload_sha256"))
                assertEquals(id, row.getJSONObject("payload").getString("id"))
                assertFalse(row.toString().contains("test-refresh"))
                assertFalse(row.toString().contains("test-access"))
                return ""
            }
        }
        GarminCloudBackupGateway(transport).upload(session, "activity", id, payload())
        assertTrue(attempted)
    }

    @Test fun rejectsOtherOwnersAndNonGarminSourcesBeforeTransport() {
        val transport = BackendTransport { _, _, _ ->
            throw AssertionError("Forbidden source reached transport")
        }
        val client = GarminCloudBackupGateway(transport)
        assertFails<IllegalArgumentException> {
            client.upload(session, "activity", id, payload(user = other))
        }
        assertFails<IllegalArgumentException> {
            client.upload(session, "activity", id, payload(source = "manual"))
        }
    }

    @Test fun restoreRejectsCrossAccountRowsEvenWithValidLookingPayload() {
        val row = JSONObject()
            .put("user_id", other)
            .put("record_kind", "activity")
            .put("record_id", id)
            .put("payload", JSONObject(payload(user = other)))
        val transport = BackendTransport { _, _, _ -> JSONArray().put(row).toString() }
        val client = GarminCloudBackupGateway(transport)
        assertFails<java.io.IOException> {
            client.readPage(session, "activity", 0, 10)
        }
    }

    @Test fun overLimitRecordingIsNeverEligibleForCloudUpload() {
        assertFalse(GarminCloudBackupGateway.fitsUpload("x".repeat(2_500_001)))
        assertTrue(GarminCloudBackupGateway.fitsUpload(payload()))
    }

    private inline fun <reified T: Throwable> assertFails(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            if (error is T) return
            throw error
        }
        fail("Expected " + T::class.java.simpleName)
    }
}
