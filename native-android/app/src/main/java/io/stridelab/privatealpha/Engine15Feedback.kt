package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.floor

/**
 * Engine 1.5 Batch C: conservative, completion-aware suggestions.
 * This is NOT an adaptive plan generator or a diagnosis. Preview is pure.
 * No source workout, activity or approved prescription is ever rewritten.
 */
internal object Engine15Feedback {
    const val RULE_ID = "stridelab.engine15.feedback-20261103.1"

    internal data class DraftReview(val asOf:LocalDate,val recoveryThrough:LocalDate,val illnessThrough:LocalDate?,val evidenceIds:List<String>)

    /** Reuse the same explicit negative evidence for a new draft, never infer successful work dose. */
    fun reviewForNewDraft(owner:String,plans:List<PlanSnapshot>,now:Instant):DraftReview? {
        val asOf=now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val owned=plans.filter { it.owner==owner && Engine1Engines.canManage(it) }
        val sessions=owned.flatMap { it.sessions }
        val duplicates=sessions.mapNotNull { it.linkedActivityId }.groupingBy { it }.eachCount().filterValues { it>1 }.keys
        val signals=sessions.filter { it.coachingLog()?.at?.isAfter(now)!=true }.mapNotNull { evidence(it,asOf,duplicates) }
        if(signals.isEmpty())return null
        return DraftReview(asOf,signals.maxOf { it.date.plusDays(if(it.category=="Pain")7 else 4) },signals.filter { it.category in setOf("Illness","Pain") }.maxOfOrNull { it.date.plusDays(if(it.category=="Pain")7 else 4) },
            signals.map { "${it.session.id}@${it.session.logRevision}:${it.category}" }.distinct().sorted())
    }

    internal data class Proposal(
        val planId: String,
        val planRevision: Int,
        val sourceSessionId: String,
        val sourceLogRevision: Int,
        val targetSessionId: String,
        val suggestedMinutes: Int,
        val reason: String,
        val uncertainty: String
    )

    private data class Evidence(
        val session: PlannedSession,
        val date: LocalDate,
        val category: String,
        val detail: String
    )

    private fun available(plan: PlanSnapshot) =
        plan.engineVersion == ENGINE15_VERSION && plan.policyId in ENGINE15_COACHING_POLICIES &&
            plan.status in setOf("approved", "paused") && Engine1Engines.canManage(plan)

    private fun dismissed(plan: PlanSnapshot, target: String) =
        plan.feedback.any {
            it.category == "Workout quality" && it.sessionId == target &&
                it.text.startsWith("$RULE_ID dismissed:")
        }

