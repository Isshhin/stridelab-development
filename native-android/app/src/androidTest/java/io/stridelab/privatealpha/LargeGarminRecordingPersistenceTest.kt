package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Local-only SQLite regression: a genuine long recording must not be rejected at 2 MB. */
@RunWith(AndroidJUnit4::class)
class LargeGarminRecordingPersistenceTest {
    @Test fun longFitSampleStreamsSurviveSQLiteReopenWithoutTruncation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = "large-garmin-recording-test.db"
        val owner = "synthetic-local-profile"
        val id = "garmin-" + "a".repeat(64)
        val samples = JSONArray()
        repeat(12_000) { index ->
            samples.put(JSONObject()
                .put("activity_id", id)
                .put("sample_index", index)
                .put("elapsed_s", index.toDouble())
                .put("pace_sec_per_km", 399.55)
                .put("heart_rate", 143.0)
                .put("altitude_m", 27.15)
                .put("cadence_spm", 162.0)
                .put("latitude", 14.6012345)
                .put("longitude", 121.0123456))
        }
        val payload = JSONObject()
            .put("user_id", owner)
            .put("activity_id", id)
            .put("fetched_at", 1_731_000_000_000L)
            .put("samples", samples)
            .put("laps", JSONArray())
            .toString()

        assertTrue(payload.toByteArray(Charsets.UTF_8).size > RecordPolicy.MAX_PAYLOAD_BYTES)
        assertEquals(12_000, ActivityDetail.parse(JSONObject(payload), owner, id).samples.size)
        context.deleteDatabase(database)
        try {
            TrainingStore(context, database).use { store ->
                store.applyRemotePage(
                    owner, "activity_detail",
                    listOf(TrainingStore.RemoteRecord(id, payload, null)),
                    null, System.currentTimeMillis()
                )
            }
            TrainingStore(context, database).use { reopened ->
                val saved = requireNotNull(reopened.get(owner, "activity_detail", id))
                val detail = ActivityDetail.parse(JSONObject(saved.payload), owner, id)
                assertEquals(12_000, detail.samples.size)
                assertEquals(14.6012345, detail.samples.last().latitude!!, 0.0000001)
                assertEquals(121.0123456, detail.samples.last().longitude!!, 0.0000001)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }
}
