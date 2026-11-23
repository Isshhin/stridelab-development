package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Approved 736:640 / source 732:166: 620dp active-plan move sheet.
 * The choice and proposed destination never mutate a plan. Only an explicit
 * second confirmation calls the EXISTING revision-guarded PlanActions.move.
 * The engine, not the presentation, performs authoritative feasibility checks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TracksideParityMoveWorkoutSheet(
    plan: PlanSnapshot,
    session: PlannedSession,
    actions: PlanActions,
    onDismiss: () -> Unit,
    onReviewDestination: ((LocalDate) -> Unit)? = null
) {
    val weekStart = tracksideCalendarMonday(session.date)
    val last = plan.request.start.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val destinations = (0L..6L).map { weekStart.plusDays(it) }
    var proposed by remember(session.id) { mutableStateOf<LocalDate?>(null) }
    var review by remember(session.id) { mutableStateOf(false) }
    val candidate = proposed
    val targetRows = candidate?.let { day -> plan.sessions.filter {
        it.date == day && it.id != session.id &&
            it.status !in setOf("missed", "skipped")
    } } ?: emptyList()
    val available = candidate != null && candidate != session.date &&
        candidate >= LocalDate.now() && candidate in plan.request.start..last &&
        candidate.dayOfWeek.value in plan.request.days && targetRows.size < 2 &&
        session.status in setOf("planned", "in_progress") &&
        session.log == null && session.linkedActivityId == null &&
        (session.kind != "long" || candidate.dayOfWeek.value == plan.request.longDay) &&
        Engine1Engines.canManage(plan)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StrideLabVisualPalette.amoledBlack,
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp),
        dragHandle = { Box(
            Modifier.padding(top = 12.dp).width(44.dp).height(4.dp)
                .background(StrideLabVisualPalette.border, RoundedCornerShape(3.dp))
        ) }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Text("MOVE WORKOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp, color = StrideLabVisualPalette.muted)
            Text(if (review) "Review the proposed move" else "Choose a new day",
                fontSize = 25.sp, color = StrideLabVisualPalette.ink, fontWeight = FontWeight.ExtraBold)
            Text(session.effectiveTitle + " · " +
                session.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                fontSize = 12.sp, color = StrideLabVisualPalette.secondary)
            Text("AVAILABLE DAYS", fontSize = 10.5.sp, letterSpacing = 1.2.sp,
                color = StrideLabVisualPalette.muted, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().height(66.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                destinations.forEach { day ->
                    val canSelect = day >= LocalDate.now() && day in plan.request.start..last &&
                        day.dayOfWeek.value in plan.request.days && day != session.date
                    val selectedDay = day == proposed
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .testTag("move-destination-" + day.toString()),
                        onClick = { proposed = day; review = false },
                        enabled = canSelect,
                        color = if (selectedDay) StrideLabVisualPalette.limeTint else StrideLabVisualPalette.surfaceRaised,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            if (selectedDay) 1.5.dp else 1.dp,
                            if (selectedDay) StrideLabVisualPalette.lime else StrideLabVisualPalette.border
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(day.dayOfWeek.name.take(2),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedDay) StrideLabVisualPalette.lime else StrideLabVisualPalette.muted)
                            Text(day.dayOfMonth.toString(),
                                fontSize = 16.sp,
                                color = if (selectedDay) StrideLabVisualPalette.lime else if (canSelect) StrideLabVisualPalette.ink else StrideLabVisualPalette.muted.copy(alpha = 0.45f),
                                fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            val hasConflict = candidate != null && plan.sessions.any { other ->
                other.id != session.id && other.status in setOf("planned", "in_progress") &&
                    (other.date == candidate || Engine15IndependentPlanGenerator.conflict(session.copy(date = candidate), other))
            }
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp),
                color = StrideLabVisualPalette.surface,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, if (hasConflict) StrideLabVisualPalette.moderateAmber.copy(alpha = 0.6f) else StrideLabVisualPalette.border)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("PLANNED WITH THIS MOVE", fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.muted)
                        if (candidate != null) {
                            Surface(
                                color = if (hasConflict) StrideLabVisualPalette.moderateAmber.copy(alpha = 0.16f) else StrideLabVisualPalette.limeTint,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (hasConflict) StrideLabVisualPalette.moderateAmber else StrideLabVisualPalette.lime)
                            ) {
                                Text(
                                    if (hasConflict) "Tight / Stacked" else "Ideal Spacing",
                                    color = if (hasConflict) StrideLabVisualPalette.moderateAmber else StrideLabVisualPalette.lime,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    if (candidate == null) Text("Choose a target day to preview its schedule.",
                        color = StrideLabVisualPalette.ink, fontSize = 13.sp)
                    else {
                        targetRows.forEach { existing ->
                            Text(existing.effectiveTitle + " · existing",
                                color = StrideLabVisualPalette.muted, fontSize = 12.sp)
                        }
                        Text(session.effectiveTitle + " · proposed",
                            color = StrideLabVisualPalette.disciplineColor(session.discipline),
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (targetRows.isNotEmpty()) "Stacked day (${targetRows.size + 1} sessions) · Separate AM/PM if possible."
                            else if (hasConflict) "Back-to-back load · Prioritize recovery & easy effort."
                            else "Clean recovery spacing around this session.",
                            fontSize = 11.sp,
                            color = if (hasConflict) StrideLabVisualPalette.moderateAmber else StrideLabVisualPalette.easyTeal
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(49.dp)
                ) { Text("Cancel", color = StrideLabVisualPalette.ink) }
                Button(
                    onClick = {
                        if (review && available && candidate != null) {
                            if (onReviewDestination != null) {
                                onReviewDestination(candidate)
                            } else {
                                actions.move(plan.id, plan.revision, session.id, candidate)
                                onDismiss()
                            }
                        } else review = true
                    },
                    enabled = available,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StrideLabVisualPalette.lime,
                        contentColor = StrideLabVisualPalette.canvas
                    ),
                    modifier = Modifier.weight(1.7f).height(49.dp)
                        .testTag("move-review"),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (review) "Confirm move" else "Review move",
                        fontWeight = FontWeight.Bold)
                }
            }
            Text("Pinned workouts must be explicitly unpinned before moving.",
                fontSize = 11.sp, color = TracksidePalette.textMuted)
        }
    }
}
