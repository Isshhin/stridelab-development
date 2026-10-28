package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal object PlanOperations {
    private fun compatible(plan:PlanSnapshot) { if(!Engine1Engines.canManage(plan))throw PlanConstraint("This saved plan belongs to another engine version or policy. Its original snapshot is retained for viewing/export; use a compatible engine before editing it.") }
    private fun conflict(a:PlannedSession,b:PlannedSession,policyA:String,policyB:String)=
        PlanScheduleValidation.conflict(a,b,policyA,policyB)
    fun approve(plan:PlanSnapshot,others:List<PlanSnapshot>):PlanSnapshot {
        compatible(plan)
        if(plan.status!="draft" && plan.status!="paused")throw PlanConstraint("Only a draft or paused plan can be approved.")
        val approved=others.filter { it.id!=plan.id&&it.status=="approved" }
        if(approved.any { other->other.sessions.any { existing->plan.sessions.any { session->conflict(session,existing,plan.policyId,other.policyId) } } })throw PlanConstraint("This plan conflicts with an approved plan. Pause the existing plan or change the dates first.")
        if(plan.policyId==MULTI_SESSION_POLICY&&approved.filter { it.policyId==MULTI_SESSION_POLICY }.any { other->
                (plan.sessions.map { it.date }.toSet() intersect other.sessions.map { it.date }.toSet()).any { date->
                    (plan.sessions.filter { it.date==date }+other.sessions.filter { it.date==date }).sumOf { it.effectiveMinutes } > minOf(plan.request.dailyMinutes,other.request.dailyMinutes)
                }
            })throw PlanConstraint("This plan would exceed the shared daily time budget on an approved training day.")
        return plan.copy(status="approved")
    }
    fun move(plan:PlanSnapshot,sessionId:String,date:LocalDate):PlanSnapshot = move(plan,sessionId,date,emptyList())
    fun move(plan:PlanSnapshot,sessionId:String,date:LocalDate,otherPlans:List<PlanSnapshot>):PlanSnapshot {
        compatible(plan)
        val s=plan.sessions.firstOrNull { it.id==sessionId } ?: throw PlanConstraint("Workout is no longer available.")
        if(s.status !in setOf("planned","in_progress") || s.log!=null)throw PlanConstraint("Recorded workouts keep their original scheduled date. Move only an unstarted session.")
        if(date<plan.request.start || date>=plan.request.start.plusWeeks(plan.request.weeks.toLong()))throw PlanConstraint("Choose a date inside this plan.")
        if(date.dayOfWeek.value !in plan.request.days)throw PlanConstraint("That is not an available training day. Edit your availability in a new reviewed plan instead.")
        if(s.kind=="long" && date.dayOfWeek.value!=plan.request.longDay)throw PlanConstraint("This plan has one preferred long-run day. Move this run to that weekday or review a new plan.")
        val moved=s.copy(date=date)
        val approved=otherPlans.filter { it.id!=plan.id&&it.status=="approved" }
        val sameDayExisting=plan.sessions.filter { it.id!=sessionId&&it.date==date&&it.status !in setOf("missed","skipped") }
        val conflictsWithinPlan=plan.sessions.filter { it.id!=sessionId&&it.status !in setOf("missed","skipped") }
            .any { existing->conflict(moved,existing,plan.policyId,plan.policyId) }
        val conflictsWithApprovedPlan=approved.any { other->
            other.sessions.filter { it.status !in setOf("missed","skipped") }
                .any { existing->conflict(moved,existing,plan.policyId,other.policyId) }
        }
        if(conflictsWithinPlan||conflictsWithApprovedPlan)throw PlanConstraint("This move conflicts with another workout or its recovery spacing. Choose a different date.")
        if(plan.policyId==MULTI_SESSION_POLICY&&sameDayExisting.sumOf { it.effectiveMinutes }+moved.effectiveMinutes>plan.request.dailyMinutes)throw PlanConstraint("This move would exceed the ${plan.request.dailyMinutes}-minute daily time budget.")
        val sharedMultiPlans=(approved.filter { it.policyId==MULTI_SESSION_POLICY&&it.sessions.any { s->s.date==date&&s.status !in setOf("missed","skipped") } }+
            listOf(plan).filter { it.policyId==MULTI_SESSION_POLICY })
        if(sharedMultiPlans.size>1) {
            val combined=sameDayExisting+listOf(moved)+approved.filter { it.policyId==MULTI_SESSION_POLICY }.flatMap { p->p.sessions.filter { it.date==date&&it.status !in setOf("missed","skipped") } }
            val budget=sharedMultiPlans.minOf { it.request.dailyMinutes }
            if(combined.sumOf { it.effectiveMinutes }>budget)throw PlanConstraint("This move would exceed the shared $budget-minute daily time budget across approved plans.")
        }
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId) moved else it }.sortedBy { it.date })
    }
    fun swapExercise(plan:PlanSnapshot,sessionId:String,exerciseId:String,now:Instant=Instant.now()):PlanSnapshot {
        compatible(plan)
        if(plan.status !in setOf("approved","paused"))throw PlanConstraint("Approve a plan before choosing an alternative.")
        val s=plan.sessions.find { it.id==sessionId } ?: throw PlanConstraint("Workout is unavailable.")
        if(s.status !in setOf("planned","in_progress"))throw PlanConstraint("Historical workouts keep their saved exercises.")
        if(s.swaps.any { it.originalId==exerciseId })throw PlanConstraint("This exercise already has a reviewed alternative.")
        if(s.log?.sets?.any { it.exerciseId==exerciseId }==true)throw PlanConstraint("Sets were already recorded for this movement. Keep that history; choose a separate reviewed workout instead.")
        val original=s.exercises.find { it.id==exerciseId } ?: throw PlanConstraint("The original exercise is unavailable.")
        val replacement=ExerciseAlternatives.easier(original) ?: throw PlanConstraint("No reviewed easier alternative is available for this movement.")
        val swap=ExerciseSwap(exerciseId,replacement,ExerciseAlternatives.REASON,now)
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId)it.copy(swaps=it.swaps+swap)else it })
    }
    fun linkActivity(plan:PlanSnapshot,sessionId:String,activityId:String?):PlanSnapshot {
        compatible(plan)
        if(plan.status !in setOf("approved","paused"))throw PlanConstraint("Use an approved or paused plan when linking a recorded run.")
        val session=plan.sessions.find { it.id==sessionId } ?: throw PlanConstraint("Workout is unavailable.")
        if(session.discipline!="run" || session.status in setOf("missed","skipped"))throw PlanConstraint("Choose a planned running session that was not marked missed or skipped.")
        if(activityId!=null)RecordPolicy.identifier(activityId)
        val updated=if(activityId==null)session.copy(linkedActivityId=null,status=session.linkPreviousStatus ?: session.status,linkPreviousStatus=null)
            else session.copy(linkedActivityId=activityId,linkPreviousStatus=session.linkPreviousStatus ?: session.status,status="completed")
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId)updated else it })
    }
    fun record(plan:PlanSnapshot,sessionId:String,log:WorkoutLog,complete:Boolean):PlanSnapshot {
        compatible(plan)
        if(plan.status !in setOf("approved","paused"))throw PlanConstraint("Approve the plan before recording its workouts.")
        val s=plan.sessions.firstOrNull { it.id==sessionId } ?: throw PlanConstraint("Workout is unavailable.")
        if(s.status=="completed")return plan
        if(s.status in setOf("missed","skipped"))throw PlanConstraint("This session was marked missed or skipped. Keep that record; schedule a separate reviewed session if needed.")
        WorkoutLogValidation.validate(s,log)
        if(complete && s.discipline in setOf("str","cal") && !WorkoutLogValidation.hasPerformedSet(s,log))throw PlanConstraint("Record at least one performed set with actual repetitions or hold seconds before completing this workout. You can save unfinished progress instead.")
        val desiredStatus=if(complete) "completed" else "in_progress"
        if(s.status==desiredStatus && s.log==log)return plan
        if(s.logRevision==Int.MAX_VALUE)throw PlanConstraint("This saved log needs a supported revision before editing.")
        val updated=s.copy(status=desiredStatus,log=log,logRevision=s.logRevision+1)
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId) updated else it })
    }
    fun missed(plan:PlanSnapshot,sessionId:String,reason:String,skipped:Boolean):PlanSnapshot {
        compatible(plan)
        if(plan.status !in setOf("approved","paused"))throw PlanConstraint("Approve the plan before recording missed workouts.")
        if(reason !in listOf("Time","Fatigue","Soreness","Illness","Other"))throw PlanConstraint("Choose a reason for the missed or skipped workout.")
        val target=plan.sessions.find { it.id==sessionId } ?: throw PlanConstraint("Workout is unavailable.")
        if(target.status=="completed")throw PlanConstraint("A completed workout cannot be changed into a missed workout.")
        val next=plan.sessions.filter { it.date>target.date && it.date>=LocalDate.now() && it.status=="planned" }.minByOrNull { it.date }
        return plan.copy(sessions=plan.sessions.map { s -> when {
            s.id==sessionId -> s.copy(status=if(skipped) "skipped" else "missed",missedReason=reason)
            s.id==next?.id && target.date>=LocalDate.now().minusDays(7) && reason in setOf("Fatigue","Soreness","Illness") -> s.copy(suggestion=SessionSuggestion(if(reason=="Illness") 0 else maxOf(10,(s.minutes*.7).toInt()),"After your reported $reason: consider ${if(reason=="Illness") "rest" else "an easier, shorter session or rest"}. No catch-up volume is added. This is a suggestion awaiting your choice, not a diagnosis."))
            else -> s
        } })
    }
    fun acceptSuggestion(plan:PlanSnapshot,sessionId:String):PlanSnapshot {
        compatible(plan)
        return plan.copy(sessions=plan.sessions.map { s ->
        if(s.id!=sessionId || s.suggestion==null || s.status!="planned" || s.log!=null) s else if(s.suggestion.minutes==0) s.copy(status="skipped",missedReason="Recovery rest chosen",suggestion=s.suggestion.copy(accepted=true)) else s.copy(suggestion=s.suggestion.copy(accepted=true))
    })
    }
    fun feedback(plan:PlanSnapshot,category:String,text:String,sessionId:String?=null,now:Instant=Instant.now()):PlanSnapshot {
        compatible(plan)
        if(category !in setOf("Plan quality","Inappropriate prescription","Workout quality","Usability") || text.trim().length !in 3..2000)throw PlanConstraint("Choose a feedback category and enter 3–2000 characters.")
        if(sessionId!=null && plan.sessions.none { it.id==sessionId })throw PlanConstraint("Feedback workout is unavailable.")
        return plan.copy(feedback=plan.feedback+PlanFeedback(UUID.randomUUID().toString(),now,category,text.trim(),sessionId))
    }
}
