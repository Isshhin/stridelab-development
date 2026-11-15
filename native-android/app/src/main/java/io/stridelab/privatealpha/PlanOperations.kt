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
        Engine15SessionLocks.validateSaved(plan)
        Engine15Availability.requireReady(plan)
        val approved=others.filter { it.id!=plan.id&&it.status=="approved" }
        if(approved.any { other->other.sessions.any { existing->plan.sessions.any { session->conflict(session,existing,plan.policyId,other.policyId) } } })throw PlanConstraint("This plan conflicts with an approved plan. Pause the existing plan or change the dates first.")
        if(PlanScheduleValidation.isMultiPolicy(plan.policyId)&&approved.filter { PlanScheduleValidation.isMultiPolicy(it.policyId) }.any { other->
                (plan.sessions.map { it.date }.toSet() intersect other.sessions.map { it.date }.toSet()).any { date->
                    (plan.sessions.filter { it.date==date }+other.sessions.filter { it.date==date }).sumOf { it.effectiveMinutes } > minOf(plan.request.dailyMinutes,other.request.dailyMinutes)
                }
            })throw PlanConstraint("This plan would exceed the shared daily time budget on an approved training day.")
        val candidate=plan.copy(status="approved")
        // Only Engine 1.5 revalidates its own edited calendar before approval.
        if(candidate.engineVersion==ENGINE15_VERSION)
            PlanScheduleValidation.validatePlan(candidate)
        return candidate
    }
    fun move(plan:PlanSnapshot,sessionId:String,date:LocalDate):PlanSnapshot = move(plan,sessionId,date,emptyList())
    fun move(plan:PlanSnapshot,sessionId:String,date:LocalDate,otherPlans:List<PlanSnapshot>):PlanSnapshot {
        compatible(plan)
        val s=plan.sessions.firstOrNull { it.id==sessionId } ?: throw PlanConstraint("Workout is no longer available.")
        if(s.status !in setOf("planned","in_progress","unassigned") || s.log!=null)throw PlanConstraint("Recorded workouts keep their original scheduled date. Move only an unstarted session.")
        val lock=plan.request.lockedSessionDays[sessionId]
        if(lock!=null && (lock!=s.date.toString() || lock!=date.toString()))
            throw PlanConstraint("This workout is locked to its saved day. Unlock it before moving.")
        if(date==s.date && s.status!="unassigned")return plan
        if(date<plan.request.start || date>=plan.request.start.plusWeeks(plan.request.weeks.toLong()))throw PlanConstraint("Choose a date inside this plan.")
        if(date.dayOfWeek.value !in plan.request.days)throw PlanConstraint("That is not an available training day. Edit your availability in a new reviewed plan instead.")
        if(s.kind=="long" && date.dayOfWeek.value!=plan.request.longDay)throw PlanConstraint("This plan has one preferred long-run day. Move this run to that weekday or review a new plan.")
        val moved=s.copy(date=date,status=if(s.status=="unassigned")"planned" else s.status)
        val approved=otherPlans.filter { it.id!=plan.id&&it.status=="approved" }
        val sameDayExisting=plan.sessions.filter { it.id!=sessionId&&it.date==date&&it.status !in setOf("missed","skipped","unassigned") }
        if(PlanScheduleValidation.isMultiPolicy(plan.policyId) && sameDayExisting.size>=2)
            throw PlanConstraint("A day can contain at most two sessions.")
        val conflictsWithinPlan=plan.sessions.filter { it.id!=sessionId&&it.status !in setOf("missed","skipped","unassigned") }
            .any { existing->conflict(moved,existing,plan.policyId,plan.policyId) }
        val conflictsWithApprovedPlan=approved.any { other->
            other.sessions.filter { it.status !in setOf("missed","skipped","unassigned") }
                .any { existing->conflict(moved,existing,plan.policyId,other.policyId) }
        }
        if(conflictsWithinPlan||conflictsWithApprovedPlan)throw PlanConstraint("This move conflicts with another workout or its recovery spacing. Choose a different date.")
        if(PlanScheduleValidation.isMultiPolicy(plan.policyId)&&sameDayExisting.sumOf { it.effectiveMinutes }+moved.effectiveMinutes>plan.request.dailyMinutes)throw PlanConstraint("This move would exceed the ${plan.request.dailyMinutes}-minute daily time budget.")
        val sharedMultiPlans=(approved.filter { PlanScheduleValidation.isMultiPolicy(it.policyId)&&it.sessions.any { s->s.date==date&&s.status !in setOf("missed","skipped","unassigned") } }+
            listOf(plan).filter { PlanScheduleValidation.isMultiPolicy(it.policyId) })
        if(sharedMultiPlans.size>1) {
            val combined=sameDayExisting+listOf(moved)+approved.filter { PlanScheduleValidation.isMultiPolicy(it.policyId) }.flatMap { p->p.sessions.filter { it.date==date&&it.status !in setOf("missed","skipped","unassigned") } }
            val budget=sharedMultiPlans.minOf { it.request.dailyMinutes }
            if(combined.sumOf { it.effectiveMinutes }>budget)throw PlanConstraint("This move would exceed the shared $budget-minute daily time budget across approved plans.")
        }
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId) moved else it }.sortedBy { it.date })
    }
    /** Reviewable availability change: relocate only displaced or unassigned workouts. */
    fun updateAvailability(
        plan:PlanSnapshot,newDays:Set<Int>,
        otherPlans:List<PlanSnapshot> = emptyList(),
        asOf:LocalDate = LocalDate.now()
    ):PlanSnapshot {
        compatible(plan)
        if(newDays==plan.request.days)return plan
        val impact=Engine15Availability.preview(plan,newDays,otherPlans,asOf)
        val reconsidered=impact.affectedSessionIds.toSet() +
            plan.sessions.filter { it.status=="unassigned" }.map { it.id }
        val sessions=plan.sessions.map { session ->
            if(session.id !in reconsidered)session else {
                val newDate=impact.relocations[session.id]
                if(newDate==null)session.copy(status="unassigned")
                else session.copy(date=newDate,status="planned")
            }
        }
        val needsReview=reconsidered.isNotEmpty() ||
            (plan.request.runDays>0 && plan.request.longDay !in newDays) ||
            (plan.request.qualityCount>0 &&
                plan.request.qualityDays.any { it !in newDays })
        return plan.copy(
            request=plan.request.copy(days=newDays),
            sessions=sessions,
            status=if(needsReview && plan.status=="approved")"paused" else plan.status,
            explanations=plan.explanations+("Availability review: " +
                "${impact.relocations.size} safely relocated; " +
                "${impact.unresolvedSessionIds.size} unresolved sessions need placement. " +
                "Other workouts were preserved and no catch-up volume was added.")
        )
    }

    fun setLongRunDay(plan:PlanSnapshot,day:Int):PlanSnapshot {
        compatible(plan)
        if(plan.engineVersion!=ENGINE15_VERSION || plan.status !in setOf("approved","paused","draft"))
            throw PlanConstraint("Changing long-run preference requires an editable Engine 1.5 plan.")
        if(day !in plan.request.days)
            throw PlanConstraint("Choose an available day for the long run.")
        if(day==plan.request.longDay)return plan
        val displaced=plan.sessions.filter { it.kind=="long" && it.status=="planned" &&
            it.date.dayOfWeek.value!=day }
        if(displaced.any { it.id in plan.request.lockedSessionDays })
            throw PlanConstraint("Unlock the affected long run before changing its preferred day.")
        val ids=displaced.map { it.id }.toSet()
        return plan.copy(
            request=plan.request.copy(longDay=day),
            sessions=plan.sessions.map { if(it.id in ids)it.copy(status="unassigned") else it },
            status=if(ids.isNotEmpty() && plan.status=="approved")"paused" else plan.status
        )
    }

    fun setQualityDays(plan:PlanSnapshot,days:Set<Int>):PlanSnapshot {
        compatible(plan)
        if(plan.engineVersion!=ENGINE15_VERSION || plan.status !in setOf("approved","paused","draft"))
            throw PlanConstraint("Changing quality preferences requires an editable Engine 1.5 plan.")
        if(days.any { it !in plan.request.days || it==plan.request.longDay } ||
            days.size<plan.request.qualityCount)
            throw PlanConstraint("Choose enough available quality days away from the long run.")
        if(days==plan.request.qualityDays)return plan
        val displaced=plan.sessions.filter { it.quality && it.status=="planned" &&
            it.date.dayOfWeek.value !in days }
        if(displaced.any { it.id in plan.request.lockedSessionDays })
            throw PlanConstraint("Unlock the affected quality run before changing its day.")
        val ids=displaced.map { it.id }.toSet()
        return plan.copy(
            request=plan.request.copy(qualityDays=days),
            sessions=plan.sessions.map { if(it.id in ids)it.copy(status="unassigned") else it },
            status=if(ids.isNotEmpty() && plan.status=="approved")"paused" else plan.status
        )
    }

    /** Owner-bound repository operations persist this exact placement explicitly. */
    fun setSessionLock(plan:PlanSnapshot,sessionId:String,locked:Boolean):PlanSnapshot {
        compatible(plan)
        if(plan.engineVersion!=ENGINE15_VERSION)
            throw PlanConstraint("Workout locking requires a supported Engine 1.5 plan.")
        if(plan.status !in setOf("draft","approved","paused"))
            throw PlanConstraint("Archived workouts cannot be locked.")
        val target=plan.sessions.singleOrNull { it.id==sessionId }
            ?: throw PlanConstraint("Workout is not part of this saved plan.")
        if(target.status!="planned" || target.log!=null || target.linkedActivityId!=null)
            throw PlanConstraint("Only a future unstarted workout can be locked.")
        val locks=if(locked) plan.request.lockedSessionDays+(sessionId to target.date.toString())
            else plan.request.lockedSessionDays-sessionId
        if(locks==plan.request.lockedSessionDays)return plan
        val changed=plan.copy(request=plan.request.copy(lockedSessionDays=locks))
        Engine15SessionLocks.validateSaved(changed)
        return changed
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
        if(session.linkedActivityId==activityId)return plan // Idempotent link/unlink; no false revision increment.
        if(session.linkedActivityId!=null && activityId!=null)
            throw PlanConstraint("This planned run is already linked to another recorded activity. Unlink it first before choosing a replacement.")
        if(session.runResponse!=null && session.logRevision==Int.MAX_VALUE)throw PlanConstraint("This feedback record needs review before changing its source.")
        val updated=if(activityId==null)session.copy(linkedActivityId=null,status=session.linkPreviousStatus ?: session.status,linkPreviousStatus=null)
            else session.copy(linkedActivityId=activityId,linkPreviousStatus=session.linkPreviousStatus ?: session.status,status="completed")
        val feedback=if(session.runResponse==null)plan.feedback else plan.feedback+PlanFeedback(UUID.randomUUID().toString(),Instant.now(),"Workout quality",
            "Archived run feedback after source link changed from ${session.linkedActivityId ?: "manual record"}: ${session.runResponse.encode()}",sessionId)
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId)updated.copy(runResponse=null,logRevision=if(session.runResponse!=null)session.logRevision+1 else session.logRevision) else it },feedback=feedback)
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
        // Engine 1.0 retains its historic immediate suggestion. Engine 1.5 uses only the
        // separate, revision-checked, explicitly staged feedback review flow.
        val next=if(plan.engineVersion==ENGINE1_VERSION)
            plan.sessions.filter { it.date>target.date && it.date>=LocalDate.now() && it.status=="planned" }.minByOrNull { it.date }
        else null
        return plan.copy(sessions=plan.sessions.map { s -> when {
            s.id==sessionId -> s.copy(status=if(skipped) "skipped" else "missed",missedReason=reason)
            s.id==next?.id && target.date>=LocalDate.now().minusDays(7) && reason in setOf("Fatigue","Soreness","Illness") -> s.copy(suggestion=SessionSuggestion(if(reason=="Illness") 0 else maxOf(10,(s.minutes*.7).toInt()),"After your reported $reason: consider ${if(reason=="Illness") "rest" else "an easier, shorter session or rest"}. No catch-up volume is added. This is a suggestion awaiting your choice, not a diagnosis."))
            else -> s
        } })
    }
    fun runResponse(plan:PlanSnapshot,sessionId:String,effort:Int?,difficulty:String?,discomfort:String?,performedOn:LocalDate?,now:Instant=Instant.now()):PlanSnapshot {
        compatible(plan)
        val session=plan.sessions.singleOrNull { it.id==sessionId } ?: throw PlanConstraint("This workout is unavailable.")
        if(plan.engineVersion!=ENGINE15_VERSION || plan.status !in setOf("approved","paused") || session.discipline!="run" || session.status!="completed")
            throw PlanConstraint("Post-run feedback requires a completed Engine 1.5 run.")
        if(effort!=null && effort !in 1..10 || performedOn!=null && performedOn>now.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            throw PlanConstraint("Check the reported effort and actual workout date.")
        if(session.logRevision==Int.MAX_VALUE)throw PlanConstraint("This feedback record needs review before editing.")
        val response=WorkoutLog(now,effort=effort,performedOn=performedOn,completionDifficulty=difficulty,discomfort=discomfort)
        return plan.copy(sessions=plan.sessions.map { if(it.id==sessionId)it.copy(runResponse=response,logRevision=it.logRevision+1) else it })
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
