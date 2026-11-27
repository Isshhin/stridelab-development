@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manage-training-availability"),
        shape = RoundedCornerShape(18.dp),
        color = StrideLabSurfaceTokens.surfaceElevated,
        border = BorderStroke(1.dp, if (open) StrideLabSurfaceTokens.lime.copy(alpha = 0.45f) else StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .strideLabPressFeedback(
                        shape = RoundedCornerShape(12.dp),
                        onClick = { open = !open }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "WEEKLY AVAILABILITY",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Training availability",
                        color = StrideLabSurfaceTokens.ink,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (open) StrideLabSurfaceTokens.limeMuted else StrideLabSurfaceTokens.surfaceMuted,
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, if (open) StrideLabSurfaceTokens.lime.copy(alpha = 0.4f) else StrideLabSurfaceTokens.hairline)
                ) {
                    Text(
                        text = if (open) "Hide" else "${plan.request.days.size}d · Edit",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                (1..7).forEach { day ->
                    val enabled = day in plan.request.days
                    val isLong = enabled && plan.request.runDays > 0 && day == plan.request.longDay
                    val isQuality = enabled && plan.request.qualityCount > 0 && day in plan.request.qualityDays && !isLong
                    val indicator = when {
                        isLong -> StrideLabSurfaceTokens.lime
                        isQuality -> StrideLabSurfaceTokens.amber
                        enabled -> StrideLabSurfaceTokens.inkSecondary
                        else -> StrideLabSurfaceTokens.inkMuted.copy(alpha = 0.45f)
                    }
                    Surface(
                        color = if (enabled) StrideLabSurfaceTokens.surfaceMuted else StrideLabSurfaceTokens.background,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, if (isLong) StrideLabSurfaceTokens.lime.copy(alpha = 0.55f) else StrideLabSurfaceTokens.hairline),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = dayName(day).take(1),
                                color = if (enabled) StrideLabSurfaceTokens.ink else StrideLabSurfaceTokens.inkMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(if (isLong || isQuality) 6.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(indicator)
                            )
                        }
                    }
                }
            }
            if (unresolved.isNotEmpty()) {
                Surface(
                    color = StrideLabSurfaceTokens.coral.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.coral.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${unresolved.size} workouts require placement",
                        color = StrideLabSurfaceTokens.coral,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (open) {
                Text(
                    text = "AVAILABLE DAYS",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.0.sp,
                    fontWeight = FontWeight.Bold
                )
                ReviewWeekdays(days) { days = if (it in days) days - it else days + it }
                OutlinedButton(
                    onClick = {
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
                    },
                    enabled = !busy && !loading && days.isNotEmpty() && days != plan.request.days
                ) {
                    Text(if (loading) "Checking schedule…" else "Preview availability change")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                impact?.let { reviewed ->
                    Surface(
                        color = StrideLabSurfaceTokens.surfaceMuted,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Proposed schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StrideLabSurfaceTokens.ink)
                            Text("Moves ${reviewed.relocations.size} · Unresolved ${reviewed.unresolvedSessionIds.size}", color = StrideLabSurfaceTokens.lime, fontSize = 12.sp, style = TabularTextStyle)
                            for ((id, date) in reviewed.relocations) {
                                val session = plan.sessions.single { it.id == id }
                                Text("${session.title} · ${session.date} → $date", color = StrideLabSurfaceTokens.inkSecondary, fontSize = 12.sp)
                            }
                            for (id in reviewed.unresolvedSessionIds) Text("Needs placement · " + plan.sessions.single { it.id == id }.title, color = StrideLabSurfaceTokens.coral, fontSize = 12.sp)
                            SignalButton("Apply reviewed availability", { actions.applyAvailability(plan.id, plan.revision, days, reviewed) }, !busy)
                        }
                    }
                }
                if (plan.request.runDays > 0) {
                    Text(
                        text = "LONG-RUN DAY",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ReviewWeekdays(setOf(plan.request.longDay), plan.request.days) {
                        actions.longDay(plan.id, plan.revision, it)
                    }
                }
                if (plan.request.qualityCount > 0) {
                    var quality by remember(plan) { mutableStateOf(plan.request.qualityDays intersect (plan.request.days - plan.request.longDay)) }
                    Text(
                        text = "QUALITY DAYS",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.0.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ReviewWeekdays(quality, plan.request.days - plan.request.longDay) {
                        quality = if (it in quality) quality - it else quality + it
                    }
                    OutlinedButton(
                        onClick = { actions.qualityDays(plan.id, plan.revision, quality) },
                        enabled = !busy && quality != plan.request.qualityDays && quality.size >= plan.request.qualityCount
                    ) { Text("Save quality days") }
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
                    OutlinedButton(
                        onClick = { destination?.let { actions.move(plan.id, plan.revision, session.id, it) } },
                        enabled = !busy && destination != null && destination >= LocalDate.now() && destination.dayOfWeek.value in plan.request.days
                    ) {
                        Text("Place workout on $date")
                    }
                }
            }
        }
    }
}

