package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private object ScheduleLook {
    val black = Color(0xFF000000)
    val card = Color(0xFF171B1C)
    val row = Color(0xFF191D1E)
    val outline = Color(0xFF383F40)
    val text = Color(0xFFF5F7F7)
    val secondary = Color(0xFFA5AFB0)
    val lime = Color(0xFFD8FB70)
}

/** Only actual, assigned plan sessions get a date in the Training schedule. */
internal data class TrainingScheduleDate(
    val date: LocalDate,
    val sessions: List<PlannedSession>
)

internal fun tracksideScheduledDates(sessions: List<PlannedSession>): List<TrainingScheduleDate> =
    sessions.asSequence()
        .filter { it.status != "unassigned" }
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, rows) -> TrainingScheduleDate(date, rows) }

/**
 * Owner-approved Training landing: a Google Calendar Schedule-style list
 * displaying dates ONLY when a saved approved plan has workouts on that date.
 * The legacy plan review, feedback, import/export and approval UI is retained
 * through Plan tools, and stays the default for draft/paused plans.
 */
@Composable
internal fun StrideLabTrainingHome(
    state: TrainingState,
    actions: PlanActions,
    onBuild: () -> Unit,
    onOpen: (String, String) -> Unit,
    onSignIn: () -> Unit,
    onManage: (String, LocalDate) -> Unit
) {
    val savedPlans = state.plans.filter { it.status != "archived" }
    val approved = savedPlans.filter { it.status == "approved" }
    val requiresReview = savedPlans.any { it.status in setOf("draft", "paused") }
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlanTools by rememberSaveable { mutableStateOf(false) }
    val active = approved.firstOrNull { it.id == selectedPlanId } ?: approved.firstOrNull()

    if (showPlanTools || (active == null && requiresReview)) {
        Column(Modifier.fillMaxSize().background(ScheduleLook.black)) {
            if (showPlanTools) {
                TextButton(
                    onClick = { showPlanTools = false },
                    modifier = Modifier.padding(start = 10.dp).testTag("training-return-to-schedule")
                ) { Text("‹  Back to schedule", color = ScheduleLook.lime) }
            }
            Box(Modifier.weight(1f)) {
                TrainingScreen(state, actions, onBuild, onOpen, onSignIn, onManage)
            }
        }
        return
    }

    val days = active?.let { tracksideScheduledDates(it.sessions) }.orEmpty()
    val locale = Locale.getDefault()
    val dayFormatter = DateTimeFormatter.ofPattern("EEE", locale)
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(ScheduleLook.black)
            .testTag("training-schedule-screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "training-heading") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("STRIDELAB", color = ScheduleLook.secondary, fontSize = 11.sp)
                    Text(
                        "Training", color = ScheduleLook.text,
                        fontSize = 31.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp
                    )
                }
                Box(
                    Modifier.size(44.dp).background(Color(0xFF202424), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null,
                        tint = ScheduleLook.text,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        if (state.initializing) {
            item(key = "training-initializing") {
                Text("Loading saved training plans…", color = ScheduleLook.secondary)
            }
        } else if (active == null) {
            item(key = "training-no-plan") {
                TrainingEmptyHero(
                    status = "NO ACTIVE PLAN",
                    heading = "Your training starts here.",
                    description = "Build a schedule around your goals, disciplines, and availability.",
                    action = "Create training plan",
                    onAction = onBuild
                )
            }
            item(key = "training-empty-agenda") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trackside_figma_calendar),
                        contentDescription = null,
                        tint = Color(0xFF808B8B),
                        modifier = Modifier.size(25.dp)
                    )
                    Text(
                        "No scheduled training", color = ScheduleLook.text,
                        fontWeight = FontWeight.Medium, fontSize = 15.sp
                    )
                    Text(
                        "Your workouts will appear here after you approve a plan.",
                        color = ScheduleLook.secondary, fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        } else {
            item(key = "training-active-plan") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                "ACTIVE PLAN", color = ScheduleLook.lime,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                active.name ?: active.request.goal,
                                color = ScheduleLook.text,
                                fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                val first = active.request.start
                                val last = first.plusWeeks(active.request.weeks.toLong()).minusDays(1)
                                onManage(active.id, LocalDate.now().coerceIn(first, last))
                            },
                            modifier = Modifier.testTag("training-manage-plan")
                        ) { Text("Manage", color = ScheduleLook.text) }
                    }
                    if (approved.size > 1) {
                        // Account-owned plans only; do not silently hide others.
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            items(approved, key = { it.id }) { candidate ->
                                Surface(
                                    onClick = { selectedPlanId = candidate.id },
                                    shape = RoundedCornerShape(22.dp),
                                    color = if (candidate.id == active.id) Color(0xFF303930)
                                        else ScheduleLook.card,
                                    border = BorderStroke(1.dp, ScheduleLook.outline)
                                ) {
                                    Text(
                                        candidate.name ?: candidate.request.goal,
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = ScheduleLook.text,
                                        fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (days.isEmpty()) {
                item(key = "training-approved-empty") {
                    Text(
                        "No scheduled sessions in this approved plan.",
                        color = ScheduleLook.secondary, fontSize = 13.sp
                    )
                }
            } else {
                days.forEachIndexed { index, day ->
                    if (index == 0 || YearMonth.from(days[index - 1].date) !=
                        YearMonth.from(day.date)
                    ) {
                        item(key = "training-month-${YearMonth.from(day.date)}") {
                            Text(
                                day.date.format(monthFormatter),
                                color = ScheduleLook.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(top = if (index == 0) 6.dp else 12.dp)
                                    .testTag("training-month-${YearMonth.from(day.date)}")
                            )
                        }
                    }
                    item(key = "training-date-${day.date}") {
                        Row(
                            Modifier.fillMaxWidth()
                                .testTag("training-date-${day.date}"),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                Modifier.width(48.dp).padding(top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    day.date.format(dayFormatter).uppercase(locale),
                                    color = ScheduleLook.secondary, fontSize = 10.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    day.date.dayOfMonth.toString(),
                                    color = ScheduleLook.text,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                day.sessions.forEach { original ->
                                    val workout = active.cardPresentation(original)
                                    TrainingAgendaWorkout(
                                        session = workout,
                                        hasDraft = state.workoutDrafts.containsKey(active.id to original.id),
                                        onClick = { onOpen(active.id, original.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!state.initializing) {
            item(key = "training-plan-tools") {
                TextButton(
                    onClick = { showPlanTools = true },
                    modifier = Modifier.fillMaxWidth().testTag("training-plan-tools")
                ) {
                    Text(
                        if (active == null) "Restore a plan backup"
                        else "Plan tools · Feedback & backups",
                        color = ScheduleLook.secondary, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainingEmptyHero(
    status: String,
    heading: String,
    description: String,
    action: String,
    onAction: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(ScheduleLook.card, RoundedCornerShape(22.dp))
            .padding(20.dp).testTag("training-empty-hero"),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(ScheduleLook.lime, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(status, color = ScheduleLook.lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(86.dp)
                    .background(Color(0xFF252D2C), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.trackside_figma_calendar),
                    contentDescription = null,
                    tint = ScheduleLook.lime,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Text(
            heading,
            color = ScheduleLook.text,
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            description,
            color = ScheduleLook.secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Surface(
            onClick = onAction,
            shape = RoundedCornerShape(28.dp),
            color = ScheduleLook.lime,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("training-create-plan")
                .semantics { contentDescription = action }
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    action, color = Color(0xFF111410),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrainingAgendaWorkout(
    session: PlannedSession,
    hasDraft: Boolean,
    onClick: () -> Unit
) {
    val accent = tracksideDisciplineColor(session.discipline)
    val icon = when (session.discipline) {
        "run" -> R.drawable.trackside_discipline_running
        "str" -> R.drawable.trackside_discipline_strength
        "cal" -> R.drawable.trackside_discipline_calisthenics
        else -> R.drawable.trackside_discipline_conditioning
    }
    val detail = buildList {
        add(disciplineLabel(session.discipline))
        session.distanceKm?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            add("Target ${String.format(Locale.getDefault(), "%.1f", it)} km")
        }
        if (session.effectiveMinutes > 0) add("${session.effectiveMinutes} min planned")
        if (session.status == "completed") add("Completed")
        else if (session.status == "in_progress") add("In progress")
        else if (session.status == "missed") add("Missed")
        if (hasDraft) add("Entry draft")
    }.joinToString(" · ")
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .testTag("workout-${session.id}")
            .semantics {
                contentDescription = "${session.effectiveTitle}, $detail"
            },
        shape = RoundedCornerShape(16.dp),
        color = ScheduleLook.row,
        border = BorderStroke(1.dp, ScheduleLook.outline)
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .background(accent.copy(alpha = .14f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(23.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    session.effectiveTitle,
                    color = ScheduleLook.text,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    detail,
                    color = ScheduleLook.secondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
