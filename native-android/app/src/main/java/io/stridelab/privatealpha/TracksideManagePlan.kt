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
                Text("This plan is no longer available here.", color = TracksidePalette.textMuted)
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

    Screen("Manage plan", "CURRENT TRAINING PLAN", compactTitle = true, dense = true) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("manage-back")) { Text("‹ Back to Training") }
                Surface(shape = RoundedCornerShape(14.dp), color = TracksidePalette.surfaceRaised,
                    border = BorderStroke(1.dp, TracksidePalette.outline)) {
                    Text(plan.status.uppercase(Locale.getDefault()),
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall, color = TracksidePalette.text)
                }
            }
            Text(plan.name ?: plan.request.goal,
                style = MaterialTheme.typography.titleLarge, color = Color(0xFFF1F5EF),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(first.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " – " +
                last.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        item {
            val week=tracksideTrainingWeek(plan,inRange)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(
                    plan.request.weeks.toString() to "Weeks",
                    week.sumOf { it.sessions.size }.toString() to "Sessions",
                    week.count { it.sessions.isNotEmpty() }.toString() to "Active days"
                ).forEach { (value,label) ->
                    Surface(Modifier.weight(1f).heightIn(min=70.dp),
                        shape=RoundedCornerShape(12.dp),color=Color(0xFF1B211D),
                        border=BorderStroke(1.dp,Color(0xFF333C33))) {
                        Column(Modifier.padding(10.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(value,style=MaterialTheme.typography.titleLarge,
                                color=Color(0xFFF1F5EF),fontWeight=FontWeight.Bold)
                            Text(label,style=MaterialTheme.typography.labelSmall,
                                color=Color(0xFFAAB5AA))
                        }
                    }
                }
            }
        }
        item { TracksidePlanWeekOverview(plan, inRange) }
        item {
            Text("COACHING & SCHEDULING",color=Color(0xFFAAB5AA),
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold)
        }
        if (plan.engineVersion == ENGINE15_VERSION && Engine1Engines.canManage(plan)) {
            item { Engine15CoachReviewCard(plan,actions,busy) }
            item { Engine15ScheduleReview(plan, actions, busy) }
            item { Engine15RecommendationReview(plan, actions, busy) }
        }
        item {
            Text("PLAN CONTROLS",color=Color(0xFFAAB5AA),
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold)
        }
        item {
            TracksideManageAction(
                title = "Edit a workout",
                subtitle = "Move, change or remove a future session",
                iconRes = R.drawable.trackside_figma_edit, accent = Color(0xFFD8FB70),
                enabled = canManage && future.isNotEmpty(),
                modifier = Modifier.testTag("manage-edit-workout")
            ) { editExpanded = !editExpanded }
        }
        if (editExpanded) {
            if (future.isEmpty()) item { Text("No future sessions to edit", color = TracksidePalette.textMuted) }
            future.forEach { session ->
                item(key = "manage-future-" + session.id) {
                    Surface(onClick = { onWorkout(session.id) },
                        modifier = Modifier.fillMaxWidth().testTag("manage-workout-" + session.id),
                        color = TracksidePalette.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TracksidePalette.outline)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(9.dp).background(
                                tracksideDisciplineColor(session.discipline), RoundedCornerShape(5.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(session.effectiveTitle, style = MaterialTheme.typography.titleSmall,
                                    color = TracksidePalette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(session.date.toString() + " · " + session.effectiveMinutes + " planned min",
                                    style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
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
                                    color = TracksidePalette.text)
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
                iconRes = R.drawable.trackside_figma_loop, accent = Color(0xFFD8FB70),
                enabled = canManage, modifier = Modifier.testTag("manage-adjust-weeks")
            ) { confirmDraft = true }
        }
        if (plan.status == "approved") item {
            TracksideManageAction(
                title = "Pause training plan",
                subtitle = "Keep your history and schedule",
                iconRes = R.drawable.trackside_figma_pause, accent = Color(0xFFD8FB70),
                enabled = canManage, modifier = Modifier.testTag("manage-pause")
            ) { confirmPause = true }
        }
        if (plan.status == "paused" || plan.status == "draft") item {
            TracksideManageAction(
                title = if (plan.status == "paused") "Resume training plan" else "Approve this draft",
                subtitle = "Review the current plan before activating it",
                iconRes = R.drawable.trackside_figma_loop, accent = Color(0xFFD8FB70),
                enabled = canManage, modifier = Modifier.testTag("manage-resume")
            ) { actions.approve(plan.id, plan.revision) }
        }
        item {
            Text(if(Engine1Engines.canManage(plan))
                "Completed workouts remain saved when you pause, edit or archive a plan."
                else "This saved engine or policy is read-only. Recorded history remains available.",
                color=Color(0xFFAAB5AA),style=MaterialTheme.typography.bodySmall)
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
    val days=tracksideTrainingWeek(plan,selected)
    val high=(days.maxOfOrNull { it.plannedMinutes } ?: 0).coerceAtLeast(1)
    val total=days.sumOf { it.plannedMinutes }
    val monday=tracksideCalendarMonday(selected)
    val firstMonday=tracksideCalendarMonday(plan.request.start)
    val weekIndex=ChronoUnit.WEEKS.between(firstMonday,monday).toInt()+1
    val last=plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val totalWeeks=ChronoUnit.WEEKS.between(firstMonday,tracksideCalendarMonday(last)).toInt()+1

    Surface(Modifier.fillMaxWidth().testTag("manage-week-overview"),
        shape=RoundedCornerShape(14.dp),color=Color(0xFF1B211D),
        border=BorderStroke(1.dp,Color(0xFF333C33))) {
        Column(Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Your training week",style=MaterialTheme.typography.titleSmall,
                    color=Color(0xFFF1F5EF),fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                Text("Week $weekIndex of $totalWeeks",
                    style=MaterialTheme.typography.labelSmall,
                    color=Color(0xFFAAB5AA))
            }
            Text("Planned ${total/60}h ${total%60}m",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFFAAB5AA))
            Row(Modifier.fillMaxWidth().height(104.dp),
                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                days.forEachIndexed { index,day ->
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth().height(82.dp),
                            contentAlignment=Alignment.BottomCenter) {
                            if(day.plannedMinutes>0) {
                                val height=(day.plannedMinutes.toFloat()/high*78f).coerceAtLeast(3f)
                                Box(Modifier.fillMaxWidth().height(height.dp)
                                    .background(if(index==5)Color(0xFFD8FB70)
                                        else Color(0xFFA4CB6D),RoundedCornerShape(5.dp)))
                            }
                        }
                        Text(day.date.dayOfWeek.name.take(3),
                            style=MaterialTheme.typography.labelSmall,
                            color=Color(0xFFAAB5AA),maxLines=1)
                    }
                }
            }
        }
    }
}

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
    Surface(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = TracksidePalette.surface,
        border = BorderStroke(1.dp, TracksidePalette.outline)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(43.dp).background(accent.copy(alpha = .2f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = iconRes), contentDescription = null,
                    tint = accent, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    color = TracksidePalette.text, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = TracksidePalette.textMuted, maxLines = 4)
            }
            Text("›", color = TracksidePalette.textMuted, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
