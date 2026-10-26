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
        compose.activity.setContent {
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
        compose.activity.setContent {
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
    private fun capture(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "native-ui-evidence"); folder.mkdirs()
        File(folder,"$name.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it))
        }
    }
}