/** A review can change only the supporting plan. Dates are local calendar dates. */
internal object RunnerSupportSchedule {
    enum class Tier { HEAVY_LOWER, MODERATE_LOWER, PLYOMETRICS, UPPER_TRUNK, MOBILITY }
    enum class Option { MOVE, AFTER_QUALITY, CAP_DOSE, NO_SAFE_OPTION }
    data class Change(val before: PlannedSession, val after: PlannedSession, val options: Set<Option>, val reason: String)
    data class Review(val candidate: PlanSnapshot, val changes: List<Change>) {
        val blocked get() = changes.any { Option.NO_SAFE_OPTION in it.options }
    }

    fun supports(plan: PlanSnapshot) = plan.policyId in setOf(
        ENGINE15_INDEPENDENT_STRENGTH_POLICY, ENGINE15_INDEPENDENT_CALISTHENICS_POLICY
    ) && plan.sessions.isNotEmpty() && plan.sessions.all { Engine15DisciplineProgression.isStructured(it) }

    fun tier(session: PlannedSession): Tier = when {
        session.kind == "structured-plyometrics" -> Tier.PLYOMETRICS
        session.exercises.all { it.id == "landing-rehearsal" || it.id == "glute-bridge" } -> Tier.MOBILITY
        session.title in setOf("Full Body", "Lower / Runner Durability") -> Tier.HEAVY_LOWER
        session.lower -> Tier.MODERATE_LOWER
        else -> Tier.UPPER_TRUNK
    }

    fun runningPlans(owner: String, plans: List<PlanSnapshot>) = plans.filter {
        it.owner == owner && it.status == "approved" && it.sessions.any { session -> session.discipline == "run" }
    }

    fun phases(runs: List<PlanSnapshot>): Map<LocalDate, Engine15MacroPhase> {
        val priority = listOf(Engine15MacroPhase.BASE, Engine15MacroPhase.BUILD, Engine15MacroPhase.PEAK,
            Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER)
        return buildMap {
            for (run in runs) {
                val goal = run.request.disciplineGoals["run"] ?: run.request.goal
                val request = run.request.copy(goal = goal)
                val byWeek = Engine15MacrocyclePeriodization.assignPhases(request, List(request.weeks) { index ->
                    index % 4 == 3 && !Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(request.goal, request.weeks, index)
                })
                for ((week, phase) in byWeek) for (day in 0L..6L) {
                    val date = request.start.plusWeeks(week - 1L).plusDays(day)
                    if (priority.indexOf(phase) > priority.indexOf(get(date))) put(date, phase)
                }
            }
        }
    }

    fun keyRuns(runs: List<PlanSnapshot>) = runs.flatMap { plan ->
        plan.sessions.filter { it.discipline == "run" && (it.quality || it.kind == "long") &&
            it.status in setOf("planned", "in_progress", "completed") }
    }.groupBy { it.date }

    data class Placement(val score: Int, val afterQuality: Boolean = false, val cap: Boolean = false)

    fun placement(session: PlannedSession, date: LocalDate, keys: Map<LocalDate, List<PlannedSession>>): Placement? {
        val tier = tier(session)
        if (tier == Tier.UPPER_TRUNK || tier == Tier.MOBILITY) return Placement(0)
        val tomorrow = date.plusDays(1) in keys
        val yesterday = date.minusDays(1) in keys
        val same = keys[date].orEmpty()
        if (same.isNotEmpty()) {
            if (same.size != 1 || same.single().kind == "long" || tomorrow || yesterday) return null
            return Placement(1, afterQuality = true, cap = tier != Tier.HEAVY_LOWER)
        }
        if (tier == Tier.MODERATE_LOWER) {
            return if (tomorrow || yesterday) Placement(3, cap = true) else Placement(0)
        }
        return if (tomorrow || yesterday) null else Placement(0)
    }

