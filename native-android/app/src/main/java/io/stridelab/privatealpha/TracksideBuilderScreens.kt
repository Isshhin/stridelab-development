package io.stridelab.privatealpha

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val black = PlanBuilderPalette.background
private val outline = PlanBuilderPalette.border
private val surfaceCard = PlanBuilderPalette.surface
private val selectedTint = PlanBuilderPalette.selected
private val limeAccent = PlanBuilderPalette.accent
private val white = PlanBuilderPalette.text
private val muted = PlanBuilderPalette.secondary

@Composable
internal fun TracksideBuilderFrame(
    eyebrow: String,
    toolbarTitle: String = "Generate Training Plan",
    ctaLabel: String = "Continue",
    headline: String,
    description: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    descriptionBottomGap: Int = 20,
    descriptionFontSize: Float = 13f,
    contentTopPadding: Int = 12,
    headlineLineHeight: Int = 34,
    progressStep: Int = 2,
    onSelectStage: ((Int) -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(black).testTag("figma-builder-frame")) {
        // Refined Top Bar with circular obsidian back button & sleek animated progress bar
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(40.dp).testTag("builder-figma-back"),
                shape = CircleShape,
                color = PlanBuilderPalette.surface,
                border = BorderStroke(1.dp, PlanBuilderPalette.border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("‹", color = white, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
            }
            Box(Modifier.weight(1f)) {
                PlanBuilderStageProgress(progressStep, onSelectStage = onSelectStage)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = contentTopPadding.dp, bottom = 20.dp)
        ) {
            Text(
                headline,
                fontSize = 28.sp,
                lineHeight = headlineLineHeight.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.ExtraBold,
                color = white,
                modifier = Modifier.fillMaxWidth()
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    fontSize = descriptionFontSize.sp,
                    lineHeight = 19.sp,
                    color = muted
                )
                Spacer(Modifier.height(descriptionBottomGap.dp))
            } else {
                Spacer(Modifier.height(14.dp))
            }
            body()
        }

        footer?.invoke()
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, black.copy(alpha = 0.96f), black)
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp)
        ) {
            Button(
                onClick = onContinue,
                enabled = continueEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("builder-figma-continue"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = limeAccent,
                    contentColor = PlanBuilderPalette.accentOn,
                    disabledContainerColor = PlanBuilderPalette.surfaceRaised,
                    disabledContentColor = PlanBuilderPalette.muted
                )
            ) {
                Text(ctaLabel, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
            }
        }
    }
}

