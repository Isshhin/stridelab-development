package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Art-directed Guided Running Goal screen (Speed / Endurance / Fitness).
 * Pure AMOLED obsidian cards, crisp high-contrast active focus pills, hairline framing.
 */
@Composable
internal fun TracksideGuidedRunning(
    route: String,
    selectedFocus: String,
    onFocus: (String) -> Unit,
    weeklyKm: Double,
    experience: Int,
    onEditAbility: () -> Unit,
    onAdvanced: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val isSpeed = route == "faster"
    val isEndurance = route == "endurance"
    val labels = when {
        isSpeed -> listOf("5K", "10K", "21K", "Speed")
        isEndurance -> listOf("Run longer", "Build base", "Long run")
        else -> listOf("Consistency", "Fitness", "Maintain")
    }

    TracksideBuilderFrame(
        progressStep = 2,
        toolbarTitle = "Running focus",
        eyebrow = when {
            isSpeed -> "RUNNING / SPEED"
            isEndurance -> "RUNNING / ENDURANCE"
            else -> "RUNNING / FITNESS"
        },
        headline = when {
            isSpeed -> "Improve your\nrunning speed"
            isEndurance -> "Build your\nrunning base"
            else -> "Run for\nyour fitness"
        },
        description = when {
            isSpeed -> "Pick your primary speed focus to tune workout structure."
            isEndurance -> "Build sustainable aerobic endurance week by week."
            else -> "Build a consistent, enjoyable weekly running rhythm."
        },
        onBack = onBack,
        onContinue = onContinue,
        descriptionBottomGap = 18,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        Text(
            when {
                isSpeed -> "PERFORMANCE FOCUS"
                isEndurance -> "ENDURANCE FOCUS"
                else -> "TRAINING FOCUS"
            },
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = PlanBuilderPalette.muted
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEach { label ->
                val selected = label == selectedFocus
                Surface(
                    onClick = { onFocus(label) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("builder-focus-" + label.replace(' ', '-')),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) PlanBuilderPalette.accent else PlanBuilderPalette.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) PlanBuilderPalette.accent else PlanBuilderPalette.border
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) PlanBuilderPalette.accentOn else PlanBuilderPalette.text
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "CURRENT BASELINE",
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = PlanBuilderPalette.muted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        StrideLabHairline()
        TracksideEditableInfo(
            title = "Current weekly distance",
            value = String.format(Locale.getDefault(), "%.0f km / week", weeklyKm),
            tag = "builder-guided-mileage",
            onClick = onEditAbility
        )
        TracksideEditableInfo(
            title = "Running experience",
            value = listOf("Beginner", "Intermediate", "Advanced")
                .getOrElse(experience.coerceIn(0, 2)) { "Beginner" },
            tag = "builder-guided-experience",
            onClick = onEditAbility
        )

        Spacer(Modifier.height(22.dp))
        Text(
            "CUSTOMIZATION",
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            color = PlanBuilderPalette.muted,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        StrideLabHairline()
        TracksideEditableInfo(
            title = "Workout styles & target volume",
            value = "Customize allowed workouts and weekly km",
            tag = "builder-guided-advanced",
            onClick = onAdvanced
        )
    }
}

@Composable
private fun TracksideEditableInfo(
    title: String,
    value: String,
    tag: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .testTag(tag),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(title, fontSize = 11.sp, color = PlanBuilderPalette.muted, fontWeight = FontWeight.Medium)
                    Text(
                        value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PlanBuilderPalette.text
                    )
                }
                Text("›", fontSize = 20.sp, color = PlanBuilderPalette.secondary)
            }
        }
        StrideLabHairline()
    }
}
