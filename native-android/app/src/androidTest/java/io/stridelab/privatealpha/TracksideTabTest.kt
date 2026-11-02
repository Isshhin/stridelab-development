package io.stridelab.privatealpha

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Covers Trackside tab routes without changing the existing navigation/state contracts. */
class TracksideTabTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun fiveApprovedTabsKeepExistingRoutesAndMoreOpensSettings() {
        listOf("home", "training", "activities", "library", "settings").forEach {
            compose.onNodeWithTag("nav-$it").assertExists()
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Settings")
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("nav-home").assertIsSelected()
    }
}
