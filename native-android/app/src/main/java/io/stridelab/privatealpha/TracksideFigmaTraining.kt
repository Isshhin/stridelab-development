package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved master gallery 614:500 and 795:839, 162 x 44 segmented switch. */
@Composable
internal fun TracksideTrainingViewSwitch(
    agenda: Boolean, onDay: () -> Unit, onAgenda: () -> Unit
) {
    Row(Modifier.width(162.dp).height(44.dp)
        .background(Color(0xFF14232D), RoundedCornerShape(12.dp))
        .padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple("Day", !agenda, onDay), Triple("Agenda", agenda, onAgenda)).forEach { (title, selected, action) ->
            Box(Modifier.weight(1f).fillMaxHeight()
                .background(if (selected) Color(0xFFF6F7F9) else Color.Transparent,
                    RoundedCornerShape(10.dp))
                .clickable(onClick = action)
                .testTag(if (title == "Day") "training-view-day" else "training-view-agenda"),
                contentAlignment = Alignment.Center) {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) Color.Black else Color(0xFFA2A4AF))
            }
        }
    }
}

/** The same 378px card layout is shared by Day and Agenda. Actual prescription data only. */
@Composable
internal fun TracksideFigmaWorkoutCard(
    session: PlannedSession, hasDraft: Boolean, onMove: (() -> Unit)?, onOpen: () -> Unit
) {
    val running = session.discipline == "run"
    val accent = tracksideDisciplineColor(session.discipline)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth()
        .heightIn(min = if (running) 155.dp else 232.dp)
        .testTag("workout-${session.id}"),
        shape = RoundedCornerShape(14.dp), color = TracksidePalette.surface,
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = .55f))) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(
            accent.copy(alpha = .10f), Color(0x00101B22))))
            .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(38.dp).background(accent.copy(alpha = .17f),
                    RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    if(running) Icon(painterResource(R.drawable.trackside_figma_run),
                        contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    else Text(disciplineLabel(session.discipline).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium, color = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(session.effectiveTitle, color = TracksidePalette.text,
                        style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(
                        session.distanceKm?.let { String.format("%.1f km", it) },
                        "${session.effectiveMinutes} min",
                        session.kind.replace('_', ' ')
                    ).joinToString(" · ")
                    Text(detail, color = TracksidePalette.textMuted,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                if(onMove != null) TextButton(onClick = onMove,
                    modifier = Modifier.testTag("move-${session.id}")) {
                    Text("Move", color = TracksidePalette.text, fontSize = 12.sp)
                }
            }
            if (running) {
                // Use saved steps only. Never fabricate warmup/cooldown or pace segments.
                val steps = session.steps.take(3)
                if(steps.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().height(25.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        steps.forEachIndexed { index, step ->
                            Box(Modifier.weight(if(index==1) 5f else 1f).fillMaxHeight()
                                .background(if(index == 1) accent else accent.copy(alpha=.52f),
                                    RoundedCornerShape(5.dp)))
                        }
                    }
                    Text(steps.joinToString(" · "), color = TracksidePalette.textMuted,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                } else Text("Open to see the saved running prescription",
                    color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                session.effectiveExercises.take(4).forEach { exercise ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(exercise.name, modifier = Modifier.weight(1f),
                            color = TracksidePalette.text, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${exercise.sets} × ${exercise.reps?.let { it.toString() } ?: "${exercise.seconds} s"}",
                            color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if(session.effectiveExercises.isEmpty()) Text("Session details available in workout",
                    color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            if(hasDraft) Text("Saved entry draft · continue without recording completion",
                color = accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Approved plan lifecycle 614:500: draft controls differ from active Manage entry. */
@Composable
internal fun TracksideDraftApprovalDock(
    plan: PlanSnapshot, enabled: Boolean, onApprove: () -> Unit
) {
    Surface(Modifier.fillMaxWidth().testTag("training-draft-approval"),
        color = Color(0xFF0B1922), shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = .5f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Draft plan", color = TracksidePalette.text,
                    style = MaterialTheme.typography.titleMedium)
                Text("${plan.sessions.size} planned sessions · not active yet",
                    color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onApprove, enabled = enabled,
                modifier = Modifier.height(44.dp).testTag("training-approve-plan"),
                colors = ButtonDefaults.buttonColors(containerColor = TracksidePalette.red),
                shape = RoundedCornerShape(12.dp)) { Text("Approve plan") }
        }
    }
}
