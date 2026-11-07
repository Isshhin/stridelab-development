package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
    val hasRunningPlan = remember(state.plans) {
        state.plans.any { plan ->
            plan.status in setOf("approved", "paused") &&
                Engine1Engines.canManage(plan) &&
                plan.sessions.any { it.discipline == "run" }
        }
    }
    if (!hasRunningPlan) return

    var choosing by remember(activity.id) { mutableStateOf(false) }
    var linkedDetails by remember(activity.id) { mutableStateOf(false) }

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
                    if (linked == null) choosing = true else linkedDetails = true
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
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Choose the planned run") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "Choose a workout to link manually. The source date may differ; " +
                                "planned targets are never copied into recorded metrics."
                        )
                    }
                    items(choices.size) { index ->
                        val (plan, session) = choices[index]
                        OutlinedButton(
                            onClick = {
                                actions.link(plan.id, plan.revision, session.id, activity.id)
                                choosing = false
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                session.date.toString() + " · " + session.title +
                                    " · " + session.effectiveMinutes + " planned min"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) { Text("Cancel") }
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
