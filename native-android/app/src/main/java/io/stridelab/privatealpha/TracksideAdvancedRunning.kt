package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import java.util.Locale

/**
 * Approved frames 551:665 (Train Your Way) and 614:1374 (Guided Advanced).
 *
 * Only weeklyKm and qualityCount map to current PlanRequest. Existing Engine 1
 * has no field for a quality-workout whitelist: selected types are displayed
 * as user preferences and never claimed to constrain the generated plan.
 */
@Composable
internal fun TracksideAdvancedRunning(
    guided: Boolean,
    weeklyKm: Double,
    qualityCount: Int,
    maxQualityCount: Int,
    selectedQualityTypes: Set<String>,
    onWeeklyKm: (Double) -> Unit,
    onQualityCount: (Int) -> Unit,
    onToggleQuality: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val crimson = PlanBuilderPalette.accent
    val panel = Color(0xFF303030)
    val inactive = Color(0xFF242424)
    val border = PlanBuilderPalette.accent
    TracksideBuilderFrame(
        progressStep = 2,
        toolbarTitle = "Generate Training Plan",
        eyebrow = if (guided) "RUNNING / ADVANCED PREFERENCES"
                  else "RUNNING / TRAIN YOUR WAY",
        headline = if (guided) "Customize your\ntraining"
                   else "Make running\nyour own",
        description = if (guided) "Optional overrides for your guided plan."
                      else "Control target mileage and preferred workout types.",
        onBack = onBack,
        onContinue = onContinue,
        ctaLabel = if (guided) "Save preferences" else "Continue"
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(143.dp)
                .testTag("builder-advanced-mileage"),
            color = panel,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, border)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target weekly mileage", fontSize = 12.sp,
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("km / week", fontSize = 10.sp,
                        color = TracksidePalette.textMuted)
                }
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format(Locale.getDefault(), "%.0f", weeklyKm),
                        fontSize = 31.sp, fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("km", fontSize = 12.sp,
                        color = TracksidePalette.textMuted,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                Slider(
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    value = weeklyKm.toFloat().coerceIn(0f, 120f),
                    onValueChange = { onWeeklyKm(it.toInt().toDouble()) },
                    valueRange = 0f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = crimson, activeTrackColor = crimson,
                        inactiveTrackColor = PlanBuilderPalette.accent
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("0", "30", "60", "90", "120").forEach {
                        Text(it, fontSize = 9.sp,
                            color = TracksidePalette.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("ALLOWED QUALITY WORKOUTS", fontSize = 11.sp,
            color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val options = listOf(
            "Threshold", "Tempo", "Intervals", "Hills", "Fartlek", "Progression"
        )
        options.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                pair.forEach { type ->
                    val chosen = type in selectedQualityTypes
                    Surface(
                        onClick = { onToggleQuality(type) },
                        modifier = Modifier.weight(1f).height(46.dp)
                            .testTag("builder-advanced-quality-" + type),
                        color = if (chosen) Color(0xFF1D240B) else inactive,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp, if (chosen) PlanBuilderPalette.accent else border
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier.size(7.dp).background(
                                    if (chosen) PlanBuilderPalette.accent
                                    else PlanBuilderPalette.accent,
                                    RoundedCornerShape(5.dp)
                                )
                            )
                            Text(type, color = if (chosen) Color.White
                                else TracksidePalette.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(17.dp))
        Text("QUALITY FREQUENCY", fontSize = 11.sp,
            fontWeight = FontWeight.Bold, color = TracksidePalette.textMuted)
        Spacer(Modifier.height(13.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(1, 2).forEach { count ->
                val chosen = qualityCount == count
                Surface(
                    onClick = { onQualityCount(count) },
                    enabled = count <= maxQualityCount,
                    modifier = Modifier.weight(1f).height(45.dp)
                        .testTag("builder-advanced-quality-count-" + count),
                    color = if (chosen) Color(0xFF1D240B) else inactive,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp, if (chosen) PlanBuilderPalette.accent else border
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$count " + if (count == 1)
                                "session / week" else "sessions / week",
                            fontSize = 12.sp, color = Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        if(maxQualityCount < 2) {
            Text(
                "Two quality sessions require experienced running, at least " +
                    "four runs per week and 30 km of recent weekly volume.",
                fontSize=10.sp,lineHeight=14.sp,
                color=TracksidePalette.textMuted
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Target mileage informs the saved plan. Quality-type choices are " +
                "preferences until supported by the active coaching policy.",
            fontSize = 11.sp, lineHeight = 15.sp,
            color = TracksidePalette.textMuted
        )
    }
}
