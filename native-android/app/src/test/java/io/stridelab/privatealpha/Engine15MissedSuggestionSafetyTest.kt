package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * Batch E compatibility gate: the shared missed-session operation must keep its
 * historical Engine 1.0 behavior while never silently staging Engine 1.5 feedback.
 */
class Engine15MissedSuggestionSafetyTest {
    private val today = LocalDate.now()
    private val now = Instant.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    private fun plan(
        version: String = ENGINE15_VERSION,
        policy: String = ENGINE15_POLICY,
        source: PlannedSession = PlannedSession(
            "source", yesterday, "run", "Earlier run", 40, 6.0, "easy"
        ),
        future: PlannedSession = PlannedSession(
            "future", tomorrow, "run", "Upcoming run", 60, 8.0, "easy"
        )
    ) = PlanSnapshot(
        "synthetic-plan", "synthetic-owner", version, policy, now,
        PlanRequest(start = yesterday.minusDays(7), weeks = 4,
            runDays = 3, days = (1..7).toSet(), weeklyKm = 30.0),
        listOf(source, future), emptyList(), status = "approved", revision = 4
    )

    @Test fun engine15ExplicitMissedOrSkippedReasonsStayUnstagedUntilReview() {
        for (skipped in listOf(false, true)) {
            for (reason in listOf("Illness", "Fatigue", "Soreness")) {
                val initial = plan()
                val marked = PlanOperations.missed(initial, "source", reason, skipped)
                assertEquals(if (skipped) "skipped" else "missed", marked.sessions.first().status)
                assertEquals(reason, marked.sessions.first().missedReason)
                assertNull("Engine 1.5 must never auto-stage after $reason", marked.sessions.last().suggestion)
                assertEquals(initial.sessions.last(), marked.sessions.last())

                val proposals = Engine15Feedback.preview(marked, today)
                assertEquals(1, proposals.size)
                assertEquals("future", proposals.single().targetSessionId)
                assertEquals(if (reason == "Illness") 0 else 42, proposals.single().suggestedMinutes)
                assertNull(marked.sessions.last().suggestion)
                assertEquals(60, marked.sessions.last().minutes)
                assertEquals(8.0, marked.sessions.last().distanceKm!!, 0.0)

                val staged = Engine15Feedback.stage(marked, proposals.single(), today)
                assertFalse(staged.sessions.last().suggestion!!.accepted)
                assertEquals(60, staged.sessions.last().effectiveMinutes)
                assertEquals(initial.sessions.first().date, staged.sessions.first().date)
                assertEquals(marked.sessions.first(), staged.sessions.first())
            }
        }
    }

    @Test fun engine1StillOffersItsExistingImmediateSuggestionOnMissedWorkout() {
        val original = plan(version = ENGINE1_VERSION, policy = ENGINE1_POLICY)
        val fatigue = PlanOperations.missed(original, "source", "Fatigue", false)
        assertEquals(42, fatigue.sessions.last().suggestion!!.minutes)
        assertFalse(fatigue.sessions.last().suggestion!!.accepted)
        assertEquals(60, fatigue.sessions.last().effectiveMinutes)
        assertTrue(fatigue.sessions.last().suggestion!!.reason.contains("After your reported Fatigue"))
        val illness = PlanOperations.missed(original, "source", "Illness", true)
        assertEquals(0, illness.sessions.last().suggestion!!.minutes)
        assertEquals("skipped", illness.sessions.first().status)
        assertTrue(Engine15Feedback.preview(illness, today).isEmpty())
    }

    @Test fun noNewEngine15SuggestionMayOverwriteAPendingReviewedProposal() {
        val completed = PlannedSession("source", today.minusDays(2), "run", "Done", 40, 6.0,
            "easy", status = "completed", logRevision = 1,
            log = WorkoutLog(now, minutes = 35.0, effort = 9, performedOn = today.minusDays(2)))
        val secondary = PlannedSession("second", today, "str", "Missable strength", 30, null, "strength")
        val original = plan(source = completed)
            .copy(sessions = listOf(completed, secondary, plan().sessions.last()))
        val preview = Engine15Feedback.preview(original, today).single()
        val staged = Engine15Feedback.stage(original, preview, today)
        val pending = staged.sessions.last().suggestion
        assertNotNull(pending)

        val marked = PlanOperations.missed(staged, "second", "Fatigue", false)
        assertEquals("missed", marked.sessions[1].status)
        assertEquals(pending, marked.sessions.last().suggestion)
        assertEquals(60, marked.sessions.last().effectiveMinutes)
        assertEquals(completed, marked.sessions.first())
    }

    @Test fun foundationPolicyIsEditableButDoesNotActivateBoundedFeedback() {
        val foundation = plan(policy = ENGINE15_FOUNDATION_POLICY)
        val marked = PlanOperations.missed(foundation, "source", "Fatigue", false)
        assertNull(marked.sessions.last().suggestion)
        assertTrue(Engine15Feedback.preview(marked, today).isEmpty())
        assertEquals("missed", marked.sessions.first().status)
    }

    @Test fun unsupportedReasonNeverManufacturesFeedbackAndHistoricalPlansStayIntact() {
        for (reason in listOf("Time", "Other")) {
            val original = plan()
            val marked = PlanOperations.missed(original, "source", reason, false)
            assertTrue(Engine15Feedback.preview(marked, today).isEmpty())
            assertNull(marked.sessions.last().suggestion)
            assertEquals(original.sessions.last(), marked.sessions.last())
        }
    }
}
