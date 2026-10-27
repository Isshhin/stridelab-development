package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** Real Android tests, NOT yet executed under emulator CI. */
@RunWith(AndroidJUnit4::class)
class Engine2LedgerSqliteInstrumentedTest {
    private val now = Instant.parse("2026-10-28T03:00:00Z")
    private val date = LocalDate.parse("2026-11-02")
    private fun initial(owner: String) = Engine2ApprovalLedger(
        Engine2PlanView(owner, "training", 5, Engine2PlanStatus.APPROVED,
            listOf(
                Engine2SessionTarget("run", date, Engine2Discipline.RUNNING, 60),
                Engine2SessionTarget("already-done", date.minusDays(1),
                    Engine2Discipline.STRENGTH, 40, Engine2SessionStatus.COMPLETED)
            ))
    )
    private fun proposal(owner: String) = Engine2DurationProposal(
        "review-1", owner, "training", 5, "run", date, 60, 35,
        "Fictional owner requested an optional lighter future running session.",
        setOf("synthetic-observation"), now
    )

    @Test fun seedStageApproveReopenAndRejectReplayWithSameDurableReceipts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "engine2-private-test-" + System.nanoTime() + ".db"
        context.deleteDatabase(name)
        try {
            Engine2LedgerSqliteStore(context, name).use { store ->
                val first = initial("account-a")
                assertTrue(store.seedIfAbsent(first))
                assertFalse(store.seedIfAbsent(first))
                assertTrue(store.stageIfRevision("account-a", "training", 1,
                    proposal("account-a")))
                assertFalse(store.stageIfRevision("account-a", "training", 1,
                    proposal("account-a").copy(id = "another")))
                assertEquals(60, store.read("account-a", "training")!!.plan.sessions.first().minutes)
                val staged = store.read("account-a", "training")!!
                val candidate = Engine2ApprovalTransactions.prepareDecision(
                    staged, "review-1", Engine2Decision.APPROVE, now.plusSeconds(10))
                assertTrue(store.commitIfRevision("account-a", "training",
                    candidate.originalLedgerRevision, candidate.originalPlanRevision,
                    candidate.updated))
                assertFalse(store.commitIfRevision("account-a", "training",
                    candidate.originalLedgerRevision, candidate.originalPlanRevision,
                    candidate.updated))
                assertEquals(35, store.read("account-a", "training")!!.plan.sessions.first().minutes)
                assertEquals(Engine2SessionStatus.COMPLETED,
                    store.read("account-a", "training")!!.plan.sessions.last().status)
                assertNull(store.read("account-b", "training"))
            }
            Engine2LedgerSqliteStore(context, name).use { reopened ->
                val persisted = reopened.read("account-a", "training")!!
                assertEquals(35, persisted.plan.sessions.first().minutes)
                assertEquals(6L, persisted.plan.revision)
                assertEquals(1, persisted.decisions.size)
                assertEquals(Engine2ProposalStatus.APPROVED,
                    persisted.proposals.getValue("review-1").status)
                assertThrows(IllegalArgumentException::class.java) {
                    reopened.stageIfRevision("account-a", "training",
                        persisted.ledgerRevision, proposal("account-a"))
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun failedTransactionDoesNotChangeSavedPlanOrExistingOutboxDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "engine2-reject-test-" + System.nanoTime() + ".db"
        try {
            Engine2LedgerSqliteStore(context, name).use { store ->
                val original = initial("account-b")
                assertTrue(store.seedIfAbsent(original))
                assertTrue(store.stageIfRevision("account-b", "training", 1,
                    proposal("account-b")))
                val staged = store.read("account-b", "training")!!
                val accepted = Engine2ApprovalTransactions.prepareDecision(staged,
                    "review-1", Engine2Decision.APPROVE, now.plusSeconds(2))
                val forged = accepted.updated.copy(plan = accepted.updated.plan.copy(
                    sessions = accepted.updated.plan.sessions.map {
                        if (it.id == "run") it.copy(minutes = 1) else it
                    }))
                assertThrows(IllegalArgumentException::class.java) {
                    store.commitIfRevision("account-b", "training",
                        accepted.originalLedgerRevision, accepted.originalPlanRevision, forged)
                }
                assertEquals(staged, store.read("account-b", "training"))
                assertTrue(store.commitIfRevision("account-b", "training",
                    accepted.originalLedgerRevision, accepted.originalPlanRevision,
                    Engine2ApprovalTransactions.prepareDecision(staged,
                        "review-1", Engine2Decision.REJECT, now.plusSeconds(3)).updated))
                assertEquals(60, store.read("account-b", "training")!!.plan.sessions.first().minutes)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun userConfirmationFacadeRejectsSilentDecisionsAndSurvivesReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "engine2-coordinator-test-" + System.nanoTime() + ".db"
        try {
            Engine2LedgerSqliteStore(context, name).use { store ->
                assertTrue(store.seedIfAbsent(initial("account-c")))
                val source = Engine2Source("account-c", "synthetic-provider", "actual-1",
                    now.minusSeconds(3600), now.minusSeconds(60))
                val athlete = Engine2AthleteContext("account-c", now,
                    listOf(Engine2WorkoutEvidence("actual-1", Engine2Discipline.RUNNING,
                        now.minusSeconds(3600), Engine2RecordStatus.CONFIRMED,
                        listOf(Engine2Measurement(Engine2Metric.DISTANCE_KM, 5.0, source)))),
                    emptyList())
                val coordinator = Engine2ApprovalCoordinator(store)
                val staged = coordinator.prepareForReview("account-c", "training", athlete,
                    "review-1", "run", 35,
                    "Athlete explicitly requested a shorter future session.",
                    setOf("actual-1"), now)
                assertTrue(staged is Engine2ApprovalCoordinator.Result.Saved)
                assertThrows(IllegalArgumentException::class.java) {
                    coordinator.decideAfterExplicitUserConfirmation(
                        "account-c", "training", "review-1",
                        Engine2Decision.APPROVE, now.plusSeconds(1), false)
                }
                assertEquals(60, store.read("account-c", "training")!!.plan.sessions.first().minutes)
                val accepted = coordinator.decideAfterExplicitUserConfirmation(
                    "account-c", "training", "review-1",
                    Engine2Decision.APPROVE, now.plusSeconds(1), true)
                assertTrue(accepted is Engine2ApprovalCoordinator.Result.Saved)
                assertEquals(35, store.read("account-c", "training")!!.plan.sessions.first().minutes)
            }
            Engine2LedgerSqliteStore(context, name).use { reopened ->
                assertEquals(35,
                    reopened.read("account-c", "training")!!.plan.sessions.first().minutes)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun legacyDatabaseNameIsNeverAcceptedAsEngine2LedgerDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertThrows(IllegalArgumentException::class.java) {
            Engine2LedgerSqliteStore(context, TrainingStore.DATABASE)
        }
    }
}
