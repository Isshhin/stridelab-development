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
    textFallback: List<String>,
    mixedSegments: List<RunningStepV2>? = null
) {
    val blocks = segments?.let(RunningStepGrouping::of).orEmpty()
    val compact = blocks.size == 1 && blocks.single() is RunningStepBlock.Single
    Column(
        Modifier.fillMaxWidth().testTag("workout-planned-structure"),
        verticalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 11.dp)
    ) {
        if(mixedSegments!=null) {
            Text("WORKOUT STEPS",color=TracksidePalette.text,
                fontSize=13.sp,fontWeight=FontWeight.Bold)
            // Timed steps are displayed from their persisted executable extent.
            // No conversion to a synthetic distance target is performed here.
            MixedRunningStepGrouping.of(mixedSegments).forEachIndexed { index,block ->
                when(block) {
                    is MixedRunningStepBlock.Single ->
                        MixedRunningStepTile(block.segment,"workout-mixed-step-"+index)
                    is MixedRunningStepBlock.Repeat -> Surface(
                        modifier=Modifier.fillMaxWidth().testTag("workout-mixed-repeat-"+index),
                        color=Color(0xFF111312),shape=RoundedCornerShape(16.dp),
                        border=BorderStroke(1.dp,Color(0xFF4D5350))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            Text(block.times.toString()+" Times",
                                color=TracksidePalette.text,fontSize=19.sp,
                                fontWeight=FontWeight.Bold)
                            MixedRunningStepTile(block.work,"workout-mixed-repeat-work-"+index)
                            block.recovery?.let { recovery ->
                                MixedRunningStepTile(recovery,"workout-mixed-repeat-recovery-"+index)
                                if(!block.recoveryAfterLast)Text("Recovery between repetitions",
                                    color=TracksidePalette.textMuted,fontSize=10.sp)
                            }
                        }
                    }
                }
            }
        } else if(compact) {
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
    val glyph=when(segment.purpose) {
        "WARM_UP" -> "W"
        "WORK" -> "R"
        "RECOVERY" -> "↺"
        "TRANSITION" -> "→"
        "COOL_DOWN" -> "C"
        else -> "E"
    }
    val name=when(segment.purpose) {
        "WARM_UP" -> "Warm-up"
        "WORK" -> "Run"
        "RECOVERY" -> "Recover"
        "TRANSITION" -> "Transition"
        "COOL_DOWN" -> "Cool-down"
        else -> "Easy running"
    }
    Surface(
        modifier=Modifier.fillMaxWidth().testTag(tag),
        color=Color(0xFF242725),
        shape=RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            Box(
                Modifier.padding(start=12.dp).size(36.dp)
                    .background(Color(0xFF303531),RoundedCornerShape(9.dp)),
                contentAlignment=Alignment.Center
            ) {
                Text(glyph,color=TracksidePalette.textMuted,
                    fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f)
                    .padding(horizontal=13.dp,vertical=2.dp),
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

/** Version-2 mixed extent tile; duration and distance are different targets. */
@Composable
private fun MixedRunningStepTile(step:RunningStepV2,tag:String) {
    val title=when(step.purpose) {
        "WARM_UP"->"Warm-up"
        "WORK"->"Run"
        "RECOVERY"->"Recover"
        "TRANSITION"->"Transition"
        "COOL_DOWN"->"Cool-down"
        else->"Easy running"
    }
    val extent=step.meters?.let(::runningStepDistanceLabel) ?: run {
        val seconds=checkNotNull(step.seconds)
        if(seconds%60==0) (seconds/60).toString()+" min"
        else if(seconds>60) (seconds/60).toString()+" min "+(seconds%60).toString()+" s"
        else seconds.toString()+" s"
    }
    Surface(modifier=Modifier.fillMaxWidth().testTag(tag),
        color=Color(0xFF242725),shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=13.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,color=TracksidePalette.text,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text(extent,color=TracksidePalette.lime,fontSize=13.sp,
                    fontWeight=FontWeight.Bold)
            }
            Text(step.effort,color=TracksidePalette.textMuted,fontSize=11.sp,lineHeight=16.sp)
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
