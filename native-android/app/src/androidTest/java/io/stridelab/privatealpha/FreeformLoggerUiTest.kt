package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class FreeformLoggerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = "freeform-ui-${UUID.randomUUID()}"
    private val database = "$fixture.db"
    private lateinit var repo: TrainingRepository
    private lateinit var owner: String

    @Before fun setup() {
        runBlocking {
            repo = TrainingRepository(context, databaseName = database, preferencesName = fixture)
            owner = requireNotNull(withTimeout(20_000) { repo.state.first { !it.initializing } }.localPlanOwner)
        }
        compose.setContent {
            StrideTheme {
                val state by repo.state.collectAsState()
                val access = androidx.compose.runtime.remember(repo) { repo.freeformAccess() }
                NativeStrideContent(state, false, NativeActions({}, { _, _ -> }, freeform = access))
            }
        }
    }

    @After fun cleanup() {
        repo.close()
        context.deleteDatabase(database)
        context.deleteDatabase("$database-manual")
        context.deleteSharedPreferences(fixture)
    }

    @Test fun universalLoggerSavesMixedExercisesAndAppearsInGuestTrainingHistory() = reportUiFailure {
        compose.onNodeWithTag("nav-training").performClick()
        openNew()
        addCustom("Fixture Strength")
        field("freeform-actual-0-quantity").performScrollTo().performTextInput("8")
        node("freeform-confirm-0").performScrollTo().performClick()

        node("freeform-editor").performScrollToNode(hasTestTag("freeform-add-exercise"))
        node("freeform-add-exercise").performClick()
        awaitNode("freeform-exercise-picker-sheet")
        compose.onNodeWithTag("freeform-picker-filter-running").assertDoesNotExist()
        awaitNode("freeform-picker-filter-isometrics").performScrollTo().performClick()
        awaitNode("freeform-picker-list").performScrollToNode(hasText("Wall sit"))
        compose.onNodeWithText("Wall sit").performClick()
        node("freeform-editor").performScrollToNode(hasText("Wall sit"))
        compose.onAllNodesWithTag("freeform-actual-0-seconds", useUnmergedTree = true).onLast().performScrollTo().performTextInput("30")
        compose.onAllNodesWithTag("freeform-confirm-0", useUnmergedTree = true).onLast().performScrollTo().performClick()

        closeKeyboard()
        node("freeform-finish").performClick()
        node("freeform-review-totals").assertIsDisplayed()
        node("freeform-save-completed").performClick()
        awaitNode("freeform-history-summary")
        val saved = runBlocking { repo.freeformAccess().read(owner).workouts.single() }
        assertTrue(saved.isComplete)
        assertEquals(2, saved.exercises.size)
        assertTrue(saved.exercises.all { it.sets.single().completed })
        awaitBack().performClick()
        val historyTag = "training-freeform-${saved.id}"
        node("training-unified-timeline").performScrollToNode(hasTestTag(historyTag))
        node(historyTag).assertIsDisplayed()
        assertTrue(repo.state.value.plans.isEmpty())
        assertTrue(repo.state.value.activities.isEmpty())
    }

    @Test fun catalogSearchSetEditingDuplicateAndResume() = reportUiFailure {
        node("nav-training").performClick()
        openNew()
        node("freeform-editor").performScrollToNode(hasTestTag("freeform-add-exercise"))
        node("freeform-add-exercise").performClick()
        awaitNode("freeform-exercise-picker-sheet")
        field("freeform-exercise-query").performTextInput("squat")
        closeKeyboard()
        val catalogResult = hasText("squat", substring = true, ignoreCase = true) and hasClickAction() and
            !hasTestTag("freeform-exercise-query") and !hasTestTag("freeform-add-custom")
        compose.waitUntil(10_000) { compose.onAllNodes(catalogResult).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(catalogResult).onFirst().performClick()
        node("freeform-editor").performScrollToIndex(0)
        field("freeform-actual-0-quantity").performScrollTo().performTextInput("6")
        field("freeform-actual-0-kg").performScrollTo().performTextInput("40")
        closeKeyboard()
        node("freeform-confirm-0").performScrollTo().performClick()
        field("freeform-actual-0-quantity").performScrollTo().performTextReplacement("7")
        closeKeyboard()
        node("freeform-add-set").performScrollTo().performClick()
        node("freeform-save-progress").performClick()
        awaitNode("freeform-resume").performScrollTo().performClick()
        awaitNode("freeform-editor")
        field("freeform-actual-1-quantity").performScrollTo().assertIsDisplayed()
        val draft = runBlocking { repo.freeformAccess().read(owner).workouts.single() }
        assertFalse(draft.isComplete)
        assertNotNull(draft.exercises.single().catalogId)
        val sets = draft.exercises.single().sets
        assertEquals(listOf("7", "7"), sets.map { it.actual.quantity })
        assertEquals(listOf("40", "40"), sets.map { it.actual.kg })
        assertEquals(listOf(true, false), sets.map { it.completed })
    }

    @Test fun universalRoutineCreateStartFinishAndSaveFromHistory() = reportUiFailure {
        node("nav-training").performClick()
        node("nav-quick-log").performClick()
        awaitNode("quick-log-hub").performScrollTo().performClick()
        awaitNode("freeform-open-templates").performClick()
        awaitNode("workout-template-new").performClick()
        awaitNode("workout-template-editor")
        field("workout-template-title").performScrollTo().performTextReplacement("Upper circuit")
        node("workout-template-exercise").performScrollTo().performClick()
        awaitNode("freeform-exercise-picker-sheet")
        field("freeform-exercise-query").performTextInput("Push-up")
        closeKeyboard()
        awaitNode("freeform-add-custom").performClick()
        node("workout-template-save").performClick()
        awaitNode("workout-template-start").performScrollTo().performClick()
        awaitNode("freeform-editor")
        val original = runBlocking { repo.freeformAccess().read(owner) }
        val draft = original.workouts.single()
        assertEquals(original.templates.single().id, draft.templateId)

        field("freeform-actual-0-quantity").performScrollTo().performTextInput("15")
        closeKeyboard()
        node("freeform-confirm-0").performScrollTo().performClick()
        node("freeform-finish").performClick()
        awaitNode("freeform-save-completed").performClick()
        awaitNode("freeform-save-as-template").performScrollTo().performClick()
        awaitNode("workout-template-editor")
        field("workout-template-title").performScrollTo().performTextReplacement("Repeat circuit")
        closeKeyboard()
        node("workout-template-save").performClick()
        awaitNode("workout-template-list")
        val after = runBlocking { repo.freeformAccess().read(owner) }
        assertEquals(listOf("Repeat circuit", "Upper circuit"), after.templates.map { it.name })
        assertTrue(after.workouts.single().isComplete)
    }

    @Test fun openingEmptyWorkoutAndLeavingWithoutEditsCreatesZeroGhostDrafts() = reportUiFailure {
        node("nav-training").performClick()
        node("nav-quick-log").performClick()
        awaitNode("quick-log-hub").performScrollTo().performClick()
        awaitNode("freeform-hub")
        awaitNode("freeform-open-templates").assertIsDisplayed()
        awaitNode("freeform-log").performClick()
        awaitNode("freeform-empty-state").assertIsDisplayed()
        awaitNode("freeform-add-exercise").assertIsDisplayed()
        compose.onNodeWithText("Back squat", substring = true, ignoreCase = true).assertDoesNotExist()
        awaitBack().performClick()
        awaitNode("freeform-hub")
        awaitNode("freeform-open-templates").assertIsDisplayed()
        compose.onNodeWithText("In progress").assertDoesNotExist()
        assertTrue(runBlocking { repo.freeformAccess().read(owner).workouts }.isEmpty())
    }

    @Test fun oneTapDiscardRemovesInProgressDraftFromWorkoutsHub() = reportUiFailure {
        node("nav-training").performClick()
        openNew()
        addCustom("Goblet squat")
        field("freeform-actual-0-quantity").performScrollTo().performTextInput("10")
        closeKeyboard()
        node("freeform-save-progress").performClick()
        awaitNode("freeform-hub")
        val draft = runBlocking { repo.freeformAccess().read(owner).workouts.single() }
        awaitNode("freeform-resume").assertIsDisplayed()
        awaitNode("freeform-discard-row-${draft.id}").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("In progress").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(runBlocking { repo.freeformAccess().read(owner).workouts }.isEmpty())
    }

    @Test fun quickAddRecentExerciseAndNonBlockingRestTimerWorkDuringSetLogging() = reportUiFailure {
        node("nav-training").performClick()
        openNew()
        addCustom("Overhead press")
        field("freeform-actual-0-quantity").performScrollTo().performTextInput("5")
        closeKeyboard()
        node("freeform-confirm-0").performScrollTo().performClick()
        node("freeform-finish").performClick()
        awaitNode("freeform-save-completed").performClick()
        awaitNode("freeform-history-summary")
        awaitBack().performClick()

        openNew()
        awaitNode("freeform-quick-add-Overhead press").performScrollTo().performClick()
        awaitNode("freeform-set-0")
        field("freeform-actual-0-quantity").performScrollTo().performTextInput("5")
        field("freeform-actual-0-kg").performScrollTo().performTextInput("42.5")
        closeKeyboard()
        node("freeform-confirm-0").performScrollTo().performClick()
        awaitNode("freeform-rest-timer").assertIsDisplayed()
        node("freeform-add-set").performScrollTo().performClick()
        field("freeform-actual-1-quantity").performScrollTo().performTextReplacement("6")
        closeKeyboard()
        assertEquals("6", runBlocking {
            node("freeform-save-progress").performClick()
            awaitNode("freeform-hub")
            repo.freeformAccess().read(owner).workouts.first { !it.isComplete }
                .exercises.single().sets[1].actual.quantity
        })
    }

    private fun openNew() {
        node("nav-quick-log").performClick()
        awaitNode("quick-log-hub").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("freeform-log") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        node("freeform-log").performClick()
        awaitNode("freeform-editor")
    }

    private fun addCustom(name: String) {
        node("freeform-editor").performScrollToNode(hasTestTag("freeform-add-exercise"))
        node("freeform-add-exercise").performClick()
        awaitNode("freeform-exercise-picker-sheet")
        field("freeform-exercise-query").performTextInput(name)
        closeKeyboard()
        awaitNode("freeform-add-custom").performClick()
        node("freeform-editor").performScrollToIndex(0)
        compose.onNodeWithText(name).assertExists()
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun field(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun awaitNode(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return node(tag)
    }
    private fun awaitBack() = compose.onNodeWithContentDescription("Back")

    private inline fun reportUiFailure(action: () -> Unit) {
        try {
            action()
        } catch (error: Throwable) {
            android.util.Log.e("FreeformAcceptance", "Freeform UI assertion failed", error)
            throw error
        }
    }

    private fun closeKeyboard() {
        compose.runOnUiThread {
            val view = compose.activity.currentFocus ?: return@runOnUiThread
            val manager = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            manager.hideSoftInputFromWindow(view.windowToken, 0)
        }
        compose.waitForIdle()
    }
}
