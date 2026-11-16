package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import org.json.JSONArray
import org.json.JSONObject

/** Planned distance, not measured execution, threshold or fitness evidence. */
internal data class RunningSegment(val purpose:String, val meters:Int, val effort:String) {
    init { require(purpose in setOf("WARM_UP","WORK","RECOVERY","COOL_DOWN","EASY") && meters>0 && meters<=120000) }
    fun encode()=JSONObject().put("purpose",purpose).put("meters",meters).put("effort",effort)
    companion object { fun decode(j:JSONObject)=RunningSegment(j.getString("purpose"),j.getInt("meters"),j.getString("effort")) }
}
/** Version-2 executable extent. Each step has exactly one target unit. */
internal data class RunningStepV2(val purpose:String, val effort:String, val meters:Int?=null, val seconds:Int?=null) {
    init {
        require(purpose in setOf("WARM_UP","WORK","RECOVERY","COOL_DOWN","EASY"))
        require((meters!=null) xor (seconds!=null))
        require(meters==null || meters in 1..120000)
        require(seconds==null || seconds in 1..14400)
    }
    fun encode():JSONObject {
        val j=JSONObject().put("purpose",purpose).put("effort",effort)
        if(meters!=null) j.put("meters",meters) else j.put("seconds",seconds)
        return j
    }
    companion object {
        fun decode(j:JSONObject):RunningStepV2 {
            require(j.has("meters") xor j.has("seconds")) { "A step must have exactly one extent" }
            return RunningStepV2(j.getString("purpose"),j.getString("effort"),
                if(j.has("meters")) j.getInt("meters") else null,
                if(j.has("seconds")) j.getInt("seconds") else null)
        }
    }
}
internal data class RunningPrescription(
    val role:String, val family:String?, val segments:List<RunningSegment>,
    val mixedSegments:List<RunningStepV2>?=null,
    /** Administrative planning-pace estimate only, never an exact distance target. */
    val estimatedDistanceKm:Double?=null
) {
    init {
        require(role in setOf("RECOVERY","EASY","ENDURANCE","LONG","QUALITY"))
        val purposes=if(mixedSegments==null) segments.map { it.purpose } else mixedSegments.map { it.purpose }
        require(purposes.isNotEmpty() && purposes.size<=50)
        require(if(role=="QUALITY") family in setOf("Threshold","Tempo","Intervals","Hills","Fartlek","Progression") && "WORK" in purposes
            else family==null && "WORK" !in purposes)
        if(mixedSegments==null) {
            require(segments.sumOf { it.meters }<=120000 && estimatedDistanceKm==null)
        } else {
            require(segments.isEmpty())
            require(mixedSegments.sumOf { it.meters ?: 0 }<=120000)
            require(mixedSegments.any { it.seconds!=null })
            require(estimatedDistanceKm!=null && estimatedDistanceKm.isFinite() && estimatedDistanceKm>0.0 && estimatedDistanceKm<=120.0)
        }
    }
    val meters get()=if(mixedSegments==null) segments.sumOf { it.meters } else mixedSegments.sumOf { it.meters ?: 0 }
    val workMeters get()=if(mixedSegments==null) segments.filter { it.purpose=="WORK" }.sumOf { it.meters }
        else mixedSegments.filter { it.purpose=="WORK" }.sumOf { it.meters ?: 0 }
    fun encode():JSONObject = if(mixedSegments==null) {
        JSONObject().put("version",1).put("role",role).put("family",family).put("segments",JSONArray(segments.map { it.encode() }))
    } else {
        JSONObject().put("version",2).put("role",role).put("family",family)
            .put("segments",JSONArray(mixedSegments.map { it.encode() }))
            .put("estimatedDistanceKm",estimatedDistanceKm)
            .put("estimateBasis","PLANNING_PACE_NOT_TARGET")
    }
    companion object {
        fun decode(j:JSONObject):RunningPrescription = when(j.getInt("version")) {
            1 -> RunningPrescription(j.getString("role"),j.nullString("family"),
                j.getJSONArray("segments").objects().map(RunningSegment::decode))
            2 -> {
                require(j.getString("estimateBasis")=="PLANNING_PACE_NOT_TARGET")
                RunningPrescription(j.getString("role"),j.nullString("family"),emptyList(),
                    j.getJSONArray("segments").objects().map(RunningStepV2::decode),
                    j.getDouble("estimatedDistanceKm"))
            }
            else -> throw IllegalArgumentException("Unsupported running prescription version")
        }
    }
}

/**
 * Calendar -> weekly objectives -> role allocation -> reconciled sessions.
 * Role weights and dose envelopes are provisional coaching policy, not injury thresholds.
 * No research authorization flag or future completion is promoted into athlete evidence.
 */
