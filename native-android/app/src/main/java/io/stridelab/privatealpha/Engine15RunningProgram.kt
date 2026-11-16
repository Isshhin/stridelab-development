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
internal data class RunningPrescription(val role:String, val family:String?, val segments:List<RunningSegment>) {
    init {
        require(role in setOf("RECOVERY","EASY","ENDURANCE","LONG","QUALITY") && segments.isNotEmpty() && segments.size<=50)
        require(segments.sumOf { it.meters }<=120000)
        require(if(role=="QUALITY") family in setOf("Threshold","Tempo","Intervals","Hills","Fartlek","Progression") && segments.any { it.purpose=="WORK" }
            else family==null && segments.none { it.purpose=="WORK" })
    }
    val meters get()=segments.sumOf { it.meters }
    val workMeters get()=segments.filter { it.purpose=="WORK" }.sumOf { it.meters }
    fun encode()=JSONObject().put("version",1).put("role",role).put("family",family).put("segments",JSONArray(segments.map { it.encode() }))
    companion object {
        fun decode(j:JSONObject):RunningPrescription {
            require(j.getInt("version")==1)
            return RunningPrescription(j.getString("role"),j.nullString("family"),j.getJSONArray("segments").objects().map(RunningSegment::decode))
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

    fun compose(p:PlanRequest, baseline:List<PlannedSession>,review:Engine15Feedback.DraftReview?=null,pattern:Engine15RunHistory?=null):List<PlannedSession> {
        if(review!=null)return applyResponse(p,compose(p,baseline,pattern=pattern),review)
        val result=mutableListOf<PlannedSession>()
        var priorBuildWork:Int?=null
        for((w,rows) in baseline.groupBy { week(p,it) }.toSortedMap()) {
            val runs=rows.filter { it.discipline=="run" }.sortedBy { it.date }
            if(runs.isEmpty()) { result+=rows; continue }
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
                continue
            }
            var budget=units(minOf(runs.sumOf { it.distanceKm ?: 0.0 },Engine15.runningCeiling(w,p.weeklyKm,p.targetWeeklyKm)))
            if(recovery && prior.isNotEmpty()) budget=minOf(budget,units(prior.sumOf { it.distanceKm ?: 0.0 }*0.8))
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
            val longIndex=runs.indexOfFirst { it.kind=="long" }
            if(runs.size>=3 && longIndex>=0)
                distance[longIndex]=minOf(distance[longIndex],floor((distance.sum()-distance[longIndex])*2.0/3.0+1e-8).toInt())
            if(distance.all { it>0 }) {
                val bounded=Engine15DistanceRounding.allocate(
                    distance,caps,
                    runs.map { units(it.distanceKm ?: 0.0) }.toIntArray(),
                    budget,longIndex,
                    runs.indices.filter { runs[it].id in qualityIds }.toSet()
                )
                for(i in distance.indices)distance[i]=bounded[i]
            }
            if(distance.any { it<=0 }) throw PlanConstraint("The mileage and recent-session limits cannot support every selected running day. Review mileage or running frequency.")

            // This is a distance budget, not a claimed physiological 80/20 distribution.
            var workBudget=floor(distance.sum()*100*0.20).toInt()
            if(priorBuildWork!=null) workBudget=minOf(workBudget,priorBuildWork!!)
            var workUsed=0
            var qualityPosition=0
            val generated=runs.mapIndexed { index,s ->
                val meters=distance[index]*100
                val family=if(p.allowedQualityTypes.isEmpty() && p.preferredQuality=="Threshold" && pattern?.thresholdWorkKm!=null) "Threshold" else selectFamily(p,w,qualityPosition)
                val quality=if(s.id in qualityIds) quality(p,w,family,meters,workBudget-workUsed,if(family=="Threshold")pattern?.thresholdWorkKm else null) else null
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
                        "The weekly and recent-session ceilings bind; no catch-up mileage or measured fitness is inferred.")
            }
            // Freeze the initial work envelope during a mileage build; variation does not imply new fitness.
            val finalized=finalizeWeek(generated,p,pattern)
            val deliveredWork=finalized.sumOf { it.runningPrescription!!.workMeters }
            if(!recovery && !taper && deliveredWork>0 && priorBuildWork==null) priorBuildWork=deliveredWork
            val replacements=finalized.associateBy { it.id }
            result+=rows.map { replacements[it.id] ?: it }
        }
        val byId=result.associateBy { it.id }
        return baseline.map { byId.getValue(it.id) }
    }

    /** Final roles, not requested roles, determine exposure and weekly work limits. */
    private fun finalizeWeek(generated:List<PlannedSession>,p:PlanRequest,pattern:Engine15RunHistory?):List<PlannedSession> {
        fun easy(s:PlannedSession,meters:Int,role:String):PlannedSession {
            if(meters<=0)throw PlanConstraint("The final running roles cannot fit this weekly distance. Review frequency or mileage.")
            val prescription=RunningPrescription(role,null,listOf(RunningSegment("EASY",meters,"Comfortable conversational running")))
            return s.copy(distanceKm=meters/1000.0,minutes=minOf(s.minutes,ceil(meters/1000.0*p.planningPace/60).toInt().coerceAtLeast(1)),
                quality=false,kind=if(role=="LONG")"long" else "easy",title=if(role=="LONG")"Long run" else "Easy run",
                runningPrescription=prescription,steps=steps(prescription),reason=s.reason+" Final role exposure reconciled without redistribution.")
        }
        fun cap(s:PlannedSession)=minOf(s.runningPrescription!!.meters,pattern?.easyKm?.let { units(it+1.0)*100 } ?: Int.MAX_VALUE)
        var runs=generated.map { s -> if(s.runningPrescription?.role=="EASY" && cap(s)<s.runningPrescription.meters)easy(s,cap(s),"EASY") else s }
        // A role downgrade can shrink the week; recheck long share and work budget on the delivered total.
        repeat(generated.count { it.quality }+1) {
            val others=runs.filter { it.kind!="long" }.sumOf { it.runningPrescription!!.meters }
            runs=runs.map { s ->
                val maxLong=(floor(others*2.0/3/100+1e-8)*100).toInt()
                if(runs.size>=3 && s.kind=="long" && s.runningPrescription!!.meters>maxLong)easy(s,maxLong,"LONG") else s
            }
            if(runs.sumOf { it.runningPrescription!!.workMeters }>runs.sumOf { it.runningPrescription!!.meters }*0.2+1e-8) {
                val last=runs.lastOrNull { it.quality }
                runs=runs.map { if(it.id==last?.id)easy(it,cap(it),"EASY") else it }
            }
        }
        return runs
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
            val others=runs.filter { it.kind!="long" }.sumOf { it.distanceKm ?: 0.0 }
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
        val maximum=if(observedWorkKm!=null)3000 else when(family) { "Hills"->100; "Fartlek"->200; "Tempo","Progression"->3000; else->when(p.goal) { "Improve 5K"->600; "Half marathon"->2000; "Marathon"->2400; else->1200 } }
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
    private fun steps(p:RunningPrescription)=p.segments.mapIndexed { i,s ->
        val label=when(s.purpose) { "WARM_UP"->"warm-up"; "COOL_DOWN"->"cool-down"; "WORK"->"work"; "RECOVERY"->"recovery"; else->"easy running" }
        "${i+1}. ${s.meters} m $label — ${s.effort}."
    }
}
