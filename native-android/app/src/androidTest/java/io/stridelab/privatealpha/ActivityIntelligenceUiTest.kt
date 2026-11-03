package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Synthetic display-only history, no backend writes or real athlete claims. */
class ActivityIntelligenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    @Test fun comparisonIsReachableFromDetailAndCaveatsRemainVisible() {
        val run = ActivityRecord("analysis-current", "Recorded run", "run", "intervals", Instant.parse("2026-10-26T00:00:00Z"),5000.0,1800.0,1750.0,360.0,145.0,0.0)
        val rows = listOf(run) + (1..3).map { run.copy(id="analysis-$it", startedAt=run.startedAt.minusSeconds(it*86400L),recordedPace=400.0) }
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-analysis",activities=rows),false,NativeActions({},{},{},{},{_,_->})) } } }
        compose.onNodeWithTag("activity-analysis-current").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("ACTIVITY HIGHLIGHTS"))
        compose.onNodeWithText("ACTIVITY HIGHLIGHTS").assertIsDisplayed()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Your recorded average pace",substring=true))
        compose.onNodeWithText("not improved or reduced fitness",substring=true).assertIsDisplayed()
        compose.onNodeWithTag("offline-status").assertIsDisplayed()
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
    }
}
