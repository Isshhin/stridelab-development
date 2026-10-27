package io.stridelab.privatealpha

import org.json.JSONObject
import java.security.MessageDigest

/** Editable strings, not confirmed performance. Invalid partial input can be resumed safely. */
internal data class WorkoutEntryDraft(val owner:String,val planId:String,val sessionId:String,val logRevision:Int,val minutes:String,val distance:String,val effort:String,val notes:String,val performedOn:String,val entries:String,val editedAt:Long) {
    val key get()=planId to sessionId
    val recordId get()="entry-draft-"+MessageDigest.getInstance("SHA-256").digest((planId+"\u0000"+sessionId).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    val contentKey get()=listOf(minutes,distance,effort,notes,performedOn,entries)
    fun validate() {
        RecordPolicy.identifier(owner);RecordPolicy.identifier(planId);RecordPolicy.identifier(sessionId)
        require(logRevision>=0 && editedAt>=0 && minutes.length<=64 && distance.length<=64 && effort.length<=64 && notes.length<=2000 && performedOn.length<=32 && entries.length<=65536)
        RecordPolicy.payload(entries)
        val fields=JSONObject(entries);require(fields.length()<=600)
        require(fields.keys().asSequence().all { it.length<=256 && fields.opt(it) is String && fields.getString(it).length<=64 })
    }
    fun encode()=JSONObject().put("schemaVersion",1).put("type","workout-entry-draft").put("user_id",owner).put("planId",planId).put("sessionId",sessionId).put("logRevision",logRevision).put("minutes",minutes).put("distance",distance).put("effort",effort).put("notes",notes).put("performedOn",performedOn).put("entries",entries).put("editedAt",editedAt)
    fun visibleEntries(exercises:List<ExercisePrescription>):String {
        val values=JSONObject(entries)
        values.keys().asSequence().toList().forEach { key -> if(exercises.none { ex ->
            if(!key.startsWith(ex.id+"|"))false else { val parts=key.removePrefix(ex.id+"|").split('|');parts.size==2 && parts[0].toIntOrNull()?.let { it in 1..ex.sets }==true && parts[1] in setOf("reps","kg","seconds") }
        })values.remove(key) }
        return values.toString()
    }
    companion object {
        fun decode(j:JSONObject,owner:String):WorkoutEntryDraft {
            require(j.getInt("schemaVersion")==1 && j.getString("type")=="workout-entry-draft" && j.getString("user_id")==owner)
            return WorkoutEntryDraft(owner,j.getString("planId"),j.getString("sessionId"),j.getInt("logRevision"),j.getString("minutes"),j.getString("distance"),j.getString("effort"),j.getString("notes"),j.getString("performedOn"),j.getString("entries"),j.getLong("editedAt")).also { it.validate() }
        }
    }
}
