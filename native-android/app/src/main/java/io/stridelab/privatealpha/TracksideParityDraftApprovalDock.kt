package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma master 614:500, draft decision dock 412×64.
 * This is a user-initiated action; generated plans never auto-activate.
 * The screenshot is positioned at viewport bottom; this composable is kept
 * in the scroll flow on the no-build branch pending final scaffold integration.
 */
@Composable
internal fun TracksideParityDraftApprovalDock(
    plan: PlanSnapshot,
    enabled: Boolean,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .testTag("training-draft-approval-dock"),
        color = StrideLabVisualPalette.surface,
        border = BorderStroke(1.dp, StrideLabVisualPalette.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Paused plan",
                    color = StrideLabVisualPalette.ink,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(plan.sessions.size.toString() + " sessions · " +
                    "currently paused",
                    color = StrideLabVisualPalette.secondary, fontSize = 10.5.sp)
            }
            Button(
                enabled = enabled,
                onClick = onApprove,
                modifier = Modifier.height(44.dp).widthIn(min = 130.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StrideLabVisualPalette.lime,
                    contentColor = Color(0xFF090B09),
                    disabledContainerColor = StrideLabVisualPalette.surfaceRaised,
                    disabledContentColor = StrideLabVisualPalette.muted
                )
            ) {
                Text("Resume plan",
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
