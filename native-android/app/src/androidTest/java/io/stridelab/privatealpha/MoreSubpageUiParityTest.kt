package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Current More visual shell / navigation regression, using synthetic source
 * summaries and inert backup callbacks. Does not access personal records.
 */
class MoreSubpageUiParityTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun approvedMorePaletteAndSubpageHeaderRemainConsistent() {
        assertEquals(Color(0xFF080B0A), MoreFeatureColors.background)
        assertEquals(Color(0xFF151A18), MoreFeatureColors.surface)
        assertEquals(Color(0xFFD8FB70), MoreFeatureColors.lime)
        compose.activity.setContent {
            StrideTheme {
                ActivityAnalyticsScreen(emptyList(), onBack = {})
            }
        }
        compose.onNodeWithTag("screen-title").assertExists()
        compose.onNodeWithTag("analytics-back").assertExists()
        compose.onNodeWithTag("analytics-run-count").assertExists()
    }

    @Test fun runningFiltersAndCompareActionRemainOperable() {
        val opened = mutableStateOf(false)
        val sample = ActivityRecord(
            "ui-parity-run", "Recorded training run", "run", "garmin",
            Instant.now().minusSeconds(7200L), 10000.0, 3600.0,
            3540.0, 360.0, 144.0, 30.0
        )
        compose.activity.setContent {
            StrideTheme {
                ActivityAnalyticsScreen(
                    listOf(sample), onBack = {},
                    onCompare = { opened.value = true }
                )
            }
        }
        compose.onNodeWithTag("analytics-period-week").assertExists()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("analytics-source-garmin"))
        compose.onNodeWithTag("analytics-source-garmin").performClick()
        compose.onNodeWithTag("analytics-clear-filters").assertExists()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("analytics-compare-open"))
        compose.onNodeWithTag("analytics-compare-open").performClick()
        compose.runOnIdle { assertTrue(opened.value) }
    }

    @Test fun backupSourceEligibilityAndSafetyCopyRemainVisible() {
        compose.activity.setContent {
            StrideTheme {
                LocalActivityArchiveScreen(
                    cloudAvailable = false,
                    onBack = {},
                    onExport = { error("This view test must not export a ZIP.") },
                    onRestore = { _,_ -> error("This view test must not restore a ZIP.") }
                )
            }
        }
        compose.onNodeWithTag("screen")
            .performScrollToNode(androidx.compose.ui.test.hasText(
                "ZIP files are NOT encrypted", substring = true))
        compose.onNodeWithText("ZIP files are NOT encrypted", substring = true).assertExists()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("archive-local-garmin"))
        compose.onNodeWithTag("archive-local-garmin").assertIsEnabled()
        compose.onNodeWithTag("archive-cloud-account").assertIsNotEnabled()
    }

    @Test fun manualEditorKeepsSignedOutDisciplineAndFormControls() {
        compose.activity.setContent {
            StrideTheme {
                StandaloneDisciplineLogScreen(
                    owner = "qa-parity-manual-owner",
                    onBack = {}
                )
            }
        }
        compose.onNodeWithTag("screen-title").assertExists()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("manual-discipline-strength"))
        compose.onNodeWithTag("manual-discipline-strength").assertIsEnabled()
        compose.onNodeWithTag("manual-title").assertExists()
    }

    @Test fun compareEmptyStateUsesCurrentMoreSubpageHeader() {
        compose.activity.setContent {
            StrideTheme { ActivityRunComparisonScreen(emptyList(), onBack = {}) }
        }
        compose.onNodeWithTag("screen-title").assertExists()
        compose.onNodeWithTag("compare-back").assertExists()
        compose.onNodeWithText("No comparable running history yet", substring = true)
            .assertExists()
    }
}
