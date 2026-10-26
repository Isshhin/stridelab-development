package io.stridelab.privatealpha

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
    private fun showHistory() {
        val rows = (0..39).map { index -> ActivityRecord("synthetic-tab-$index", "Saved walk $index", "Walking", "manual",
            Instant.parse("2026-10-26T00:00:00Z").minusSeconds(index * 3600L), 2500.0, 900.0, null, null, null, null) }
        val state = TrainingState(initializing = false, account = "synthetic-tabs", activities = rows)
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme { NativeStrideContent(state, true, NativeActions({}, {}, {}, {}, { _, _ -> })) } }
        }
    }
    private fun tab(path: String, title: String) {
        compose.onNodeWithTag("nav-$path").performClick()
        compose.onNode(hasTestTag("screen") and SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).assertExists()
    }
    private fun back() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    @Test fun detailHomeActivitiesOpensListAndPreservesListPositionAcrossRepeatedSwitches() {
        showHistory()
        tab("activities", "Activities")
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("activity-synthetic-tab-39"))
        compose.onNodeWithTag("activity-synthetic-tab-39").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Saved walk 39")
        tab("home", "Your training")
        tab("activities", "Activities")
        // Root navigation must not discard useful state to fix the restored-detail bug.
        compose.onNodeWithTag("activity-synthetic-tab-39").assertIsDisplayed()
        repeat(3) {
            tab("settings", "Settings")
            tab("home", "Your training")
            tab("activities", "Activities")
            compose.onNodeWithTag("activity-synthetic-tab-39").assertIsDisplayed()
        }
        tab("activities", "Activities")
        back()
        compose.onNodeWithTag("screen-title").assertTextEquals("Your training")
    }

    @Test fun androidBackAlwaysReturnsDetailToHistoryIncludingHomeShortcut() {
        showHistory()
        tab("activities", "Activities")
        compose.onNodeWithTag("activity-synthetic-tab-0").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Saved walk 0")
        back()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        tab("home", "Your training")
        compose.onNodeWithTag("activity-synthetic-tab-0").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Saved walk 0")
        back()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
        compose.onNodeWithTag("activity-synthetic-tab-0").performClick()
        tab("activities", "Activities")
    }
}
