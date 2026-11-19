package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
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
        val today = compose.onNodeWithTag("screen")
        today.performScrollToNode(hasTestTag("today-discipline-pages"))
        compose.onNodeWithTag("today-page-all").assertExists().assertIsSelected()
        compose.onNodeWithTag("today-page-running").assertExists()
        compose.onNodeWithTag("today-page-strength").assertDoesNotExist()
        val weeklyBottom = compose.onNodeWithTag("today-weekly-activity")
            .getUnclippedBoundsInRoot().bottom
        val tabsTop = compose.onNodeWithTag("today-discipline-pages")
            .getUnclippedBoundsInRoot().top
        assertTrue("Discipline tabs should follow the activity card", tabsTop >= weeklyBottom)
        compose.onNodeWithTag("today-page-running").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("today-discipline-running").assertExists()
        compose.onNodeWithText("Your week in running").assertExists()
        val time = compose.onNodeWithTag("today-running-metric-time")
        today.performScrollToNode(hasTestTag("today-running-metric-time"))
        time.performSemanticsAction(SemanticsActions.OnClick)
        time.assertIsSelected()
        val monday = compose.onNodeWithTag("today-running-day-0")
        today.performScrollToNode(hasTestTag("today-running-day-0"))
        monday.performSemanticsAction(SemanticsActions.OnClick)
        monday.assertIsSelected()
        compose.onNodeWithText("No runs", ignoreCase = false).assertExists()
        compose.onNodeWithTag("today-hero").assertDoesNotExist()
        today.performScrollToNode(hasTestTag("today-discipline-pages"))
        compose.onNodeWithTag("today-page-running").assertIsSelected()
        compose.onNodeWithTag("today-page-all").performClick()
        compose.waitForIdle()
        today.performScrollToNode(hasTestTag("today-discipline-pages"))
        compose.onNodeWithTag("today-page-all").assertIsSelected()
        compose.onNodeWithTag("today-discipline-running").assertDoesNotExist()
    }
}
