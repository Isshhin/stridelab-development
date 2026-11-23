package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActivityDetailTest {
    private fun sample(index:Int=0) = JSONObject().put("activity_id","run-1").put("sample_index",index).put("elapsed_s",index*10).put("heart_rate",145).put("altitude_m",-4)
    private fun raw() = JSONObject().put("user_id","owner").put("activity_id","run-1").put("fetched_at",1234).put("samples",JSONArray().put(sample()).put(sample(1))).put("laps",JSONArray())
    @Test fun keepsMissingCoordinatesAndPaceMissingAndAllowsBelowSeaLevel() {
        val parsed=ActivityDetail.parse(raw(),"owner","run-1")
        assertNull(parsed.samples.first().latitude); assertNull(parsed.samples.first().pace)
        assertEquals(-4.0,parsed.samples.first().altitude!!,0.0)
    }
    @Test fun rejectsWrongOwnerActivityAndUnorderedSamples() {
        for(data in listOf(raw().put("user_id","other"),raw().put("samples",JSONArray().put(sample().put("activity_id","another"))),raw().put("samples",JSONArray().put(sample(1)).put(sample(0))))) {
            assertThrows(IllegalArgumentException::class.java) { ActivityDetail.parse(data,"owner","run-1") }
        }
    }
    @Test fun rejectsImpossibleCoordinatesAndNonpositiveHeartRate() {
        for(row in listOf(sample().put("latitude",91),sample().put("longitude",181),sample().put("heart_rate",0))) {
            assertThrows(IllegalArgumentException::class.java) { ActivityDetail.parse(raw().put("samples",JSONArray().put(row)),"owner","run-1") }
        }
    }
    @Test fun gatewayPaginatesShortPagesAndUsesJoinedOwnerFilter() {
        val paths=mutableListOf<String>()
        val gateway=SupabaseGateway(BackendTransport { path,_,_ ->
            paths += path
            assertTrue(path.contains("activities.user_id=eq.owner"))
            if(path.contains("activity_samples") && path.contains("offset=0")) JSONArray().put(sample().put("activities",JSONObject().put("user_id","owner"))).toString() else "[]"
        })
        val parsed=ActivityDetail.parse(gateway.detail(NativeSession("owner","fixture","fixture",5000),"run-1"),"owner","run-1")
        assertEquals(1,parsed.samples.size); assertTrue(paths.any { it.contains("offset=1") })
    }
    @Test fun gatewayRejectsCrossAccountNestedRows() {
        val gateway=SupabaseGateway(BackendTransport { _,_,_ -> JSONArray().put(sample().put("activities",JSONObject().put("user_id","other"))).toString() })
        assertThrows(IllegalArgumentException::class.java) { gateway.detail(NativeSession("owner","fixture","fixture",5000),"run-1") }
    }
}
