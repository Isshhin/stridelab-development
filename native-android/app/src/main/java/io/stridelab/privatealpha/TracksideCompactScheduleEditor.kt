package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/**
 * Lightweight pre-generation capacity check (never restricts multi-discipline double days).
 */
internal fun compactScheduleError(request: PlanRequest): String? {
    val chosen = request.days.size
    val total = request.runDays + request.strengthDays + request.calDays + request.conditioningDays
    return when {
        WeeklyPlacement.validationError(request) != null ->
            WeeklyPlacement.validationError(request)
        chosen == 0 -> "Select at least one available day."
        total == 0 -> "Select at least one weekly workout."
        else -> null
    }
}

/**
 * Art-directed inline weekly availability & adaptive schedule card.
 * Completely removes the obsolete "Compatible same-day sessions" toggle and research preview clutter.
 */
@Composable
internal fun TracksideCompactScheduleEditor(
    request: PlanRequest,
    onDay: (Int) -> Unit,
    onRemovePinnedDay: (Int) -> Unit,
    onMultiSessionToggle: (Boolean) -> Unit = {},
    selectedOtherDiscipline: String = "str",
    selectedOtherSlot: Int = 0,
    onSelectOtherDiscipline: (String) -> Unit = {},
    onSelectOtherSlot: (Int) -> Unit = {},
    onAssignOtherDay: (String, Int, Int) -> Unit = { _, _, _ -> },
    onClearOtherSlot: (String, Int) -> Unit = { _, _ -> },
    onAutoOther: (String) -> Unit = {},
    onCountOther: (String, Int) -> Unit = { _, _ -> },
    researchContent: (@Composable () -> Unit)? = null
) {
    var crossTrainingExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by rememberSaveable { mutableIntStateOf(0) }
    val error = compactScheduleError(request)
    val total = request.runDays + request.strengthDays + request.calDays + request.conditioningDays
    val hasCrossTraining = request.strengthDays + request.calDays + request.conditioningDays > 0
    val stackedDays = (total - request.days.size).coerceAtLeast(0)
    val assignments = request.weeklySlots.values.flatten()
    val byDay = assignments.filter { it in 1..7 }.groupingBy { it }.eachCount()
    val runSlots = WeeklyPlacement.days(request, "run")

    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("builder-inline-schedule"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WEEKLY AVAILABILITY",
                    color = PlanBuilderPalette.muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp
                )
                Text(
                    "Tap any day to toggle",
                    color = PlanBuilderPalette.secondary,
                    fontSize = 10.5.sp
                )
            }

            // Equal-width 7-day semantic tile strip (fits 360dp cleanly with zero clipping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                (1..7).forEach { day ->
                    val available = day in request.days
                    val slot = runSlots.indexOf(day)
                    val isLong = available && slot == 0 && request.runDays > 0
                    val isQuality = available && slot > 0 && slot <= request.qualityCount
                    val isEasy = available && slot > 0 && !isQuality
                    val otherCount = byDay[day] ?: 0
                    val code = when {
                        !available -> "–"
                        isLong -> "L"
                        isQuality -> "Q"
                        isEasy -> "E"
                        otherCount > 0 -> otherCount.toString()
                        else -> "·"
                    }
                    val tileAccent = when {
                        isLong -> PlanBuilderPalette.accent
                        isQuality -> StrideLabVisualPalette.moderateAmber
                        isEasy -> StrideLabVisualPalette.easyTeal
                        available && otherCount > 0 -> StrideLabVisualPalette.strengthCoral
                        available -> PlanBuilderPalette.secondary
                        else -> PlanBuilderPalette.muted
                    }
                    val tileBg = when {
                        isLong -> PlanBuilderPalette.accent.copy(alpha = 0.14f)
                        isQuality -> StrideLabVisualPalette.moderateAmber.copy(alpha = 0.14f)
                        isEasy -> StrideLabVisualPalette.easyTeal.copy(alpha = 0.12f)
                        available && otherCount > 0 -> StrideLabVisualPalette.strengthCoral.copy(alpha = 0.13f)
                        available -> PlanBuilderPalette.surfaceRaised
                        else -> PlanBuilderPalette.input
                    }
                    val hasAssignedRun = isLong || isQuality || isEasy || (available && otherCount > 0)

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            DayOfWeek.of(day).name.take(3),
                            color = if (available) PlanBuilderPalette.secondary else PlanBuilderPalette.muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            onClick = {
                                if (available && day in assignments) pendingRemoval = day
                                else onDay(day)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("available-day-$day")
                                .semantics {
                                    stateDescription = if (available) "Available, $code" else "Unavailable"
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = tileBg,
                            border = BorderStroke(
                                1.dp,
                                if (hasAssignedRun) tileAccent.copy(alpha = 0.65f)
                                else if (available) PlanBuilderPalette.borderStrong
                                else PlanBuilderPalette.border
                            )
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (hasAssignedRun) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth(0.55f)
                                            .height(2.5.dp)
                                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                                            .background(tileAccent)
                                    )
                                }
                                Text(
                                    code,
                                    color = tileAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }

            // Semantic dot legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScheduleLegendItem(StrideLabVisualPalette.easyTeal, "Easy")
                ScheduleLegendItem(StrideLabVisualPalette.moderateAmber, "Quality")
                ScheduleLegendItem(PlanBuilderPalette.accent, "Long")
                ScheduleLegendItem(PlanBuilderPalette.muted, "Rest")
            }

            if (error != null) {
                Text(
                    error,
                    color = StrideLabVisualPalette.highCoral,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Optional cross-training day slots (only shown when multi-discipline is selected)
            if (hasCrossTraining) {
                Surface(
                    onClick = { crossTrainingExpanded = !crossTrainingExpanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("builder-advanced-scheduling"),
                    shape = RoundedCornerShape(12.dp),
                    color = PlanBuilderPalette.surfaceRaised,
                    border = BorderStroke(1.dp, PlanBuilderPalette.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (crossTrainingExpanded) "Hide cross-training day placement"
                            else "Customize cross-training days",
                            color = PlanBuilderPalette.accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            if (crossTrainingExpanded) "−" else "›",
                            color = PlanBuilderPalette.accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (crossTrainingExpanded) {
                    TracksideOtherDisciplineSlots(
                        request,
                        selectedOtherDiscipline,
                        selectedOtherSlot,
                        onSelectOtherDiscipline,
                        onSelectOtherSlot,
                        onAssignOtherDay,
                        onClearOtherSlot,
                        onAutoOther,
                        onCountOther
                    )
                }
            }
            StrideLabHairline()
    }

    if (pendingRemoval in 1..7) {
        val day = pendingRemoval
        AlertDialog(
            onDismissRequest = { pendingRemoval = 0 },
            containerColor = PlanBuilderPalette.surface,
            title = {
                Text(
                    "Change ${DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() }}?",
                    color = PlanBuilderPalette.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Placed sessions on this day will be cleared so the coach can place them on your remaining days.",
                    color = PlanBuilderPalette.secondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRemovePinnedDay(day); pendingRemoval = 0 },
                    modifier = Modifier.testTag("builder-confirm-remove-pinned-day")
                ) { Text("Make unavailable", color = PlanBuilderPalette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = 0 }) {
                    Text("Cancel", color = PlanBuilderPalette.secondary)
                }
            }
        )
    }
}

@Composable
private fun ScheduleLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            color = PlanBuilderPalette.secondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
