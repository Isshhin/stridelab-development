package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Figma approved 736:870 — a proposal, not an applied plan change.
 * These screens work with the current saved Engine 1 policy contracts; they
 * DO NOT claim that the separate Engine 2.0 research engine has checked a plan.
 */
internal enum class TracksideChangeKind { SKIP, ADJUST, MOVE }

internal data class TracksideChangeProposal(
    val kind: TracksideChangeKind,
    val sessionId: String,
    val reason: String = "Time",
    val destination: LocalDate? = null
)

internal data class TracksideReviewCheckResult(
    val accepted: Boolean,
    val capacity: String,
    val recovery: String,
    val load: String,
    val message: String
)

/** Pure preview. No calls to PlanActions, database, or network.
 * PlanOperations is deliberately reused to ensure the same present-day
 * deterministic safeguards as the actual revision-guarded action.
 */
internal fun tracksideReviewExistingEngineChecks(
    plan: PlanSnapshot,
    proposal: TracksideChangeProposal,
    otherPlans: List<PlanSnapshot>
): TracksideReviewCheckResult {
    val session = plan.sessions.firstOrNull { it.id == proposal.sessionId }
        ?: return TracksideReviewCheckResult(false, "Review", "Review", "Review",
            "The workout is no longer in this plan.")
    if (!Engine1Engines.canManage(plan) ||
        plan.status !in setOf("approved", "paused") ||
        session.status !in setOf("planned", "in_progress") ||
        session.log != null ||
        session.linkedActivityId != null
    ) return TracksideReviewCheckResult(false, "Review", "Review", "Review",
        "This is not an editable, unrecorded workout. Completed and linked records stay untouched.")

    val candidate = try {
        when (proposal.kind) {
            TracksideChangeKind.SKIP ->
                PlanOperations.missed(plan, proposal.sessionId, proposal.reason, true)
            TracksideChangeKind.ADJUST ->
                PlanOperations.missed(plan, proposal.sessionId, proposal.reason, false)
            TracksideChangeKind.MOVE -> {
                val destination = proposal.destination ?: throw PlanConstraint(
                    "Choose a destination day before reviewing a move.")
                if (destination < LocalDate.now()) throw PlanConstraint(
                    "A missed workout cannot be moved into an earlier day.")
                PlanOperations.move(plan, proposal.sessionId, destination, otherPlans)
            }
        }
    } catch (e: PlanConstraint) {
        return TracksideReviewCheckResult(false, "Review", "Review", "Review",
            e.message ?: "Existing coaching constraints did not validate this proposal.")
    } catch (_: IllegalArgumentException) {
        return TracksideReviewCheckResult(false, "Review", "Review", "Review",
            "The proposed change could not be validated.")
    }

    val originalMinutes = plan.sessions
        .filter { it.status in setOf("planned", "in_progress") }
        .sumOf { it.effectiveMinutes.coerceAtLeast(0) }
    val proposedMinutes = candidate.sessions
        .filter { it.status in setOf("planned", "in_progress") }
        .sumOf { it.effectiveMinutes.coerceAtLeast(0) }
    if (proposedMinutes > originalMinutes) return TracksideReviewCheckResult(
        false, "Review", "Review", "Review",
        "This proposal increases planned volume; it cannot be automatically approved."
    )
    val moved = proposal.kind == TracksideChangeKind.MOVE
    return TracksideReviewCheckResult(
        accepted = true,
        capacity = if (moved) "Checked" else "No increase",
        recovery = if (moved) "Checked" else "No added hard session",
        load = if (moved) "No added minutes" else "Reduced or unchanged",
        message = "Current Engine 1 schedule constraints passed. This is not an Engine 2.0 " +
            "validation or approval, and the saved plan remains unchanged until confirmation."
    )
}

/** Exact pending state is the approved Figma reference. After review, an explicit
 * second action applies via the existing guarded PlanActions callback.
 */
