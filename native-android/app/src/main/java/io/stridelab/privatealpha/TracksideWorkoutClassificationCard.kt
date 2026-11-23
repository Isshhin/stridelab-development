package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val activityLime = Color(0xFFD8FB70)
private val activityText = Color(0xFFF3F6F3)
private val activityMuted = Color(0xFFA6B4AD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityWorkoutClassificationCard(
    assessment: ActivityWorkoutAssessment,
    userLabel: UserActivityWorkoutLabel?,
    canSave: Boolean,
    saveFailed: Boolean,
    onSelect: (UserActivityWorkoutLabel) -> Unit,
    onClear: () -> Unit
) {
    var correctionOpen by rememberSaveable { mutableStateOf(false) }
    var evidenceOpen by rememberSaveable { mutableStateOf(false) }
    val detectedLabel = assessment.detected.qualitySubtype?.label ?: assessment.detected.kind.label
    val label = userLabel?.let { "Your label: ${it.label}" }
        ?: if (assessment.detected.kind == ActivityWorkoutKind.UNKNOWN) "Workout type unclear" else detectedLabel
    Surface(onClick = { correctionOpen = true }, color = Color(0xFF14181C),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("activity-workout-label-edit")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.trackside_discipline_running),
                contentDescription = null, tint = activityLime, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(if (userLabel == null) "DETECTED WORKOUT" else "YOUR CORRECTION",
                    color = activityMuted, style = MaterialTheme.typography.labelSmall)
                Text(label, color = activityText, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(if (userLabel == null) "activity-workout-detected"
                        else "activity-workout-user-label"))
            }
            Text("›", color = activityMuted, fontSize = 22.sp)
        }
    }
    if (saveFailed) Text("Couldn’t save your label.", color = Color(0xFFFF8C87),
        modifier = Modifier.testTag("activity-workout-label-error"))
    if (correctionOpen) {
        ModalBottomSheet(onDismissRequest = { correctionOpen = false },
            containerColor = Color(0xFF151917)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Correct workout type", color = activityText, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { evidenceOpen = !evidenceOpen }) {
                    Text(if (evidenceOpen) "Hide classification details" else "Why this classification?", color = activityLime)
                }
                if (evidenceOpen) {
                    if (userLabel != null) Text("Detected: $detectedLabel", color = activityText,
                        modifier = Modifier.testTag("activity-workout-detected"))
                    assessment.detected.evidence.forEach { Text(it, color = activityMuted) }
                }
                if (!canSave) return@Column
                fun select(label: UserActivityWorkoutLabel) {
                    correctionOpen = false
                    onSelect(label)
                }
                Text("ENDURANCE", color = activityLime, modifier = Modifier.padding(top = 16.dp))
                listOf(ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.RECOVERY_RUN,
                    ActivityWorkoutKind.LONG_RUN).forEach { kind ->
                    WorkoutCorrectionOption(kind.label, userLabel?.kind == kind) {
                        select(UserActivityWorkoutLabel(kind))
                    }
                }
                Text("QUALITY", color = activityLime, modifier = Modifier.padding(top = 16.dp))
                WorkoutCorrectionOption("Quality Run", userLabel?.kind == ActivityWorkoutKind.QUALITY_RUN &&
                    userLabel.qualitySubtype == null) {
                    select(UserActivityWorkoutLabel(ActivityWorkoutKind.QUALITY_RUN))
                }
                ActivityQualitySubtype.entries.forEach { subtype ->
                    WorkoutCorrectionOption(subtype.label, userLabel?.qualitySubtype == subtype) {
                        select(UserActivityWorkoutLabel(ActivityWorkoutKind.QUALITY_RUN, subtype))
                    }
                }
                WorkoutCorrectionOption("Mixed Session", userLabel?.kind == ActivityWorkoutKind.MIXED_SESSION) {
                    select(UserActivityWorkoutLabel(ActivityWorkoutKind.MIXED_SESSION))
                }
                if (userLabel != null) TextButton(onClick = {
                    correctionOpen = false
                    onClear()
                }, modifier = Modifier.testTag("activity-workout-label-clear")) { Text("Clear my label") }
            }
        }
    }
}

@Composable
private fun WorkoutCorrectionOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF1F2A16) else Color(0xFF101312),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) activityLime else Color(0xFF232B26)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                label,
                color = if (selected) activityLime else activityText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
