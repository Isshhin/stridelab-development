package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate

/** Experimental pure review workflow. Caller must persist both outputs atomically. */
internal enum class Engine2PlanStatus { APPROVED, PAUSED, DRAFT }
internal enum class Engine2SessionStatus { PLANNED, IN_PROGRESS, COMPLETED, SKIPPED, MISSED }
internal enum class Engine2ProposalStatus { PENDING, APPROVED, REJECTED, POSTPONED }
internal enum class Engine2Decision { APPROVE, REJECT, POSTPONE }

internal data class Engine2SessionTarget(
    val id: String,
    val date: LocalDate,
    val discipline: Engine2Discipline,
    val minutes: Int,
    val status: Engine2SessionStatus = Engine2SessionStatus.PLANNED
) {
    init { require(id.isNotBlank() && minutes in 1..720) }
}

internal data class Engine2PlanView(
    val ownerId: String,
    val planId: String,
    val revision: Long,
    val status: Engine2PlanStatus,
    val sessions: List<Engine2SessionTarget>,
    /** Durable adapter must save this receipt set together with the plan revision. */
    val appliedProposalIds: Set<String> = emptySet()
) {
    init {
        require(ownerId.isNotBlank() && planId.isNotBlank() && revision > 0)
        require(sessions.map { it.id }.distinct().size == sessions.size)
        require(appliedProposalIds.none { it.isBlank() })
    }
}

/** This first slice proposes a shorter session, NOT an unreviewed move or intensity increase. */
internal data class Engine2DurationProposal(
    val id: String,
    val ownerId: String,
    val planId: String,
    val expectedRevision: Long,
    val sessionId: String,
    val originalDate: LocalDate,
    val originalMinutes: Int,
    val suggestedMinutes: Int,
    val reason: String,
    val evidenceRecordIds: Set<String>,
    val createdAt: Instant,
    val status: Engine2ProposalStatus = Engine2ProposalStatus.PENDING,
    val decidedAt: Instant? = null
) {
    init {
        require(id.isNotBlank() && ownerId.isNotBlank() && planId.isNotBlank() && sessionId.isNotBlank())
        require(expectedRevision > 0 && originalMinutes in 1..720)
        require(suggestedMinutes in 10 until originalMinutes)
        require(reason.trim().length in 10..1000)
        require(evidenceRecordIds.none { it.isBlank() })
        require((status == Engine2ProposalStatus.PENDING) == (decidedAt == null))
    }
}

internal data class Engine2DecisionResult(
    val plan: Engine2PlanView,
    val proposal: Engine2DurationProposal
)

internal object Engine2ProposalWorkflow {
    /** Preparing a proposal never changes the approved plan or saved training. */
    fun prepare(
        plan: Engine2PlanView,
        context: Engine2AthleteContext,
        id: String,
        sessionId: String,
        reducedMinutes: Int,
        reason: String,
        evidenceRecordIds: Set<String>,
        at: Instant
    ): Engine2DurationProposal {
        require(context.ownerId == plan.ownerId) { "Athlete account differs from plan" }
        require(plan.status == Engine2PlanStatus.APPROVED) { "Only an approved plan can be revised" }
        require(id !in plan.appliedProposalIds) { "Proposal ID has already been applied" }
        require(evidenceRecordIds.isNotEmpty()) { "Coach-authored proposals require at least one genuine referenced observation" }
        val target = plan.sessions.singleOrNull { it.id == sessionId }
            ?: throw IllegalArgumentException("Session not found")
        require(target.status == Engine2SessionStatus.PLANNED) { "Historical or active sessions are protected" }
        require(evidenceRecordIds.all { ref ->
            context.workouts.any {
                it.id == ref && it.status == Engine2RecordStatus.CONFIRMED &&
                    !it.occurredAt.isAfter(at) &&
                    it.measurements.none { m ->
                        m.source.observedAt.isAfter(at) ||
                            m.source.receivedAt?.isAfter(at) == true
                    }
            } || context.wellness.any {
                it.id == ref && !it.metric.source.observedAt.isAfter(at) &&
                    it.metric.source.receivedAt?.isAfter(at) != true
            }
        }) { "Proposal cites unavailable, draft, planned or future evidence" }
        require(!at.isAfter(context.asOf)) { "Proposal time must be covered by the context snapshot" }
        return Engine2DurationProposal(
            id, plan.ownerId, plan.planId, plan.revision, sessionId,
            target.date, target.minutes, reducedMinutes, reason, evidenceRecordIds, at
        )
    }

    /** Produces candidate snapshots only; durable store must compare revisions and save atomically. */
    fun decide(
        plan: Engine2PlanView,
        proposal: Engine2DurationProposal,
        decision: Engine2Decision,
        at: Instant
    ): Engine2DecisionResult {
        require(proposal.ownerId == plan.ownerId && proposal.planId == plan.planId)
        require(!at.isBefore(proposal.createdAt))
        val finalStatus = when (decision) {
            Engine2Decision.APPROVE -> Engine2ProposalStatus.APPROVED
            Engine2Decision.REJECT -> Engine2ProposalStatus.REJECTED
            Engine2Decision.POSTPONE -> Engine2ProposalStatus.POSTPONED
        }
        if (proposal.status != Engine2ProposalStatus.PENDING) {
            require(proposal.status == finalStatus) { "A finalized proposal cannot change its decision" }
            // Replaying an approval needs its durable idempotency receipt, not a coincidental minute match.
            if (finalStatus == Engine2ProposalStatus.APPROVED) {
                val current = plan.sessions.singleOrNull { it.id == proposal.sessionId }
                require(proposal.id in plan.appliedProposalIds &&
                    plan.revision >= proposal.expectedRevision + 1 &&
                    current != null && current.minutes == proposal.suggestedMinutes &&
                    current.date == proposal.originalDate) { "Approved receipt does not match persisted plan" }
            }
            return Engine2DecisionResult(plan, proposal)
        }
        require(plan.status == Engine2PlanStatus.APPROVED)
        require(plan.revision == proposal.expectedRevision) { "Stale plan revision: refresh before reviewing" }
        val current = plan.sessions.singleOrNull { it.id == proposal.sessionId }
            ?: throw IllegalArgumentException("The original workout is unavailable")
        require(current.status == Engine2SessionStatus.PLANNED &&
            current.date == proposal.originalDate &&
            current.minutes == proposal.originalMinutes) { "Original workout changed since proposal creation" }
        val candidate = if (decision == Engine2Decision.APPROVE) {
            require(plan.revision < Long.MAX_VALUE)
            require(proposal.id !in plan.appliedProposalIds)
            plan.copy(
                revision = plan.revision + 1,
                appliedProposalIds = plan.appliedProposalIds + proposal.id,
                sessions = plan.sessions.map {
                    if (it.id == proposal.sessionId) it.copy(minutes = proposal.suggestedMinutes) else it
                }
            )
        } else plan
        return Engine2DecisionResult(
            candidate,
            proposal.copy(status = finalStatus, decidedAt = at)
        )
    }
}
