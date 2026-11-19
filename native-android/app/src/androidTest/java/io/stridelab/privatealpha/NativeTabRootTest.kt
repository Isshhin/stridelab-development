package io.stridelab.privatealpha

import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Native navigation with immutable synthetic records; no account or backend requests. */
class NativeTabRootTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun legacyDestinationsRedirectToCurrentRoots() {
        lateinit var navigation: NavHostController
        compose.runOnUiThread {
            compose.activity.setContent {
                val nav = rememberNavController()
                navigation = nav
                StrideTheme {
                    NativeStrideContent(TrainingState(initializing = false), false,
                        NativeActions({}, { _, _ -> }), nav)
                }
            }
        }
        compose.onNodeWithTag("nav-home").assertExists()
        for ((old, current) in listOf("activities" to "training", "more/garmin" to "settings",
                                     "more/data" to "settings")) {
            compose.runOnIdle { navigation.navigate(old) }
            compose.waitUntil(5_000) {
                compose.runOnIdle { navigation.currentDestination?.route == current }
            }
            compose.onNodeWithTag("nav-$current").assertIsSelected()
        }
    }
    private fun showHistory() {
        val rows = (0..39).map { index -> ActivityRecord("synthetic-tab-$index", "Saved walk $index", "Walking", "manual",
            Instant.now().minusSeconds(index * 3600L), 2500.0, 900.0, null, null, null, null) }
        val state = TrainingState(initializing = false, account = "synthetic-tabs", activities = rows)
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme { NativeStrideContent(state, true, NativeActions({}, { _, _ -> })) } }
        }
    }
    private fun tab(path: String, title: String) {
        // The approved floating navigation is a fixed Scaffold sibling, not a LazyColumn item.
        // Click its real root item directly; scrolling the screen to "nav-settings"
        // was an invalid test locator and never exercised the More destination.
        compose.onNodeWithTag("nav-$path").assertIsDisplayed().performClick()
        when(path) {
            "training" -> compose.onNodeWithTag("training-unified-timeline").assertExists()
            "home" -> compose.onNodeWithTag("today-week-swipe").assertExists()
            else -> compose.onNode(hasTestTag("screen") and SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).assertExists()
        }
    }
    private fun back() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    @Test fun trainingHistoryOpensDetailAndPreservesListPositionAcrossRootSwitches() {
        showHistory()
        tab("training", "Training")
        compose.onNodeWithTag("training-unified-timeline", useUnmergedTree = true).performScrollToNode(hasTestTag("timeline-recorded-synthetic-tab-39"))
        compose.onNodeWithTag("timeline-recorded-synthetic-tab-39").performClick()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        back()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("timeline-recorded-synthetic-tab-39").assertIsDisplayed()
        repeat(3) {
            tab("settings", "More")
            tab("home", "Today")
            tab("training", "Training")
            compose.onNodeWithTag("timeline-recorded-synthetic-tab-39").assertIsDisplayed()
        }
    }

    @Test fun androidBackAlwaysReturnsDetailToHistoryIncludingHomeShortcut() {
        showHistory()
        tab("training", "Training")
        val timeline=compose.onNodeWithTag("training-unified-timeline",useUnmergedTree=true)
        timeline.performScrollToNode(hasTestTag("timeline-recorded-synthetic-tab-0"))
        // The final Today card can sit beneath the floating dock after lazy-list alignment.
        timeline.performTouchInput { swipeUp(startY=height*.8f,endY=height*.35f) }
        compose.onNodeWithTag("timeline-recorded-synthetic-tab-0").assertIsDisplayed().performClick()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        back()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("nav-training").assertIsSelected()
        compose.onNodeWithTag("timeline-recorded-synthetic-tab-0").assertIsDisplayed()
    }

    @Test fun retiredLibraryRemainsAbsentFromMoreAndTheDock() {
        tab("settings", "More")
        compose.onNodeWithTag("nav-library").assertDoesNotExist()
        compose.onNodeWithText("Exercise library").assertDoesNotExist()
    }
}