@Composable
internal fun TracksideParityReviewChanges(
    plan: PlanSnapshot,
    proposal: TracksideChangeProposal,
    otherPlans: List<PlanSnapshot>,
    busy: Boolean,
    onBack: () -> Unit,
    onRegenerateDraft: () -> Unit,
    onApply: (TracksideChangeProposal) -> Unit
) {
    val session = plan.sessions.firstOrNull { it.id == proposal.sessionId }
    var check by remember(plan.id, plan.revision, proposal) {
        mutableStateOf<TracksideReviewCheckResult?>(null)
    }
    val fmt = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
    val current = session?.date?.format(fmt) ?: "Unavailable"
    val proposed = when (proposal.kind) {
        TracksideChangeKind.SKIP -> "Skipped"
        TracksideChangeKind.ADJUST -> "Missed"
        TracksideChangeKind.MOVE ->
            proposal.destination?.format(fmt) ?: "Not selected"
    }

    Box(Modifier.fillMaxSize().background(StrideLabVisualPalette.amoledBlack)
        .testTag("review-changes-screen")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(bottom = 180.dp)
        ) {
            TracksideParityPlanFlowHeader("Review changes", onBack)
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("PLAN / DRAFT CHANGES", color = StrideLabVisualPalette.lime,
                    fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Text("Check before applying",
                    fontSize = 24.sp, lineHeight = 29.sp, color = StrideLabVisualPalette.ink,
                    fontWeight = FontWeight.ExtraBold)
                Text("Only future sessions may change. Completed workouts stay untouched.",
                    fontSize = 12.sp, lineHeight = 16.sp,
                    color = StrideLabVisualPalette.secondary)
                Spacer(Modifier.height(7.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 185.dp)
                        .testTag("review-proposal-diff"),
                    shape = RoundedCornerShape(16.dp), color = StrideLabVisualPalette.surface,
                    border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                ) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (proposal.kind == TracksideChangeKind.MOVE)
                                "WORKOUT MOVE" else "MISSED WORKOUT",
                                fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                                color = StrideLabVisualPalette.muted)
                            Text("NOT APPLIED", fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.moderateAmber)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TracksideParityDateBlock("CURRENT", current,
                                Modifier.weight(1f), active = false)
                            Text("→", fontSize = 27.sp, fontWeight = FontWeight.Bold,
                                color = StrideLabVisualPalette.lime)
                            TracksideParityDateBlock("PROPOSED", proposed,
                                Modifier.weight(1f), active = true)
                        }
                        Text(
                            when (proposal.kind) {
                                TracksideChangeKind.SKIP ->
                                    "Other sessions remain unchanged. No catch-up volume."
                                TracksideChangeKind.ADJUST ->
                                    "A recovery suggestion may be offered for the next session; it requires separate acceptance."
                                TracksideChangeKind.MOVE ->
                                    "Other sessions stay on their original dates."
                            },
                            fontSize = 11.5.sp, lineHeight = 15.sp,
                            color = StrideLabVisualPalette.secondary)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text("COACHING CHECKS", fontSize = 10.5.sp, letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.muted)
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 169.dp)
                        .testTag("review-checks-card"),
                    shape = RoundedCornerShape(16.dp), color = StrideLabVisualPalette.surface,
                    border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        TracksideParityCheckRow("Daily session capacity", check?.capacity)
                        TracksideParityCheckRow("Recovery between hard sessions", check?.recovery)
                        TracksideParityCheckRow("Combined training load", check?.load, last = true)
                    }
                }
                if (check != null) {
                    Text(check!!.message,
                        fontSize = 11.5.sp, lineHeight = 15.sp,
                        color = if (check!!.accepted) StrideLabVisualPalette.easyTeal else StrideLabVisualPalette.highCoral,
                        modifier = Modifier.testTag("review-check-result"))
                }
                Surface(
                    onClick = onRegenerateDraft,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 73.dp)
                        .testTag("review-regenerate-entry"),
                    shape = RoundedCornerShape(16.dp), color = StrideLabVisualPalette.surface,
                    border = BorderStroke(1.dp, StrideLabVisualPalette.border)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        Box(Modifier.size(41.dp).background(StrideLabVisualPalette.limeTint,
                                RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center) {
                            Text("↻", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.lime)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Regenerate future weeks", fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.ink)
                            Text("Choose a start date and compare proposals",
                                fontSize = 11.5.sp, color = StrideLabVisualPalette.secondary)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(StrideLabVisualPalette.surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PROPOSED ONLY", fontSize = 10.5.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold, color = StrideLabVisualPalette.muted)
                Text(if (check == null) "Approval requires coaching checks"
                else if (check!!.accepted) "Current engine checks complete"
                else "Review the proposal",
                    fontSize = 10.5.sp, color = StrideLabVisualPalette.secondary)
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(51.dp)
                    .testTag(if (check?.accepted == true)
                        "review-confirm-change" else "review-run-checks"),
                enabled = !busy && session != null &&
                    (check == null || check!!.accepted),
                onClick = {
                    if (check == null) {
                        check = tracksideReviewExistingEngineChecks(plan, proposal, otherPlans)
                    } else if (check!!.accepted) {
                        val currentCheck = tracksideReviewExistingEngineChecks(
                            plan, proposal, otherPlans)
                        if (currentCheck.accepted) onApply(proposal)
                        else check = currentCheck
                    }
                },
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StrideLabVisualPalette.lime,
                    contentColor = Color(0xFF090B09),
                    disabledContainerColor = StrideLabVisualPalette.surfaceRaised,
                    disabledContentColor = StrideLabVisualPalette.muted)
            ) {
                Text(if (check?.accepted == true) "Apply reviewed change"
                    else if (check != null) "Checks require review"
                    else "Run coaching checks",
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(if (check?.accepted == true)
                "Only current-engine safeguards ran; Engine 2.0 is not connected."
            else "No plan changes have been applied.",
                fontSize = 11.sp, textAlign = TextAlign.Center,
                color = StrideLabVisualPalette.muted, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TracksideParityCheckRow(label: String, status: String?, last: Boolean = false) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(51.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.5.sp, color = StrideLabVisualPalette.ink,
                modifier = Modifier.weight(1f), maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Text(status ?: "Pending",
                fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                color = if (status == null) StrideLabVisualPalette.moderateAmber
                else if (status == "Review") StrideLabVisualPalette.highCoral
                else StrideLabVisualPalette.easyTeal,
                maxLines = 2, textAlign = TextAlign.End)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp)
            .background(StrideLabVisualPalette.border))
    }
}

@Composable
private fun TracksideParityDateBlock(
    label: String, value: String, modifier: Modifier, active: Boolean
) {
    Surface(modifier.height(86.dp), shape = RoundedCornerShape(12.dp),
        color = if (active) StrideLabVisualPalette.limeTint else StrideLabVisualPalette.surfaceRaised,
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) StrideLabVisualPalette.lime else StrideLabVisualPalette.border)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                color = if (active) StrideLabVisualPalette.lime else StrideLabVisualPalette.muted)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                color = if (active) StrideLabVisualPalette.lime else StrideLabVisualPalette.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shared safe-area header for full-screen approved 25D/25E states. */
@Composable
internal fun TracksideParityPlanFlowHeader(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp)) {
        Row(Modifier.fillMaxWidth().height(78.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.size(28.dp)
                .testTag("plan-flow-back"),
                contentPadding = PaddingValues(0.dp)) {
                Text("‹", color = StrideLabVisualPalette.lime, fontSize = 27.sp)
            }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = StrideLabVisualPalette.ink)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(StrideLabVisualPalette.border))
    }
}
