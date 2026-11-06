package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Exercises the Today's saved-data presentation without external login or network access. */
class TracksideTodayTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun plannedRunShowsOnlyKnownTargetsAndOpensExistingWorkoutAction() {
        val session = PlannedSession(
            id = "synthetic-today", date = LocalDate.now(), discipline = "run",
            title = "Easy Run", minutes = 45, distanceKm = null, kind = "easy"
        )
        var opened = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme { TracksideTodayHero(session) { opened++ } }
            }
        }
        compose.onNodeWithTag("today-hero").assertExists()
        // Premium artwork is explicitly illustrative; the actual workout
        // still uses only its saved duration and reports absent distance.
        compose.onNodeWithTag("today-map-illustration", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("45", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Distance not set", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("today-hero").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun signedOutTodayHeroKeepsSameDesignAcrossPastAndFutureWeeks() {
        var signInCount = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = TrainingState(initializing = false, account = null),
                        visibleActivities = emptyList(),
                        online = false,
                        onOpenTraining = {},
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
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().height.value

        // A previous week used to switch to a dark TRAINING HISTORY card.
        compose.onNodeWithTag("today-week-swipe").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithText("Train with StrideLab").assertExists()
        compose.onNodeWithText("TRAINING HISTORY").assertDoesNotExist()
        assertEquals(
            originalHeight,
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().height.value,
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
            compose.onNodeWithTag("today-hero").getUnclippedBoundsInRoot().height.value,
            0.5f
        )
        compose.onNodeWithTag("today-workout-action").performClick()
        compose.runOnIdle { assertEquals(1, signInCount) }
    }

}
