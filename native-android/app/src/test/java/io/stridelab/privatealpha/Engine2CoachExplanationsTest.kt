package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2CoachExplanationsTest {
    private val at=Instant.parse("2026-10-28T10:00:00Z")
    private val date=LocalDate.parse("2026-11-02")
    private fun scenario():Triple<Engine2PlanView,Engine2AthleteContext,Engine2DurationProposal> {
        val plan=Engine2PlanView("owner","p",1,Engine2PlanStatus.APPROVED,
            listOf(Engine2SessionTarget("s",date,Engine2Discipline.STRENGTH,60)))
        val provider=Engine2Source("owner","manual","confirmed-1",at.minusSeconds(3600))
        val observation=Engine2WorkoutEvidence("actual-1",Engine2Discipline.RUNNING,
            provider.observedAt,Engine2RecordStatus.CONFIRMED,listOf(
                Engine2Measurement(Engine2Metric.DISTANCE_KM,6.0,provider)))
        val context=Engine2AthleteContext("owner",at,listOf(observation),emptyList())
        val proposal=Engine2ProposalWorkflow.prepare(plan,context,"p1","s",45,
            "A shorter session is available for review based on prior confirmed training.",
            setOf("actual-1"),at)
        return Triple(plan,context,proposal)
    }

    @Test fun explanationPreservesBeforeAfterAndEvidenceIdentity() {
        val (plan,context,proposal)=scenario()
        val result=Engine2CoachExplanations.forDurationProposal(plan,context,proposal)
        assertTrue(result.readyForUserReview)
        assertTrue(result.beforeAfter.contains("60 → 45"))
        assertTrue(result.evidence.single().contains("actual-1"))
        assertTrue(result.evidence.single().contains("manual"))
        assertTrue(result.cautions.any { it.contains("not proof of fatigue") })
        assertEquals(60,plan.sessions.single().minutes)
    }

    @Test fun staleRevisionDisablesReviewRatherThanFabricatingApproval() {
        val (plan,context,proposal)=scenario()
        val stale=plan.copy(revision=2)
        val result=Engine2CoachExplanations.forDurationProposal(stale,context,proposal)
        assertFalse(result.readyForUserReview)
        assertTrue(result.cautions.any { it.contains("Refresh before reviewing") })
    }

    @Test fun anotherAccountCannotReconstructEvidence() {
        val (plan,context,proposal)=scenario()
        assertThrows(IllegalArgumentException::class.java) {
            Engine2CoachExplanations.forDurationProposal(plan.copy(ownerId="other"),context,proposal)
        }
    }
}
