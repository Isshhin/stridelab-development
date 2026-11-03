package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Source-only staged tests. Do not execute Gradle or assemble an APK until
 * the user explicitly lifts the no-build hold.
 */
class TracksideParityPlanStateTest {
    private fun plan(): PlanSnapshot {
        val today = LocalDate.now()
        return PlanSnapshot(
            id = "synthetic-parity-plan",
            owner = "synthetic-athlete",
            engineVersion = ENGINE1_VERSION,
            policyId = ENGINE1_POLICY,
            createdAt = Instant.parse("2026-11-02T00:00:00Z"),
            request = PlanRequest(
                start = today.minusDays(7), weeks = 4,
                days = (1..7).toSet(), longDay = 7
            ),
            sessions = listOf(
                PlannedSession(
                    id = "target", date = today.minusDays(1), discipline = "run",
                    title = "Threshold run", minutes = 60, distanceKm = 10.0,
                    kind = "quality", quality = true
                ),
                PlannedSession(
                    id = "next", date = today.plusDays(3), discipline = "run",
                    title = "Easy run", minutes = 45, distanceKm = 7.0,
                    kind = "easy"
                )
            ),
            explanations = emptyList(), status = "approved"
        )
    }

    @Test fun skipPreviewNeverMutatesSourceOrAddsCatchUpVolume() {
        val plan = plan()
        val before = plan.encode().toString()
        val result = tracksideReviewExistingEngineChecks(
            plan,
            TracksideChangeProposal(TracksideChangeKind.SKIP, "target", reason = "Time"),
            listOf(plan)
        )
        assertTrue(result.message, result.accepted)
        assertEquals("No increase", result.capacity)
        assertEquals("Reduced or unchanged", result.load)
        assertEquals(before, plan.encode().toString())
        assertEquals("planned", plan.sessions[0].status)
    }

    @Test fun fatigueAdjustPreviewRequiresLaterExplicitAcceptance() {
        val plan = plan()
        val result = tracksideReviewExistingEngineChecks(
            plan,
            TracksideChangeProposal(TracksideChangeKind.ADJUST, "target", reason = "Fatigue"),
            emptyList()
        )
        assertTrue(result.message, result.accepted)
        assertTrue(result.message.contains("not an Engine 2.0"))
        val proposed = PlanOperations.missed(plan, "target", "Fatigue", false)
        assertNotNull(proposed.sessions.first { it.id == "next" }.suggestion)
        assertFalse(proposed.sessions.first { it.id == "next" }.suggestion!!.accepted)
        assertEquals("planned", plan.sessions.first { it.id == "target" }.status)
        assertEquals(null, plan.sessions.first { it.id == "next" }.suggestion)
    }

    @Test fun movingIntoPastIsBlockedBeforeAnyMutation() {
        val plan = plan()
        val result = tracksideReviewExistingEngineChecks(
            plan,
            TracksideChangeProposal(
                TracksideChangeKind.MOVE, "target",
                destination = LocalDate.now().minusDays(2)
            ),
            listOf(plan)
        )
        assertFalse(result.accepted)
        assertTrue(result.message.contains("earlier day"))
        assertEquals("Review", result.recovery)
        assertEquals(plan.request.start.plusDays(6), plan.sessions[0].date)
    }

    @Test fun completionAndActivityLinksPreventReviewAndRewriting() {
        val plan = plan()
        val target = plan.sessions.first()
        val done = plan.copy(sessions = listOf(
            target.copy(status = "completed",
                log = WorkoutLog(Instant.parse("2026-11-02T01:00:00Z"), minutes = 58.0)
            ),
            plan.sessions.last()
        ))
        val review = tracksideReviewExistingEngineChecks(
            done, TracksideChangeProposal(TracksideChangeKind.SKIP, "target"), emptyList()
        )
        assertFalse(review.accepted)
        val linked = plan.copy(sessions = listOf(
            target.copy(linkedActivityId = "synthetic-source-id"),
            plan.sessions.last()
        ))
        assertFalse(tracksideReviewExistingEngineChecks(
            linked, TracksideChangeProposal(TracksideChangeKind.ADJUST, "target"),
            emptyList()
        ).accepted)
    }

    @Test fun foreignEngineSnapshotsStayReadOnlyAndUnknownReasonsFailClosed() {
        val plan = plan()
        assertFalse(tracksideReviewExistingEngineChecks(
            plan.copy(engineVersion = "2.0-unreleased"),
            TracksideChangeProposal(TracksideChangeKind.SKIP, "target"),
            emptyList()
        ).accepted)
        assertFalse(tracksideReviewExistingEngineChecks(
            plan,
            TracksideChangeProposal(TracksideChangeKind.ADJUST, "target", reason = "fake"),
            emptyList()
        ).accepted)
    }
}
