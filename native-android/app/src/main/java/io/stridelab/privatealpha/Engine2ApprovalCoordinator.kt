package io.stridelab.privatealpha

import java.time.Instant

/**
 * Explicit user-action facade around the isolated Engine 2.0 ledger.
 * Not connected to existing app screens or Engine 1.0 persistence.
 * A future UI must verify authentication, show a current preview and only call
 * decide after a direct user tap. Never call this from sync or background jobs.
 */
internal class Engine2ApprovalCoordinator(
    private val store: Engine2LedgerSqliteStore
) {
    sealed class Result {
        data class Saved(val ledger: Engine2ApprovalLedger) : Result()
        data class NeedsRefresh(val reason: String) : Result()
    }

    fun prepareForReview(
        accountId: String,
        planId: String,
        context: Engine2AthleteContext,
        proposalId: String,
        sessionId: String,
        reducedMinutes: Int,
        reason: String,
        supportingEvidenceIds: Set<String>,
        at: Instant
    ): Result {
        require(context.ownerId == accountId)
        val original = store.read(accountId, planId)
            ?: return Result.NeedsRefresh("No durable Engine 2.0 plan exists for this account.")
        val proposal = Engine2ProposalWorkflow.prepare(
            original.plan, context, proposalId, sessionId,
            reducedMinutes, reason, supportingEvidenceIds, at)
        if (!store.stageIfRevision(accountId, planId, original.ledgerRevision, proposal)) {
            return Result.NeedsRefresh("Another edit was saved; refresh before reviewing.")
        }
        return Result.Saved(store.read(accountId, planId)!!
            .also { require(it.proposals[proposalId] == proposal) })
    }

    fun decideAfterExplicitUserConfirmation(
        accountId: String,
        planId: String,
        proposalId: String,
        decision: Engine2Decision,
        at: Instant,
        userConfirmed: Boolean
    ): Result {
        require(userConfirmed) { "Only an explicit user's review action may finalize the proposal" }
        val original = store.read(accountId, planId)
            ?: return Result.NeedsRefresh("Plan no longer exists in the local approval ledger.")
        if (original.decisions.any { it.proposalId == proposalId }) {
            return Result.NeedsRefresh("This proposal was already decided; review the saved result.")
        }
        val candidate = Engine2ApprovalTransactions.prepareDecision(
            original, proposalId, decision, at)
        val saved = store.commitIfRevision(
            accountId, planId, candidate.originalLedgerRevision,
            candidate.originalPlanRevision, candidate.updated)
        return if (saved) Result.Saved(store.read(accountId, planId)!!)
        else Result.NeedsRefresh("Saved plan revision changed; reload before deciding.")
    }
}
