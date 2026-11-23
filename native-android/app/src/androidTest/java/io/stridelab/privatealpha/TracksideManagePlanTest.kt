package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Dedicated Manage Plan pilot with immutable, synthetic local plan data. */
class TracksideManagePlanTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private fun plan(): PlanSnapshot {
        val start = LocalDate.now().plusDays(1)
        return PlanSnapshot(
            id = "manage-synthetic", owner = "synthetic-owner", engineVersion = ENGINE1_VERSION,
            policyId = ENGINE1_POLICY, createdAt = Instant.EPOCH,
            request = PlanRequest(start = start, weeks = 2),
            sessions = listOf(
                PlannedSession("synthetic-run", start, "run", "Easy run", 45, 7.0, "easy"),
                PlannedSession("synthetic-strength", start, "str", "Upper body", 35, null, "strength")
            ),
            explanations = emptyList(), status = "approved"
        )
    }

    @Test fun editFutureWorkoutNavigatesToExistingSessionWithoutMutation() {
        val original = plan()
        var opened: String? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideManagePlanScreen(
                        plan = original, selected = original.request.start, busy = false,
                        actions = PlanActions(), onBack = {},
                        onBuildDraft = {}, onWorkout = { opened = it }
                    )
                }
            }
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-edit-workout"))
        compose.onNodeWithTag("manage-edit-workout").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-workout-synthetic-run"))
        compose.onNodeWithTag("manage-workout-synthetic-run").performClick()
        compose.runOnIdle {
            assertEquals("synthetic-run", opened)
            assertEquals(2, original.sessions.size)
            assertEquals("planned", original.sessions.first().status)
        }
    }

    @Test fun pauseRequiresConfirmationAndDoesNotFireOnCancel() {
        val original = plan()
        var pauses = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideManagePlanScreen(
                        plan = original, selected = original.request.start, busy = false,
                        actions = PlanActions(pause = { _, _ -> pauses++ }), onBack = {},
                        onBuildDraft = {}, onWorkout = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-pause"))
        compose.onNodeWithTag("manage-pause").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, pauses) }
        compose.onNodeWithTag("manage-pause").performClick()
        compose.onNodeWithText("Pause plan").performClick()
        compose.runOnIdle {
            assertEquals(1, pauses)
            assertEquals("approved", original.status)
        }
    }
}
