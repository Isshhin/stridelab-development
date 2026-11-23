package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

internal object WorkoutDetailPalette {
    val background = Color(0xFF0A0C0E)
    val card = Color(0xFF14181C)
    val cardNested = Color(0xFF161B20)
    val cardRaised = Color(0xFF1C2229)
    val border = Color(0xFF232A31)
    val borderMuted = Color(0xFF1B2128)
    val lime = Color(0xFFD8FB70)
    val amber = Color(0xFFF6D32D)
    val amberDeep = Color(0xFFE0B416)
    val teal = Color(0xFF45D4C8)
    val warmTeal = Color(0xFF4AD3DE)
    val mint = Color(0xFF4ADBA8)
    val text = Color(0xFFF5F7FA)
    val textSecondary = Color(0xFF9BA4B0)
    val textMuted = Color(0xFF7B8591)
}

internal data class WorkoutStepDetail(
    val title: String,
    val purpose: String,
    val zoneBadge: String,
    val metricLine: String?,
    val effortLine: String?,
    val accentColor: Color,
    val durationMinutes: Float,
    val intensityFraction: Float,
    val testTag: String
)

internal sealed interface WorkoutStepItem {
    val accentColor: Color
    val itemTag: String

    data class SingleStep(
        val step: WorkoutStepDetail,
        val testTag: String
    ) : WorkoutStepItem {
        override val accentColor: Color get() = step.accentColor
        override val itemTag: String get() = testTag
    }

    data class RepeatGroup(
        val reps: Int,
        val title: String,
        val badge: String,
        val children: List<WorkoutStepDetail>,
        val recoveryAfterLast: Boolean,
        val testTag: String,
        val countTag: String
    ) : WorkoutStepItem {
        override val accentColor: Color get() = WorkoutDetailPalette.amber
        override val itemTag: String get() = testTag
    }
}

internal data class WorkoutProfileBar(
    val stepIndex: Int,
    val label: String,
    val durationMinutes: Float,
    val intensityFraction: Float,
    val accentColor: Color
)

internal fun formatWorkoutAvgPace(distanceKm: Double?, minutes: Int): String {
    if (distanceKm == null || !distanceKm.isFinite() || distanceKm <= 0.0 || minutes <= 0) return "—"
    val secPerKm = ((minutes * 60.0) / distanceKm).roundToInt().coerceAtLeast(1)
    val paceMin = secPerKm / 60
    val paceSec = secPerKm % 60
    return String.format(Locale.US, "~%d:%02d/km", paceMin, paceSec)
}

internal fun flattenProfileBars(items: List<WorkoutStepItem>): List<WorkoutProfileBar> {
    val bars = mutableListOf<WorkoutProfileBar>()
    items.forEachIndexed { stepIndex, item ->
        when (item) {
            is WorkoutStepItem.SingleStep -> {
                bars += WorkoutProfileBar(
                    stepIndex = stepIndex,
                    label = item.step.title,
                    durationMinutes = item.step.durationMinutes.coerceAtLeast(1f),
                    intensityFraction = item.step.intensityFraction.coerceIn(0.32f, 1f),
                    accentColor = item.step.accentColor
                )
            }
            is WorkoutStepItem.RepeatGroup -> {
                val work = item.children.firstOrNull { it.purpose == "WORK" } ?: item.children.firstOrNull()
                val recover = item.children.firstOrNull { it.purpose == "RECOVERY" }
                if (work != null) {
                    repeat(item.reps.coerceAtLeast(1)) { rep ->
                        bars += WorkoutProfileBar(
                            stepIndex = stepIndex,
                            label = "${work.title} (${rep + 1}/${item.reps})",
                            durationMinutes = work.durationMinutes.coerceAtLeast(1f),
                            intensityFraction = work.intensityFraction.coerceIn(0.32f, 1f),
                            accentColor = work.accentColor
                        )
                        if (recover != null && (rep < item.reps - 1 || item.recoveryAfterLast)) {
                            bars += WorkoutProfileBar(
                                stepIndex = stepIndex,
                                label = recover.title,
                                durationMinutes = recover.durationMinutes.coerceAtLeast(1f),
                                intensityFraction = recover.intensityFraction.coerceIn(0.32f, 1f),
                                accentColor = recover.accentColor
                            )
                        }
                    }
                }
            }
        }
    }
    return bars
}

