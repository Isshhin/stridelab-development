package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Exercises the Today's saved-data presentation without external login or network access. */
class TracksideTodayTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun companionCopyAndActionCoverAllTrainingDisciplines() {
        var opened = 0
        val today = LocalDate.of(2026, 11, 23)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TodayDailyTrainingCard(today, today, TodayDailyTrainingState.NoPlanCompanion,
                        emptyMap(), onOpenWorkout = { _, _ -> }, onOpenActivityDetail = {},
                        onOpenTraining = { opened++ })
                }
            }
        }
        compose.onNodeWithText("Track multisport sessions freely or generate a weekly training plan tailored to your goals.").assertExists()
        compose.onNodeWithText("Build a Training Plan").performClick()
        assertEquals(1, opened)
    }

    @Test fun coldStartAndGuestModeUseTheSameTodayShell() {
        val state = androidx.compose.runtime.mutableStateOf(TrainingState())
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state.value, emptyList(), false,
                        onOpenTraining = {}, onOpenActivity = {}, onOpenWorkout = { _, _ -> },
                        onOpenActivityDetail = {}, onOpenMore = {}, onSignIn = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("today-discipline-pages").assertExists()
        compose.onNodeWithTag("today-week-swipe").assertExists()
        compose.onNodeWithTag("today-date-header").assertExists()
        compose.onNodeWithText("Loading training day...").assertExists()
        compose.onNodeWithText("Train with StrideLab").assertDoesNotExist()
        compose.onNodeWithText("Your schedule awaits").assertDoesNotExist()
        val calendar = compose.onNodeWithTag("today-week-swipe").getUnclippedBoundsInRoot()
        compose.runOnIdle { state.value = TrainingState(initializing = false) }
        compose.onNodeWithText("Companion training mode").assertExists()
        assertEquals(calendar, compose.onNodeWithTag("today-week-swipe").getUnclippedBoundsInRoot())
        compose.onNodeWithText("Train with StrideLab").assertDoesNotExist()
        compose.onNodeWithText("Your schedule awaits").assertDoesNotExist()
    }

    @Test fun completedActivityRowOpensDetailWithoutRedundantLabels() {
        val monday = LocalDate.of(2026, 11, 23)
        val activity = ActivityRecord("recorded-run", "Morning run", "running", "garmin",
            java.time.Instant.parse("2026-11-23T07:00:00Z"), 16_690.0, 6695.0, 6695.0,
            402.0, 144.0, null)
        var opened: String? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TodayDailyTrainingCard(monday, monday.plusDays(1),
                        TodayDailyTrainingState.Sessions(
                            listOf(TodayDailySessionItem.Recorded(activity, TodayDiscipline.RUNNING)), false),
                        emptyMap(), onOpenWorkout = { _, _ -> }, onOpenActivityDetail = { opened = it },
                        onOpenTraining = {})
                }
            }
        }
        compose.onNodeWithText("Monday's Training").assertDoesNotExist()
        compose.onNodeWithText("View Analysis").assertDoesNotExist()
        compose.onNodeWithText("Running • 16.69 km", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("today-workout-action").performClick()
        compose.runOnIdle { assertEquals(activity.id, opened) }
    }

    @Test fun signedInEmptyPastDayUsesCompactStatusInsteadOfTallHero() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = TrainingState(initializing = false, account = "synthetic-empty-day"),
                        visibleActivities = emptyList(),
                        online = false,
                        onOpenTraining = {},
                        onOpenActivity = {},
                        onOpenWorkout = { _, _ -> },
                        onOpenActivityDetail = {},
                        onOpenMore = {},
                        onSignIn = {}
                    )
                }
            }
        }
        // Today's no-approved-plan state retains its existing onboarding CTA.
        compose.onNodeWithTag("today-compact-empty-day").assertDoesNotExist()
        // Browsing a previous week with no saved records or scheduled session
        // must not consume the 232dp full-card footprint.
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("today-compact-empty-day").assertExists()
        compose.onNodeWithTag("today-recorded-hero").assertDoesNotExist()
        val emptyDayHeightDp = compose.onNodeWithTag("today-compact-empty-day")
            .getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        org.junit.Assert.assertTrue(
            "Empty-day status is still oversized: ${emptyDayHeightDp}dp",
            emptyDayHeightDp < 110f
        )
        // Return to the current date; no historical empty state should persist.
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("today-compact-empty-day").assertDoesNotExist()
    }

    @Test fun signedOutTabsKeepTodaySelectionWithoutExposingInactiveScreens() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = TrainingState(initializing = false, account = null),
                        online = true,
                        actions = NativeActions({}, { _, _ -> })
                    )
                }
            }
        }
        // Today is composed once; switching to Training and More must not
        // dispose its selected date or leave its controls clickable.
        compose.onNodeWithTag("today-day-0").performClick()
        compose.onNodeWithTag("today-day-0").assertIsSelected()

        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("today-hero").assertDoesNotExist()

        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Not signed in · Saved on this device").assertExists()
        compose.onNodeWithTag("today-hero").assertDoesNotExist()
        compose.onNodeWithTag("training-unified-timeline").assertDoesNotExist()

        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithTag("today-day-0").assertIsSelected()
        compose.onNodeWithText("Not signed in · Saved on this device").assertDoesNotExist()
    }

    @Test fun signedInTabsRetainTodayDateAndDoNotExposeInactiveControls() {
        val state = TrainingState(initializing = false, account = "synthetic-retained-tab")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = state,
                        online = false,
                        actions = NativeActions({}, { _, _ -> })
                    )
                }
            }
        }
        // Signed-in Today was previously reconstructed on each NavHost visit.
        // A weekday selection now belongs to its retained, account-scoped root.
        compose.onNodeWithTag("today-day-0").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-training").performClick().assertIsSelected()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("today-day-0").assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        compose.onNodeWithTag("screen-title").assertExists()
        compose.onNodeWithTag("training-unified-timeline").assertDoesNotExist()
        compose.onNodeWithTag("nav-home").performClick().assertIsSelected()
        compose.onNodeWithTag("today-day-0").assertIsSelected()
        compose.onNodeWithTag("screen-title").assertDoesNotExist()
    }

    @Test fun rapidTabRequestsEndAtLastDestination() {
        listOf(null, "synthetic-tab-account").forEach { account ->
            compose.runOnUiThread {
                compose.activity.setContent {
                    StrideTheme {
                        NativeStrideContent(
                            state = TrainingState(initializing = false, account = account),
                            online = false,
                            actions = NativeActions({}, { _, _ -> })
                        )
                    }
                }
            }
            compose.onNodeWithTag("nav-training").performClick()
            compose.onNodeWithTag("nav-home").performClick()
            compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
            compose.onNodeWithTag("screen-title").assertExists()
            compose.onNodeWithTag("today-day-0").assertDoesNotExist()
            compose.onNodeWithTag("nav-home").performClick().assertIsSelected()
            compose.onNodeWithTag("today-day-0").assertExists()
        }
    }

}