@Composable
internal fun TracksideDisciplineScreen(
    request: PlanRequest,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val entries = listOf(
        Triple("run", "Running", "Endurance, speed & race preparation"),
        Triple("str", "Strength", "Muscle, power & injury resilience"),
        Triple("cal", "Calisthenics", "Bodyweight mastery & core control"),
        Triple("cond", "Conditioning", "Work capacity & metabolic fitness")
    )
    TracksideBuilderFrame(
        eyebrow = "BUILD YOUR PLAN",
        headline = "Choose your focus",
        description = "Select one or combine multiple disciplines — we adapt the schedule for you.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = request.runDays + request.strengthDays +
            request.calDays + request.conditioningDays > 0,
        progressStep = 1
    ) {
        entries.forEachIndexed { i, (discipline, name, desc) ->
            val selected = when (discipline) {
                "run" -> request.runDays > 0
                "str" -> request.strengthDays > 0
                "cal" -> request.calDays > 0
                else -> request.conditioningDays > 0
            }
            val discColor = StrideLabVisualPalette.disciplineColor(discipline)
            val cardBg by animateColorAsState(
                if (selected) discColor.copy(alpha = 0.16f) else surfaceCard,
                animationSpec = tween(180),
                label = "disc-$discipline"
            )
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp)
                    .testTag("discipline-$discipline"),
                onClick = { onToggle(discipline) },
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) discColor else outline)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) discColor else discColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(when (discipline) {
                                "run" -> R.drawable.trackside_discipline_running
                                "str" -> R.drawable.trackside_discipline_strength
                                "cal" -> R.drawable.trackside_discipline_calisthenics
                                else -> R.drawable.trackside_discipline_conditioning
                            }),
                            tint = if (selected) PlanBuilderPalette.accentOn else discColor,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp).testTag("discipline-icon-$discipline")
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            name,
                            color = if (selected) discColor else white,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            desc,
                            color = if (selected) white.copy(alpha = 0.85f) else muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (i < entries.lastIndex) Spacer(Modifier.height(10.dp))
        }
        val chosen = listOf(request.runDays, request.strengthDays,
            request.calDays, request.conditioningDays).count { it > 0 }
        Spacer(Modifier.height(16.dp))
        Text(
            if (chosen > 1) "$chosen disciplines selected · Smart hybrid scheduling enabled"
            else "$chosen selected",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = if (chosen > 1) limeAccent else muted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TracksideOption(
    label: String, description: String, selected: Boolean,
    icon: Int, onClick: () -> Unit, height: Int = 82,
    tag: String = label, runningGoal: Boolean = false
) {
    val bg by animateColorAsState(
        if (selected) limeAccent.copy(alpha = 0.16f) else surfaceCard,
        animationSpec = tween(180),
        label = "opt-$tag"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = height.dp)
            .testTag("builder-option-$tag"),
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) limeAccent else outline)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) limeAccent else PlanBuilderPalette.surfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = if (selected) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                    modifier = Modifier.size(23.dp).testTag("builder-goal-icon-$tag")
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    label,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) limeAccent else white,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (selected) white.copy(alpha = 0.85f) else muted,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
internal fun TracksideRunningGoalScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val entries = listOf(
        Triple("race", "Train for a race", "5K, 10K, half marathon or marathon"),
        Triple("faster", "Run faster", "Improve pace and speed economy"),
        Triple("endurance", "Build endurance", "Run longer and expand your aerobic base"),
        Triple("fitness", "General fitness", "Stay consistent with balanced running"),
        Triple("custom", "Train your way", "Customize your mileage and workout style")
    )
    TracksideBuilderFrame(
        eyebrow = "RUNNING SETUP",
        headline = "Your running goal",
        description = "What are you training toward?",
        onBack = onBack,
        onContinue = onContinue,
        progressStep = 2
    ) {
        entries.forEachIndexed { i, (route, label, desc) ->
            TracksideOption(
                label, desc, selected == route,
                icon = when (route) {
                    "race" -> R.drawable.trackside_running_goal_race
                    "faster" -> R.drawable.trackside_running_goal_faster
                    "endurance" -> R.drawable.trackside_running_goal_endurance
                    "fitness" -> R.drawable.trackside_running_goal_fitness
                    else -> R.drawable.trackside_running_goal_custom
                },
                onClick = { onSelect(route) }, tag = route, runningGoal = true
            )
            if (i < entries.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TracksideRaceOption(
    title: String, description: String, selected: Boolean, icon: Int,
    tag: String, onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (selected) selectedTint else surfaceCard,
        animationSpec = tween(180),
        label = "race-$tag"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp).testTag("builder-option-$tag"),
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) limeAccent else outline)
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (selected) limeAccent.copy(alpha = 0.18f) else PlanBuilderPalette.surfaceRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = if (selected) limeAccent else PlanBuilderPalette.secondary,
                    modifier = Modifier.size(22.dp).testTag("builder-race-icon-$tag")
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.5.sp, color = white, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 11.5.sp, lineHeight = 15.sp, color = muted, maxLines = 2)
            }
            if (selected) {
                Icon(
                    painterResource(R.drawable.trackside_race_goal_check),
                    contentDescription = null, tint = limeAccent,
                    modifier = Modifier.size(18.dp).testTag("builder-race-selected-check")
                )
            }
        }
    }
}