    fun review(plan: PlanSnapshot, others: List<PlanSnapshot>): Review? {
        if (!supports(plan) || plan.status != "draft") return null
        val runs = runningPlans(plan.owner, others)
        val approvedPeers = others.filter { it.owner == plan.owner && it.status == "approved" && it.id != plan.id }
        val phases = phases(runs)
        val keys = keyRuns(runs)
        val sessions = plan.sessions.toMutableList()
        val changes = mutableListOf<Change>()
        for ((index, original) in plan.sessions.withIndex()) {
            val week = ChronoUnit.DAYS.between(plan.request.start, original.date) / 7
            val start = plan.request.start.plusWeeks(week)
            val fixed = original.discipline in plan.request.weeklySlots || original.id in plan.request.lockedSessionDays
            val dates = if (fixed) listOf(original.date) else (0L..6L).map(start::plusDays)
            val choices = dates.asSequence().filter { date ->
                date.dayOfWeek.value in plan.request.days
            }.mapNotNull { date ->
                val fit = placement(original, date, keys) ?: return@mapNotNull null
                val phase = phases[date]
                val mode = when {
                    fit.cap || phase in setOf(Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER) &&
                        tier(original) !in setOf(Tier.UPPER_TRUNK, Tier.MOBILITY) -> RunnerSupportDose.LIGHT
                    original.kind == "structured-plyometrics" || original.title == "Strength for Runners" ||
                        original.discipline == "cal" && phase in setOf(Engine15MacroPhase.BUILD, Engine15MacroPhase.PEAK) ->
                        RunnerSupportDose.ENTRY
                    else -> null
                }
                val moved = original.copy(date = date, runnerSupportPlacement = date != original.date)
                val dosed = if (mode != null) mode.apply(moved, plan.request) else moved
                val candidate = if (fit.afterQuality) dosed.copy(
                    reason = "${dosed.reason} Complete the quality run first. Skip support work if recovery or technique is poor."
                ) else dosed
                if (plan.request.timeBudgetEnabled && candidate.minutes > plan.request.minutes) return@mapNotNull null
                if (sessions.any { it.id != candidate.id &&
                        PlanScheduleValidation.conflict(candidate, it, plan.policyId, plan.policyId) }) return@mapNotNull null
                if (approvedPeers.any { peer -> peer.sessions.any { existing ->
                        existing.status in setOf("planned", "in_progress") &&
                            PlanScheduleValidation.acrossPlansConflict(candidate, existing, plan.policyId, peer.policyId)
                    } }) return@mapNotNull null
                val concurrent = approvedPeers.flatMap { peer ->
                    peer.sessions.filter { it.date == date && it.status in setOf("planned", "in_progress") }.map { peer to it }
                }
                val dailyCap = (listOf(plan) + concurrent.map { it.first }).filter { it.request.timeBudgetEnabled }
                    .minOfOrNull { it.request.dailyMinutes }
                if (fit.afterQuality && (concurrent.any { it.second.discipline != "run" } ||
                        dailyCap != null && concurrent.sumOf { it.second.effectiveMinutes } + candidate.minutes > dailyCap))
                    return@mapNotNull null
                Triple(candidate, fit, phase)
            }
            val choice = choices.minWithOrNull(compareBy<Triple<PlannedSession, Placement, Engine15MacroPhase?>> { it.second.score }
                .thenBy { kotlin.math.abs(ChronoUnit.DAYS.between(original.date, it.first.date)) }.thenBy { it.first.date })
            if (choice == null) {
                changes += Change(original, original, setOf(Option.NO_SAFE_OPTION),
                    "Change the fixed day or widen your availability to give the key run room.")
                continue
            }
            val (after, fit, phase) = choice
            sessions[index] = after
            val options = buildSet {
                if (after.date != original.date) add(Option.MOVE)
                if (fit.afterQuality) add(Option.AFTER_QUALITY)
                if (after.exercises != original.exercises) add(Option.CAP_DOSE)
            }
            if (options.isNotEmpty()) {
                val reason = when {
                    fit.afterQuality -> "Complete the quality run first. Skip support work if recovery or technique is poor."
                    phase in setOf(Engine15MacroPhase.RECOVERY, Engine15MacroPhase.TAPER) ->
                        "Running ${phase?.name?.lowercase()} week. Keep the movement familiar with fewer sets or contacts."
                    fit.cap -> "Keep two working sets so the nearby key run takes priority."
                    else -> "Keep support work at a controlled dose before adding load from completed workouts."
                }
                changes += Change(original, after, options, reason)
            }
        }
        if (changes.isEmpty() && sessions == plan.sessions) return null
        return Review(plan.copy(sessions = sessions.sortedBy { it.date }), changes)
    }
}

