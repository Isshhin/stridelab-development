package io.stridelab.privatealpha

import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A user's reusable session targets, separate from both generated plans and actual logs. */
internal data class SessionTemplate(val id:String,val name:String,val discipline:String,val minutes:Int,val exercises:List<ExercisePrescription>,val source:String="User-created") {
    fun encode(owner:String)=JSONObject().put("schemaVersion",1).put("type","session-template").put("user_id",owner).put("id",id).put("name",name).put("discipline",discipline).put("minutes",minutes).put("source",source).put("exercises",JSONArray(exercises.map { it.encode() }))
    fun validate() {
        RecordPolicy.identifier(id)
        if(name.trim().length !in 3..80 || discipline !in setOf("str","cal") || minutes !in 15..120 || exercises.size !in 1..8 || exercises.map { it.id }.distinct().size!=exercises.size)throw PlanConstraint("Choose a name, 15–120 minutes and one to eight distinct resistance movements.")
        if(source.length>500 || exercises.any { it.name.length>120 || it.equipment.length>200 || it.cue.length>1000 })throw PlanConstraint("Shorten the template metadata.")
        if(exercises.any { it.sets !in 1..10 || it.reps==null || it.reps !in 1..100 || it.seconds!=null || it.rest !in 15..300 })throw PlanConstraint("Use 1–10 sets, 1–100 target reps and 15–300 seconds rest for each selected movement.")
    }
    fun draft(owner:String,request:PlanRequest,date:LocalDate,id:String=UUID.randomUUID().toString(),now:Instant=Instant.now()):PlanSnapshot {
        validate();RecordPolicy.identifier(owner);RecordPolicy.identifier(id)
        if(date<LocalDate.now())throw PlanConstraint("Choose today or a future date for the practice session.")
        fun has(key:String,n:Int=1)=(request.equipment[key]?:0)>=n
        exercises.forEach { ex ->
            val equipped=when(ex.id) {
                "e1"->has("bb")&&has("rack");"e3"->has("bb");"e6"->has("support")||has("bench")
                "gs","dh","dr"->has("db");"bp"->has("db",2)&&has("bench");"fp"->has("db",2)
                "br"->has("bands")&&has("anchor");"ip"->has("support");"e2","e5"->has("bar");"ap"->has("bar")&&has("support")
                "e4","rl","sq","hg","fc"->true;else->false
            }
            if(!equipped)throw PlanConstraint("${ex.name} needs its listed equipment or is not a supported library movement. Choose an available alternative.")
            if(ex.id in setOf("e1","e3","bp") && request.strengthExperience==0)throw PlanConstraint("${ex.name} requires controlled technique. Choose a beginner alternative or review your actual strength experience.")
            if(ex.id=="e2" && request.pullLevel<2)throw PlanConstraint("Choose assisted pull-up practice until a controlled pull-up is repeatable.")
            if(ex.id=="e4" && request.calLevel<1)throw PlanConstraint("Choose incline push-ups until a controlled push-up is repeatable.")
            if(ex.id=="e5" && (request.pullLevel<2 || request.coreLevel<2))throw PlanConstraint("Choose floor bent-knee leg raises until hanging control and core ability are established.")
        }
        val estimated=8+exercises.sumOf { kotlin.math.ceil((it.sets*it.reps!!*3+(it.sets-1)*it.rest)/60.0+1).toInt() }
        if(estimated>minutes)throw PlanConstraint("These targets need about $estimated minutes including an easy warm-up/finish. Reduce sets/reps or allow more time.")
        val p=request.copy(goal=if(discipline=="str")"General strength" else "Calisthenics foundation",secondaryGoal="User-reviewed practice: $name",start=date,weeks=1,minutes=minutes,days=(1..7).toSet(),runDays=0,strengthDays=if(discipline=="str")1 else 0,calDays=if(discipline=="cal")1 else 0,conditioningDays=0,qualityCount=0,qualityDays=emptySet())
        Engine1.validate(p)
        val lower=exercises.any { it.id in setOf("e1","e3","e6","gs","dh","rl","sq","hg") }
        val session=PlannedSession("$id-practice",date,discipline,name.trim(),minutes,null,"practice",lower=lower,exercises=exercises,steps=listOf("5 min comfortable general warm-up","Use the reviewed targets and stop before form breaks","3 min easy finish"),reason="Your selected session targets. Review ability, equipment and recovery before approving; no automatic progression is applied.")
        return PlanSnapshot(id,owner,ENGINE1_VERSION,POLICY_ID,now,p,listOf(session),listOf("A one-session draft from your selected library movements. These are your reviewed targets, not an automatically optimized training recommendation.","Source: $source. Saving or editing a template never changes an approved session or historical log.","Calendar conflicts and original Engine 1.0 logging safeguards apply. Actual values remain blank until you record them."),name=name.trim())
    }
    companion object {
        const val POLICY_ID="stridelab.engine1.user-session.v1"
        fun decode(j:JSONObject,owner:String):SessionTemplate {
            require(j.getInt("schemaVersion")==1 && j.getString("user_id")==owner && j.getString("type")=="session-template")
            return SessionTemplate(j.getString("id"),j.getString("name"),j.getString("discipline"),j.getInt("minutes"),j.getJSONArray("exercises").objects().map(ExercisePrescription::decode),j.getString("source")).also { it.validate() }
        }
    }
}
