package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Figma approved 736:839 / source 734:125.
 * The missed decision + review flow never automatically applies a proposed
 * schedule adjustment. The existing engine's mutations are only called after
 * an explicit review and confirmation, with current source version checks.
 */
@Composable
internal fun TracksideParityMissedWorkoutFlow(
    plan: PlanSnapshot?,
    session: PlannedSession?,
    otherPlans: List<PlanSnapshot>,
    busy: Boolean,
    actions: PlanActions,
    onBack: () -> Unit,
    onBuildDraft: () -> Unit
) {
    if (plan == null || session == null || session !in plan.sessions) {
        TracksideParityUnavailableMissed(onBack)
        return
    }

    val canEdit = Engine1Engines.canManage(plan) &&
        plan.status in setOf("approved", "paused") &&
        session.status in setOf("planned", "in_progress") &&
        session.log == null && session.linkedActivityId == null
    var decision by rememberSaveable(plan.id, session.id) { mutableStateOf("adjust") }
    var reason by rememberSaveable(plan.id, session.id) { mutableStateOf("Time") }
    var choosingReason by remember { mutableStateOf(false) }
    var choosingDestination by remember { mutableStateOf(false) }
    var proposal by remember(plan.id, plan.revision, session.id) {
        mutableStateOf<TracksideChangeProposal?>(null)
    }

    val currentProposal = proposal
    if (currentProposal != null) {
        TracksideParityReviewChanges(
            plan = plan, proposal = currentProposal, otherPlans = otherPlans,
            busy = busy,
            onBack = { proposal = null },
            onRegenerateDraft = onBuildDraft,
            onApply = { selected ->
                if (canEdit) {
                    when (selected.kind) {
                        TracksideChangeKind.SKIP ->
                            actions.missed(plan.id, plan.revision, session.id, selected.reason, true)
                        TracksideChangeKind.ADJUST ->
                            actions.missed(plan.id, plan.revision, session.id, selected.reason, false)
                        TracksideChangeKind.MOVE ->
                            selected.destination?.let {
                                actions.move(plan.id, plan.revision, session.id, it)
                            }
                    }
                    onBack()
                }
            }
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF010305))
        .testTag("missed-workout-screen")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(bottom = 175.dp)
        ) {
            TracksideParityPlanFlowHeader("Missed workout", onBack)
            Spacer(Modifier.height(34.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 19.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text("PLAN / MISSED WORKOUT", fontSize = 10.sp,
                    color = TracksidePalette.red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(21.dp))
                Text("What happens to\nthis workout?", fontSize = 23.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Choose how to handle the missed session.",
                    fontSize = 12.sp, color = Color(0xFFB7C5CF))
                Spacer(Modifier.height(27.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(83.dp)
                        .testTag("missed-session-context"),
                    shape = RoundedCornerShape(14.dp), color = Color(0xFF102735),
                    border = BorderStroke(1.dp, Color(0xFF547588))
                ) {
                    Row(Modifier.fillMaxWidth().padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(width = 46.dp, height = 49.dp)
                                .background(Color(0xFF492838), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                painterResource(R.drawable.trackside_figma_run), null,
                                modifier = Modifier.size(27.dp),
                                tint = Color(0xFFFF637E)
                            )
                        }
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(session.effectiveTitle, fontSize = 14.sp,
                                color = Color.White, fontWeight = FontWeight.Bold)
                            val summary = buildList {
                                add(session.date.format(
                                    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())))
                                session.distanceKm?.let { add(
                                    String.format(Locale.getDefault(), "%.1f km", it)) }
                                add(if (session.status == "planned") "Not completed"
                                    else "In progress")
                            }.joinToString(" · ")
                            Text(summary, fontSize = 11.sp,
                                color = Color(0xFFB7C5CF))
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text("CHOOSE WHAT HAPPENS NEXT", fontSize = 11.sp,
                    color = Color(0xFFB7C5CF), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(11.dp))
                TracksideMissedDecisionCard(
                    title = "Skip workout",
                    subtitle = "Mark missed; leave the other sessions unchanged.",
                    selected = decision == "skip",
                    enabled = canEdit && !busy,
                    testTag = "missed-option-skip",
                    onClick = { decision = "skip" }
                )
                Spacer(Modifier.height(10.dp))
                TracksideMissedDecisionCard(
                    title = "Move to another day",
                    subtitle = "Choose a day, then check for schedule conflicts.",
                    selected = decision == "move",
                    enabled = canEdit && !busy,
                    testTag = "missed-option-move",
                    onClick = { decision = "move" }
                )
                Spacer(Modifier.height(10.dp))
                TracksideMissedDecisionCard(
                    title = "Let StrideLab adjust",
                    subtitle = "Preview changes that respect recovery and workload.",
                    selected = decision == "adjust",
                    enabled = canEdit && !busy,
                    testTag = "missed-option-adjust",
                    onClick = { decision = "adjust" }
                )
                if (!canEdit) {
                    Spacer(Modifier.height(12.dp))
                    Text("This saved workout is read-only, already completed or linked to a recording.",
                        color = Color(0xFFFFA6AC),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Color(0xFF010305))
                .padding(start = 19.dp, end = 19.dp, bottom = 23.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Button(
                onClick = {
                    if (decision == "move") choosingDestination = true
                    else choosingReason = true
                },
                modifier = Modifier.fillMaxWidth().height(51.dp)
                    .testTag("missed-preview-adjustment"),
                enabled = canEdit && !busy,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD12648))
            ) {
                Text(when (decision) {
                    "skip" -> "Preview skip"
                    "move" -> "Choose destination"
                    else -> "Preview adjustment"
                }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text("No automatic catch-up or back-to-back hard workouts.",
                fontSize = 11.sp, color = Color(0xFFB7C5CF),
                modifier = Modifier.testTag("missed-safety-note"))
        }
    }

    if (choosingReason) {
        val reasons = listOf("Time", "Fatigue", "Soreness", "Illness", "Other")
        AlertDialog(
            onDismissRequest = { choosingReason = false },
            title = { Text("Why was this session missed?") },
            text = {
                Column {
                    Text("The reason is needed to determine whether the existing engine proposes an easier upcoming workout. No change is applied yet.")
                    Spacer(Modifier.height(10.dp))
                    reasons.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { reason = item },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reason == item, onClick = { reason = item })
                            Text(item)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        choosingReason = false
                        proposal = TracksideChangeProposal(
                            if (decision == "skip") TracksideChangeKind.SKIP
                            else TracksideChangeKind.ADJUST,
                            session.id, reason = reason
                        )
                    }
                ) { Text("Review proposal") }
            },
            dismissButton = {
                TextButton(onClick = { choosingReason = false }) { Text("Cancel") }
            }
        )
    }

    if (choosingDestination) {
        TracksideParityMoveWorkoutSheet(
            plan = plan, session = session, actions = actions,
            onDismiss = { choosingDestination = false },
            onReviewDestination = { date ->
                choosingDestination = false
                proposal = TracksideChangeProposal(
                    TracksideChangeKind.MOVE, session.id, destination = date
                )
            }
        )
    }
}

@Composable
private fun TracksideMissedDecisionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(91.dp).testTag(testTag),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color(0xFF254354) else Color(0xFF102936),
        border = BorderStroke(if (selected) 1.2f.dp else 1.dp,
            if (selected) Color(0xFF94B8CA) else Color(0xFF527183))
    ) {
        Column(Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color.White)
            Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp,
                color = Color(0xFFB7C5CF))
        }
    }
}

@Composable
private fun TracksideParityUnavailableMissed(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF010305))) {
        TracksideParityPlanFlowHeader("Missed workout", onBack)
        Text("This workout is not available in the current saved plan.",
            modifier = Modifier.padding(19.dp), color = Color.White)
    }
}
