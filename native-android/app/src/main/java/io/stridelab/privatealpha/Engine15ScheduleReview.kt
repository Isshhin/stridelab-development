@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun Engine15ScheduleReview(plan: PlanSnapshot, actions: PlanActions, busy: Boolean) {
    var open by remember(plan.owner, plan.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var days by remember(plan) { mutableStateOf(plan.request.days) }
    var impact by remember(plan, days) { mutableStateOf<Engine15Availability.Impact?>(null) }
    var error by remember(plan, days) { mutableStateOf<String?>(null) }
    var loading by remember(plan, days) { mutableStateOf(false) }
    val unresolved = plan.sessions.filter { it.status == "unassigned" }
    TracksideGlassCard(compact = true) {
        TracksideSectionHeader("Training availability")
        Text(plan.request.days.sorted().joinToString(" · ", transform = ::dayName))
        if (unresolved.isNotEmpty()) Text("${unresolved.size} unresolved workouts need placement before approval.")
        OutlinedButton(onClick = { open = !open }) { Text(if (open) "Close schedule review" else "Review schedule") }
        if (open) {
            Text("Available training days", style = MaterialTheme.typography.titleMedium)
            ReviewWeekdays(days) { days = if (it in days) days - it else days + it }
            OutlinedButton(onClick = {
                loading = true
                error = null
                scope.launch {
                    try {
                        impact = actions.previewAvailability(plan.id, days)
                        if (impact == null) error = "This plan is unavailable for this account."
                    } catch (e: PlanConstraint) {
                        error = e.message
                    } finally { loading = false }
                }
            }, enabled = !busy && !loading && days.isNotEmpty() && days != plan.request.days) {
                Text(if (loading) "Checking schedule…" else "Preview availability change")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            impact?.let { reviewed ->
                Text("Proposed schedule", style = MaterialTheme.typography.titleMedium)
                Text("${reviewed.relocations.size} safe moves · ${reviewed.unresolvedSessionIds.size} unresolved")
                for ((id, date) in reviewed.relocations) {
                    val session = plan.sessions.single { it.id == id }
                    Text("${session.title} · ${session.date} → $date")
                }
                for (id in reviewed.unresolvedSessionIds) Text("Needs placement · " + plan.sessions.single { it.id == id }.title)
                Text("Changes affecting scheduled workouts pause an approved plan for review.")
                SignalButton("Apply reviewed availability", { actions.applyAvailability(plan.id, plan.revision, days, reviewed) }, !busy)
            }
            if (plan.request.runDays > 0) {
                Text("Preferred long-run day")
                ReviewWeekdays(setOf(plan.request.longDay), plan.request.days) {
                    actions.longDay(plan.id, plan.revision, it)
                }
            }
            if (plan.request.qualityCount > 0) {
                var quality by remember(plan) { mutableStateOf(plan.request.qualityDays intersect (plan.request.days - plan.request.longDay)) }
                Text("Preferred quality days")
                ReviewWeekdays(quality, plan.request.days - plan.request.longDay) {
                    quality = if (it in quality) quality - it else quality + it
                }
                OutlinedButton(onClick = { actions.qualityDays(plan.id, plan.revision, quality) },
                    enabled = !busy && quality != plan.request.qualityDays && quality.size >= plan.request.qualityCount) { Text("Save quality days") }
            }
            for ((id, date) in plan.request.lockedSessionDays) {
                val session = plan.sessions.firstOrNull { it.id == id } ?: continue
                if (session.status == "planned") OutlinedButton(onClick = { actions.lockWorkout(plan.id, plan.revision, id, false) }, enabled = !busy) {
                    Text("Unlock ${session.title} · $date")
                }
            }
            for (session in unresolved) key(session.id) {
                var date by remember(session) { mutableStateOf(session.date.coerceAtLeast(LocalDate.now()).toString()) }
                Text("Needs placement · ${session.title}", style = MaterialTheme.typography.titleMedium)
                NativeDateField("Proposed workout date", date, { date = it })
                val destination = runCatching { LocalDate.parse(date) }.getOrNull()
                OutlinedButton(onClick = { destination?.let { actions.move(plan.id, plan.revision, session.id, it) } },
                    enabled = !busy && destination != null && destination >= LocalDate.now() && destination.dayOfWeek.value in plan.request.days) {
                    Text("Place workout on $date")
                }
            }
        }
    }
}

@Composable
private fun ReviewWeekdays(selected: Set<Int>, options: Set<Int> = (1..7).toSet(), toggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (day in options.sorted()) FilterChip(selected = day in selected, onClick = { toggle(day) }, label = { Text(dayName(day)) })
    }
}

@Composable
internal fun Engine15RecommendationReview(plan: PlanSnapshot, actions: PlanActions, busy: Boolean, onlyTarget: String? = null) {
    var previews by remember(plan.owner, plan.id, onlyTarget) { mutableStateOf<List<Engine15Feedback.Proposal>?>(null) }
    fun refresh() {
        previews = actions.previewAdjustments(plan.id).filter { onlyTarget == null || it.targetSessionId == onlyTarget }
    }
    val pending = plan.sessions.filter {
        (onlyTarget == null || onlyTarget == it.id) && it.suggestion?.let { s -> !s.accepted && s.reason.startsWith(Engine15Feedback.RULE_ID) } == true
    }
    TracksideGlassCard(compact = true) {
        TracksideSectionHeader("Workout recommendations")
        if (plan.status == "approved") OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Preview recommendations") }
        if (previews?.isEmpty() == true && pending.isEmpty()) Text("No recommendation from your current workout records.")
        for (proposal in previews.orEmpty()) {
            val target = plan.sessions.firstOrNull { it.id == proposal.targetSessionId } ?: continue
            if (target.suggestion != null || plan.feedback.any { it.sessionId == target.id && it.text.startsWith("${Engine15Feedback.RULE_ID} dismissed:") }) continue
            if (plan.revision != proposal.planRevision) {
                Text("Plan changed since preview. Refresh before reviewing this recommendation.")
                continue
            }
            val source = plan.sessions.first { it.id == proposal.sourceSessionId }
            Text("Preview · original workout active", style = MaterialTheme.typography.titleMedium)
            Text("${source.title} · ${source.log?.performedOn ?: source.date}")
            Text(proposal.reason.substringAfter(": "))
            Text(proposal.uncertainty)
            Text("${target.title} · ${target.date} · ${target.minutes} → ${proposal.suggestedMinutes} min")
            SignalButton("Review suggestion", { actions.stageAdjustment(proposal) }, !busy)
            OutlinedButton(onClick = { actions.dismissPreview(proposal) }, enabled = !busy) { Text("Keep original") }
        }
        for (session in pending) {
            val suggestion = requireNotNull(session.suggestion)
            var reviewedRevision by remember(plan.owner, plan.id, session.id, suggestion) { mutableIntStateOf(plan.revision) }
            val changed = reviewedRevision != plan.revision
            val stale = if (changed) "The plan changed after this review opened." else actions.pendingError(plan.id, session.id)
            Text(if (stale == null) "Awaiting your decision" else "Evidence changed", style = MaterialTheme.typography.titleMedium)
            Text("${session.title} · ${session.date} · ${session.minutes} → ${suggestion.minutes} min")
            val source = plan.sessions.find { suggestion.reason.endsWith("Evidence: session ${it.id}, log revision ${it.logRevision}.") }
            source?.let { Text("${it.title} · ${it.log?.performedOn ?: it.date}") }
            Text(suggestion.reason.substringAfter(": ").substringBefore(" Evidence:"))
            if (stale != null) Text(stale, color = MaterialTheme.colorScheme.error)
            if (changed) OutlinedButton(onClick = { reviewedRevision = plan.revision }) { Text("Refresh decision") }
            SignalButton(if (suggestion.minutes == 0) "Accept rest" else "Accept ${suggestion.minutes} min",
                { actions.acceptSuggestion(plan.id, reviewedRevision, session.id) }, !busy && stale == null)
            OutlinedButton(onClick = { actions.dismissPending(plan.id, reviewedRevision, session.id) }, enabled = !busy && !changed) { Text("Dismiss suggestion") }
        }
        val lastDismissal = plan.feedback.lastOrNull { (onlyTarget == null || it.sessionId == onlyTarget) && it.text.startsWith("${Engine15Feedback.RULE_ID} dismissed:") }
        lastDismissal?.let { record ->
            val session = plan.sessions.find { it.id == record.sessionId }
            Text("Dismissed · ${session?.title.orEmpty()} · original target retained")
        }
        plan.sessions.filter { (onlyTarget == null || it.id == onlyTarget) && it.suggestion?.accepted == true &&
            it.suggestion.reason.startsWith(Engine15Feedback.RULE_ID) }.forEach { session ->
            val current = Engine15SessionPresentation.forSession(plan, session.id)
            Text("${session.date} · ${session.title}")
            Text(current.disclosure.orEmpty())
            Text("Original prescription · ${current.originalMinutes} min" + (current.originalDistanceKm?.let { " · $it km" } ?: ""))
            if (current.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY) Text("Current target · ${current.currentMinutes} min")
        }
    }
}

/** Display-only adapter for the original Trackside cards. Never pass this copy to a plan action. */
internal fun PlanSnapshot.cardPresentation(session: PlannedSession): PlannedSession {
    if (engineVersion != ENGINE15_VERSION || session.suggestion?.accepted != true ||
        !session.suggestion.reason.startsWith(Engine15Feedback.RULE_ID)) return session
    val target = Engine15SessionPresentation.forSession(this, session.id)
    if (target.disclosure == null) return session
    val title = when (target.state) {
        Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY -> "Time-only run"
        Engine15SessionPresentation.State.ACCEPTED_REST -> "Reviewed rest"
        else -> return session.copy(suggestion = null)
    }
    return session.copy(title = title, minutes = target.currentMinutes, distanceKm = target.currentDistanceTargetKm,
        steps = target.currentSteps, kind = if (target.currentMinutes == 0) "rest" else "time only",
        exercises = emptyList(), suggestion = null, reason = target.disclosure)
}
