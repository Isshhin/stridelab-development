package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

internal val ENGINE1_GOALS=listOf("General fitness","Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon","General strength","Muscle development","Strength endurance","Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations","Conditioning")
/** Native port/adaptation of the accepted Phase 4E run/strength/calendar model.
 * Fixed explainable rules, not athlete-response learning or Advanced Engine 2.0. */
internal object Engine1:PlanGenerationEngine {
    override val version=ENGINE1_VERSION
    override val policyId=ENGINE1_POLICY
    override fun generate(request:PlanRequest,owner:String,id:String,now:Instant):PlanSnapshot {
        validate(request);RecordPolicy.identifier(owner);RecordPolicy.identifier(id)
        val p=request; val all=mutableListOf<PlannedSession>()
        val why=mutableListOf("Engine 1.0 uses versioned baseline rules adapted from the accepted Phase 4E planner. Prescriptions are saved snapshots; future engines will not rewrite them.",
            "A smartwatch is optional. Targets are planned estimates, not recorded performance. Use conversational effort for easy running and leave several controlled repetitions in reserve for resistance work.",
            "Every selected discipline is scheduled. Conflicting requests are rejected for review instead of silently dropping workouts.",
            "Calisthenics skill levels do not advance automatically with elapsed weeks. Change level only after controlled, repeatable practice.")
        if(p.runDays>0) why.add("Running planning pace is ${p.planningPace.toInt()} seconds/km, an editable planning assumption. It is not a measured pace. Time and recent-distance limits may reduce the requested mileage.")
        if(p.weeklyKm==0.0 && p.runDays>0) why.add("No recent running distance was supplied. This plan uses time-based easy run/walk, with no invented mileage or hard running.")
        if(p.experience==0 && p.qualityCount>0) why.add("Quality sessions were changed to easy run/walk for a beginner; no hard sessions are prescribed.")
        if(p.returning) why.add("Return-from-break weeks start at 60% of reported mileage, with quality withheld for the first two weeks. Missed work is not added back automatically.")
        if(p.strengthDays>1 && p.runDays>0) why.add("Resistance sessions alternate lower/full-body and upper-body work to limit overlapping leg stress. Lower-body work is kept away from quality runs and the day before the long run, including week boundaries.")
        if(p.limitations.isNotBlank()) why.add("Your limitations are retained for review: ${p.limitations}. This baseline does not infer clinical accommodations; excluded exercises are never prescribed.")
        if(p.secondaryGoal.isNotBlank()) why.add("Secondary goal recorded: ${p.secondaryGoal}. The selected disciplines, experience, equipment and time budget determine this baseline block; no outcome is guaranteed.")
        for(week in 0 until p.weeks) {
            val recovery=week%4==3
            val taper=p.goal in setOf("Half marathon","Marathon") && p.weeks>=8 && week>=p.weeks-2
            val factor=(if(p.returning) .6 else .95)*minOf(1.2,1.0+week*.05)*(if(recovery) .8 else 1.0)*(if(taper) .7 else 1.0)
            val weekKm=p.weeklyKm*factor
            val q=if(p.experience==0 || p.weeklyKm<10 || (p.returning&&week<2)) 0 else p.qualityCount
            val templates=mutableListOf<PlannedSession>()
            for(i in 0 until p.runDays) {
                val kind=when { i==0->"long";i<=q->"quality";else->"easy" }
                val weight=if(kind=="long"&&p.runDays>1) 1.4 else 1.0
                val share=weekKm*weight/(p.runDays+if(p.runDays>1) .4 else 0.0)
                val maxDistance=p.minutes*60.0/p.planningPace
                val longestLimit=if(p.longestKm>0) p.longestKm+minOf(2.0,week*.5) else maxOf(1.0,p.weeklyKm/maxOf(1,p.runDays))
                val distance=if(p.weeklyKm==0.0) null else round(minOf(share,maxDistance,if(kind=="long") longestLimit else share)*10)/10
                val beginnerMinutes=minOf(p.minutes,(if(kind=="long") 20 else 15)+week/2*2)
                val minutes=if(distance==null) beginnerMinutes else maxOf(10,ceil(distance*p.planningPace/60).toInt()).coerceAtMost(p.minutes)
                val quality=kind=="quality" && minutes>=30 && !recovery
                val title=when { quality->if(p.goal=="Improve 5K") "Controlled intervals" else "Controlled tempo intervals";p.experience==0||p.weeklyKm==0.0->if(kind=="long") "Longer easy run/walk" else "Easy run/walk";kind=="long"->"Long easy run";else->"Easy run" }
                val steps=if(quality) listOf("10 min easy warm-up","3 × 2 min controlled hard, with 2 min easy between; never sprint","${minutes-20} min easy finish and additional easy running; stop when your time budget is reached")
                    else listOf("${minOf(5,minutes/3)} min comfortable walk or easy jog",if(p.experience==0||p.weeklyKm==0.0) "Alternate 1 min easy running with 1 min walking; shorten the run intervals if needed" else "Keep the main portion conversational; no fixed effort derived from missing sensors","Finish with a comfortable walk; total session $minutes min")
                templates.add(PlannedSession("$id-w$week-run$i",p.start.plusWeeks(week.toLong()),"run",title,minutes,distance,kind,quality=quality,steps=steps,reason=if(kind=="long") "On your single preferred long-run day, kept easy and bounded by recent distance/time." else if(quality) "Your requested quality work, with recovery spacing." else "Easy aerobic practice within the selected time and distance limits."))
            }
            for(i in 0 until p.strengthDays) {
                val upper=p.runDays>0 && i%2==1
                val exercises=strengthExercises(p,upper,recovery)
                templates.add(PlannedSession("$id-w$week-str$i",p.start.plusWeeks(week.toLong()),"str",if(upper) "Upper body support" else if(i%2==0) "Full body A" else "Full body B",resistanceMinutes(exercises),null,"strength",lower=!upper,exercises=exercises,
                    steps=listOf("5 min comfortable general warm-up","Practise the first set with an easy load","3 min easy finish"),reason=if(upper) "Supports strength while reducing leg overlap with running." else "Build foundational squat, hinge, push/pull and trunk control; equipment-aware options."))
            }
            for(i in 0 until p.calDays) {
                val ex=calExercises(p,recovery)
                templates.add(PlannedSession("$id-w$week-cal$i",p.start.plusWeeks(week.toLong()),"cal",if(p.calLevel==0) "Calisthenics foundations" else "Controlled skill practice",resistanceMinutes(ex),null,"calisthenics",exercises=ex,
                    steps=listOf("5 min comfortable warm-up","Use the current controllable variation; no automatic stage promotion","3 min easy finish"),reason="Practice the selected current ability with several repetitions in reserve; progress by demonstrated control, not calendar time."))
            }
            for(i in 0 until p.conditioningDays) templates.add(PlannedSession("$id-w$week-cond$i",p.start.plusWeeks(week.toLong()),"cond","Low-impact conditioning",minOf(p.minutes,25),null,"conditioning",steps=listOf("5 min easy walking","10–15 min comfortable brisk walking or low-impact marching, with easy breaks","5 min easy walking; shorten the middle portion to stay within the total"),reason="Comfortable conditioning without adding another hard lower-body day."))
            val scheduled=schedule(p,p.start.plusWeeks(week.toLong()),templates,all)
            all.addAll(scheduled)
        }
        require(all.size==(p.runDays+p.strengthDays+p.calDays+p.conditioningDays)*p.weeks)
        why.add("Every fourth week is lighter. The modest fixed running build is capped at 20% above the starting reference, subject to time limits; it is not a learned response or a promise of readiness.")
        why.add("Plan goals do not establish race readiness. Review the actual dated prescriptions before approval; you can pause, move or reduce sessions without deleting historical records.")
        return PlanSnapshot(id,owner,version,policyId,now,p,all.sortedBy { it.date },why)
    }
    fun validate(p:PlanRequest) {
        fun check(condition:Boolean,message:String) { if(!condition)throw PlanConstraint(message) }
        check(p.goal in ENGINE1_GOALS,"Choose a supported goal.")
        check(p.weeks in 1..16 && p.minutes in 15..120,"Choose 1–16 weeks and 15–120 available minutes per session.")
        check(p.experience in 0..2 && p.strengthExperience in 0..2 && p.calLevel in 0..2 && p.pullLevel in 0..2 && p.coreLevel in 0..2,"Choose your current ability level.")
        check(p.days.isNotEmpty() && p.days.all { it in 1..7 },"Choose available training days.")
        WeeklyPlacement.validationError(p)?.let { throw PlanConstraint(it) }
        check(p.runDays in 0..6 && p.strengthDays in 0..4 && p.calDays in 0..4 && p.conditioningDays in 0..3,"Check session counts.")
        val total=p.runDays+p.strengthDays+p.calDays+p.conditioningDays
        check(total>0 && total<=p.days.size,"You requested $total sessions across ${p.days.size} days. Add available days or reduce session counts; no discipline will be dropped silently.")
        if(p.goal in setOf("Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"))check(p.runDays>0,"Your selected goal needs running sessions. Enable Running or choose a different primary goal.")
        if(p.goal in setOf("General strength","Muscle development","Strength endurance"))check(p.strengthDays>0,"Your selected goal needs strength sessions. Enable Strength or choose another primary goal.")
        if(p.goal in setOf("Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"))check(p.calDays>0,"Your selected goal needs calisthenics sessions. Enable Calisthenics or choose another primary goal.")
        if(p.goal=="Conditioning")check(p.conditioningDays>0,"Enable Conditioning sessions for this goal.")
        check(p.qualityCount in 0..2 && p.qualityCount<=maxOf(0,p.runDays-2),"Quality work requires room for a long/easy run and easy training. Reduce quality count or add runs.")
        check(p.qualityCount<=1 || (p.experience==2 && p.runDays>=4 && p.weeklyKm>=30),"Two quality sessions require experienced running, at least four runs and 30 km of recent weekly mileage.")
        if(p.runDays>0) check(p.longDay in p.days,"Choose exactly one long-run day from your available days.")
        if(p.qualityCount>0) check(p.qualityDays.size>=p.qualityCount && p.qualityDays.all { it in p.days && it!=p.longDay },"Preferred quality days must be available and different from the single long-run day.")
        check(p.weeklyKm.isFinite() && p.weeklyKm in 0.0..150.0 && p.longestKm.isFinite() && p.longestKm in 0.0..80.0,"Enter finite, nonnegative recent mileage and longest-run distance.")
        check(p.planningPace.isFinite() && p.planningPace in 180.0..1200.0,"Use a planning pace estimate from 3 to 20 minutes/km; this is not a recorded metric.")
        check(p.equipment.values.all { it in 0..20 },"Equipment quantities must be between zero and twenty.")
        check(p.limitations.length<=1000 && p.recoveryNotes.length<=1000 && p.secondaryGoal.length<=200,"Shorten the optional notes.")
        if(p.goal=="Pull-up progression" && p.calDays>0) {
            check((p.equipment["bar"]?:0)>0,"Pull-up practice requires a secure overhead bar. Choose foundations without it.")
            check(p.pullLevel==2 || (p.equipment["support"]?:0)>0,"Assisted pulling requires suitable safe foot support as well as a secure bar. Add verified support or choose foundations.")
        }
    }
    private fun strengthExercises(p:PlanRequest,upper:Boolean,recovery:Boolean):List<ExercisePrescription> {
        val db=(p.equipment["db"]?:0); val barbell=(p.equipment["bb"]?:0)>0;val rack=(p.equipment["rack"]?:0)>0;val bench=(p.equipment["bench"]?:0)>0; val band=(p.equipment["bands"]?:0)>0 && (p.equipment["anchor"]?:0)>0
        val sets=if(recovery) 1 else if(p.strengthExperience==0) 2 else 3
        val reps=when(p.goal) { "Muscle development"->10;"Strength endurance"->12;else->8 }
        fun ex(id:String,name:String,eq:String="None",hold:Int?=null)=ExercisePrescription(id,name,sets,if(hold==null) reps else null,hold,if(p.strengthExperience==0) 60 else 90,eq,"Use a comfortable load or variation; keep 3–4 controlled reps in reserve. No automatic load increase.")
        val lower=listOf(if(barbell&&rack) ex("e1","Back squat","Barbell + rack with safeties") else if(db>0) ex("gs","Goblet squat","One dumbbell") else ex("sq","Bodyweight squat"),if(barbell) ex("e3","Romanian deadlift","Barbell") else if(db>0) ex("dh","Dumbbell Romanian deadlift","One or two dumbbells") else ex("hg","Unloaded hip hinge"))
        val push=if(barbell&&rack&&bench) ex("bb-bench","Bench press","Barbell + bench + rack with safeties") else if(db>=2) ex("fp","Dumbbell floor press","Two dumbbells") else if((p.equipment["support"]?:0)>0) ex("ip","Incline push-up","Stable raised surface") else ex("wall-push","Wall push-up","Solid wall")
        val pull=if(barbell) ex("bb-row","Barbell row","Barbell") else if(db>0) ex("dr","Dumbbell row","One dumbbell") else if(band) ex("br","Band row","Band + secure anchor") else ex("prone-w","Prone W raise")
        return fitTime(p,(if(upper) emptyList() else lower)+listOf(push,pull,ex("plank","Comfortable plank",hold=20)))
    }
    private fun calExercises(p:PlanRequest,recovery:Boolean):List<ExercisePrescription> {
        val sets=if(recovery) 1 else 2
        val push=when { p.calLevel>=1->ExercisePrescription("e4","Push-up",sets,6,rest=75);(p.equipment["support"]?:0)>0->ExercisePrescription("ip","Incline push-up",sets,6,rest=75,equipment="Stable raised surface");else->ExercisePrescription("wall-push","Wall push-up",sets,6,rest=60,equipment="Solid wall") }
        val pull=if((p.equipment["bar"]?:0)>0 && p.pullLevel==2) ExercisePrescription("e2","Controlled pull-up",sets,1,rest=90,equipment="Secure overhead bar",cue="Use only a controlled repetition well within your ability; use suitable assistance if needed, never grind or swing.") else if((p.equipment["bar"]?:0)>0 && (p.equipment["support"]?:0)>0 && p.pullLevel==1) ExercisePrescription("supported-scapular","Supported scapular pull",sets,3,rest=75,equipment="Secure overhead bar + safe foot support",cue="Keep feet supported; move shoulders gently without swinging or forcing range.") else if((p.equipment["bar"]?:0)>0 && (p.equipment["support"]?:0)>0) ExercisePrescription("supported-hang","Foot-assisted active hang",sets,seconds=15,rest=75,equipment="Secure overhead bar + safe foot support",cue="Keep feet supported and shoulders comfortable; release before grip fails. This is preparation, not a requirement or full pull-up.") else ExercisePrescription("prone-w","Prone W raise",sets,8,rest=60,cue="Gentle comfortable shoulder movement, not a replacement for loaded vertical pulling.")
        val core=when(p.coreLevel) { 2->ExercisePrescription("hollow","Comfortable hollow hold",sets,seconds=20,rest=60,cue="Use a controllable lever and breathe; shorten the lever at any loss of position.");1->ExercisePrescription("hollow-tuck","Hollow tuck hold",sets,seconds=15,rest=60,cue="Keep the lever short and breathe; reduce hold time or support the head if needed.");else->ExercisePrescription("deadbug","Dead bug",sets,6,rest=45,cue="Slow alternating limbs; reduce range to keep a comfortable, steady trunk.") }
        if(p.goal=="Core strength and control")return fitTime(p,listOf(core,ExercisePrescription("side-plank","Kneeling side plank",sets,seconds=15,rest=60,cue="Use a short comfortable lever; breathe and stop before form breaks.")))
        if(p.goal=="Handstand foundations")return fitTime(p,listOf(ExercisePrescription("pike-support","Ground-supported pike hold",sets,seconds=15,rest=75,cue="Keep feet on the floor and more weight on them as needed. Do not load the neck or attempt an unsupported inversion."),core))
        return fitTime(p,listOf(push,pull,core))
    }
    private fun fitTime(p:PlanRequest,list:List<ExercisePrescription>):List<ExercisePrescription> {
        var ex=list.filter { it.id !in p.excluded }
        if(ex.size<2)throw PlanConstraint("Too many exercises are excluded for this template. Review your preferences before generating.")
        while(resistanceMinutes(ex)>p.minutes && ex.size>2) ex=ex.dropLast(1)
        if(resistanceMinutes(ex)>p.minutes) ex=ex.map { it.copy(sets=1) }
        if(resistanceMinutes(ex)>p.minutes)throw PlanConstraint("The selected resistance prescription does not fit your time budget. Increase available time.")
        return ex
    }
    private fun resistanceMinutes(ex:List<ExercisePrescription>)=ceil(8+ex.sumOf { (it.sets*(it.seconds ?: ((it.reps?:0)*3))+(it.sets-1)*it.rest)/60.0+1 }).toInt()
    internal fun conflict(a:PlannedSession,b:PlannedSession):Boolean {
        val gap=ChronoUnit.DAYS.between(a.date,b.date)
        if(gap==0L)return true
        if(abs(gap)<=1 && ((a.quality&&(b.quality||b.kind=="long"||b.lower))||(b.quality&&(a.kind=="long"||a.lower))))return true
        if(a.lower&&b.kind=="long"&&gap==1L)return true
        if(b.lower&&a.kind=="long"&&gap == -1L)return true
        if(a.lower&&b.lower&&abs(gap)<=1)return true
        return false
    }
    private fun schedule(p:PlanRequest,start:LocalDate,templates:List<PlannedSession>,prior:List<PlannedSession>):List<PlannedSession> {
        val slots=(0L..6L).map(start::plusDays).filter { it.dayOfWeek.value in p.days }
        val ordered=templates.sortedBy { when { it.kind=="long"->0;it.quality->1;it.lower->2;it.discipline=="str"->3;it.discipline=="cal"->4;else->5 } }
        val placed=mutableListOf<PlannedSession>();var attempts=0
        fun solve(index:Int):Boolean {
            if(index==ordered.size)return true
            if(++attempts>100000)return false
            val s=ordered[index]
            val pinnedDay=WeeklyPlacement.fixedDay(p,s)
            val eligible=slots.filter { day ->
                when {
                    pinnedDay!=null -> day.dayOfWeek.value==pinnedDay
                    s.kind=="long" -> day.dayOfWeek.value==p.longDay
                    s.kind=="quality" -> day.dayOfWeek.value in p.qualityDays
                    else -> true
                }
            }
            for(date in eligible) { val candidate=s.copy(date=date)
                if((placed+prior.takeLast(7)).any { conflict(candidate,it) })continue
                placed.add(candidate);if(solve(index+1))return true;placed.removeAt(placed.lastIndex)
            }
            return false
        }
        if(!solve(0))throw PlanConstraint(
            if(p.weeklySlots.isEmpty())
                "These days cannot fit all requested sessions with recovery spacing around quality running, lower-body work and your long run. Add days, reduce sessions or change quality/long-run preferences."
            else "The selected weekdays cannot fit all sessions with recovery spacing around quality, strength and your long run. Move a session or switch to automatic scheduling."
        )
        return placed.sortedBy { it.date }
    }
}
