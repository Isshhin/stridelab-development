package io.stridelab.privatealpha

import java.time.Instant

/**
 * One immutable in-memory transaction candidate. The native UI must not treat it
 * as saved until a storage adapter atomically persists plan, proposal and receipt.
 */
internal data class Engine2DecisionReceipt(
    val proposalId: String,
    val decision: Engine2Decision,
    val at: Instant,
    val resultingPlanRevision: Long
)

internal data class Engine2ApprovalLedger(
    val plan: Engine2PlanView,
    val proposals: Map<String, Engine2DurationProposal> = emptyMap(),
    val decisions: List<Engine2DecisionReceipt> = emptyList(),
    val ledgerRevision: Long = 1
) {
    init {
        require(ledgerRevision > 0)
        require(proposals.all { (id, p) ->
            id == p.id && p.ownerId == plan.ownerId && p.planId == plan.planId
        })
        require(decisions.map { it.proposalId }.distinct().size == decisions.size)
        require(proposals.values.filter { it.status == Engine2ProposalStatus.PENDING }
            .map { it.sessionId }.distinct().size ==
            proposals.values.count { it.status == Engine2ProposalStatus.PENDING })
        require(decisions.all { receipt ->
            val proposal = proposals[receipt.proposalId]
            val expected = when (receipt.decision) {
                Engine2Decision.APPROVE -> Engine2ProposalStatus.APPROVED
                Engine2Decision.REJECT -> Engine2ProposalStatus.REJECTED
                Engine2Decision.POSTPONE -> Engine2ProposalStatus.POSTPONED
            }
            proposal != null && proposal.status == expected &&
                proposal.decidedAt == receipt.at &&
                receipt.resultingPlanRevision <= plan.revision &&
                (receipt.decision != Engine2Decision.APPROVE ||
                    proposal.id in plan.appliedProposalIds)
        })
    }
}

internal object Engine2ApprovalLedgerReducer {
    fun stage(ledger: Engine2ApprovalLedger, proposal: Engine2DurationProposal): Engine2ApprovalLedger {
        require(proposal.status == Engine2ProposalStatus.PENDING)
        require(proposal.ownerId == ledger.plan.ownerId && proposal.planId == ledger.plan.planId)
        val existing = ledger.proposals[proposal.id]
        if (existing != null) {
            require(existing == proposal) { "Proposal ID reused with conflicting details" }
            return ledger
        }
        require(ledger.plan.status == Engine2PlanStatus.APPROVED)
        require(ledger.plan.revision == proposal.expectedRevision)
        val session = ledger.plan.sessions.singleOrNull { it.id == proposal.sessionId }
            ?: throw IllegalArgumentException("Proposal refers to a missing workout")
        require(session.status == Engine2SessionStatus.PLANNED &&
            session.date == proposal.originalDate &&
            session.minutes == proposal.originalMinutes)
        require(ledger.proposals.values.none {
            it.status == Engine2ProposalStatus.PENDING &&
                it.sessionId == proposal.sessionId
        }) { "Review or dismiss the pending proposal for this workout first" }
        require(ledger.ledgerRevision < Long.MAX_VALUE)
        return ledger.copy(
            proposals = ledger.proposals + (proposal.id to proposal),
            ledgerRevision = ledger.ledgerRevision + 1
        )
    }

    fun decide(
        ledger: Engine2ApprovalLedger,
        proposalId: String,
        decision: Engine2Decision,
        at: Instant
    ): Engine2ApprovalLedger {
        val proposed = ledger.proposals[proposalId]
            ?: throw IllegalArgumentException("Proposal not found")
        val existingReceipt = ledger.decisions.firstOrNull { it.proposalId == proposalId }
        if (existingReceipt != null) {
            require(existingReceipt.decision == decision) {
                "A finalized proposal cannot change its decision"
            }
            require(proposed.status != Engine2ProposalStatus.PENDING)
            if (decision == Engine2Decision.APPROVE) {
                // Revalidate the stored durable receipt rather than a coincidental minute match.
                Engine2ProposalWorkflow.decide(ledger.plan, proposed, decision, at)
            }
            return ledger
        }
        val result = Engine2ProposalWorkflow.decide(ledger.plan, proposed, decision, at)
        require(ledger.ledgerRevision < Long.MAX_VALUE)
        return ledger.copy(
            plan = result.plan,
            proposals = ledger.proposals + (proposalId to result.proposal),
            decisions = ledger.decisions + Engine2DecisionReceipt(
                proposalId, decision, at, result.plan.revision
            ),
            ledgerRevision = ledger.ledgerRevision + 1
        )
    }
}

/** Production implementation MUST compare-and-swap AND durably commit all ledger fields in one SQLite transaction. */
internal interface Engine2LedgerCommitPort {
    fun commitIfRevision(
        accountId: String,
        planId: String,
        expectedLedgerRevision: Long,
        expectedPlanRevision: Long,
        updated: Engine2ApprovalLedger
    ): Boolean
}

/** Produces candidate state; it cannot claim to have saved it. */
internal data class Engine2LedgerCommitCandidate(
    val originalLedgerRevision: Long,
    val originalPlanRevision: Long,
    val updated: Engine2ApprovalLedger
)

internal object Engine2ApprovalTransactions {
    fun prepareDecision(
        snapshot: Engine2ApprovalLedger,
        proposalId: String,
        decision: Engine2Decision,
        at: Instant
    ): Engine2LedgerCommitCandidate {
        return Engine2LedgerCommitCandidate(
            snapshot.ledgerRevision, snapshot.plan.revision,
            Engine2ApprovalLedgerReducer.decide(snapshot, proposalId, decision, at)
        )
    }
}
