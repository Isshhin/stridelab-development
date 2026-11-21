package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Art-directed AMOLED Running Step Cards.
 * Always displays distances in kilometers (km) in the athlete UI, with semantic
 * intensity color accents (Easy Teal, Work Lime/Amber, Recovery Cyan).
 */
@Composable
internal fun TracksideRunningStepCards(
    segments: List<RunningSegment>?,
    textFallback: List<String>,
    mixedSegments: List<RunningStepV2>? = null
) {
    val blocks = segments?.let(RunningStepGrouping::of).orEmpty()
    val compact = blocks.size == 1 && blocks.single() is RunningStepBlock.Single
    Column(
        Modifier.fillMaxWidth().testTag("workout-planned-structure"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        if (mixedSegments != null) {
            Text(
                "WORKOUT STEPS",
                color = StrideLabVisualPalette.muted,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold
            )
            MixedRunningStepGrouping.of(mixedSegments).forEachIndexed { index, block ->
                when (block) {
                    is MixedRunningStepBlock.Single ->
                        MixedRunningStepTile(block.segment, "workout-mixed-step-$index")
                    is MixedRunningStepBlock.Repeat -> Surface(
                        modifier = Modifier.fillMaxWidth().testTag("workout-mixed-repeat-$index"),
                        color = StrideLabVisualPalette.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, StrideLabVisualPalette.moderateAmber.copy(alpha = 0.45f))
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${block.times} × Interval Set",
                                    color = StrideLabVisualPalette.ink,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StrideLabVisualPalette.moderateAmber.copy(alpha = 0.16f))
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${block.times} REPS",
                                        color = StrideLabVisualPalette.moderateAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            MixedRunningStepTile(block.work, "workout-mixed-repeat-work-$index")
                            block.recovery?.let { recovery ->
                                MixedRunningStepTile(recovery, "workout-mixed-repeat-recovery-$index")
                                if (!block.recoveryAfterLast) Text(
                                    "Recovery jog between repetitions",
                                    color = StrideLabVisualPalette.secondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } else if (compact) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = StrideLabVisualPalette.surface,
                border = BorderStroke(1.dp, StrideLabVisualPalette.border)
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "WORKOUT STRUCTURE",
                        color = StrideLabVisualPalette.muted,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RunningStepTile(
                        segment = (blocks.single() as RunningStepBlock.Single).segment,
                        tag = "workout-step-single-0"
                    )
                }
            }
        } else {
            Text(
                "WORKOUT STEPS",
                color = StrideLabVisualPalette.muted,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold
            )
            if (blocks.isNotEmpty()) {
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is RunningStepBlock.Single ->
                            RunningStepTile(block.segment, "workout-step-single-$index")
                        is RunningStepBlock.Repeat ->
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("workout-repeat-group-$index"),
                                color = StrideLabVisualPalette.surface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, StrideLabVisualPalette.moderateAmber.copy(alpha = 0.45f))
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "${block.times} Times",
                                        color = StrideLabVisualPalette.ink,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.testTag("workout-repeat-count-$index")
                                    )
                                    RunningStepTile(
                                        block.work,
                                        "workout-repeat-work-$index"
                                    )
                                    block.recovery?.let { rest ->
                                        RunningStepTile(
                                            rest,
                                            "workout-repeat-recovery-$index",
                                            additionalLabel = if (block.recoveryAfterLast)
                                                null else "Between repetitions"
                                        )
                                    }
                                }
                            }
                    }
                }
            } else if (textFallback.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("workout-step-fallback"),
                    color = StrideLabVisualPalette.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        textFallback.forEach { step ->
                            Text(
                                step,
                                color = StrideLabVisualPalette.ink,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No additional structured steps for this session.",
                    color = StrideLabVisualPalette.secondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun stepPurposeAccent(purpose: String): Color = when (purpose) {
    "WORK" -> StrideLabVisualPalette.moderateAmber
    "RECOVERY" -> StrideLabVisualPalette.recoveryCyan
    "WARM_UP", "COOL_DOWN" -> StrideLabVisualPalette.easyTeal
    else -> StrideLabVisualPalette.lime
}

@Composable
private fun RunningStepTile(
    segment: RunningSegment,
    tag: String,
    additionalLabel: String? = null
) {
    val accent = stepPurposeAccent(segment.purpose)
    val glyph = when (segment.purpose) {
        "WARM_UP" -> "W"
        "WORK" -> "R"
        "RECOVERY" -> "↺"
        "TRANSITION" -> "→"
        "COOL_DOWN" -> "C"
        else -> "E"
    }
    val name = when (segment.purpose) {
        "WARM_UP" -> "Warm-up"
        "WORK" -> "Run"
        "RECOVERY" -> "Recover"
        "TRANSITION" -> "Transition"
        "COOL_DOWN" -> "Cool-down"
        else -> "Easy running"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        color = StrideLabVisualPalette.surfaceRaised,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    glyph,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 13.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        color = StrideLabVisualPalette.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        runningStepDisplayKmLabel(segment.meters),
                        color = accent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    segment.effort,
                    color = StrideLabVisualPalette.secondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
                additionalLabel?.let {
                    Text(
                        it,
                        color = StrideLabVisualPalette.muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Version-2 mixed extent tile; always formats distance equivalents in km. */
@Composable
private fun MixedRunningStepTile(step: RunningStepV2, tag: String) {
    val accent = stepPurposeAccent(step.purpose)
    val title = when (step.purpose) {
        "WARM_UP" -> "Warm-up"
        "WORK" -> "Work"
        "RECOVERY" -> "Recover"
        "TRANSITION" -> "Transition"
        "COOL_DOWN" -> "Cool-down"
        else -> "Easy running"
    }
    val extent = step.meters?.let(::runningStepDisplayKmLabel) ?: run {
        val seconds = checkNotNull(step.seconds)
        val timeStr = if (seconds % 60 == 0) "${seconds / 60} min"
        else if (seconds > 60) "${seconds / 60} min ${seconds % 60} s"
        else "$seconds s"
        val kmEquiv = step.distanceEquivalentMeters?.takeIf { it > 0 }?.let {
            " · ${runningStepDisplayKmLabel(it)}"
        }.orEmpty()
        timeStr + kmEquiv
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        color = StrideLabVisualPalette.surfaceRaised,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = StrideLabVisualPalette.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    extent,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                step.effort,
                color = StrideLabVisualPalette.secondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
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
