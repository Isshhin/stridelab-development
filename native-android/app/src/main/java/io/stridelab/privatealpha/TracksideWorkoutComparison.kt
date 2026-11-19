package io.stridelab.privatealpha

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

private val lime = Color(0xFFD8FB70)
private val muted = Color(0xFFA6B4AD)
private val panel = Color(0xFF151917)

@Composable
internal fun ActivityWorkoutComparisonCard(
    activity: ActivityRecord,
    session: PlannedSession?,
    adherence: ActivityWorkoutAdherence
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val planned = session?.distanceKm?.takeIf { it.isFinite() && it > 0 }
    val recorded = activity.distanceM?.takeIf { it.isFinite() && it >= 0 }?.div(1000)
    val ratio = if (planned != null && recorded != null) (recorded / planned)
        .takeIf { it.isFinite() } else null
    Surface(Modifier.fillMaxWidth().testTag("activity-workout-adherence"),
        shape = RoundedCornerShape(20.dp), color = panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Planned vs completed", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ComparisonMetric("Planned distance", planned, Modifier.weight(1f))
                ComparisonMetric("Recorded distance", recorded, Modifier.weight(1f))
            }
            if (ratio != null) {
                LinearProgressIndicator(progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(), color = lime, trackColor = Color(0xFF364039))
                Text(String.format(Locale.getDefault(), "%.0f%% of prescribed distance recorded", ratio * 100),
                    color = muted)
            }
            HorizontalDivider(color = Color(0xFF364039))
            Text("Work / recovery segments", style = MaterialTheme.typography.titleSmall)
            Text(if (adherence.observedWorkLaps + adherence.observedRecoveryLaps == 0)
                "No matched work or recovery laps"
                else "${adherence.observedWorkLaps} work · ${adherence.observedRecoveryLaps} recovery laps recorded",
                color = muted)
            Text("Distance alone cannot verify prescribed pace or heart-rate targets.", color = muted)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide comparison" else "View comparison", color = lime)
            }
            if (expanded) adherence.evidence.forEach { Text(it, color = muted) }
        }
    }
}

@Composable
private fun ComparisonMetric(label: String, km: Double?, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = muted, style = MaterialTheme.typography.labelMedium)
        Text(km?.let { String.format(Locale.getDefault(), "%.1f km", it) } ?: "Unavailable",
            style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun ActivityRecordedWorkoutTimeline(recorded: ActivityDetail?) {
    if (recorded == null) return
    val steps = remember(recorded) {
        val byIndex = recorded.workoutSteps.associateBy { it.index }
        recorded.laps.mapNotNull { lap ->
            val step = lap.workoutStepIndex?.let(byIndex::get) ?: return@mapNotNull null
            val seconds = lap.seconds?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            step to seconds
        }
    }
    if (steps.isEmpty()) return
    var details by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.padding(top = 12.dp).fillMaxWidth().testTag("activity-recorded-workout"),
        shape = RoundedCornerShape(20.dp), color = panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout structure", style = MaterialTheme.typography.titleMedium)
            Text("Recorded laps linked to FIT steps", color = muted)
            steps.forEachIndexed { index, (step, seconds) ->
                val role = when (step.role) {
                    "WARM_UP" -> "Warm-up"
                    "WORK" -> "Work"
                    "RECOVERY" -> "Recovery"
                    "COOL_DOWN" -> "Cool-down"
                    else -> "Recorded lap"
                }
                val tint = when (step.role) {
                    "WORK" -> lime
                    "RECOVERY" -> Color(0xFF7CA79A)
                    else -> muted
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.width(12.dp).fillMaxHeight()) {
                        val y = 10.dp.toPx()
                        if (index < steps.lastIndex) drawLine(tint.copy(alpha = .45f),
                            androidx.compose.ui.geometry.Offset(center.x, y),
                            androidx.compose.ui.geometry.Offset(center.x, size.height + 8.dp.toPx()),
                            strokeWidth = 1.dp.toPx())
                        drawCircle(tint, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x, y))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(role, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(step.name?.takeIf { it.isNotBlank() },
                            ActivityRecord.duration(seconds)).joinToString(" · "), color = muted)
                        if (details) Text(listOfNotNull("FIT step ${step.index}", step.intensity,
                            step.durationType, step.targetType).joinToString(" · "), color = muted)
                    }
                }
            }
            TextButton(onClick = { details = !details }) {
                Text(if (details) "Hide FIT details" else "View FIT details", color = lime)
            }
        }
    }
}
