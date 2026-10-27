package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * Versioned representation of one Engine 2.0 proposal ledger. No Engine 1.0
 * PlanSnapshot or legacy records are rewritten by this codec.
 */
internal object Engine2LedgerCodec {
    const val SCHEMA = 1

    fun encode(ledger: Engine2ApprovalLedger): String {
        val p = ledger.plan
        val plan = JSONObject()
            .put("account", p.ownerId).put("id", p.planId)
            .put("revision", p.revision).put("status", p.status.name)
            .put("applied", JSONArray(p.appliedProposalIds.sorted()))
            .put("sessions", JSONArray().also { arr ->
                p.sessions.forEach { s ->
                    arr.put(JSONObject().put("id", s.id).put("date", s.date.toString())
                        .put("discipline", s.discipline.name)
                        .put("minutes", s.minutes).put("status", s.status.name))
                }
            })
        val proposals = JSONArray()
        ledger.proposals.toSortedMap().values.forEach { proposal ->
            proposals.put(JSONObject()
                .put("id", proposal.id).put("account", proposal.ownerId)
                .put("planId", proposal.planId)
                .put("expectedRevision", proposal.expectedRevision)
                .put("sessionId", proposal.sessionId)
                .put("originalDate", proposal.originalDate.toString())
                .put("originalMinutes", proposal.originalMinutes)
                .put("suggestedMinutes", proposal.suggestedMinutes)
                .put("reason", proposal.reason)
                .put("evidence", JSONArray(proposal.evidenceRecordIds.sorted()))
                .put("createdAt", proposal.createdAt.toString())
                .put("status", proposal.status.name)
                .put("decidedAt", proposal.decidedAt?.toString()))
        }
        val receipts = JSONArray()
        ledger.decisions.forEach { decision ->
            receipts.put(JSONObject().put("id", decision.proposalId)
                .put("choice", decision.decision.name)
                .put("at", decision.at.toString())
                .put("planRevision", decision.resultingPlanRevision))
        }
        return JSONObject().put("schema", SCHEMA)
            .put("ledgerRevision", ledger.ledgerRevision)
            .put("plan", plan)
            .put("proposals", proposals)
            .put("decisions", receipts).toString()
    }

    fun decode(json: String): Engine2ApprovalLedger {
        val root = JSONObject(json)
        require(root.getInt("schema") == SCHEMA) {
            "Unknown Engine 2.0 ledger version: preserve raw bytes for recovery."
        }
        val original = root.getJSONObject("plan")
        val list = original.getJSONArray("sessions")
        require(list.length() <= 400) { "Excessive session count" }
        val sessions = (0 until list.length()).map { index ->
            val s = list.getJSONObject(index)
            Engine2SessionTarget(s.getString("id"), LocalDate.parse(s.getString("date")),
                Engine2Discipline.valueOf(s.getString("discipline")),
                s.getInt("minutes"),
                Engine2SessionStatus.valueOf(s.getString("status")))
        }
        val plan = Engine2PlanView(
            original.getString("account"), original.getString("id"),
            original.getLong("revision"),
            Engine2PlanStatus.valueOf(original.getString("status")),
            sessions, strings(original.getJSONArray("applied")).toSet())
        val inputProposals = root.getJSONArray("proposals")
        require(inputProposals.length() <= 500)
        val proposals = (0 until inputProposals.length()).map { index ->
            val p = inputProposals.getJSONObject(index)
            Engine2DurationProposal(
                p.getString("id"), p.getString("account"), p.getString("planId"),
                p.getLong("expectedRevision"), p.getString("sessionId"),
                LocalDate.parse(p.getString("originalDate")),
                p.getInt("originalMinutes"), p.getInt("suggestedMinutes"),
                p.getString("reason"), strings(p.getJSONArray("evidence")).toSet(),
                Instant.parse(p.getString("createdAt")),
                Engine2ProposalStatus.valueOf(p.getString("status")),
                p.optString("decidedAt").takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse)
            )
        }
        require(proposals.map { it.id }.distinct().size == proposals.size) {
            "Duplicate proposal IDs in saved ledger"
        }
        val inputDecisions = root.getJSONArray("decisions")
        require(inputDecisions.length() <= 500)
        val decisions = (0 until inputDecisions.length()).map { i ->
            val d = inputDecisions.getJSONObject(i)
            Engine2DecisionReceipt(d.getString("id"),
                Engine2Decision.valueOf(d.getString("choice")),
                Instant.parse(d.getString("at")), d.getLong("planRevision"))
        }
        return Engine2ApprovalLedger(plan, proposals.associateBy { it.id }, decisions,
            root.getLong("ledgerRevision"))
    }

    private fun strings(values: JSONArray): List<String> =
        (0 until values.length()).map { values.getString(it) }
}
