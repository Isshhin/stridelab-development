package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma approved master gallery 736:785. No archived data is deleted and
 * resumption uses the existing version/revision-gated PlanActions callback.
 */
@Composable
internal fun TracksideParityPausedPlan(
    plan: PlanSnapshot,
    busy: Boolean,
    actions: PlanActions,
    onBack: () -> Unit
) {
    var confirmDelete by rememberSaveable(plan.id) { mutableStateOf(false) }
    Screen("Training", "PLAN STATUS", compactTitle = true, dense = true) {
        item {
            TextButton(onClick = onBack) { Text("‹ Back to Training") }
        }
        if (plan.engineVersion == ENGINE15_VERSION && Engine1Engines.canManage(plan)) {
            item { Engine15ScheduleReview(plan, actions, busy) }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = StrideLabVisualPalette.surface,
                border = BorderStroke(1.dp, StrideLabVisualPalette.border)
            ) {
                Column(
                    Modifier.background(Brush.verticalGradient(listOf(
                        StrideLabVisualPalette.moderateAmber.copy(alpha = 0.08f),
                        StrideLabVisualPalette.surface
                    ))).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Text("PAUSED", fontSize = 11.sp, color = StrideLabVisualPalette.moderateAmber,
                        letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    Text("TRAINING PLAN PAUSED", fontSize = 12.sp,
                        color = StrideLabVisualPalette.muted, fontWeight = FontWeight.Bold)
                    Text("Your plan is on hold",
                        style = MaterialTheme.typography.headlineMedium, color = StrideLabVisualPalette.ink)
                    Text("WHEN YOU RESUME", fontSize = 11.sp, letterSpacing = 1.1.sp,
                        color = StrideLabVisualPalette.muted, fontWeight = FontWeight.Bold)
                    Text("Review upcoming workouts",
                        style = MaterialTheme.typography.titleLarge, color = StrideLabVisualPalette.ink)
                    Text("Keep, shift or regenerate future sessions",
                        color = StrideLabVisualPalette.secondary, style = MaterialTheme.typography.bodyMedium)
                    Text("Training history is preserved", color = StrideLabVisualPalette.ink,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Completed sessions remain unchanged",
                        color = StrideLabVisualPalette.secondary, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { actions.approve(plan.id, plan.revision) },
                        enabled = !busy && Engine1Engines.canManage(plan) && plan.sessions.none { it.status == "unassigned" },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manage-resume"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrideLabVisualPalette.lime,
                            contentColor = Color(0xFF090B09)
                        ),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Resume plan", fontWeight = FontWeight.ExtraBold) }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("manage-delete")
                    ) {
                        Text("Quit & delete plan", color = StrideLabVisualPalette.highCoral)
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = StrideLabVisualPalette.surface,
        title = { Text("Quit and delete this plan?", color = StrideLabVisualPalette.ink) },
        text = { Text("This permanently removes the plan and its scheduled workouts from this device. Your recorded runs and activity history are not affected.", color = StrideLabVisualPalette.secondary) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete(plan.id, plan.revision); onBack() }, enabled = !busy) { Text("Delete plan", color = StrideLabVisualPalette.highCoral) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = StrideLabVisualPalette.secondary) } }
    )
}
