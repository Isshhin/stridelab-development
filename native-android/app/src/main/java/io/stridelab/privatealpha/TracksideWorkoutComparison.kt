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
private val structureTitle = Color(0xFFF1F5F9)
private val structureLabel = Color(0xFFE2E8F0)
private val structureMuted = Color(0xFF94A3B8)

internal fun showsWorkoutStructure(
    assessment: ActivityWorkoutAssessment,
    mappedSteps: List<ActivityWorkoutStep>
): Boolean {
    val prescription = assessment.linkedSession?.runningPrescription
    val planned = assessment.intended?.kind == ActivityWorkoutKind.QUALITY_RUN ||
        prescription?.role == "QUALITY" ||
        prescription?.segments.orEmpty().any { it.purpose == "WORK" &&
            it.stepIntent in setOf(RunningStepIntent.RACE_PACE, RunningStepIntent.STRIDE) } ||
        prescription?.mixedSegments.orEmpty().any {
            it.purpose == "WORK" && it.stepIntent in setOf(RunningStepIntent.RACE_PACE, RunningStepIntent.STRIDE)
        }
    val namedWork = mappedSteps.any { step ->
        step.role == "WORK" && step.name?.contains(
            Regex("""\b(intervals?|repeats?|fartlek|tempos?|threshold|pyramids?|hills?|strides?|race[ -]pace)\b""",
                RegexOption.IGNORE_CASE)
        ) == true
    }
    val workRecovery = mappedSteps.windowed(3).any { steps ->
        steps.map { it.role } == listOf("WORK", "RECOVERY", "WORK")
    }
    return planned || namedWork || workRecovery
}

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
internal fun ActivityRecordedWorkoutTimeline(
    recorded: ActivityDetail?,
    assessment: ActivityWorkoutAssessment
) {
    val steps = remember(recorded) {
        val byIndex = recorded?.workoutSteps.orEmpty().associateBy { it.index }
        recorded?.laps.orEmpty().mapNotNull { lap ->
            val step = lap.workoutStepIndex?.let(byIndex::get) ?: return@mapNotNull null
            step to lap
        }
    }
    if (!showsWorkoutStructure(assessment, steps.map { it.first })) return
    val planned = assessment.linkedSession?.runningPrescription?.let { prescription ->
        prescription.mixedSegments ?: prescription.segments.map {
            RunningStepV2(it.purpose, it.effort, meters = it.meters, stepIntent = it.stepIntent,
                targetPaceMinSecPerKm = it.targetPaceMinSecPerKm,
                targetPaceMaxSecPerKm = it.targetPaceMaxSecPerKm)
        }
    }.orEmpty()
    val alignTargets = steps.isNotEmpty() && steps.map { it.first.role } == planned.map { it.purpose }
    if (steps.isEmpty() && planned.isEmpty()) return
    var details by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.padding(top = 12.dp).fillMaxWidth().testTag("activity-recorded-workout"),
        shape = RoundedCornerShape(20.dp), color = Color(0xFF14181C), contentColor = structureLabel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout structure", color = structureTitle, style = MaterialTheme.typography.titleMedium)
            Text(if (steps.isEmpty()) "Planned steps" else "Steps recorded by your watch", color = structureMuted)
            val count = if (steps.isEmpty()) planned.size else steps.size
            var workNumber = 0
            repeat(count) { index ->
                val (step, lap) = steps.getOrNull(index) ?: (null to null)
                val target = planned.getOrNull(index).takeIf { steps.isEmpty() || alignTargets }
                val purpose = step?.role ?: target?.purpose
                if (purpose == "WORK") workNumber++
                val role = when (purpose) {
                    "WARM_UP" -> "Warmup"
                    "WORK" -> when (target?.stepIntent) {
                        RunningStepIntent.RACE_PACE -> "Race pace"
                        RunningStepIntent.STRIDE -> "Stride $workNumber"
                        RunningStepIntent.HILL -> "Hill repeat $workNumber"
                        RunningStepIntent.TEMPO -> "Tempo"
                        RunningStepIntent.THRESHOLD -> "Threshold"
                        else -> "Interval $workNumber"
                    }
                    "RECOVERY" -> "Recovery"
                    "COOL_DOWN" -> "Cooldown"
                    "EASY" -> "Easy"
                    "TRANSITION" -> "Transition"
                    else -> "Step ${index + 1}"
                }
                val tint = when (purpose) {
                    "WORK" -> lime
                    "RECOVERY" -> Color(0xFF7CA79A)
                    else -> muted
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.width(12.dp).fillMaxHeight()) {
                        val y = 10.dp.toPx()
                        if (index < count - 1) drawLine(tint.copy(alpha = .45f),
                            androidx.compose.ui.geometry.Offset(center.x, y),
                            androidx.compose.ui.geometry.Offset(center.x, size.height + 8.dp.toPx()),
                            strokeWidth = 1.dp.toPx())
                        drawCircle(tint, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x, y))
                    }
                    Column(Modifier.weight(1f)) {
                        val name = step?.name?.takeIf { it.isNotBlank() } ?: role
                        val meters = target?.meters ?: lap?.distance?.takeIf { it.isFinite() && it > 0 }?.toInt()
                        val numericStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                        Text(listOfNotNull(name, meters?.let { "${it}m" }).joinToString(" · "),
                            color = structureLabel, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                        val observed = listOfNotNull(
                            lap?.seconds?.takeIf { it.isFinite() && it > 0 }?.let { ActivityRecord.duration(it) },
                            lap?.pace?.takeIf { it.isFinite() && it > 0 }?.let { "${ActivityRecord.duration(it)}/km" }
                        )
                        if (observed.isNotEmpty()) Text("Observed · " + observed.joinToString(" · "),
                            color = structureMuted, style = numericStyle)
                        if (target != null) {
                            val pace = listOfNotNull(target.targetPaceMinSecPerKm, target.targetPaceMaxSecPerKm)
                                .distinct().joinToString("–") { ActivityRecord.duration(it.toDouble()) }
                            val cues = listOfNotNull(target.meters?.let { "${it}m" }
                                ?: target.seconds?.let { ActivityRecord.duration(it.toDouble()) },
                                pace.takeIf { it.isNotEmpty() }?.let { "$it/km" })
                            Text("Target · " + cues.joinToString(" · "), color = structureMuted, style = numericStyle)
                        }
                        if (details && step != null) Text(listOfNotNull("FIT step ${step.index}", step.intensity,
                            step.durationType, step.targetType).joinToString(" · "), color = structureMuted)
                    }
                }
            }
            if (steps.isNotEmpty()) TextButton(onClick = { details = !details }) {
                Text(if (details) "Hide FIT details" else "View FIT details", color = lime)
            }
        }
    }
}