@Composable
internal fun TracksideRaceGoalScreen(
    intent: String, distance: String,
    onIntent: (String) -> Unit, onDistance: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit,
    targetRaceDate: LocalDate? = null,
    onTargetRaceDate: ((LocalDate?) -> Unit)? = null,
    benchmarkSeconds: Int? = null,
    onBenchmarkSeconds: ((Int?) -> Unit)? = null,
    paceZones: Engine15ProvisionalPaceZones? = null
) {
    val context = LocalContext.current
    var benchmarkDialogOpen by remember { mutableStateOf(false) }

    val intents = listOf(
        Triple("pr", "Beat my personal best", "Train to improve your existing PR"),
        Triple("time", "Reach a target time", "Work toward a specific finish time"),
        Triple("finish", "Finish confidently", "Prepare to complete the distance"),
        Triple("benchmark", "Set my first benchmark", "Establish a PR for a new distance")
    )
    TracksideBuilderFrame(
        eyebrow = "RUNNING / RACE TARGET",
        headline = "What are you aiming\nfor?",
        description = "Pick your race distance, optional time target, and race day.",
        onBack = onBack,
        onContinue = onContinue,
        descriptionBottomGap = 14,
        progressStep = 2,
        continueEnabled = true
    ) {
        Text("RACE DISTANCE", fontSize = 10.sp, letterSpacing = 1.4.sp, color = muted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("5K", "10K", "21K", "42K").forEach { dist ->
                val active = distance == dist
                Surface(
                    modifier = Modifier.weight(1f).height(44.dp).testTag("builder-distance-$dist"),
                    onClick = { onDistance(dist) },
                    color = if (active) limeAccent else surfaceCard,
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, if (active) limeAccent else outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            dist,
                            fontSize = 13.5.sp,
                            color = if (active) PlanBuilderPalette.accentOn else white,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        intents.forEachIndexed { i, (id, title, desc) ->
            TracksideRaceOption(
                title, desc, intent == id,
                icon = when (id) {
                    "pr" -> R.drawable.trackside_race_goal_pr
                    "time" -> R.drawable.trackside_race_goal_time
                    "finish" -> R.drawable.trackside_race_goal_finish
                    else -> R.drawable.trackside_race_goal_benchmark
                },
                onClick = { onIntent(id) }, tag = id
            )
            if (i < intents.lastIndex) Spacer(Modifier.height(8.dp))
        }
        if (intent == "pr" || intent == "time") {
            Spacer(Modifier.height(14.dp))
            BenchmarkEntryCard(
                title = if (intent == "time") "Target $distance finish time" else "Current $distance PR",
                subtitle = "Estimates training pace zones (editable anytime)",
                benchmarkSeconds = benchmarkSeconds,
                onClick = { benchmarkDialogOpen = true },
                cardTag = "builder-race-pr-panel",
                buttonTag = "builder-race-add-pr"
            )
        }
        if (paceZones != null && (benchmarkSeconds != null || paceZones.basis == Engine15PaceBandBasis.CONFIRMED_EASY_BASELINE_ESTIMATE)) {
            Spacer(Modifier.height(10.dp))
            ProvisionalPaceZoneStrip(paceZones)
        }
        Spacer(Modifier.height(14.dp))
        Text("TARGET RACE DAY", fontSize = 10.sp, letterSpacing = 1.4.sp, color = muted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = {
                val init = targetRaceDate ?: LocalDate.now().plusWeeks(12)
                DatePickerDialog(context, { _, y, m, d ->
                    onTargetRaceDate?.invoke(LocalDate.of(y, m + 1, d))
                }, init.year, init.monthValue - 1, init.dayOfMonth).show()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("builder-race-target-day"),
            color = surfaceCard,
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, if (targetRaceDate != null) limeAccent else outline)
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (targetRaceDate != null) limeAccent.copy(alpha = 0.16f) else PlanBuilderPalette.surfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.trackside_race_goal_calendar),
                        contentDescription = null, tint = Color.Unspecified,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        targetRaceDate?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))
                            ?: "Choose race date (optional)",
                        fontSize = 13.5.sp,
                        fontWeight = if (targetRaceDate != null) FontWeight.Bold else FontWeight.Medium,
                        color = if (targetRaceDate != null) white else muted
                    )
                    if (targetRaceDate != null) {
                        val wks = ChronoUnit.WEEKS.between(LocalDate.now(), targetRaceDate).coerceIn(4, 16)
                        Text("$wks-week plan · automatic taper synced", fontSize = 11.sp, color = limeAccent)
                    }
                }
                if (targetRaceDate != null && onTargetRaceDate != null) {
                    TextButton(onClick = { onTargetRaceDate(null) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Clear", fontSize = 11.sp, color = muted)
                    }
                } else {
                    Text("›", color = muted, fontSize = 20.sp)
                }
            }
        }
    }
    if (benchmarkDialogOpen) {
        BenchmarkTimeDialog(
            distanceLabel = distance,
            initialSeconds = benchmarkSeconds,
            onSave = { sec -> onBenchmarkSeconds?.invoke(sec); benchmarkDialogOpen = false },
            onDismiss = { benchmarkDialogOpen = false }
        )
    }
}

