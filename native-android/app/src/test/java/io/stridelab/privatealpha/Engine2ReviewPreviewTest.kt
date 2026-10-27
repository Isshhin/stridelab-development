package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2ReviewPreviewTest {
    private val date = LocalDate.parse("2026-11-02")
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val p = Engine2PlanView("athlete", "plan", 3L, Engine2PlanStatus.APPROVED,
        listOf(
            Engine2SessionTarget("run", date, Engine2Discipline.RUNNING, 60),
            Engine2SessionTarget("strength", date.plusDays(1), Engine2Discipline.STRENGTH, 40),
            Engine2SessionTarget("skipped", date.plusDays(2), Engine2Discipline.RUNNING, 45,
                Engine2SessionStatus.SKIPPED)
        ))
    private val proposal = Engine2DurationProposal(
        "p-1", "athlete", "plan", 3L, "run", date, 60, 35,
        "Offer a shorter run after reviewing the provided training evidence.",
        setOf("synthetic-run"), now
    )

    @Test fun previewShowsTargetAndWeeklyImpactWithoutSavingAnything() {
        val original = p.copy()
        val data = Engine2ReviewPreview.build(p, proposal)
        assertTrue(data.requiresExplicitApproval)
        assertTrue(data.eligibleForApproval)
        assertEquals(60, data.originalMinutes)
        assertEquals(35, data.proposedMinutes)
        assertEquals(100L, data.originalWeekMinutes)
        assertEquals(75L, data.proposedWeekMinutes)
        assertEquals(proposal.reason, data.reason)
        assertEquals(setOf("synthetic-run"), data.evidenceRecordIds)
        assertEquals(original, p)
    }

    @Test fun stalePlanDoesNotPretendToOfferAnApplicableChange() {
        val stale = Engine2ReviewPreview.build(p.copy(revision = 4L), proposal)
        assertFalse(stale.eligibleForApproval)
        assertEquals(stale.originalWeekMinutes, stale.proposedWeekMinutes)
        val completed = p.copy(sessions = p.sessions.map {
            if (it.id == "run") it.copy(status = Engine2SessionStatus.COMPLETED) else it
        })
        val completedPreview = Engine2ReviewPreview.build(completed, proposal)
        assertFalse(completedPreview.eligibleForApproval)
        assertEquals(completedPreview.originalWeekMinutes, completedPreview.proposedWeekMinutes)
    }

    @Test fun accountMismatchAndMissingSessionFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ReviewPreview.build(p.copy(ownerId = "other-athlete"), proposal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2ReviewPreview.build(p.copy(sessions = emptyList()), proposal)
        }
    }
}
