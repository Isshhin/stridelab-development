@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable internal fun CoachFeedbackChoices(label:String,options:List<String>,value:String?,enabled:Boolean=true,onChange:(String?)->Unit) {
    Text(label,style=MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        (listOf("Unanswered")+options).forEach { option ->
            val choice=option.takeUnless { it=="Unanswered" }
            SignalChip(selected=value==choice,onClick={onChange(choice)},enabled=enabled,label={Text(option)},modifier=Modifier.heightIn(min=48.dp))
        }
    }
}

@Composable internal fun Engine15CoachReviewCard(plan:PlanSnapshot,actions:PlanActions,busy:Boolean,error:String?=null) {
    if(plan.status!="approved" || plan.engineVersion!=ENGINE15_VERSION || plan.policyId !in setOf(ENGINE15_POLICY,ENGINE15_MULTI_POLICY,ENGINE15_INTERFERENCE_POLICY))return
    var open by rememberSaveable(plan.owner,plan.id) { mutableStateOf(false) }
    var fatigue by rememberSaveable(plan.owner,plan.id) { mutableStateOf<String?>(null) }
    var unavailableCsv by rememberSaveable(plan.owner,plan.id) { mutableStateOf("") }
    var review by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<Engine15WeeklyReview.Review?>(null) }
    var overrun by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<Engine15WeeklyReview.Review?>(null) }
    var dismissedOverrun by rememberSaveable(plan.owner,plan.id) { mutableStateOf("") }
    var localError by remember(plan.owner,plan.id,plan.revision) { mutableStateOf<String?>(null) }
    val today=LocalDate.now()
    val unavailable=unavailableCsv.split(',').filter { it.isNotBlank() }.map(LocalDate::parse).filter { it>today && it<=today.plusDays(7) }.toSet()
    LaunchedEffect(plan.owner,plan.id,plan.revision,today) {
        overrun=runCatching { actions.previewWeek(plan.id,Engine15WeeklyReview.Input(trigger=Engine15WeeklyReview.Trigger.OVERRUN)) }.getOrNull()?.takeIf { it.overrunSessionIds.isNotEmpty() }
    }
    Surface(
        modifier=Modifier.fillMaxWidth().testTag("coach-week-review"),
        color=Color(0xFF1B211D),
        shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.dp,Color(0xFF343E34))
    ) {
        Column(Modifier.padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weekly coach review",
                        style=MaterialTheme.typography.titleSmall,
                        color=Color(0xFFF0F5EC))
                    Text("Review upcoming adjustments",
                        style=MaterialTheme.typography.bodySmall,
                        color=Color(0xFFAAB6A7))
                }
                TextButton(onClick={open=!open},enabled=!busy) {
                    Text(if(open)"Hide" else "Review week",
                        color=Color(0xFFD8FB70))
                }
            }
        overrun?.takeIf { it.overrunSessionIds.joinToString()!=dismissedOverrun }?.let { extra ->
            Text("You recorded more running than planned. A review is optional; extra distance alone does not mean you need less training.")
            TextButton(onClick={
                open=true;localError=null
                try { review=actions.previewWeek(plan.id,Engine15WeeklyReview.Input(fatigue?.toInt(),unavailable,Engine15WeeklyReview.Trigger.OVERRUN)) }
                catch(e:IllegalArgumentException) { localError=e.message }
            },enabled=!busy) { Text("Review extra running") }
            TextButton(onClick={dismissedOverrun=extra.overrunSessionIds.joinToString()}) { Text("Not now") }
        }
        if(open) {
            CoachFeedbackChoices("General fatigue · 1 fresh, 5 exhausted",(1..5).map { it.toString() },fatigue,!busy) { fatigue=it;review=null }
            Text("Days you cannot train",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                (1L..7L).forEach { offset -> val date=today.plusDays(offset)
                    SignalChip(selected=date in unavailable,onClick={unavailableCsv=(if(date in unavailable)unavailable-date else unavailable+date).sorted().joinToString(",");review=null},enabled=!busy,
                        label={Text("${dayName(date.dayOfWeek.value)} ${date.dayOfMonth}")},modifier=Modifier.heightIn(min=48.dp))
                }
            }
            SignalButton("Preview weekly decision",{
                localError=null
                try { review=actions.previewWeek(plan.id,Engine15WeeklyReview.Input(fatigue?.toInt(),unavailable)) ?: throw PlanConstraint("Review is unavailable. Refresh your plan.") }
                catch(e:IllegalArgumentException) { localError=e.message }
            },!busy)
            review?.let { proposed ->
                Text(if(proposed.changes.isEmpty())"Keep this week's plan" else "${proposed.changes.size} proposed ${if(proposed.changes.size==1)"change" else "changes"}",style=MaterialTheme.typography.titleMedium)
                proposed.changes.forEach { change ->
                    val session=plan.sessions.single { it.id==change.sessionId }
                    Text("${session.date} · ${session.title}",style=MaterialTheme.typography.labelLarge)
                    Text("${session.minutes} min → ${if(change.minutes==0)"Rest" else "${change.minutes} min, time-only"}")
                    Text(change.reason,style=MaterialTheme.typography.bodySmall)
                }
                proposed.explanations.filterNot { it.startsWith("Evidence:") }.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                SignalButton(if(proposed.changes.isEmpty())"Approve unchanged week" else "Approve these changes",{actions.acceptWeek(proposed)},!busy)
            }
            (localError ?: error)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            TextButton(onClick={open=false;review=null}) { Text("Close review") }
        }
        }
    }
}

