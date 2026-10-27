package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Synthetic end-to-end domain acceptance, not actual Android/SQLite acceptance. */
class Engine2Batch4AcceptanceTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    @Test fun syntheticProviderToReadOnlyPlanToApprovalIsConsistentAndExplicit() {
        val input = Engine2ProviderPage("synthetic-provider",
            listOf(Engine2ProviderActivity(
                "run-125", Engine2Discipline.RUNNING, now.minusSeconds(7200),
                distanceMeters = 6500.0, durationSeconds = 2800.0
            )), null, now)
        val incoming = Engine2ProviderNormalizer.normalize("fictional", input, now)
        val assembled = Engine2ContextAssembler.build("fictional", now, incoming, emptyList())
        assertTrue(assembled.rejected.isEmpty())
        val athlete = assembled.context
        assertEquals(6.5, athlete.runningCoverage().knownDistanceKm!!, 0.0)
        assertEquals(3, Engine2WellnessAudit.inspect(athlete).missingMetrics.size)

        val request = Engine2PlanningRequest(LocalDate.parse("2026-11-02"), 2,
            (1..6).toSet(), mapOf(
                Engine2Discipline.RUNNING to 3,
                Engine2Discipline.STRENGTH to 2,
                Engine2Discipline.CALISTHENICS to 1
            ), preferredLongRunDay = 6)
        val outline = Engine2UnifiedPlanGenerator.generate("fictional", "plan-1", request)
        assertEquals(Engine2PlanStatus.DRAFT, outline.plan.status)
        assertEquals(12, outline.plan.sessions.size)
        // This explicit synthetic test approval is not an application approval transaction.
        val acceptedByTestOwner = outline.plan.copy(status = Engine2PlanStatus.APPROVED)
        val target = acceptedByTestOwner.sessions.first()
        val proposal = Engine2ConservativeAdaptation.proposeLighter(
            acceptedByTestOwner, athlete,
            Engine2AthleteRequest("fictional", target.id,
                "I request a shorter session for my next training day.", now),
            "suggest-1"
        )
        val preview = Engine2ReviewPreview.build(acceptedByTestOwner, proposal)
        assertTrue(preview.eligibleForApproval)
        assertTrue(preview.requiresExplicitApproval)
        assertEquals(target.minutes, preview.originalMinutes)
        val staged = Engine2ApprovalLedgerReducer.stage(
            Engine2ApprovalLedger(acceptedByTestOwner), proposal)
        val store = Engine2VolatileLedgerStore()
        store.seed(staged)
        val candidate = Engine2ApprovalTransactions.prepareDecision(
            staged, "suggest-1", Engine2Decision.APPROVE, now.plusSeconds(1))
        assertTrue(store.commitIfRevision("fictional", "plan-1",
            candidate.originalLedgerRevision, candidate.originalPlanRevision, candidate.updated))
        val saved = store.read("fictional", "plan-1")!!
        assertEquals(target.minutes, outline.plan.sessions.first().minutes)
        assertEquals(target.minutes, acceptedByTestOwner.sessions.first().minutes)
        assertEquals(proposal.suggestedMinutes, saved.plan.sessions.first().minutes)
        assertTrue("suggest-1" in saved.plan.appliedProposalIds)
        assertEquals(1, saved.decisions.size)
        assertFalse(store.commitIfRevision("fictional", "plan-1",
            candidate.originalLedgerRevision, candidate.originalPlanRevision, candidate.updated))
    }

    @Test fun forgedLedgerReceiptsAndDuplicatePendingChangesFailClosed() {
        val p = Engine2PlanView("fictional", "test-plan", 2, Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("run", LocalDate.parse("2026-11-02"),
                Engine2Discipline.RUNNING, 45)))
        val proposal = Engine2DurationProposal("p-1", "fictional", "test-plan", 2,
            "run", LocalDate.parse("2026-11-02"), 45, 30,
            "Fictional owner requested reduced scheduled duration.", setOf("actual-1"), now)
        val duplicate = proposal.copy(id = "p-2")
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ApprovalLedger(p, mapOf(proposal.id to proposal, duplicate.id to duplicate))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ApprovalLedger(p, emptyMap(), listOf(
                Engine2DecisionReceipt("unlisted", Engine2Decision.APPROVE, now, 3)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ApprovalLedger(p, mapOf(proposal.id to proposal), listOf(
                Engine2DecisionReceipt("p-1", Engine2Decision.APPROVE, now, 3)))
        }
    }

    @Test fun missingIndependentVerificationAlwaysBlocksDelivery() {
        val unverified = Engine2AcceptanceGate.assess(Engine2VerificationEvidence())
        assertFalse(unverified.eligibleForDomainCodeReview)
        assertFalse(unverified.eligibleForPersistenceIntegration)
        assertFalse(unverified.eligibleForLiveProviderIntegration)
        assertFalse(unverified.eligibleForPhoneUpdate)
        assertTrue(unverified.blockers.isNotEmpty())
        val unitOnly = Engine2AcceptanceGate.assess(Engine2VerificationEvidence(
            exactHeadCompiled = true, engineUnitTestsPassed = true,
            datasetBehaviorTestsPassed = true
        ))
        assertTrue(unitOnly.eligibleForDomainCodeReview)
        assertFalse(unitOnly.eligibleForPersistenceIntegration)
        assertFalse(unitOnly.eligibleForPhoneUpdate)
    }

    @Test fun evenSuccessfulAndroidTestsCannotBypassSigningOrOwnerRequest() {
        val verifiedPersistence = Engine2VerificationEvidence(
            exactHeadCompiled = true, engineUnitTestsPassed = true,
            datasetBehaviorTestsPassed = true, androidInstrumentationPassed = true,
            schemaUpgradePassed = true, offlineReopenPassed = true,
            accountIsolationPassed = true
        )
        val report = Engine2AcceptanceGate.assess(verifiedPersistence)
        assertTrue(report.eligibleForPersistenceIntegration)
        assertFalse(report.eligibleForPhoneUpdate)
        assertFalse(report.eligibleForLiveProviderIntegration)
        assertTrue(Engine2AcceptanceGate.assess(verifiedPersistence.copy(
            installedSigningCompatible = true, ownerRequestedInstallableApk = true
        )).eligibleForPhoneUpdate)
        assertTrue(Engine2AcceptanceGate.assess(verifiedPersistence.copy(
            liveProviderAuthorizationVerified = true,
            providerDedupVerified = true
        )).eligibleForLiveProviderIntegration)
    }
}
