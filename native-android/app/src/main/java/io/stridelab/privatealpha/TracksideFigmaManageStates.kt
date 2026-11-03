package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Approved gallery 736:554: no duplicate generic screen header. */
@Composable
internal fun TracksideManageHeader(plan: PlanSnapshot, selected: LocalDate, onBack: () -> Unit) {
    val first = plan.request.start
    val last = first.plusWeeks(plan.request.weeks.toLong()).minusDays(1)
    val monday = tracksideCalendarMonday(selected)
    val span = monday.format(DateTimeFormatter.ofPattern("d MMM")) +
        " – " + monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))
    val week = ChronoUnit.WEEKS.between(tracksideCalendarMonday(first), monday).toInt() + 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.width(32.dp).testTag("manage-back"),
                contentPadding = PaddingValues(0.dp)) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = TracksidePalette.text)
            }
            Text("Manage plan", modifier = Modifier.weight(1f),
                fontSize = 29.sp, fontWeight = FontWeight.Bold, color = TracksidePalette.text)
            Surface(color = Color(0xFF172A35), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF7593A5))) {
                Text(plan.status.uppercase(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TracksidePalette.text)
            }
        }
        Text("CURRENT TRAINING PLAN", style = MaterialTheme.typography.labelSmall,
            color = TracksidePalette.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(span, style = MaterialTheme.typography.titleMedium, color = TracksidePalette.text)
            Text("Week $week of ${plan.request.weeks}", style = MaterialTheme.typography.labelSmall,
                color = TracksidePalette.textMuted)
        }
    }
}

@Composable
internal fun TracksidePausedPlanState(onResume: () -> Unit, enabled: Boolean) {
    Surface(Modifier.fillMaxWidth().testTag("manage-paused-state"),
        color = TracksidePalette.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("TRAINING PLAN PAUSED", style = MaterialTheme.typography.labelSmall,
                color = TracksidePalette.textMuted)
            Text("Your plan is on hold", style = MaterialTheme.typography.headlineSmall,
                color = TracksidePalette.text, fontWeight = FontWeight.Bold)
            Text("WHEN YOU RESUME", style = MaterialTheme.typography.labelSmall,
                color = TracksidePalette.textMuted)
            Text("Review upcoming workouts", color = TracksidePalette.text,
                style = MaterialTheme.typography.titleMedium)
            Text("Keep, shift or regenerate future sessions", color = TracksidePalette.textMuted)
            Text("Training history is preserved", color = TracksidePalette.text)
            Text("Completed sessions remain unchanged", color = TracksidePalette.textMuted)
            Button(onClick = onResume, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TracksidePalette.red)) {
                Text("Resume plan")
            }
        }
    }
}

@Composable
internal fun TracksideDraftPlanState() {
    Surface(Modifier.fillMaxWidth().testTag("manage-draft-state"),
        color = TracksidePalette.surfaceVariant, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("REVIEW CHANGES", style = MaterialTheme.typography.labelSmall,
                color = TracksidePalette.textMuted)
            Text("Checks pending", color = TracksidePalette.text,
                style = MaterialTheme.typography.titleMedium)
            Text("Your existing approved training is unchanged until you approve this draft.",
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun TracksideUncompletedPlanState(session: PlannedSession) {
    Surface(Modifier.fillMaxWidth().testTag("manage-uncompleted-state"),
        color = TracksidePalette.surface, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TracksidePalette.outline.copy(alpha = .6f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("SCHEDULE REVIEW", color = TracksidePalette.textMuted,
                style = MaterialTheme.typography.labelSmall)
            Text("Uncompleted scheduled workout", color = TracksidePalette.text,
                style = MaterialTheme.typography.titleMedium)
            Text("${session.effectiveTitle} · ${session.date}",
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
            Text("No completion or missed-workout status is inferred from an empty record.",
                color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
