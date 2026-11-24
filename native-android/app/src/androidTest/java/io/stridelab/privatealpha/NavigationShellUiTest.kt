package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationShellUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private fun testNativeActions(
        initialBoard: FreeformBoard = FreeformBoard(),
        onStartCalled: () -> Unit = {}
    ): NativeActions = NativeActions(
        signOut = {},
        signIn = { _, _ -> },
        freeform = FreeformAccess(
            read = { initialBoard },
            start = {
                onStartCalled()
                it
            },
            save = { it },
            finish = { it.finish(it.modifiedAt) },
            discard = { _, _ -> true },
            export = { "" },
            restore = { _, _ -> 0 },
            saveTemplate = { _, _ -> },
            deleteTemplate = { _, _ -> true },
            saveSchedule = { },
            changeSchedule = { _, _, _ -> throw UnsupportedOperationException() },
            startCustom = { _, _, _ -> throw UnsupportedOperationException() }
        )
    )

    @Test
    fun dockedFiveSlotBarRendersBalancedTargetsWithoutOverflow() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(initializing = false, account = null, localPlanOwner = "local"),
                        online = false,
                        actions = testNativeActions()
                    )
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("floating-navigation-overlay").fetchSemanticsNodes().isNotEmpty()
        }
        val barBounds = compose.onNodeWithTag("floating-navigation-overlay").getUnclippedBoundsInRoot()
        val tags = listOf("nav-home", "nav-training", "nav-quick-log", "nav-activities", "nav-settings")
        val slotBounds = tags.map { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag height ${(b.bottom - b.top).value} < 48dp", (b.bottom - b.top).value >= 47.5f)
            assertTrue("$tag width ${(b.right - b.left).value} < 48dp", (b.right - b.left).value >= 47.5f)
            assertTrue("$tag overflows left", b.left >= barBounds.left - 1.dp)
            assertTrue("$tag overflows right", b.right <= barBounds.right + 1.dp)
            b
        }
        for (i in 0 until slotBounds.lastIndex) {
            assertTrue(
                "Slot ${tags[i]} overlaps ${tags[i + 1]}",
                slotBounds[i].right <= slotBounds[i + 1].left + 1.dp
            )
        }
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }

    @Test
    fun quickLogSheetLaunchesEmptyWorkoutHidesBottomBarAndLeavesNoGhostDraft() {
        var startCount = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(initializing = false, account = null, localPlanOwner = "local"),
                        online = false,
                        actions = testNativeActions(onStartCalled = { startCount++ })
                    )
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("nav-quick-log").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-quick-log").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("quick-log-sheet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quick-log-discipline-STRENGTH").assertIsDisplayed()
        compose.onNodeWithTag("quick-log-templates").assertIsDisplayed()

        compose.onNodeWithTag("quick-log-discipline-STRENGTH").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("freeform-editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("floating-navigation-overlay").assertDoesNotExist()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("floating-navigation-overlay").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, startCount)
    }

    @Test
    fun activityTabUnifiesCompletedEvidenceFiltersDisciplinesAndHidesBarOnDetail() {
        val doneRun = FreeformWorkout(
            id = "run-done",
            owner = "local",
            discipline = ManualDiscipline.RUNNING,
            performedOn = "2026-11-23",
            startedAt = 1_000L,
            modifiedAt = 2_000L,
            completedAt = 2_000L,
            distanceKm = "6.5",
            durationMinutes = "36",
            workoutName = "Morning Tempo"
        )
        val doneStrength = FreeformWorkout(
            id = "str-done",
            owner = "local",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 3_000L,
            modifiedAt = 4_000L,
            completedAt = 4_000L,
            workoutName = "Lower Body Lift",
            exercises = listOf(
                FreeformExercise(
                    id = "ex-gs",
                    name = "Goblet squat",
                    catalogId = "gs",
                    sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "24"), completed = true))
                )
            )
        )
        val draftStrength = FreeformWorkout(
            id = "str-draft",
            owner = "local",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 5_000L,
            modifiedAt = 5_000L,
            completedAt = null,
            workoutName = "Draft Push Session"
        )
        val board = FreeformBoard(workouts = listOf(doneRun, doneStrength, draftStrength))

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(initializing = false, account = null, localPlanOwner = "local"),
                        online = false,
                        actions = testNativeActions(initialBoard = board)
                    )
                }
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("nav-activities").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-activities").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("activity-history-tab").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-activities").assertIsSelected()

        // Completed runs & strength appear; incomplete draft does not
        compose.onNodeWithText("Morning Tempo").assertIsDisplayed()
        compose.onNodeWithText("Lower Body Lift").assertIsDisplayed()
        compose.onAllNodesWithText("Draft Push Session").assertCountEquals(0)

        // Filter to Strength
        compose.onNodeWithTag("activity-filter-strength").performClick()
        compose.onNodeWithText("1 completed").assertIsDisplayed()
        compose.onNodeWithText("Lower Body Lift").assertIsDisplayed()
        compose.onAllNodesWithText("Morning Tempo").assertCountEquals(0)

        // Open detail and verify bottom bar hides
        compose.onNodeWithTag("activity-evidence-manual:str-done").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("freeform-history-summary").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("floating-navigation-overlay").assertDoesNotExist()

        // Navigate back to Activity tab and verify Quick-Log surfaces the in-progress draft
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("nav-quick-log").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-quick-log").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("quick-log-resume-str-draft").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quick-log-resume-str-draft").assertIsDisplayed()
    }
}
