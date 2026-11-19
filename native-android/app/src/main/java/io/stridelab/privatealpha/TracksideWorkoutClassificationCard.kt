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
import java.util.Locale

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
    Surface(color = Color(0xFF151917), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().testTag("activity-workout-label").padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val detectedLabel = assessment.detected.qualitySubtype?.label ?: assessment.detected.kind.label
            Text(if (userLabel == null) "DETECTED WORKOUT" else "YOUR CORRECTION",
                color = activityLime, style = MaterialTheme.typography.labelMedium)
            Text(userLabel?.let { "Your label: ${it.label}" } ?: "Detected: $detectedLabel",
                color = activityText, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag(if (userLabel == null) "activity-workout-detected"
                    else "activity-workout-user-label"))
            if (userLabel == null) {
                Text("Detection confidence: " + assessment.detected.confidence.name.lowercase(Locale.ROOT),
                    color = activityMuted, modifier = Modifier.testTag("activity-workout-confidence"))
            } else {
                Text("Saved on this device. The imported activity is unchanged.", color = activityMuted)
            }
            TextButton(onClick = { evidenceOpen = !evidenceOpen }) {
                Text(if (evidenceOpen) "Hide detection details" else
                    if (userLabel == null) "Why this classification?" else "View original detection",
                    color = activityLime)
            }
            if (evidenceOpen) {
                if (userLabel != null) Text("Detected: $detectedLabel", color = activityText,
                    modifier = Modifier.testTag("activity-workout-detected"))
                assessment.detected.evidence.forEach { Text(it, color = activityMuted) }
            }
            OutlinedButton(onClick = { correctionOpen = true }, enabled = canSave,
                modifier = Modifier.heightIn(min = 48.dp).testTag("activity-workout-label-edit")) {
                Text("Correct workout type", color = if (canSave) activityLime else activityMuted)
            }
            assessment.intended?.let { intended ->
                Spacer(Modifier.height(4.dp))
                Text("Planned intent: ${intended.qualitySubtype?.label ?: intended.kind.label}",
                    color = activityText, fontSize = 12.sp,
                    modifier = Modifier.testTag("activity-workout-intended"))
                Text(intended.evidence.first(), color = activityMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            assessment.intentWarning?.let {
                Text(it, color = activityMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            assessment.adherence?.let { adherence ->
                Spacer(Modifier.height(5.dp))
                Text("PLANNED VS COMPLETED · " + adherence.status.replace('_', ' '),
                    color = activityLime, fontSize = 10.sp,
                    modifier = Modifier.testTag("activity-workout-adherence"))
                adherence.evidence.forEach { line ->
                    Text(line, color = activityMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            if (!canSave) Text("A local activity owner is needed to save a label offline.",
                color = activityMuted, fontSize = 11.sp)
            if (saveFailed) Text("Couldn't save the label on this device. Your previous label is unchanged.",
                color = Color(0xFFFF8C87), fontSize = 11.sp, modifier = Modifier.testTag("activity-workout-label-error"))
        }
    }
    if (correctionOpen && canSave) {
        ModalBottomSheet(onDismissRequest = { correctionOpen = false },
            containerColor = Color(0xFF151917)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Correct workout type", style = MaterialTheme.typography.titleLarge)
                Text("Your selection is saved on this device.", color = activityMuted)
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
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(label, color = activityText)
    }
}
