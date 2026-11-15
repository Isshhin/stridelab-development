package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBack: () -> Unit
) {
    var notesExpanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val label=when {
        target.state==Engine15SessionPresentation.State.ACCEPTED_REST -> "Rest"
        target.state==Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY -> "Time-only alternative"
        session.runningPrescription?.role=="QUALITY" ->
            session.runningPrescription.family ?: "Quality"
        session.runningPrescription?.role=="LONG" || session.kind=="long" -> "Long run · Easy effort"
        session.runningPrescription?.role=="RECOVERY" -> "Recovery"
        else -> "Easy effort"
    }
    val segments=session.runningPrescription?.segments?.takeIf {
        target.state==Engine15SessionPresentation.State.ORIGINAL ||
            target.state==Engine15SessionPresentation.State.PENDING_REVIEW
    }
    Column(
        modifier=Modifier.fillMaxWidth().testTag("workout-planned-preview"),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=onBack,
                modifier=Modifier.heightIn(min=48.dp).testTag("workout-planned-back")) {
                Text("‹ Training",color=TracksidePalette.lime,fontSize=13.sp)
            }
            Text(session.date.format(DateTimeFormatter.ofPattern("EEE, MMM d",Locale.getDefault())),
                color=TracksidePalette.textMuted,fontSize=12.sp)
        }
        Surface(
            modifier=Modifier.fillMaxWidth().testTag("workout-planned-summary"),
            color=Color(0xFF152018),
            contentColor=TracksidePalette.text,
            border=BorderStroke(1.dp,Color(0xFF354536)),
            shape=RoundedCornerShape(17.dp)
        ) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("YOUR PLANNED RUN",color=TracksidePalette.lime,fontSize=11.sp,
                    fontWeight=FontWeight.Bold)
                Text(displayTitle,color=TracksidePalette.text,
                    style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Row(verticalAlignment=Alignment.Bottom,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    val distance=target.currentDistanceTargetKm
                    Text(
                        when {
                            target.state==Engine15SessionPresentation.State.ACCEPTED_REST ->
                                "Rest"
                            distance!=null ->
                                String.format(Locale.getDefault(),"%.1f km",distance)
                            else -> "Time-based"
                        },
                        fontSize=31.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold,
                        color=TracksidePalette.lime,
                        modifier=Modifier.testTag("workout-planned-primary-target")
                    )
                }
                Text(label,color=TracksidePalette.text,
                    fontSize=13.sp,fontWeight=FontWeight.Medium)
                Text(
                    if(target.state==Engine15SessionPresentation.State.ACCEPTED_REST)
                        "Reviewed rest · 0 min"
                    else "Estimated duration · ${target.currentMinutes} min",
                    color=TracksidePalette.text,fontSize=13.sp,
                    modifier=Modifier.testTag("workout-planned-duration")
                )
                Text(
                    if(target.state==Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY)
                        "Only the reviewed time allowance is current. The original distance and intervals are not targets."
                    else "Planned prescription, not a recorded run. Duration is an estimate, not a pace requirement.",
                    color=TracksidePalette.textMuted,fontSize=11.sp,lineHeight=16.sp
                )
            }
        }
        TracksideRunningStepCards(
            segments = segments,
            textFallback = target.currentSteps
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
