package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Synthetic evidence only. No network, device fixtures or user records. */
class Engine2FoundationTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val date = LocalDate.parse("2026-11-02")
    private fun source(
        account: String = "athlete-a",
        observed: Instant = now.minusSeconds(3600)
    ) = Engine2Source(account, "synthetic-manual", "rec-1", observed, now)

    private fun run(id: String = "real-run", distance: Double? = 6.0,
                    status: Engine2RecordStatus = Engine2RecordStatus.CONFIRMED) =
        Engine2WorkoutEvidence(id, Engine2Discipline.RUNNING, now.minusSeconds(3600), status,
            distance?.let { listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, it, source())) } ?: emptyList())

    private fun context(workouts: List<Engine2WorkoutEvidence> = listOf(run()),
                        wellness: List<Engine2WellnessEvidence> = emptyList()) =
        Engine2AthleteContext("athlete-a", now, workouts, wellness, mapOf("dumbbells" to 2),
            listOf("General fitness"))

    private fun plan(target: Engine2SessionTarget = session()) =
        Engine2PlanView("athlete-a", "approved-plan", 7L, Engine2PlanStatus.APPROVED,
            listOf(target, Engine2SessionTarget("other", date.plusDays(1),
                Engine2Discipline.STRENGTH, 40)))

    private fun session(status: Engine2SessionStatus = Engine2SessionStatus.PLANNED) =
        Engine2SessionTarget("session-1", date, Engine2Discipline.RUNNING, 60, status)

    private fun proposal(p: Engine2PlanView = plan(), ctx: Engine2AthleteContext = context()) =
        Engine2ProposalWorkflow.prepare(p, ctx, "proposal-1", "session-1", 35,
            "Recorded workload suggests offering a shorter session.", setOf("real-run"), now)

    @Test fun observedRunCoverageNeverTreatsMissingOrDraftAsZero() {
        val ctx = context(listOf(run(), run("no-distance", null),
            run("draft", 50.0, Engine2RecordStatus.DRAFT),
            Engine2WorkoutEvidence("planned", Engine2Discipline.RUNNING, now.plusSeconds(86400),
                Engine2RecordStatus.PLANNED)))
        val summary = ctx.runningCoverage()
        assertEquals(2, summary.confirmedRuns)
        assertEquals(1, summary.runsWithDistance)
        assertEquals(1, summary.runsMissingDistance)
        assertEquals(6.0, summary.knownDistanceKm!!, 0.0)
        assertFalse(summary.complete)
        assertNull(context(listOf(run(distance = null))).runningCoverage().knownDistanceKm)
        assertFalse(context(emptyList()).runningCoverage().complete)
        assertEquals(0, context(emptyList()).runningCoverage().confirmedRuns)
    }

    @Test fun contextEnforcesAccountOwnershipAndObservationTimestamps() {
        val foreign = run().copy(measurements = listOf(
            Engine2Measurement(Engine2Metric.DISTANCE_KM, 3.0, source("athlete-b"))))
        assertThrows(IllegalArgumentException::class.java) { context(listOf(foreign)) }
        val future = Engine2Source("athlete-a", "synthetic-provider", "future",
            now.plusSeconds(1), null)
        assertThrows(IllegalArgumentException::class.java) { context(listOf(
            run().copy(measurements = listOf(Engine2Measurement(
                Engine2Metric.DISTANCE_KM, 4.0, future))))) }
        assertThrows(IllegalArgumentException::class.java) { context(listOf(run(), run())) }
        assertThrows(IllegalArgumentException::class.java) { context(listOf(
            run().copy(occurredAt = now.plusSeconds(10)))) }
    }

    @Test fun plannedWorkoutsCannotFabricateActualsAndSetsHaveUnitsAndIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            Engine2WorkoutEvidence("future", Engine2Discipline.RUNNING, now,
                Engine2RecordStatus.PLANNED,
                listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 9.0, source())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2Measurement(Engine2Metric.DISTANCE_KM, Double.NaN, source())
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2Measurement(Engine2Metric.REPS, 8.0, source())
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2Measurement(Engine2Metric.DISTANCE_KM, 4.0, source(),
                exerciseId = "sq", setNumber = 1)
        }
        assertEquals(0.0, Engine2Measurement(Engine2Metric.LOAD_KG, 0.0,
            source(), "sq", 1).value, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2WorkoutEvidence("wrong-wellness", Engine2Discipline.RUNNING, now,
                Engine2RecordStatus.CONFIRMED, listOf(
                    Engine2Measurement(Engine2Metric.HRV_MS, 60.0, source())))
        }
        val wellness = Engine2WellnessEvidence("wellness-1",
            Engine2Measurement(Engine2Metric.HRV_MS, 60.0, source()))
        assertEquals("wellness-1", context(wellness = listOf(wellness)).wellness.single().id)
    }

    @Test fun prepareOnlyCreatesPendingProposalAndRequiresGroundedPastEvidence() {
        val p = plan()
        val before = p.copy()
        val draft = proposal(p)
        assertEquals(before, p)
        assertEquals(7L, p.revision)
        assertEquals(Engine2ProposalStatus.PENDING, draft.status)
        assertNull(draft.decidedAt)
        assertEquals(35, draft.suggestedMinutes)
        assertEquals(60, draft.originalMinutes)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.prepare(p, context(), "bad", "session-1",
                35, "Explained adjustment.", setOf("unknown-source"), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.prepare(p, context(listOf(run(status = Engine2RecordStatus.DRAFT))),
                "bad", "session-1", 35, "Explained adjustment.", setOf("real-run"), now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.prepare(p, context(), "bad", "session-1",
                35, "Explained adjustment.", setOf("real-run"), now.minusSeconds(7200))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.prepare(p, context(), "bad", "session-1",
                60, "Explained adjustment.", setOf("real-run"), now)
        }
    }

    @Test fun explicitApprovalChangesOnlyOneProposedTargetAndCarriesReceipt() {
        val p = plan()
        val pending = proposal(p)
        val result = Engine2ProposalWorkflow.decide(p, pending, Engine2Decision.APPROVE,
            now.plusSeconds(1))
        assertEquals(60, p.sessions.first().minutes)
        assertEquals(35, result.plan.sessions.first().minutes)
        assertEquals(p.sessions.last(), result.plan.sessions.last())
        assertEquals(8L, result.plan.revision)
        assertTrue("proposal-1" in result.plan.appliedProposalIds)
        assertEquals(Engine2ProposalStatus.APPROVED, result.proposal.status)
        assertEquals(now.plusSeconds(1), result.proposal.decidedAt)
        assertEquals(result, Engine2ProposalWorkflow.decide(result.plan,
            result.proposal, Engine2Decision.APPROVE, now.plusSeconds(2)))
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.decide(p, result.proposal, Engine2Decision.APPROVE,
                now.plusSeconds(2))
        }
    }

    @Test fun rejectionAndPostponementNeverRewriteAnApprovedPlan() {
        for (choice in listOf(Engine2Decision.REJECT, Engine2Decision.POSTPONE)) {
            val p = plan()
            val pending = proposal(p)
            val result = Engine2ProposalWorkflow.decide(p, pending, choice, now.plusSeconds(1))
            assertEquals(p, result.plan)
            assertEquals(p.revision, result.plan.revision)
            assertEquals(if (choice == Engine2Decision.REJECT)
                Engine2ProposalStatus.REJECTED else Engine2ProposalStatus.POSTPONED,
                result.proposal.status)
            assertEquals(result, Engine2ProposalWorkflow.decide(result.plan,
                result.proposal, choice, now.plusSeconds(2)))
            assertThrows(IllegalArgumentException::class.java) {
                Engine2ProposalWorkflow.decide(result.plan, result.proposal,
                    Engine2Decision.APPROVE, now.plusSeconds(3))
            }
        }
    }

    @Test fun staleRevisionChangedSessionAndProtectedHistoryFailClosed() {
        val p = plan()
        val pending = proposal(p)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.decide(p.copy(revision = 8L), pending,
                Engine2Decision.APPROVE, now.plusSeconds(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.decide(p.copy(sessions = p.sessions.map {
                if (it.id == "session-1") it.copy(minutes = 45) else it
            }), pending, Engine2Decision.APPROVE, now.plusSeconds(1))
        }
        for (status in listOf(Engine2SessionStatus.COMPLETED, Engine2SessionStatus.SKIPPED,
                Engine2SessionStatus.MISSED, Engine2SessionStatus.IN_PROGRESS)) {
            assertThrows(IllegalArgumentException::class.java) {
                Engine2ProposalWorkflow.prepare(plan(session(status)), context(),
                    "blocked", "session-1", 30, "Respect recorded training.", emptySet(), now)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ProposalWorkflow.decide(p.copy(ownerId = "athlete-b"),
                pending, Engine2Decision.APPROVE, now.plusSeconds(1))
        }
    }
}
