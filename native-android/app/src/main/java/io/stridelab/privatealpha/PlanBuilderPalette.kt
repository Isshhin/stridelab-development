package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Plan Builder visual tokens. Colors are sampled from the user-approved
 * 2026-11-07 StrideLab plan-generation reference screenshots.
 *
 * These tokens are for presentation only. No scheduling, engine, plan
 * request, or persisted activity behavior depends on them.
 */
internal object PlanBuilderPalette {
    val background = Color(0xFF0B0B0B)
    val surface = Color(0xFF151515)
    val surfaceRaised = Color(0xFF171717)
    val input = Color(0xFF0F0F0F)
    val selected = Color(0xFF1D240B)
    val accent = TracksidePalette.lime
    val accentOn = Color(0xFF0B0B0B)
    val border = Color(0xFF2B2B2B)
    val borderStrong = Color(0xFF525252)
    val text = Color(0xFFFFFFFF)
    val secondary = Color(0xFFC5C5C5)
    val muted = Color(0xFFAAAAAA)
    val disabled = Color(0xFF5F5F5F)
}

@Composable
internal fun PlanBuilderStageProgress(step: Int, total: Int = 6) {
    val safeStep = step.coerceIn(1, total)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("builder-stage-progress"),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(total) { index ->
            Spacer(
                Modifier.weight(1f).height(3.dp)
                    .background(
                        if (index < safeStep) PlanBuilderPalette.accent
                        else PlanBuilderPalette.border,
                        RoundedCornerShape(10.dp)
                    )
            )
        }
        Text(
            "$safeStep/$total",
            fontSize = 10.sp,
            color = PlanBuilderPalette.secondary,
            fontWeight = FontWeight.Medium
        )
    }
}
