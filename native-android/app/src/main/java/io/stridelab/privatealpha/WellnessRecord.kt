package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Instant

internal data class WellnessRecord(val id:String,val type:String,val value:Double,val unit:String,val recordedAt:Instant,val source:String) {
    companion object {
        fun parse(row:JSONObject,account:String):WellnessRecord? {
            require(row.getString("user_id")==account)
            val type=row.getString("metric_type")
            val expected=mapOf("resting_hr" to "bpm","hrv" to "ms","hrv_sdnn" to "ms","sleep_duration" to "hours","steps" to "steps")
            val unit=expected[type] ?: return null
            require(row.getString("unit")==unit)
            val value=row.getDouble("value"); require(value.isFinite() && value>=0)
            if(type in setOf("resting_hr","hrv","hrv_sdnn")) require(value>0)
            if(type=="sleep_duration") require(value<=24)
            val source=row.getString("source"); require(source in setOf("intervals","health_connect","manual"))
            val id=row.getString("id"); RecordPolicy.identifier(id)
            return WellnessRecord(id,type,value,unit,Instant.parse(row.getString("recorded_at")),source)
        }
    }
}
