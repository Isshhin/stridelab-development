package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Art-directed Train Your Way / Customize Running screen.
 * Pure AMOLED obsidian surfaces, gradient volume slider, semantic workout type pills,
 * and a quality frequency selector bounded by the running week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TracksideAdvancedRunning(
    guided: Boolean,
    weeks: Int,
    userDirectedPlanning: Boolean,
    userDefinedQualityWorkouts: Map<String, RunningPrescription>,
    weeklyKm: Double,
    qualityCount: Int,
    maxQualityCount: Int,
    selectedQualityTypes: Set<String>,
    onWeeklyKm: (Double) -> Unit,
    onQualityCount: (Int) -> Unit,
    onToggleQuality: (String) -> Unit,
    onSaveQualityWorkouts: (Map<String, RunningPrescription>) -> Unit,
    onRemoveQualityWorkout: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val lime = PlanBuilderPalette.accent
    TracksideBuilderFrame(
        progressStep = if (guided) 2 else 4,
        progressLabel = if (guided) null else "Targets",
        toolbarTitle = if (guided) "Customize" else "Training targets",
        eyebrow = if (guided) "RUNNING / PREFERENCES" else "RUNNING / TRAIN YOUR WAY",
        headline = if (guided) "Customize your\ntraining" else "Training targets",
        description = if (guided) "Fine-tune your target volume and quality session styles."
        else "Set your target volume and choose the workout styles you love.",
        onBack = onBack,
        onContinue = onContinue,
        ctaLabel = if (guided) "Save preferences" else "Continue",
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        // Flat Target Weekly Volume section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("builder-advanced-mileage")
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TARGET WEEKLY VOLUME",
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = PlanBuilderPalette.muted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Progressive build",
                    fontSize = 10.5.sp,
                    color = PlanBuilderPalette.secondary
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.getDefault(), "%.0f", weeklyKm),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PlanBuilderPalette.text,
                    letterSpacing = (-1).sp,
                    style = TabularTextStyle
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "km / wk",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = lime,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .testTag("builder-target-mileage-slider"),
                value = weeklyKm.toFloat().coerceIn(0f, 120f),
                onValueChange = { onWeeklyKm(it.toInt().toDouble()) },
                valueRange = 0f..120f,
                thumb = { _ ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(lime)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(PlanBuilderPalette.accentOn)
                    )
                },
                track = { _ ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlanBuilderPalette.surfaceRaised)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((weeklyKm.toFloat() / 120f).coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(StrideLabVisualPalette.easyTeal, lime)
                                    )
                                )
                        )
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0", "30", "60", "90", "120").forEach {
                    Text("$it km", fontSize = 10.sp, color = PlanBuilderPalette.muted)
                }
            }
            Spacer(Modifier.height(12.dp))
            StrideLabHairline()
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "WORKOUT STYLES",
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = PlanBuilderPalette.muted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        val options = listOf(
            "Threshold" to StrideLabVisualPalette.moderateAmber,
            "Tempo" to StrideLabVisualPalette.moderateAmber,
            "Intervals" to StrideLabVisualPalette.highCoral,
            "Hills" to StrideLabVisualPalette.highCoral,
            "Fartlek" to StrideLabVisualPalette.lime,
            "Progression" to StrideLabVisualPalette.easyTeal
        )
        options.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (type, dotColor) ->
                    val chosen = type in selectedQualityTypes
                    Surface(
                        onClick = { onToggleQuality(type) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("builder-advanced-quality-$type"),
                        color = if (chosen) dotColor.copy(alpha = 0.14f) else PlanBuilderPalette.surface,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(
                            1.dp,
                            if (chosen) dotColor.copy(alpha = 0.75f) else PlanBuilderPalette.border
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (chosen) dotColor else PlanBuilderPalette.muted)
                            )
                            Text(
                                type,
                                color = if (chosen) PlanBuilderPalette.text else PlanBuilderPalette.secondary,
                                fontSize = 13.sp,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (userDirectedPlanning && qualityCount > 0) {
            Spacer(Modifier.height(14.dp))
            TracksideStructuredWorkoutAuthoring(
                weeks, qualityCount, userDefinedQualityWorkouts,
                onSaveQualityWorkouts, onRemoveQualityWorkout
            )
        }
    }
}
