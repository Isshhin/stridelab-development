package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Exercises real NavHost round-trip while all athlete and plan content is synthetic. */
class TracksideManageNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun manageBackPreservesSelectedTrainingDate() {
        val start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val selected = start.plusDays(2)
        val session = PlannedSession(
            id = "manage-navigation-run", date = selected, discipline = "run",
            title = "Synthetic easy run", minutes = 40, distanceKm = 6.0, kind = "easy"
        )
        val plan = PlanSnapshot(
            id = "manage-navigation-plan", owner = "synthetic-account",
            engineVersion = ENGINE1_VERSION, policyId = ENGINE1_POLICY,
            createdAt = Instant.EPOCH,
            request = PlanRequest(start = start, weeks = 2),
            sessions = listOf(session), explanations = emptyList(), status = "approved"
        )
        val state = TrainingState(initializing = false, account = "synthetic-account",
            plans = listOf(plan))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(state, true, NativeActions({}, {}, {}, {}, { _, _ -> }))
                }
            }
        }
        compose.onNodeWithTag("nav-training").performClick()
        // Calendar controls are reached through the existing timeline's Plan tools.
        compose.onNodeWithTag("training-plan-tools").performClick()
        compose.waitUntil(timeoutMillis = 10_000L) {
            runCatching { compose.onNodeWithTag("screen-title").assertTextEquals("Training"); true }.getOrDefault(false)
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-calendar"))
        compose.onNodeWithTag("calendar-day-$selected").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-manage-plan"))
        compose.onNodeWithTag("training-manage-plan").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Manage plan")
        compose.onNodeWithTag("nav-training").assertIsSelected()
        compose.onNodeWithTag("manage-back").performClick()
        // NavHost transitions are asynchronous on an actual emulator. Wait for the
        // Training destination instead of reading Manage's exiting semantics tree.
        compose.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                compose.onNodeWithTag("screen-title").assertTextEquals("Training")
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("screen-title").assertTextEquals("Training")
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-calendar"))
        compose.onNodeWithTag("training-calendar").assertExists()
        val dateLabel = selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
        compose.onNodeWithTag("screen").performScrollToNode(hasText(dateLabel))
        compose.onNodeWithText(dateLabel).assertExists()
    }
}
