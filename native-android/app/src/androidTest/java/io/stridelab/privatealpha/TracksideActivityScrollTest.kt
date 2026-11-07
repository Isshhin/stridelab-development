package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import java.time.Instant

/**
 * Approved map preview -> expanded continuous activity details.
 * Synthetic offline-only records; no backend request or invented chart source.
 */
class TracksideActivityScrollTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun previewStartsWithoutPillThenExpandsIntoScrollDrivenSections() {
        val activity = ActivityRecord(
            "synthetic-scroll-activity", "Santa Mesa Running", "run", "manual",
            Instant.parse("2026-11-06T10:38:00Z"), 13110.0, 5100.0,
            5099.0, 390.0, 148.0, 64.0
        )
        val recorded = ActivityDetail(
            activity.id,
            samples = listOf(
                ActivitySample(0, 0.0, 405.0, 130.0, 12.0, null, null),
                ActivitySample(1, 60.0, 390.0, 145.0, 18.0, null, null),
                ActivitySample(2, 120.0, 381.0, 152.0, 15.0, null, null),
                ActivitySample(3, 180.0, 395.0, 149.0, 19.0, null, null)
            ),
            laps = (0 until 8).map { i ->
                ActivityLap(i, 1000.0, 390.0 + i, 390.0 + i, 140.0 + i)
            },
            fetchedAt = 0L
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-test-account",
            activities = listOf(activity)
        )
        val tab = mutableIntStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaActivityDetailSurface(
                        activity = activity, recorded = recorded,
                        detailSource = activity,
                        matchedSourceIds = setOf(activity.id),
                        history = listOf(activity),
                        state = state, actions = NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online = false, selectedTab = tab.intValue,
                        onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithTag("activity-primary-metrics").assertIsDisplayed()
        // Regression: the collapsed summary must finish near the bottom
        // edge, not leave a large unused sheet area below its metrics.
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val previewBottom = compose.onNodeWithTag("activity-preview-bottom")
            .getUnclippedBoundsInRoot()
        val handleBounds = compose.onNodeWithTag("activity-sheet-handle")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Collapsed sheet leaves empty space after its metrics",
            rootBounds.bottom - previewBottom.bottom < 40.dp
        )
        if (rootBounds.height >= 520.dp) {
            assertTrue(
                "Collapsed sheet should keep most of the map visible",
                handleBounds.top > rootBounds.height * 0.58f
            )
        }
        compose.onNodeWithTag("activity-sticky-pill").assertDoesNotExist()
        compose.onNodeWithTag("activity-sheet-handle").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-overview").assertIsSelected()
        compose.onNodeWithTag("activity-tab-splits").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-section-splits")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-charts").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-section-charts")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-charts").assertIsSelected()
        compose.onNodeWithTag("activity-continuous-scroll").assertExists()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-expanded-back").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("activity-parity-overview").assertExists()
    }

    @Test fun activityWithoutSourceStreamsDoesNotInventSplitsOrCharts() {
        val activity = ActivityRecord(
            "synthetic-scroll-no-streams", "Evening Run", "run", "manual",
            Instant.parse("2026-11-06T10:38:00Z"), 5000.0, 1800.0,
            1750.0, null, null, null
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-test-account",
            activities = listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaActivityDetailSurface(
                        activity = activity, recorded = null,
                        detailSource = activity, matchedSourceIds = setOf(activity.id),
                        history = listOf(activity),
                        state = state, actions = NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online = false, selectedTab = 0, onTab = {},
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sticky-pill").assertDoesNotExist()
        compose.onNodeWithTag("activity-sheet-handle").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-charts").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText("No recorded charts are available.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-chart-pace").assertDoesNotExist()
    }
}
