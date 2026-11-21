package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Pure preview and explicit atomic approval; never rewrites the original prescription. */
internal object Engine15WeeklyReview {
    enum class Trigger { WEEKLY, OVERRUN }
    enum class Direction { REST, REDUCE, PROGRESS }
    data class Input(val fatigue:Int?=null,val unavailableDays:Set<LocalDate> = emptySet(),val trigger:Trigger=Trigger.WEEKLY) {
        init { require(fatigue==null || fatigue in 1..5) }
    }
    data class Change(
        val sessionId:String,
        val minutes:Int,
        val reason:String,
        val direction:Direction=if(minutes==0) Direction.REST else Direction.REDUCE,
        val suggestedDistanceKm:Double?=null
    )
    data class Review(
        val original:PlanSnapshot,
        val asOf:LocalDate,
        val evaluatedAt:Instant,
        val input:Input,
        val changes:List<Change>,
        val explanations:List<String>,
        val overrunSessionIds:List<String> = emptyList(),
        val aerobicEfficiencyPercentChange:Double? = null
    )

    fun preview(plan:PlanSnapshot,asOf:LocalDate,input:Input=Input(),allPlans:List<PlanSnapshot> = listOf(plan),now:Instant=Instant.now(),activities:List<ActivityRecord> = emptyList()):Review {
        if(plan.engineVersion!=ENGINE15_VERSION || plan.policyId !in ENGINE15_COACHING_POLICIES || !Engine1Engines.canManage(plan) || plan.status!="approved")
            throw PlanConstraint("Weekly review requires an approved Engine 1.5 plan.")
        if(input.unavailableDays.any { it<=asOf || it>asOf.plusDays(7) })
            throw PlanConstraint("Availability changes must be within the next seven days.")
        val owned=(allPlans.filter { it.owner==plan.owner && it.id!=plan.id && Engine1Engines.canManage(it) }+plan)
        val sessions=owned.flatMap { it.sessions }
        val duplicateLinks=sessions.mapNotNull { it.linkedActivityId }.groupingBy { it }.eachCount().filterValues { it>1 }.keys
        val duplicateIds=sessions.groupingBy { it.id }.eachCount().filterValues { it>1 }.keys
        val completed=sessions.filter { s ->
            val log=s.coachingLog(); val date=log?.performedOn
            s.discipline=="run" && s.status=="completed" && s.logRevision>0 && s.id !in duplicateIds &&
                s.linkedActivityId !in duplicateLinks && log!=null && date!=null &&
                ChronoUnit.DAYS.between(date,asOf) in 0L..6L && kotlin.math.abs(ChronoUnit.DAYS.between(s.date,date))<=2 &&
                !log.at.isAfter(now)
        }
        val health=completed.filter { it.coachingLog()!!.discomfort=="Pain" }
        val difficult=completed.filter { it.coachingLog()!!.effort?.let { r -> r in 8..10 }==true ||
            it.coachingLog()!!.completionDifficulty in setOf("Harder","Could not finish") || it.coachingLog()!!.discomfort=="Soreness" }
        val missed=sessions.filter { it.id !in duplicateIds && it.linkedActivityId !in duplicateLinks && it.discipline=="run" && it.status in setOf("missed","skipped") && it.coachingLog()==null &&
            ChronoUnit.DAYS.between(it.date,asOf) in 0L..6L }
        val illness=missed.any { it.missedReason=="Illness" }
        val negativeMiss=missed.any { it.missedReason in setOf("Fatigue","Soreness") }
        // shortcut: distance/duration trigger a review, not a physiological load estimate; add measured intensity evidence separately.
        val overruns=completed.filter { s ->
            ChronoUnit.DAYS.between(s.coachingLog()!!.performedOn,asOf) in 0L..1L && (
                s.coachingLog()!!.distanceKm?.let { actual -> s.distanceKm?.let { planned -> planned>0 && actual.isFinite() && actual>planned*1.10 && actual-planned>=1.0 && s.suggestion?.accepted!=true } }==true ||
                s.coachingLog()!!.minutes?.let { it.isFinite() && it>s.effectiveMinutes*1.10 && it-s.effectiveMinutes>=10 }==true)
        }
        val notes=mutableListOf("Weekly review uses recorded response and availability; absent logs do not establish zero training or lost fitness.")
        if(missed.any { it.missedReason !in setOf("Fatigue","Soreness","Illness") }) notes+="Scheduling/availability interruption: no catch-up mileage and no inferred fatigue."
        if(overruns.isNotEmpty())notes+="Overrun review: Extra distance or duration alone does not earn progression or require a reduction."
        if(input.trigger==Trigger.OVERRUN && overruns.isEmpty())notes+="No eligible recent overrun; no next-day adjustment proposed."
        val enabled=input.trigger==Trigger.WEEKLY || overruns.isNotEmpty()
        val strong=health.isNotEmpty() || illness
        val fatigue=input.fatigue?.let { it>=4 }==true
        val caution=difficult.isNotEmpty() || negativeMiss
        val candidates=plan.sessions.filter { it.discipline=="run" && it.date>asOf && it.date<=asOf.plusDays(7) &&
            it.status=="planned" && it.coachingLog()==null && it.linkedActivityId==null && it.suggestion==null && it.id !in plan.request.lockedSessionDays }
            .sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        val efficiencyAsOf=asOf.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant()
        val efficiency=if(activities.isNotEmpty()) RunningAerobicEfficiency.analyze(activities,windowWeeks=8,asOf=efficiencyAsOf) else null
        val efficiencyGainPct=efficiency?.percentChange?.takeIf { it.isFinite() && it>=2.0 }

        val changes:List<Change> = if(!enabled) emptyList() else {
            val downChanges = candidates.mapNotNull { s ->
                val unavailable=s.date in input.unavailableDays
                val limit=strong || fatigue || caution && s.id==candidates.firstOrNull()?.id
                if(!unavailable && !limit)null else {
                    val minutes=if(unavailable || strong || s.quality)0 else floor(s.minutes*0.8).toInt().coerceAtLeast(1)
                    if(minutes>=s.minutes)null else Change(s.id,minutes,when {
                        unavailable->"Reported unavailable day; no relocation or catch-up."
                        strong->"Reported pain or illness requires review before further running; rest proposed, not a diagnosis or recovery deadline."
                        fatigue->"Current high general fatigue: a lighter upcoming week is proposed."
                        else->"Reported difficulty in the reviewed week: reduce the next exposure and reassess."
                    },if(minutes==0) Direction.REST else Direction.REDUCE)
                }
            }
            if(downChanges.isNotEmpty()) {
                downChanges
            } else {
                val progressionReady=input.trigger==Trigger.WEEKLY && !strong && !fatigue && !caution &&
                    input.unavailableDays.isEmpty() && overruns.isEmpty() && (input.fatigue==null || input.fatigue<=2)
                val allComfortableNoDiscomfort=completed.all { s ->
                    val log=s.coachingLog()!!
                    log.completionDifficulty !in setOf("Harder","Could not finish") &&
                        log.discomfort !in setOf("Pain","Soreness")
                }
                val lowRpeWeek=completed.size>=3 && allComfortableNoDiscomfort &&
                    completed.all { it.coachingLog()!!.effort?.let { r -> r in 1..4 }==true }
                val efficiencyProgressWeek=efficiencyGainPct!=null && completed.size>=2 && allComfortableNoDiscomfort &&
                    completed.all { s ->
                        val log=s.coachingLog()!!
                        (log.effort!=null && log.effort in 1..5) || (log.effort==null && s.linkedActivityId!=null)
                    }
                if(progressionReady && (lowRpeWeek || efficiencyProgressWeek)) {
                    val progressTargets=candidates.filter { s ->
                        !s.quality && s.runningPrescription?.role!="RECOVERY" &&
                            !s.title.contains("Recovery",ignoreCase=true)
                    }.sortedWith(compareByDescending<PlannedSession> { it.kind=="long" }.thenBy { it.date }.thenBy { it.id })
                        .take(2)
                    progressTargets.mapNotNull { s ->
                        val delta=ceil(s.minutes*0.05).toInt().coerceIn(3,8)
                        var targetMin=s.minutes+delta
                        if(plan.request.timeBudgetEnabled) {
                            val otherDayMin=plan.sessions.filter { it.date==s.date && it.id!=s.id }.sumOf { it.effectiveMinutes }
                            targetMin=minOf(targetMin,plan.request.dailyMinutes-otherDayMin)
                        }
                        if(targetMin<=s.minutes) null else {
                            val suggestedDistKm=s.distanceKm?.let { km ->
                                if(s.minutes>0) floor((km*targetMin.toDouble()/s.minutes)*10.0+1e-8)/10.0 else km
                            }
                            val trendSuffix=efficiencyGainPct?.let {
                                String.format(Locale.US," and Garmin aerobic efficiency rose +%.1f%%",it)
                            }.orEmpty()
                            val reasonText=if(lowRpeWeek) {
                                "Consistent low-effort week (RPE <= 4 across ${completed.size} runs$trendSuffix): conservative +${targetMin-s.minutes} min aerobic build option."
                            } else {
                                "Steady execution with +%.1f%% Garmin aerobic efficiency trend: conservative +%d min aerobic build option.".format(Locale.US,efficiencyGainPct!!,targetMin-s.minutes)
                            }
                            Change(s.id,targetMin,reasonText,Direction.PROGRESS,suggestedDistKm)
                        }
                    }
                } else emptyList()
            }
        }
        val hasProgression=changes.any { it.direction==Direction.PROGRESS }
        if(strong)notes+="Pain/illness remains a review concern; the end of this proposal is not clearance to resume."
        if(hasProgression) {
            notes+="Bidirectional progression preview: steady low-effort execution${efficiencyGainPct?.let { String.format(Locale.US," and +%.1f%% aerobic efficiency trend",it) }.orEmpty()} supports a conservative +5% build step if you choose to approve it."
        }
        if(changes.isEmpty())notes+="Maintain the existing prescription; missing feedback or an isolated easy completion does not authorize increased load."
        notes+="Evidence: "+(completed.map { "${it.id}@${it.logRevision}" }+missed.map { "${it.id}:${it.missedReason}" }).sorted().joinToString()
        return Review(plan,asOf,now,input,changes,notes,overruns.map { it.id }.sorted(),efficiencyGainPct)
    }

