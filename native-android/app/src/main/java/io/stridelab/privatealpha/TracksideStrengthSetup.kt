package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

private val strCoral = StrideLabVisualPalette.strengthCoral

/**
 * Art-directed Strength Setup & Equipment screens.
 * Pure AMOLED obsidian surfaces, Strength Coral discipline accent + StrideLab Lime primary CTA,
 * readable high-contrast icons and pills, zero dead-end "Not available in Engine 1" chrome.
 */
@Composable
internal fun TracksideStrengthSetup(
    request: PlanRequest,
    selectedSlot: Int,
    onSelectedSlot: (Int) -> Unit,
    onChangeMode: (Boolean) -> Unit,
    onCount: (Int) -> Unit,
    onAssign: (Int, Int) -> Unit,
    onClear: (Int) -> Unit,
    onEquipment: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onEditGoal: () -> Unit,
    onContinue: () -> Unit
) {
    val track = strengthTrack(request)
    val title = if (track == "str") "Strength" else request.disciplineGoal("str")
    val manual = "str" in request.weeklySlots
    val count = request.strengthDays.coerceAtLeast(1)
    val explicit = WeeklyPlacement.days(request, "str")
    val placed = if (manual) explicit.count { it in 1..7 } else 0
    var editingDays by rememberSaveable { mutableStateOf(false) }
    var choosingEquipment by rememberSaveable { mutableStateOf(false) }

    if (choosingEquipment) {
        TracksideStrengthEquipment(
            request = request,
            onChoice = onEquipment,
            onBack = { choosingEquipment = false }
        )
        return
    }

    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle = "$title setup",
        eyebrow = "${title.uppercase()} / SCHEDULE",
        headline = "Your training week",
        description = "Set your weekly sessions, days, and available gear.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = !manual || WeeklyPlacement.full(request, "str"),
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
                listOf(true to "Choose my days", false to "Coach decides").forEach { (custom, label) ->
                    val selected = manual == custom
                    Surface(
                        onClick = { onChangeMode(custom) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag(if (custom) "builder-strength-customize" else "builder-strength-auto"),
                        color = if (selected) PlanBuilderPalette.accent else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                color = if (selected) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        StrengthSectionLabel("PREFERENCES")
        Spacer(Modifier.height(8.dp))

        StrengthPreferenceRow(
            title = "Strength goal",
            subtitle = when (Engine1Engines.goal(request, "str")) {
                "General strength" -> "Build strength"
                "Muscle development" -> "Build muscle"
                "Strength endurance" -> "Maintain fitness"
                else -> request.disciplineGoal("str")
            },
            iconRes = R.drawable.trackside_strength_goal_icon,
            onClick = onEditGoal,
            tag = "builder-strength-setup-goal"
        )
        Spacer(Modifier.height(8.dp))
        if (track == "plyo") {
            Text("Start with short sets of low jumps. Stop when your landings get loud or your legs feel tired.",
                color = StrideLabVisualPalette.ink)
            Spacer(Modifier.height(8.dp))
        }
        val equipmentSummary = listOf(
            "db" to "Dumbbells", "bands" to "Bands",
            "bb" to "Barbell", "bench" to "Bench",
            "bar" to "Pull-up bar", "rack" to "Rack"
        ).filter { (id, _) -> (request.equipment[id] ?: 0) > 0 }
            .joinToString(" · ") { it.second }.ifBlank { "Bodyweight only" }
        StrengthPreferenceRow(
            title = "Equipment",
            subtitle = equipmentSummary,
            iconRes = R.drawable.trackside_strength_equipment_icon,
            onClick = { choosingEquipment = true },
            tag = "builder-strength-setup-equipment"
        )

        Spacer(Modifier.height(14.dp))

        // Weekly Sessions & Day Grid Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("builder-strength-weekly"),
            color = PlanBuilderPalette.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, PlanBuilderPalette.border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StrengthSectionLabel("WEEKLY SESSIONS")
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
                    listOf(-1, 1).forEach { delta ->
                        val enabled = count + delta in 1..4
                        Surface(
                            onClick = { onCount(count + delta) },
                            enabled = enabled,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(42.dp)
                                .testTag(if (delta < 0) "builder-strength-minus" else "builder-strength-plus"),
                            color = PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PlanBuilderPalette.borderStrong)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (delta < 0) "−" else "+",
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
                        val slot = if (manual) explicit.indexOf(day) else suggested.indexOf(day)
                        val selected = manual && slot == selectedSlot && slot >= 0
                        Surface(
                            onClick = {
                                if (manual) onAssign(selectedSlot.coerceIn(0, count - 1), day)
                                else {
                                    onChangeMode(true)
                                    onAssign(0, day)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("builder-strength-weekday-$day"),
                            color = when {
                                selected -> strCoral
                                slot >= 0 -> strCoral.copy(alpha = 0.15f)
                                day !in request.days -> PlanBuilderPalette.input
                                else -> PlanBuilderPalette.surfaceRaised
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    selected -> strCoral
                                    slot >= 0 -> strCoral.copy(alpha = 0.65f)
                                    else -> PlanBuilderPalette.border
                                }
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val label = if (slot >= 0) when (slot) {
                                    0 -> "UB"
                                    1 -> "LB"
                                    else -> "S${slot + 1}"
                                } else if (day !in request.days) "–" else "·"
                                Text(
                                    label,
                                    color = when {
                                        selected -> PlanBuilderPalette.accentOn
                                        slot >= 0 -> strCoral
                                        else -> PlanBuilderPalette.muted
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
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
                        if (manual) "$placed of $count placed" else "Coach places $count sessions",
                        modifier = Modifier.weight(1f),
                        color = PlanBuilderPalette.secondary,
                        fontSize = 11.5.sp
                    )
                    TextButton(
                        onClick = { editingDays = !editingDays },
                        modifier = Modifier.testTag("builder-strength-edit-schedule")
                    ) {
                        Text(
                            if (editingDays) "Done" else "Select session ›",
                            color = PlanBuilderPalette.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (editingDays) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(count) { i ->
                            val isChosen = i == selectedSlot
                            Surface(
                                onClick = { onSelectedSlot(i) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("builder-strength-session-$i"),
                                color = if (isChosen) strCoral else PlanBuilderPalette.surfaceRaised,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isChosen) strCoral else PlanBuilderPalette.border)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "Session ${i + 1}",
                                        color = if (isChosen) PlanBuilderPalette.accentOn else PlanBuilderPalette.text,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    if (manual) {
                        TextButton(onClick = { onClear(selectedSlot.coerceIn(0, count - 1)) }) {
                            Text("Clear selected session day", color = StrideLabVisualPalette.highCoral, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthSectionLabel(label: String) {
    Text(
        label,
        color = PlanBuilderPalette.muted,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun StrengthPreferenceRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .testTag(tag),
        color = PlanBuilderPalette.surface,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PlanBuilderPalette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(strCoral.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = strCoral,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PlanBuilderPalette.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = PlanBuilderPalette.secondary, fontSize = 11.5.sp, maxLines = 1)
            }
            Text("›", color = PlanBuilderPalette.secondary, fontSize = 20.sp)
        }
    }
}

@Composable
private fun TracksideStrengthEquipment(
    request: PlanRequest,
    onChoice: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val list = listOf(
        "db" to "Dumbbells", "bb" to "Barbell",
        "bands" to "Resistance bands", "bench" to "Bench",
        "bar" to "Pull-up bar", "rack" to "Squat rack",
        "anchor" to "Band anchors", "support" to "Stable supports"
    )
    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle = "Equipment",
        eyebrow = "STRENGTH / GEAR",
        headline = "What equipment\ndo you have?",
        description = "StrideLab matches every exercise to your available gear.",
        ctaLabel = "Save gear",
        onBack = onBack,
        onContinue = onBack,
        descriptionBottomGap = 14,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        val selected = list.count { (id, _) -> (request.equipment[id] ?: 0) > 0 }
        Text(
            "$selected of ${list.size} selected",
            color = PlanBuilderPalette.accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                pair.forEach { (id, label) ->
                    val active = (request.equipment[id] ?: 0) > 0
                    Surface(
                        onClick = { onChoice(id, !active) },
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .testTag("builder-strength-equipment-$id"),
                        color = if (active) strCoral.copy(alpha = 0.18f) else PlanBuilderPalette.surface,
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(
                            if (active) 1.5.dp else 1.dp,
                            if (active) strCoral else PlanBuilderPalette.border
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                label,
                                color = if (active) strCoral else PlanBuilderPalette.secondary,
                                fontSize = 13.5.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
    }
}
