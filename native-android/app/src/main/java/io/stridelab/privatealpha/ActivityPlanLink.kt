package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.layout.navigationBarsPadding
import java.time.Instant
import java.time.LocalDate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId

private val linkLime = Color(0xFFD8FB70)
private val linkInk = Color(0xFFEFF5F0)
private val linkMuted = Color(0xFF81918A)

internal fun hasRunningPlanForActivityLink(plans: List<PlanSnapshot>): Boolean =
    plans.any { plan ->
        plan.status in setOf("approved", "paused") &&
            Engine1Engines.canManage(plan) &&
            plan.sessions.any { it.discipline == "run" }
    }

/** Only workouts on the recorded activity date or three preceding days.
 * No invented date matching, fuzzy future appointments, or automatic linking.
 */
internal fun eligiblePlannedRunsForLink(
    plans:List<PlanSnapshot>,
    recordedAt:Instant,
    zone:ZoneId
):List<Pair<PlanSnapshot,PlannedSession>> {
    val recordedDate=recordedAt.atZone(zone).toLocalDate()
    val earliest=recordedDate.minusDays(3)
    return plans.filter {
        it.status in setOf("approved","paused") && Engine1Engines.canManage(it)
    }.flatMap { plan ->
        plan.sessions.filter { s ->
            s.discipline=="run" && s.linkedActivityId==null &&
                s.status !in setOf("missed","skipped","unassigned") &&
                s.date in earliest..recordedDate
        }.map { plan to it }
    }.sortedWith(compareByDescending<Pair<PlanSnapshot,PlannedSession>> {
        it.second.date
    }.thenBy { it.first.id }.thenBy { it.second.id })
}

