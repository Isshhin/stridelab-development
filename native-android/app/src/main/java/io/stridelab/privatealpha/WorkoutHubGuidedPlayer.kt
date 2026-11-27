package io.stridelab.privatealpha

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class GuidedPhase { PREP, WORK, REST, COMPLETE }

private data class GuidedSegment(
    val phase: GuidedPhase,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val cue: String
)

private fun buildGuidedSegments(workout: CuratedWorkout): List<GuidedSegment> {
    val segments = mutableListOf<GuidedSegment>()
    val first = workout.steps.firstOrNull()
    if (first != null) {
        segments += GuidedSegment(
            phase = GuidedPhase.PREP,
            exerciseName = first.name,
            setNumber = 1,
            totalSets = first.sets,
            reps = first.reps,
            durationSeconds = 5,
            cue = "Get ready: ${first.cue.ifBlank { workout.targetFocus }}"
        )
    }
    workout.steps.forEachIndexed { stepIndex, step ->
        val sets = step.sets.coerceAtLeast(1)
        val isLastStep = stepIndex == workout.steps.lastIndex
        for (set in 1..sets) {
            val isLastSet = isLastStep && set == sets
            segments += GuidedSegment(
                phase = GuidedPhase.WORK,
                exerciseName = step.name,
                setNumber = set,
                totalSets = sets,
                reps = step.reps,
                durationSeconds = step.holdSeconds ?: step.workSeconds,
                cue = step.cue
            )
            if (step.restSeconds > 0 && !isLastSet) {
                segments += GuidedSegment(
                    phase = GuidedPhase.REST,
                    exerciseName = step.name,
                    setNumber = set,
                    totalSets = sets,
                    reps = null,
                    durationSeconds = step.restSeconds,
                    cue = if (set < sets) "Next: ${step.name} (Set ${set + 1} of $sets)"
                    else "Next: ${workout.steps[stepIndex + 1].name}"
                )
            }
        }
    }
    return segments
}