    fun accept(plan:PlanSnapshot,review:Review,asOf:LocalDate,now:Instant,allPlans:List<PlanSnapshot> = listOf(plan),activities:List<ActivityRecord> = emptyList()):PlanSnapshot {
        if(plan!=review.original || asOf!=review.asOf || now.isBefore(review.evaluatedAt) || review!=preview(plan,asOf,review.input,allPlans,now,activities).copy(evaluatedAt=review.evaluatedAt))
            throw PlanConstraint("The weekly review is stale. Preview current records before approving.")
        val byId=review.changes.associateBy { it.sessionId }
        val updated=plan.sessions.map { s -> byId[s.id]?.let { change ->
            val suggestion=if(change.direction==Direction.PROGRESS) {
                SessionSuggestion(change.minutes,"${Engine15Feedback.RULE_ID}: Approved weekly progression. ${change.reason}",true)
            } else {
                SessionSuggestion(change.minutes,"${Engine15Feedback.RULE_ID}: Approved weekly review. ${change.reason} Original distance and structured work are not requirements for this time-only alternative.",true)
            }
            s.copy(suggestion=suggestion,status=if(change.minutes==0)"skipped" else s.status,
                missedReason=if(change.minutes==0)"Reviewed rest or availability" else s.missedReason)
        } ?: s }
        return plan.copy(sessions=updated,feedback=plan.feedback+PlanFeedback(java.util.UUID.randomUUID().toString(),now,"Workout quality",
            "Approved weekly review ${review.asOf}; fatigue=${review.input.fatigue ?: "unknown"}; unavailable=${review.input.unavailableDays.sorted()}; trigger=${review.input.trigger}. "+review.explanations.joinToString(" ")))
    }
}
