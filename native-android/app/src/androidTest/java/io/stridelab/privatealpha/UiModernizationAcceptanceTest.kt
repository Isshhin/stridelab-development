package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UiModernizationAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = "modern-ui-${UUID.randomUUID()}"
    private val database = "$fixture.db"
    private lateinit var repo: TrainingRepository
    private lateinit var owner: String

    @After fun cleanup() {
        if (::repo.isInitialized) repo.close()
        context.deleteDatabase(database)
        context.deleteDatabase("$database-manual")
        context.deleteSharedPreferences(fixture)
    }

    @Test fun modernizedScreensKeepBoundsAndMinimumTouchTargetsAcrossFlows() {
        val today = LocalDate.now()
        val sampleActivity = ActivityRecord(
            "modern-run-1", "Tempo Run", "running", "garmin",
            Instant.now(), 8500.0, 2600.0, 2580.0, 303.0, 152.0, 40.0
        )
        runBlocking {
            repo = TrainingRepository(context, databaseName = database, preferencesName = fixture)
            owner = requireNotNull(withTimeout(20_000) { repo.state.first { !it.initializing } }.localPlanOwner)
            val access = repo.freeformAccess()
            val draft = access.start(FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, today, 100L))
            access.save(
                draft.copy(
                    exercises = listOf(
                        FreeformExercise(
                            name = "Romanian deadlift",
                            sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "80")))
                        )
                    ),
                    modifiedAt = 200L
                )
            )
            access.saveTemplate(
                ManualWorkoutTemplate.empty(owner, ManualDiscipline.STRENGTH).copy(
                    name = "Lower hypertrophy",
                    recipe = WorkoutRecipe(exercises = listOf(FreeformExercise(name = "Front squat")))
                ),
                null
            )
        }

        compose.setContent {
            val state by repo.state.collectAsState()
            val hydrated = state.copy(
                activities = listOf(sampleActivity),
                localGarminIds = setOf(sampleActivity.id),
                groupedActivitySummaries = null
            )
            val access = repo.freeformAccess()
            StrideTheme {
                NativeStrideContent(hydrated, false, NativeActions({}, { _, _ -> }, freeform = access))
            }
        }

        // Today flattened dashboard + bounds check
        await("screen").assertIsDisplayed()
        val todayRoot = node("screen").getUnclippedBoundsInRoot()
        val overview = await("today-training-overview").getUnclippedBoundsInRoot()
        assertTrue("Overview clips horizontally", overview.right <= todayRoot.right + 1.dp)
        await("today-recent-activities").assertIsDisplayed()

        // Training calendar-first header without Workout history link
        node("nav-training").performClick()
        await("training-unified-timeline").assertIsDisplayed()
        compose.onAllNodesWithTag("training-workout-history").assertCountEquals(0)
        assertMinTouchHeight("training-add-workout")

        // Workouts Hub: In progress row with one-tap discard + templates visible + modal bottom sheet
        node("training-add-workout").performClick()
        await("freeform-hub").assertIsDisplayed()
        await("freeform-open-templates").assertIsDisplayed()
        val draftId = runBlocking { repo.freeformAccess().read(owner).workouts.single().id }
        assertMinTouchHeight("freeform-log")
        assertMinTouchHeight("freeform-discard-row-$draftId")

        // Templates flattened list with chips + preview
        node("freeform-open-templates").performClick()
        await("workout-template-list").assertIsDisplayed()
        await("workout-template-filter").assertIsDisplayed()
        compose.onNodeWithText("Front squat", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        // Open FreeformEditor and verify active set grid + confirm + rest timer + action bar bounds
        await("freeform-resume").performScrollTo().performClick()
        await("freeform-editor").assertIsDisplayed()
        assertMinTouchHeight("freeform-confirm-0")
        assertMinTouchHeight("freeform-finish")
        await("freeform-rest-timer").assertIsDisplayed()
        val editorBounds = node("freeform-editor").getUnclippedBoundsInRoot()
        val setBounds = await("freeform-set-0").getUnclippedBoundsInRoot()
        assertTrue("Set row overflows width at viewport", setBounds.right <= editorBounds.right + 1.dp)
    }

    private fun assertMinTouchHeight(tag: String) {
        val bounds = await(tag).getUnclippedBoundsInRoot()
        val heightDp = (bounds.bottom - bounds.top).value
        assertTrue("Touch target '$tag' height ${heightDp}dp < 48dp", heightDp >= 47.5f)
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)
    private fun await(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return node(tag)
    }
}
