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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * One dynamic representation of all saved Engine 1.5 running step sequences:
 * easy runs stay compact; multiple steps become individually legible cards;
 * identical quality intervals are nested in an explicit repeated group.
 *
 * Repetition is visual compression, not a change to the executable prescription.
 */
@Composable
internal fun TracksideRunningStepCards(
    segments: List<RunningSegment>?,
    textFallback: List<String>
) {
    val blocks = segments?.let(RunningStepGrouping::of).orEmpty()
    val compact = blocks.size == 1 && blocks.single() is RunningStepBlock.Single
    Column(
        Modifier.fillMaxWidth().testTag("workout-planned-structure"),
        verticalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 11.dp)
    ) {
        if(compact) {
            Surface(
                modifier=Modifier.fillMaxWidth(),
                shape=RoundedCornerShape(15.dp),
                color=TracksidePalette.surfaceRaised,
                border=BorderStroke(1.dp,TracksidePalette.outline)
            ) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(11.dp)) {
                    Text("WORKOUT STRUCTURE",color=TracksidePalette.text,
                        fontSize=12.sp,fontWeight=FontWeight.Bold)
                    RunningStepTile(
                        segment=(blocks.single() as RunningStepBlock.Single).segment,
                        tag="workout-step-single-0"
                    )
                }
            }
        } else {
            Text("WORKOUT STEPS",color=TracksidePalette.text,
                fontSize=13.sp,fontWeight=FontWeight.Bold)
            if(blocks.isNotEmpty()) {
                blocks.forEachIndexed { index, block ->
                    when(block) {
                        is RunningStepBlock.Single ->
                            RunningStepTile(block.segment,"workout-step-single-$index")
                        is RunningStepBlock.Repeat ->
                            Surface(
                                modifier=Modifier.fillMaxWidth()
                                    .testTag("workout-repeat-group-$index"),
                                color=Color(0xFF111312),
                                shape=RoundedCornerShape(16.dp),
                                border=BorderStroke(1.dp,Color(0xFF4D5350))
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement=Arrangement.spacedBy(9.dp)
                                ) {
                                    Text("${block.times} Times",
                                        color=TracksidePalette.text,
                                        fontSize=19.sp,
                                        fontWeight=FontWeight.Bold,
                                        modifier=Modifier.testTag("workout-repeat-count-$index"))
                                    RunningStepTile(
                                        block.work,
                                        "workout-repeat-work-$index"
                                    )
                                    block.recovery?.let { rest ->
                                        RunningStepTile(
                                            rest,
                                            "workout-repeat-recovery-$index",
                                            additionalLabel=if(block.recoveryAfterLast)
                                                null else "Between repetitions"
                                        )
                                    }
                                }
                            }
                    }
                }
            } else if(textFallback.isNotEmpty()) {
                // For legacy or reviewed time-only prescriptions, display
                // only the current saved instructions. Never resurrect original
                // structured intervals after an accepted time-only reduction.
                Surface(
                    modifier=Modifier.fillMaxWidth().testTag("workout-step-fallback"),
                    color=TracksidePalette.surfaceRaised,
                    shape=RoundedCornerShape(13.dp),
                    border=BorderStroke(1.dp,TracksidePalette.outline)
                ) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        textFallback.forEach { step ->
                            Text(step,color=TracksidePalette.text,
                                fontSize=12.sp,lineHeight=18.sp)
                        }
                    }
                }
            } else {
                Text("No additional structured steps for this session.",
                    color=TracksidePalette.textMuted,
                    fontSize=12.sp)
            }
        }
    }
}

@Composable
private fun RunningStepTile(
    segment: RunningSegment,
    tag: String,
    additionalLabel: String? = null
) {
    val accent=when(segment.purpose) {
        "WARM_UP" -> Color(0xFFFF9980)
        "WORK" -> TracksidePalette.lime
        "RECOVERY" -> Color(0xFF9EA8A3)
        "COOL_DOWN" -> Color(0xFF91D6B5)
        else -> TracksidePalette.lime
    }
    val name=when(segment.purpose) {
        "WARM_UP" -> "Warm-up"
        "WORK" -> "Run"
        "RECOVERY" -> "Recover"
        "COOL_DOWN" -> "Cool-down"
        else -> "Easy running"
    }
    Surface(
        modifier=Modifier.fillMaxWidth().testTag(tag),
        color=Color(0xFF242725),
        shape=RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(
                Modifier.fillMaxHeight().width(5.dp)
                    .background(accent)
            )
            Column(
                Modifier.weight(1f)
                    .padding(horizontal=13.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(name,color=TracksidePalette.text,
                        fontSize=14.sp,fontWeight=FontWeight.Bold)
                    Text(runningStepDistanceLabel(segment.meters),
                        color=TracksidePalette.lime,
                        fontSize=13.sp,fontWeight=FontWeight.Bold)
                }
                Text(segment.effort,color=TracksidePalette.textMuted,
                    fontSize=11.sp,lineHeight=16.sp)
                additionalLabel?.let {
                    Text(it,color=TracksidePalette.textMuted,
                        fontSize=10.sp,fontWeight=FontWeight.Medium)
                }
            }
        }
    }
}

/** Exact saved metres when a short km label would round the prescription. */
internal fun runningStepDistanceLabel(meters: Int): String = when {
    meters < 1000 -> "$meters m"
    meters % 100 == 0 -> String.format(Locale.US,"%.1f km",meters / 1000.0)
    meters % 10 == 0 -> String.format(Locale.US,"%.2f km",meters / 1000.0)
    else -> "$meters m"
}
