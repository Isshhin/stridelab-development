package io.stridelab.privatealpha

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithTag("more-row-about").performClick()
        compose.onNodeWithTag("more-detail-title").assertTextEquals("About StrideLab")
        compose.onNodeWithTag("more-back").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }
}
