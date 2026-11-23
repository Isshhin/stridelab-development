package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Synthetic, offline-only UI acceptance scenarios for approved 25D/25E and
 * matte Activity 551:365. These tests are STAGED but not executed until the
 * user explicitly lifts the APK/Android build hold.
 */
class TracksideParityStatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private fun plan(): PlanSnapshot {
        val today = LocalDate.now()
        return PlanSnapshot(
            id = "parity-ui-plan", owner = "synthetic-ui",
            engineVersion = ENGINE1_VERSION, policyId = ENGINE1_POLICY,
            createdAt = Instant.EPOCH,
            request = PlanRequest(
                start = today.minusDays(7), weeks = 3, days = (1..7).toSet(),
                longDay = 7
            ),
            sessions = listOf(
                PlannedSession(
                    id = "missed-session", date = today.minusDays(1),
                    discipline = "run", title = "Threshold run", minutes = 55,
                    distanceKm = 9.0, kind = "quality", quality = true
                ),
                PlannedSession(
                    id = "future-session", date = today.plusDays(3),
                    discipline = "run", title = "Easy run", minutes = 45,
                    distanceKm = 7.0, kind = "easy"
                )
            ),
            explanations = emptyList(), status = "approved"
        )
    }

    @Test fun skipRequiresExplicitProposalReviewAndSafetyCheck() {
        val plan = plan()
        var writes = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideParityMissedWorkoutFlow(
                        plan = plan, session = plan.sessions.first(),
                        otherPlans = listOf(plan), busy = false,
                        actions = PlanActions(missed = { _, _, _, _, _ -> writes++ }),
                        onBack = {}, onBuildDraft = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("missed-workout-screen").assertExists()
        compose.onNodeWithTag("missed-option-skip").performClick()
        compose.onNodeWithTag("missed-preview-adjustment").performClick()
        compose.onNodeWithText("Why was this session missed?").assertExists()
        compose.onNodeWithText("Review proposal").performClick()
        compose.onNodeWithTag("review-changes-screen").assertExists()
        compose.onNodeWithText("NOT APPLIED").assertExists()
        compose.runOnIdle { assertEquals(0, writes) }
        compose.onNodeWithTag("review-run-checks").performClick()
        compose.onNodeWithTag("review-confirm-change").assertExists()
        compose.runOnIdle {
            assertEquals(0, writes)
            assertEquals("planned", plan.sessions.first().status)
        }
        compose.onNodeWithTag("review-confirm-change").performClick()
        compose.runOnIdle { assertEquals(1, writes) }
    }

    @Test fun activityEditIsAvailableWithoutTheRetiredMapAppearanceButton() {
        val activity = ActivityRecord(
            "source-run", "Recorded tempo", "run", "manual",
            Instant.parse("2026-10-29T10:00:00Z"),
            7400.0, 2870.0, 2790.0, 379.0, 150.0, 64.0
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-ui",
            activities = listOf(activity)
        )
        val renamed = java.util.concurrent.atomic.AtomicReference<String?>(null)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state, false,
                        NativeActions({}, { _, _ -> },
                            renameActivity = { id, title ->
                                renamed.set(id + ":" + title)
                                true
                            })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("timeline-recorded-source-run"))
        compose.onNodeWithTag("timeline-recorded-source-run").performClick()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-map-fullscreen").assertDoesNotExist()
        compose.onNodeWithTag("activity-change-glass-variant").assertDoesNotExist()
        compose.onNodeWithTag("activity-share").assertExists()
        compose.onNodeWithTag("activity-edit").assertExists().performClick()
        compose.onNodeWithTag("activity-rename-field").assertExists()
            .performTextReplacement("Renamed tempo")
        compose.onNodeWithTag("activity-rename-save").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            renamed.get() == "source-run:Renamed tempo"
        }
        compose.onNodeWithTag("activity-rename-field").assertDoesNotExist()
    }
}
