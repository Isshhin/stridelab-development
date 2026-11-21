package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation of a saved running prescription, not a completed activity.
 * Never reconstruct distance from the original after an accepted time-only review.
 */
@Composable
internal fun TracksidePlannedRunningPreview(
    session: PlannedSession,
    target: Engine15SessionPresentation.Target,
    displayTitle: String,
    coachingReason: String,
    onBack: () -> Unit,
    planStart: LocalDate = session.date,
    onMove: (() -> Unit)? = null,
    onMissed: (() -> Unit)? = null,
    onAdjustDifficulty: ((Int) -> Unit)? = null,
    busy: Boolean = false
) {
    var notesExpanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val label=when {
        target.state==Engine15SessionPresentation.State.ACCEPTED_REST -> "Rest"
        target.state==Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY -> "Time-only alternative"
        session.runningPrescription?.role=="QUALITY" ->
            session.runningPrescription.family ?: "Quality"
        session.quality || session.kind=="quality" ->
            session.runningPrescription?.family ?: "Quality session"
        session.runningPrescription?.role=="LONG" || session.kind=="long" -> "Long run · Easy effort"
        session.runningPrescription?.role=="RECOVERY" -> "Recovery"
        else -> "Easy effort"
    }
    val showOriginalSteps=target.state==Engine15SessionPresentation.State.ORIGINAL ||
        target.state==Engine15SessionPresentation.State.PENDING_REVIEW
    val segments=session.runningPrescription?.segments?.takeIf { showOriginalSteps }
    val mixedSegments=session.runningPrescription?.mixedSegments?.takeIf { showOriginalSteps }
    val estimatedDistance=session.runningPrescription?.estimatedDistanceKm?.takeIf { showOriginalSteps }
    val weekNumber = (ChronoUnit.DAYS.between(planStart,session.date) / 7L + 1L)
        .coerceAtLeast(1L)
    Column(
        modifier=Modifier.fillMaxWidth().testTag("workout-planned-preview"),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically
        ) {
            TextButton(onClick=onBack,
                modifier=Modifier.heightIn(min=48.dp).testTag("workout-planned-back")) {
                Text("‹  Week $weekNumber · Training",color=TracksidePalette.text,
                    fontSize=14.sp,fontWeight=FontWeight.Medium)
            }
            Text("PLANNED",color=TracksidePalette.lime,
                fontSize=10.sp,fontWeight=FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().testTag("workout-planned-summary")) {
            // Decorative translucent training-wave motif; it is not data.
            Canvas(Modifier.matchParentSize()) {
                val w=size.width
                val h=size.height
                for(i in 0..7) {
                    val shift=i*8.dp.toPx()
                    val p=Path().apply {
                        moveTo(w*.48f,h*.62f+shift)
                        cubicTo(w*.75f,h*.60f-shift*.2f,
                            w*.79f,h*.28f+shift,w*1.15f,h*.16f+shift)
                    }
                    drawPath(p,TracksidePalette.lime.copy(alpha=.055f),
                        style=Stroke(width=1.15.dp.toPx()))
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(
                    session.date.format(DateTimeFormatter.ofPattern(
                        "EEE, MMM d, yyyy",Locale.getDefault())).uppercase(Locale.getDefault()),
                    color=TracksidePalette.textMuted,
                    fontSize=11.sp,fontWeight=FontWeight.SemiBold
                )
                Text(displayTitle,color=TracksidePalette.text,
                    fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.testTag("workout-planned-title"))
                Text(
                    when {
                        target.currentDistanceTargetKm!=null ->
                            "$label · "+String.format(Locale.getDefault(),
                                "%.1f km",target.currentDistanceTargetKm)
                        target.state==Engine15SessionPresentation.State.ACCEPTED_REST -> "Reviewed rest"
                        estimatedDistance!=null ->
                            "$label · "+String.format(Locale.getDefault(),"~%.1f km estimated",estimatedDistance)
                        else -> label
                    },
                    color=TracksidePalette.textMuted,fontSize=15.sp,
                    modifier=Modifier.testTag("workout-planned-subtitle")
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(20.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(
                            when {
                                target.state==Engine15SessionPresentation.State.ACCEPTED_REST -> "Rest"
                                target.currentDistanceTargetKm!=null ->
                                    String.format(Locale.getDefault(),
                                        "%.1f km",target.currentDistanceTargetKm)
                                estimatedDistance!=null -> "Mixed targets"
                                else -> "Time-based"
                            },
                            color=TracksidePalette.text,fontSize=24.sp,
                            fontWeight=FontWeight.Bold,
                            modifier=Modifier.testTag("workout-planned-primary-target"))
                        Text(
                            if(target.currentDistanceTargetKm!=null) "Planned distance"
                            else if(estimatedDistance!=null) "Time + distance steps"
                            else "Current target",
                            color=TracksidePalette.textMuted,fontSize=11.sp
                        )
                    }
                    Box(Modifier.width(1.dp).height(36.dp)
                        .background(TracksidePalette.outline))
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(
                            if(target.state==Engine15SessionPresentation.State.ACCEPTED_REST)
                                "Reviewed rest · 0 min"
                            else "Estimated duration · ${target.currentMinutes} min",
                            color=TracksidePalette.text,fontSize=13.sp,
                            fontWeight=FontWeight.SemiBold,
                            modifier=Modifier.testTag("workout-planned-duration"))
                        Text("Planned, not recorded",color=TracksidePalette.textMuted,
                            fontSize=11.sp)
                    }
                }
                if(estimatedDistance!=null) {
                    Text(
                        "Estimated total distance uses planning pace, not a distance target. Follow the timed and measured steps below.",
                        color=TracksidePalette.textMuted,fontSize=11.sp,lineHeight=16.sp
                    )
                }
                if(target.state==Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY) {
                    Text(
                        "The reviewed time allowance is the current target; original distance and intervals are not required.",
                        color=TracksidePalette.textMuted,fontSize=11.sp,lineHeight=16.sp
                    )
                }
            }
        }
        if(onMove!=null || onMissed!=null) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                onMove?.let { action ->
                    OutlinedButton(onClick=action,modifier=Modifier.weight(1f)
                            .heightIn(min=48.dp).testTag("workout-planned-move"),
                        border=BorderStroke(1.dp,TracksidePalette.outline),
                        shape=RoundedCornerShape(13.dp)) {
                        Text("Move workout",color=TracksidePalette.text,fontSize=12.sp)
                    }
                }
                onMissed?.let { action ->
                    OutlinedButton(onClick=action,modifier=Modifier.weight(1f)
                            .heightIn(min=48.dp).testTag("workout-planned-missed"),
                        border=BorderStroke(1.dp,TracksidePalette.outline),
                        shape=RoundedCornerShape(13.dp)) {
                        Text("Missed / skipped",color=TracksidePalette.text,fontSize=12.sp)
                    }
                }
            }
        }
        if (onAdjustDifficulty != null && target.state != Engine15SessionPresentation.State.ACCEPTED_REST) {
            TracksideWorkoutDifficultyCard(
                session = session,
                busy = busy,
                onAdjust = onAdjustDifficulty
            )
        }
        Spacer(Modifier.height(3.dp))
        TracksideRunningStepCards(
            segments = segments,
            textFallback = target.currentSteps,
            mixedSegments = mixedSegments
        )
        Surface(
            modifier=Modifier.fillMaxWidth().testTag("workout-planned-coach-notes"),
            color=TracksidePalette.surfaceRaised,
            contentColor=TracksidePalette.text,
            border=BorderStroke(1.dp,TracksidePalette.outline),
            shape=RoundedCornerShape(15.dp)
        ) {
            Column(Modifier.padding(horizontal=16.dp,vertical=9.dp)) {
                TextButton(
                    onClick={ notesExpanded=!notesExpanded },
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .testTag("workout-planned-notes-toggle")
                ) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("COACH'S NOTES",color=TracksidePalette.text,
                            fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Text(if(notesExpanded)"Hide −" else "Read +",
                            color=TracksidePalette.lime,fontSize=12.sp)
                    }
                }
                if(notesExpanded) {
                    Text(
                        coachingReason.ifBlank {
                            "No additional explanation was saved for this workout."
                        },
                        color=TracksidePalette.textMuted,fontSize=12.sp,
                        lineHeight=18.sp,
                        modifier=Modifier.padding(bottom=10.dp)
                    )
                    target.disclosure?.let {
                        Text(it,color=TracksidePalette.textMuted,fontSize=12.sp,
                            lineHeight=18.sp,modifier=Modifier.padding(bottom=8.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun TracksideWorkoutDifficultyCard(
    session: PlannedSession,
    busy: Boolean,
    onAdjust: (Int) -> Unit
) {
    val modified = session.difficultyStep != 0
    val label = Engine15WorkoutDifficultyAdjuster.labelFor(session.difficultyStep)
    val badgeText = if (modified) {
        val sign = if (session.difficultyStep > 0) "+" else ""
        "$label ($sign${session.difficultyStep})"
    } else label
    val summary = Engine15WorkoutDifficultyAdjuster.changeSummary(session)

    Surface(
        modifier = Modifier.fillMaxWidth().testTag("workout-difficulty-card"),
        color = TracksidePalette.surfaceRaised,
        contentColor = TracksidePalette.text,
        border = BorderStroke(
            1.dp,
            if (modified) TracksidePalette.lime.copy(alpha = 0.55f) else TracksidePalette.outline
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WORKOUT DIFFICULTY",
                    color = TracksidePalette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        badgeText,
                        color = if (modified) TracksidePalette.lime else TracksidePalette.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("workout-difficulty-badge")
                    )
                    if (modified) {
                        TextButton(
                            onClick = { onAdjust(0) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 32.dp).testTag("workout-difficulty-reset")
                        ) {
                            Text("Reset", color = TracksidePalette.lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjust(-1) },
                    enabled = !busy && session.difficultyStep > -2,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("workout-difficulty-easier"),
                    border = BorderStroke(1.dp, TracksidePalette.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("− Easier", color = TracksidePalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { onAdjust(1) },
                    enabled = !busy && session.difficultyStep < 2,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("workout-difficulty-harder"),
                    border = BorderStroke(1.dp, TracksidePalette.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("+ Harder", color = TracksidePalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            summary?.let {
                Text(
                    it,
                    color = TracksidePalette.textMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.testTag("workout-difficulty-summary")
                )
            }
        }
    }
}
