package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CoachedSetTableUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val engine = WorkoutPlayerEngine { android.os.SystemClock.elapsedRealtime() }
    private val logTime = Instant.parse("2026-11-24T12:00:00Z")

    private fun buildStrengthSession(id: String = "str-s1", status: String = "planned", log: WorkoutLog? = null) =
        PlannedSession(
            id = id,
            date = LocalDate.of(2026, 11, 24),
            discipline = "str",
            title = "Strength Foundation",
            minutes = 35,
            distanceKm = null,
            kind = "strength",
            exercises = listOf(
                ExercisePrescription("gs", "Goblet squat", 2, reps = 8, rest = 60, equipment = "Dumbbell"),
                ExercisePrescription("plank", "Forearm plank", 2, seconds = 10, rest = 45, equipment = "Mat / floor")
            ),
            status = status,
            log = log
        )

    private fun buildCalisthenicsPlan(): PlanSnapshot {
        val prevSession = PlannedSession(
            id = "cal-prev",
            date = LocalDate.of(2026, 11, 20),
            discipline = "cal",
            title = "Calisthenics",
            minutes = 30,
            distanceKm = null,
            kind = "calisthenics",
            exercises = listOf(
                ExercisePrescription("e4", "Push-up", 2, reps = 10, rest = 60, equipment = "Bodyweight"),
                ExercisePrescription("fc", "Hollow tuck", 2, seconds = 30, rest = 45, equipment = "Mat / floor")
            ),
            status = "completed",
            log = WorkoutLog(
                at = logTime.minusSeconds(86_400),
                performedOn = LocalDate.of(2026, 11, 20),
                sets = listOf(
                    SetRecord("e4", 1, reps = 12),
                    SetRecord("e4", 2, reps = 12)
                )
            )
        )
        val todaySession = PlannedSession(
            id = "cal-today",
            date = LocalDate.of(2026, 11, 24),
            discipline = "cal",
            title = "Calisthenics",
            minutes = 30,
            distanceKm = null,
            kind = "calisthenics",
            exercises = listOf(
                ExercisePrescription("e4", "Push-up", 2, reps = 10, rest = 60, equipment = "Bodyweight"),
                ExercisePrescription("fc", "Hollow tuck", 2, seconds = 30, rest = 45, equipment = "Mat / floor")
            )
        )
        return PlanSnapshot(
            id = "cal-plan",
            owner = "owner",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = logTime.minusSeconds(172_800),
            request = PlanRequest(goal = "Calisthenics foundation", runDays = 0, calDays = 2, days = setOf(1, 5)),
            sessions = listOf(prevSession, todaySession),
            explanations = emptyList(),
            status = "approved"
        )
    }

    @Test fun strengthAndCalisthenicsDefaultToSetTableWithTargetsAndNoOverflow() {
        val calPlan = buildCalisthenicsPlan()
        val planState = mutableStateOf(calPlan)
        val execMap = mutableMapOf<String, WorkoutExecution>()
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    val p = planState.value
                    val s = p.sessions.last()
                    WorkoutScreen(
                        state = TrainingState(initializing = false, account = "owner", plans = listOf(p)),
                        planId = p.id,
                        sessionId = s.id,
                        actions = PlanActions(
                            executeWorkout = { _, sid, _, event ->
                                val base = execMap.getOrPut(sid) { WorkoutExecution.create("ex-$sid", p.id, s, "fin-$sid") }
                                val next = event?.let { engine.reduce(base, it) } ?: base
                                execMap[sid] = next
                                next
                            }
                        ),
                        onBack = {}
                    )
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coached-set-table").fetchSemanticsNodes().isNotEmpty() }
        val root = compose.onNodeWithTag("coached-set-table").getUnclippedBoundsInRoot()

        for (tag in listOf("coached-back", "coached-open-player", "coached-finish", "coached-timer-fc")) {
            val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag height ${(b.bottom - b.top).value} < 48dp", (b.bottom - b.top).value >= 47.5f)
            assertTrue("$tag clips right edge", b.right <= root.right + 1.dp)
        }
        compose.onNodeWithTag("coached-set-list").performScrollToNode(hasTestTag("coached-level-up-e4"))
        val levelUpBounds = compose.onNodeWithTag("coached-level-up-e4").getUnclippedBoundsInRoot()
        assertTrue((levelUpBounds.bottom - levelUpBounds.top).value >= 47.5f)
        assertTrue(levelUpBounds.right <= root.right + 1.dp)
    }

    @Test fun scopedTimerPartialHoldAutoSavesUnconfirmedAndConfirmedHoldFeedsDebrief() {
        val strengthSession = buildStrengthSession()
        val strengthPlan = PlanSnapshot(
            id = "str-plan",
            owner = "owner",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = logTime,
            request = PlanRequest(goal = "General strength", runDays = 0, strengthDays = 1, days = setOf(2)),
            sessions = listOf(strengthSession),
            explanations = emptyList(),
            status = "approved"
        )
        val currentPlan = mutableStateOf(strengthPlan)
        var executionState = WorkoutExecution.create("exec-str", strengthPlan.id, strengthSession, "fin-str")

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    val p = currentPlan.value
                    val s = p.sessions.single()
                    WorkoutScreen(
                        state = TrainingState(initializing = false, account = "owner", plans = listOf(p)),
                        planId = p.id,
                        sessionId = s.id,
                        actions = PlanActions(
                            executeWorkout = { _, _, _, event ->
                                executionState = event?.let { engine.reduce(executionState, it) } ?: executionState
                                if (event is PlayerEvent.FinalizeSession && executionState.finalLog != null) {
                                    currentPlan.value = p.copy(
                                        sessions = listOf(s.copy(status = "completed", log = executionState.finalLog))
                                    )
                                }
                                executionState
                            }
                        ),
                        onBack = {}
                    )
                }
            }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coached-set-table").fetchSemanticsNodes().isNotEmpty() }
        // Session 1 Strength shows effort-anchored target with zero guessed weights
        compose.onNodeWithTag("coached-projection-gs").assertTextContains("RIR", substring = true)

        // Open scoped WorkoutPlayer from Plank Timer CTA -> scopes directly to Forearm plank Set 1
        compose.onNodeWithTag("coached-set-list").performScrollToNode(hasTestTag("coached-timer-plank"))
        compose.onNodeWithTag("coached-timer-plank").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player-exercise").assertTextEquals("Forearm plank")
        compose.onNodeWithTag("player-back-to-table").assertIsDisplayed()

        // Start hold timer -> wait > 3.2s -> Pause -> Back to Table auto-saves unconfirmed partial seconds
        compose.onNodeWithTag("player-play-pause").performClick()
        Thread.sleep(3_300)
        compose.onNodeWithTag("player-play-pause").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("player-log-hold").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("player-back-to-table").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coached-set-table").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, executionState.outcome.confirmedSets)
        assertTrue((executionState.sets[2].actual?.seconds ?: 0) >= 3)
        assertEquals(SetDisposition.PENDING, executionState.sets[2].disposition)

        // Now confirm the partial hold directly in the Set Table -> Finish -> Debrief
        compose.onNodeWithTag("coached-set-list").performScrollToNode(hasTestTag("coached-confirm-2"))
        compose.onNodeWithTag("coached-confirm-2").performClick()
        compose.waitUntil(3_000) { executionState.outcome.confirmedSets == 1 }
        compose.onNodeWithTag("coached-rest-bar").assertIsDisplayed()
        compose.onNodeWithTag("coached-finish").performClick()
        compose.waitUntil(3_000) { compose.onAllNodesWithText("Save & View Debrief").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save & View Debrief").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coach-debrief").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, currentPlan.value.sessions.single().log!!.sets.size)
        assertEquals("plank", currentPlan.value.sessions.single().log!!.sets.single().exerciseId)
    }
}
