package io.stridelab.privatealpha

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Isolated emulator: does not sign in, read a real account or submit provider requests. */
class NativeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    @Test fun nativeEmptyStateAndNavigationSurviveRecreation() {
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        compose.onNodeWithText("Sign in to view your history").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Settings")
    }
    @Test fun passwordIsNotRestoredAfterRecreation() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("email").performTextInput("synthetic@example.invalid")
        compose.onNodeWithTag("password").performTextInput("never-submit-this-fixture")
        compose.onNodeWithTag("submit-signin").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("email").assertTextContains("synthetic@example.invalid")
        compose.onNodeWithTag("submit-signin").assertIsNotEnabled()
    }
}
