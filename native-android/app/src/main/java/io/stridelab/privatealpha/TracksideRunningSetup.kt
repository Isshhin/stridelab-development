package io.stridelab.privatealpha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/**
 * Art-directed Running Week Setup:
 * - Clean 7-day semantic color-coded calendar (Lime = Long, Amber = Quality, Mint = Easy).
 * - Direct tap-to-assign day sheet.
 * - Clean "Choose my days" / "Coach decides" segmented toggle right below the week card.
 * - Zero cluttered "Advanced weekly setup" expansion.
 */
@Composable
internal fun TracksideRunningSetup(
    request: PlanRequest,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    custom: Boolean,
    onCustom: (Boolean) -> Unit,
    qualityPreference: String,
    onQualityPreference: (String) -> Unit,
    onQualityCount: (Int) -> Unit,
    onRunningCount: (Int) -> Unit,
    onAssignDay: (Int) -> Unit,
    onAssignSlot: (Int, Int) -> Unit,
    onClearDay: (Int) -> Unit,
    onClearSlot: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    progressStep: Int = 4
) {
    val count = request.runDays.coerceIn(1, 6)
    val slots = WeeklyPlacement.days(request, "run")
    val assigned = slots.count { it in 1..7 }
    val complete = assigned == count && slots.distinct().size == count &&
        slots.all { it in request.days }
    val pinned = request.weeklySlots["run"]
    var editingDay by remember { mutableStateOf<Int?>(null) }

    TracksideBuilderFrame(
        progressStep = progressStep,
        progressLabel = "Week",
        toolbarTitle = "Running Setup",
        eyebrow = "RUNNING / WEEKLY RHYTHM",
        headline = "Your running week",
        description = "Set your weekly run count and tap any day to customize.",
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = !custom || complete
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("builder-running-week-card"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "WEEKLY RUNS",
                        color = PlanBuilderPalette.muted,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$count runs / week",
                        color = PlanBuilderPalette.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { onRunningCount((count - 1).coerceAtLeast(1)) },
                        enabled = count > 1,
                        modifier = Modifier.size(42.dp).testTag("builder-run-minus"),
                        color = PlanBuilderPalette.surfaceRaised,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, PlanBuilderPalette.border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "−",
                                color = if (count > 1) PlanBuilderPalette.text else PlanBuilderPalette.disabled,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        count.toString(),
                        color = PlanBuilderPalette.accent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.widthIn(min = 24.dp),
                        style = TabularTextStyle
                    )
                    Surface(
                        onClick = { onRunningCount((count + 1).coerceAtMost(6)) },
                        enabled = count < 6,
                        modifier = Modifier.size(42.dp).testTag("builder-run-plus"),
                        color = PlanBuilderPalette.surfaceRaised,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, PlanBuilderPalette.border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "+",
                                color = if (count < 6) PlanBuilderPalette.text else PlanBuilderPalette.disabled,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Semantic color-coded 7-day strip (responsive without horizontal clipping)
            Row(
                Modifier.fillMaxWidth().testTag("builder-running-day-row"),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                (1..7).forEach { day ->
                    val slot = slots.indexOf(day)
                    val isPinned = pinned?.contains(day) == true
                    val available = day in request.days
                    val code = when {
                        slot == 0 -> "L"
                        slot > 0 && slot <= request.qualityCount -> "Q"
                        slot > 0 -> "E"
                        !available -> "−"
                        else -> ""
                    }
                    val accentColor = when (code) {
                        "L" -> StrideLabVisualPalette.lime
                        "Q" -> StrideLabVisualPalette.moderateAmber
                        "E" -> StrideLabVisualPalette.easyTeal
                        else -> PlanBuilderPalette.muted
                    }
                    val tileBg = when (code) {
                        "L" -> StrideLabVisualPalette.lime.copy(alpha = 0.14f)
                        "Q" -> StrideLabVisualPalette.moderateAmber.copy(alpha = 0.14f)
                        "E" -> StrideLabVisualPalette.easyTeal.copy(alpha = 0.11f)
                        else -> PlanBuilderPalette.surfaceRaised.copy(alpha = 0.45f)
                    }
                    val tileBorder = when (code) {
                        "L", "Q", "E" -> accentColor.copy(alpha = 0.60f)
                        else -> Color.Transparent
                    }
                    val dayLabel = DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() }
                    val sessionLabel = when (code) {
                        "L" -> "Long run"
                        "Q" -> "Quality run"
                        "E" -> "Easy run"
                        "−" -> "Unavailable"
                        else -> "Rest day"
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            DayOfWeek.of(day).name.take(3),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (code in setOf("L", "Q", "E")) PlanBuilderPalette.text else PlanBuilderPalette.muted
                        )
                        Surface(
                            onClick = { editingDay = day },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("builder-running-day-$day")
                                .semantics {
                                    contentDescription = "$dayLabel: $sessionLabel. Tap to edit"
                                },
                            color = tileBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, tileBorder)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (code in setOf("L", "Q", "E")) {
                                    Box(
                                        Modifier.fillMaxWidth().height(3.dp)
                                            .align(Alignment.TopCenter)
                                            .background(accentColor)
                                    )
                                }
                                Text(
                                    text = code.ifBlank { "·" },
                                    color = if (code in setOf("L", "Q", "E")) accentColor else PlanBuilderPalette.muted,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                if (isPinned) {
                                    Box(
                                        Modifier.size(5.dp)
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 5.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean visual legend + summary footer
            Row(
                Modifier.fillMaxWidth().testTag("builder-running-legend"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WeekLegendPill("Easy", StrideLabVisualPalette.easyTeal)
                    WeekLegendPill("Quality", StrideLabVisualPalette.moderateAmber)
                    WeekLegendPill("Long", StrideLabVisualPalette.lime)
                }
                Text(
                    "$assigned/$count placed",
                    color = PlanBuilderPalette.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("builder-running-assignment-progress")
                )
            }
            StrideLabHairline()
        }

        Spacer(Modifier.height(14.dp))

        // Clean "Choose my days" | "Coach decides" segmented toggle right in place
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to "Choose my days", false to "Coach decides").forEach { (mode, label) ->
                    val active = custom == mode
                    val bg by animateColorAsState(
                        if (active) PlanBuilderPalette.accent else Color.Transparent,
                        animationSpec = tween(180),
                        label = "mode-$label"
                    )
                    Surface(
                        onClick = { onCustom(mode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag(if (mode) "builder-running-custom" else "builder-running-decide"),
                        color = bg,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (custom) "Tap any day tile above to place or swap your Long, Quality, or Easy runs."
            else "StrideLab automatically optimizes recovery spacing across your available days.",
            color = PlanBuilderPalette.muted,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.testTag("builder-running-pinned-note")
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "QUALITY SESSIONS PER WEEK",
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = PlanBuilderPalette.muted
        )
        Spacer(Modifier.height(10.dp))
        val maxQualityCount = (count - 2).coerceIn(0, 2)
        val currentQualityCount = request.qualityCount.coerceIn(0, 2)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(0, 1, 2).forEach { qCount ->
                    val chosen = currentQualityCount == qCount
                    val enabled = qCount <= maxQualityCount
                    Surface(
                        onClick = { onQualityCount(qCount) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("builder-advanced-quality-count-$qCount"),
                        color = if (chosen) PlanBuilderPalette.accent else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                when (qCount) {
                                    0 -> "Easy only"
                                    1 -> "1 / week"
                                    else -> "2 / week"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (chosen) FontWeight.ExtraBold else FontWeight.Medium,
                                color = when {
                                    chosen -> PlanBuilderPalette.accentOn
                                    enabled -> PlanBuilderPalette.secondary
                                    else -> PlanBuilderPalette.disabled
                                }
                            )
                        }
                    }
                }
            }
        }
        if (maxQualityCount < 2) {
            Text(
                "2 quality sessions need 4 running days, including one easy run and one long run.",
                modifier = Modifier.padding(top = 6.dp).testTag("builder-quality-frequency-reason"),
                fontSize = 12.sp,
                color = PlanBuilderPalette.secondary
            )
        }
    }

    val chosenDay = editingDay
    if (chosenDay != null) {
        RunningDayAssignmentSheet(
            day = chosenDay,
            request = request,
            slots = slots,
            onAssign = onAssignSlot,
            onClear = onClearDay,
            onDismiss = { editingDay = null }
        )
    }
}

@Composable
private fun WeekLegendPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = PlanBuilderPalette.secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private data class RunningDayChoice(
    val code: String,
    val title: String,
    val subtitle: String,
    val indices: List<Int>,
    val tag: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunningDayAssignmentSheet(
    day: Int,
    request: PlanRequest,
    slots: List<Int>,
    onAssign: (Int, Int) -> Unit,
    onClear: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val assignedIndex = slots.indexOf(day)
    val options = listOf(
        RunningDayChoice("E", "Easy run", "Aerobic base and recovery",
            ((request.qualityCount + 1) until request.runDays).toList(), "easy", StrideLabVisualPalette.easyTeal),
        RunningDayChoice("Q", "Quality workout", "Tempo, Threshold, Intervals or Hills",
            (1..request.qualityCount).toList(), "quality", StrideLabVisualPalette.moderateAmber),
        RunningDayChoice("L", "Long run", "Key weekly endurance run",
            listOf(0), "long", StrideLabVisualPalette.lime)
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("builder-run-day-sheet"),
        containerColor = PlanBuilderPalette.surface,
        contentColor = PlanBuilderPalette.text,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text("ASSIGN SESSION", color = PlanBuilderPalette.accent,
                fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() },
                color = PlanBuilderPalette.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(14.dp))
            options.forEach { choice ->
                val targetIndex = choice.indices.firstOrNull { slots.getOrNull(it) == day }
                    ?: choice.indices.firstOrNull { slots.getOrNull(it) == 0 }
                    ?: choice.indices.firstOrNull()
                val selected = assignedIndex in choice.indices && assignedIndex >= 0
                val fromDay = targetIndex?.let { slots.getOrNull(it) } ?: 0
                val status = when {
                    targetIndex == null -> "Not active in current count"
                    selected -> "Assigned"
                    fromDay == 0 -> "Place here"
                    assignedIndex >= 0 -> "Swap with ${DayOfWeek.of(fromDay).name.take(3)}"
                    else -> "Move from ${DayOfWeek.of(fromDay).name.take(3)}"
                }
                Surface(
                    onClick = {
                        if (targetIndex != null) {
                            onAssign(targetIndex, day)
                            onDismiss()
                        }
                    },
                    enabled = targetIndex != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .heightIn(min = 66.dp)
                        .testTag("builder-run-sheet-option-${choice.tag}"),
                    color = if (selected) choice.color.copy(alpha = 0.14f) else PlanBuilderPalette.surfaceRaised,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (selected) choice.color else PlanBuilderPalette.border)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(choice.color.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(choice.code, color = choice.color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(choice.title, color = PlanBuilderPalette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(choice.subtitle, color = PlanBuilderPalette.secondary, fontSize = 11.sp)
                        }
                        Text(
                            status,
                            color = if (selected) choice.color else PlanBuilderPalette.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (assignedIndex >= 0) {
                TextButton(
                    onClick = { onClear(day); onDismiss() },
                    modifier = Modifier.fillMaxWidth().testTag("builder-run-sheet-clear")
                ) {
                    Text("Make rest day", color = StrideLabVisualPalette.highCoral, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
