
package io.stridelab.privatealpha

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/**
 * Isolated navigation tests.
 *
 * Uses a synthetic signed-out UI so previous instrumentation
 * tests cannot influence the expected account state.
 *
 * Does not submit authentication requests.
 * Does not delete existing workout history or databases.
 */
class NativeNavigationTest {

    private val compose =
        createAndroidComposeRule<NativeMainActivity>()

    /**
     * Installs a synthetic UI after each Activity creation,
     * including after ActivityScenario.recreate().
     *
     * The original account preference is temporarily hidden
     * before Activity startup and restored after testing.
     */
    private val isolationRule = TestRule { base, _ ->

        object : Statement() {

            override fun evaluate() {

                val instrumentation =
                    InstrumentationRegistry.getInstrumentation()

                val context = instrumentation.targetContext

                val application =
                    context.applicationContext as Application

                val preferences =
                    context.getSharedPreferences(
                        "native-account-v1",
                        Context.MODE_PRIVATE
                    )

                val previousAccount =
                    preferences.getString("active", null)

                // Prevent loading an existing account at startup.
                check(
                    preferences.edit()
                        .remove("active")
                        .commit()
                ) {
                    "Unable to establish signed-out test state."
                }

                /*
                 * A controlled UI is installed after onCreate.
                 *
                 * It uses the real NativeStrideContent,
                 * navigation graph, and authentication screen,
                 * but never uses a real repository account.
                 */
                val callbacks =
                    object : Application.ActivityLifecycleCallbacks {

                        override fun onActivityCreated(
                            activity: Activity,
                            savedInstanceState: Bundle?
                        ) = Unit

                        override fun onActivityPostCreated(
                            activity: Activity,
                            savedInstanceState: Bundle?
                        ) {
                            if (activity is NativeMainActivity) {

                                activity.setContent {
                                    StrideTheme {
                                        NativeStrideContent(
                                            state = TrainingState(
                                                initializing = false,
                                                account = null
                                            ),
                                            online = true,
                                            actions = NativeActions(
                                                refresh = {},
                                                checkIntervals = {},
                                                syncIntervals = {},
                                                signOut = {},
                                                signIn = { _, _ -> }
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        override fun onActivityStarted(
                            activity: Activity
                        ) = Unit

                        override fun onActivityResumed(
                            activity: Activity
                        ) = Unit

                        override fun onActivityPaused(
                            activity: Activity
                        ) = Unit

                        override fun onActivityStopped(
                            activity: Activity
                        ) = Unit

                        override fun onActivitySaveInstanceState(
                            activity: Activity,
                            outState: Bundle
                        ) = Unit

                        override fun onActivityDestroyed(
                            activity: Activity
                        ) = Unit
                    }

                application.registerActivityLifecycleCallbacks(
                    callbacks
                )

                try {
                    base.evaluate()
                } finally {

                    application.unregisterActivityLifecycleCallbacks(
                        callbacks
                    )

                    // Restore the previous account preference.
                    val editor = preferences.edit()

                    if (previousAccount != null) {
                        editor.putString(
                            "active",
                            previousAccount
                        )
                    } else {
                        editor.remove("active")
                    }

                    check(editor.commit()) {
                        "Unable to restore original account preference."
                    }
                }
            }
        }
    }

    /**
     * The isolation rule runs before the Compose Activity rule,
     * ensuring the synthetic UI is available from startup.
     */
    @get:Rule
    val rules: TestRule =
        RuleChain.outerRule(isolationRule)
            .around(compose)

    @Test
    fun nativeEmptyStateAndNavigationSurviveRecreation() {

        compose.waitForIdle()

        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Your training")

        capture("home")

        // Navigate to Activities.
        compose.onNodeWithTag("nav-activities")
            .performClick()

        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Activities")

        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasText("Sign in to view your history")
            )

        compose.onNodeWithText(
            "Sign in to view your history"
        ).assertExists()

        // Recreate the real Activity.
        compose.activityRule.scenario.recreate()

        compose.waitForIdle()

        // Navigation state must survive recreation.
        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Activities")

        // Signed-out contents must remain available.
        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasText("Sign in to view your history")
            )

        compose.onNodeWithText(
            "Sign in to view your history"
        ).assertExists()

        capture("activities")

        // Verify navigation still works after recreation.
        compose.onNodeWithTag("nav-settings")
            .performClick()

        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Settings")

        capture("settings")
    }

    @Test
    fun passwordIsNotRestoredAfterRecreation() {

        compose.waitForIdle()

        // Open Settings.
        compose.onNodeWithTag("nav-settings")
            .performClick()

        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Settings")

        // Find the signed-out Sign in button.
        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasText("Sign in")
            )

        compose.onNodeWithText(
            "Sign in",
            useUnmergedTree = true
        ).performClick()

        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Welcome back")

        capture("signin")

        // Synthetic credentials; never submitted.
        compose.onNodeWithTag("email")
            .performTextInput(
                "synthetic@example.invalid"
            )

        compose.onNodeWithTag("password")
            .performTextInput(
                "never-submit-this-fixture"
            )

        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasTestTag("submit-signin")
            )

        // Confirm inputs were accepted before recreation.
        compose.onNodeWithTag("submit-signin")
            .assertIsEnabled()

        // Recreate the real Android Activity.
        compose.activityRule.scenario.recreate()

        compose.waitForIdle()

        // Auth navigation should remain restored.
        compose.onNodeWithTag("screen-title")
            .assertTextEquals("Welcome back")

        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasTestTag("email")
            )

        // Email is allowed to survive recreation.
        compose.onNodeWithTag("email")
            .assertTextContains(
                "synthetic@example.invalid"
            )

        compose.onNodeWithTag("screen")
            .performScrollToNode(
                hasTestTag("submit-signin")
            )

        /*
         * Password must not survive Activity recreation.
         *
         * Submit must therefore be disabled even though
         * the email address was restored.
         */
        compose.waitUntil(timeoutMillis = 5000) {

            runCatching {
                compose.onNodeWithTag("submit-signin")
                    .assertIsNotEnabled()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithTag("submit-signin")
            .assertIsNotEnabled()
    }

    private fun capture(name: String) {

        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        compose.runOnUiThread {
            compose.activity.window.decorView.invalidate()
        }

        InstrumentationRegistry
            .getInstrumentation()
            .waitForIdleSync()

        android.os.SystemClock.sleep(350)

        val directory = File(
            compose.activity.getExternalFilesDir(null),
            "native-ui-evidence"
        )

        check(
            directory.isDirectory || directory.mkdirs()
        )

        File(directory, "$name.png")
            .outputStream()
            .use { output ->

                val screenshot = checkNotNull(
                    InstrumentationRegistry
                        .getInstrumentation()
                        .uiAutomation
                        .takeScreenshot()
                )

                assertTrue(
                    screenshot.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        output
                    )
                )
            }
    }
}