@Composable
internal fun WorkoutHubGuidedPlayer(
    workout: CuratedWorkout,
    owner: String,
    access: FreeformAccess,
    onCompleted: (FreeformWorkout) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val segments = remember(workout.id) { buildGuidedSegments(workout) }

    var index by remember(workout.id) { mutableIntStateOf(0) }
    var paused by remember(workout.id) { mutableStateOf(false) }
    var saving by remember(workout.id) { mutableStateOf(false) }
    var audioEnabled by remember(owner) {
        mutableStateOf(WorkoutHubPreferences.audioCuesEnabled(context, owner))
    }
    val startedAtMs = remember(workout.id) { System.currentTimeMillis() }
    var elapsedWorkSeconds by remember(workout.id) { mutableLongStateOf(0L) }

    val current = segments.getOrNull(index)
    val isComplete = current == null
    var remainingSeconds by remember(index, workout.id) {
        mutableIntStateOf(current?.durationSeconds ?: 0)
    }
    var segmentTotalSeconds by remember(index, workout.id) {
        mutableIntStateOf(current?.durationSeconds ?: 1)
    }

    val toneGenerator = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { toneGenerator?.release() } }
    }

    fun playTone(tone: Int, durationMs: Int = 120) {
        if (!audioEnabled) return
        runCatching { toneGenerator?.startTone(tone, durationMs) }
    }

    LaunchedEffect(index, paused, isComplete) {
        if (current == null) return@LaunchedEffect
        val timed = current.durationSeconds != null
        while (!paused && (!timed || remainingSeconds > 0)) {
            delay(1000L)
            if (paused) break
            elapsedWorkSeconds += 1L
            if (timed) {
                remainingSeconds -= 1
                if (remainingSeconds in 1..3) {
                    playTone(ToneGenerator.TONE_PROP_BEEP, 80)
                }
                if (remainingSeconds <= 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playTone(ToneGenerator.TONE_PROP_ACK, 180)
                    if (index < segments.lastIndex) {
                        index += 1
                    } else {
                        index = segments.size
                    }
                    break
                }
            }
        }
    }

    val accent = when (current?.phase) {
        GuidedPhase.PREP -> StrideLabSurfaceTokens.amber
        GuidedPhase.WORK -> workout.category.accentColor
        GuidedPhase.REST -> StrideLabVisualPalette.easyTeal
        GuidedPhase.COMPLETE, null -> StrideLabSurfaceTokens.lime
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StrideLabSurfaceTokens.canvas)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("workout-guided-player")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = StrideLabSurfaceTokens.ink
                )
            }
            Text(
                text = workout.title,
                color = StrideLabSurfaceTokens.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Surface(
                onClick = {
                    val next = !audioEnabled
                    audioEnabled = next
                    WorkoutHubPreferences.setAudioCuesEnabled(context, owner, next)
                },
                shape = RoundedCornerShape(999.dp),
                color = StrideLabSurfaceTokens.surfaceSubtle,
                border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
            ) {
                Text(
                    text = if (audioEnabled) "Audio On" else "Muted",
                    color = if (audioEnabled) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isComplete) {
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = StrideLabSurfaceTokens.surfaceElevated,
                border = BorderStroke(1.dp, StrideLabSurfaceTokens.lime.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SESSION COMPLETE",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = workout.title,
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    val loggedMinutes = ((elapsedWorkSeconds / 60L).toInt()).coerceAtLeast(1)
                    val totalSetsCompleted = workout.steps.sumOf { it.sets }
                    Text(
                        text = "$loggedMinutes min elapsed · $totalSetsCompleted sets · ${workout.steps.size} exercises",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    StrideLabMetricBadge(
                        label = "${workout.category.label} · ${workout.intensity.label} intensity",
                        accentColor = workout.category.accentColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val loggedMinutes = ((elapsedWorkSeconds / 60L).toInt()).coerceAtLeast(1)
                                val draft = workout.toGuidedCompletionDraft(
                                    owner = owner,
                                    date = LocalDate.now(),
                                    now = startedAtMs,
                                    elapsedMinutes = loggedMinutes
                                )
                                val created = access.start(draft)
                                val completed = access.finish(
                                    created.copy(modifiedAt = maxOf(now, created.modifiedAt + 1))
                                )
                                saving = false
                                onCompleted(completed)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StrideLabSurfaceTokens.lime,
                            contentColor = StrideLabSurfaceTokens.onLime
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("guided-save-complete")
                    ) {
                        Text(
                            text = if (saving) "Saving..." else "Save & Finish",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        } else {
            val seg = current!!
            Text(
                text = when (seg.phase) {
                    GuidedPhase.PREP -> "PREPARE"
                    GuidedPhase.WORK -> "WORK · SET ${seg.setNumber} OF ${seg.totalSets}"
                    GuidedPhase.REST -> "REST"
                    GuidedPhase.COMPLETE -> "DONE"
                },
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = seg.exerciseName,
                color = StrideLabSurfaceTokens.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (seg.cue.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = seg.cue,
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (seg.phase == GuidedPhase.PREP || seg.phase == GuidedPhase.WORK) {
                Spacer(Modifier.height(10.dp))
                val pattern = remember(seg.exerciseName, workout.category) {
                    GuidedMotionClassifier.classify(seg.exerciseName, workout.category)
                }
                val reducedMotion = remember(context) { isSystemReducedMotion(context) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StrideLabSurfaceTokens.surfaceElevated,
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("guided-motion-demo")
                ) {
                    WorkoutGuidedMotionCanvas(
                        pattern = pattern,
                        accentColor = accent,
                        paused = paused,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                val progress = if (seg.durationSeconds != null && segmentTotalSeconds > 0) {
                    remainingSeconds.toFloat() / segmentTotalSeconds.toFloat()
                } else 1f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = StrideLabSurfaceTokens.surfaceSubtle,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (seg.durationSeconds != null) {
                        val mins = remainingSeconds / 60
                        val secs = remainingSeconds % 60
                        Text(
                            text = if (mins > 0) String.format("%d:%02d", mins, secs) else "${secs}s",
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Text(
                            text = "${seg.reps ?: 10}",
                            color = StrideLabSurfaceTokens.ink,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "REPS",
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            val nextSeg = segments.getOrNull(index + 1)
            if (nextSeg != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = StrideLabSurfaceTokens.surfaceElevated,
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "UP NEXT",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (nextSeg.phase == GuidedPhase.REST) "Rest ${nextSeg.durationSeconds ?: 0}s"
                                else "${nextSeg.exerciseName} (Set ${nextSeg.setNumber}/${nextSeg.totalSets})",
                                color = StrideLabSurfaceTokens.ink,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${index + 1} / ${segments.size}",
                            color = StrideLabSurfaceTokens.inkMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (seg.durationSeconds != null) {
                    OutlinedButton(
                        onClick = {
                            remainingSeconds += 15
                            segmentTotalSeconds += 15
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("+15s", color = StrideLabSurfaceTokens.ink, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { paused = !paused },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = StrideLabSurfaceTokens.onLime
                        ),
                        modifier = Modifier.weight(1.4f).height(48.dp)
                    ) {
                        Text(if (paused) "Resume" else "Pause", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playTone(ToneGenerator.TONE_PROP_ACK, 120)
                            index = if (index < segments.lastIndex) index + 1 else segments.size
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = StrideLabSurfaceTokens.onLime
                        ),
                        modifier = Modifier.weight(1.6f).height(48.dp)
                    ) {
                        Text("Done Set", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        index = if (index < segments.lastIndex) index + 1 else segments.size
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Skip", color = StrideLabSurfaceTokens.inkMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
