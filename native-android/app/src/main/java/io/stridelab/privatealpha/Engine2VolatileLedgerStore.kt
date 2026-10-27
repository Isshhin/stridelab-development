package io.stridelab.privatealpha

/**
 * A volatile correctness reference for revision-based atomic decisions.
 * NOT a disk-backed store, user database, persistence implementation or migration.
 * Intended to validate transaction boundaries before adding an SQLite adapter.
 */
internal class Engine2VolatileLedgerStore : Engine2LedgerCommitPort {
    private val lock = Any()
    private val ledgers = mutableMapOf<Pair<String, String>, Engine2ApprovalLedger>()

    fun seed(snapshot: Engine2ApprovalLedger) = synchronized(lock) {
        val key = snapshot.plan.ownerId to snapshot.plan.planId
        require(key !in ledgers) { "Cannot silently replace an existing plan ledger" }
        ledgers[key] = snapshot
        Unit
    }

    fun read(accountId: String, planId: String): Engine2ApprovalLedger? = synchronized(lock) {
        ledgers[accountId to planId]
    }

    override fun commitIfRevision(
        accountId: String,
        planId: String,
        expectedLedgerRevision: Long,
        expectedPlanRevision: Long,
        updated: Engine2ApprovalLedger
    ): Boolean = synchronized(lock) {
        val key = accountId to planId
        val existing = ledgers[key] ?: return@synchronized false
        if (existing.ledgerRevision != expectedLedgerRevision ||
            existing.plan.revision != expectedPlanRevision) return@synchronized false
        require(updated.plan.ownerId == accountId && updated.plan.planId == planId)
        require(updated.ledgerRevision == existing.ledgerRevision + 1)
        require(updated.decisions.size == existing.decisions.size + 1)
        require(updated.decisions.take(existing.decisions.size) == existing.decisions)
        val decisionReceipt = updated.decisions.last()
        val recomputed = Engine2ApprovalLedgerReducer.decide(
            existing, decisionReceipt.proposalId,
            decisionReceipt.decision, decisionReceipt.at
        )
        require(recomputed == updated) {
            "Mutation must equal the deterministic approval reducer's exact candidate"
        }
        ledgers[key] = updated
        true
    }
}
