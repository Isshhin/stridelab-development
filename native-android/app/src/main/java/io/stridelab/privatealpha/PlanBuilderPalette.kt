package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Luxury AMOLED Black + StrideLab Lime visual token system for Plan Builder.
 * Presentation-only: zero engine mutation, zero fabricated metrics.
 */
internal object PlanBuilderPalette {
    val background = Color(0xFF000000)      // Pure AMOLED Black
    val surface = Color(0xFF101214)         // Obsidian Card
    val surfaceRaised = Color(0xFF16191E)   // Elevated Interactive Surface
    val input = Color(0xFF0D0F12)           // Recessed Input Surface
    val selected = Color(0xFF17200B)        // Lime-tinted Obsidian Selection
    val accent = Color(0xFFD8FB70)          // Signature StrideLab Lime
    val accentOn = Color(0xFF090B09)        // High-contrast Obsidian ink on Lime
    val border = Color(0xFF22262D)          // Whisper-thin Hairline Border
    val borderStrong = Color(0xFF343A44)    // Hover / Subtle Emphasis Border
    val text = Color(0xFFF7F7F5)            // Crisp Warm Bone White
    val secondary = Color(0xFFA0A7B2)       // Balanced Cool Secondary Ink
    val muted = Color(0xFF707885)           // Subtle Metadata Ink
    val disabled = Color(0xFF3C424C)
}

@Composable
internal fun PlanBuilderStageProgress(
    step: Int,
    total: Int = 6,
    onSelectStage: ((Int) -> Unit)? = null, stepLabel: String? = null
) {
    val safeStep = step.coerceIn(1, total)
    val stageLabels = listOf("Focus", "Goal", "Baseline", "Week", "Schedule", "Blueprint")
    Column(
        modifier = Modifier.fillMaxWidth().testTag("builder-stage-progress"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (stepLabel ?: stageLabels.getOrElse(safeStep - 1) { "Step $safeStep" }).uppercase(),
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = PlanBuilderPalette.accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$safeStep / $total",
                fontSize = 10.sp,
                color = PlanBuilderPalette.secondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(total) { index ->
                val stage = index + 1
                val filled = index < safeStep
                val current = stage == safeStep
                val clickable = onSelectStage != null && stage < safeStep
                val barColor by animateColorAsState(
                    targetValue = when {
                        current -> PlanBuilderPalette.accent
                        filled -> PlanBuilderPalette.accent.copy(alpha = 0.55f)
                        else -> PlanBuilderPalette.border
                    },
                    animationSpec = tween(220),
                    label = "stage-$stage"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(barColor)
                        .then(if (clickable) Modifier.clickable { onSelectStage?.invoke(stage) } else Modifier)
                )
            }
        }
    }
}
