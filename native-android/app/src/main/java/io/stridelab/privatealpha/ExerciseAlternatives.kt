package io.stridelab.privatealpha

import java.time.Instant
import org.json.JSONObject

internal data class ExerciseSwap(val originalId:String,val replacement:ExercisePrescription,val reason:String,val at:Instant) {
    fun encode()=JSONObject().put("originalId",originalId).put("replacement",replacement.encode()).put("reason",reason).put("at",at.toString())
    companion object { fun decode(j:JSONObject)=ExerciseSwap(j.getString("originalId"),ExercisePrescription.decode(j.getJSONObject("replacement")),j.getString("reason"),Instant.parse(j.getString("at"))) }
}
/** Explicit regressions and progressions for the baseline's known movements; never transfers a previous load. */
internal object ExerciseAlternatives {
    enum class SwapDirection { EASIER, HARDER }
    private data class Option(val name:String,val reps:Int?,val seconds:Int?,val cue:String)
    fun easier(ex:ExercisePrescription):ExercisePrescription? {
        if(ex.id.startsWith("swap-"))return null
        val (name,reps,seconds,cue)=when(ex.id) {
            "e1","gs","sq","e6","rl","sit-to-stand"->Option("Comfortable partial-range squat",6,null,"Use a shallow comfortable range with bodyweight. Keep feet stable; stop before pain or loss of control.")
            "e3","dh","hg","glute-bridge"->Option("Unloaded short-range hip hinge",6,null,"Use no external load and a small comfortable hip movement. Keep the trunk steady.")
            "fp","bp","bb-bench","e4","ip","wall-push"->Option("Gentle wall push-up",6,null,"Use a solid wall and stand closer to reduce resistance. Stop before form breaks; never copy a previous load.")
            "dr","bb-row","br","cable-latpulldown","e2","ap","supported-hang","supported-scapular","prone-w"->Option("Gentle prone W raise",6,null,"Use a small comfortable shoulder movement with no load. This is a gentle alternative, not equivalent to loaded rowing or vertical pulling.")
            "e5","fc","plank","hollow","hollow-tuck","core-hollow","deadbug","bird-dog","side-plank"->Option("Short-range dead bug",6,null,"Use a small range and alternate limbs slowly. Breathe normally; support a limb or shorten the range if needed.")
            "pike-support"->Option("Ground-supported quadruped hold",null,15,"Keep knees and hands on the floor, neck unloaded and feet grounded. This does not require inversion.")
            else->return null
        }
        return ExercisePrescription("swap-${ex.id}-regression",name,minOf(ex.sets,2),reps,seconds,60,"Bodyweight / floor or solid wall",cue)
    }
    private data class HarderOption(val name:String,val reps:Int?,val seconds:Int?,val rest:Int,val equipment:String,val cue:String)
    fun harder(ex:ExercisePrescription):ExercisePrescription? {
        if(ex.id.startsWith("swap-"))return null
        val opt=when(ex.id) {
            "e1","gs","sq","e6","rl","sit-to-stand"-> HarderOption(
                "Tempo split squat",8,null,75,"Bodyweight or dumbbells",
                "Lower for a controlled 3-second tempo and pause smoothly above the floor before driving up.")
            "e3","dh","hg","glute-bridge"-> HarderOption(
                "Single-leg Romanian deadlift",8,null,75,"Bodyweight or light dumbbell",
                "Hinge slowly on one leg with square hips and controlled hamstring tension; keep back neutral.")
            "wall-push"-> HarderOption(
                "Incline tempo push-up",10,null,60,"Elevated sturdy bench or box",
                "Lower chest under control for 2–3 seconds to the bench edge with braced trunk.")
            "fp","bp","bb-bench","e4","ip"-> HarderOption(
                "Paused tempo push-up / press",10,null,75,ex.equipment,
                "Control a 2-second lowering phase and brief pause at the bottom before pressing cleanly.")
            "e2","ap","supported-hang","supported-scapular","prone-w"-> HarderOption(
                "Slow eccentric pull-up / inverted row",6,null,90,ex.equipment,
                "Pull smoothly and lower over 4 full seconds with engaged shoulders; stop with 1–2 crisp reps in reserve.")
            "dr","bb-row","br","cable-latpulldown"-> HarderOption(
                "Paused single-arm / strict row",10,null,75,ex.equipment,
                "Hold a crisp 1-second peak contraction at the ribcage without torso swing.")
            "side-plank"-> HarderOption(
                "Elevated star side plank",null,30,60,"Mat / floor",
                "Keep hips stacked high with controlled top-leg elevation and steady breathing.")
            "e5","fc","plank","hollow","hollow-tuck","core-hollow","deadbug","bird-dog"-> HarderOption(
                "Extended hollow body hold",null,30,60,"Mat / floor",
                "Press lower back firmly into the floor with long extended levers; shorten lever if ribs flare.")
            "pike-support"-> HarderOption(
                "Elevated pike hold",null,25,75,"Bench or box / floor",
                "Stack hips high with feet elevated on a stable bench and steady shoulder push.")
            else->return null
        }
        return ExercisePrescription(
            id="swap-${ex.id}-progression",
            name=opt.name,
            sets=minOf(maxOf(ex.sets,3),4),
            reps=opt.reps,
            seconds=opt.seconds,
            rest=opt.rest,
            equipment=opt.equipment,
            cue=opt.cue
        )
    }
    fun alternative(ex:ExercisePrescription,direction:SwapDirection):ExercisePrescription? =
        if(direction==SwapDirection.HARDER) harder(ex) else easier(ex)
    fun reasonFor(direction:SwapDirection):String =
        if(direction==SwapDirection.HARDER) HARDER_REASON else REASON
    const val REASON="User-chosen easier alternative. Range, loading and training effect differ; it is not a claim of equivalent stimulus or clinical suitability. Original prescription retained. No prior load is transferred."
    const val HARDER_REASON="User-chosen harder progression. Range, leverage, and loading demands increase; start conservatively and never copy a heavier prior load automatically. Original prescription retained."
}