internal object Engine15RunningProgram {
    private fun week(p:PlanRequest,s:PlannedSession)=(ChronoUnit.DAYS.between(p.start,s.date)/7).toInt()
    private fun units(km:Double)=floor(km*10+1e-8).toInt()
    private fun hard(s:PlannedSession)=s.quality || s.kind=="long" || s.lower

    fun compose(p:PlanRequest, baseline:List<PlannedSession>,review:Engine15Feedback.DraftReview?=null,
        pattern:Engine15RunHistory?=null,ledger:Engine15WorkloadLedger?=null):List<PlannedSession> {
        if(review!=null) {
            val initial=compose(p,baseline,pattern=pattern,ledger=ledger)
            val reviewed=applyResponse(p,initial,review)
            for(w in 0 until p.weeks) {
                val before=initial.filter { it.discipline=="run" && week(p,it)==w }
                val after=reviewed.filter { it.discipline=="run" && week(p,it)==w }
                ledger?.record(w,"RECOVERY_RESPONSE","EXPLICIT_NEGATIVE_FEEDBACK",
                    ledger?.runningKm(before),null,ledger?.runningKm(after),
                    "Observed adverse feedback; output may be reduced, never catch-up")
            }
            return reviewed
        }
        val result=mutableListOf<PlannedSession>()
        var priorBuildWork:Int?=null
        for((w,rows) in baseline.groupBy { week(p,it) }.toSortedMap()) {
            val runs=rows.filter { it.discipline=="run" }.sortedBy { it.date }
            if(runs.isEmpty()) { result+=rows; continue }
            ledger?.record(w,"BASELINE","ENGINE1_BASELINE_OUTPUT",
                p.weeklyKm,p.targetWeeklyKm,ledger?.runningKm(runs),
                "Executed output of existing baseline generator; its internal rule attribution is not yet instrumented")
            val recovery=w%4==3
            val taper=p.goal in setOf("Half marathon","Marathon") && p.weeks>=8 && w>=p.weeks-2
            val qualityCount=if(p.experience==0 || p.weeklyKm<20 || recovery || taper || p.returning && w<3) 0
                else minOf(p.qualityCount,p.recentQualitySessionsPerWeek ?: 1)
            val qualityIds=mutableSetOf<String>()
            for(s in runs.filter { it.kind=="quality" }) {
                val candidate=s.copy(quality=true)
                val neighbors=baseline.filter { it.id!=s.id && (it.kind=="long" || it.lower) } + result.filter { it.quality }
                if(qualityIds.size<qualityCount && neighbors.none { Engine1.conflict(candidate,it) } &&
                    runs.filter { it.id in qualityIds }.none { kotlin.math.abs(ChronoUnit.DAYS.between(it.date,s.date))<=1 }) qualityIds+=s.id
            }
            val role=runs.associate { s -> s.id to when {
                s.kind=="long" -> "LONG"
                s.id in qualityIds -> "QUALITY"
                baseline.any { other -> other.id!=s.id && (other.kind=="long" || other.lower || other.id in qualityIds) &&
                    ChronoUnit.DAYS.between(other.date,s.date)==1L } || result.any { hard(it) && ChronoUnit.DAYS.between(it.date,s.date)==1L } -> "RECOVERY"
                else -> "EASY"
            } }.toMutableMap()
            // A second endurance stimulus belongs away from demanding days, not on every easy day.
            if(runs.size>=5 && !recovery && !p.returning) runs.filter { role[it.id]=="EASY" }
                .maxWithOrNull(compareBy<PlannedSession> { s ->
                    (runs.filter { role[it.id] in setOf("QUALITY","LONG") }+rows.filter { it.lower })
                        .minOfOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(s.date,it.date)) } ?: 7L
                }.thenBy { it.id })?.let { role[it.id]="ENDURANCE" }

