package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Explicit acceptance must recheck evidence, not only the saved plan revision. */
class Engine15AcceptanceEvidenceTest {
    private val today = LocalDate.parse("2026-11-03")
    private val at = Instant.parse("2026-11-02T12:00:00Z")
    private val source = PlannedSession(
        "source", today.minusDays(1), "run", "Completed", 40, 6.0, "easy",
        status = "completed", logRevision = 1,
        log = WorkoutLog(at, minutes = 35.0, effort = 9, performedOn = today.minusDays(1))
    )
    private val future = PlannedSession(
        "future", today.plusDays(1), "run", "Upcoming", 60, 8.0, "easy"
    )
    private fun plan() = PlanSnapshot(
        "e15-accept", "synthetic-owner", ENGINE15_VERSION, ENGINE15_POLICY, at,
        PlanRequest(start = today.minusDays(8), weeks = 4, runDays = 3),
        listOf(source, future), emptyList(), status = "approved", revision = 7
    )
    private fun staged() = plan().let {
        Engine15Feedback.stage(it, Engine15Feedback.preview(it, today).single(), today)
            .copy(revision = it.revision + 1)
    }

    @Test fun unchangedRecentEvidenceAllowsExplicitApprovalWithoutChangingHistory() {
        val p = staged()
        Engine15Feedback.requireCurrentPendingAcceptance(p, "future", today)
        val accepted = PlanOperations.acceptSuggestion(p, "future")
        assertEquals(45, accepted.sessions.last().effectiveMinutes)
        assertEquals(60, accepted.sessions.last().minutes)
        assertEquals(8.0, accepted.sessions.last().distanceKm!!, 0.0)
        assertEquals(source, accepted.sessions.first())
        assertEquals(accepted, PlanSnapshot.decode(accepted.encode(), "synthetic-owner"))
    }

    @Test fun changedSourceEffortOrLogRevisionInvalidatesStagedSuggestion() {
        for (effort in listOf(3, 8)) {
            val p = staged().copy(sessions = staged().sessions.map {
                if (it.id == "source") it.copy(
                    log = it.log!!.copy(effort = effort), logRevision = it.logRevision + 1
                ) else it
            })
            assertThrows(PlanConstraint::class.java) {
                Engine15Feedback.requireCurrentPendingAcceptance(p, "future", today)
            }
        }
    }

    @Test fun expiredTargetPausedPlanAndEditedTargetAreNotEligible() {
        val p = staged()
        val expired = p.copy(sessions = p.sessions.map {
            if (it.id == "future") it.copy(date = today) else it
        })
        for (invalid in listOf(expired, p.copy(status = "paused"),
            p.copy(sessions = p.sessions.map {
                if (it.id == "future") it.copy(minutes = 40) else it
            }))) {
            assertThrows(PlanConstraint::class.java) {
                Engine15Feedback.requireCurrentPendingAcceptance(invalid, "future", today)
            }
        }
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.requireCurrentPendingAcceptance(p, "future", today.plusDays(2))
        }
    }

    @Test fun duplicateImportedSourceLinkAndChangedMissedReasonInvalidateReview() {
        val linked = staged().copy(sessions = staged().sessions.map {
            if (it.id == "source") it.copy(linkedActivityId = "same-provider-run") else it
        })
        val other = plan().copy(
            id = "different",
            sessions = listOf(source.copy(id = "other-source", linkedActivityId = "same-provider-run"))
        )
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.requireCurrentPendingAcceptance(linked, "future", today, listOf(linked, other))
        }
        val missed = plan().copy(sessions = listOf(
            source.copy(status = "missed", missedReason = "Fatigue", log = null, logRevision = 0), future
        ))
        val pending = Engine15Feedback.stage(
            missed, Engine15Feedback.preview(missed, today).single(), today
        )
        val changed = pending.copy(sessions = pending.sessions.map {
            if (it.id == "source") it.copy(missedReason = "Time") else it
        })
        assertThrows(PlanConstraint::class.java) {
            Engine15Feedback.requireCurrentPendingAcceptance(changed, "future", today)
        }
    }

    @Test fun legacyPolicyAndAlreadyAcceptedSuggestionsDoNotCreateNewRestrictions() {
        val legacy = plan().copy(engineVersion = ENGINE1_VERSION, policyId = ENGINE1_POLICY,
            sessions = listOf(source, future.copy(
                suggestion = SessionSuggestion(45, "Existing Engine 1.0 suggestion")
            )))
        Engine15Feedback.requireCurrentPendingAcceptance(legacy, "future", today.plusWeeks(1))
        val accepted = PlanOperations.acceptSuggestion(staged(), "future")
        Engine15Feedback.requireCurrentPendingAcceptance(accepted, "future", today.plusWeeks(1))
    }
}
