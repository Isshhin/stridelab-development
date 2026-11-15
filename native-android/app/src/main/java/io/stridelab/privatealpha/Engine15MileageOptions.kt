package io.stridelab.privatealpha
import org.json.JSONObject

/** Generated alternatives, never an automatic change to the athlete's selected goal. */
internal data class Engine15MileageOptions(val lowerTargetKm:Double,val targetKm:Double,val longerWeeks:Int?) {
    init { require(lowerTargetKm.isFinite() && targetKm.isFinite() && lowerTargetKm>=0 && lowerTargetKm<targetKm && targetKm<=120 && (longerWeeks==null || longerWeeks in 1..16)) }
    fun encode()=JSONObject().put("lowerTargetKm",lowerTargetKm).put("targetKm",targetKm).put("longerWeeks",longerWeeks)
    companion object { fun decode(j:JSONObject)=Engine15MileageOptions(j.getDouble("lowerTargetKm"),j.getDouble("targetKm"),j.nullInt("longerWeeks")) }
    fun explanations(weeks:Int)=listOf(
        "LOWER_TARGET_OPTION: Review $lowerTargetKm km/week within the selected $weeks-week schedule; the requested target has not been changed.",
        if(longerWeeks!=null) "LONGER_TIMELINE_OPTION: A $longerWeeks-week generated projection reaches the requested $targetKm km/week within the same constraints. This is conditional on future completion and response, not guaranteed readiness; approval is required."
        else "LONGER_TIMELINE_OPTION: A sufficient timeline is not established within the supported 16-week horizon. Reassess actual completed exposure and availability before extending; more weeks alone do not remove session limits.")
}
