package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TracksideEngine15ManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val today = LocalDate.now()
    private val source = PlannedSession("source", today, "run", "Recorded easy run", 40, 6.0, "easy",
        status = "completed", logRevision = 1,
        log = WorkoutLog(Instant.now(), minutes = 35.0, effort = 9, performedOn = today))
    private val target = PlannedSession("target", today.plusDays(1), "run", "Easy run", 60, 8.0, "easy",
        steps = listOf("Original pace target"))
    private val original = PlanSnapshot("ui-review", "test-owner", ENGINE15_VERSION, ENGINE15_POLICY, Instant.EPOCH,
        PlanRequest(policyChoice = "engine15", start = today, weeks = 2, days = (1..7).toSet()), listOf(source, target), emptyList(), status = "approved")

    @Test fun reviewRequiresTwoDecisionsAndRejectsSupersededPlanBeforeAcceptance() {
        val plan = mutableStateOf(original)
        val actions = PlanActions(
            previewAdjustments = { Engine15Feedback.preview(plan.value, today) },
            stageAdjustment = { proposal ->
                plan.value = Engine15Feedback.stage(plan.value, proposal, today).copy(revision = plan.value.revision + 1)
            },
            acceptSuggestion = { _, revision, id ->
                assertEquals(plan.value.revision, revision)
                Engine15Feedback.requireCurrentPendingAcceptance(plan.value, id, today)
                plan.value = PlanOperations.acceptSuggestion(plan.value, id).copy(revision = revision + 1)
            }
        )
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { Engine15RecommendationReview(plan.value, actions, false) }
            } }
        }
        compose.onNodeWithText("Preview recommendations").performClick()
        compose.runOnIdle { assertEquals(original, plan.value) }
        compose.onNodeWithText("Review suggestion").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(plan.value.sessions.last().suggestion!!.accepted)
            plan.value = plan.value.copy(revision = plan.value.revision + 1)
        }
        compose.onNodeWithText("Accept 45 min").assertIsNotEnabled()
        compose.onNodeWithText("Refresh decision").performScrollTo().performClick()
        compose.onNodeWithText("Accept 45 min").performScrollTo().performClick()
        compose.runOnIdle {
            val stored = plan.value.sessions.last()
            assertEquals(8.0, stored.distanceKm!!, 0.0)
            val shown = plan.value.cardPresentation(stored)
            assertEquals(45, shown.minutes)
            assertNull(shown.distanceKm)
            assertFalse("Original pace target" in shown.steps)
        }
    }

    @Test fun reviewedRestRemovesAllWorkoutStepsAndPreservesOriginalHistory() {
        val missed = source.copy(status = "missed", log = null, missedReason = "Illness")
        val initial = original.copy(sessions = listOf(missed, target))
        val proposal = Engine15Feedback.preview(initial, today).single()
        val accepted = PlanOperations.acceptSuggestion(Engine15Feedback.stage(initial, proposal, today), target.id)
        val saved = accepted.sessions.last()
        val card = accepted.cardPresentation(saved)
        assertEquals("skipped", saved.status)
        assertEquals(60, saved.minutes)
        assertEquals(0, card.minutes)
        assertNull(card.distanceKm)
        assertTrue(card.steps.isEmpty())
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme { TracksideParityWorkoutCard(card, false) {} } }
        }
        compose.onNodeWithText("Reviewed rest").assertExists()
        compose.onNodeWithText("Original pace target").assertDoesNotExist()
        compose.onNodeWithText("Planned run").assertDoesNotExist()
    }

    @Test fun schedulePreviewBlocksLockedDaysThenShowsRelocationBeforeApply() {
        val request = original.request.copy(runDays = 0)
        val schedule = original.copy(request = request, sessions = listOf(target))
        val locked = PlanOperations.setSessionLock(schedule, target.id, true)
        val saved = mutableStateOf(locked)
        val actions = PlanActions(
            lockWorkout = { _, _, id, lock -> saved.value = PlanOperations.setSessionLock(saved.value, id, lock) },
            previewAvailability = { _, days -> Engine15Availability.preview(saved.value, days) },
            applyAvailability = { _, _, days, reviewed ->
                assertEquals(reviewed, Engine15Availability.preview(saved.value, days))
                saved.value = PlanOperations.updateAvailability(saved.value, days)
            }
        )
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { Engine15ScheduleReview(saved.value, actions, false) }
            } }
        }
        val weekday = dayName(target.date.dayOfWeek.value)
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText(weekday).performScrollTo().performClick()
        compose.onNodeWithText("Preview availability change").performScrollTo().performClick()
        compose.onNodeWithText("A locked workout falls", substring = true).assertExists()
        compose.runOnIdle { assertEquals(locked, saved.value) }
        compose.onNodeWithText("Unlock Easy run", substring = true).performScrollTo().performClick()
        compose.onNodeWithText(weekday).performScrollTo().performClick()
        compose.onNodeWithText("Preview availability change").performScrollTo().performClick()
        compose.onNodeWithText("Safe moves · 1   Unresolved · 0").assertExists()
        compose.runOnIdle { assertEquals(target.date, saved.value.sessions.single().date) }
        compose.onNodeWithText("Apply reviewed availability").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotEquals(target.date, saved.value.sessions.single().date)
            assertEquals("paused", saved.value.status)
        }
    }

    @Test fun dismissPreviewKeepsSourceAndTargetPrescriptions() {
        val plan = mutableStateOf(original)
        val actions = PlanActions(
            previewAdjustments = { Engine15Feedback.preview(plan.value, today) },
            dismissPreview = { plan.value = Engine15Feedback.dismiss(plan.value, it, today, Instant.now()) }
        )
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { Engine15RecommendationReview(plan.value, actions, false) }
            } }
        }
        compose.onNodeWithText("Preview recommendations").performClick()
        compose.onNodeWithText("Keep original").performScrollTo().performClick()
        compose.onNodeWithText("Dismissed · Easy run · original target retained").assertExists()
        compose.runOnIdle {
            assertEquals(original.sessions, plan.value.sessions)
            assertTrue(Engine15Feedback.preview(plan.value, today).isEmpty())
        }
    }
    @Test fun unresolvedWorkoutStaysOffCalendarAndBlocksResume() {
        val unassigned = target.copy(status = "unassigned")
        val paused = original.copy(status = "paused", sessions = listOf(unassigned))
        assertTrue(tracksideTrainingWeek(paused, target.date).all { it.sessions.isEmpty() })
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme {
                TracksideManagePlanScreen(paused, today, false, PlanActions(), {}, {}, {})
            } }
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("1 workouts require placement"))
        compose.onNodeWithText("1 workouts require placement").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-resume"))
        compose.onNodeWithTag("manage-resume").assertIsNotEnabled()
    }
}