            val prior=result.filter { it.discipline=="run" && week(p,it)==w-1 }
            if(p.weeklyKm==0.0) {
                val cap=if(recovery && prior.isNotEmpty()) prior.sumOf { it.minutes }*0.8/runs.sumOf { it.minutes } else 1.0
                result+=rows.map { s -> if(s.discipline!="run")s else s.copy(
                    minutes=maxOf(1,floor(s.minutes*minOf(1.0,cap)).toInt()),quality=false,kind=if(s.kind=="long")"long" else "easy",
                    title=if(role[s.id]=="LONG")"Long easy run / walk" else "Easy run / walk",
                    steps=listOf("Alternate comfortable running and walking. Keep conversation easy; shorten or stop if needed."),
                    reason="Time-only foundation: distance capacity is unknown. Reassess completed practice before increasing load.") }
                ledger?.record(w,"FINAL","UNKNOWN_TIME_ONLY_FOUNDATION",null,null,null,
                    "No reported recent mileage; time-only sessions cannot be expressed as delivered km")
                continue
            }
            val baselineKm=runs.sumOf { it.distanceKm ?: 0.0 }
            val weeklyCeiling=Engine15.runningCeiling(w,p.weeklyKm,p.targetWeeklyKm)
            var budget=units(minOf(baselineKm,weeklyCeiling))
            ledger?.record(w,"WEEK_BUDGET","ENGINE15_WEEKLY_CEILING",
                baselineKm,weeklyCeiling,budget/10.0,
                "Applied minimum of executed baseline volume and Engine 1.5 weekly ceiling")
            if(recovery && prior.isNotEmpty()) {
                val recoveryLimit=prior.sumOf {
                    it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0
                }*0.8
                val beforeRecovery=budget
                budget=minOf(budget,units(recoveryLimit))
                ledger?.record(w,"WEEK_BUDGET","ENGINE15_RECOVERY_CAP",
                    beforeRecovery/10.0,recoveryLimit,budget/10.0,
                    "Fourth-week recovery also limited by 80% of previous delivered week")
            }
            val reportedExposure=if(p.longestKm>0) p.longestKm + if(p.returning && w<2)0.0 else 1.0 else p.weeklyKm/runs.size
            val exposure=if(p.returning)reportedExposure else maxOf(reportedExposure,pattern?.longKm ?: 0.0)
            val caps=runs.map { s ->
                val fraction=when(role.getValue(s.id)) { "LONG"->1.0; "ENDURANCE","QUALITY"->0.85; "RECOVERY"->0.6; else->0.8 }
                val otherMinutes=rows.filter { it.date==s.date && it.id!=s.id }.sumOf { it.minutes }
                val multi=p.allowDoubleDays || p.runDays+p.strengthDays+p.calDays+p.conditioningDays>p.days.size ||
                    p.weeklySlots.values.flatten().let { it.size!=it.distinct().size }
                val available=if(multi || otherMinutes>0) minOf(p.minutes,p.dailyMinutes-otherMinutes) else p.minutes
                val timeCap=if(p.timeBudgetEnabled) available*60.0/p.planningPace else Double.MAX_VALUE
                val observedCap=pattern?.easyKm?.let { easy -> when(role.getValue(s.id)) {
                    "EASY"->easy+1.0; "RECOVERY"->easy; "ENDURANCE"->easy*1.25; else->120.0
                } } ?: 120.0
                minOf(units(exposure*fraction),units(minOf(timeCap,120.0,observedCap))).coerceAtLeast(0)
            }
            runs.forEachIndexed { i,s ->
                ledger?.record(w,"SESSION_LIMIT","SESSION_EXPOSURE_CAP",
                    s.distanceKm,caps[i]/10.0,null,
                    "session=${s.id}; role=${role.getValue(s.id)}; evaluated cap only, not an executed distance reduction")
            }
            ledger?.record(w,"WEEK_CAPACITY","SUM_SESSION_CAPS",
                budget/10.0,caps.sum()/10.0,minOf(budget,caps.sum())/10.0,
                "Actual maximum allocatable before weighted distribution")
            val easyReference=pattern?.easyKm
            val longReference=pattern?.longKm ?: p.longestKm.takeIf { it>0 }
            val longWeight=if(easyReference!=null && longReference!=null) (longReference/easyReference).coerceIn(1.0,3.0) else 1.8
            val weight=runs.map { when(role.getValue(it.id)) { "LONG"->longWeight; "ENDURANCE"->1.3; "QUALITY"->1.15; "RECOVERY"->0.65; else->1.0 } }
            val distance=IntArray(runs.size)
            repeat(minOf(budget,caps.sum())) {
                val index=runs.indices.filter { distance[it]<caps[it] }
                    .minWithOrNull(compareBy<Int> { (distance[it]+1)/weight[it] }.thenBy { runs[it].id })
                if(index!=null) distance[index]++
            }
            val weightedKm=distance.sum()/10.0
            ledger?.record(w,"DISTRIBUTION","WEIGHTED_ALLOCATOR",
                minOf(budget,caps.sum())/10.0,null,weightedKm,
                "Executed weighted allocation in 100 m units")
            val longIndex=runs.indexOfFirst { it.kind=="long" }
            if(runs.size>=3 && longIndex>=0)
                distance[longIndex]=minOf(distance[longIndex],floor((distance.sum()-distance[longIndex])*2.0/3.0+1e-8).toInt())
            ledger?.record(w,"LONG_SHARE","INITIAL_LONG_SHARE",
                weightedKm,null,distance.sum()/10.0,
                "40 percent long-run share check; removed volume is not redistributed")
            val beforeRoundingKm=distance.sum()/10.0
            if(distance.all { it>0 }) {
                val bounded=Engine15DistanceRounding.allocate(
                    distance,caps.toIntArray(),
                    runs.map { units(it.distanceKm ?: 0.0) }.toIntArray(),
                    budget,longIndex,
                    runs.indices.filter { runs[it].id in qualityIds }.toSet()
                )
                for(i in distance.indices)distance[i]=bounded[i]
            }
            ledger?.record(w,"ROUNDING","DISTANCE_ROUNDING",
                beforeRoundingKm,budget/10.0,distance.sum()/10.0,
                "Executed grid rounding subject to upstream limits")
            if(distance.any { it<=0 }) throw PlanConstraint("The mileage and recent-session limits cannot support every selected running day. Review mileage or running frequency.")

