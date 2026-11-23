package io.stridelab.privatealpha

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test

/** Covers Trackside tab routes without changing the existing navigation/state contracts. */
class TracksideTabTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun threeTabsOpenTracksideMore() {
        listOf("home", "training", "settings").forEach {
            compose.onNodeWithTag("nav-$it").assertExists()
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        // Library is deferred: no entry point or obsolete page on the live More screen.
        compose.onNodeWithText("Workout library").assertDoesNotExist()
        compose.onNodeWithTag("more-row-library").assertDoesNotExist()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("more-row-about"))
        compose.onNodeWithTag("screen").performTouchInput {
            swipeUp(startY = height * .78f, endY = height * .55f)
        }
        compose.onNodeWithTag("more-row-about").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("more-detail-title", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("more-detail-title", useUnmergedTree = true)
            .assertTextEquals("About StrideLab")
        compose.onNodeWithTag("more-back").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("more-row-about", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }
}