@Composable
private fun BenchmarkEntryCard(
    title: String,
    subtitle: String,
    benchmarkSeconds: Int?,
    onClick: () -> Unit,
    cardTag: String,
    buttonTag: String
) {
    fun formatSec(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s) else "%d:%02d".format(Locale.US, m, s)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(cardTag),
        color = surfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (benchmarkSeconds != null) limeAccent.copy(alpha = 0.65f) else outline)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = white)
                Text(
                    benchmarkSeconds?.let { "Recorded: ${formatSec(it)}" } ?: subtitle,
                    fontSize = 11.5.sp,
                    color = if (benchmarkSeconds != null) limeAccent else muted,
                    fontWeight = if (benchmarkSeconds != null) FontWeight.Bold else FontWeight.Normal
                )
            }
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                color = if (benchmarkSeconds != null) PlanBuilderPalette.surfaceRaised else limeAccent,
                border = BorderStroke(1.dp, if (benchmarkSeconds != null) limeAccent else Color.Transparent),
                modifier = Modifier.heightIn(min = 38.dp).testTag(buttonTag)
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (benchmarkSeconds != null) "Edit" else "Add result",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (benchmarkSeconds != null) limeAccent else PlanBuilderPalette.accentOn
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkTimeDialog(
    distanceLabel: String,
    initialSeconds: Int?,
    onSave: (Int?) -> Unit,
    onDismiss: () -> Unit,
    showDistancePicker: Boolean = false,
    selectedDistance: String = distanceLabel,
    onSelectDistance: ((String) -> Unit)? = null
) {
    var hText by remember(initialSeconds) { mutableStateOf(initialSeconds?.let { (it / 3600).toString() } ?: "0") }
    var mText by remember(initialSeconds) { mutableStateOf(initialSeconds?.let { ((it % 3600) / 60).toString().padStart(2, '0') } ?: "25") }
    var sText by remember(initialSeconds) { mutableStateOf(initialSeconds?.let { (it % 60).toString().padStart(2, '0') } ?: "00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PlanBuilderPalette.surface,
        title = { Text("Recent Race / Time Trial", color = white, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showDistancePicker && onSelectDistance != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("5K", "10K", "21K", "42K").forEach { d ->
                            val active = selectedDistance == d
                            Surface(
                                onClick = { onSelectDistance(d) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                color = if (active) limeAccent else PlanBuilderPalette.surfaceRaised,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(d, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (active) PlanBuilderPalette.accentOn else white)
                                }
                            }
                        }
                    }
                }
                Text("Enter your $selectedDistance time to estimate training pace zones.", color = muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hText, onValueChange = { hText = it.filter(Char::isDigit).take(1) }, label = { Text("Hr") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = mText, onValueChange = { mText = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sText, onValueChange = { sText = it.filter(Char::isDigit).take(2) }, label = { Text("Sec") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val totalSec = (hText.toIntOrNull() ?: 0) * 3600 + (mText.toIntOrNull() ?: 0) * 60 + (sText.toIntOrNull() ?: 0)
                if (totalSec in 120..43200) onSave(totalSec) else onDismiss()
            }) { Text("Save", color = limeAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (initialSeconds != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Clear", color = muted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = muted) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracksideAbilityRange(
    header: String, title: String, note: String,
    value: Double, maxValue: Float, unit: String, tag: String,
    onValue: (Double) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().testTag("builder-ability-$tag-card"),
        color = surfaceCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, outline)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(limeAccent.copy(alpha = 0.04f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(header, fontSize = 10.sp, letterSpacing = 1.4.sp, color = PlanBuilderPalette.muted, fontWeight = FontWeight.Bold)
                Text(note, color = muted, fontSize = 10.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    String.format(Locale.getDefault(), "%.0f", value),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = white,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    unit,
                    color = limeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = value.toFloat().coerceIn(0f, maxValue),
                onValueChange = { onValue(it.toDouble()) },
                valueRange = 0f..maxValue,
                modifier = Modifier.fillMaxWidth().height(30.dp).testTag("builder-ability-$tag-slider"),
                thumb = { _ ->
                    Box(
                        Modifier.size(20.dp)
                            .clip(CircleShape)
                            .background(limeAccent)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(PlanBuilderPalette.accentOn)
                    )
                },
                track = { _ ->
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlanBuilderPalette.surfaceRaised)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((value.toFloat() / maxValue).coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(StrideLabVisualPalette.easyTeal, limeAccent)
                                    )
                                )
                        )
                    }
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 km", fontSize = 10.sp, color = PlanBuilderPalette.muted)
                Text(String.format(Locale.getDefault(), "%.0f km", maxValue / 2), fontSize = 10.sp, color = PlanBuilderPalette.muted)
                Text(String.format(Locale.getDefault(), "%.0f km", maxValue), fontSize = 10.sp, color = PlanBuilderPalette.muted)
            }
        }
    }
}

/**
 * Art-directed Running Ability screen:
 * - Clean experience selector pills
 * - Typical weekly volume (automatic)
 * - Longest recent run
 * - ONLY "Recent Performance (Optional)" card below it (wired to real benchmark picker + live pace zones)
 */
@Composable
internal fun TracksideRunningAbilityScreen(
    experience: Int, weeklyKm: Double, longestKm: Double,
    onExperience: (Int) -> Unit,
    onWeeklyKm: (Double) -> Unit,
    onLongestKm: (Double) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAdvanced: () -> Unit,
    historyContent: @Composable () -> Unit = {},
    benchmarkDistance: String = "5K",
    benchmarkSeconds: Int? = null,
    onSaveBenchmark: ((String, Int?) -> Unit)? = null,
    paceZones: Engine15ProvisionalPaceZones? = null,
    autoDetectedExperience: Boolean = false
) {
    var benchmarkDialogOpen by remember { mutableStateOf(false) }
    var chosenDistance by remember(benchmarkDistance) { mutableStateOf(benchmarkDistance) }

    TracksideBuilderFrame(
        eyebrow = "RUNNING / BASELINE",
        headline = "Where are you\nstarting?",
        description = "Auto-calibrated from your training history — adjust any value anytime.",
        onBack = onBack,
        onContinue = onContinue,
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34,
        progressStep = 3
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EXPERIENCE LEVEL", fontSize = 10.sp, letterSpacing = 1.4.sp, color = PlanBuilderPalette.muted, fontWeight = FontWeight.Bold)
            if (autoDetectedExperience) {
                Text("Auto-detected from history", fontSize = 10.5.sp, color = limeAccent, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = surfaceCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, outline)
        ) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Beginner", "Intermediate", "Advanced").forEachIndexed { i, label ->
                    val active = experience == i
                    Surface(
                        modifier = Modifier.weight(1f).height(44.dp).testTag("builder-experience-$i"),
                        onClick = { onExperience(i) },
                        color = if (active) limeAccent else Color.Transparent,
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (active) PlanBuilderPalette.accentOn else muted,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        TracksideAbilityRange(
            header = "TYPICAL WEEKLY VOLUME", title = "Typical weekly volume",
            note = "Automatic · 4–6 wks", value = weeklyKm, maxValue = 120f, unit = "km / wk",
            tag = "weekly", onValue = onWeeklyKm
        )
        Spacer(Modifier.height(12.dp))
        TracksideAbilityRange(
            header = "LONGEST RECENT RUN", title = "Longest run completed",
            note = "Recent training", value = longestKm, maxValue = 42f, unit = "km",
            tag = "longest", onValue = onLongestKm
        )
        Spacer(Modifier.height(16.dp))
        Text("RECENT PERFORMANCE  (OPTIONAL)", fontSize = 10.sp, letterSpacing = 1.4.sp, color = PlanBuilderPalette.muted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        BenchmarkEntryCard(
            title = "Race or time trial result",
            subtitle = "Estimates accurate training pace zones",
            benchmarkSeconds = benchmarkSeconds,
            onClick = { benchmarkDialogOpen = true },
            cardTag = "builder-ability-optional-card",
            buttonTag = "builder-ability-add-result"
        )
        if (paceZones != null && (benchmarkSeconds != null || autoDetectedExperience)) {
            Spacer(Modifier.height(10.dp))
            ProvisionalPaceZoneStrip(paceZones)
        }
    }
    if (benchmarkDialogOpen) {
        BenchmarkTimeDialog(
            distanceLabel = chosenDistance,
            initialSeconds = benchmarkSeconds,
            showDistancePicker = true,
            selectedDistance = chosenDistance,
            onSelectDistance = { chosenDistance = it },
            onSave = { sec -> onSaveBenchmark?.invoke(chosenDistance, sec); benchmarkDialogOpen = false },
            onDismiss = { benchmarkDialogOpen = false }
        )
    }
}
