package io.stridelab.privatealpha

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant

/** Immutable synthetic presentation fixtures only; no sessions, cache writes or network requests. */
class NativeVisualTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val activity = ActivityRecord("synthetic-visual", "Morning trail run", "Trail running", "manual",
        Instant.parse("2026-10-26T00:00:00Z"), 8750.0, 3210.0, 3100.0, 354.0, null, 124.0)

    @Test fun activityCardShowsRecordedMetricsAndOpens() {
        var opened = false
        show {
            StrideTheme { Surface { Column(Modifier.padding(22.dp)) {
                Text("UI SIMULATION · SYNTHETIC FIXTURE")
                ActivityRow(activity) { opened = true }
            } } }
        }
        compose.onNodeWithText("8.75 km · 53:30").assertExists()
        capture("simulation-recorded-activity")
        compose.onNodeWithText("Morning trail run").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun narrowLargeFontActivityTitleRemainsReachable() {
        show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                StrideTheme { Surface { Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("UI SIMULATION · 200% TEXT")
                    ActivityRow(activity.copy(title = "A long activity title that must wrap without hiding training details")) {}
                } } }
            }
        }
        compose.onNodeWithText("A long activity title that must wrap without hiding training details").performScrollTo().assertIsDisplayed()
        capture("simulation-narrow-200pct")
    }
    @Test fun populatedScreensUseNativeNavigationAndRealActions() {
        val fixture = TrainingState(initializing = false, account = "synthetic-visual-account",
            activities = listOf(activity, activity.copy(id = "synthetic-second", title = "Evening walk", type = "Walking", distanceM = 2500.0, durationS = 1800.0)),
            lastSync = 1792972800000)
        val actions = NativeActions({}, { _, _ -> })
        show { StrideTheme { NativeStrideContent(fixture, true, actions) } }
        capture("simulation-dashboard")
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("timeline-recorded-synthetic-visual").assertExists()
        compose.onNodeWithTag("timeline-recorded-synthetic-second").assertExists()
        capture("simulation-training-timeline")
        compose.onNodeWithTag("timeline-recorded-synthetic-visual").performClick()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithText("DISTANCE").assertExists()
        capture("simulation-detail")
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNode(hasTestTag("screen") and androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,"More"))
            .performScrollToNode(hasTestTag("more-row-garmin"))
        compose.onNodeWithTag("more-row-garmin").performClick()
        compose.onNodeWithTag("garmin-modal-sheet").assertExists()
        capture("simulation-source-settings")
        compose.onNodeWithText("Synchronize Intervals.icu").assertDoesNotExist()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
    }

    @Test fun loadingOfflineAndFailureStayVisibleWithCachedHistory() {
        val recentActivity=activity.copy(startedAt=Instant.now())
        val fixture = androidx.compose.runtime.mutableStateOf(TrainingState(initializing = false,
            account = "synthetic-visual-account", activities = listOf(recentActivity), busy = true, task = "Refreshing saved history"))
        val actions = NativeActions({}, { _, _ -> })
        show { StrideTheme { NativeStrideContent(fixture.value, false, actions) } }
        compose.onNodeWithTag("loading").assertExists()
        capture("simulation-loading-offline")
        compose.runOnIdle { fixture.value = fixture.value.copy(busy = false, message = "Unable to refresh. Saved history is still available.") }
        compose.onNodeWithTag("loading").assertDoesNotExist()
        compose.onNodeWithText("Unable to refresh. Saved history is still available.").assertExists()
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("timeline-recorded-synthetic-visual").assertExists()
        capture("simulation-failure-with-cache")
    }

    @Test fun widerLayoutKeepsContentBounded() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                StrideTheme { NativeStrideContent(TrainingState(initializing = false),true,NativeActions({}, { _, _ -> })) }
            }
        }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-plan-tools").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("training-unified-timeline").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Training content must stay within the display",bounds.left>=viewport.left && bounds.right<=viewport.right)
        capture("simulation-wide-layout")
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) { compose.runOnUiThread { compose.activity.setContent(content = content) } }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let the actual display commit its rendered frame.
        val folder = File(compose.activity.getExternalFilesDir(null) ?: compose.activity.cacheDir, "native-ui-evidence"); folder.mkdirs()
        File(folder,"$name.png").outputStream().use {
            assertTrue(checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it))
        }
    }
}
