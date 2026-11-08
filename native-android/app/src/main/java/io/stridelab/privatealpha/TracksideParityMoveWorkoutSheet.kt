package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
        containerColor = StrideWorkoutVisuals.card,
        contentColor = StrideWorkoutVisuals.white,
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp),
        dragHandle = { Box(
            Modifier.padding(top = 12.dp).width(44.dp).height(4.dp)
                .background(StrideWorkoutVisuals.muted, RoundedCornerShape(3.dp))
        ) }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 590.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Text("TRAINING  ·  PLANNED",fontSize=11.sp,fontWeight=FontWeight.Medium,
                color=StrideWorkoutVisuals.lime)
            Text(if (review) "Review the proposed move" else "Choose a new day",
                fontSize = 25.sp, color = StrideWorkoutVisuals.white, fontWeight = FontWeight.Bold)
            Text(session.effectiveTitle + " · " +
                session.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                fontSize = 12.sp, color = StrideWorkoutVisuals.muted)
            Text("AVAILABLE DAYS", fontSize = 11.sp,
                color = StrideWorkoutVisuals.muted, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                destinations.forEach { day ->
                    val canSelect = day >= LocalDate.now() && day in plan.request.start..last &&
                        day.dayOfWeek.value in plan.request.days && day != session.date
                    Surface(
                        modifier = Modifier.width(49.dp).fillMaxHeight()
                            .testTag("move-destination-" + day.toString()),
                        onClick = { proposed = day; review = false },
                        enabled = canSelect,
                        color = if (day == proposed) Color(0xFF273A1B) else Color(0xFF17191A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp,
                            if (day == proposed) StrideWorkoutVisuals.lime else StrideWorkoutVisuals.divider)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(day.dayOfWeek.name.take(2),
                                fontSize = 10.sp, color = StrideWorkoutVisuals.muted)
                            Text(day.dayOfMonth.toString(),
                                fontSize = 16.sp, color = StrideWorkoutVisuals.white,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 177.dp),
                color = StrideWorkoutVisuals.card,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, StrideWorkoutVisuals.divider)
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PLANNED WITH THIS MOVE", fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = StrideWorkoutVisuals.muted)
                    if (candidate == null) Text("Choose an available day to preview its existing sessions.",
                        color = StrideWorkoutVisuals.white)
                    else {
                        targetRows.forEach { existing ->
                            Text(existing.effectiveTitle + " · existing",
                                color = StrideWorkoutVisuals.muted)
                        }
                        Text(session.effectiveTitle + " · proposed",
                            color = if(session.discipline=="run") StrideWorkoutVisuals.lime else tracksideDisciplineColor(session.discipline),
                            fontWeight = FontWeight.Bold)
                        Text("Daily slots: " + (targetRows.size + 1) + " / 2",
                            fontSize = 11.sp, color = StrideWorkoutVisuals.muted)
                    }
                    HorizontalDivider(color = StrideWorkoutVisuals.divider)
                    Text("RECOVERY PENDING · Load and workout compatibility",
                        fontSize = 11.sp, color = StrideWorkoutVisuals.muted)
                    Text("An open session slot does not guarantee safe recovery.",
                        style = MaterialTheme.typography.bodySmall, color = StrideWorkoutVisuals.muted)
                }
            }
            if (review) Text(
                "Not applied. The existing coaching engine will validate scheduling and revision conflicts when you confirm.",
                color = StrideWorkoutVisuals.muted, fontSize = 12.sp)
            else Text(
                "Active plan stays unchanged until review and confirmation.",
                color = StrideWorkoutVisuals.muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(49.dp)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        if (review && available && candidate != null) {
                            if (onReviewDestination != null) {
                                // Review as a separate, read-only screen. The caller controls
                                // dismissal and preserves Back to the uncommitted selection.
                                onReviewDestination(candidate)
                            } else {
                                actions.move(plan.id, plan.revision, session.id, candidate)
                                onDismiss()
                            }
                        } else review = true
                    },
                    enabled = available,
                    colors = ButtonDefaults.buttonColors(containerColor = StrideWorkoutVisuals.lime,
                        contentColor=Color(0xFF10120D)),
                    modifier = Modifier.weight(1.7f).height(49.dp)
                        .testTag("move-review"),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text(if (review) "Confirm move" else "Review move",
                        color = Color(0xFF10120D))
                }
            }
            Text("Pinned workouts must be explicitly unpinned before moving.",
                fontSize = 11.sp, color = StrideWorkoutVisuals.muted)
        }
    }
}
