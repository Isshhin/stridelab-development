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

    @Test
    fun trainingOmitsAddWorkoutTodayAllShowsMultiColorConsistencyAndRunningOmitsDiagnostics() {
        val today = java.time.LocalDate.now()
        val sameDayRun = FreeformWorkout(
            id = "run-today",
            owner = "local",
            discipline = ManualDiscipline.RUNNING,
            performedOn = today.toString(),
            startedAt = 1_000L,
            modifiedAt = 2_000L,
            completedAt = 2_000L,
            distanceKm = "5.0",
            durationMinutes = "28",
            workoutName = "Easy Run"
        )
        val sameDayStrength = FreeformWorkout(
            id = "str-today",
            owner = "local",
            discipline = ManualDiscipline.STRENGTH,
            performedOn = today.toString(),
            startedAt = 3_000L,
            modifiedAt = 4_000L,
            completedAt = 4_000L,
            workoutName = "Strength Session",
            exercises = listOf(
                FreeformExercise(
                    id = "ex-sq",
                    name = "Goblet squat",
                    catalogId = "gs",
                    sets = listOf(FreeformSet(actual = FreeformMeasure(quantity = "8", kg = "20"), completed = true))
                )
            )
        )
        val runActivity = ActivityRecord(
            id = "act-run-1",
            source = "garmin",
            type = "Run",
            title = "Morning Run",
            startedAt = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().plusSeconds(60),
            distanceM = 5000.0,
            durationS = 1680.0,
            movingTimeS = 1680.0,
            recordedPace = null,
            averageHr = null,
            elevationM = null
        )
        val runningPlan = PlanSnapshot(
            id = "run-plan-1",
            owner = "local",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = java.time.Instant.now().minusSeconds(86_400),
            request = PlanRequest(goal = "10K", runDays = 3, days = setOf(1, 3, 5)),
            sessions = listOf(
                PlannedSession(
                    id = "run-s1",
                    date = today,
                    discipline = "run",
                    title = "Easy Run",
                    minutes = 35,
                    distanceKm = 5.0,
                    kind = "easy"
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )
        val board = FreeformBoard(workouts = listOf(sameDayRun, sameDayStrength))

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(
                            initializing = false,
                            account = null,
                            localPlanOwner = "local",
                            localGarminOwner = "local",
                            localGarminIds = setOf(runActivity.id),
                            plans = listOf(runningPlan),
                            activities = listOf(runActivity),
                            todayPlanReady = true,
                            activitySummariesReady = true
                        ),
                        online = false,
                        actions = testNativeActions(initialBoard = board)
                    )
                }
            }
        }

        // 1. Today -> All renders multi-discipline Consistency card with split color segments
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("screen").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("today-page-running").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("consistency-heatmap-strip"))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("consistency-seg-$today-strength").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("consistency-day-$today").assertIsDisplayed()
        compose.onNodeWithTag("consistency-seg-$today-running").assertExists()
        compose.onNodeWithTag("consistency-seg-$today-strength").assertExists()
        compose.onNodeWithTag("consistency-legend").assertIsDisplayed()

        // 2. Today -> Running omits both Coaching Diagnostics and Consistency card
        compose.onNodeWithTag("today-page-running").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("coaching-diagnostics").assertCountEquals(0)
        compose.onAllNodesWithTag("consistency-heatmap-strip").assertCountEquals(0)

        // 3. Training tab has no Add Workout button
        compose.onNodeWithTag("nav-training").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("training-unified-timeline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("training-add-workout").assertCountEquals(0)
    }

    @Test
    fun todayTappableHeroAndActivityCardsReturnToOriginAndShowRealSources() {
        val today = java.time.LocalDate.now()
        val activityDate = java.time.LocalDate.of(2026, 11, 23)
        val garminRun = ActivityRecord(
            id = "garmin-run-nov23",
            source = "garmin",
            type = "Run",
            title = "Threshold Garmin Run",
            startedAt = activityDate.atTime(7, 30)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant(),
            distanceM = 7450.0,
            durationS = 2460.0,
            movingTimeS = 2400.0,
            recordedPace = 322.0,
            averageHr = 152.0,
            elevationM = 40.0
        )
        val plan = PlanSnapshot(
            id = "plan-hero",
            owner = "local",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = java.time.Instant.now().minusSeconds(86_400),
            request = PlanRequest(policyChoice = ENGINE15_POLICY, goal = "Half Marathon", runDays = 4, days = setOf(1, 3, 5, 7)),
            sessions = listOf(
                PlannedSession(
                    id = "today-s1",
                    date = today,
                    discipline = "run",
                    title = "Easy Aerobic Run",
                    minutes = 68,
                    distanceKm = 9.9,
                    kind = "easy"
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(
                            initializing = false,
                            account = null,
                            localPlanOwner = "local",
                            localGarminOwner = "local",
                            localGarminIds = setOf(garminRun.id),
                            plans = listOf(plan),
                            activities = listOf(garminRun),
                            todayPlanReady = true,
                            activitySummariesReady = true
                        ),
                        online = false,
                        actions = testNativeActions()
                    )
                }
            }
        }

        // 1. Today hero card is tappable without "View Workout" and Back returns to Today
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("today-workout-action").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("View Workout").assertCountEquals(0)
        compose.onNodeWithTag("today-workout-action").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("workout-planned-title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("floating-navigation-overlay").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("today-workout-action").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-home").assertIsSelected()

        // 2. Activity card shows "Mon | Nov 23", real provider "GARMIN" (no "IMPORTED"), 3 metrics, and Back returns to Activity
        compose.onNodeWithTag("nav-activities").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("activity-evidence-import:garmin-run-nov23").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-activities").assertIsSelected()
        compose.onNodeWithText("Mon | Nov 23").assertExists()
        compose.onNodeWithText("GARMIN").assertExists()
        compose.onAllNodesWithText("IMPORTED").assertCountEquals(0)
        compose.onNodeWithText("DISTANCE").assertExists()
        compose.onNodeWithText("AVG PACE").assertExists()
        compose.onNodeWithText("AVG HR").assertExists()
        compose.onNodeWithText("7.45 km").assertExists()
        compose.onNodeWithText("5:22 /km").assertExists()
        compose.onNodeWithText("152 bpm").assertExists()

        compose.onNodeWithTag("activity-evidence-import:garmin-run-nov23").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("floating-navigation-overlay").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("activity-history-tab").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-activities").assertIsSelected()

        // 3. Training tab shows revamped visual cards and omits Workout recommendations
        compose.onNodeWithTag("nav-training").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("training-unified-timeline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("workout-recommendations-accordion").assertCountEquals(0)
        compose.onAllNodesWithText("Workout recommendations").assertCountEquals(0)
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("coach-week-review"))
        compose.onNodeWithTag("coach-week-review").assertExists()

        // 4. Manage plan renders Training Availability, omits Workout recommendations, and Back returns to Training
        compose.onNodeWithTag("training-unified-timeline").performScrollToIndex(0)
        compose.onNodeWithTag("training-manage-plan").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("manage-back").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-training-availability"))
        compose.onNodeWithTag("manage-training-availability").assertExists()
        compose.onAllNodesWithTag("manage-recommendations").assertCountEquals(0)
        compose.onAllNodesWithText("Workout recommendations").assertCountEquals(0)
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-back"))
        compose.onNodeWithTag("manage-back").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("training-unified-timeline").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-training").assertIsSelected()
    }
}
