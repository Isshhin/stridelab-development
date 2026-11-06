package io.stridelab.privatealpha

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.os.Build
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Isolated emulator: does not sign in, read a real account or submit provider requests. */
class NativeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Before fun ensureUnauthenticatedStart() {
        assumeTrue("NativeNavigationTest requires an isolated Android emulator", isEmulator())
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-settings").performClick()
        // The compact More screen keeps account controls within its lazy list;
        // account actions are lazy-list items below the provider sections.
        compose.onNodeWithTag("screen").performScrollToNode(
            hasText("Sign out on this device") or hasText("Sign in")
        )
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText("Sign out on this device", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Sign in", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Sign out on this device", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Sign out on this device", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Sign out", useUnmergedTree = true).performClick()
            compose.waitUntil(timeoutMillis = 5000) {
                compose.onAllNodesWithText("Sign in", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.onNodeWithTag("nav-home").performClick()
    }
    @Test fun nativeEmptyStateAndNavigationSurviveRecreation() {
        capture("home")
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        compose.onNodeWithText("Sign in to view your history").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        capture("activities")
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        capture("settings")
    }
    @Test fun passwordIsNotRestoredAfterRecreation() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Sign in", useUnmergedTree = true).performClick()
        capture("signin")
        compose.onNodeWithTag("email").performTextInput("synthetic@example.invalid")
        compose.onNodeWithTag("password").performTextInput("never-submit-this-fixture")
        compose.onNodeWithTag("submit-signin").performScrollTo()
        compose.onNodeWithTag("submit-signin").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("email").performScrollTo()
        compose.onNodeWithTag("email").assertTextContains("synthetic@example.invalid")
        compose.onNodeWithTag("submit-signin").performScrollTo()
        // Recreation resumes asynchronously. Require the new credential-free composition,
        // rather than accidentally asserting against the outgoing Activity's semantics.
        compose.waitUntil(timeoutMillis = 5000) {
            runCatching { compose.onNodeWithTag("submit-signin").assertIsNotEnabled(); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("submit-signin").assertIsNotEnabled()
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            fingerprint.contains("sdk_gphone", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let the actual display commit its rendered frame.
        val directory = File(compose.activity.getExternalFilesDir(null), "native-ui-evidence")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}