            // This is a distance budget, not a claimed physiological 80/20 distribution.
            var workBudget=floor(distance.sum()*100*0.20).toInt()
            // Even when the request reports two quality days, two new sessions
            // must not multiply a single observed quality-work exposure.
            pattern?.verifiedWeeklyQualityWorkKm?.let { observed ->
                workBudget=minOf(workBudget,floor(observed*1000.0).toInt())
            }
            if(priorBuildWork!=null) workBudget=minOf(workBudget,priorBuildWork!!)
            var workUsed=0
            var qualityPosition=0
            val generated=runs.mapIndexed { index,s ->
                val meters=distance[index]*100
                // Verified completed work supports only its matching family;
                // user-requested quality frequency and weekly ceilings still bind.
                val family=if(p.allowedQualityTypes.isEmpty() &&
                    pattern?.verifiedWorkKm(p.preferredQuality)!=null) p.preferredQuality
                    else selectFamily(p,w,qualityPosition)
                val observedWorkKm=pattern?.verifiedWorkKm(family)
                val quality=if(s.id in qualityIds) quality(p,w,family,meters,workBudget-workUsed,observedWorkKm) else null
                val actualRole=if(s.id in qualityIds && quality==null)"EASY" else role.getValue(s.id)
                val prescription=quality ?: RunningPrescription(actualRole,null,listOf(RunningSegment("EASY",meters,
                    if(p.experience==0) "Alternate easy running and walking; keep conversation comfortable" else "Conversational; no pace target")))
                if(quality!=null) { workUsed+=quality.workMeters; qualityPosition++ }
                val title=if(quality!=null) qualityTitle(quality) else when(actualRole) {
                    "LONG"->"Long run"; "ENDURANCE"->"Aerobic run"; "RECOVERY"->"Recovery run"; else->"Easy run"
                }
                val minutes=ceil(meters/1000.0*p.planningPace/60).toInt().coerceAtLeast(1)
                s.copy(distanceKm=meters/1000.0,minutes=minutes,quality=quality!=null,
                    kind=if(s.kind=="long")"long" else if(quality!=null)"quality" else "easy",title=title,
                    runningPrescription=prescription,steps=steps(prescription),reason=
                        "${actualRole}: ${purpose(actualRole)} Distances reconcile to this session; duration is a planning estimate, not an intensity target. " +
                        if(s.id in qualityIds && quality==null) "Quality was withheld because warm-up, work, recovery and cool-down did not fit the available distance/work budget." else
                        "The weekly and recent-session ceilings bind; no catch-up mileage or measured fitness is inferred." +
                        if(quality!=null && observedWorkKm!=null)
                            " VERIFIED_QUALITY_WORK: Repeated completed ${family.lowercase()} sessions support up to ${observedWorkKm} km of work; this is a ceiling, not an automatic progression or pace estimate."
                        else "")
            }
            // Freeze the initial work envelope during a mileage build; variation does not imply new fitness.
            val finalized=finalizeWeek(generated,p,pattern,ledger,w)
            ledger?.record(w,"ROLE_RECONCILIATION","FINAL_ROLE_RECHECK",
                ledger?.runningKm(generated),null,ledger?.runningKm(finalized),
                "Executed final role exposure, long-share and quality-work reconciliation")
            // Convert only AFTER the legacy dose, spacing and long-share checks.
            // Do not fabricate distance to fill an unused quality-session allowance.
            val mixed=finalized.map { toMixedSupportSteps(p,it) }
            ledger?.record(w,"MIXED_UNITS","Q2_TIMED_SUPPORT_CONVERSION",
                ledger?.runningKm(finalized),null,ledger?.runningKm(mixed),
                "After replacing quality warm-up/cool-down distances by planning-pace estimates")
            val converted=reconcileMixedWeek(finalized,mixed,pattern,p,ledger,w)
            ledger?.record(w,"MIXED_RECONCILIATION","Q2_POST_CONVERSION_CHECK",
                ledger?.runningKm(mixed),null,ledger?.runningKm(converted),
                "Executed post-Q2 long-share and work-share checks")
            ledger?.record(w,"FINAL","FINAL_PRESCRIPTION",
                ledger?.runningKm(finalized),null,ledger?.runningKm(converted),
                "Final distance includes Q2 estimates, not exact target km; quality sessions=${converted.count { it.quality }}")
            val deliveredWork=converted.sumOf { it.runningPrescription!!.workMeters }
            if(!recovery && !taper && deliveredWork>0 && priorBuildWork==null) priorBuildWork=deliveredWork
            val replacements=converted.associateBy { it.id }
            result+=rows.map { replacements[it.id] ?: it }
        }
        val byId=result.associateBy { it.id }
        return baseline.map { byId.getValue(it.id) }
    }

    /** Final roles, not requested roles, determine exposure and weekly work limits. */
    private fun finalizeWeek(generated:List<PlannedSession>,p:PlanRequest,
        pattern:Engine15RunHistory?,ledger:Engine15WorkloadLedger?=null,w:Int=0):List<PlannedSession> {
        fun easy(s:PlannedSession,meters:Int,role:String):PlannedSession {
            if(meters<=0)throw PlanConstraint("The final running roles cannot fit this weekly distance. Review frequency or mileage.")
            val prescription=RunningPrescription(role,null,listOf(RunningSegment("EASY",meters,"Comfortable conversational running")))
            return s.copy(distanceKm=meters/1000.0,minutes=minOf(s.minutes,ceil(meters/1000.0*p.planningPace/60).toInt().coerceAtLeast(1)),
                quality=false,kind=if(role=="LONG")"long" else "easy",title=if(role=="LONG")"Long run" else "Easy run",
                runningPrescription=prescription,steps=steps(prescription),reason=s.reason+" Final role exposure reconciled without redistribution.")
        }
        fun cap(s:PlannedSession)=minOf(s.runningPrescription!!.meters,pattern?.easyKm?.let { units(it+1.0)*100 } ?: Int.MAX_VALUE)
        var runs=generated.map { s -> if(s.runningPrescription?.role=="EASY" && cap(s)<s.runningPrescription.meters)easy(s,cap(s),"EASY") else s }
        ledger?.record(w,"ROLE_RECONCILIATION","OBSERVED_EASY_ROLE_CAP",
            ledger?.runningKm(generated),pattern?.easyKm?.plus(1.0),ledger?.runningKm(runs),
            "Executed after role assignment; cap applies only to EASY roles")
        // A role downgrade can shrink the week; recheck long share and work budget on the delivered total.
        repeat(generated.count { it.quality }+1) { pass ->
            val beforeLong=ledger?.runningKm(runs)
            val others=runs.filter { it.kind!="long" }.sumOf { it.runningPrescription!!.meters }
            runs=runs.map { s ->
                val maxLong=(floor(others*2.0/3/100+1e-8)*100).toInt()
                if(runs.size>=3 && s.kind=="long" && s.runningPrescription!!.meters>maxLong)easy(s,maxLong,"LONG") else s
            }
            if(ledger!=null && beforeLong!=ledger.runningKm(runs))
                ledger.record(w,"ROLE_RECONCILIATION","LONG_SHARE_RECHECK",
                    beforeLong,null,ledger.runningKm(runs),"Executed pass ${pass+1}")
            if(runs.sumOf { it.runningPrescription!!.workMeters }>runs.sumOf { it.runningPrescription!!.meters }*0.2+1e-8) {
                val beforeQuality=ledger?.runningKm(runs)
                val last=runs.lastOrNull { it.quality }
                runs=runs.map { if(it.id==last?.id)easy(it,cap(it),"EASY") else it }
                if(ledger!=null && last!=null)
                    ledger.record(w,"ROLE_RECONCILIATION","QUALITY_SHARE_DOWNGRADE",
                        beforeQuality,null,ledger.runningKm(runs),
                        "Executed pass ${pass+1}; demoted quality session ${last.id} to easy")
            }
        }
        return runs
    }

    /** Recheck original hard-work and long-run limits on the smaller mixed-unit week. */
    private fun reconcileMixedWeek(
        originals:List<PlannedSession>,converted:List<PlannedSession>,
        pattern:Engine15RunHistory?,p:PlanRequest,
        ledger:Engine15WorkloadLedger?=null,w:Int=0
    ):List<PlannedSession> {
        val source=originals.associateBy { it.id }
        fun budgetKm(s:PlannedSession)=s.distanceKm ?: s.runningPrescription?.estimatedDistanceKm ?: 0.0
        fun toEasy(s:PlannedSession):PlannedSession {
            val old=source.getValue(s.id)
            val oldMeters=old.runningPrescription!!.meters
            val limit=pattern?.easyKm?.let { units(it+1.0)*100 } ?: Int.MAX_VALUE
            val meters=minOf(oldMeters,limit)
            if(meters<=0)throw PlanConstraint("A quality fallback cannot fit even a short easy run.")
            val prescription=RunningPrescription("EASY",null,listOf(RunningSegment("EASY",meters,
                "Conversational easy run; quality withheld to preserve weekly workload limits")))
            return old.copy(distanceKm=meters/1000.0,
                minutes=minOf(old.minutes,ceil(meters/1000.0*p.planningPace/60.0).toInt().coerceAtLeast(1)),
                kind="easy",quality=false,title="Easy run",runningPrescription=prescription,
                steps=steps(prescription),reason=old.reason+" Quality withheld after timed-step reconciliation; no catch-up.")
        }
        var runs=converted
        repeat(converted.count { it.quality }+2) { pass ->
            if(runs.size>=3) {
                val beforeLong=ledger?.runningKm(runs)
                val otherKm=runs.filter { it.kind!="long" }.sumOf(::budgetKm)
                val maximum=units(otherKm*2.0/3.0)*100
                runs=runs.map { s ->
                    if(s.kind=="long" && (s.distanceKm ?: 0.0)*1000>maximum+1e-8) {
                        if(maximum<=0)throw PlanConstraint("Mixed-unit session constraints leave no valid long-run distance.")
                        val prescription=RunningPrescription("LONG",null,listOf(
                            RunningSegment("EASY",maximum,"Comfortable conversational running")))
                        s.copy(distanceKm=maximum/1000.0,
                            minutes=minOf(s.minutes,ceil(maximum/1000.0*p.planningPace/60.0).toInt().coerceAtLeast(1)),
                            runningPrescription=prescription,steps=steps(prescription),
                            reason=s.reason+" Reduced to preserve 40% long-run share after mixed-unit conversion.")
                    } else s
                }
                if(ledger!=null && beforeLong!=ledger.runningKm(runs))
                    ledger.record(w,"MIXED_RECONCILIATION","Q2_LONG_SHARE_RECHECK",
                        beforeLong,null,ledger.runningKm(runs),"Executed pass ${pass+1}")
            }
            val weeklyMeters=runs.sumOf { budgetKm(it)*1000 }
            val workMeters=runs.sumOf { it.runningPrescription?.workMeters ?: 0 }
            if(workMeters>weeklyMeters*0.20+1e-8) {
                val beforeQuality=ledger?.runningKm(runs)
                val last=runs.lastOrNull { it.quality }
                if(last!=null)runs=runs.map { if(it.id==last.id)toEasy(it) else it }
                if(ledger!=null && last!=null)
                    ledger.record(w,"MIXED_RECONCILIATION","Q2_QUALITY_SHARE_DOWNGRADE",
                        beforeQuality,null,ledger.runningKm(runs),
                        "Executed pass ${pass+1}; demoted ${last.id} after mixed-unit estimation")
            }
        }
        return runs
    }

    /**
     * Q2: preserve Q3-selected WORK and RECOVERY distances. Replace
     * remainder-funded WARM_UP / COOL_DOWN with bounded times.
     * Old approved v1 plans are never rewritten.
     */
    private fun toMixedSupportSteps(p:PlanRequest,s:PlannedSession):PlannedSession {
        if(!s.quality)return s
        val legacy=s.runningPrescription ?: return s
        if(legacy.mixedSegments!=null)return s
        val originalKm=s.distanceKm ?: return s
        val known=legacy.segments.filter { it.purpose!="WARM_UP" && it.purpose!="COOL_DOWN" }
        val warmChoices=if(legacy.family in setOf("Hills","Intervals")) listOf(12,10) else listOf(10)
        var selected:List<RunningStepV2>?=null
        var estimatedKm=0.0
        var estimatedMinutes=0
        for(warm in warmChoices) {
            for(cool in listOf(8,5)) {
                val mixed=buildList {
                    add(RunningStepV2("WARM_UP","Easy conversational running",seconds=warm*60))
                    known.forEach { add(RunningStepV2(it.purpose,it.effort,meters=it.meters)) }
                    add(RunningStepV2("COOL_DOWN","Easy running or walking",seconds=cool*60))
                }
                val knownMeters=mixed.sumOf { it.meters ?: 0 }
                val supportSeconds=mixed.sumOf { it.seconds ?: 0 }
                val km=(knownMeters+supportSeconds*1000.0/p.planningPace)/1000.0
                val minutes=ceil((supportSeconds+knownMeters*p.planningPace/1000.0)/60.0).toInt()
                if(km<=originalKm+1e-8 && (!p.timeBudgetEnabled || minutes<=s.minutes)) {
                    selected=mixed;estimatedKm=km;estimatedMinutes=minutes
                    break
                }
            }
            if(selected!=null)break
        }
        if(selected==null) {
            val easy=RunningPrescription("EASY",null,listOf(RunningSegment("EASY",legacy.meters,
                "Easy conversational running; the quality set did not fit timed support-step minimums")))
            return s.copy(quality=false,kind="easy",title="Easy run",runningPrescription=easy,
                steps=steps(easy),reason=s.reason+" Quality withheld: bounded warm-up, work, recovery and cool-down cannot fit the allocated session.")
        }
        val prescription=RunningPrescription("QUALITY",legacy.family,emptyList(),selected,estimatedKm)
        return s.copy(distanceKm=null,minutes=estimatedMinutes,runningPrescription=prescription,
            steps=steps(prescription),reason=s.reason+" Mixed-unit Q2 prescription: warm-up/cool-down are bounded time targets; total distance is an estimate only. Unused distance is not redistributed.")
    }

    private fun applyResponse(p:PlanRequest,normal:List<PlannedSession>,review:Engine15Feedback.DraftReview):List<PlannedSession> {
        fun reduced(s:PlannedSession,meters:Int?,minutes:Int,reason:String):PlannedSession {
            if(meters!=null && meters<=0)throw PlanConstraint("The reviewed recovery target is too small for a distance session. Review running frequency or choose rest.")
            val prescription=meters?.let { RunningPrescription("RECOVERY",null,listOf(RunningSegment("EASY",it,"Easy running or walking; stop if recovery feels poor"))) }
            return s.copy(title="Recovery run",minutes=minutes,distanceKm=meters?.div(1000.0),quality=false,
                kind=if(s.kind=="long")"long" else "easy",runningPrescription=prescription,
                steps=prescription?.let(::steps) ?: listOf("Up to $minutes minutes of comfortable running or walking; rest if needed."),
                reason=s.reason+" $reason")
        }
        // Apply negative evidence only as subtraction. Reallocation here would create catch-up load.
        val limited=normal.map { s ->
            if(s.discipline!="run" || s.date !in review.asOf..review.recoveryThrough)s else {
                val meters=s.distanceKm?.let { units(it*0.75)*100 }
                val minutes=if(meters==null)maxOf(1,floor(s.minutes*0.75).toInt())
                    else minOf(s.minutes,ceil(meters/1000.0*p.planningPace/60).toInt().coerceAtLeast(1))
                reduced(s,meters,minutes,"Recent explicit difficulty: reduced easy exposure, no hard work and no redistribution to other days.")
            }
        }
        val corrected=limited.groupBy { week(p,it) }.values.flatMap { rows ->
            val runs=rows.filter { it.discipline=="run" }
            val others=runs.filter { it.kind!="long" }.sumOf { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0 }
            rows.map { s ->
                val maximum=units(others*2.0/3)*100
                if(runs.size<3 || s.discipline!="run" || s.kind!="long" || s.distanceKm==null || s.distanceKm*1000<=maximum+1e-8)s
                else reduced(s,maximum,minOf(s.minutes,ceil(maximum/1000.0*p.planningPace/60).toInt().coerceAtLeast(1)),
                    "Long-run exposure reduced to preserve its share of the now smaller week.")
            }
        }.associateBy { it.id }
        val result=normal.map { corrected.getValue(it.id) }
        check(result.zip(normal).all { (a,b) -> a.minutes<=b.minutes && (a.distanceKm==null || a.distanceKm<=requireNotNull(b.distanceKm)+1e-8) })
        return result
    }

    private fun selectFamily(p:PlanRequest,w:Int,slot:Int):String {
        if(p.allowedQualityTypes.isEmpty())return "Intervals"
        val preferred=p.preferredQuality.takeIf { it in p.allowedQualityTypes } ?: p.allowedQualityTypes.sorted().first()
        val ordered=listOf(preferred)+(p.allowedQualityTypes-preferred).sorted()
        return ordered[(slot+w/4)%ordered.size]
    }
    private fun quality(p:PlanRequest,w:Int,family:String,total:Int,budget:Int,observedWorkKm:Double?=null):RunningPrescription? {
        val warm=ceil(600000.0/p.planningPace/10).toInt()*10
        val cool=ceil(480000.0/p.planningPace/10).toInt()*10
        val phase=w%4%3
        val reps=when(family) {
            "Progression"->3; "Tempo"->if(phase==2)1 else 2; "Hills","Fartlek"->6
            "Intervals"->when(p.goal) { "Improve 5K"->listOf(5,4,3)[phase]; "Half marathon"->listOf(3,2,2)[phase]; "Marathon"->listOf(2,2,1)[phase]; else->listOf(4,3,2)[phase] }
            else->listOf(4,3,2)[phase]
        }
        val recoveryRatio=when(family) { "Progression"->0.0; "Tempo"->0.25; "Hills","Fartlek"->1.0; else->0.5 }
        val workSeconds=when(family) {
            "Hills"->180; "Fartlek"->360
            "Intervals"->when(p.goal) { "Improve 5K"->540; "Half marathon","Marathon"->960; else->720 }
            else->720
        }
        val workLimit=minOf(budget,floor(total*(if(observedWorkKm!=null)0.60 else 0.40)).toInt(),observedWorkKm?.let { floor(it*1000).toInt() } ?: floor(workSeconds*1000.0/p.planningPace).toInt())
        val minimum=if(family in setOf("Hills","Fartlek"))50 else 200
        // A completed total work distance cannot verify longer individual
        // repeats. Preserve hill/fartlek and goal-specific interval caps;
        // only sustained Threshold work has the prior 3 km evidence allowance.
        val maximum=when(family) {
            "Hills"->100
            "Fartlek"->200
            "Tempo","Progression"->3000
            "Threshold"->if(observedWorkKm!=null)3000 else
                when(p.goal) { "Improve 5K"->600; "Half marathon"->2000; "Marathon"->2400; else->1200 }
            else->when(p.goal) { "Improve 5K"->600; "Half marathon"->2000; "Marathon"->2400; else->1200 }
        }
        val perRep=(minOf(maximum,workLimit/reps,((total-warm-cool)/(reps+(reps-1)*recoveryRatio)).toInt())/10)*10
        if(perRep<minimum)return null
        val recovery=(perRep*recoveryRatio/10).toInt()*10
        val effort=when {
            family in setOf("Tempo","Progression") || family=="Intervals" && p.goal in setOf("Marathon","Aerobic foundation","General fitness") ->"Steady controlled effort (5–6/10), never race"
            family=="Hills" ->"Controlled uphill effort (6/10), never sprint"
            family=="Intervals" && p.goal=="Half marathon" ->"Sustained controlled effort (6/10), never race"
            else ->"Comfortably hard (6–7/10), slow down if form or breathing deteriorates"
        }
        val segments=mutableListOf(RunningSegment("WARM_UP",warm,"Easy conversational running"))
        repeat(reps) { i ->
            val target=if(family=="Progression") listOf("Comfortable aerobic effort (4/10)","Steady effort (5/10)","Controlled steady effort (5–6/10), no sprint finish")[i] else effort
            segments+=RunningSegment("WORK",perRep,target)
            if(i<reps-1 && recovery>0)segments+=RunningSegment("RECOVERY",recovery,if(family=="Hills")"Walk or jog easily downhill" else "Easy jog or walk")
        }
        segments+=RunningSegment("COOL_DOWN",total-segments.sumOf { it.meters },"Easy conversational running or walking")
        return RunningPrescription("QUALITY",family,segments)
    }
    /** A readable workout identity derived from actual saved work segments. */
    private fun qualityTitle(prescription:RunningPrescription):String {
        val work=prescription.segments.filter { it.purpose=="WORK" }
        return when(prescription.family) {
            "Threshold" -> if(work.size>1) "Broken Threshold" else "Threshold Run"
            "Tempo" -> if(work.size>1) "Tempo Intervals" else "Tempo Run"
            "Hills" -> "Hill Repeats"
            "Fartlek" -> "Fartlek Run"
            "Progression" -> "Progression Run"
            else -> {
                val length=work.firstOrNull()?.meters
                if(length==null || work.any { it.meters!=length }) "Interval Repeats"
                else {
                    val distance=if(length%1000==0) "${length/1000}K"
                        else "${length}m"
                    "$distance Repeats"
                }
            }
        }
    }
    private fun purpose(role:String)=when(role) {
        "RECOVERY"->"Shorter easy exposure after a demanding day; recovery is the priority."
        "ENDURANCE"->"A secondary aerobic stimulus away from the main demanding sessions."
        "LONG"->"Develop comfortable time on feet; no fast finish or hidden quality."
        "QUALITY"->"A bounded work dose with explicit recoveries; future variation is provisional."
        else->"Build repeatable conversational running, without adding another demanding session."
    }
    private fun steps(p:RunningPrescription):List<String> {
        fun label(purpose:String)=when(purpose) { "WARM_UP"->"warm-up"; "COOL_DOWN"->"cool-down"; "WORK"->"work"; "RECOVERY"->"recovery"; else->"easy running" }
        return p.mixedSegments?.mapIndexed { i,s ->
            val extent=if(s.meters!=null) s.meters.toString()+" m" else s.seconds.toString()+" s"
            (i+1).toString()+". "+extent+" "+label(s.purpose)+" — "+s.effort+"."
        } ?: p.segments.mapIndexed { i,s ->
            (i+1).toString()+". "+s.meters+" m "+label(s.purpose)+" — "+s.effort+"."
        }
    }
}
