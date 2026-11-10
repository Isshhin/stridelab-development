package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** UI-only future validation: no activity import, sync, plan creation or deployment. */
class TodayDisciplinePagesTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun qualifiedRunningPageUsesExistingPaletteAndAllStillExists() {
        val zone = ZoneId.systemDefault()
        val thisMonday = TodayDisciplineAnalytics.monday(LocalDate.now(zone))
        val days = listOf(1L, 3L, 5L).flatMap { day ->
            listOf(thisMonday.minusWeeks(2).plusDays(day),
                thisMonday.minusWeeks(1).plusDays(day))
        }
        val history = days.mapIndexed { index, date ->
            ActivityRecord(
                id = "today-page-run-$index", title = "Recorded run",
                type = "running", source = "manual",
                startedAt = date.atTime(10, 0).atZone(zone).toInstant(),
                distanceM = 4000.0, durationS = 1800.0,
                movingTimeS = null, recordedPace = null,
                averageHr = 145.0, elevationM = null
            )
        }
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = TrainingState(
                            initializing = false,
                            account = "synthetic-today-insights",
                            activities = history
                        ),
                        visibleActivities = history,
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
        compose.onNodeWithTag("today-page-all").assertExists().assertIsSelected()
        compose.onNodeWithTag("today-page-running").assertExists()
        compose.onNodeWithTag("today-page-strength").assertDoesNotExist()
        compose.onNodeWithTag("today-page-running").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("today-page-running").assertIsSelected()
        compose.onNodeWithTag("today-discipline-running").assertExists()
        compose.onNodeWithText("Training effort").assertExists()
        compose.onNodeWithTag("today-hero").assertDoesNotExist()
        compose.onNodeWithTag("today-page-all").performClick()
        compose.onNodeWithTag("today-page-all").assertIsSelected()
        compose.onNodeWithTag("today-discipline-running").assertDoesNotExist()
    }
}
