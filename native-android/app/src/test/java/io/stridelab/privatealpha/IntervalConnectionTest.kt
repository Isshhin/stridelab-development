package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IntervalConnectionTest {
    @Test fun keepsOnlyPublicConnectionStateAndNormalizesTimestamp() {
        val value = JSONObject().put("configured",true).put("syncStatus","connected").put("lastSyncedAt","2026-10-26T08:00:00+08:00").put("apiKey","DO_NOT_PERSIST")
        val connection = IntervalConnection.parse(value,1234)
        assertEquals("2026-10-26T00:00:00Z",connection.lastProviderSync.toString()); assertEquals(1234,connection.checkedAt)
        assertFalse(connection.encode().contains("DO_NOT_PERSIST"))
    }
    @Test fun missingCredentialsCannotBeReportedAsConnected() {
        val connection = IntervalConnection.parse(JSONObject().put("configured",false).put("syncStatus","connected"),1234)
        assertFalse(connection.configured); assertEquals("disconnected",connection.status)
    }
    @Test fun malformedStatusIsNotAcceptedAsSuccessfulCheck() {
        assertThrows(org.json.JSONException::class.java) { IntervalConnection.parse(JSONObject().put("ok",true),1234) }
        assertThrows(IllegalArgumentException::class.java) { IntervalConnection.parse(JSONObject().put("configured",true).put("syncStatus","invented"),1234) }
    }
}
