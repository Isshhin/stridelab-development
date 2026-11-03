package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Synthetic/offline QA: never signs in or writes real athlete records. */
class TracksideBatch345UiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun curatedLibraryUsesVerifiedOnDeviceMetadata() {
        val records = readCuratedLibrary(compose.activity)
        assertEquals(66, records.size)
        assertEquals(45, records.count { !it.guide })
        assertEquals(21, records.count { it.guide })
        assertTrue(records.any { it.discipline == "mob" })
        assertTrue(records.any { it.discipline == "cond" })
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme { LibraryScreen(TrainingState(initializing = false)) }
            }
        }
        compose.waitUntil(15_000L) {
            runCatching { compose.onNodeWithTag("library-ready").assertExists(); true }.getOrDefault(false)
        }
        compose.onNodeWithText("45 exercises · 21 guides", substring = true).assertExists()
    }

    @Test fun savedHistoryDetailAndSettingsKeepExistingNavigation() {
        val activity = ActivityRecord(
            "synthetic-activity", "Offline park run", "Running", "manual",
            Instant.parse("2026-11-01T10:00:00Z"), 5000.0, 1850.0,
            1800.0, 360.0, 145.0, 22.0
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-athlete",
            activities = listOf(activity)
        )
        var automaticLoads = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(state, true, NativeActions({}, {}, {}, {}, { _, _ -> },
                        loadDetail = { automaticLoads += 1 }))
                }
            }
        }
        compose.onNodeWithTag("nav-activities").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("activity-synthetic-activity"))
        compose.onNodeWithTag("activity-synthetic-activity").performClick()
        compose.runOnIdle { assertEquals("Recorded source detail should be requested once", 1, automaticLoads) }
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithText("5.00 km").assertExists()
        // Detail is intentionally full screen in the approved Figma gallery.
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("nav-settings").assertIsDisplayed().performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("DEVICE & DATA"))
        compose.onNodeWithText("DEVICE & DATA").assertExists()
    }
}