private fun sameDisplayMixedStep(a: RunningStepV2, b: RunningStepV2): Boolean =
    a == b || (
        a.distanceEquivalentMeters != null && b.distanceEquivalentMeters != null &&
            kotlin.math.abs(a.distanceEquivalentMeters - b.distanceEquivalentMeters) <= 1 &&
            a.copy(distanceEquivalentMeters = b.distanceEquivalentMeters) == b
        )

private fun groupDisplayMixedSteps(steps: List<RunningStepV2>): List<MixedRunningStepBlock> {
    val blocks = mutableListOf<MixedRunningStepBlock>()
    var index = 0
    while (index < steps.size) {
        val current = steps[index]
        if (current.purpose == "WORK" || current.stepIntent == RunningStepIntent.STRIDE) {
            val recovery = steps.getOrNull(index + 1)?.takeIf { it.purpose == "RECOVERY" }
            if (recovery != null) {
                var lastWork = index
                var count = 1
                while (lastWork + 2 < steps.size &&
                    sameDisplayMixedStep(steps[lastWork + 1], recovery) &&
                    sameDisplayMixedStep(steps[lastWork + 2], current)
                ) {
                    count++
                    lastWork += 2
                }
                if (count >= 2) {
                    val after = steps.getOrNull(lastWork + 1)?.let { sameDisplayMixedStep(it, recovery) } == true
                    blocks += MixedRunningStepBlock.Repeat(count, current, recovery, after)
                    index = lastWork + 1 + if (after) 1 else 0
                    continue
                }
            }
            var next = index + 1
            while (next < steps.size && sameDisplayMixedStep(steps[next], current)) next++
            if (next - index >= 2) {
                blocks += MixedRunningStepBlock.Repeat(next - index, current, null, false)
                index = next
                continue
            }
        }
        blocks += MixedRunningStepBlock.Single(current)
        index++
    }
    return blocks
}

