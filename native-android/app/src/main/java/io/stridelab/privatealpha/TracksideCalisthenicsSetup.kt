package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

private val caliViolet = StrideLabVisualPalette.calisthenicsViolet

/**
 * Art-directed Calisthenics Week Setup screen.
 * Pure AMOLED obsidian cards, Calisthenics Violet discipline accent + StrideLab Lime primary CTA,
 * high-contrast active pills (obsidian ink on accent), zero muddy grays.
 */
@Composable
internal fun TracksideCalisthenicsSetup(
    request: PlanRequest,
    selectedSlot: Int,
    onSelectedSlot: (Int) -> Unit,
    onMode: (Boolean) -> Unit,
    onCount: (Int) -> Unit,
    onAssign: (Int, Int) -> Unit,
    onClear: (Int) -> Unit,
    onAbility: (String, Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val count = request.calDays.coerceAtLeast(1)
    val custom = "cal" in request.weeklySlots
    val days = WeeklyPlacement.days(request, "cal")
    val assigned = if (custom) days.count { it in 1..7 } else 0
    var focusedDay by rememberSaveable { mutableIntStateOf(3) }

    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle = "Calisthenics",
        eyebrow = "CALISTHENICS / SCHEDULE",
        headline = "Your calisthenics\nweek",
        description = "Set weekly volume, pin preferred days, and calibrate movement levels.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = !custom || WeeklyPlacement.full(request, "cal"),
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        // Mode segmented pill
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to "Choose my days", false to "Coach decides").forEach { (selectedMode, label) ->
                    val isSelected = selectedMode == custom
                    Surface(
                        onClick = { onMode(selectedMode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag(if (selectedMode) "builder-cali-customize" else "builder-cali-auto"),
                        color = if (isSelected) PlanBuilderPalette.accent else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                color = if (isSelected) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Weekly Sessions & Schedule Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("builder-cali-weekly"),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "WEEKLY SESSIONS",
                    color = PlanBuilderPalette.muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        count.toString(),
                        color = PlanBuilderPalette.text,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "sessions / week",
                        color = PlanBuilderPalette.secondary,
                        fontSize = 12.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    listOf(-1, 1).forEach { d ->
                        val enabled = count + d in 1..4
                        Surface(
                            onClick = { onCount(count + d) },
                            enabled = enabled,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(42.dp)
                                .testTag(if (d < 0) "builder-cali-minus" else "builder-cali-plus"),
                            color = PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PlanBuilderPalette.borderStrong)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (d < 0) "−" else "+",
                                    color = if (enabled) PlanBuilderPalette.text else PlanBuilderPalette.disabled,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(PlanBuilderPalette.border))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                DayOfWeek.of(day).name.take(3),
                                color = PlanBuilderPalette.muted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                val suggested = request.days.sorted().take(count)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        val index = if (custom) days.indexOf(day) else suggested.indexOf(day)
                        val selected = focusedDay == day
                        Surface(
                            onClick = {
                                focusedDay = day
                                if (custom) onAssign(selectedSlot.coerceIn(0, count - 1), day)
                                else {
                                    onMode(true)
                                    onAssign(0, day)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("builder-cali-day-$day"),
                            color = when {
                                index >= 0 && selected -> caliViolet
                                index >= 0 -> caliViolet.copy(alpha = 0.16f)
                                day !in request.days -> PlanBuilderPalette.input
                                else -> PlanBuilderPalette.surfaceRaised
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    selected -> caliViolet
                                    index >= 0 -> caliViolet.copy(alpha = 0.65f)
                                    else -> PlanBuilderPalette.border
                                }
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val marker = if (index >= 0) when (index) {
                                    0 -> "PU"
                                    1 -> "PL"
                                    2 -> "CR"
                                    else -> "SK"
                                } else if (day !in request.days) "–" else "·"
                                Text(
                                    marker,
                                    color = when {
                                        index >= 0 && selected -> PlanBuilderPalette.accentOn
                                        index >= 0 -> caliViolet
                                        else -> PlanBuilderPalette.muted
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (custom) "$assigned of $count placed" else "Coach places sessions",
                        color = PlanBuilderPalette.text,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (custom) "Tap any day to pin" else "Auto-balanced",
                        color = PlanBuilderPalette.secondary,
                        fontSize = 11.sp
                    )
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(PlanBuilderPalette.border))

                Text(
                    "${DayOfWeek.of(focusedDay).name} · FOCUS SLOT",
                    color = caliViolet,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Push", "Pull", "Core", "Skills").forEachIndexed { i, label ->
                        val chosen = selectedSlot == i
                        Surface(
                            onClick = { onSelectedSlot(i.coerceAtMost(count - 1)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("builder-cali-session-type-$label"),
                            color = if (chosen) caliViolet else PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, if (chosen) caliViolet else PlanBuilderPalette.border)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    color = if (chosen) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (chosen) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            if (!custom) onMode(true)
                            onAssign(selectedSlot.coerceIn(0, count - 1), focusedDay)
                        },
                        modifier = Modifier
                            .weight(2f)
                            .height(44.dp)
                            .testTag("builder-cali-pin"),
                        color = caliViolet.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, caliViolet.copy(alpha = 0.65f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Pin ${DayOfWeek.of(focusedDay).name.take(3)}",
                                color = caliViolet,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        onClick = { onClear(selectedSlot.coerceIn(0, count - 1)) },
                        enabled = custom,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("builder-cali-unassign"),
                        color = PlanBuilderPalette.surfaceRaised,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PlanBuilderPalette.border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Clear",
                                color = if (custom) StrideLabVisualPalette.highCoral else PlanBuilderPalette.disabled,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Starting Levels Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("builder-cali-starting-levels"),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "STARTING LEVELS",
                        color = PlanBuilderPalette.muted,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Tap to cycle", color = PlanBuilderPalette.secondary, fontSize = 10.5.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Push" to request.calLevel,
                        "Pull" to request.pullLevel,
                        "Core" to request.coreLevel
                    ).forEach { (key, value) ->
                        Surface(
                            onClick = { onAbility(key, (value + 1) % 3) },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("builder-cali-level-$key"),
                            color = PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PlanBuilderPalette.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(key, color = PlanBuilderPalette.secondary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    when (value) {
                                        0 -> "Beginner"
                                        1 -> "Interm."
                                        else -> "Advanced"
                                    },
                                    color = caliViolet,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