@Composable internal fun RunningHistoryConfirmation(state:TrainingState,onUse:(Double)->Unit) {
    val rows=remember(state.activities,state.plans) {
        observedRunningWeeks(state.activities,plans=state.plans)
    }
    val detected=remember(state.activities,state.plans) {
        automaticRunningBaseline(state.activities,plans=state.plans)
    }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    val selected=selectedCsv.split(',').filter { it.isNotBlank() }.map(LocalDate::parse).toSet()
    SignalCard(Modifier.testTag("running-history-confirmation")) {
        Text("RECENT RUNNING HISTORY",style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.primary)
        if(detected!=null) {
            Text("Estimated baseline · %.1f km/week".format(detected.weeklyKm),
                style=MaterialTheme.typography.titleMedium,
                modifier=Modifier.testTag("running-history-auto-baseline"))
            Text("${detected.weeks.size} completed calendar weeks with recorded runs. Used when no existing baseline is set; previously chosen mileage is preserved.",
                style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={onUse(detected.weeklyKm)}) {
                Text("Use estimated mileage")
            }
            TextButton(onClick={manualOpen=!manualOpen}) {
                Text(if(manualOpen) "Hide manual week selection" else "Select different weeks")
            }
        } else {
            Text("An automatic baseline needs at least two completed weeks with three recorded runs each. Enter your usual mileage above, or select complete weeks manually.",
                style=MaterialTheme.typography.bodySmall)
        }
        if(detected==null || manualOpen) {
            rows.forEach { row ->
                val complete=row.start.plusDays(6)<LocalDate.now()
                SignalChip(selected=row.start in selected,enabled=complete && row.distanceKm!=null,
                    onClick={
                        selectedCsv=(if(row.start in selected) selected-row.start
                            else selected+row.start).sorted().joinToString(",")
                    },label={
                        Text("${row.start} · ${row.distanceKm?.let { "%.1f km".format(it) } ?: "distance incomplete"} · ${row.runs} ${if(row.runs==1)"run" else "runs"}${if(!complete)" · in progress" else ""}")
                    },
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp))
            }
            val average=historyWeeklyKm(state.activities,plans=state.plans,confirmedWeeks=selected)
            SignalButton(
                average?.let { "Use selected average · %.1f km/week".format(it) }
                    ?: "Confirm complete weeks",
                { average?.let(onUse) },average!=null)
        }
    }
}

@Composable internal fun MileageGoalChoices(plan:PlanSnapshot,busy:Boolean,onChoose:(PlanRequest)->Unit) {
    val options=plan.mileageOptions ?: return
    SignalCard(Modifier.testTag("mileage-goal-options")) {
        Text("RECONSIDER YOUR MILEAGE GOAL",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Text("The current constraints do not reach ${options.targetKm} km/week. Choosing an option creates another draft for review; nothing is approved automatically.")
        SignalButton("Review %.1f km within %d weeks".format(options.lowerTargetKm,plan.request.weeks),{onChoose(plan.request.copy(targetWeeklyKm=options.lowerTargetKm))},!busy && options.lowerTargetKm>0)
        options.longerWeeks?.let { weeks ->
            SignalButton("Review ${options.targetKm} km over $weeks weeks",{onChoose(plan.request.copy(weeks=weeks))},!busy)
        } ?: Text("A longer timeline is not established within 16 weeks. Review history and availability before extending.",style=MaterialTheme.typography.bodySmall)
    }
}
