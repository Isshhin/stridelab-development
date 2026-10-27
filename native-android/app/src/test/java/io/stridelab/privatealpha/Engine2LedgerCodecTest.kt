package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** JVM-only persistence format tests. The disk durability suite runs on an emulator. */
class Engine2LedgerCodecTest {
    private val at = Instant.parse("2026-10-28T03:00:00Z")
    private val date = LocalDate.parse("2026-11-02")

    private fun pending(): Engine2ApprovalLedger {
        val p = Engine2PlanView("synthetic-owner", "synthetic-plan", 8,
            Engine2PlanStatus.APPROVED, listOf(
                Engine2SessionTarget("run", date, Engine2Discipline.RUNNING, 60),
                Engine2SessionTarget("strength", date.plusDays(1),
                    Engine2Discipline.STRENGTH, 45)))
        val proposal = Engine2DurationProposal("p1", "synthetic-owner", "synthetic-plan",
            8, "run", date, 60, 40,
            "Fictional athlete requested a shorter training session.",
            setOf("fictional-provider-run"), at)
        return Engine2ApprovalLedgerReducer.stage(Engine2ApprovalLedger(p), proposal)
    }

    @Test fun pendingAndFinalizedDecisionsRoundTripWithoutHistoryLoss() {
        val original = pending()
        assertEquals(original, Engine2LedgerCodec.decode(Engine2LedgerCodec.encode(original)))
        for (decision in Engine2Decision.values()) {
            val next = Engine2ApprovalLedgerReducer.decide(
                original, "p1", decision, at.plusSeconds(60))
            assertEquals(next, Engine2LedgerCodec.decode(Engine2LedgerCodec.encode(next)))
            assertEquals(original.plan.sessions[1], next.plan.sessions[1])
            assertEquals(if (decision == Engine2Decision.APPROVE) 40 else 60,
                next.plan.sessions[0].minutes)
        }
    }

    @Test fun unsupportedSchemaCannotBeSilentlyMislabeledAsCurrent() {
        val raw = JSONObject(Engine2LedgerCodec.encode(pending()))
        raw.put("schema", 2)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(raw.toString())
        }
        assertEquals(Engine2LedgerCodec.SCHEMA,
            JSONObject(Engine2LedgerCodec.encode(pending())).getInt("schema"))
    }

    @Test fun tamperedOwnerRevisionOrDuplicateProposalFailsClosed() {
        val base = JSONObject(Engine2LedgerCodec.encode(pending()))
        val alien = JSONObject(base.toString())
        alien.getJSONObject("plan").put("account", "someone-else")
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(alien.toString())
        }
        val duplicate = JSONObject(base.toString())
        duplicate.getJSONArray("proposals").put(
            duplicate.getJSONArray("proposals").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(duplicate.toString())
        }
        val incorrectStatus = JSONObject(base.toString())
        incorrectStatus.getJSONArray("proposals").getJSONObject(0)
            .put("status", "APPROVED")
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(incorrectStatus.toString())
        }
    }

    @Test fun finalizedReceiptsCannotBeForgedToPointAtAbsentProposal() {
        val committed = Engine2ApprovalLedgerReducer.decide(pending(),
            "p1", Engine2Decision.APPROVE, at.plusSeconds(10))
        val raw = JSONObject(Engine2LedgerCodec.encode(committed))
        raw.getJSONArray("decisions").getJSONObject(0).put("id", "not-a-proposal")
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(raw.toString())
        }
        val wrongTime = JSONObject(Engine2LedgerCodec.encode(committed))
        wrongTime.getJSONArray("decisions").getJSONObject(0)
            .put("at", at.plusSeconds(15).toString())
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerCodec.decode(wrongTime.toString())
        }
    }
}
