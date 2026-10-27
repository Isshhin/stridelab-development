package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2Batch3Test {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val monday = LocalDate.parse("2026-10-26")
    private fun plan() = Engine2PlanView("owner", "plan", 5, Engine2PlanStatus.APPROVED,
        listOf(Engine2SessionTarget("run", monday.plusDays(1),
            Engine2Discipline.RUNNING, 50)))
    private fun source(provider: String, record: String) =
        Engine2Source("owner", provider, record, now.minusSeconds(1000), now)
    private fun confirmed(id: String, provider: String, sourceId: String) =
        Engine2WorkoutEvidence(id, Engine2Discipline.RUNNING,
            Instant.parse("2026-10-27T09:30:00Z"), Engine2RecordStatus.CONFIRMED,
            listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 5.5,
                source(provider, sourceId))))
    private fun ctx(vararg workouts: Engine2WorkoutEvidence) =
        Engine2AthleteContext("owner", now, workouts.toList(), emptyList())

    @Test fun dateMatchingNeverAutomaticallyLinksAndRetainsMultipleCandidates() {
        val a = confirmed("a", "synthetic-provider-a", "1")
        val b = confirmed("b", "synthetic-provider-b", "2")
        val review = Engine2ActivityMatcher.findCandidates(plan(), ctx(a, b), "run")
        assertEquals(2, review.candidates.size)
        assertFalse(review.automaticLinkAllowed)
        assertTrue(review.warnings.any { it.contains("Multiple") })
        assertEquals(listOf("a", "b"), review.candidates.map { it.workoutId })
    }

    @Test fun exactSourceCollisionIsExcludedFromLinkCandidates() {
        val a = confirmed("a", "synthetic-provider", "same")
        val b = confirmed("b", "synthetic-provider", "same")
        val review = Engine2ActivityMatcher.findCandidates(plan(), ctx(a, b), "run")
        assertTrue(review.candidates.isEmpty())
        assertFalse(review.automaticLinkAllowed)
        assertTrue(review.warnings.any { it.contains("Duplicate") })
    }

    @Test fun unknownWorkoutDistanceNeverBecomesFitnessImprovementClaim() {
        val record = Engine2WorkoutEvidence("unknown", Engine2Discipline.RUNNING,
            now.minusSeconds(500), Engine2RecordStatus.CONFIRMED)
        val insight = Engine2FitnessInsights.running(ctx(record), 7)
        assertEquals(Engine2InsightConfidence.PARTIAL, insight.confidence)
        assertNull(insight.knownRecentKm)
        assertTrue(insight.explanation.contains("incomplete") ||
            insight.explanation.contains("lack recorded distance"))
    }

    @Test fun missingRunningHistoryIsNotZeroTrainingProof() {
        val insight = Engine2FitnessInsights.running(ctx(), 7)
        assertEquals(Engine2InsightConfidence.UNKNOWN, insight.confidence)
        assertTrue(insight.explanation.contains("do not establish"))
    }

    @Test fun volatileStoreComparesRevisionsAndRejectsTamperedCandidates() {
        val approved = plan()
        val pending = Engine2DurationProposal("proposal", "owner", "plan", 5,
            "run", monday.plusDays(1), 50, 30,
            "Athlete requested a shorter, reviewed training session.", setOf("source"), now)
        val staged = Engine2ApprovalLedgerReducer.stage(
            Engine2ApprovalLedger(approved), pending)
        val store = Engine2VolatileLedgerStore()
        store.seed(staged)
        val candidate = Engine2ApprovalTransactions.prepareDecision(
            staged, "proposal", Engine2Decision.APPROVE, now.plusSeconds(1))
        val forged = candidate.updated.copy(plan = candidate.updated.plan.copy(
            sessions = candidate.updated.plan.sessions.map { it.copy(minutes = 5) }))
        assertThrows(IllegalArgumentException::class.java) {
            store.commitIfRevision("owner", "plan",
                candidate.originalLedgerRevision, candidate.originalPlanRevision, forged)
        }
        assertEquals(staged, store.read("owner", "plan"))
        assertTrue(store.commitIfRevision("owner", "plan",
            candidate.originalLedgerRevision, candidate.originalPlanRevision, candidate.updated))
        assertFalse(store.commitIfRevision("owner", "plan",
            candidate.originalLedgerRevision, candidate.originalPlanRevision, candidate.updated))
        assertEquals(6L, store.read("owner", "plan")!!.plan.revision)
        assertThrows(IllegalArgumentException::class.java) {
            store.seed(staged)
        }
    }

    @Test fun rejectedProposalsDoNotModifyApprovedTrainingInReferenceStore() {
        val p = plan()
        val proposal = Engine2DurationProposal("p", "owner", "plan", 5,
            "run", monday.plusDays(1), 50, 30,
            "Athlete asked to review a shorter training alternative.", setOf("source"), now)
        val staged = Engine2ApprovalLedgerReducer.stage(Engine2ApprovalLedger(p), proposal)
        val store = Engine2VolatileLedgerStore()
        store.seed(staged)
        val next = Engine2ApprovalTransactions.prepareDecision(staged, "p",
            Engine2Decision.REJECT, now.plusSeconds(2))
        assertTrue(store.commitIfRevision("owner", "plan",
            staged.ledgerRevision, p.revision, next.updated))
        assertEquals(p, store.read("owner", "plan")!!.plan)
        assertEquals(Engine2ProposalStatus.REJECTED,
            store.read("owner", "plan")!!.proposals.getValue("p").status)
    }
}
