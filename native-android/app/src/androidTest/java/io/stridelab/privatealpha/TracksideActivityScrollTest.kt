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
            // HR-zone estimates classify only adjacent recorded intervals
            // lasting at most 30 seconds. Preserve the existing sparse
            // samples for chart coverage but add two genuine short intervals
            // so the zone presentation can be asserted without bypassing QC.
            samples = listOf(
                ActivitySample(0, 0.0, 405.0, 130.0, 12.0, null, null, 172.0),
                ActivitySample(1, 10.0, 403.0, 133.0, 13.0, null, null, 173.0),
                ActivitySample(2, 20.0, 400.0, 137.0, 14.0, null, null, 174.0),
                ActivitySample(3, 60.0, 390.0, 145.0, 18.0, null, null, 177.0),
                ActivitySample(4, 120.0, 381.0, 152.0, 15.0, null, null, 180.0),
                ActivitySample(5, 180.0, 395.0, 149.0, 19.0, null, null, 175.0)
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
        AthleteHeartRatePreferences(compose.activity).save(
            state.account,
            AthleteHeartRateZoneSettings(
                method = HeartRateZoneMethod.MAX_BPM,
                maximumHr = 197
            )
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
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
        compose.onNodeWithText("DURATION").assertDoesNotExist()
        compose.onNodeWithTag("activity-metric-duration").assertDoesNotExist()
        compose.onNodeWithText("AVG HR").assertExists()
        listOf("distance", "avg-pace", "avg-hr").forEach { metric ->
            compose.onNodeWithTag("activity-metric-" + metric).assertExists()
        }
        // The three remaining metrics occupy equally sized, centered sections.
        val metricPositions = listOf("distance", "avg-pace", "avg-hr")
            .map { name ->
                val bounds = compose.onNodeWithTag("activity-metric-" + name)
                    .getUnclippedBoundsInRoot()
                (bounds.left + bounds.right) / 2f
            }
        val spacing = metricPositions.zipWithNext().map { (a, b) -> b - a }
        assertTrue("Metric columns are not evenly distributed",
            spacing.all { kotlin.math.abs((it - spacing.first()).value) < 2.5f })
        // Regression: the collapsed summary must finish near the bottom
        // edge, not leave a large unused sheet area below its metrics.
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val previewBottom = compose.onNodeWithTag("activity-preview-bottom")
            .getUnclippedBoundsInRoot()
        val previewHeight = compose.onNodeWithTag("activity-parity-overview")
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val titleHeight = compose.onNodeWithTag("activity-preview-title")
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val metricsHeight = compose.onNodeWithTag("activity-primary-metrics")
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val handleBounds = compose.onNodeWithTag("activity-sheet-handle")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Collapsed sheet leaves empty space after its metrics",
            rootBounds.bottom - previewBottom.bottom < 40.dp
        )
        if ((rootBounds.bottom - rootBounds.top) >= 520.dp) {
            assertTrue(
                "Collapsed sheet should keep most of the map visible: " +
                    "sheet top=${handleBounds.top}, viewport height=${rootBounds.bottom - rootBounds.top}, " +
                    "peek preview height=$previewHeight, title=$titleHeight, metrics=$metricsHeight, " +
                    "viewport width=${rootBounds.right - rootBounds.left}",
                handleBounds.top > (rootBounds.bottom - rootBounds.top) * 0.58f
            )
        }
        compose.onNodeWithTag("activity-sticky-pill").assertExists().assertIsNotDisplayed()
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
                .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        )
        // Compact Athlete Intelligence-inspired overview. Evidence remains
        // collapsed until explicitly requested and must stay within the
        // original scrolling details sheet.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-summary-redesign"))
        compose.onNodeWithTag("activity-summary-redesign").assertExists()
        compose.onNodeWithTag("activity-summary-source").assertExists()
        compose.onNodeWithText("Summary").assertExists()
        compose.onNodeWithText("Key stats from your run at Santa Mesa.").assertExists()
        compose.onNodeWithText("13.11 km").assertExists()
        compose.onNodeWithText("1:24:59").assertExists()
        compose.onNodeWithText("Elapsed time").assertDoesNotExist()
        val summaryBounds = compose.onNodeWithTag("activity-summary-redesign")
            .getUnclippedBoundsInRoot()
        for (tag in listOf("activity-summary-heading", "activity-summary-subtitle")) {
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(kotlin.math.abs((((bounds.left + bounds.right) - (summaryBounds.left + summaryBounds.right)) / 2f).value) < 2f)
        }
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-insights"))
        compose.onNodeWithTag("activity-insights-headline").assertExists()
        compose.onNodeWithTag("activity-insights-summary").assertExists()
        compose.onNodeWithTag("activity-insights-descriptor").assertExists()
        compose.onNodeWithText("Steady pacing", substring = true).assertExists()
        compose.onNodeWithText("Overall Analysis").assertExists()
        compose.onNodeWithText("RECORDED DATA").assertDoesNotExist()
        compose.onNodeWithTag("activity-insights-detail-0").assertDoesNotExist()
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-insights-toggle"))
        compose.onNodeWithTag("activity-insights-toggle").performClick()
        compose.onNodeWithTag("activity-insights-detail-0").assertExists()
        // Expanding makes the card taller; scroll to the relocated button
        // before tapping again rather than relying on a stale screen offset.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-insights-toggle"))
        compose.onNodeWithTag("activity-insights-toggle").performClick()
        compose.onNodeWithTag("activity-insights-detail-0").assertDoesNotExist()

        compose.onNodeWithTag("activity-tab-splits").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-section-splits")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val splitsBounds = compose.onNodeWithTag("activity-section-splits")
            .getUnclippedBoundsInRoot()
        val splitsHeadingBounds = compose.onNodeWithTag("activity-section-heading-splits")
            .getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((((splitsHeadingBounds.left + splitsHeadingBounds.right) - (splitsBounds.left + splitsBounds.right)) / 2f).value) < 2f)
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-splits-insights"))
        compose.onNodeWithTag("activity-splits-insights").assertExists()
        compose.onNodeWithText("Splits Analysis").assertExists()
        compose.onNodeWithContentDescription("fastest split", substring = true).assertExists()
        compose.onNodeWithContentDescription("slowest split", substring = true).assertExists()
        compose.onNodeWithTag("activity-tab-charts").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-section-charts")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val chartsBounds = compose.onNodeWithTag("activity-section-charts")
            .getUnclippedBoundsInRoot()
        val chartsHeadingBounds = compose.onNodeWithTag("activity-section-heading-charts")
            .getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs((((chartsHeadingBounds.left + chartsHeadingBounds.right) - (chartsBounds.left + chartsBounds.right)) / 2f).value) < 2f)
        compose.onNodeWithTag("activity-tab-charts").assertIsSelected()
        compose.onNodeWithTag("activity-continuous-scroll").assertExists()
        // HR zones use the athlete-level value saved in App preferences.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-chart-heart-rate-zones"))
        compose.onNodeWithTag("activity-chart-heart-rate-zones").assertExists()
        compose.onNodeWithTag("activity-hr-zone-max-input").assertDoesNotExist()
        compose.onNodeWithTag("activity-hr-zone-method").assertExists()
        compose.onNodeWithText("MAX BPM").assertExists()
        compose.onNodeWithTag("activity-hr-zone-1").assertExists()
        compose.onNodeWithTag("activity-hr-zone-5").assertExists()
        compose.onNodeWithTag("activity-chart-insights-heart-rate-zones").assertExists()
        // Cadence is a REAL recorded stream, not a calculated placeholder.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-chart-cadence"))
        compose.onNodeWithTag("activity-chart-cadence").assertIsDisplayed()
        compose.onNodeWithTag("activity-chart-summary-cadence").assertExists()
        compose.onNodeWithTag("activity-chart-y-axis-cadence").assertExists()
        compose.onNodeWithTag("activity-chart-insights-cadence").assertExists()
        compose.onNodeWithText("Cadence Analysis").assertExists()
        // The branded analysis must be below (not inside) the plotted chart
        // container. Expanding it cannot change the chart's surface bounds.
        val chartBounds = compose.onNodeWithTag("activity-chart-cadence")
            .getUnclippedBoundsInRoot()
        val analysisBounds = compose.onNodeWithTag("activity-chart-insights-cadence")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Cadence Analysis is still inside the chart surface",
            analysisBounds.top >= chartBounds.bottom
        )
        compose.onNodeWithTag("stream-Cadence").performTouchInput { swipeRight() }
        compose.onNodeWithTag("activity-chart-cursor-cadence").assertExists()
        // Cadence Analysis is the final real content item in this fixture.
        // Once it reaches the bottom, additional upward swipes must not
        // reveal the former viewport-sized blank tail.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-chart-insights-cadence"))
        repeat(3) {
            compose.onNodeWithTag("activity-continuous-scroll")
                .performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        val scrollBottom = compose.onNodeWithTag("activity-continuous-scroll")
            .getUnclippedBoundsInRoot().bottom
        val finalAnalysisBottom = compose.onNodeWithTag("activity-chart-insights-cadence")
            .getUnclippedBoundsInRoot().bottom
        assertTrue(
            "Blank scrolling continues past the final recorded analysis",
            scrollBottom - finalAnalysisBottom <= 60.dp
        )
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-expanded-back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("activity-sticky-pill").assertExists().assertIsNotDisplayed()
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
                    TracksideActivityDetailSurface(
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
            (sheetLip.right - sheetLip.left) >= (mapOnlyRoot.right - mapOnlyRoot.left) - 2.dp
        )
        assertTrue(
            "Map-only should leave only a thin sheet edge visible",
            (sheetLip.bottom - sheetLip.top) <= 26.dp
        )
        assertTrue(
            "The sheet grip should sit flush with the bottom of the screen",
            mapOnlyRoot.bottom - sheetLip.bottom < 2.dp
        )
        compose.onNodeWithTag("activity-map-attribution").assertDoesNotExist()
        compose.onNodeWithTag("activity-sticky-pill").assertExists().assertIsNotDisplayed()

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
        compose.waitForIdle()
        compose.onNodeWithTag("activity-sticky-pill").assertExists().assertIsNotDisplayed()
        compose.onNodeWithTag("activity-parity-overview").assertIsDisplayed()
    }

    @Test fun linkRunIsHiddenWithoutRunningPlanAndShownForMixedPlanWithRun() {
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
        compose.onNodeWithTag("activity-link-run-action").assertDoesNotExist()

        val strengthOnlyPlan = PlanSnapshot(
            id = "synthetic-strength-plan",
            owner = "synthetic-owner",
            engineVersion = ENGINE1_VERSION,
            policyId = ENGINE1_POLICY,
            createdAt = Instant.EPOCH,
            request = PlanRequest(),
            sessions = listOf(
                PlannedSession(
                    "str-only", java.time.LocalDate.now(), "str",
                    "Strength", 40, null, "strength"
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )
        org.junit.Assert.assertFalse(
            hasRunningPlanForActivityLink(listOf(strengthOnlyPlan))
        )

        val mixedPlan = PlanSnapshot(
            id = "synthetic-mixed-plan",
            owner = "synthetic-owner",
            engineVersion = ENGINE1_VERSION,
            policyId = ENGINE1_POLICY,
            createdAt = Instant.EPOCH,
            request = PlanRequest(),
            sessions = listOf(
                PlannedSession(
                    "run-1", java.time.LocalDate.now(), "run",
                    "Easy run", 40, 5.0, "easy"
                ),
                PlannedSession(
                    "str-1", java.time.LocalDate.now(), "str",
                    "Strength", 40, null, "strength"
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    ActivityPlanLink(
                        TrainingState(initializing = false, plans = listOf(mixedPlan)),
                        activity, PlanActions()
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-link-run-action").assertExists()
        compose.onNodeWithText("Link run").assertExists()
    }

    @Test fun overviewSurfacesOnlyRecordedEvidenceDuringTimerPauses() {
        val activity = ActivityRecord(
            "synthetic-paused-run", "Paused Run", "run", "manual",
            Instant.parse("2026-11-06T10:38:00Z"), 4000.0, 1500.0,
            1440.0, 30.0, 145.0, 20.0
        )
        val recorded = ActivityDetail(
            activity.id,
            samples = listOf(
                ActivitySample(0, 0.0, 390.0, 135.0, 10.0, 14.60, 121.00, 170.0),
                ActivitySample(1, 20.0, 0.0, 110.0, 10.5, 14.601, 121.001, 90.0),
                ActivitySample(2, 25.0, 0.0, 108.0, 10.5, 14.602, 121.002, 88.0),
                ActivitySample(3, 40.0, 385.0, 140.0, 11.0, 14.603, 121.003, 172.0)
            ),
            laps = emptyList(),
            fetchedAt = 0L,
            pauseIntervals = listOf(ActivityPauseInterval(15.0, 30.0))
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-test-account",
            activities = listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = activity, recorded = recorded,
                        detailSource = activity, matchedSourceIds = setOf(activity.id),
                        history = listOf(activity),
                        state = state, actions = NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online = false, selectedTab = 0, onTab = {},
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-handle").performClick()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("activity-pause-summary")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-pause-summary").assertExists()
        compose.onNodeWithText("Paused time").assertExists()
        compose.onNodeWithText("1 timer pause").assertExists()
        compose.onNodeWithText(
            "2 source samples were still recorded during pauses · 2 GPS · 2 HR · 2 cadence."
        ).assertExists()
        compose.onNodeWithText(
            "Only source-recorded values are shown. Missing paused data is not reconstructed."
        ).assertExists()
    }

    @Test fun mapKeepsRecordedPauseFixesWithoutSpecialDotLegend() {
        val detail=ActivityDetail(
            "synthetic-map-legend",
            samples=listOf(
                ActivitySample(0,0.0,390.0,135.0,10.0,14.600,121.000,170.0),
                ActivitySample(1,10.0,388.0,138.0,10.2,14.601,121.001,171.0),
                ActivitySample(2,20.0,null,110.0,10.3,14.602,121.002,90.0),
                ActivitySample(3,30.0,386.0,140.0,10.4,14.603,121.003,172.0),
                ActivitySample(4,40.0,384.0,142.0,10.5,14.604,121.004,173.0)
            ),
            laps=emptyList(),
            fetchedAt=0L,
            pauseIntervals=listOf(ActivityPauseInterval(15.0,25.0))
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideParityActivityMapHero(
                        detail=detail,
                        onBack={},
                        online=false
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-legend").assertExists()
        compose.onNodeWithTag("activity-map-gps-continuity").assertExists()
        compose.onNodeWithText("Continuous GPS").assertExists()
        compose.onNodeWithText("Recorded route").assertDoesNotExist()
        compose.onNodeWithText("Timer pause").assertDoesNotExist()
        compose.onNodeWithText("GPS recorded while paused").assertDoesNotExist()
    }

    @Test fun fullScreenMapShowsOnlyRecordedAnalysisModes() {
        val detail=ActivityDetail(
            "synthetic-fullscreen-analysis",
            samples=listOf(
                ActivitySample(0,0.0,390.0,130.0,10.0,14.600,121.000,170.0),
                ActivitySample(1,30.0,380.0,140.0,14.0,14.601,121.001,174.0),
                ActivitySample(2,60.0,370.0,150.0,18.0,14.602,121.002,178.0)
            ),
            laps=emptyList(),
            fetchedAt=0L
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideParityActivityMapHero(
                        detail=detail,
                        onBack={},
                        online=false,
                        fullScreen=true,
                        mapMode=TracksideActivityMapMode.PACE
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-fullscreen").assertExists()
        compose.onNodeWithTag("activity-map-mode-selector").assertExists()
        compose.onNodeWithTag("activity-map-mode-route").assertExists()
        compose.onNodeWithTag("activity-map-mode-pace").assertExists()
        compose.onNodeWithTag("activity-map-mode-elevation").assertExists()
        compose.onNodeWithTag("activity-map-mode-heart_rate").assertExists()
        compose.onNodeWithTag("activity-map-analysis-legend").assertExists()
        compose.onNodeWithText("Recorded pace").assertExists()
        compose.onNodeWithText("Faster").assertExists()
        compose.onNodeWithText("Slower").assertExists()
    }

    @Test fun activityMapUsesDedicatedStrideLabOpenFreeMapStyle() {
        org.junit.Assert.assertEquals(
            "asset://stridelab_activity_dark.json",
            TracksideMapTiles.STYLE_URL
        )
        assertTrue(TracksideMapTiles.ATTRIBUTION.contains("OpenStreetMap"))
        assertTrue(TracksideMapTiles.ATTRIBUTION.contains("OpenFreeMap"))

        val styleText = compose.activity.assets.open("stridelab_activity_dark.json")
            .bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(styleText)
        org.junit.Assert.assertEquals("StrideLab Activity Dark", root.getString("name"))
        org.junit.Assert.assertEquals(
            "https://tiles.openfreemap.org/planet",
            root.getJSONObject("sources").getJSONObject("openmaptiles").getString("url")
        )

        val layers = root.getJSONArray("layers")
        fun layer(id: String): org.json.JSONObject {
            for (index in 0 until layers.length()) {
                val item = layers.getJSONObject(index)
                if (item.getString("id") == id) return item
            }
            throw AssertionError("Missing style layer: " + id)
        }

        org.junit.Assert.assertEquals(
            "#2D343A", layer("background").getJSONObject("paint")
                .getString("background-color")
        )
        org.junit.Assert.assertEquals(
            "#48694E", layer("landuse_park").getJSONObject("paint")
                .getString("fill-color")
        )
        org.junit.Assert.assertEquals(
            "#24442F", layer("landcover_wood").getJSONObject("paint")
                .getString("fill-color")
        )
        org.junit.Assert.assertEquals(
            "#3F6048", layer("landcover_grass").getJSONObject("paint")
                .getString("fill-color")
        )
        org.junit.Assert.assertEquals(
            "#2D485E", layer("water").getJSONObject("paint")
                .getString("fill-color")
        )
        assertTrue(layer("highway_path").getInt("minzoom") >= 12)
        assertTrue(layer("highway_name_other").getInt("minzoom") >= 12)
        assertTrue(layer("landuse_pitch").getInt("minzoom") >= 11)
        assertTrue(layer("building").getInt("minzoom") >= 13)

        // Runner-focused overlays remain selective rather than restoring the
        // generic commercial POI clutter from the provider style.
        assertTrue(layer("stridelab-trail-unpaved").getInt("minzoom") >= 12)
        assertTrue(layer("stridelab-footway").getInt("minzoom") >= 13)
        assertTrue(layer("stridelab-cycleway").getInt("minzoom") >= 13)
        assertTrue(layer("stridelab-steps").getInt("minzoom") >= 14)
        assertTrue(layer("stridelab-runner-poi").getInt("minzoom") >= 15)
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
                    TracksideActivityDetailSurface(
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
        compose.onNodeWithTag("activity-sticky-pill").assertExists().assertIsNotDisplayed()
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
