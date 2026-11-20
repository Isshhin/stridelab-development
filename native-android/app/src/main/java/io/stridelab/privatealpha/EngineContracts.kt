package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal const val PLAN_SCHEMA=1
internal const val ENGINE1_VERSION="1.0.0"
internal const val ENGINE1_POLICY="stridelab.engine1.conservative-20261026.1"
internal class PlanConstraint(message:String):IllegalArgumentException(message)
internal object PlanDisciplineContract {
    val goals=mapOf(
        "run" to listOf("Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"),
        "str" to listOf("General strength","Muscle development","Strength endurance"),
        "cal" to listOf("Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"),
        "cond" to listOf("Conditioning"))
}
internal interface PlanGenerationEngine {
    val version:String
    val policyId:String
    fun generate(request:PlanRequest,owner:String,id:String=UUID.randomUUID().toString(),now:Instant=Instant.now()):PlanSnapshot
}
internal data class PlanRequest(
    val goal:String="General fitness", val secondaryGoal:String="", val experience:Int=0,
    val start:LocalDate=LocalDate.now(), val weeks:Int=4, val minutes:Int=45,
    val days:Set<Int> = setOf(1,2,4,5,6), val runDays:Int=3, val strengthDays:Int=0, val calDays:Int=0, val conditioningDays:Int=0,
    val longDay:Int=6,val qualityDays:Set<Int> = setOf(2),val qualityCount:Int=0,
    val weeklyKm:Double=0.0,val longestKm:Double=0.0,val planningPace:Double=480.0,
    val equipment:Map<String,Int> = emptyMap(),val calLevel:Int=0,val returning:Boolean=false,
    val excluded:Set<String> = emptySet(),val limitations:String="",val recoveryNotes:String="",val strengthExperience:Int=0,val pullLevel:Int=0,val coreLevel:Int=0,val policyChoice:String="baseline",val disciplineGoals:Map<String,String> = emptyMap(),val dailyMinutes:Int=90,
    val weeklySlots:Map<String,List<Int>> = emptyMap(),
    val preferredQuality:String = "Threshold",
    val allowedQualityTypes:Set<String> = emptySet(),
    val lockedSessionDays:Map<String,String> = emptyMap(),
    val allowDoubleDays:Boolean = false,
    val timeBudgetEnabled:Boolean = true,
    val targetWeeklyKm:Double? = null,
    val runningRecoveryHold:Boolean = false,
    val recentQualitySessionsPerWeek:Int? = null,
    /** Preserves the manual mileage-builder identity; never treated as race intent. */
    val trainYourWay:Boolean = false,
    /** Explicit consent to a foundation plan below the requested future mileage. */
    val foundationAcknowledged:Boolean = false,
    /** M3 evidence provenance. Legacy snapshots remain unclassified. */
    val recentMileageBasis:String = "LEGACY_UNSPECIFIED",
    /** Reported peak is not treated as a sustainable weekly average. */
    val reportedPeakWeeklyKm:Double? = null,
    /** Private/debug plan preference; this remains a requested budget, not validated capacity. */
    val userDirectedPlanning:Boolean = false,
    /** Explicit user-authored workouts keyed by stable week/run-slot suffix, e.g. w2-run1. */
    val userDefinedQualityWorkouts:Map<String,RunningPrescription> = emptyMap(),
    /** True only after the runner chooses a family in Plan Builder; default Threshold is not intent. */
    val preferredQualityExplicit:Boolean = false,
    /** User-asserted per-segment equivalents for debug-only quality alternatives. */
    val qualityDistanceClaims:List<Engine15M5SegmentDistanceClaim> = emptyList()
) {
    init {
        require(qualityDistanceClaims.size <= 16)
        require(qualityDistanceClaims.all {
            it.provenance == Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT &&
                it.externalReviewReference == null
        })
    }
    fun disciplineSessionCount(discipline:String)=when(discipline) {
        "run"->runDays;"str"->strengthDays;"cal"->calDays;"cond"->conditioningDays;else->0
    }
    fun disciplineGoal(discipline:String):String=disciplineGoals[discipline]
        ?: goal.takeIf { it in PlanDisciplineContract.goals.getValue(discipline) }
        ?: PlanDisciplineContract.goals.getValue(discipline).first()

    fun encode():JSONObject {
        val encoded=JSONObject().put("goal",goal).put("secondaryGoal",secondaryGoal).put("experience",experience).put("start",start.toString()).put("weeks",weeks).put("minutes",minutes)
            .put("days",JSONArray(days.sorted())).put("runDays",runDays).put("strengthDays",strengthDays).put("calDays",calDays).put("conditioningDays",conditioningDays).put("longDay",longDay)
            .put("qualityDays",JSONArray(qualityDays.sorted())).put("qualityCount",qualityCount).put("weeklyKm",weeklyKm).put("longestKm",longestKm).put("planningPace",planningPace)
            .put("equipment",JSONObject(equipment)).put("calLevel",calLevel).put("returning",returning).put("excluded",JSONArray(excluded.sorted())).put("limitations",limitations).put("recoveryNotes",recoveryNotes).put("strengthExperience",strengthExperience).put("pullLevel",pullLevel).put("coreLevel",coreLevel).put("policyChoice",policyChoice).put("disciplineGoals",JSONObject(disciplineGoals))
        if(policyChoice=="engine15")encoded.put("lockedSessionDays",JSONObject(lockedSessionDays))
        if(policyChoice=="engine15" && allowDoubleDays)encoded.put("allowDoubleDays",true)
        if(!timeBudgetEnabled)encoded.put("timeBudgetEnabled",false)
        if(policyChoice=="multi-session" || policyChoice=="engine15")encoded.put("dailyMinutes",dailyMinutes)
        if(weeklySlots.isNotEmpty()) {
            val assignments=JSONObject()
            weeklySlots.forEach { (discipline, slots) ->
                assignments.put(discipline, JSONArray(slots))
            }
            encoded.put("weeklySlots", assignments)
        }
        if(targetWeeklyKm!=null)encoded.put("targetWeeklyKm",targetWeeklyKm)
        if(trainYourWay)encoded.put("trainYourWay",true)
        if(foundationAcknowledged)encoded.put("foundationAcknowledged",true)
        if(recentMileageBasis!="LEGACY_UNSPECIFIED")encoded.put("recentMileageBasis",recentMileageBasis)
        if(reportedPeakWeeklyKm!=null)encoded.put("reportedPeakWeeklyKm",reportedPeakWeeklyKm)
        if(policyChoice=="engine15" && userDirectedPlanning)encoded.put("userDirectedPlanning",true)
        if(policyChoice=="engine15" && userDefinedQualityWorkouts.isNotEmpty())
            encoded.put("userDefinedQualityWorkouts",JSONObject().apply {
                userDefinedQualityWorkouts.toSortedMap().forEach { (key,value)->put(key,value.encode()) }
            })
        if(runningRecoveryHold)encoded.put("runningRecoveryHold",true)
        if(recentQualitySessionsPerWeek!=null)encoded.put("recentQualitySessionsPerWeek",recentQualitySessionsPerWeek)
        if(preferredQuality!="Threshold")encoded.put("preferredQuality",preferredQuality)
        if(policyChoice=="engine15" && preferredQualityExplicit)encoded.put("preferredQualityExplicit",true)
        if(policyChoice=="engine15" && qualityDistanceClaims.isNotEmpty())
            encoded.put("qualityDistanceClaims",JSONArray(qualityDistanceClaims.map { it.encode() }))
        if(allowedQualityTypes.isNotEmpty())
            encoded.put("allowedQualityTypes",JSONArray(allowedQualityTypes.sorted()))
        return encoded
    }
    companion object {
        fun decode(j:JSONObject):PlanRequest {
            val eq=j.getJSONObject("equipment"); val days=j.getJSONArray("days");val quality=j.getJSONArray("qualityDays");val excluded=j.getJSONArray("excluded")
            return PlanRequest(j.getString("goal"),j.getString("secondaryGoal"),j.getInt("experience"),LocalDate.parse(j.getString("start")),j.getInt("weeks"),j.getInt("minutes"),
                (0 until days.length()).map { days.getInt(it) }.toSet(),j.getInt("runDays"),j.getInt("strengthDays"),j.getInt("calDays"),j.getInt("conditioningDays"),j.getInt("longDay"),
                (0 until quality.length()).map { quality.getInt(it) }.toSet(),j.getInt("qualityCount"),j.getDouble("weeklyKm"),j.getDouble("longestKm"),j.getDouble("planningPace"),
                eq.keys().asSequence().associateWith { eq.getInt(it) },j.getInt("calLevel"),j.getBoolean("returning"),(0 until excluded.length()).map { excluded.getString(it) }.toSet(),j.getString("limitations"),j.getString("recoveryNotes"),j.optInt("strengthExperience",j.getInt("experience")),j.optInt("pullLevel",0),j.optInt("coreLevel",0),j.optString("policyChoice","baseline"),j.optJSONObject("disciplineGoals")?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } } ?: emptyMap(),j.optInt("dailyMinutes",90),
                j.optJSONObject("weeklySlots")?.let { assignments ->
                    assignments.keys().asSequence().associateWith { discipline ->
                        val days = assignments.getJSONArray(discipline)
                        (0 until days.length()).map { days.getInt(it) }
                    }
                } ?: emptyMap(),
                j.optString("preferredQuality","Threshold"),
                j.optJSONArray("allowedQualityTypes")?.let { qualities ->
                    (0 until qualities.length()).map { qualities.getString(it) }.toSet()
                } ?: emptySet(),
                j.optJSONObject("lockedSessionDays")?.let { locks ->
                    locks.keys().asSequence().associateWith { locks.getString(it) }
                } ?: emptyMap(), j.has("allowDoubleDays") && j.getBoolean("allowDoubleDays"),
                j.optBoolean("timeBudgetEnabled",true),j.optDouble("targetWeeklyKm").takeIf { j.has("targetWeeklyKm") },j.optBoolean("runningRecoveryHold",false),j.nullInt("recentQualitySessionsPerWeek"),
                j.optBoolean("trainYourWay",false),j.optBoolean("foundationAcknowledged",false),
                j.optString("recentMileageBasis","LEGACY_UNSPECIFIED"),
                j.nullDouble("reportedPeakWeeklyKm"),j.optBoolean("userDirectedPlanning",false),
                j.optJSONObject("userDefinedQualityWorkouts")?.let { workouts ->
                    workouts.keys().asSequence().associateWith { RunningPrescription.decode(workouts.getJSONObject(it)) }
                } ?: emptyMap(),j.optBoolean("preferredQualityExplicit",false),
                j.optJSONArray("qualityDistanceClaims")?.let { claims ->
                    (0 until claims.length()).map { Engine15M5SegmentDistanceClaim.decode(claims.getJSONObject(it)) }
                } ?: emptyList())
        }
    }
}
internal data class ExercisePrescription(val id:String,val name:String,val sets:Int,val reps:Int?=null,val seconds:Int?=null,val rest:Int=60,val equipment:String="None",val cue:String="Keep the movement controlled; stop before form breaks.") {
    fun encode()=JSONObject().put("id",id).put("name",name).put("sets",sets).put("reps",reps).put("seconds",seconds).put("rest",rest).put("equipment",equipment).put("cue",cue)
    companion object { fun decode(j:JSONObject)=ExercisePrescription(j.getString("id"),j.getString("name"),j.getInt("sets"),j.nullInt("reps"),j.nullInt("seconds"),j.getInt("rest"),j.getString("equipment"),j.getString("cue")) }
}
internal data class SetRecord(val exerciseId:String,val set:Int,val reps:Int?=null,val kg:Double?=null,val seconds:Int?=null) {
    fun encode()=JSONObject().put("exerciseId",exerciseId).put("set",set).put("reps",reps).put("kg",kg).put("seconds",seconds)
    companion object { fun decode(j:JSONObject)=SetRecord(j.getString("exerciseId"),j.getInt("set"),j.nullInt("reps"),j.nullDouble("kg"),j.nullInt("seconds")) }
}
internal data class WorkoutLog(val at:Instant,val minutes:Double?=null,val distanceKm:Double?=null,val effort:Int?=null,val sets:List<SetRecord> = emptyList(),val notes:String="",val performedOn:LocalDate?=null,val completionDifficulty:String?=null,val discomfort:String?=null,val qualityWorkKm:Double?=null) {
    init {
        require(qualityWorkKm==null || qualityWorkKm.isFinite() && qualityWorkKm>0 && qualityWorkKm<=120 && (distanceKm==null || qualityWorkKm<=distanceKm))
        require(completionDifficulty==null || completionDifficulty in setOf("As planned","Easier","Harder","Could not finish"))
        require(discomfort==null || discomfort in setOf("None","Soreness","Pain"))
    }
    fun encode()=JSONObject().put("at",at.toString()).put("minutes",minutes).put("distanceKm",distanceKm).put("effort",effort).put("sets",JSONArray(sets.map { it.encode() })).put("notes",notes).put("performedOn",performedOn?.toString()).put("completionDifficulty",completionDifficulty).put("discomfort",discomfort).put("qualityWorkKm",qualityWorkKm)
    companion object { fun decode(j:JSONObject)=WorkoutLog(Instant.parse(j.getString("at")),j.nullDouble("minutes"),j.nullDouble("distanceKm"),j.nullInt("effort"),j.getJSONArray("sets").objects().map(SetRecord::decode),j.getString("notes"),j.nullString("performedOn")?.let(LocalDate::parse),j.nullString("completionDifficulty"),j.nullString("discomfort"),j.nullDouble("qualityWorkKm")) }
}
internal data class SessionSuggestion(val minutes:Int,val reason:String,val accepted:Boolean=false) {
    fun encode()=JSONObject().put("minutes",minutes).put("reason",reason).put("accepted",accepted)
    companion object { fun decode(j:JSONObject)=SessionSuggestion(j.getInt("minutes"),j.getString("reason"),j.getBoolean("accepted")) }
}
internal data class PlannedSession(val id:String,val date:LocalDate,val discipline:String,val title:String,val minutes:Int,val distanceKm:Double?,val kind:String,val lower:Boolean=false,val quality:Boolean=false,
    val exercises:List<ExercisePrescription> = emptyList(),val steps:List<String> = emptyList(),val reason:String="",val status:String="planned",val missedReason:String?=null,val log:WorkoutLog?=null,val suggestion:SessionSuggestion?=null,val linkedActivityId:String?=null,val linkPreviousStatus:String?=null,val swaps:List<ExerciseSwap> = emptyList(),val logRevision:Int=0,val runningPrescription:RunningPrescription?=null,val runResponse:WorkoutLog?=null) {
    init {
        runResponse?.let { require(discipline=="run" && it.minutes==null && it.distanceKm==null && it.qualityWorkKm==null && it.sets.isEmpty() && (it.effort==null || it.effort in 1..10)) }
        runningPrescription?.let {
            require(discipline=="run")
            if(it.mixedSegments==null) {
                require(distanceKm!=null && kotlin.math.abs(distanceKm*1000-it.meters)<0.001)
            } else if(it.reconciledDistanceMeters!=null) {
                require(distanceKm!=null && kotlin.math.abs(distanceKm*1000-it.reconciledDistanceMeters)<0.001)
            } else {
                // Pace-based estimate remains metadata only; it is not a target.
                require(distanceKm==null)
            }
            require(quality==(it.role=="QUALITY"))
        }
    }
    val effectiveExercises get()=exercises.map { original -> swaps.find { it.originalId==original.id }?.replacement ?: original }
    val effectiveMinutes get()=if(suggestion?.accepted==true) suggestion.minutes else minutes
    val effectiveTitle get()=if(suggestion?.accepted==true) "Lighter alternative · $title" else title
    fun encode()=JSONObject().put("id",id).put("date",date.toString()).put("discipline",discipline).put("title",title).put("minutes",minutes).put("distanceKm",distanceKm).put("kind",kind).put("lower",lower).put("quality",quality)
        .put("exercises",JSONArray(exercises.map { it.encode() })).put("steps",JSONArray(steps)).put("reason",reason).put("status",status).put("missedReason",missedReason).put("log",log?.encode()).put("suggestion",suggestion?.encode()).put("linkedActivityId",linkedActivityId).put("linkPreviousStatus",linkPreviousStatus).put("swaps",JSONArray(swaps.map { it.encode() })).put("logRevision",logRevision).put("runningPrescription",runningPrescription?.encode()).put("runResponse",runResponse?.encode())
    companion object { fun decode(j:JSONObject)=PlannedSession(j.getString("id"),LocalDate.parse(j.getString("date")),j.getString("discipline"),j.getString("title"),j.getInt("minutes"),j.nullDouble("distanceKm"),j.getString("kind"),j.getBoolean("lower"),j.getBoolean("quality"),
        j.getJSONArray("exercises").objects().map(ExercisePrescription::decode),j.getJSONArray("steps").strings(),j.getString("reason"),j.getString("status"),j.nullString("missedReason"),j.optJSONObject("log")?.let(WorkoutLog::decode),j.optJSONObject("suggestion")?.let(SessionSuggestion::decode),j.nullString("linkedActivityId"),j.nullString("linkPreviousStatus"),j.optJSONArray("swaps")?.objects()?.map(ExerciseSwap::decode) ?: emptyList(),j.optInt("logRevision",0),j.optJSONObject("runningPrescription")?.let(RunningPrescription::decode),j.optJSONObject("runResponse")?.let(WorkoutLog::decode)) }
}
/** Feedback overlays only the coaching interpretation; original actual measurements stay immutable. */
internal fun PlannedSession.coachingLog():WorkoutLog? {
    val response=runResponse ?: return log
    val original=log ?: return response
    return original.copy(at=maxOf(original.at,response.at),performedOn=original.performedOn ?: response.performedOn,effort=response.effort,
        completionDifficulty=response.completionDifficulty,discomfort=response.discomfort)
}
internal data class PlanFeedback(val id:String,val at:Instant,val category:String,val text:String,val sessionId:String?=null) {
    fun encode()=JSONObject().put("id",id).put("at",at.toString()).put("category",category).put("text",text).put("sessionId",sessionId)
    companion object { fun decode(j:JSONObject)=PlanFeedback(j.getString("id"),Instant.parse(j.getString("at")),j.getString("category"),j.getString("text"),j.nullString("sessionId")) }
}
/** Prescriptions are persisted snapshots. Loading never re-runs a generation engine. */
internal data class PlanSnapshot(val id:String,val owner:String,val engineVersion:String,val policyId:String,val createdAt:Instant,val request:PlanRequest,val sessions:List<PlannedSession>,val explanations:List<String>,val status:String="draft",val feedback:List<PlanFeedback> = emptyList(),val revision:Int=1,val foreignSnapshot:String?=null,val name:String?=null,val mileageOptions:Engine15MileageOptions?=null,val experimentalQualityPolicy:String?=null,val experimentalQualityExposureSeconds:Int?=null) {
    fun encode():JSONObject {
        if(!Engine1Engines.canManage(this) && foreignSnapshot!=null)return JSONObject(foreignSnapshot)
        return JSONObject().put("schemaVersion",PLAN_SCHEMA).put("id",id).put("user_id",owner).put("engineVersion",engineVersion).put("policyId",policyId).put("createdAt",createdAt.toString()).put("request",request.encode())
        .put("sessions",JSONArray(sessions.map { it.encode() })).put("explanations",JSONArray(explanations)).put("status",status).put("feedback",JSONArray(feedback.map { it.encode() })).put("revision",revision).put("name",name).put("mileageOptions",mileageOptions?.encode()).put("experimentalQualityPolicy",experimentalQualityPolicy)
        .apply { if (experimentalQualityExposureSeconds != null) put("experimentalQualityExposureSeconds", experimentalQualityExposureSeconds) }
    }
    companion object {
        fun decode(j:JSONObject,owner:String):PlanSnapshot {
            require(j.getInt("schemaVersion")==PLAN_SCHEMA) { "Unsupported plan schema; saved snapshot retained" }
            require(j.getString("user_id")==owner) { "Plan owner does not match" }
            val sessions=j.getJSONArray("sessions").objects().map(PlannedSession::decode)
            require(sessions.size<=400 && sessions.map { it.id }.distinct().size==sessions.size && sessions.all { it.logRevision>=0 })
            return PlanSnapshot(j.getString("id"),owner,j.getString("engineVersion"),j.getString("policyId"),Instant.parse(j.getString("createdAt")),PlanRequest.decode(j.getJSONObject("request")),sessions,j.getJSONArray("explanations").strings(),j.getString("status"),j.getJSONArray("feedback").objects().map(PlanFeedback::decode),j.getInt("revision"),if(!Engine1Engines.supports(j.getString("engineVersion"),j.getString("policyId")))j.toString()else null,j.nullString("name"),j.optJSONObject("mileageOptions")?.let(Engine15MileageOptions::decode),j.nullString("experimentalQualityPolicy"),j.nullInt("experimentalQualityExposureSeconds"))
        }
    }
}
internal fun JSONObject.nullInt(key:String)=if(isNull(key)) null else getInt(key)
internal fun JSONObject.nullDouble(key:String)=if(isNull(key)) null else getDouble(key)
internal fun JSONObject.nullString(key:String)=if(isNull(key)) null else getString(key)
internal fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings()=(0 until length()).map { getString(it) }
