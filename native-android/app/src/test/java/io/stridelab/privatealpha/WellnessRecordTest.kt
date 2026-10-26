package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WellnessRecordTest {
    private fun row()=JSONObject().put("id","wellness-1").put("user_id","owner").put("source","intervals").put("metric_type","sleep_duration").put("value",7.5).put("unit","hours").put("recorded_at","2026-10-25T12:00:00Z")
    @Test fun retainsSourceUnitsAndDateWithoutCreatingReadiness() {
        val value=WellnessRecord.parse(row(),"owner")!!
        assertEquals(7.5,value.value,0.0); assertEquals("hours",value.unit); assertEquals("intervals",value.source)
    }
    @Test fun rejectsOwnerWrongUnitsAndImpossibleValuesAndSkipsUnsupportedMetrics() {
        for(raw in listOf(row().put("user_id","other"),row().put("unit","seconds"),row().put("value",25),row().put("value",-1))) assertThrows(IllegalArgumentException::class.java) { WellnessRecord.parse(raw,"owner") }
        assertNull(WellnessRecord.parse(row().put("metric_type","unknown"),"owner"))
    }
    @Test fun wellnessGatewayUsesOwnerAndBoundedDateFilterAndRejectsWrongOwner() {
        var seen=false
        val gateway=SupabaseGateway(BackendTransport { path,_,_ -> seen=true; assertTrue(path.contains("user_id=eq.owner"));assertTrue(path.contains("recorded_at=gte."));"[]" })
        assertEquals(0,gateway.wellness(NativeSession("owner","fixture","fixture",5000)).length());assertTrue(seen)
        val wrong=SupabaseGateway(BackendTransport { _,_,_ -> "[{\"user_id\":\"another\"}]" })
        assertThrows(IllegalArgumentException::class.java) { wrong.wellness(NativeSession("owner","fixture","fixture",5000)) }
    }
}