    private fun evidence(
        session: PlannedSession, asOf: LocalDate, duplicateLinks: Set<String>
    ): Evidence? {
        // A duplicate source link, even from imported records, is ambiguous.
        if (session.linkedActivityId != null && session.linkedActivityId in duplicateLinks)
            return null
        if (session.status in setOf("missed", "skipped") && session.coachingLog() == null &&
            session.date <= asOf && ChronoUnit.DAYS.between(session.date, asOf) in 0L..3L
        ) {
            val category = session.missedReason
            if (category in setOf("Illness", "Fatigue", "Soreness"))
                return Evidence(session, session.date, category!!,
                    "Explicitly marked ${session.status.lowercase()} ($category)")
        }
        val log = session.coachingLog() ?: return null
        if (session.status != "completed" || session.logRevision <= 0) return null
        // A linked run without an actual WorkoutLog has no recorded effort;
        // missing RPE, partial progress and zero-set strength logs do not imply readiness.
        val performed = log.performedOn ?: return null
        if (performed > asOf || ChronoUnit.DAYS.between(performed, asOf) !in 0L..(if(log.discomfort=="Pain")6L else 3L) ||
            kotlin.math.abs(ChronoUnit.DAYS.between(session.date, performed)) > 2 ||
            log.at.isAfter(asOf.plusDays(2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant())
        ) return null
        if (session.discipline in setOf("str", "cal") &&
            !WorkoutLogValidation.hasPerformedSet(session, log)) return null
        if(log.discomfort=="Pain") return Evidence(session,performed,"Pain","Reported pain during the completed run")
        if(log.discomfort=="Soreness" || log.completionDifficulty in setOf("Harder","Could not finish"))
            return Evidence(session,performed,"Reported difficulty","Reported ${log.completionDifficulty ?: log.discomfort}; no fitness inference")
        val effort = log.effort ?: return null
        if (effort !in 1..10) return null
        val minutes = log.minutes
        val durationSpike = minutes != null && minutes.isFinite() &&
            minutes > session.minutes * 1.35 && effort >= 7
        if (effort >= 8 || durationSpike) {
            val partial = minutes != null && minutes.isFinite() &&
                minutes < session.minutes * 0.75
            return Evidence(session, performed, "High reported effort",
                if (partial)
                    "Partial workout (${minutes!!.toInt()} of ${session.minutes} planned minutes) with recorded effort $effort/10"
                else if (durationSpike)
                    "Recorded effort $effort/10 and duration ${minutes!!.toInt()} min vs planned ${session.minutes} min"
                else "Recorded effort $effort/10")
        }
        return null
    }

    /**
     * Uses only explicit status/revision and manually recorded effort or missed reason.
     * No inference from incomplete weeks, unlinked/imported runs, missing logs, HR or
     * pace. Late uploads older than three days do not trigger retrospective changes.
     *
     * allPlans is only needed to reject duplicate source links across stored plans.
     */
    fun preview(
        plan: PlanSnapshot,
        asOf: LocalDate,
        allPlans: List<PlanSnapshot> = listOf(plan)
    ): List<Proposal> {
        if (!available(plan) || plan.status != "approved") return emptyList()
        val links = (allPlans + listOf(plan)).distinctBy { it.id }
            .flatMap { it.sessions }.mapNotNull { it.linkedActivityId }
        val duplicateLinks = links.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        val sourceEvidence = plan.sessions.mapNotNull { evidence(it, asOf, duplicateLinks) }
            .sortedWith(compareByDescending<Evidence> { it.date }.thenBy { it.session.id })
        val reserved = mutableSetOf<String>()
        val proposed = mutableListOf<Proposal>()
        for (source in sourceEvidence) {
            val target = plan.sessions.asSequence()
                .filter { it.discipline == "run" && it.id !in reserved && it.date > asOf && it.date > source.date }
                .filter { ChronoUnit.DAYS.between(source.date, it.date) in 1L..4L }
                .filter {
                    it.status == "planned" && it.coachingLog() == null &&
                        it.linkedActivityId == null && it.suggestion == null &&
                        !dismissed(plan, it.id)
                }
                .sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
                .firstOrNull() ?: continue
            // An explicit illness or high effort before quality suggests reviewed
            // rest, never an automatically rewritten "easy" workout with hard steps.
            val rest = source.category in setOf("Illness","Pain") || target.quality
            val adjusted = if (rest) 0 else floor(
                target.minutes * if (source.category in setOf("Fatigue", "Soreness")) 0.70 else 0.75
            ).toInt().coerceAtLeast(10).coerceAtMost(target.minutes)
            if (adjusted >= target.minutes) continue
            reserved += target.id
            val decision = if (rest) "consider resting instead of this session"
                else "consider reducing this session from ${target.minutes} to $adjusted minutes"
            proposed += Proposal(
                plan.id, plan.revision, source.session.id, source.session.logRevision,
                target.id, adjusted,
                "$RULE_ID: ${source.detail}; $decision. No missed training is made up.",
                "One recorded event is not a physiological readiness score. " +
                    "Check how you feel and review the recommendation; no automatic change."
            )
        }
        return proposed
    }

    /** Stage only a currently valid preview; plan revision is checked again in the repository. */
    fun stage(
        plan: PlanSnapshot, proposal: Proposal, asOf: LocalDate,
        allPlans: List<PlanSnapshot> = listOf(plan)
    ): PlanSnapshot {
        if (plan.id != proposal.planId || plan.revision != proposal.planRevision ||
            proposal !in preview(plan, asOf, allPlans))
            throw PlanConstraint("The training suggestion is stale. Review current workout records and preview it again.")
        val session = plan.sessions.single { it.id == proposal.targetSessionId }
        if (session.status != "planned" || session.coachingLog() != null || session.suggestion != null)
            throw PlanConstraint("Only an unstarted workout without another suggestion may be reviewed.")
        val note = "${proposal.reason} ${proposal.uncertainty} " +
            "Evidence: session ${proposal.sourceSessionId}, log revision ${proposal.sourceLogRevision}."
        val updated = session.copy(suggestion = SessionSuggestion(proposal.suggestedMinutes, note))
        return plan.copy(sessions = plan.sessions.map { if (it.id == session.id) updated else it })
    }

    /**
     * Before explicitly accepting a persisted Engine 1.5 suggestion, revalidate
     * the exact source evidence and future target against the current account's
     * plan collection. Staging alone does not freeze historical evidence.
     * Legacy Engine 1.0 suggestions retain their existing separate behavior.
     */
    fun requireCurrentPendingAcceptance(
        plan: PlanSnapshot, targetSessionId: String, asOf: LocalDate,
        allPlans: List<PlanSnapshot> = listOf(plan)
    ) {
        if (plan.engineVersion != ENGINE15_VERSION ||
            plan.policyId !in ENGINE15_COACHING_POLICIES)
            return
        val target = plan.sessions.find { it.id == targetSessionId } ?: return
        val pending = target.suggestion ?: return
        if (pending.accepted || !pending.reason.startsWith(RULE_ID)) return
        if (plan.status != "approved" || target.status != "planned" ||
            target.log != null || target.linkedActivityId != null || target.date <= asOf)
            throw PlanConstraint("The reviewed adjustment is no longer eligible. Refresh the workout.")
        val withoutPending = plan.copy(sessions = plan.sessions.map {
            if (it.id == targetSessionId) it.copy(suggestion = null) else it
        })
        val currentPlans = allPlans.filter { it.id != plan.id } + withoutPending
        val stillValid = preview(withoutPending, asOf, currentPlans).any { proposal ->
            val exactNote = "${proposal.reason} ${proposal.uncertainty} " +
                "Evidence: session ${proposal.sourceSessionId}, log revision ${proposal.sourceLogRevision}."
            proposal.targetSessionId == targetSessionId &&
                proposal.suggestedMinutes == pending.minutes && exactNote == pending.reason
        }
        if (!stillValid)
            throw PlanConstraint("The recorded evidence or target changed. Dismiss this stale suggestion and review a new one.")
    }

    /** Dismissals are durable and stop another proposal for this target session. */
    fun dismiss(
        plan: PlanSnapshot, proposal: Proposal, asOf: LocalDate,
        now: Instant, allPlans: List<PlanSnapshot> = listOf(plan)
    ): PlanSnapshot {
        if (plan.id != proposal.planId || plan.revision != proposal.planRevision ||
            proposal !in preview(plan, asOf, allPlans))
            throw PlanConstraint("The training suggestion changed. Refresh it before dismissal.")
        return addDismissal(plan, proposal.targetSessionId, proposal.sourceSessionId, now)
    }

    /** Reject a persisted but not-yet-accepted suggestion; never undo acceptance. */
    fun dismissStaged(plan: PlanSnapshot, targetSessionId: String, now: Instant): PlanSnapshot {
        if (!available(plan)) throw PlanConstraint("A supported Engine 1.5 plan is required.")
        val target = plan.sessions.find { it.id == targetSessionId }
            ?: throw PlanConstraint("This workout is unavailable.")
        val suggestion = target.suggestion
        if (target.status != "planned" || target.log != null ||
            suggestion == null || suggestion.accepted ||
            !suggestion.reason.startsWith(RULE_ID))
            throw PlanConstraint("There is no pending Engine 1.5 suggestion to dismiss.")
        return addDismissal(
            plan.copy(sessions = plan.sessions.map {
                if (it.id == targetSessionId) it.copy(suggestion = null) else it
            }),
            targetSessionId, suggestion.reason, now
        )
    }

    private fun addDismissal(
        plan: PlanSnapshot, target: String, source: String, now: Instant
    ): PlanSnapshot {
        if (plan.feedback.size >= 1000)
            throw PlanConstraint("This plan's feedback history is full. Export and review it before adding a dismissal.")
        return plan.copy(feedback = plan.feedback + PlanFeedback(
            UUID.randomUUID().toString(), now, "Workout quality",
            "$RULE_ID dismissed: target=$target; evidence=$source. Original prescription retained.",
            target
        ))
    }
}
