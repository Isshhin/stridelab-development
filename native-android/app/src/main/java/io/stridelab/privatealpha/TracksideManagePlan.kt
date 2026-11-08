package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Dedicated native Manage Plan presentation, based on approved Figma 732:3.
 * Existing plan operations own all mutations. No synthetic changes or rewriting saved plans.
 */
@Composable
internal fun TracksideManagePlanScreen(
    plan: PlanSnapshot?,
    selected: LocalDate,
    busy: Boolean,
    actions: PlanActions,
    onBack: () -> Unit,
    onBuildDraft: () -> Unit,
    onWorkout: (String) -> Unit,
    onReviewMove: ((String, LocalDate) -> Unit)? = null
) {
    if (plan == null || plan.status == "archived") {
        Screen("Manage plan", "CURRENT TRAINING PLAN", compactTitle = true, dense = true) {
            item {
                TextButton(onClick = onBack) { Text("‹ Back to Training") }
                Text("This plan is no longer available here.", color = StrideWorkoutVisuals.muted)
            }
        }
        return
    }
    if (plan.status == "paused") {
        TracksideParityPausedPlan(plan, busy, actions, onBack)
        return
    }
    val canManage = Engine1Engines.canManage(plan) && !busy
    val first = plan.request.start
    val last = first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val inRange = selected.coerceIn(first, last)
    val future = plan.sessions.filter {
        it.date >= LocalDate.now() && it.status in setOf("planned", "in_progress")
    }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    var editExpanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    var movingWorkout by remember { mutableStateOf<PlannedSession?>(null) }
    var confirmDraft by rememberSaveable(plan.id) { mutableStateOf(false) }
    var confirmPause by rememberSaveable(plan.id) { mutableStateOf(false) }
    var confirmArchive by rememberSaveable(plan.id) { mutableStateOf(false) }

    StridePlanScreen("Manage plan", "Training", onBack) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(plan.name ?: plan.request.goal,
                    modifier=Modifier.weight(1f),
                    style=MaterialTheme.typography.headlineSmall,
                    color=StrideWorkoutVisuals.white,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(plan.status.uppercase(Locale.getDefault()),
                    style=MaterialTheme.typography.labelSmall,
                    color=StrideWorkoutVisuals.lime,
                    modifier=Modifier.padding(start=10.dp))
            }
            Text(first.format(DateTimeFormatter.ofPattern("d MMM yyyy"))+" – "+
                last.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                color=StrideWorkoutVisuals.muted,
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            TracksidePlanWeekOverview(plan, inRange)
        }
        item { TracksideSectionHeader("Manage your plan") }
        if (plan.engineVersion == ENGINE15_VERSION && Engine1Engines.canManage(plan)) {
            item { Engine15ScheduleReview(plan, actions, busy) }
            item { Engine15RecommendationReview(plan, actions, busy) }
        }
        item {
            TracksideManageAction(
                title = "Edit a workout",
                subtitle = "Move, change or remove a future session",
                iconRes = R.drawable.trackside_figma_edit, accent = StrideWorkoutVisuals.lime,
                enabled = canManage && future.isNotEmpty(),
                modifier = Modifier.testTag("manage-edit-workout")
            ) { editExpanded = !editExpanded }
        }
        if (editExpanded) {
            if (future.isEmpty()) item { Text("No future sessions to edit", color = StrideWorkoutVisuals.muted) }
            future.forEach { session ->
                item(key = "manage-future-" + session.id) {
                    Surface(onClick = { onWorkout(session.id) },
                        modifier = Modifier.fillMaxWidth().testTag("manage-workout-" + session.id),
                        color = StrideWorkoutVisuals.card,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StrideWorkoutVisuals.divider)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(9.dp).background(
                                tracksideDisciplineColor(session.discipline), RoundedCornerShape(5.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(session.effectiveTitle, style = MaterialTheme.typography.titleSmall,
                                    color = StrideWorkoutVisuals.white, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(session.date.toString() + " · " + session.effectiveMinutes + " planned min",
                                    style = MaterialTheme.typography.bodySmall, color = StrideWorkoutVisuals.muted)
                            }
                            if (plan.engineVersion == ENGINE15_VERSION && session.status == "planned" && session.log == null && session.linkedActivityId == null) {
                                val locked = session.id in plan.request.lockedSessionDays
                                TextButton(onClick = { actions.lockWorkout(plan.id, plan.revision, session.id, !locked) }, enabled = canManage) {
                                    Text(if (locked) "Unlock" else "Lock")
                                }
                            }
                            TextButton(onClick = { movingWorkout = session }, enabled = session.id !in plan.request.lockedSessionDays,
                                modifier = Modifier.testTag("manage-move-" + session.id)) {
                                Text("Move ›", style = MaterialTheme.typography.labelMedium,
                                    color = StrideWorkoutVisuals.white)
                            }
                        }
                    }
                }
            }
        }
        item {
            TracksideManageAction(
                title = "Adjust future weeks",
                subtitle = "Regenerate from a date you choose",
                iconRes = R.drawable.trackside_figma_loop, accent = StrideWorkoutVisuals.lime,
                enabled = canManage, modifier = Modifier.testTag("manage-adjust-weeks")
            ) { confirmDraft = true }
        }
        if (plan.status == "approved") item {
            TracksideManageAction(
                title = "Pause training plan",
                subtitle = "Keep your history and schedule",
                iconRes = R.drawable.trackside_figma_pause, accent = StrideWorkoutVisuals.lime,
                enabled = canManage, modifier = Modifier.testTag("manage-pause")
            ) { confirmPause = true }
        }
        if (plan.status == "paused" || plan.status == "draft") item {
            TracksideManageAction(
                title = if (plan.status == "paused") "Resume training plan" else "Approve this draft",
                subtitle = "Review the current plan before activating it",
                iconRes = R.drawable.trackside_figma_loop, accent = StrideWorkoutVisuals.lime,
                enabled = canManage, modifier = Modifier.testTag("manage-resume")
            ) { actions.approve(plan.id, plan.revision) }
        }
        item {
            StrideWorkoutSection("Your history stays intact") {
                Text("Completed workouts and recorded history are preserved. Any future plan change requires review.",
                    color = StrideWorkoutVisuals.white, style = MaterialTheme.typography.bodyMedium)
                if (!Engine1Engines.canManage(plan)) Text(
                    "This saved plan uses an unsupported engine or policy and is read-only here.",
                    color = StrideWorkoutVisuals.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedButton(onClick = { confirmArchive = true }, enabled = canManage,
                modifier = Modifier.fillMaxWidth().testTag("manage-archive")) {
                Text("Archive plan · keep records")
            }
        }
    }

    movingWorkout?.let { session ->
        TracksideParityMoveWorkoutSheet(
            plan = plan,
            session = session,
            actions = actions,
            onDismiss = { movingWorkout = null },
            onReviewDestination = if (onReviewMove == null) null else {
                date ->
                    movingWorkout = null
                    onReviewMove(session.id, date)
            }
        )
    }

    if (confirmDraft) AlertDialog(
        onDismissRequest = { confirmDraft = false },
        title = { Text("Create a reviewed draft?") },
        text = { Text("The existing planner creates a new draft. It cannot yet regenerate only future weeks from a selected date. Your approved plan and completed workouts remain unchanged.") },
        confirmButton = { TextButton(onClick = { confirmDraft = false; onBuildDraft() }) { Text("Create draft") } },
        dismissButton = { TextButton(onClick = { confirmDraft = false }) { Text("Cancel") } }
    )
    if (confirmPause) AlertDialog(
        onDismissRequest = { confirmPause = false },
        title = { Text("Pause training plan?") },
        text = { Text("Pause the approved schedule while preserving your workout history and saved sessions.") },
        confirmButton = { TextButton(onClick = { confirmPause = false; actions.pause(plan.id, plan.revision) }, enabled = canManage) { Text("Pause plan") } },
        dismissButton = { TextButton(onClick = { confirmPause = false }) { Text("Cancel") } }
    )
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("Archive this plan?") },
        text = { Text("The plan will leave your active calendar. Your completed workouts and saved records are retained.") },
        confirmButton = { TextButton(onClick = { confirmArchive = false; actions.archive(plan.id, plan.revision) }, enabled = canManage) { Text("Archive") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } }
    )
}

