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
                ActivitySample(0, 0.0, 405.0, 130.0, 12.0, null, null, 172.0),
                ActivitySample(1, 60.0, 390.0, 145.0, 18.0, null, null, 177.0),
                ActivitySample(2, 120.0, 381.0, 152.0, 15.0, null, null, 180.0),
                ActivitySample(3, 180.0, 395.0, 149.0, 19.0, null, null, 175.0)
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
        val previewHeight = compose.onNodeWithTag("activity-parity-overview")
            .getUnclippedBoundsInRoot().height
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
        // The summary stays mounted, at the same measured height, as the
        // expanded content fades in. No full content swap or peek-anchor jump.
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithTag("activity-preview-bottom").assertExists()
        org.junit.Assert.assertEquals(
            previewHeight,
            compose.onNodeWithTag("activity-parity-overview")
                .getUnclippedBoundsInRoot().height
        )
        // Compact Athlete Intelligence-inspired overview. Evidence remains
        // collapsed until explicitly requested and must stay within the
        // original scrolling details sheet.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-insights"))
        compose.onNodeWithTag("activity-insights-headline").assertExists()
        compose.onNodeWithTag("activity-insights-summary").assertExists()
        compose.onNodeWithTag("activity-insights-detail-0").assertDoesNotExist()
        compose.onNodeWithTag("activity-insights-toggle").performClick()
        compose.onNodeWithTag("activity-insights-detail-0").assertExists()
        compose.onNodeWithTag("activity-insights-toggle").performClick()
        compose.onNodeWithTag("activity-insights-detail-0").assertDoesNotExist()

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
        // Cadence is a REAL recorded stream, not a calculated placeholder.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-chart-cadence"))
        compose.onNodeWithTag("activity-chart-cadence").assertIsDisplayed()
        compose.onNodeWithTag("activity-chart-summary-cadence").assertExists()
        compose.onNodeWithTag("stream-Cadence").performTouchInput { swipeRight() }
        compose.onNodeWithTag("activity-chart-cursor-cadence").assertExists()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-expanded-back").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("activity-parity-overview").assertExists()
    }

    @Test fun summaryCanHideForMapOnlyAndRestoreWithGrip() {
        val activity = ActivityRecord(
            "synthetic-map-only-activity", "Santa Mesa Running", "run", "manual",
            Instant.parse("2026-11-06T10:38:00Z"), 13110.0, 5100.0,
            5099.0, 390.0, 148.0, 64.0
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-map-only-account",
            activities = listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaActivityDetailSurface(
                        activity = activity,
                        recorded = null,
                        detailSource = activity,
                        matchedSourceIds = setOf(activity.id),
                        history = listOf(activity),
                        state = state,
                        actions = NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online = false, selectedTab = 0, onTab = {},
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-only-handle").assertDoesNotExist()
        compose.onNodeWithTag("activity-parity-overview").assertIsDisplayed()

        // Same third anchor reached by dragging below the compact summary.
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.Dismiss)
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-map-only-handle")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-map-only-handle").assertIsDisplayed()
        val mapOnlyRoot = compose.onRoot().getUnclippedBoundsInRoot()
        val sheetLip = compose.onNodeWithTag("activity-map-only-handle")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "The map-only grip must span the screen, not float as a black pill",
            sheetLip.width >= mapOnlyRoot.width - 2.dp
        )
        assertTrue(
            "Map-only should leave only a thin sheet edge visible",
            sheetLip.height <= 26.dp
        )
        assertTrue(
            "The sheet grip should sit flush with the bottom of the screen",
            mapOnlyRoot.bottom - sheetLip.bottom < 2.dp
        )
        compose.onNodeWithTag("activity-map-attribution").assertDoesNotExist()
        compose.onNodeWithTag("activity-sticky-pill").assertDoesNotExist()

        // Touching the small bottom grip restores the approved summary.
        compose.onNodeWithTag("activity-map-only-handle").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-map-only-handle")
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("activity-parity-overview").assertIsDisplayed()
        compose.onNodeWithTag("activity-primary-metrics").assertIsDisplayed()

        // The third position must not break the previous expanded behavior.
        compose.onNodeWithTag("activity-sheet-handle").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-overview").assertIsSelected()
        compose.onNodeWithTag("activity-expanded-back").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-sticky-pill")
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("activity-parity-overview").assertIsDisplayed()
    }

    @Test fun compactLinkRunDoesNotShowLegacyExplainerWhenNoPlanExists() {
        val activity = ActivityRecord(
            "synthetic-link-activity", "Recorded Run", "run", "manual",
            Instant.parse("2026-11-06T10:38:00Z"), 5000.0, 1800.0,
            1760.0, 360.0, 140.0, null
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    ActivityPlanLink(
                        TrainingState(initializing = false, plans = emptyList()),
                        activity, PlanActions()
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-link-run-action").assertExists()
        compose.onNodeWithText("Link run").assertExists()
        compose.onNodeWithText("PLANNED & RECORDED").assertDoesNotExist()
        compose.onNodeWithText("Link to planned run").assertDoesNotExist()
    }

    @Test fun activityMapUsesPublishedDarkVectorStyle() {
        org.junit.Assert.assertEquals(
            "https://tiles.openfreemap.org/styles/dark",
            TracksideMapTiles.STYLE_URL
        )
        assertTrue(TracksideMapTiles.ATTRIBUTION.contains("OpenStreetMap"))
        assertTrue(TracksideMapTiles.ATTRIBUTION.contains("OpenFreeMap"))
    }

    @Test fun twilightMapKeepsStreetsLegibleAndDistinctFromGround() {
        // These are the actual IDs published by OpenFreeMap's Dark Matter
        // style, not titles copied from a different provider style.
        val ids = TracksideReadableMapPalette.recoloredLayerIds
        for (id in listOf(
            "background", "water", "landuse_park", "highway_minor",
            "highway_major_inner", "highway_motorway_inner",
            "highway_name_other", "place_suburb", "building"
        )) assertTrue("Missing real provider layer ID: " + id, id in ids)
        assertFalse(ids.contains("Background"))
        assertFalse(ids.contains("Road network"))
        assertFalse(ids.contains("Road labels"))

        val ground = TracksideReadableMapPalette.canvas
        val minor = TracksideReadableMapPalette.minorRoads
        val major = TracksideReadableMapPalette.majorRoads
        val labels = TracksideReadableMapPalette.roadLabels
        val parks = TracksideReadableMapPalette.grass
        val water = TracksideReadableMapPalette.water
        fun red(color: Int) = android.graphics.Color.red(color)
        fun green(color: Int) = android.graphics.Color.green(color)
        fun blue(color: Int) = android.graphics.Color.blue(color)

        // Google Maps-inspired slate-blue night palette: never near black.
        assertTrue(red(ground) >= 35)
        assertTrue(red(minor) - red(ground) >= 45)
        assertTrue(red(major) > red(minor))
        assertTrue(blue(labels) - blue(major) >= 70)
        assertTrue(green(parks) > green(ground))
        assertTrue(blue(water) > blue(ground))
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
