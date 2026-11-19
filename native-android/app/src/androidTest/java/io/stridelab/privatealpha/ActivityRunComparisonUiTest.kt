package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Synthetic recorded summaries only; does not sign in, access Garmin or write the database. */
class ActivityRunComparisonUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private val recent = ActivityRecord(
        "recent", "Recent 10K", "run", "garmin",
        Instant.parse("2026-11-18T09:00:00Z"), 10000.0, 3600.0,
        3540.0, 360.0, 145.0, 25.0
    )
    private val earlier = recent.copy(
        id = "earlier", title = "Earlier 10K",
        startedAt = Instant.parse("2026-11-12T09:00:00Z"),
        recordedPace = 390.0, averageHr = 149.0
    )
    private fun mount(rows: List<ActivityRecord>) {
        compose.activity.setContent {
            StrideTheme {
                ActivityRunComparisonScreen(rows, onBack = {})
            }
        }
        compose.waitForIdle()
    }

    @Test fun selectingTwoRunsShowsRecordedComparison() {
        mount(listOf(recent, earlier))
        compose.onNodeWithTag("compare-row-recent-recent").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("compare-row-earlier-earlier").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("compare-change-earlier").assertExists()
        compose.onNodeWithText(
            "Recorded pace was 7.7% faster than the earlier run.",
            substring = true
        ).assertExists()
        compose.onNodeWithText("Earlier: 149 bpm", substring = true).assertExists()
        compose.onNodeWithText("Recent: 145 bpm", substring = true).assertExists()
    }

    @Test fun missingRecordedPaceStaysUnknownInComparison() {
        mount(listOf(recent.copy(recordedPace = null), earlier))
        compose.onNodeWithTag("compare-row-recent-recent").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("compare-row-earlier-earlier").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(
            "Recorded average pace is unavailable for one or both runs.",
            substring = true
        ).assertExists()
    }

    @Test fun noRunningHistoryDisplaysUsefulEmptyState() {
        mount(emptyList())
        compose.onNodeWithText("No comparable running history yet", substring = true).assertExists()
    }
}
