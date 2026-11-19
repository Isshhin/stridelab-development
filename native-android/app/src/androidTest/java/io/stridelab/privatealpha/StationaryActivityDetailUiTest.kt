package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Synthetic Garmin summaries only; no account login, network call,
 * user recording or physical-device installation.
 */
class StationaryActivityDetailUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private fun activity(type: String) = ActivityRecord(
        "stationary-qa", "Strength", type, "garmin",
        Instant.parse("2026-11-19T08:00:00Z"),
        distanceM = 0.0, durationS = 317.0, movingTimeS = 310.0,
        recordedPace = null, averageHr = 105.0, elevationM = null
    )

    private fun mount(
        activity: ActivityRecord,
        detail: ActivityDetail? = null,
        onBack: () -> Unit = {}
    ) {
        val state = TrainingState(
            initializing = false, account = "synthetic-stationary-qa",
            activities = listOf(activity)
        )
        compose.activity.setContent {
            StrideTheme {
                TracksideActivityDetailSurface(
                    activity = activity, recorded = detail,
                    detailSource = activity, matchedSourceIds = setOf(activity.id),
                    history = listOf(activity), state = state,
                    actions = NativeActions({}, { _, _ -> }, renameActivity = { _, _ -> true }),
                    online = false, selectedTab = 0, onTab = {},
                    matte = false, onToggleMatte = {}, onBack = onBack
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun strengthZeroMetersUsesDurationAndHrWithNoGpsHero() {
        mount(activity("strength_training"))
        compose.onNodeWithTag("activity-stationary-detail").assertExists()
        compose.onNodeWithTag("activity-map-hero").assertDoesNotExist()
        compose.onNodeWithTag("activity-primary-metrics").assertExists()
        compose.onNodeWithTag("activity-metric-duration").assertExists()
        compose.onNodeWithTag("activity-metric-avg-hr").assertExists()
        compose.onNodeWithTag("activity-metric-distance").assertDoesNotExist()
        compose.onNodeWithTag("activity-metric-avg-pace").assertDoesNotExist()
        compose.onNodeWithText("0.00 km").assertDoesNotExist()
        compose.onNodeWithTag("activity-edit").assertExists().assertHasClickAction()
        compose.onNodeWithTag("activity-share").assertExists().assertHasClickAction()
    }

    @Test fun strengthWithUnexpectedGpsSamplesStillAvoidsRoutePresentation() {
        val run = activity("strength")
        val strayGps = ActivityDetail(
            activityId = run.id,
            samples = listOf(
                ActivitySample(0, 0.0, null, null, null, 14.60, 121.00),
                ActivitySample(1, 15.0, null, null, null, 14.601, 121.001)
            ),
            laps = emptyList(), fetchedAt = 0L
        )
        mount(run, strayGps)
        compose.onNodeWithTag("activity-stationary-detail").assertExists()
        compose.onNodeWithTag("activity-map-hero").assertDoesNotExist()
        compose.onNodeWithTag("activity-map-attribution").assertDoesNotExist()
        compose.onNodeWithTag("activity-metric-duration").assertExists()
    }

    @Test fun ordinaryRunningRecordingRetainsMapFirstDetail() {
        val run = activity("run").copy(
            title = "Running", distanceM = 2110.0,
            recordedPace = 485.0, averageHr = 144.0
        )
        mount(run)
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-stationary-detail").assertDoesNotExist()
        compose.onNodeWithTag("activity-metric-distance").assertExists()
        compose.onNodeWithTag("activity-metric-avg-pace").assertExists()
    }

    @Test fun stationaryBackAndRenameReuseSourceActions() {
        var exits = 0
        mount(activity("strength"), onBack = { exits++ })
        compose.onNodeWithTag("activity-edit").performClick()
        compose.onNodeWithTag("activity-rename-field").assertExists()
        compose.onNodeWithTag("activity-rename-field").performTextReplacement("Gym session")
        compose.onNodeWithTag("activity-rename-save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("activity-rename-field").assertDoesNotExist()
        compose.onNodeWithTag("activity-back").performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }
}