/** Static week preview: only scheduled minutes from persisted plan records. */
@Composable
private fun TracksidePlanWeekOverview(plan: PlanSnapshot, selected: LocalDate) {
    val days = tracksideTrainingWeek(plan, selected)
    val all = days.flatMap { it.sessions }
    val count = all.size
    val trainingDays = days.count { it.sessions.isNotEmpty() }
    val distribution = all.groupBy { it.discipline }.toList()
    val high = (days.maxOfOrNull { it.plannedMinutes } ?: 0).coerceAtLeast(1)
    val firstMonday = tracksideCalendarMonday(plan.request.start)
    val nowMonday = tracksideCalendarMonday(selected)
    val lastMonday = tracksideCalendarMonday(plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1))
    val totalCalendarWeeks = ChronoUnit.WEEKS.between(firstMonday, lastMonday).toInt() + 1
    val week = ChronoUnit.WEEKS.between(firstMonday, nowMonday).toInt() + 1

    Column(Modifier.fillMaxWidth().testTag("manage-week-overview"),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Week $week of $totalCalendarWeeks",
                style = MaterialTheme.typography.labelMedium, color = StrideWorkoutVisuals.muted,
                modifier = Modifier.weight(1f))
            Text("$count sessions · $trainingDays days", style = MaterialTheme.typography.labelSmall,
                color = StrideWorkoutVisuals.muted)
        }
        if (distribution.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                distribution.forEach { (discipline, rows) ->
                    Box(Modifier.weight(rows.size.toFloat()).height(8.dp)
                        .background(tracksideDisciplineColor(discipline), RoundedCornerShape(4.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                distribution.forEach { (discipline, rows) ->
                    Text(rows.size.toString() + " " + disciplineLabel(discipline),
                        modifier = Modifier.weight(rows.size.toFloat()), maxLines = 2,
                        color = tracksideDisciplineColor(discipline),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        StrideWorkoutSection("Your training week") {
            // Session/day counts above remain visible without duplicating them.
            Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.fillMaxWidth().height(92.dp), verticalArrangement = Arrangement.Bottom) {
                            day.byDiscipline.reversed().forEach { (discipline, minutes) ->
                                val height = (minutes.toFloat() / high * 88f).coerceAtLeast(3f)
                                Box(Modifier.fillMaxWidth().height(height.dp)
                                    .background(tracksideDisciplineColor(discipline), RoundedCornerShape(5.dp)))
                            }
                        }
                        Text(day.date.dayOfWeek.name.take(3), style = MaterialTheme.typography.labelSmall,
                            color = StrideWorkoutVisuals.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun TracksideManageAction(
    title: String,
    subtitle: String,
    iconRes: Int,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    StridePlanActionRow(title=title,subtitle=subtitle,enabled=enabled,
        modifier=modifier,onClick=onClick)
}
