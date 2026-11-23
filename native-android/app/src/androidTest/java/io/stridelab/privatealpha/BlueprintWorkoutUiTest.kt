package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class BlueprintWorkoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun builderOpensExactWorkoutAndBackPreservesWeek() {
        val state = mutableStateOf(TrainingState(initializing = false, account = "blueprint-qa"))
        var gearSaved: Pair<String, Map<String, Int>>? = null
        var approvals = 0
        val actions = PlanActions(
            saveBuilderEquipment = { owner, map -> gearSaved = owner to map },
            approve = { _, _ -> approvals++ },
            generate = { request ->
                val weekTwo = request.start.plusWeeks(1)
                val run = PlannedSession(
                    "second-week-run", weekTwo, "run", "Second week run", 35, 5.0, "easy",
                    steps = listOf("Warm up for 5 minutes", "Run easy for 25 minutes", "Cool down for 5 minutes")
                )
                val draft = PlanSnapshot(
                    "blueprint-qa-draft", "blueprint-qa", ENGINE1_VERSION, ENGINE1_POLICY,
                    Instant.EPOCH, request.copy(weeks = 2), listOf(run), emptyList()
                )
                val saved = draft.copy(status = "approved",
                    sessions = listOf(run.copy(title = "Saved collision")))
                state.value = state.value.copy(planPreview = draft, plans = listOf(saved))
            }
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    val nav = rememberNavController()
                    NativeStrideContent(state.value, false, NativeActions({}, { _, _ -> }, planning = actions), nav)
                    LaunchedEffect(nav) { nav.navigate("builder/str") }
                }
            }
        }
        fun next() = compose.onNodeWithTag("builder-figma-continue").performClick()
        next()
        compose.onNodeWithTag("builder-strength-goal-1").performClick()
        next()
        compose.onNodeWithTag("builder-strength-setup-equipment").performScrollTo().performClick()
        compose.onNodeWithTag("builder-strength-equipment-db").performClick()
        next()
        compose.runOnIdle { assertEquals("blueprint-qa" to mapOf("db" to 1), gearSaved) }
        next()
        next()
        next()
        compose.onNodeWithTag("builder-next-week").performScrollTo().performClick()
        val row = compose.onNodeWithTag("builder-preview-session-second-week-run")
        row.performScrollTo().performClick()
        compose.onNodeWithTag("workout-planned-title").assertTextEquals("Second week run")
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-profile-card"))
        compose.onNodeWithTag("workout-log-toggle").assertDoesNotExist()
        compose.onNodeWithText("Export to Watch (.FIT)").assertDoesNotExist()
        compose.onNodeWithTag("screen").performScrollToIndex(0)
        compose.onNodeWithTag("back-to-blueprint").performClick()
        row.assertExists()
        compose.onNodeWithText("Week 2 of 2").assertExists()
        row.performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(account = "different-owner") }
        compose.onNodeWithTag("blueprint-workout-title").assertTextEquals("Workout unavailable")
        compose.onNodeWithTag("workout-planned-title").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, approvals) }
    }
}
