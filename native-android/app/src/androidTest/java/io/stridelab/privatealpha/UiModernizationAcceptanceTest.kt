package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test fun modernizedScreensKeepBoundsTouchTargetsCachesAndDisciplinePickerAcrossFlows() {
        val today = LocalDate.now()
        val sampleActivity = ActivityRecord(
            "modern-run-1", "Tempo Run", "running", "garmin",
            Instant.now(), 8500.0, 2600.0, 2580.0, 303.0, 152.0, 40.0
        )
        val sampleDetail = ActivityDetail(
            activityId = sampleActivity.id,
            samples = listOf(
                ActivitySample(0, 0.0, null, 138.0, null, 37.7749, -122.4194),
                ActivitySample(1, 60.0, null, 144.0, null, 37.7759, -122.4184),
                ActivitySample(2, 120.0, null, 148.0, null, 37.7769, -122.4174)
            ),
            laps = emptyList(),
            fetchedAt = 0L
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

        val cache = SharedThumbnailCacheHolder.get(context)
        val uuidAccount = "3474c5ae-56ec-43cd-9317-ff22704ed33e"
        val simulateSignedInUuid = mutableStateOf(true)
        compose.setContent {
            val state by repo.state.collectAsState()
            val showUuidAccount = simulateSignedInUuid.value
            val hydrated = state.copy(
                account = if (showUuidAccount) uuidAccount else state.account,
                accountDisplayName = if (showUuidAccount) "Marc Runner" else state.accountDisplayName,
                accountEmail = if (showUuidAccount) "marc@stridelab.io" else state.accountEmail,
                activities = listOf(sampleActivity),
                details = mapOf(sampleActivity.id to sampleDetail),
                localGarminIds = setOf(sampleActivity.id),
                groupedActivitySummaries = null
            )
            val access = remember(repo) { repo.freeformAccess() }
            StrideTheme {
                ActivityThumbnailProvider(null, uuidAccount, 0L, cache) {
                    NativeStrideContent(hydrated, false, NativeActions({}, { _, _ -> }, freeform = access))
                }
            }
        }

        // 1. Today flattened dashboard + cached GPS thumbnail + centered overview + no recent circular badge
        await("screen").assertIsDisplayed()
        val todayRoot = node("screen").getUnclippedBoundsInRoot()
        val overview = await("today-training-overview").getUnclippedBoundsInRoot()
        assertTrue("Overview clips horizontally", overview.right <= todayRoot.right + 1.dp)
        await("today-recent-activities").assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("gps-thumb-modern-run-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("gps-thumb-modern-run-1", useUnmergedTree = true).onFirst().assertIsDisplayed()
        assertNotNull("GPS thumbnail must be populated in memory LruCache", cache.getMemory(cache.cacheKey(uuidAccount, sampleActivity.id)))

        // 2. More signed-in card shows display name/email, never raw UUID
        node("nav-settings").performClick()
        await("more-profile-card").assertIsDisplayed()
        compose.onNodeWithText("Marc Runner").assertIsDisplayed()
        compose.onNodeWithText("marc@stridelab.io").assertIsDisplayed()
        compose.onNodeWithText("3474c5ae", substring = true).assertDoesNotExist()

        // Switch back to Today and confirm GPS thumbnail is still immediately rendered from LruCache
        node("nav-home").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("gps-thumb-modern-run-1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("gps-thumb-modern-run-1", useUnmergedTree = true).onFirst().assertIsDisplayed()

        // 3. Training calendar-first header with symmetric week arrows & Return to Today below days
        simulateSignedInUuid.value = false
        node("nav-training").performClick()
        await("training-unified-timeline").assertIsDisplayed()
        compose.onAllNodesWithTag("training-workout-history").assertCountEquals(0)
        val prevArrow = await("training-week-prev").getUnclippedBoundsInRoot()
        val nextArrow = await("training-week-next").getUnclippedBoundsInRoot()
        assertTrue("Week arrows should sit near opposite edges", nextArrow.left > prevArrow.right)
        node("training-week-prev").performClick()
        val returnToToday = await("training-return-today").getUnclippedBoundsInRoot()
        assertTrue("Return to Today should sit cleanly below calendar header", returnToToday.top > prevArrow.bottom + 40.dp)
        node("training-return-today").performClick()
        compose.onAllNodesWithTag("training-add-workout").assertCountEquals(0)

        // 4. Workouts Hub via center Quick-Log sheet: In progress row with one-tap discard + templates visible + modal bottom sheet
        node("nav-quick-log").performClick()
        await("quick-log-hub").performScrollTo().performClick()
        await("freeform-hub").assertIsDisplayed()
        await("freeform-open-templates").performScrollTo().assertIsDisplayed()
        val draftId = runBlocking { repo.freeformAccess().read(owner).workouts.single().id }
        node("freeform-hub").performScrollToNode(hasTestTag("freeform-log"))
        assertMinTouchHeight("freeform-log")
        node("freeform-hub").performScrollToNode(hasTestTag("freeform-discard-row-$draftId"))
        assertMinTouchHeight("freeform-discard-row-$draftId")

        // 5. Open Plyometrics empty workout & verify discipline-aware bottom sheet picker
        node("freeform-hub").performScrollToNode(hasTestTag("freeform-log"))
        node("freeform-log").performClick()
        await("freeform-discipline-sheet")
        await("freeform-discipline-PLYOMETRICS").performScrollTo().performClick()
        await("freeform-empty-state").assertIsDisplayed()
        compose.onNodeWithText("Back squat", substring = true).assertDoesNotExist()
        node("freeform-add-exercise").performClick()
        await("freeform-exercise-picker-sheet").assertIsDisplayed()
        await("freeform-picker-list").performScrollToNode(hasText("Pogo hops"))
        compose.onNodeWithText("Pogo hops").assertIsDisplayed()
        compose.onNodeWithText("Assisted pull-up").assertDoesNotExist()
        compose.onNodeWithText("Pogo hops").performClick()

        // 6. Auto-prefill + Add set and clean trailing checkmark without overflow
        await("freeform-set-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("freeform-actual-0-quantity", useUnmergedTree = true).performScrollTo().performTextInput("12")
        node("freeform-add-set").performScrollTo().performClick()
        compose.onNodeWithTag("freeform-actual-1-quantity", useUnmergedTree = true).performScrollTo().assert(hasText("12"))
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
