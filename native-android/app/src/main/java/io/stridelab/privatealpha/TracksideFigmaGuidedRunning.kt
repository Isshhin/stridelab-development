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

/** Approved 614:1258 / 614:1298 / 614:1336 guided running goal setup.
 * Real athlete mileage and experience are used, not example Figma values.
 * Focus remains a visual preference if no matching PlanRequest goal exists.
 */
@Composable
internal fun TracksideFigmaGuidedRunning(
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
        isEndurance -> listOf("Run longer", "Build mileage", "Long run")
        else -> listOf("Consistency", "Improve fitness", "Maintain")
    }
    val outline = Color(0xFF3C5361)
    val bg = Color(0xFF172630)
    TracksideFigmaBuilderFrame(
        toolbarTitle = "Generate Training Plan",
        eyebrow = when {
            isSpeed -> "RUNNING / SPEED DEVELOPMENT"
            isEndurance -> "RUNNING / ENDURANCE"
            else -> "RUNNING / GENERAL FITNESS"
        },
        headline = when {
            isSpeed -> "Improve your\nrunning speed"
            isEndurance -> "Build your\nrunning base"
            else -> "Run for\nyour fitness"
        },
        description = when {
            isSpeed -> "Choose the distance or performance you want to improve."
            isEndurance -> "Develop endurance from your current training level."
            else -> "Build a sustainable running routine."
        },
        onBack = onBack, onContinue = onContinue
    ) {
        Text(
            when {
                isSpeed -> "PERFORMANCE FOCUS"
                isEndurance -> "ENDURANCE FOCUS"
                else -> "TRAINING FOCUS"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TracksidePalette.textMuted
        )
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEach { label ->
                val selected = label == selectedFocus
                Surface(
                    onClick = { onFocus(label) },
                    modifier = Modifier.weight(1f).height(44.dp)
                        .testTag("builder-focus-" + label.replace(' ', '-')),
                    shape = RoundedCornerShape(9.dp),
                    color = if(selected) Color(0xFF562735) else bg,
                    border = BorderStroke(
                        1.dp,
                        if(selected) Color(0xFFBC657B) else outline
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 11.sp,
                            fontWeight = if(selected) FontWeight.SemiBold
                                else FontWeight.Normal, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("CURRENT ABILITY", fontSize = 11.sp,
            color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TracksideFigmaEditableInfo(
            title = "Current weekly distance",
            value = String.format(Locale.getDefault(), "%.0f km/week", weeklyKm),
            tag = "builder-guided-mileage",
            onClick = onEditAbility
        )
        Spacer(Modifier.height(14.dp))
        TracksideFigmaEditableInfo(
            title = "Running experience",
            value = listOf("Beginner", "Intermediate", "Advanced")
                .getOrElse(experience.coerceIn(0,2)) { "Beginner" },
            tag = "builder-guided-experience",
            onClick = onEditAbility
        )
        Spacer(Modifier.height(26.dp))
        Text("TRAINING PREFERENCES", fontSize = 11.sp,
            color = TracksidePalette.textMuted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        TracksideFigmaEditableInfo(
            title = "Customize training",
            value = "Target mileage and allowed quality workouts",
            tag = "builder-guided-advanced",
            onClick = onAdvanced
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "The active coaching policy determines the workout prescriptions.",
            color = TracksidePalette.textMuted, fontSize = 10.sp
        )
    }
}

@Composable
private fun TracksideFigmaEditableInfo(
    title: String,
    value: String,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp).testTag(tag),
        color = Color(0xFF192A35),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Color(0xFF3C5665))
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 11.sp, color = TracksidePalette.textMuted)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White)
            }
            Text("›", fontSize = 22.sp, color = TracksidePalette.textMuted)
        }
    }
}