internal fun buildWorkoutStepItems(
    session: PlannedSession,
    segments: List<RunningSegment>?,
    mixedSegments: List<RunningStepV2>?,
    textFallback: List<String>,
    planningPaceSecPerKm: Double = 360.0
): List<WorkoutStepItem> {
    val familyLower = session.runningPrescription?.family?.lowercase(Locale.ROOT).orEmpty()
    val isLong = session.runningPrescription?.role == "LONG" || session.kind == "long"
    val paceSec = planningPaceSecPerKm.takeIf { it.isFinite() && it in 180.0..900.0 } ?: 360.0

    fun accentForPurpose(purpose: String): Color = when (purpose) {
        "WARM_UP" -> WorkoutDetailPalette.warmTeal
        "WORK" -> WorkoutDetailPalette.amber
        "COOL_DOWN" -> WorkoutDetailPalette.mint
        "RECOVERY", "TRANSITION", "EASY" -> WorkoutDetailPalette.teal
        else -> if (isLong) WorkoutDetailPalette.teal else WorkoutDetailPalette.warmTeal
    }

    fun titleForPurpose(purpose: String, intent: RunningStepIntent? = null): String = when (intent) {
        RunningStepIntent.WARMUP -> "Warm-up"
        RunningStepIntent.COOLDOWN -> "Cool-down"
        RunningStepIntent.RECOVERY -> "Recover"
        RunningStepIntent.STRIDE -> "Stride"
        RunningStepIntent.STEADY -> "Steady Finish"
        RunningStepIntent.RACE_PACE -> "Race Pace"
        RunningStepIntent.TEMPO -> "Tempo"
        RunningStepIntent.THRESHOLD -> "Threshold"
        RunningStepIntent.VO2_INTERVAL -> "VO₂ Interval"
        RunningStepIntent.HILL -> "Hill Repeat"
        RunningStepIntent.AEROBIC_EASY -> if (isLong) "Long base" else "Easy aerobic"
        null -> when (purpose) {
            "WARM_UP" -> "Warm-up"
            "WORK" -> if (mixedSegments != null) "Work" else "Run"
            "RECOVERY" -> "Recover"
            "TRANSITION" -> "Transition"
            "COOL_DOWN" -> "Cool-down"
            else -> if (isLong) "Long run" else "Easy running"
        }
    }

    fun zoneBadgeForPurpose(purpose: String, intent: RunningStepIntent? = null): String = when (intent) {
        RunningStepIntent.WARMUP -> "Z1-Z2 · Easy/Warm"
        RunningStepIntent.COOLDOWN -> "Z1-Z2 · Warm-down"
        RunningStepIntent.RECOVERY -> "Z1 · Recovery"
        RunningStepIntent.AEROBIC_EASY -> if (isLong) "Z2 · Aerobic Long" else "Z1-Z2 · Easy Aerobic"
        RunningStepIntent.STEADY -> "Z3 · Steady Endurance"
        RunningStepIntent.TEMPO -> "Z3-Z4 · Tempo"
        RunningStepIntent.THRESHOLD -> "Z4 · Threshold / Hard"
        RunningStepIntent.RACE_PACE -> "Z3-Z4 · Goal Race Pace"
        RunningStepIntent.VO2_INTERVAL -> "Z4-Z5 · VO₂max"
        RunningStepIntent.HILL -> "Z4-Z5 · Uphill Effort"
        RunningStepIntent.STRIDE -> "Z5 · Neuromuscular Stride"
        null -> when (purpose) {
            "WARM_UP" -> "Z1-Z2 · Easy/Warm"
            "COOL_DOWN" -> "Z1-Z2 · Warm-down"
            "RECOVERY" -> "Z1-Z2 · Recovery"
            "TRANSITION" -> "Z1-Z2 · Transition"
            "WORK" -> when {
                "interval" in familyLower || "hill" in familyLower -> "Z4-Z5 · Intervals / Hard"
                "tempo" in familyLower || "progression" in familyLower -> "Z3-Z4 · Tempo"
                else -> "Z4 · Threshold / Hard"
            }
            else -> if (isLong) "Z2 · Aerobic Long" else "Z1-Z2 · Easy Aerobic"
        }
    }

    fun intensityForPurpose(purpose: String, intent: RunningStepIntent? = null): Float = when (intent) {
        RunningStepIntent.VO2_INTERVAL, RunningStepIntent.STRIDE -> 0.96f
        RunningStepIntent.HILL -> 0.92f
        RunningStepIntent.THRESHOLD -> 0.86f
        RunningStepIntent.RACE_PACE -> 0.80f
        RunningStepIntent.TEMPO -> 0.78f
        RunningStepIntent.STEADY -> 0.68f
        RunningStepIntent.RECOVERY -> 0.38f
        RunningStepIntent.WARMUP, RunningStepIntent.COOLDOWN -> 0.48f
        RunningStepIntent.AEROBIC_EASY -> if (isLong) 0.58f else 0.48f
        null -> when (purpose) {
            "WORK" -> when {
                "interval" in familyLower || "hill" in familyLower -> 0.94f
                "tempo" in familyLower || "progression" in familyLower -> 0.78f
                else -> 0.86f
            }
            "RECOVERY" -> 0.42f
            "WARM_UP", "COOL_DOWN", "TRANSITION" -> 0.50f
            else -> if (isLong) 0.58f else 0.48f
        }
    }

    fun estimateDurationMin(seconds: Int?, meters: Int?): Float {
        if (seconds != null && seconds > 0) return (seconds / 60f).coerceAtLeast(1f)
        if (meters != null && meters > 0) {
            return ((meters / 1000.0) * (paceSec / 60.0)).toFloat().coerceAtLeast(1f)
        }
        return session.minutes.toFloat().coerceAtLeast(5f)
    }

    fun formatDurationAndMeters(seconds: Int?, meters: Int?, distanceEquivalentMeters: Int?): String? {
        val distMeters = meters ?: distanceEquivalentMeters
        val timeText = seconds?.takeIf { it > 0 }?.let { sec ->
            when {
                sec % 60 == 0 -> "${sec / 60} min"
                sec > 60 -> "${sec / 60} min ${sec % 60} s"
                else -> "$sec s"
            }
        }
        val distText = distMeters?.takeIf { it > 0 }?.let(::runningStepDisplayKmLabel)
        return listOfNotNull(timeText, distText).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun displayEffort(effort: String, paceMin: Int?, paceMax: Int?): String? {
        val paces = listOfNotNull(paceMin, paceMax).distinct().joinToString("–") {
            ActivityRecord.duration(it.toDouble())
        }
        return listOfNotNull(paces.takeIf { it.isNotEmpty() }?.let { "$it /km" },
            effort.takeIf { it.isNotBlank() }).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    val displaySteps = mixedSegments ?: segments?.takeIf { steps ->
        steps.any { it.stepIntent == RunningStepIntent.STRIDE }
    }?.map { step ->
        RunningStepV2(step.purpose, step.effort, meters = step.meters, stepIntent = step.stepIntent,
            targetPaceMinSecPerKm = step.targetPaceMinSecPerKm,
            targetPaceMaxSecPerKm = step.targetPaceMaxSecPerKm,
            targetHrMinBpm = step.targetHrMinBpm, targetHrMaxBpm = step.targetHrMaxBpm)
    }
    if (displaySteps != null) {
        return groupDisplayMixedSteps(displaySteps).mapIndexed { index, block ->
            when (block) {
                is MixedRunningStepBlock.Single -> {
                    val step = block.segment
                    WorkoutStepItem.SingleStep(
                        step = WorkoutStepDetail(
                            title = titleForPurpose(step.purpose, step.stepIntent),
                            purpose = step.purpose,
                            zoneBadge = zoneBadgeForPurpose(step.purpose, step.stepIntent),
                            metricLine = formatDurationAndMeters(step.seconds, step.meters, step.distanceEquivalentMeters),
                            effortLine = displayEffort(step.effort, step.targetPaceMinSecPerKm, step.targetPaceMaxSecPerKm),
                            accentColor = accentForPurpose(step.purpose),
                            durationMinutes = estimateDurationMin(step.seconds, step.meters ?: step.distanceEquivalentMeters),
                            intensityFraction = intensityForPurpose(step.purpose, step.stepIntent),
                            testTag = "workout-mixed-step-$index"
                        ),
                        testTag = "workout-mixed-step-$index"
                    )
                }
                is MixedRunningStepBlock.Repeat -> {
                    val work = WorkoutStepDetail(
                        title = titleForPurpose(block.work.purpose, block.work.stepIntent),
                        purpose = block.work.purpose,
                        zoneBadge = zoneBadgeForPurpose(block.work.purpose, block.work.stepIntent),
                        metricLine = formatDurationAndMeters(
                            block.work.seconds,
                            block.work.meters,
                            block.work.distanceEquivalentMeters
                        ),
                        effortLine = displayEffort(block.work.effort, block.work.targetPaceMinSecPerKm, block.work.targetPaceMaxSecPerKm),
                        accentColor = accentForPurpose(block.work.purpose),
                        durationMinutes = estimateDurationMin(
                            block.work.seconds,
                            block.work.meters ?: block.work.distanceEquivalentMeters
                        ),
                        intensityFraction = intensityForPurpose(block.work.purpose, block.work.stepIntent),
                        testTag = "workout-mixed-repeat-work-$index"
                    )
                    val recover = block.recovery?.let { rec ->
                        WorkoutStepDetail(
                            title = titleForPurpose(rec.purpose, rec.stepIntent),
                            purpose = rec.purpose,
                            zoneBadge = zoneBadgeForPurpose(rec.purpose, rec.stepIntent),
                            metricLine = formatDurationAndMeters(
                                rec.seconds,
                                rec.meters,
                                rec.distanceEquivalentMeters
                            ),
                            effortLine = displayEffort(rec.effort, rec.targetPaceMinSecPerKm, rec.targetPaceMaxSecPerKm),
                            accentColor = accentForPurpose(rec.purpose),
                            durationMinutes = estimateDurationMin(
                                rec.seconds,
                                rec.meters ?: rec.distanceEquivalentMeters
                            ),
                            intensityFraction = intensityForPurpose(rec.purpose, rec.stepIntent),
                            testTag = "workout-mixed-repeat-recovery-$index"
                        )
                    }
                    WorkoutStepItem.RepeatGroup(
                        reps = block.times,
                        title = "${block.times} × Interval Set",
                        badge = "${block.times} REPS",
                        children = listOfNotNull(work, recover),
                        recoveryAfterLast = block.recoveryAfterLast,
                        testTag = "workout-mixed-repeat-$index",
                        countTag = "workout-mixed-repeat-count-$index"
                    )
                }
            }
        }
    }

    val blocks = segments?.let(RunningStepGrouping::of).orEmpty()
    if (blocks.isNotEmpty()) {
        val totalMeters = segments?.sumOf { it.meters }?.takeIf { it > 0 } ?: 1
        fun segMinutes(meters: Int): Float =
            ((session.minutes.coerceAtLeast(1) * meters.toFloat()) / totalMeters.toFloat()).coerceAtLeast(1f)

        return blocks.mapIndexed { index, block ->
            when (block) {
                is RunningStepBlock.Single -> {
                    val seg = block.segment
                    WorkoutStepItem.SingleStep(
                        step = WorkoutStepDetail(
                            title = titleForPurpose(seg.purpose, seg.stepIntent),
                            purpose = seg.purpose,
                            zoneBadge = zoneBadgeForPurpose(seg.purpose, seg.stepIntent),
                            metricLine = runningStepDisplayKmLabel(seg.meters),
                            effortLine = displayEffort(seg.effort, seg.targetPaceMinSecPerKm, seg.targetPaceMaxSecPerKm),
                            accentColor = accentForPurpose(seg.purpose),
                            durationMinutes = segMinutes(seg.meters),
                            intensityFraction = intensityForPurpose(seg.purpose, seg.stepIntent),
                            testTag = "workout-step-single-$index"
                        ),
                        testTag = "workout-step-single-$index"
                    )
                }
                is RunningStepBlock.Repeat -> {
                    val work = WorkoutStepDetail(
                        title = titleForPurpose(block.work.purpose, block.work.stepIntent),
                        purpose = block.work.purpose,
                        zoneBadge = zoneBadgeForPurpose(block.work.purpose, block.work.stepIntent),
                        metricLine = runningStepDisplayKmLabel(block.work.meters),
                        effortLine = displayEffort(block.work.effort, block.work.targetPaceMinSecPerKm, block.work.targetPaceMaxSecPerKm),
                        accentColor = accentForPurpose(block.work.purpose),
                        durationMinutes = segMinutes(block.work.meters),
                        intensityFraction = intensityForPurpose(block.work.purpose, block.work.stepIntent),
                        testTag = "workout-repeat-work-$index"
                    )
                    val recover = block.recovery?.let { rec ->
                        WorkoutStepDetail(
                            title = "Recover",
                            purpose = rec.purpose,
                            zoneBadge = zoneBadgeForPurpose(rec.purpose),
                            metricLine = runningStepDisplayKmLabel(rec.meters),
                        effortLine = displayEffort(rec.effort, rec.targetPaceMinSecPerKm, rec.targetPaceMaxSecPerKm),
                            accentColor = accentForPurpose(rec.purpose),
                            durationMinutes = segMinutes(rec.meters),
                            intensityFraction = intensityForPurpose(rec.purpose),
                            testTag = "workout-repeat-recovery-$index"
                        )
                    }
                    WorkoutStepItem.RepeatGroup(
                        reps = block.times,
                        title = "${block.times} × Interval Set",
                        badge = "${block.times} REPS",
                        children = listOfNotNull(work, recover),
                        recoveryAfterLast = block.recoveryAfterLast,
                        testTag = "workout-repeat-group-$index",
                        countTag = "workout-repeat-count-$index"
                    )
                }
            }
        }
    }

    if (textFallback.isNotEmpty()) {
        val avgMin = (session.minutes.coerceAtLeast(1) / textFallback.size.coerceAtLeast(1).toFloat())
            .coerceAtLeast(1f)
        return textFallback.mapIndexed { index, raw ->
            val lower = raw.lowercase(Locale.ROOT)
            val purpose = when {
                "warm" in lower -> "WARM_UP"
                "cool" in lower -> "COOL_DOWN"
                "recover" in lower -> "RECOVERY"
                session.quality && index in 1 until (textFallback.lastIndex.coerceAtLeast(1)) -> "WORK"
                else -> "EASY"
            }
            WorkoutStepItem.SingleStep(
                step = WorkoutStepDetail(
                    title = titleForPurpose(purpose),
                    purpose = purpose,
                    zoneBadge = zoneBadgeForPurpose(purpose),
                    metricLine = null,
                    effortLine = raw,
                    accentColor = accentForPurpose(purpose),
                    durationMinutes = avgMin,
                    intensityFraction = intensityForPurpose(purpose),
                    testTag = "workout-step-fallback-$index"
                ),
                testTag = "workout-step-fallback-$index"
            )
        }
    }

    return emptyList()
}

/**
 * Art-directed AMOLED Running Step Timeline.
 * Uses a vertical left rail with color-coded indicator dots aligned to each step card.
 */
@Composable
internal fun TracksideRunningStepCards(
    items: List<WorkoutStepItem>,
    selectedStepIndex: Int? = null,
    onSelectStep: (Int) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workout-planned-structure"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "WORKOUT STEPS",
            color = WorkoutDetailPalette.textMuted,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (items.isEmpty()) {
            Text(
                text = "No additional structured steps for this session.",
                color = WorkoutDetailPalette.textSecondary,
                fontSize = 12.sp
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedStepIndex == index
                    val isLast = index == items.lastIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.Top
                    ) {
                        TimelineRailIndicator(
                            dotColor = item.accentColor,
                            selected = isSelected,
                            showTopLine = index > 0,
                            showBottomLine = !isLast
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 0.dp else 10.dp)
                        ) {
                            when (item) {
                                is WorkoutStepItem.SingleStep -> SingleStepTimelineCard(
                                    step = item.step,
                                    selected = isSelected,
                                    onClick = { onSelectStep(index) }
                                )
                                is WorkoutStepItem.RepeatGroup -> RepeatGroupTimelineCard(
                                    group = item,
                                    selected = isSelected,
                                    onClick = { onSelectStep(index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRailIndicator(
    dotColor: Color,
    selected: Boolean,
    showTopLine: Boolean,
    showBottomLine: Boolean
) {
    Box(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(22.dp)
                    .background(if (showTopLine) WorkoutDetailPalette.border else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(1.5.dp)
                    .background(if (showBottomLine) WorkoutDetailPalette.border else Color.Transparent)
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 13.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) dotColor.copy(alpha = 0.24f) else WorkoutDetailPalette.cardRaised)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) dotColor else WorkoutDetailPalette.border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
private fun SingleStepTimelineCard(
    step: WorkoutStepDetail,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(step.testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = WorkoutDetailPalette.card,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) step.accentColor else WorkoutDetailPalette.border
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, top = 11.dp, bottom = 11.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(step.accentColor, step.accentColor.copy(alpha = 0.45f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        color = WorkoutDetailPalette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    ZonePillBadge(text = step.zoneBadge, color = step.accentColor)
                }
                step.metricLine?.let { metric ->
                    Text(
                        text = metric,
                        color = WorkoutDetailPalette.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                step.effortLine?.let { effort ->
                    Text(
                        text = effort,
                        color = if (step.metricLine == null) WorkoutDetailPalette.textSecondary else WorkoutDetailPalette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatGroupTimelineCard(
    group: WorkoutStepItem.RepeatGroup,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(group.testTag)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = WorkoutDetailPalette.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.25.dp,
            color = WorkoutDetailPalette.amber.copy(alpha = if (selected) 1f else 0.72f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(WorkoutDetailPalette.amber, WorkoutDetailPalette.amberDeep)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.title,
                        color = WorkoutDetailPalette.background,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .testTag(group.countTag)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WorkoutDetailPalette.background.copy(alpha = 0.12f))
                            .border(
                                width = 1.dp,
                                color = WorkoutDetailPalette.background.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = group.badge,
                            color = WorkoutDetailPalette.background,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                WorkoutDetailPalette.amber.copy(alpha = 0.14f),
                                WorkoutDetailPalette.card
                            )
                        )
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.children.forEach { child ->
                    RepeatChildStepCard(step = child)
                }
                if (!group.recoveryAfterLast && group.children.any { it.purpose == "RECOVERY" }) {
                    Text(
                        text = "Between repetitions",
                        color = WorkoutDetailPalette.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatChildStepCard(step: WorkoutStepDetail) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(step.testTag),
        color = WorkoutDetailPalette.cardNested,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, WorkoutDetailPalette.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(step.accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        color = WorkoutDetailPalette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    )
                    ZonePillBadge(text = step.zoneBadge, color = step.accentColor)
                }
                val summaryLine = step.metricLine ?: step.effortLine
                summaryLine?.let { line ->
                    Text(
                        text = line,
                        color = WorkoutDetailPalette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (step.metricLine != null && !step.effortLine.isNullOrBlank()) {
                    Text(
                        text = step.effortLine,
                        color = WorkoutDetailPalette.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ZonePillBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Clean km display label used across all athlete-facing running step cards. */
internal fun runningStepDisplayKmLabel(meters: Int): String {
    val km = meters / 1000.0
    return when {
        meters <= 0 -> "0.0 km"
        km < 1.0 && meters % 100 != 0 -> String.format(Locale.US, "%.2f km", km)
        else -> String.format(Locale.US, "%.1f km", km)
    }
}

/** Legacy helper preserved for RunningStepGroupingTest contract checks. */
internal fun runningStepDistanceLabel(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    meters % 100 == 0 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
    meters % 10 == 0 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
    else -> "$meters m"
}