/**
 * A single compact action in Activity Detail > Overview. Existing source
 * identity, manual linking, and unlink eligibility are preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityPlanLink(
    state: TrainingState,
    activity: ActivityRecord,
    actions: PlanActions,
    matchedSourceIds: Set<String> = setOf(activity.id)
) {
    val hasRunningPlan = remember(state.plans) { hasRunningPlanForActivityLink(state.plans) }
    if (!hasRunningPlan) return

    var choosing by remember(activity.id) { mutableStateOf(false) }
    var selectedChoice by remember(activity.id) { mutableStateOf<Pair<String,String>?>(null) }
    var linkedDetails by remember(activity.id) { mutableStateOf(false) }

    // A grouped Garmin + Intervals activity can already be linked through
    // the secondary source. Keep the same lookup as before the redesign.
    val linked = state.plans.flatMap { plan ->
        plan.sessions.filter { it.linkedActivityId in matchedSourceIds }
            .map { plan to it }
    }.firstOrNull()

    val choices = remember(state.plans, activity.startedAt) {
        eligiblePlannedRunsForLink(state.plans,activity.startedAt,ZoneId.systemDefault())
    }
    // Keep the action accessible when no nearby session matches: the sheet
    // explains the date window instead of silently disabling manual linking.
    val enabled = !state.busy
    val tint = if (enabled) linkLime else linkMuted
    val label = if (linked != null) "Linked to plan" else "Link run"

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("activity-link-run-action"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF111B17),
        border = BorderStroke(1.dp, if (enabled) Color(0xFF34463C) else Color(0xFF28332D))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = enabled) {
                    if (linked == null) { selectedChoice=null;choosing = true } else linkedDetails = true
                }
                .semantics {
                    if (linked == null && choices.isEmpty())
                        contentDescription = "No eligible planned workouts nearby. Tap to view the date window."
                }
                .heightIn(min = 50.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (linked == null) LinkRunGlyph(tint) else {
                Text("✓", fontSize = 18.sp, color = tint, fontWeight = FontWeight.Bold)
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = if (enabled) linkInk else linkMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text("›", fontSize = 23.sp, color = if (enabled) linkMuted else Color(0xFF546059))
        }
    }

    if (linkedDetails && linked != null) {
        val (plan, session) = linked
        AlertDialog(
            onDismissRequest = { linkedDetails = false },
            title = { Text("Linked to plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(session.title, style = MaterialTheme.typography.titleMedium)
                    Text("Scheduled " + session.date + " · recorded " + activity.dateLabel)
                    Text("Planned " + session.effectiveMinutes + " min · recorded elapsed " + activity.durationLabel)
                    if (session.suggestion?.accepted != true) session.distanceKm?.let {
                        Text("Planned %.2f km · recorded %s".format(it, activity.distanceLabel))
                    }
                    Text(
                        "Manual link only. Original targets and workout logs are unchanged.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { linkedDetails = false }) { Text("Done") }
            },
            dismissButton = {
                if (Engine1Engines.canManage(plan) &&
                    plan.status in setOf("approved", "paused")) {
                    TextButton(
                        onClick = {
                            actions.link(plan.id, plan.revision, session.id, null)
                            linkedDetails = false
                        },
                        enabled = !state.busy,
                        modifier = Modifier.testTag("activity-unlink-run")
                    ) { Text("Unlink run") }
                }
            }
        )
    }

    if (choosing) {
        val selected=choices.firstOrNull { (plan,session) ->
            selectedChoice== (plan.id to session.id)
        }
        ModalBottomSheet(
            onDismissRequest={ choosing=false },
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=Color(0xFF171C18),
            contentColor=linkInk,
            shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),
            modifier=Modifier.testTag("activity-link-bottom-sheet")
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start=18.dp,end=18.dp,bottom=16.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Link to planned run",fontSize=22.sp,
                    fontWeight=FontWeight.Bold,color=linkInk)
                Text("Select the workout you completed.",
                    fontSize=12.sp,color=linkMuted)
                Surface(shape=RoundedCornerShape(12.dp),
                    color=Color(0xFF242C23)) {
                    Text("Your recorded activity data stays unchanged.",
                        Modifier.padding(12.dp),fontSize=11.sp,color=linkInk)
                }
                val recordedDay=activity.startedAt.atZone(
                    ZoneId.systemDefault()).toLocalDate()
                Text("PLANNED WORKOUTS · ${recordedDay.minusDays(3)} – $recordedDay",
                    color=linkMuted,fontSize=10.sp,fontWeight=FontWeight.Bold)
                if(choices.isEmpty()) {
                    Text("No eligible planned run in the previous three days or today.",
                        color=linkMuted,modifier=Modifier.padding(vertical=18.dp))
                } else LazyColumn(
                    modifier=Modifier.fillMaxWidth().heightIn(max=430.dp)
                        .testTag("activity-link-choices"),
                    verticalArrangement=Arrangement.spacedBy(8.dp)
                ) {
                    items(choices.size) { index ->
                        val (plan,session)=choices[index]
                        val isSelected=selectedChoice == (plan.id to session.id)
                        Surface(
                            onClick={ selectedChoice=plan.id to session.id },
                            modifier=Modifier.fillMaxWidth().heightIn(min=65.dp)
                                .testTag("activity-link-option-${plan.id}-${session.id}"),
                            shape=RoundedCornerShape(13.dp),
                            color=if(isSelected)Color(0xFF2B3925) else Color(0xFF222923),
                            border=BorderStroke(if(isSelected)2.dp else 1.dp,
                                if(isSelected)linkLime else Color(0xFF3B443A))
                        ) {
                            Column(Modifier.padding(12.dp),
                                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(session.date.toString(),fontSize=11.sp,
                                    fontWeight=FontWeight.SemiBold,
                                    color=if(isSelected)linkLime else linkMuted)
                                Text(session.effectiveTitle,fontSize=14.sp,
                                    fontWeight=FontWeight.SemiBold,color=linkInk)
                                Text(listOfNotNull(
                                    session.distanceKm?.let { "%.1f km".format(it) },
                                    "${session.effectiveMinutes} min planned"
                                ).joinToString(" · "),fontSize=11.sp,color=linkMuted)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick={choosing=false},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                        Text("Cancel",color=linkInk)
                    }
                    Surface(
                        onClick={
                            selected?.let { (plan,session) ->
                                actions.link(plan.id,plan.revision,session.id,activity.id)
                                choosing=false
                            }
                        },
                        enabled=selected!=null && !state.busy,
                        modifier=Modifier.weight(2f).heightIn(min=48.dp)
                            .testTag("activity-link-confirm"),
                        shape=RoundedCornerShape(12.dp),
                        color=if(selected!=null && !state.busy)linkLime else Color(0xFF4E5A49)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text("Link Run",color=Color(0xFF101610),
                                fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }
}

/** Two intersecting outlined capsules, rendered in the approved lime accent. */
@Composable
private fun LinkRunGlyph(color: Color) {
    Canvas(Modifier.size(21.dp)) {
        val line = 2.dp.toPx()
        rotate(-42f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * .12f, size.height * .31f),
                size = Size(size.width * .44f, size.height * .37f),
                cornerRadius = CornerRadius(size.height * .19f),
                style = Stroke(width = line)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * .44f, size.height * .31f),
                size = Size(size.width * .44f, size.height * .37f),
                cornerRadius = CornerRadius(size.height * .19f),
                style = Stroke(width = line)
            )
        }
    }
}
