package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithTag("today-route-unavailable").assertExists()
        compose.onNodeWithText("45 min").assertExists()
        compose.onNodeWithText("Not set").assertExists()
        compose.onNodeWithTag("today-hero").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
