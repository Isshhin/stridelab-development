package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CustomPlannerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = "custom-ui-${UUID.randomUUID()}"
    private val database = "$fixture.db"
    private var repo: TrainingRepository? = null
    private val today = LocalDate.now()

    @After fun cleanup() {
        repo?.close()
        context.deleteDatabase(database)
        context.deleteDatabase("$database-manual")
        context.deleteSharedPreferences(fixture)
    }

    @Test fun scheduleMoveResumeFinishAndRepeatWithNoCoachedPlan() {
        val repository = TrainingRepository(context, databaseName = database, preferencesName = fixture)
        repo = repository
        val access = repository.freeformAccess()
        val owner = runBlocking {
            val owner = requireNotNull(withTimeout(20_000) { repository.state.first { !it.initializing } }.localPlanOwner)
            access.saveTemplate(ManualWorkoutTemplate.empty(owner, ManualDiscipline.RUNNING).copy(
                name = "Scheduled tempo", recipe = WorkoutRecipe(distanceKm = "5", durationMinutes = "30")
            ), null)
            owner
        }
        compose.setContent {
            val state by repository.state.collectAsState()
            StrideTheme {
                NativeStrideContent(state, false, NativeActions({}, { _, _ -> }, freeform = remember { access }))
            }
        }
        node("nav-training").performClick()
        node("nav-quick-log").performClick()
        await("quick-log-hub").performScrollTo().performClick()
        await("freeform-open-templates").performClick()
        await("workout-template-schedule").performScrollTo().performClick()
        await("custom-repeat-weekly").performScrollTo().performClick()
        node("custom-save-schedule").performSemanticsAction(SemanticsActions.OnClick)
        await("training-unified-timeline")
        val key = runBlocking { CustomOccurrenceKey(access.read(owner).schedules.single().id, today) }
        openCalendarWorkout(key)
        node("custom-reschedule").performScrollTo().performClick()
        val movedDate = today.plusDays(1).toString()
        await("custom-schedule-date").performScrollTo().performTextReplacement(movedDate)
        node("custom-schedule-date").assert(hasText(movedDate))
        hideKeyboard()
        node("custom-save-schedule").performSemanticsAction(SemanticsActions.OnClick)
        openCalendarWorkout(key)
        node("custom-start-workout").performScrollTo().performClick()
        await("freeform-run-targets").assertExists()
        node("freeform-run-distance").performScrollTo().performTextInput("4.5")
        node("freeform-run-duration").performScrollTo().performTextInput("28")
        hideKeyboard()
        node("freeform-save-progress").performClick()
        await("custom-start-workout").performScrollTo().performClick()
        await("freeform-finish").performClick()
        await("freeform-save-completed").performClick()
        await("freeform-history-summary").assertExists()
        val saved = runBlocking { access.read(owner).workouts.single() }
        assertEquals(key.workoutId, saved.id)
        assertEquals(today.plusDays(1), saved.custom?.date)
        assertEquals(today, saved.date)
        assertEquals("4.5", saved.distanceKm)
        assertTrue(saved.isComplete)
        node("custom-repeat-workout").performScrollTo().performClick()
        await("custom-save-schedule").performSemanticsAction(SemanticsActions.OnClick)
        await("training-unified-timeline").assertExists()
        val board = runBlocking { access.read(owner) }
        assertEquals(2, board.schedules.size)
        assertEquals(listOf(saved), board.workouts)
        assertEquals(1, board.customBetween(today.plusDays(1), today.plusDays(1)).size)
        assertTrue(repository.state.value.plans.isEmpty())
    }

    @Test fun hybridCalendarKeepsCustomAndCoachedSourcesAndPlanFilters() {
        val owner = "custom-hybrid-fixture"
        val template = ManualWorkoutTemplate.empty(owner, ManualDiscipline.STRENGTH).copy(name = "Custom lift")
        val scheduled = CustomSchedule.create(template, ScheduleRule(today), 1)
        val hidden = CustomSchedule.create(template.copy(owner = "another-profile"), ScheduleRule(today), 1)
        fun plan(id: String, discipline: String, title: String) = PlanSnapshot(
            id, owner, ENGINE1_VERSION, ENGINE1_POLICY, Instant.EPOCH, PlanRequest(start = today, weeks = 2),
            listOf(PlannedSession(id, today, discipline, title, 25, null, "easy")),
            emptyList(), status = "approved", name = title
        )
        val plans = listOf(plan("running-plan", "run", "Coached run"), plan("strength-plan", "str", "Coached strength"))
        compose.setContent {
            StrideTheme {
                StrideLabUnifiedTrainingTimeline(
                    TrainingState(initializing = false, account = owner, plans = plans),
                    emptyList(), PlanActions(), {}, { _, _ -> }, {}, { _, _ -> },
                    manualBoard = FreeformBoard(schedules = listOf(scheduled, hidden))
                )
            }
        }
        val customTag = "training-custom-${CustomOccurrenceKey(scheduled.id, today).workoutId}"
        val hiddenTag = "training-custom-${CustomOccurrenceKey(hidden.id, today).workoutId}"
        val timeline = node("training-unified-timeline")
        timeline.performScrollToNode(hasTestTag(customTag))
        node(customTag).assertExists()
        node(hiddenTag).assertDoesNotExist()
        node("timeline-planned-running-plan").assertExists()
        timeline.performScrollToNode(hasTestTag("training-switch-plan"))
        node("training-switch-plan").performClick()
        timeline.performScrollToNode(hasTestTag("training-filter-running-plan"))
        node("training-filter-running-plan").performClick()
        timeline.performScrollToNode(hasTestTag("timeline-planned-running-plan"))
        node(customTag).assertDoesNotExist()
        timeline.performScrollToNode(hasTestTag("training-switch-plan"))
        node("training-switch-plan").performClick()
        timeline.performScrollToNode(hasTestTag("training-filter-all"))
        node("training-filter-all").performClick()
        timeline.performScrollToNode(hasTestTag(customTag))
        node(customTag).assertExists()
    }

    private fun openCalendarWorkout(key: CustomOccurrenceKey) {
        val tag = "training-custom-${key.workoutId}"
        await("training-unified-timeline").performScrollToNode(hasTestTag(tag))
        node(tag).performClick()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun await(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return node(tag)
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            val view = compose.activity.currentFocus ?: return@runOnUiThread
            context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
        compose.waitForIdle()
    }
}
