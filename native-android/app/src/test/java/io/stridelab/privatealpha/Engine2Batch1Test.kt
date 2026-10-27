package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2Batch1Test {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val monday = LocalDate.parse("2026-11-02")
    private val source = Engine2Source("owner", "synthetic-provider", "run-1",
        now.minusSeconds(3600), now.minusSeconds(10))

    @Test fun inputAssemblerRejectsCrossAccountAndNonFiniteMetricsWithoutDiscardingValidHistory() {
        val safe = Engine2IncomingWorkout("run", Engine2Discipline.RUNNING, now.minusSeconds(3600),
            Engine2RecordStatus.CONFIRMED, source,
            listOf(Engine2RawMetric(Engine2Metric.DISTANCE_KM, 6.5)))
        val bad = safe.copy(id = "foreign", source = source.copy(accountId = "other"))
        val invalid = safe.copy(id = "invalid", measurements =
            listOf(Engine2RawMetric(Engine2Metric.DISTANCE_KM, Double.NaN)))
        val output = Engine2ContextAssembler.build("owner", now, listOf(safe, bad, invalid),
            emptyList())
        assertEquals(1, output.context.workouts.size)
        assertEquals(2, output.rejected.size)
        assertEquals(6.5, output.context.runningCoverage().knownDistanceKm!!, 0.0)
        assertEquals(1, output.provenance.distinctProviderRecords)
    }

    @Test fun approvalLedgerStagesAndAppliesExactlyOnceWithoutSilentMutation() {
        val plan = Engine2PlanView("owner", "plan", 3, Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("run", monday, Engine2Discipline.RUNNING, 50)))
        val proposed = Engine2DurationProposal("proposal", "owner", "plan", 3,
            "run", monday, 50, 30,
            "Offer a lower duration after reviewing actual training.", setOf("run-1"), now)
        val original = Engine2ApprovalLedger(plan)
        val staged = Engine2ApprovalLedgerReducer.stage(original, proposed)
        assertEquals(original.plan, staged.plan)
        assertEquals(1, staged.proposals.size)
        assertEquals(staged, Engine2ApprovalLedgerReducer.stage(staged, proposed))
        val candidate = Engine2ApprovalTransactions.prepareDecision(staged, "proposal",
            Engine2Decision.APPROVE, now.plusSeconds(1))
        assertEquals(3, candidate.originalPlanRevision)
        assertEquals(2, candidate.originalLedgerRevision)
        val saved = candidate.updated
        assertEquals(4L, saved.plan.revision)
        assertEquals(30, saved.plan.sessions.first().minutes)
        assertEquals(1, saved.decisions.size)
        assertEquals(saved, Engine2ApprovalLedgerReducer.decide(
            saved, "proposal", Engine2Decision.APPROVE, now.plusSeconds(2)))
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ApprovalLedgerReducer.decide(saved, "proposal",
                Engine2Decision.REJECT, now.plusSeconds(2))
        }
        assertEquals(50, original.plan.sessions.first().minutes)
    }

    @Test fun competingPendingChangesToSameWorkoutNeedReviewFirst() {
        val plan = Engine2PlanView("owner", "plan", 3, Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("run", monday, Engine2Discipline.RUNNING, 50)))
        val p1 = Engine2DurationProposal("p1", "owner", "plan", 3, "run", monday, 50,
            30, "Review the original session before modifying anything.", setOf("run"), now)
        val p2 = p1.copy(id = "p2", suggestedMinutes = 35)
        val ledger = Engine2ApprovalLedgerReducer.stage(Engine2ApprovalLedger(plan), p1)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ApprovalLedgerReducer.stage(ledger, p2)
        }
        val rejected = Engine2ApprovalLedgerReducer.decide(ledger, "p1",
            Engine2Decision.REJECT, now.plusSeconds(1))
        assertEquals(plan, rejected.plan)
        assertEquals(Engine2ProposalStatus.REJECTED, rejected.proposals.getValue("p1").status)
        assertEquals(2, Engine2ApprovalLedgerReducer.stage(rejected, p2).proposals.size)
    }

    @Test fun workloadTrendOnlyComparesFullyObservedAndNonCollidingDistances() {
        val srcOld = source.copy(recordId = "old", observedAt = now.minusSeconds(40L * 86400),
            receivedAt = now.minusSeconds(38L * 86400))
        val srcNew = source.copy(recordId = "new")
        val old = Engine2WorkoutEvidence("old", Engine2Discipline.RUNNING,
            now.minusSeconds(40L * 86400), Engine2RecordStatus.CONFIRMED,
            listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 8.0, srcOld)))
        val recent = Engine2WorkoutEvidence("new", Engine2Discipline.RUNNING,
            now.minusSeconds(3600), Engine2RecordStatus.CONFIRMED,
            listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 10.0, srcNew)))
        val ctx = Engine2AthleteContext("owner", now, listOf(old, recent), emptyList())
        val trend = Engine2WorkloadReview.compare(ctx, 28)
        assertTrue(trend.comparableRunningDistance)
        assertEquals(2.0, trend.runningDistanceChangeKm!!, 0.0)
        val unknown = ctx.copy(workouts = listOf(old, recent.copy(measurements = emptyList())))
        assertFalse(Engine2WorkloadReview.compare(unknown).comparableRunningDistance)
        assertNull(Engine2WorkloadReview.compare(unknown).runningDistanceChangeKm)
    }

    @Test fun unifiedGeneratorKeepsAllDisciplinesAndLeavesScheduleUnapproved() {
        val req = Engine2PlanningRequest(
            monday, 4, (1..6).toSet(),
            mapOf(Engine2Discipline.RUNNING to 3, Engine2Discipline.STRENGTH to 2,
                Engine2Discipline.CALISTHENICS to 1),
            preferredLongRunDay = 6, preferredQualityDays = setOf(2)
        )
        val first = Engine2UnifiedPlanGenerator.generate("owner", "draft", req)
        val second = Engine2UnifiedPlanGenerator.generate("owner", "draft", req)
        assertEquals(first, second)
        assertEquals(24, first.plan.sessions.size)
        assertEquals(Engine2PlanStatus.DRAFT, first.plan.status)
        assertTrue(first.plan.appliedProposalIds.isEmpty())
        assertEquals(12, first.plan.sessions.count { it.discipline == Engine2Discipline.RUNNING })
        assertTrue(first.demands.filter { it.longRun }.all { d ->
            first.plan.sessions.single { it.id == d.sessionId }.date.dayOfWeek.value == 6
        })
        assertTrue(first.plan.sessions.all { it.status == Engine2SessionStatus.PLANNED })
    }

    @Test fun infeasibleRequestsFailRatherThanDroppingOrStackingSessions() {
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, setOf(1, 2),
                mapOf(Engine2Discipline.RUNNING to 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, (1..7).toSet(),
                mapOf(Engine2Discipline.RUNNING to 7), requireRestDay = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanningRequest(monday, 4, setOf(1, 2, 3),
                mapOf(Engine2Discipline.CALISTHENICS to 2),
                preferredLongRunDay = 2)
        }
    }
}
