package io.stridelab.privatealpha

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated emulator: does not sign in, read a real account or submit provider requests. */
class NativeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    @Test fun nativeEmptyStateAndNavigationSurviveRecreation() {
        capture("home")
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        compose.onNodeWithText("Sign in to view your history").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        capture("activities")
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Settings")
        capture("settings")
    }
    @Test fun passwordIsNotRestoredAfterRecreation() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        capture("signin")
        compose.onNodeWithTag("email").performTextInput("synthetic@example.invalid")
        compose.onNodeWithTag("password").performTextInput("never-submit-this-fixture")
        compose.onNodeWithTag("submit-signin").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("email").assertTextContains("synthetic@example.invalid")
        compose.onNodeWithTag("submit-signin").assertIsNotEnabled()
    }
    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "native-ui-evidence")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
