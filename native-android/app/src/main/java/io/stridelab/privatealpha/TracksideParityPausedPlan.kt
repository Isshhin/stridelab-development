package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    Screen("Training", "PLAN STATUS", compactTitle = true, dense = true) {
        item {
            TextButton(onClick = onBack) { Text("‹ Back to Training") }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF11232F),
                border = BorderStroke(1.dp, Color(0x88677987))
            ) {
                Column(
                    Modifier.background(Brush.verticalGradient(listOf(
                        Color(0xFF172C39), Color(0xFF08131C)
                    ))).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Text("PAUSED", fontSize = 11.sp, color = TracksidePalette.textMuted,
                        fontWeight = FontWeight.Bold)
                    Text("TRAINING PLAN PAUSED", fontSize = 12.sp,
                        color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
                    Text("Your plan is on hold",
                        style = MaterialTheme.typography.headlineMedium, color = TracksidePalette.text)
                    Text("WHEN YOU RESUME", fontSize = 12.sp,
                        color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
                    Text("Review upcoming workouts",
                        style = MaterialTheme.typography.titleLarge, color = TracksidePalette.text)
                    Text("Keep, shift or regenerate future sessions",
                        color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("Training history is preserved", color = TracksidePalette.text,
                        style = MaterialTheme.typography.titleMedium)
                    Text("Completed sessions remain unchanged",
                        color = TracksidePalette.textMuted, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { actions.approve(plan.id, plan.revision) },
                        enabled = !busy && Engine1Engines.canManage(plan),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manage-resume"),
                        colors = ButtonDefaults.buttonColors(containerColor = TracksidePalette.red),
                        shape = RoundedCornerShape(13.dp)
                    ) { Text("Resume plan", color = Color.White) }
                }
            }
        }
    }
}