@Composable
internal fun RunnerSupportPreviewCard(review: RunnerSupportSchedule.Review, weekStart: LocalDate, weekEnd: LocalDate) {
    TracksideWorkoutNeutralCard {
        Text("RUNNING PRIORITY", color = TracksidePalette.text, fontWeight = FontWeight.Bold)
        Text(if (review.blocked) "No safe option for ${review.changes.count { RunnerSupportSchedule.Option.NO_SAFE_OPTION in it.options }} workouts."
            else "${review.changes.size} schedule or dose changes. Activate to approve this support plan.")
        for (change in review.changes.filter { it.before.date in weekStart..weekEnd }) {
            val (before, after, options, reason) = change
            val date = if (before.date == after.date) "${after.date}" else "${before.date} → ${after.date}"
            Text("${after.title} · $date", fontWeight = FontWeight.SemiBold)
            if (RunnerSupportSchedule.Option.CAP_DOSE in options)
                Text("${RunnerSupportDose.label(before)} → ${RunnerSupportDose.label(after)}")
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReviewWeekdays(selected: Set<Int>, options: Set<Int> = (1..7).toSet(), toggle: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (day in 1..7) {
            val allowed = day in options
            val active = day in selected
            Surface(
                color = when {
                    !allowed -> StrideLabSurfaceTokens.background
                    active -> StrideLabSurfaceTokens.limeMuted
                    else -> StrideLabSurfaceTokens.surfaceMuted
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        !allowed -> StrideLabSurfaceTokens.hairline.copy(alpha = 0.45f)
                        active -> StrideLabSurfaceTokens.lime
                        else -> StrideLabSurfaceTokens.hairline
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = allowed) { toggle(day) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = dayName(day).take(1),
                        color = when {
                            !allowed -> StrideLabSurfaceTokens.inkMuted.copy(alpha = 0.4f)
                            active -> StrideLabSurfaceTokens.lime
                            else -> StrideLabSurfaceTokens.inkSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun Engine15RecommendationReview(plan: PlanSnapshot, actions: PlanActions, busy: Boolean, onlyTarget: String? = null, embedded: Boolean = false) {
    var previews by remember(plan.owner, plan.id, onlyTarget) { mutableStateOf<List<Engine15Feedback.Proposal>?>(null) }
    fun refresh() {
        previews = actions.previewAdjustments(plan.id).filter { onlyTarget == null || it.targetSessionId == onlyTarget }
    }
    val pending = plan.sessions.filter {
        (onlyTarget == null || onlyTarget == it.id) && it.suggestion?.let { s -> !s.accepted && s.reason.startsWith(Engine15Feedback.RULE_ID) } == true
    }
    val body: @Composable ColumnScope.() -> Unit = {
        if (!embedded) Text("WORKOUT RECOMMENDATIONS",
            color=TracksidePalette.text,fontSize=12.sp,fontWeight=FontWeight.Bold)
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
            if (current.state == Engine15SessionPresentation.State.ACCEPTED_PROGRESSION) {
                val distLabel = current.currentDistanceTargetKm?.let { " · %.1f km".format(java.util.Locale.US, it) }.orEmpty()
                Text("Progressed target · ${current.currentMinutes} min$distLabel")
            }
        }
    }
    if(embedded) {
        Column(Modifier.fillMaxWidth(),
            verticalArrangement=Arrangement.spacedBy(8.dp)) { body() }
    } else {
        TracksideWorkoutNeutralCard { body() }
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
        Engine15SessionPresentation.State.ACCEPTED_PROGRESSION ->
            return session.copy(minutes = target.currentMinutes, distanceKm = target.currentDistanceTargetKm,
                suggestion = null, reason = target.disclosure)
        else -> return session.copy(suggestion = null)
    }
    return session.copy(title = title, minutes = target.currentMinutes, distanceKm = target.currentDistanceTargetKm,
        steps = target.currentSteps, kind = if (target.currentMinutes == 0) "rest" else "time only",
        exercises = emptyList(), suggestion = null, runningPrescription = null, reason = target.disclosure)
}
