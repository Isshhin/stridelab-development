package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AnatomicalMuscleMapUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val engine = WorkoutPlayerEngine { android.os.SystemClock.elapsedRealtime() }
    private val logTime = Instant.parse("2026-11-24T12:00:00Z")

    private fun testFreeformAccess(): FreeformAccess = FreeformAccess(
        read = { FreeformBoard() },
        start = { it },
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

    @Test
    fun coachedSetTableOpensExerciseInfoSheetAndPreservesLiveSetInputAndBounds() {
        val squat = ExercisePrescription("gs", "Goblet squat", 2, reps = 8, rest = 60, equipment = "Dumbbell")
        val plank = ExercisePrescription("plank", "Forearm plank", 2, seconds = 20, rest = 45, equipment = "Mat / floor")
        val session = PlannedSession(
            id = "str-s1",
            date = LocalDate.of(2026, 11, 24),
            discipline = "str",
            title = "Strength Foundation",
            minutes = 35,
            distanceKm = null,
            kind = "strength",
            exercises = listOf(squat, plank)
        )
        val plan = PlanSnapshot(
            id = "str-plan",
            owner = "owner",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = logTime,
            request = PlanRequest(goal = "General strength", runDays = 0, strengthDays = 1, days = setOf(2)),
            sessions = listOf(session),
            explanations = emptyList(),
            status = "approved"
        )
        var execution = WorkoutExecution.create("exec-str", plan.id, session, "fin-str")

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    WorkoutScreen(
                        state = TrainingState(initializing = false, account = "owner", plans = listOf(plan)),
                        planId = plan.id,
                        sessionId = session.id,
                        actions = PlanActions(
                            executeWorkout = { _, _, _, event ->
                                execution = event?.let { engine.reduce(execution, it) } ?: execution
                                execution
                            }
                        ),
                        onBack = {}
                    )
                }
            }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coached-set-table").fetchSemanticsNodes().isNotEmpty() }
        val root = compose.onNodeWithTag("coached-set-table").getUnclippedBoundsInRoot()

        // Edit set input before opening info sheet
        compose.onNodeWithTag("coached-reps-0").performTextReplacement("11")

        // Verify >= 48dp trigger target and zero horizontal overflow
        val triggerBounds = compose.onNodeWithTag("exercise-info-trigger-gs").getUnclippedBoundsInRoot()
        assertTrue((triggerBounds.bottom - triggerBounds.top).value >= 47.5f)
        assertTrue((triggerBounds.right - triggerBounds.left).value >= 47.5f)
        assertTrue(triggerBounds.right <= root.right + 1.dp)

        // Open ExerciseInfoSheet
        compose.onNodeWithTag("exercise-info-trigger-gs").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("exercise-info-sheet").fetchSemanticsNodes().isNotEmpty() }

        val sheetBounds = compose.onNodeWithTag("exercise-info-sheet").getUnclippedBoundsInRoot()
        val canvasBounds = compose.onNodeWithTag("muscle-map-canvas").getUnclippedBoundsInRoot()
        assertTrue("Muscle canvas overflows sheet right edge", canvasBounds.right <= sheetBounds.right + 1.dp)
        compose.onNodeWithTag("muscle-map-front").assertIsDisplayed()
        compose.onNodeWithTag("muscle-map-back").assertIsDisplayed()
        compose.onNodeWithTag("muscle-map-primary-legend").assertIsDisplayed()

        // Dismiss sheet and verify live edited set value is preserved
        compose.onNodeWithTag("exercise-info-close").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("exercise-info-sheet").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread {
            val imm = compose.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithTag("coached-reps-0").assertTextEquals("11")

        // Toggle whole-session Muscle Focus section cleanly
        compose.onNodeWithTag("coached-set-list").performScrollToNode(hasTestTag("coached-muscle-focus-toggle"))
        compose.onNodeWithTag("coached-muscle-focus-toggle").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("session-muscle-focus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("coached-set-list").performScrollToNode(hasTestTag("session-muscle-focus"))
        compose.onNodeWithText("Planned session focus").performScrollTo().assertIsDisplayed()
        val sessionFocusBounds = compose.onNodeWithTag("session-muscle-focus").getUnclippedBoundsInRoot()
        assertTrue(sessionFocusBounds.right <= root.right + 1.dp)
    }

    @Test
    fun freeformEditorOpensExerciseInfoSheetWithoutLosingLiveSets() {
        val workout = FreeformWorkout(
            id = "fw-edit-1",
            owner = "owner",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 100L,
            modifiedAt = 100L,
            exercises = listOf(
                FreeformExercise(
                    id = "ex-bp",
                    name = "Dumbbell bench press",
                    catalogId = "bp",
                    sets = listOf(
                        FreeformSet(
                            id = "set-1",
                            actual = FreeformMeasure(quantity = "8", kg = "30"),
                            completed = false
                        )
                    )
                )
            )
        )

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    FreeformEditor(
                        initial = workout,
                        board = FreeformBoard(),
                        access = testFreeformAccess(),
                        persisted = false,
                        update = {},
                        remove = {},
                        leave = {},
                        saved = {}
                    )
                }
            }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("freeform-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("freeform-actual-0-quantity").performTextReplacement("12")
        val root = compose.onNodeWithTag("freeform-editor").getUnclippedBoundsInRoot()
        val triggerBounds = compose.onNodeWithTag("exercise-info-trigger-bp").getUnclippedBoundsInRoot()
        assertTrue((triggerBounds.bottom - triggerBounds.top).value >= 47.5f)
        assertTrue(triggerBounds.right <= root.right + 1.dp)

        compose.onNodeWithTag("exercise-info-trigger-bp").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("exercise-info-sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("muscle-map-canvas").assertIsDisplayed()
        compose.onNodeWithTag("muscle-map-front").assertIsDisplayed()
        compose.onNodeWithTag("muscle-map-back").assertIsDisplayed()
        compose.onNodeWithTag("muscle-map-primary-legend").assertIsDisplayed()

        compose.onNodeWithTag("exercise-info-close").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("exercise-info-sheet").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("freeform-actual-0-quantity").assertTextEquals("12")
    }

    @Test
    fun historyDetailShowsConfirmedMuscleFocusOrOmitsCleanlyWhenUnmapped() {
        val mappedWorkout = FreeformWorkout(
            id = "fw-mapped",
            owner = "owner",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 100L,
            modifiedAt = 200L,
            completedAt = 200L,
            exercises = listOf(
                FreeformExercise(
                    id = "ex-1",
                    name = "Dumbbell bench press",
                    catalogId = "bp",
                    sets = listOf(
                        FreeformSet(actual = FreeformMeasure(quantity = "10", kg = "40"), completed = true),
                        FreeformSet(actual = FreeformMeasure(quantity = "10", kg = "40"), completed = true),
                        FreeformSet(actual = FreeformMeasure(quantity = "10", kg = "40"), completed = false)
                    )
                )
            )
        )
        val unmappedWorkout = FreeformWorkout(
            id = "fw-unmapped",
            owner = "owner",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = "2026-11-24",
            startedAt = 100L,
            modifiedAt = 200L,
            completedAt = 200L,
            exercises = listOf(
                FreeformExercise(
                    id = "ex-custom",
                    name = "Custom Garage Odd Object",
                    catalogId = null,
                    sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "5"), completed = true))
                )
            )
        )
        val activeWorkout = mutableStateOf(mappedWorkout)

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    FreeformHistoryDetail(
                        workout = activeWorkout.value,
                        legacy = null,
                        loading = false,
                        back = {}
                    )
                }
            }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("session-muscle-focus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Based on 2 confirmed sets").assertIsDisplayed()
        val focusBounds = compose.onNodeWithTag("session-muscle-focus").getUnclippedBoundsInRoot()
        assertTrue((focusBounds.right - focusBounds.left).value > 0f)

        compose.runOnUiThread { activeWorkout.value = unmappedWorkout }
        compose.waitForIdle()
        compose.onNodeWithTag("session-muscle-focus").assertDoesNotExist()
    }
}
