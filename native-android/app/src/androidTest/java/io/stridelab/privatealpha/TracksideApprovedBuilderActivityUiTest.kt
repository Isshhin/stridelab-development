package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Staged UI assertions for Figma 551:580/603/796/865, 720:546,
 * 614:88/412, and 551:235. Synthetic offline data only.
 * DO NOT run Android builds while the user's APK compilation hold is active.
 */
class TracksideApprovedBuilderActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun runningBuilderUsesApprovedStagesNotLegacyChipPage() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(
                        state=state, actions=PlanActions(),
                        onDone={}, onSignIn={},
                        onPreview={_,_->}
                    )
                }
            }
        }
        compose.onNodeWithTag("figma-builder-frame").assertExists()
        compose.onNodeWithText("Choose your", substring=true).assertExists()
        compose.onNodeWithTag("discipline-run").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-race").assertExists()
        compose.onNodeWithText("running goal?", substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-distance-10K").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-experience-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-run-plus").assertExists()
        compose.onNodeWithTag("builder-running-day-2").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithText("Your training week").assertExists()
        compose.onNodeWithText("Workouts will be assigned by the coaching engine.",
            substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertExists()
    }

    @Test fun guidedGoalAndAdvancedPreferencesStayWithinApprovedBuilder() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-faster").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-focus-10K").assertExists()
        compose.onNodeWithTag("builder-guided-advanced").performClick()
        compose.onNodeWithTag("builder-advanced-mileage").assertExists()
        compose.onNodeWithTag("builder-advanced-quality-Threshold").assertExists()
        compose.onNodeWithTag("builder-advanced-quality-count-1").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-guided-mileage").assertExists()
    }

    @Test fun targetTimeIntentCannotSilentlyEnterUnsupportedEngineGoal() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-time").performClick()
        compose.onNodeWithTag("builder-target-time-unavailable").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-option-pr").performClick()
        compose.onNodeWithTag("builder-race-add-pr").performClick()
        compose.onNodeWithText("PR entry").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun availabilityRequiresExplicitMultiSessionPolicyForSevenSessionsInSixDays() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("discipline-str").performClick()
        compose.onNodeWithTag("discipline-cal").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertIsNotEnabled()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-enable-multi-session").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.onNodeWithText("Engine 1 only permits selected pairings",
            substring=true).assertExists()
    }

    @Test fun activityMapRemainsBehindIndependentScrollingGlassSheet() {
        val activity = ActivityRecord(
            "synthetic-run", "Recorded Park Run", "run", "manual",
            Instant.parse("2026-11-01T09:00:00Z"),
            7400.0, 2830.0, 2790.0, 379.0, 150.0, 64.0
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-ui",
            activities = listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state,false,
                        NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("activity-synthetic-run"))
        compose.onNodeWithTag("activity-synthetic-run").performClick()
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        compose.onNodeWithTag("activity-sheet-scroll").assertExists()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithTag("activity-change-glass-variant").performClick()
        compose.onNodeWithTag("activity-matte-overview").assertExists()
    }
}
