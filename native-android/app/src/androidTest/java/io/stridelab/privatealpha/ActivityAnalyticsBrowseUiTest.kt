package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Synthetic source summaries; no network, accounts, or physical activity data. */
class ActivityAnalyticsBrowseUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun filteredHistoryOpensOriginalActivityByStableId() {
        val run = ActivityRecord("browse", "Saved 10 km run", "run", "garmin",
            Instant.parse("2026-11-18T01:00:00Z"),
            10000.0, 3600.0, 3550.0, 360.0, 145.0, 60.0)
        val selected = mutableStateOf<String?>(null)
        compose.activity.setContent {
            StrideTheme {
                ActivityAnalyticsScreen(
                    activities = listOf(run),
                    onBack = {},
                    onOpenActivity = { selected.value = it }
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("activity-browse"))
        compose.onNodeWithTag("activity-browse").performClick()
        compose.runOnIdle { assertEquals("browse", selected.value) }
    }
}
