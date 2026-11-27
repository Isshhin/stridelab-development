package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val CoachingBorder = Color(0xFF232A31)
private val CoachingInk = Color(0xFFF5F7FA)
private val CoachingMuted = Color(0xFF9BA4B0)
private val CoachingLime = Color(0xFFD8FB70)

@Composable
internal fun PendingActivityMatchCards(state: TrainingState, actions: PlanActions, modifier: Modifier = Modifier) {
    val pending = state.activityMatches.values.filter { it.matchStatus == ActivityMatchStatus.NEEDS_CONFIRMATION }
    pending.forEach { match ->
        val activity = state.activities.firstOrNull { it.id == match.activityId } ?: return@forEach
        val candidates = match.candidates.ifEmpty {
            match.plannedSessionId?.let { listOf(ActivityMatchCandidate(it, match.confidenceScore, match.dateOffsetDays ?: 0, match.reasonCodes)) }.orEmpty()
        }
        candidates.forEach { candidate ->
            val plan = state.plans.singleOrNull { p ->
                p.status == "approved" && p.sessions.any { it.id == candidate.plannedSessionId && it.discipline == "run" }
            } ?: return@forEach
            val session = plan.sessions.singleOrNull { it.id == candidate.plannedSessionId && it.discipline == "run" } ?: return@forEach
            SignalCard(modifier.testTag("activity-match-${match.activityId}-${session.id}")) {
                Text("CONFIRM WORKOUT LINK", style = MaterialTheme.typography.labelMedium, color = CoachingMuted)
                Text(activity.title, style = MaterialTheme.typography.titleMedium, color = CoachingInk)
                Text("${activity.dateLabel} · ${activity.distanceLabel}", color = CoachingMuted, style = MaterialTheme.typography.bodySmall)
                Text("Candidate: ${session.title} · ${session.date}", color = CoachingInk, style = MaterialTheme.typography.bodyMedium)
                Text("${(candidate.confidenceScore * 100).toInt()}% match${candidate.reasonCodes.firstOrNull()?.let { " · ${it.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)}" } ?: if (candidate.dateOffsetDays != 0) " · off-day match" else ""}", color = CoachingLime, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.confirmActivityMatch(plan.id, plan.revision, session.id, activity.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Confirm Link") }
                    OutlinedButton(onClick = { actions.rejectActivityMatch(activity.id, session.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Not This Workout") }
                }
            }
        }
    }
}

@Composable
internal fun AthleteDiagnosticsCard(diagnostic: AthleteDiagnosticSnapshot?, modifier: Modifier = Modifier) {
    if (diagnostic == null) return
    AthleteDiagnosticsVisualCard(diagnostic = diagnostic, modifier = modifier)
}

@Composable
internal fun EasyPacingLockBanner(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().testTag("easy-pacing-lock"), shape = RoundedCornerShape(12.dp), color = Color(0xFF20252A), border = BorderStroke(1.dp, CoachingBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EASY-PACING LOCK", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB6C2A1))
            Text("Pace promotions are paused until easy days stay within their coached zone.", style = MaterialTheme.typography.bodySmall, color = CoachingInk)
        }
    }
}

@Composable
internal fun AdaptationProposalBanner(proposal: PlanAdaptationProposal?, onReview: () -> Unit, modifier: Modifier = Modifier) {
    if (proposal == null || proposal.status != AdaptationProposalStatus.PENDING) return
    SignalCard(modifier.testTag("adaptation-proposal-banner")) {
        Text("COACHING ADJUSTMENT", style = MaterialTheme.typography.labelMedium, color = CoachingMuted)
        Text(proposal.title, style = MaterialTheme.typography.titleMedium, color = CoachingInk)
        Text("Source confidence · ${proposal.sourceConfidence.name}", style = MaterialTheme.typography.bodySmall, color = CoachingMuted)
        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Review Adjustment") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdaptationProposalSheet(proposal: PlanAdaptationProposal?, busy: Boolean, onDismiss: () -> Unit, onApply: (String, String) -> Unit, onDismissProposal: (String, String) -> Unit) {
    if (proposal == null) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF111519), contentColor = CoachingInk) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(proposal.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoachingBadge(proposal.sourceConfidence.name)
                if ("BIOMETRIC_STRAIN_DELOAD" in proposal.reasonCodes) {
                    CoachingBadge("GARMIN BIOMETRICS")
                }
            }
            Text(proposal.coachingRationale, style = MaterialTheme.typography.bodyMedium)
            val diff = proposal.diff
            Text("FROM ${diff.effectiveFromDate} · ${diff.affectedSessionCount} sessions", style = MaterialTheme.typography.labelMedium, color = CoachingMuted)
            val disciplineRecovery = "NON_RUNNING_RECOVERY" in proposal.reasonCodes ||
                proposal.type in setOf(
                    AdaptationType.RUNNER_SUPPORT_DOSE,
                    AdaptationType.STRENGTH_PROGRESSION,
                    AdaptationType.CALISTHENICS_PROGRESSION
                )
            if (!disciplineRecovery)
                Text("Remaining distance · ${diff.totalRemainingKmBefore.oneDecimal()} → ${diff.totalRemainingKmAfter.oneDecimal()} km", style = MaterialTheme.typography.bodyMedium)
            diff.paceDiffs.forEach { pace ->
                Text("${pace.intent.name.prettyLabel()} · ${paceBand(pace.oldMinSecPerKm, pace.oldMaxSecPerKm)} → ${paceBand(pace.newMinSecPerKm, pace.newMaxSecPerKm)}", style = MaterialTheme.typography.bodySmall)
            }
            diff.weekDiffs.forEach { week ->
                val km = if (disciplineRecovery) "" else " · ${week.plannedKmBefore.oneDecimal()} → ${week.plannedKmAfter.oneDecimal()} km"
                Text("Week ${week.weekIndex + 1} · ${week.phaseBefore} → ${week.phaseAfter}$km\n${week.summaryNote}", style = MaterialTheme.typography.bodySmall)
            }
            if (proposal.candidatePlanJson != null) {
                Button(onClick = { onApply(proposal.planId, proposal.proposalId); onDismiss() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply Adjustment") }
                OutlinedButton(onClick = { onDismissProposal(proposal.planId, proposal.proposalId); onDismiss() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Dismiss") }
            } else {
                Text("This advisory is read-only. Easy days need to stay in zone before pace promotions resume.", color = CoachingMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onDismissProposal(proposal.planId, proposal.proposalId); onDismiss() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Got It / Dismiss") }
            }
        }
    }
}

@Composable
internal fun UndoAdaptationButton(plan: PlanSnapshot?, revisions: List<PlanAdaptationRevision>, busy: Boolean, onUndo: (String) -> Unit, modifier: Modifier = Modifier) {
    if (plan == null || revisions.none { it.planId == plan.id && it.revertedAtDate == null }) return
    var confirm by remember(plan.id) { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("undo-last-adaptation")) { Text("Undo Last Adaptation") }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Undo last adaptation?") }, text = { Text("Restore the plan version saved before this adjustment.") }, confirmButton = { TextButton(onClick = { confirm = false; onUndo(plan.id) }) { Text("Undo") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
internal fun WorkoutExecutionVerdictCard(analysis: WorkoutExecutionAnalysis, modifier: Modifier = Modifier) {
    SignalCard(modifier.testTag("execution-verdict")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("EXECUTION VERDICT", style = MaterialTheme.typography.labelMedium, color = CoachingMuted)
            CoachingBadge(analysis.verdict.name.prettyLabel(), verdictTint(analysis.verdict))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CoachingBadge(when (analysis.evidenceTier) { ExecutionEvidenceTier.LAP_AND_RECORD -> "Lap + GPS"; ExecutionEvidenceTier.RECORD_WINDOW -> "Stream Window"; ExecutionEvidenceTier.SUMMARY_ONLY -> "Summary Only" })
            if (analysis.qualityStimulusVerified == true) CoachingBadge("Quality verified", tint = Color(0xFF7EBF9A))
            else if (analysis.qualityStimulusVerified == false && analysis.verdict != ExecutionVerdict.VOLUME_ONLY_MATCH) CoachingBadge("Quality not verified", tint = Color(0xFFB9A77A))
            if (analysis.verdict == ExecutionVerdict.VOLUME_ONLY_MATCH) CoachingBadge("Volume Only", tint = Color(0xFFB9A77A))
        }
        Text(analysis.coachingSummary, style = MaterialTheme.typography.bodyMedium, color = CoachingInk)
        Text("Distance · Actual ${analysis.actualKm?.oneDecimal() ?: "—"} / Planned ${analysis.plannedKm?.oneDecimal() ?: "—"} km", style = MaterialTheme.typography.bodySmall)
        analysis.workStepPaceAdherencePct?.let { Text("Work pace adherence · ${it.toInt()}%", style = MaterialTheme.typography.bodySmall) }
        analysis.hrZoneCompliancePct?.let { Text("HR zone compliance · ${it.toInt()}%", style = MaterialTheme.typography.bodySmall) }
        analysis.aerobicDecouplingPct?.let { Text("Pa:Hr decoupling · ${it.oneDecimal()}%", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun ExecutionStepBreakdownCards(analysis: WorkoutExecutionAnalysis, modifier: Modifier = Modifier) {
    if (analysis.stepBreakdowns.isEmpty()) return
    SignalCard(modifier.testTag("execution-step-breakdowns")) {
        Text("EXPECTED VS OBSERVED", style = MaterialTheme.typography.labelMedium, color = CoachingMuted)
        analysis.stepBreakdowns.forEachIndexed { index, step ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${index + 1}. ${step.purpose}", style = MaterialTheme.typography.titleSmall, color = CoachingInk)
                val planned = step.plannedDistanceKm?.let { "${it.oneDecimal()} km" } ?: step.plannedSeconds?.let { "${it / 60} min" } ?: "—"
                val observed = step.actualDistanceKm?.let { "${it.oneDecimal()} km" } ?: step.actualSeconds?.let { "${it / 60} min" } ?: "—"
                Text("Planned $planned · Observed $observed", style = MaterialTheme.typography.bodySmall, color = CoachingMuted)
                val pace = step.actualPaceSecPerKm?.let { "${formatPace(it.toInt())}/km observed" } ?: "Pace unavailable"
                Text("$pace${step.paceAdherencePct?.let { " · ${it.toInt()}% adherence" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = CoachingMuted)
            }
        }
    }
}

@Composable private fun CoachingBadge(text: String, tint: Color = Color(0xFF9BA4B0)) {
    Surface(shape = RoundedCornerShape(999.dp), color = tint.copy(alpha = .12f), border = BorderStroke(1.dp, tint.copy(alpha = .25f))) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun Double.oneDecimal() = String.format(Locale.ROOT, "%.1f", this)
private fun String.prettyLabel() = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun paceBand(min: Int, max: Int) = "${formatPace(min)}–${formatPace(max)} /km"
private fun formatPace(seconds: Int) = "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
private fun verdictTint(verdict: ExecutionVerdict): Color = when (verdict) {
    ExecutionVerdict.ON_TARGET -> Color(0xFF8CB89B)
    ExecutionVerdict.PARTIALLY_EXECUTED -> Color(0xFFB9A77A)
    ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST -> Color(0xFFCD8B82)
    ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW -> Color(0xFF8EABC2)
    ExecutionVerdict.VOLUME_ONLY_MATCH -> Color(0xFFB9A77A)
    ExecutionVerdict.INSUFFICIENT_TELEMETRY -> Color(0xFF9BA4B0)
}
