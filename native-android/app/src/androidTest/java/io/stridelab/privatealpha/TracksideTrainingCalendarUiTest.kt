package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** No backend or real account needed: calendar is verified against immutable prescriptions. */
class TracksideTrainingCalendarUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun selectedDayAndWeekButtonsUseExistingDatesWithoutChangingPlan() {
        val start = LocalDate.parse("2026-11-02")
        val sessions = listOf(
            PlannedSession("run-one", start.plusDays(2), "run", "Easy run", 50, 8.0, "easy"),
            PlannedSession("cal-one", start.plusDays(2), "cal", "Push", 30, null, "calisthenics")
        )
        val plan = PlanSnapshot(
            "synthetic-week", "synthetic-account", ENGINE1_VERSION, ENGINE1_POLICY,
            Instant.EPOCH, PlanRequest(start = start, weeks = 2), sessions, emptyList()
        )
        val selected = mutableStateOf(start)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideTrainingWeekChart(plan, selected.value) { selected.value = it }
                }
            }
        }
        compose.onNodeWithTag("training-week-chart").assertExists()
        compose.onNodeWithTag("calendar-day-2026-11-04").performClick()
        compose.runOnIdle {
            assertEquals(start.plusDays(2), selected.value)
            assertEquals(2, plan.sessions.size)
            assertEquals("planned", plan.sessions[0].status)
        }
        compose.onNodeWithTag("training-next-week").performClick()
        compose.runOnIdle { assertEquals(start.plusWeeks(1), selected.value) }
        compose.onNodeWithTag("training-prev-week").performClick()
        compose.runOnIdle { assertEquals(start, selected.value) }
    }
}
