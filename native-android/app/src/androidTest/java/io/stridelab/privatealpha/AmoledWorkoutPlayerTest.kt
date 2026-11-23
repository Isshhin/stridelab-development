package io.stridelab.privatealpha

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AmoledWorkoutPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val engine = WorkoutPlayerEngine(SystemClock::elapsedRealtime)
    private val date = LocalDate.now()
    private val logTime = Instant.parse("2026-11-23T08:00:00Z")

    private fun session(discipline: String, target: ExercisePrescription, title: String = "Session") =
        PlannedSession("session", date, discipline, title, 20, null, "structured-$discipline", exercises = listOf(target))

    private fun show(session: PlannedSession, initial: WorkoutExecution = WorkoutExecution.create("fixture", "plan", session, "final")):
        androidx.compose.runtime.MutableState<WorkoutExecution> {
        val state = mutableStateOf(initial)
        val entries = mutableStateOf("{}")
        val effort = mutableStateOf("")
        compose.activity.setContent {
            StrideTheme {
                WorkoutPlayer(state.value, entries.value, { entries.value = it }, effort.value, { effort.value = it },
                    "", {}, date.toString(), {}, "", {}, null, {}, false, null, {}, {}, session.playerMode(), false
                ) { event -> state.value = engine.reduce(state.value, event) }
            }
        }
        return state
    }

    @Test fun repsRestPreviewAndUndoKeepTheEventsDistinct() {
        val state = show(session("str", ExercisePrescription("squat", "Squat", 2, reps = 8, rest = 60)))
        compose.onNodeWithContentDescription("Increase reps").performClick()
        compose.onNodeWithContentDescription("Repetitions 9").assertExists()
        compose.onNodeWithTag("player-done-set").performClick()
        compose.onNodeWithTag("player-next-up").assertExists()
        compose.onNodeWithText("NEXT UP").assertExists()
        val initialRest = compose.runOnIdle { state.value.timer!!.budgetMs }
        compose.onNodeWithTag("player-extend-rest").performClick()
        compose.runOnIdle { assertEquals(initialRest + 15_000, state.value.timer!!.budgetMs) }
        compose.onNodeWithText("Edit previous set").performClick()
        compose.onNodeWithText("Undo set").performClick()
        compose.runOnIdle {
            assertEquals(SetDisposition.PENDING, state.value.current.disposition)
            assertNull(state.value.current.actual)
        }
        compose.onNodeWithTag("player-done-set").performClick()
        compose.onNodeWithTag("player-skip-rest").performClick()
        compose.onNodeWithText("Skip Set").performClick()
        compose.runOnIdle {
            assertEquals(SetDisposition.COMPLETED, state.value.sets[0].disposition)
            assertEquals(SetDisposition.SKIPPED, state.value.sets[1].disposition)
        }
    }

    @Test fun expiredHoldWaitsForConfirmationAndLogsTheMeasuredSeconds() {
        val session = session("iso", ExercisePrescription("hold", "Hold", 2, seconds = 45))
        val expired = WorkoutExecution.create("fixture", "plan", session, "final").copy(
            phase = PlayerPhase.ActiveWork, timer = PlayerTimer(45_000, deadlineMs = SystemClock.elapsedRealtime() - 1000))
        val state = show(session, expired)
        compose.onNodeWithContentDescription("Work countdown 0:00").assertExists()
        compose.runOnIdle { assertNull(state.value.current.actual) }
        compose.onNodeWithTag("player-done-set").performClick()
        compose.runOnIdle { assertEquals(45, state.value.current.actual!!.seconds) }
    }

    @Test fun plyometricContactsAndEarlyExitUseDistinctActions() {
        val state = show(session("plyo", ExercisePrescription("pogo-hop", "Pogo hop", 2, reps = 12)))
        compose.onNodeWithText("0 / 24 contacts").assertExists()
        compose.onNodeWithTag("player-done-set").performClick()
        compose.onNodeWithTag("player-skip-rest").performClick()
        compose.onNodeWithText("12 / 24 contacts").assertExists()
        compose.onNodeWithContentDescription("Exit workout").performClick()
        compose.onNodeWithText("Finish Early").performClick()
        compose.runOnIdle { assertEquals(ExecutionStatus.FINISHED_EARLY, state.value.status) }
        compose.onNodeWithText("Save & View Debrief").assertExists()
    }

    @Test fun conditioningShowsRoundAndClockWithMuteToggle() {
        show(session("cond", ExercisePrescription("march", "Brisk march", 2, seconds = 45), "AMRAP work capacity"))
        compose.onNodeWithText("ROUND 1 / 2").assertExists()
        compose.onNodeWithContentDescription("Work countdown 0:45").assertExists()
        compose.onNodeWithText("Mute").performClick()
        compose.onNodeWithText("Unmute").assertExists()
    }

    @Test fun completedWorkoutScreenCarriesEachDisciplineIntoTheDebrief() {
        val fixtures = listOf("str", "cal", "iso", "plyo", "cond").map { discipline ->
            val timed = discipline in setOf("iso", "cond")
            val id = if (discipline == "plyo") "pogo-hop" else "movement"
            val target = ExercisePrescription(id, "Movement", 1, reps = 8.takeUnless { timed }, seconds = 30.takeIf { timed })
            val session = session(discipline, target).copy(id = "session-$discipline")
            PlanSnapshot("plan-$discipline", "owner", ENGINE15_VERSION, ENGINE15_POLICY, logTime,
                PlanRequest(goal = "General"), listOf(session), emptyList(), "approved")
        }
        val plan = mutableStateOf(fixtures.first())
        val executions = mutableMapOf<String, WorkoutExecution>()
        compose.activity.setContent {
            StrideTheme {
                val current = plan.value
                val session = current.sessions.single()
                WorkoutScreen(TrainingState(initializing = false, plans = listOf(current)), current.id, session.id,
                    PlanActions(executeWorkout = { _, _, _, event ->
                        val before = executions.getOrPut(current.id) { WorkoutExecution.create(current.id, current.id, session, "final") }
                        val after = event?.let { engine.reduce(before, it) } ?: before
                        executions[current.id] = after
                        if (event is PlayerEvent.FinalizeSession && after.finalLog != null)
                            plan.value = current.copy(sessions = listOf(session.copy(status = "completed", log = after.finalLog)))
                        after
                    }), {})
            }
        }
        fixtures.forEach { fixture ->
            compose.runOnIdle { plan.value = fixture }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("player-done-set").fetchSemanticsNodes().isNotEmpty() }
            if (fixture.sessions.single().exercises.single().seconds != null) {
                compose.onNodeWithTag("player-done-set").performClick()
                compose.onNodeWithText("Actual work seconds").performTextInput("25")
                compose.onNodeWithText("Confirm set").performClick()
            } else compose.onNodeWithTag("player-done-set").performClick()
            compose.onNodeWithTag("player-skip-rest").performClick()
            compose.onNodeWithText("Save & View Debrief").performClick()
            compose.onAllNodesWithTag("coach-debrief").assertCountEquals(1)
        }
    }
}
