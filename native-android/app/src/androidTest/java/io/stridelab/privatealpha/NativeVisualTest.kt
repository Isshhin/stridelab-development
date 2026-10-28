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

    @Test fun activityCardIsActionableAndMissingMetricsRemainHonest() {
        var opened = false
        show {
            StrideTheme { Surface { Column(Modifier.padding(22.dp)) {
                Text("UI SIMULATION · SYNTHETIC FIXTURE")
                ActivityRow(activity) { opened = true }
                MetricPair("Average heart rate" to "Not recorded", "Elevation gain" to "124 m")
            } } }
        }
        compose.onNodeWithText("8.75 km · 53:30").assertExists()
        compose.onNodeWithText("Not recorded").assertExists()
        capture("simulation-recorded-activity")
        compose.onNodeWithText("Morning trail run").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
    @Test fun narrowLargeFontMetricsReflowAndRemainReachable() {
        show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                StrideTheme { Surface { Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("UI SIMULATION · 200% TEXT")
                    ActivityRow(activity.copy(title = "A long activity title that must wrap without hiding training details")) {}
                    MetricPair("Average heart rate" to "Not recorded", "Maximum heart rate" to "Not recorded")
                } } }
            }
        }
        compose.onNodeWithText("Maximum heart rate").performScrollTo().assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("Not recorded").fetchSemanticsNodes().size)
        capture("simulation-narrow-200pct")
    }
    @Test fun populatedScreensUseNativeNavigationAndRealActions() {
        var checks = 0
        var syncs = 0
        val fixture = TrainingState(initializing = false, account = "synthetic-visual-account",
            activities = listOf(activity, activity.copy(id = "synthetic-second", title = "Evening walk", type = "Walking", distanceM = 2500.0, durationS = 1800.0)),
            intervals = IntervalConnection(true,"connected",null,1792972800000), lastSync = 1792972800000)
        val actions = NativeActions({}, { checks++ }, { syncs++ }, {}, { _, _ -> })
        show { StrideTheme { NativeStrideContent(fixture, true, actions) } }
        compose.onNodeWithTag("activity-synthetic-visual").assertExists()
        capture("simulation-dashboard")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("RECENT TRAINING TIME"))
        capture("simulation-recorded-duration-chart")
        compose.onNodeWithTag("nav-activities").performClick()
        capture("simulation-history")
        compose.onNodeWithText("Morning trail run").performClick()
        compose.onNodeWithText("Average pace").assertExists()
        capture("simulation-detail")
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Check connection"))
        capture("simulation-source-settings")
        compose.onNodeWithText("Check connection").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Synchronize Intervals.icu"))
        compose.onNodeWithText("Synchronize Intervals.icu").performClick()
        compose.runOnIdle { assertEquals(1,checks); assertEquals(1,syncs) }
    }

    @Test fun loadingOfflineAndFailureStayVisibleWithCachedHistory() {
        val fixture = androidx.compose.runtime.mutableStateOf(TrainingState(initializing = false,
            account = "synthetic-visual-account", activities = listOf(activity), busy = true, task = "Refreshing saved history"))
        val actions = NativeActions({}, {}, {}, {}, { _, _ -> })
        show { StrideTheme { NativeStrideContent(fixture.value, false, actions) } }
        compose.onNodeWithTag("loading").assertExists()
        compose.onNodeWithTag("offline-status").assertExists()
        capture("simulation-loading-offline")
        compose.runOnIdle { fixture.value = fixture.value.copy(busy = false, message = "Unable to refresh. Saved history is still available.") }
        compose.onNodeWithTag("loading").assertDoesNotExist()
        compose.onNodeWithText("Unable to refresh. Saved history is still available.").assertExists()
        compose.onNodeWithText("Morning trail run").assertExists()
        capture("simulation-failure-with-cache")
    }

    @Test fun recordedStreamsAndGpsHaveAccessibleUnitsAndOfflineProvenance() {
        val points=(0..20).map { i -> ActivitySample(i,i*10.0,340.0+i*2,140.0+i,10.0+i,14.5+i*.0001,121.0+i*.00015) }
        val detail=ActivityDetail("synthetic-visual",points,listOf(ActivityLap(0,1000.0,350.0,350.0,148.0)),1792972800000)
        show { StrideTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("UI SIMULATION · SYNTHETIC STREAMS")
            RecordedActivityDetail(detail,true)
        } } } }
        compose.onNodeWithText("RECORDED STREAMS").assertIsDisplayed()
        compose.onNodeWithTag("stream-Pace").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithText("Selected source sample 10 · elapsed 1:40").assertExists()
        compose.onNodeWithText("Selected: 6:00 /km at 1:40").assertExists()
        compose.onNodeWithText("Heart rate",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        capture("simulation-recorded-streams")
        compose.onNodeWithText("Route").performScrollTo().performClick()
        compose.onNodeWithText("Recorded GPS trace").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Recorded GPS route trace",substring=true).performScrollTo().assertIsDisplayed()
        capture("simulation-recorded-gps-trace")
        compose.onNodeWithText("Splits").performScrollTo().performClick()
        compose.onNodeWithText("Source laps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lap 1 · 1.00 km · 5:50").performScrollTo().assertIsDisplayed()
        capture("simulation-recorded-laps")
    }
    @Test fun weeklyDashboardAndSourceDatedWellnessRemainReadableAtLargeText() {
        val now=Instant.parse("2026-10-26T02:00:00Z")
        var refreshed=false
        show {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                StrideTheme { Surface { Column(Modifier.width(300.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("UI SIMULATION · SYNTHETIC DASHBOARD")
                    WeeklyDashboard(listOf(activity),now,java.time.ZoneId.of("Asia/Manila"))
                    WellnessOverview(listOf(WellnessRecord("synthetic-wellness","sleep_duration",7.5,"hours",now.minusSeconds(86400),"intervals")),{refreshed=true},true)
                } } }
            }
        }
        compose.onNodeWithText("LAST 7 DAYS").assertExists()
        compose.onNodeWithText("Running distance").performScrollTo().assertIsDisplayed()
        capture("simulation-weekly-200pct")
        compose.onNodeWithText("Sleep duration · 7.5 hours").performScrollTo().assertIsDisplayed()
        capture("simulation-wellness-200pct")
        compose.onNodeWithText("Refresh wellness").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(refreshed) }
    }
    @Test fun widerLayoutKeepsContentBounded() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                StrideTheme { NativeStrideContent(TrainingState(initializing = false),true,NativeActions({}, {}, {}, {}, { _, _ -> })) }
            }
        }
        compose.onNodeWithTag("screen-title").assertIsDisplayed()
        val bounds = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        assertTrue("Reading column must be bounded", bounds.width <= 761f)
        capture("simulation-wide-layout")
    }    @Test fun datedNativeWorkoutAppearsInDashboardAsUserConfirmationWithoutSourceActivityFabrication() {
        val now=Instant.parse("2026-10-26T02:00:00Z");val date=java.time.LocalDate.parse("2026-10-25")
        val session=PlannedSession("native-summary",date,"str","Confirmed strength",45,null,"practice",status="completed",log=WorkoutLog(now,minutes=20.0,performedOn=date))
        val plan=PlanSnapshot("native-summary-plan","synthetic-dashboard",ENGINE1_VERSION,ENGINE1_POLICY,now,PlanRequest(start=date),listOf(session),emptyList())
        show { StrideTheme { Surface { Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) { WeeklyDashboard(emptyList(),now,java.time.ZoneId.of("UTC"),listOf(plan)) } } } }
        compose.onNodeWithText("1 active day · 1 activity").assertExists();compose.onNodeWithText("20:00").assertExists()
        compose.onNodeWithText("1 dated, user-confirmed workout logs included.",substring=true).performScrollTo().assertExists()
        capture("simulation-native-confirmed-volume")
    }
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) { compose.runOnUiThread { compose.activity.setContent(content = content) } }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350) // Let the actual display commit its rendered frame.
        val folder = File(compose.activity.getExternalFilesDir(null), "native-ui-evidence"); folder.mkdirs()
        File(folder,"$name.png").outputStream().use {
            assertTrue(checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it))
        }
    }
}
