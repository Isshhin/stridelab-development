package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Art-directed Manage Plan screen.
 * Pure AMOLED obsidian surfaces, gradient weekly volume overview bars with glowing top caps,
 * crisp tabular KPI blocks, and clean interactive controls.
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
                TextButton(onClick = onBack) { Text("‹ Back to Training", color = StrideLabVisualPalette.lime) }
                Text("This plan is no longer available here.", color = StrideLabVisualPalette.secondary)
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
    var showRecommendations by rememberSaveable(plan.id) { mutableStateOf(false) }

    Screen("Manage plan", "CURRENT TRAINING PLAN", compactTitle = true, dense = true) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("manage-back")) {
                    Text("‹ Back to Training", color = StrideLabVisualPalette.lime, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StrideLabVisualPalette.limeTint)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        plan.status.uppercase(Locale.getDefault()),
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StrideLabVisualPalette.lime
                    )
                }
            }
            Text(
                plan.name ?: plan.request.goal,
                style = MaterialTheme.typography.titleLarge,
                color = StrideLabVisualPalette.ink,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                first.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " – " +
                    last.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                color = StrideLabVisualPalette.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            val week = tracksideTrainingWeek(plan, inRange)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(plan.request.weeks.toString(), "Weeks", StrideLabVisualPalette.lime),
                    Triple(week.sumOf { it.sessions.size }.toString(), "Sessions", StrideLabVisualPalette.ink),
                    Triple(week.count { it.sessions.isNotEmpty() }.toString(), "Active days", StrideLabVisualPalette.easyTeal)
                ).forEach { (value, label, accent) ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 70.dp),
                        shape = RoundedCornerShape(15.dp),
                        color = StrideLabVisualPalette.surface,
                        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(11.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                value,
                                fontSize = 22.sp,
                                color = accent,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(label, fontSize = 11.sp, color = StrideLabVisualPalette.muted)
                        }
                    }
                }
            }
        }
        item { TracksidePlanWeekOverview(plan, inRange) }
        item {
            Text(
                "COACHING & SCHEDULING",
                color = StrideLabVisualPalette.muted,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (plan.engineVersion == ENGINE15_VERSION && Engine1Engines.canManage(plan)) {
            item { Engine15CoachReviewCard(plan, actions, busy) }
            item { Engine15ScheduleReview(plan, actions, busy) }
            item {
                Surface(
                    onClick = { showRecommendations = !showRecommendations },
                    modifier = Modifier.fillMaxWidth().testTag("manage-recommendations"),
                    color = StrideLabVisualPalette.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Workout recommendations",
                            modifier = Modifier.weight(1f),
                            color = StrideLabVisualPalette.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (showRecommendations) "Hide" else "Review ›",
                            color = StrideLabVisualPalette.lime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (showRecommendations) item { Engine15RecommendationReview(plan, actions, busy) }
        }
        item {
            Text(
                "PLAN CONTROLS",
                color = StrideLabVisualPalette.muted,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            TracksideManageAction(
                title = "Edit a workout",
                subtitle = "Move, adjust, or lock upcoming sessions",
                iconRes = R.drawable.trackside_figma_edit,
                accent = StrideLabVisualPalette.lime,
                enabled = canManage && future.isNotEmpty(),
                modifier = Modifier.testTag("manage-edit-workout")
            ) { editExpanded = !editExpanded }
        }
        if (editExpanded) {
            if (future.isEmpty()) item { Text("No future sessions to edit", color = StrideLabVisualPalette.secondary) }
            future.forEach { session ->
                item(key = "manage-future-${session.id}") {
                    Surface(
                        onClick = { onWorkout(session.id) },
                        modifier = Modifier.fillMaxWidth().testTag("manage-workout-${session.id}"),
                        color = StrideLabVisualPalette.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(StrideLabVisualPalette.disciplineColor(session.discipline))
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.effectiveTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StrideLabVisualPalette.ink,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${session.date} · ${session.effectiveMinutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StrideLabVisualPalette.secondary
                                )
                            }
                            if (plan.engineVersion == ENGINE15_VERSION && session.status == "planned" && session.log == null && session.linkedActivityId == null) {
                                val locked = session.id in plan.request.lockedSessionDays
                                TextButton(onClick = { actions.lockWorkout(plan.id, plan.revision, session.id, !locked) }, enabled = canManage) {
                                    Text(if (locked) "Unlock" else "Lock", color = StrideLabVisualPalette.secondary)
                                }
                            }
                            TextButton(
                                onClick = { movingWorkout = session },
                                enabled = session.id !in plan.request.lockedSessionDays,
                                modifier = Modifier.testTag("manage-move-${session.id}")
                            ) {
                                Text("Move ›", color = StrideLabVisualPalette.lime, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            TracksideManageAction(
                title = "Create a revised draft",
                subtitle = "Build a fresh plan without modifying this active schedule",
                iconRes = R.drawable.trackside_figma_loop,
                accent = StrideLabVisualPalette.easyTeal,
                enabled = canManage,
                modifier = Modifier.testTag("manage-adjust-weeks")
            ) { confirmDraft = true }
        }
        if (plan.status == "approved") item {
            TracksideManageAction(
                title = "Pause training plan",
                subtitle = "Keep your completed history and schedule intact",
                iconRes = R.drawable.trackside_figma_pause,
                accent = StrideLabVisualPalette.moderateAmber,
                enabled = canManage,
                modifier = Modifier.testTag("manage-pause")
            ) { confirmPause = true }
        }
        if (plan.status == "paused" || plan.status == "draft") item {
            TracksideManageAction(
                title = if (plan.status == "paused") "Resume training plan" else "Approve this draft",
                subtitle = "Activate this training schedule on your calendar",
                iconRes = R.drawable.trackside_figma_loop,
                accent = StrideLabVisualPalette.lime,
                enabled = canManage,
                modifier = Modifier.testTag("manage-resume")
            ) { actions.approve(plan.id, plan.revision) }
        }
        item {
            OutlinedButton(
                onClick = { confirmArchive = true },
                enabled = canManage,
                modifier = Modifier.fillMaxWidth().testTag("manage-archive")
            ) {
                Text("Archive plan · keep history", color = StrideLabVisualPalette.highCoral)
            }
        }
    }

    movingWorkout?.let { session ->
        TracksideParityMoveWorkoutSheet(
            plan = plan,
            session = session,
            actions = actions,
            onDismiss = { movingWorkout = null },
            onReviewDestination = if (onReviewMove == null) null else { date ->
                movingWorkout = null
                onReviewMove(session.id, date)
            }
        )
    }

    if (confirmDraft) AlertDialog(
        onDismissRequest = { confirmDraft = false },
        containerColor = StrideLabVisualPalette.surface,
        title = { Text("Create a revised draft?", color = StrideLabVisualPalette.ink) },
        text = { Text("Opens the plan builder to draft a new plan. Your active plan and completed history remain untouched.", color = StrideLabVisualPalette.secondary) },
        confirmButton = { TextButton(onClick = { confirmDraft = false; onBuildDraft() }) { Text("Create draft", color = StrideLabVisualPalette.lime) } },
        dismissButton = { TextButton(onClick = { confirmDraft = false }) { Text("Cancel", color = StrideLabVisualPalette.secondary) } }
    )
    if (confirmPause) AlertDialog(
        onDismissRequest = { confirmPause = false },
        containerColor = StrideLabVisualPalette.surface,
        title = { Text("Pause training plan?", color = StrideLabVisualPalette.ink) },
        text = { Text("Pause the active schedule while preserving your workout history and saved sessions.", color = StrideLabVisualPalette.secondary) },
        confirmButton = { TextButton(onClick = { confirmPause = false; actions.pause(plan.id, plan.revision) }, enabled = canManage) { Text("Pause plan", color = StrideLabVisualPalette.moderateAmber) } },
        dismissButton = { TextButton(onClick = { confirmPause = false }) { Text("Cancel", color = StrideLabVisualPalette.secondary) } }
    )
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        containerColor = StrideLabVisualPalette.surface,
        title = { Text("Archive this plan?", color = StrideLabVisualPalette.ink) },
        text = { Text("The plan will leave your active calendar. Your completed workouts and saved records are retained.", color = StrideLabVisualPalette.secondary) },
        confirmButton = { TextButton(onClick = { confirmArchive = false; actions.archive(plan.id, plan.revision) }, enabled = canManage) { Text("Archive", color = StrideLabVisualPalette.highCoral) } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel", color = StrideLabVisualPalette.secondary) } }
    )
}

/** Art-directed weekly duration chart with vertical luminance gradient bars and glowing caps. */
@Composable
private fun TracksidePlanWeekOverview(plan: PlanSnapshot, selected: LocalDate) {
    val days = tracksideTrainingWeek(plan, selected)
    val high = (days.maxOfOrNull { it.plannedMinutes } ?: 0).coerceAtLeast(1)
    val total = days.sumOf { it.plannedMinutes }
    val monday = tracksideCalendarMonday(selected)
    val firstMonday = tracksideCalendarMonday(plan.request.start)
    val weekIndex = ChronoUnit.WEEKS.between(firstMonday, monday).toInt() + 1
    val last = plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val totalWeeks = ChronoUnit.WEEKS.between(firstMonday, tracksideCalendarMonday(last)).toInt() + 1

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("manage-week-overview"),
        shape = RoundedCornerShape(18.dp),
        color = StrideLabVisualPalette.surface,
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "WEEKLY LOAD",
                        fontSize = 10.sp,
                        letterSpacing = 1.3.sp,
                        color = StrideLabVisualPalette.muted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Planned ${total / 60}h ${total % 60}m",
                        fontSize = 15.sp,
                        color = StrideLabVisualPalette.ink,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    "Week $weekIndex / $totalWeeks",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = StrideLabVisualPalette.lime
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                days.forEach { day ->
                    val hasLong = day.sessions.any { it.kind == "long" }
                    val hasQuality = day.sessions.any { it.kind == "quality" }
                    val barColor = when {
                        hasLong -> StrideLabVisualPalette.lime
                        hasQuality -> StrideLabVisualPalette.moderateAmber
                        else -> StrideLabVisualPalette.easyTeal
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(82.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (day.plannedMinutes > 0) {
                                val height = (day.plannedMinutes.toFloat() / high * 78f).coerceAtLeast(8f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.76f)
                                        .height(height.dp)
                                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .background(StrideLabVisualPalette.barGradient(barColor))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(2.5.dp)
                                            .align(Alignment.TopCenter)
                                            .background(barColor)
                                    )
                                }
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(StrideLabVisualPalette.border)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            day.date.dayOfWeek.name.take(3),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (day.plannedMinutes > 0) StrideLabVisualPalette.secondary else StrideLabVisualPalette.muted,
                            maxLines = 1
                        )
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StrideLabVisualPalette.surface,
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    color = StrideLabVisualPalette.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = StrideLabVisualPalette.secondary,
                    maxLines = 3
                )
            }
            Text("›", color = StrideLabVisualPalette.muted, fontSize = 20.sp)
        }
    }
}
