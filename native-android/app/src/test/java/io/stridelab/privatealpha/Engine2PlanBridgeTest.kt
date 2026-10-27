package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine2PlanBridgeTest {
    private val start = LocalDate.parse("2026-11-02")
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private fun engine1Plan() = Engine1.generate(
        PlanRequest(start = start, weeks = 2, runDays = 3),
        "synthetic-athlete", "source-plan", now
    )

    @Test fun previewOfExistingPlanPreservesDatesCountsAndNoReceipts() {
        val original = engine1Plan().copy(status = "approved")
        val before = original.copy()
        val view = Engine2PlanBridge.preview(original)
        assertEquals("synthetic-athlete", view.ownerId)
        assertEquals("source-plan", view.planId)
        assertEquals(original.revision.toLong(), view.revision)
        assertEquals(Engine2PlanStatus.APPROVED, view.status)
        assertEquals(original.sessions.size, view.sessions.size)
        assertTrue(view.appliedProposalIds.isEmpty())
        original.sessions.zip(view.sessions).forEach { (source, converted) ->
            assertEquals(source.id, converted.id)
            assertEquals(source.date, converted.date)
            assertEquals(source.effectiveMinutes, converted.minutes)
            assertEquals(Engine2Discipline.RUNNING, converted.discipline)
            assertEquals(Engine2SessionStatus.PLANNED, converted.status)
        }
        assertEquals(before, original)
    }

    @Test fun acceptedLighterSuggestionIsRepresentedAsEffectiveTargetNotActualWorkout() {
        val original = engine1Plan().copy(status = "approved")
        val updated = original.copy(sessions = original.sessions.mapIndexed { index, s ->
            if (index == 0) s.copy(
                suggestion = SessionSuggestion(20, "Previously accepted shorter option", true)
            ) else s
        })
        val view = Engine2PlanBridge.preview(updated)
        assertEquals(20, view.sessions.first().minutes)
        assertEquals(Engine2SessionStatus.PLANNED, view.sessions.first().status)
        assertNull(updated.sessions.first().log)
    }

    @Test fun completedSkippedPausedAndUnknownStatesDoNotSilentlyBecomeEditable() {
        val p = engine1Plan()
        val completed = p.copy(status = "paused", sessions = p.sessions.mapIndexed { i, s ->
            when (i) {
                0 -> s.copy(status = "completed")
                1 -> s.copy(status = "skipped")
                else -> s
            }
        })
        val view = Engine2PlanBridge.preview(completed)
        assertEquals(Engine2PlanStatus.PAUSED, view.status)
        assertEquals(Engine2SessionStatus.COMPLETED, view.sessions[0].status)
        assertEquals(Engine2SessionStatus.SKIPPED, view.sessions[1].status)
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanBridge.preview(p.copy(status = "unknown-state"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanBridge.preview(p.copy(sessions =
                p.sessions.mapIndexed { i, s -> if (i == 0) s.copy(status = "unknown") else s }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Engine2PlanBridge.preview(p.copy(engineVersion = "future-2.0"))
        }
    }
}
