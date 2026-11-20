package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/**
 * Art-directed Cross-Training Weekday Slot editor.
 * Pure AMOLED obsidian surfaces with discipline-specific semantic color coding
 * (Strength Coral, Calisthenics Violet, Conditioning Sky) and zero restriction warnings.
 */
@Composable
internal fun TracksideOtherDisciplineSlots(
    request: PlanRequest,
    selectedDiscipline: String,
    selectedSlot: Int,
    onSelectDiscipline: (String) -> Unit,
    onSelectSlot: (Int) -> Unit,
    onAssignDay: (String, Int, Int) -> Unit,
    onClearSlot: (String, Int) -> Unit,
    onAuto: (String) -> Unit,
    onCount: (String, Int) -> Unit
) {
    val active = listOf("str", "cal", "cond").filter {
        WeeklyPlacement.count(request, it) > 0
    }
    if (active.isEmpty()) return
    val discipline = selectedDiscipline.takeIf { it in active } ?: active.first()
    val discAccent = StrideLabVisualPalette.disciplineColor(discipline)
    val n = WeeklyPlacement.count(request, discipline)
    val slots = WeeklyPlacement.days(request, discipline)
    val manual = discipline in request.weeklySlots
    val current = selectedSlot.coerceIn(0, (n - 1).coerceAtLeast(0))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("builder-other-discipline-editor"),
        color = PlanBuilderPalette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PlanBuilderPalette.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "CROSS-TRAINING DAYS",
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = PlanBuilderPalette.muted,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                active.forEach { name ->
                    val isSelected = name == discipline
                    val accent = StrideLabVisualPalette.disciplineColor(name)
                    Surface(
                        onClick = {
                            onSelectDiscipline(name)
                            onSelectSlot(0)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("builder-discipline-day-tab-$name"),
                        color = if (isSelected) accent else PlanBuilderPalette.surfaceRaised,
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) accent else PlanBuilderPalette.border
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (name) {
                                    "str" -> "Strength"
                                    "cal" -> "Cali"
                                    else -> "Conditioning"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) PlanBuilderPalette.accentOn else PlanBuilderPalette.text
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SESSIONS / WEEK",
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = PlanBuilderPalette.muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    n.toString(),
                    color = PlanBuilderPalette.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(10.dp))
                val max = if (discipline == "cond") 3 else 4
                listOf(-1, 1).forEach { delta ->
                    Surface(
                        onClick = { onCount(discipline, n + delta) },
                        enabled = n + delta in 1..max,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(34.dp)
                            .testTag("builder-other-count-" + (if (delta < 0) "minus" else "plus")),
                        color = PlanBuilderPalette.surfaceRaised,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PlanBuilderPalette.borderStrong)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (delta < 0) "−" else "+",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (n + delta in 1..max) PlanBuilderPalette.text else PlanBuilderPalette.disabled
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                (0 until n).forEach { i ->
                    val activeSlot = i == current
                    Surface(
                        onClick = { onSelectSlot(i) },
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .heightIn(min = 48.dp)
                            .testTag("builder-other-session-$i"),
                        shape = RoundedCornerShape(11.dp),
                        color = if (activeSlot) discAccent.copy(alpha = 0.14f) else PlanBuilderPalette.surfaceRaised,
                        border = BorderStroke(
                            1.dp,
                            if (activeSlot) discAccent else PlanBuilderPalette.border
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                WeeklyPlacement.label(request, discipline, i),
                                fontSize = 11.5.sp,
                                color = PlanBuilderPalette.text,
                                fontWeight = FontWeight.Bold
                            )
                            val day = slots.getOrNull(i) ?: 0
                            Text(
                                if (!manual) "Auto"
                                else if (day == 0) "Unassigned"
                                else DayOfWeek.of(day).name.take(3),
                                fontSize = 10.sp,
                                color = if (activeSlot) discAccent else PlanBuilderPalette.secondary,
                                modifier = Modifier.testTag("builder-other-session-weekday-$i")
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                (1..7).forEach { day ->
                    val occupied = if (manual) slots.indexOf(day) else -1
                    val picked = occupied == current && manual
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            DayOfWeek.of(day).name.take(3),
                            fontSize = 9.5.sp,
                            color = PlanBuilderPalette.muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            onClick = { onAssignDay(discipline, current, day) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("builder-other-day-$day"),
                            color = when {
                                picked -> discAccent
                                occupied >= 0 -> discAccent.copy(alpha = 0.16f)
                                else -> PlanBuilderPalette.surfaceRaised
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    picked -> discAccent
                                    occupied >= 0 -> discAccent.copy(alpha = 0.55f)
                                    else -> PlanBuilderPalette.border
                                }
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (occupied >= 0) (occupied + 1).toString() else "+",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (picked) PlanBuilderPalette.accentOn
                                    else if (occupied >= 0) discAccent
                                    else PlanBuilderPalette.secondary
                                )
                            }
                        }
                    }
                }
            }

            Text(
                if (!manual) "Coach places these sessions automatically. Tap any weekday to pin."
                else "${slots.count { it in 1..7 }} of $n sessions pinned. Tap an occupied day to swap.",
                color = if (manual && slots.all { it in 1..7 }) discAccent else PlanBuilderPalette.secondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.testTag("builder-other-assignment-progress")
            )

            if (manual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = { onClearSlot(discipline, current) }) {
                        Text("Clear day", fontSize = 11.5.sp, color = StrideLabVisualPalette.highCoral)
                    }
                    TextButton(onClick = { onAuto(discipline) }) {
                        Text("Let coach decide", fontSize = 11.5.sp, color = PlanBuilderPalette.accent)
                    }
                }
            }
        }
    }
}
