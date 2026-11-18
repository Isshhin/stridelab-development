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

    @Test fun signedOutTodayHeroKeepsSameDesignAcrossPastAndFutureWeeks() {
        var signInCount = 0
        var trainingOpenCount = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = TrainingState(initializing = false, account = null),
                        visibleActivities = emptyList(),
                        online = false,
                        onOpenTraining = { trainingOpenCount++ },
                        onOpenActivity = {},
                        onOpenWorkout = { _, _ -> },
                        onOpenActivityDetail = {},
                        onOpenMore = {},
                        onSignIn = { signInCount++ }
                    )
                }
            }
        }
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithText("Train with StrideLab").assertExists()
        val originalHeight =
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

        // A previous week used to switch to a dark TRAINING HISTORY card.
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithText("Train with StrideLab").assertExists()
        compose.onNodeWithText("TRAINING HISTORY").assertDoesNotExist()
        assertEquals(
            originalHeight,
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value },
            0.5f
        )

        // Switching forward again must preserve the exact same card.
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Train with StrideLab").assertExists()
        compose.onNodeWithText("TRAINING HISTORY").assertDoesNotExist()
        assertEquals(
            originalHeight,
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value },
            0.5f
        )
        compose.onNodeWithTag("today-workout-action").performClick()
        compose.runOnIdle { assertEquals(1, trainingOpenCount); assertEquals(0, signInCount) }
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
        compose.onNodeWithText("Not signed in").assertExists()
        compose.onNodeWithTag("today-hero").assertDoesNotExist()
        compose.onNodeWithTag("training-unified-timeline").assertDoesNotExist()

        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithTag("today-day-0").assertIsSelected()
        compose.onNodeWithText("Not signed in").assertDoesNotExist()
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
