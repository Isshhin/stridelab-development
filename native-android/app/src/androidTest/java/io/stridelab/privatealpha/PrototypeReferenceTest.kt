package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the current native roots; the retired WebView reference is not a product screen. */
class PrototypeReferenceTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun todayTrainingAndMoreRemainTheOnlyPrimaryDestinations() {
        val state = androidx.compose.runtime.mutableStateOf(
            TrainingState(initializing = false, account = "synthetic-native-roots")
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state.value, false,
                        NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("today-week-swipe").assertExists()
        compose.onNodeWithTag("nav-training").performClick().assertIsSelected()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        // Empty plan tools use native neutral/lime controls, with no dead-end
        // return row when the timeline has no recorded content.
        compose.onNodeWithTag("training-plan-tools").performClick()
        compose.onNodeWithTag("training-empty-plan-card").assertExists()
        compose.onNodeWithTag("training-create-plan").assertExists()
        compose.onNodeWithTag("training-restore-plan-backup").assertExists()
        compose.onNodeWithTag("training-back-to-timeline").assertDoesNotExist()
        compose.onNodeWithTag("training-view-day").assertExists()
        compose.onNodeWithTag("training-view-agenda").assertExists()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        compose.onNodeWithTag("screen-title").assertExists()
        compose.onNodeWithTag("nav-home").performClick().assertIsSelected()
        compose.onNodeWithTag("today-week-swipe").assertExists()
        compose.onNodeWithTag("nav-library").assertDoesNotExist()
        assertTrue(state.value.plans.isEmpty())
    }
}
