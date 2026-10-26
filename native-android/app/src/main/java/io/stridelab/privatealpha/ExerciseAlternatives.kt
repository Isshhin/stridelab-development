package io.stridelab.privatealpha

import java.time.Instant
import org.json.JSONObject

internal data class ExerciseSwap(val originalId:String,val replacement:ExercisePrescription,val reason:String,val at:Instant) {
    fun encode()=JSONObject().put("originalId",originalId).put("replacement",replacement.encode()).put("reason",reason).put("at",at.toString())
    companion object { fun decode(j:JSONObject)=ExerciseSwap(j.getString("originalId"),ExercisePrescription.decode(j.getJSONObject("replacement")),j.getString("reason"),Instant.parse(j.getString("at"))) }
}
/** Explicit regressions for the baseline's known movements; never transfers a previous load. */
internal object ExerciseAlternatives {
    private data class Option(val name:String,val reps:Int?,val seconds:Int?,val cue:String)
    fun easier(ex:ExercisePrescription):ExercisePrescription? {
        if(ex.id.startsWith("swap-"))return null
        val (name,reps,seconds,cue)=when(ex.id) {
            "e1","gs","sq","e6","rl"->Option("Comfortable partial-range squat",6,null,"Use a shallow comfortable range with bodyweight. Keep feet stable; stop before pain or loss of control.")
            "e3","dh","hg"->Option("Unloaded short-range hip hinge",6,null,"Use no external load and a small comfortable hip movement. Keep the trunk steady.")
            "fp","bp","bb-bench","e4","ip","wall-push"->Option("Gentle wall push-up",6,null,"Use a solid wall and stand closer to reduce resistance. Stop before form breaks; never copy a previous load.")
            "dr","bb-row","br","e2","ap","supported-hang","supported-scapular","prone-w"->Option("Gentle prone W raise",6,null,"Use a small comfortable shoulder movement with no load. This is a gentle alternative, not equivalent to loaded rowing or vertical pulling.")
            "e5","fc","plank","hollow","hollow-tuck","deadbug","side-plank"->Option("Short-range dead bug",6,null,"Use a small range and alternate limbs slowly. Breathe normally; support a limb or shorten the range if needed.")
            "pike-support"->Option("Ground-supported quadruped hold",null,15,"Keep knees and hands on the floor, neck unloaded and feet grounded. This does not require inversion.")
            else->return null
        }
        return ExercisePrescription("swap-${ex.id}-regression",name,minOf(ex.sets,2),reps,seconds,60,"Bodyweight / floor or solid wall",cue)
    }
    const val REASON="User-chosen easier alternative. Range, loading and training effect differ; it is not a claim of equivalent stimulus or clinical suitability. Original prescription retained. No prior load is transferred."
}
