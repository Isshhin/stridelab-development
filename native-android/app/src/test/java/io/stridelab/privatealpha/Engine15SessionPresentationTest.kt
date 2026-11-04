package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** No Android UI in this test: presentation values are policy-gated pure data. */
class Engine15SessionPresentationTest {
    private val today = LocalDate.parse("2026-11-03")
    private val original = PlannedSession(
        "next-run", today.plusDays(1), "run", "Easy run", 60, 8.0, "easy",
        steps = listOf("Run exactly 8.0 km", "Keep original prescribed pace")
    )
    private val now = Instant.parse("2026-11-03T00:00:00Z")

    private fun plan(
        session: PlannedSession = original,
        version: String = ENGINE15_VERSION,
        policy: String = ENGINE15_POLICY
    ) = PlanSnapshot(
        "p", "owner", version, policy, now,
        PlanRequest(start = today, weeks = 4, runDays = 3),
        listOf(session), emptyList(), status = "approved"
    )

    private fun recommendation(minutes: Int) = SessionSuggestion(
        minutes, "${Engine15Feedback.RULE_ID}: recent athlete-reported exertion. Reviewed change."
    )

    @Test fun pendingProposalLeavesOriginalSessionMetricsAndStepsActive() {
        val saved = plan(original.copy(suggestion = recommendation(45)))
        val active = Engine15SessionPresentation.forSession(saved, "next-run")
        assertEquals(Engine15SessionPresentation.State.PENDING_REVIEW, active.state)
        assertEquals(60, active.currentMinutes)
        assertEquals(8.0, active.currentDistanceTargetKm!!, 0.0)
        assertEquals(original.steps, active.currentSteps)
        assertNotNull(active.disclosure)
        assertEquals(saved.sessions.single(), original.copy(suggestion = recommendation(45)))
    }

    @Test fun explicitlyAcceptedShorterRunIsTimeOnlyAndCannotImplyOldDistanceOrPace() {
        val staged = plan(original.copy(suggestion = recommendation(45)))
        val accepted = PlanOperations.acceptSuggestion(staged, "next-run")
        val display = Engine15SessionPresentation.forSession(accepted, "next-run")
        assertEquals(Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY, display.state)
        assertEquals(45, display.currentMinutes)
        assertNull("8 km is historical context, not a 45-minute target", display.currentDistanceTargetKm)
        assertEquals(60, display.originalMinutes)
        assertEquals(8.0, display.originalDistanceKm!!, 0.0)
        assertFalse(display.currentSteps.any { it.contains("8.0 km") || it.contains("prescribed pace") })
        assertTrue(display.currentSteps.any { it.contains("45-minute") })
        assertNotNull(display.disclosure)
        assertEquals(60, accepted.sessions.single().minutes)
        assertEquals(8.0, accepted.sessions.single().distanceKm!!, 0.0)
        assertEquals(original.steps, accepted.sessions.single().steps)
        assertEquals(accepted, PlanSnapshot.decode(accepted.encode(), "owner"))
    }

    @Test fun acceptedRestShowsNoFutureWorkoutTargetOrSteps() {
        val staged = plan(original.copy(suggestion = recommendation(0)))
        val accepted = PlanOperations.acceptSuggestion(staged, "next-run")
        val display = Engine15SessionPresentation.forSession(accepted, "next-run")
        assertEquals(Engine15SessionPresentation.State.ACCEPTED_REST, display.state)
        assertEquals(0, display.currentMinutes)
        assertNull(display.currentDistanceTargetKm)
        assertTrue(display.currentSteps.isEmpty())
        assertEquals(60, display.originalMinutes)
        assertEquals(8.0, display.originalDistanceKm!!, 0.0)
        assertEquals("skipped", accepted.sessions.single().status)
        assertEquals(60, accepted.sessions.single().minutes)
    }

    @Test fun engineOneAndFoundationSnapshotsPreserveTheirExistingDisplayContract() {
        val accepted = original.copy(suggestion = recommendation(45).copy(accepted = true))
        for ((version, policy) in listOf(
            ENGINE1_VERSION to ENGINE1_POLICY,
            ENGINE15_VERSION to ENGINE15_FOUNDATION_POLICY
        )) {
            val display = Engine15SessionPresentation.forSession(plan(accepted, version, policy), "next-run")
            assertEquals(Engine15SessionPresentation.State.ORIGINAL, display.state)
            assertEquals(45, display.currentMinutes)
            assertEquals(8.0, display.currentDistanceTargetKm!!, 0.0)
            assertEquals(original.steps, display.currentSteps)
        }
    }

    @Test fun incompatibleSuggestionAndUnsupportedPolicyCannotInventNewTargets() {
        val unrelated = original.copy(
            suggestion = SessionSuggestion(45, "Legacy suggestion", accepted = true)
        )
        assertEquals(Engine15SessionPresentation.State.ORIGINAL,
            Engine15SessionPresentation.forSession(plan(unrelated), "next-run").state)
        val unknown = plan(original.copy(suggestion = recommendation(45).copy(accepted = true)),
            policy = "stridelab.engine15.unknown")
        assertEquals(Engine15SessionPresentation.State.ORIGINAL,
            Engine15SessionPresentation.forSession(unknown, "next-run").state)
    }

    @Test fun invalidAcceptedStatesFailClosedAndUnknownSessionIsRejected() {
        val inconsistent = plan(original.copy(suggestion = recommendation(0).copy(accepted = true)))
        val displayed = Engine15SessionPresentation.forSession(inconsistent, "next-run")
        assertEquals(Engine15SessionPresentation.State.ORIGINAL, displayed.state)
        assertEquals(60, displayed.currentMinutes)
        assertEquals(original.steps, displayed.currentSteps)
        assertNotNull(displayed.disclosure)
        assertThrows(PlanConstraint::class.java) {
            Engine15SessionPresentation.forSession(inconsistent, "other-session")
        }
    }

    @Test fun finishedWorkoutsRemainHistoricalNotActiveTimeOnlyTargets() {
        val saved = plan(original.copy(
            status = "completed", suggestion = recommendation(45).copy(accepted = true),
            log = WorkoutLog(now, minutes = 38.0, effort = 7, performedOn = today)
        ))
        val displayed = Engine15SessionPresentation.forSession(saved, "next-run")
        assertEquals(Engine15SessionPresentation.State.ORIGINAL, displayed.state)
        assertEquals(60, displayed.currentMinutes)
        assertEquals(8.0, displayed.currentDistanceTargetKm!!, 0.0)
        assertEquals(38.0, saved.sessions.single().log!!.minutes!!, 0.0)
    }
}
