package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val linkLime = Color(0xFFD8FB70)
private val linkInk = Color(0xFFEFF5F0)
private val linkMuted = Color(0xFF81918A)

internal fun hasRunningPlanForActivityLink(plans: List<PlanSnapshot>): Boolean =
    plans.any { plan ->
        plan.status in setOf("approved", "paused") &&
            Engine1Engines.canManage(plan) &&
            plan.sessions.any { it.discipline == "run" }
    }

/**
 * A single compact action in Activity Detail > Overview. Existing source
 * identity, manual linking, and unlink eligibility are preserved.
 */
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
    var linkedDetails by remember(activity.id) { mutableStateOf(false) }
    var selectedChoice by remember(activity.id) { mutableStateOf<Pair<String,String>?>(null) }

    // A grouped Garmin + Intervals activity can already be linked through
    // the secondary source. Keep the same lookup as before the redesign.
    val linked = state.plans.flatMap { plan ->
        plan.sessions.filter { it.linkedActivityId in matchedSourceIds }
            .map { plan to it }
    }.firstOrNull()

    val choices = remember(state.plans, activity.startedAt) {
        state.plans.filter {
            it.status in setOf("approved", "paused") && Engine1Engines.canManage(it)
        }.flatMap { plan ->
            plan.sessions.filter {
                it.discipline == "run" && it.linkedActivityId == null &&
                    it.status !in setOf("missed", "skipped")
            }.map { plan to it }
        }.sortedBy {
            abs(ChronoUnit.DAYS.between(
                it.second.date,
                activity.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            ))
        }
    }
    val enabled = !state.busy && (linked != null || choices.isNotEmpty())
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
                    if (linked == null) { selectedChoice=null; choosing = true } else linkedDetails = true
                }
                .semantics {
                    if (linked == null && choices.isEmpty())
                        contentDescription = "Link run unavailable: no eligible planned workouts"
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
        ModalBottomSheet(
            onDismissRequest = { choosing=false; selectedChoice=null },
            containerColor = StrideWorkoutVisuals.card,
            contentColor = StrideWorkoutVisuals.white,
            shape = RoundedCornerShape(topStart=26.dp,topEnd=26.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color=StrideWorkoutVisuals.muted) },
            modifier = Modifier.testTag("activity-link-picker")
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding()
                    .padding(horizontal=20.dp).padding(bottom=28.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)
            ) {
                Text("Link to planned workout",fontSize=23.sp,fontWeight=FontWeight.SemiBold,
                    color=StrideWorkoutVisuals.white)
                Text(
                    "Choose a planned run to associate with this recording. The source date may differ. Planned targets are never copied into recorded metrics.",
                    color=StrideWorkoutVisuals.muted,style=MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(color=StrideWorkoutVisuals.divider)
                LazyColumn(
                    modifier=Modifier.fillMaxWidth().weight(1f,fill=false),
                    verticalArrangement=Arrangement.spacedBy(4.dp),
                    contentPadding=PaddingValues(bottom=4.dp)
                ) {
                    items(choices.size) { index ->
                        val (plan,session)=choices[index]
                        val key=plan.id to session.id
                        val selected=selectedChoice==key
                        val shape=RoundedCornerShape(12.dp)
                        Surface(
                            color=if(selected) Color(0xFF25351A) else Color.Transparent,
                            border=BorderStroke(1.dp,
                                if(selected) StrideWorkoutVisuals.lime else Color.Transparent),
                            shape=shape,
                            onClick={ selectedChoice=key },
                            enabled=!state.busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)
                                .testTag("activity-link-choice-"+session.id)
                                .semantics { this.selected=selected }
                        ) {
                            Row(
                                Modifier.padding(horizontal=12.dp,vertical=10.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f),
                                    verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(session.title,
                                        color=StrideWorkoutVisuals.white,
                                        fontWeight=FontWeight.Medium,fontSize=15.sp,
                                        maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(
                                        "${session.date}  ·  ${session.effectiveMinutes} planned min",
                                        color=StrideWorkoutVisuals.muted,fontSize=12.sp,
                                        maxLines=1,overflow=TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if(index<choices.lastIndex && !selected)
                            HorizontalDivider(color=StrideWorkoutVisuals.divider.copy(alpha=.45f))
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    TextButton(
                        onClick={ choosing=false; selectedChoice=null },
                        modifier=Modifier.heightIn(min=48.dp)
                    ) { Text("Cancel",color=StrideWorkoutVisuals.muted) }
                    val selected=choices.firstOrNull {
                        selectedChoice== (it.first.id to it.second.id)
                    }
                    Button(
                        onClick={
                            selected?.let { (plan,session) ->
                                actions.link(plan.id,plan.revision,session.id,activity.id)
                                choosing=false
                                selectedChoice=null
                            }
                        },
                        enabled=selected!=null && !state.busy,
                        modifier=Modifier.weight(1f).heightIn(min=52.dp)
                            .testTag("activity-link-confirm"),
                        colors=ButtonDefaults.buttonColors(
                            containerColor=StrideWorkoutVisuals.lime,
                            contentColor=Color(0xFF10120D)
                        ),
                        shape=RoundedCornerShape(26.dp)
                    ) { Text("Link workout",fontWeight=FontWeight.SemiBold) }
                }
            }
        }
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
