package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

class WorkoutPlayerTest {
    @get:Rule val compose=createAndroidComposeRule<NativeMainActivity>()
    private fun plan():PlanSnapshot {
        val generated=Engine1.generate(PlanRequest(start=LocalDate.now(),weeks=1,runDays=0,strengthDays=1,days=(1..7).toSet()),"synthetic-player")
        val exercises=listOf(ExercisePrescription("e1","Back squat",2,8,rest=60,equipment="Barbell + rack with safeties"),ExercisePrescription("e3","Romanian deadlift",2,8,rest=60,equipment="Barbell"))
        return PlanOperations.approve(generated.copy(sessions=generated.sessions.map { it.copy(exercises=exercises) }),emptyList())
    }
    @Test fun guideAndBrowsingDoNotRecordAndExplicitConfirmationSavesOnlyOneSetBeforeRest() {
        val initial=plan();val state=mutableStateOf(TrainingState(initializing=false,account=initial.owner,plans=listOf(initial)))
        val actions=PlanActions(record={_,_,sid,log,complete -> state.value=state.value.copy(plans=listOf(PlanOperations.record(state.value.plans.single(),sid,log,complete).let { it.copy(revision=it.revision+1) })) })
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { TestWorkout(state.value,initial.id,initial.sessions.first().id,actions,{}) } } }
        compose.onNodeWithTag("workout-player").assertExists()
        compose.onNodeWithTag("player-exercise").assertTextEquals("Back squat")
        capture("simulation-workout-focus")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("How to do this exercise"));compose.onNodeWithText("How to do this exercise").performClick()
        compose.onNodeWithText("Back to workout").performScrollTo().assertExists();capture("simulation-workout-form-guide")
        compose.onNodeWithText("Back to workout").performClick()
        assertNull(state.value.plans.single().sessions.first().log)
        compose.onNodeWithText("Confirm 8 reps & rest").performClick()
        compose.waitUntil(5000) { state.value.plans.single().sessions.first().log!=null }
        val session=state.value.plans.single().sessions.first()
        assertEquals("in_progress",session.status);assertEquals(1,session.log!!.sets.size);assertEquals(8,session.log!!.sets.single().reps)
        assertNull(session.log!!.minutes);assertNull(session.log!!.distanceKm);assertNull(session.log!!.sets.single().kg)
        compose.onNodeWithTag("player-rest").assertExists();capture("simulation-workout-rest")
        compose.onNodeWithText("Pause rest").performClick();compose.onNodeWithText("+15 sec").performClick()
        compose.onNodeWithText("Finish rest & continue").performClick()
        compose.onNodeWithText("Set 2 of 2 · Barbell + rack with safeties").assertExists()
        assertEquals(1,state.value.plans.single().sessions.first().log!!.sets.size)
        capture("simulation-workout-next-set")
    }
    @Test fun differentActualRepsAndLoadStayExplicitAndReviewKeepsOriginalTargets() {
        val initial=plan();val state=mutableStateOf(TrainingState(initializing=false,account=initial.owner,plans=listOf(initial)))
        val actions=PlanActions(record={_,_,sid,log,complete -> state.value=state.value.copy(plans=listOf(PlanOperations.record(state.value.plans.single(),sid,log,complete).copy(revision=1))) })
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { TestWorkout(state.value,initial.id,initial.sessions.first().id,actions,{}) } } }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Enter different reps, time or load"));compose.onNodeWithText("Enter different reps, time or load").performClick()
        compose.onNodeWithText("Actual reps").performScrollTo().performTextInput("5")
        compose.onNodeWithText("Actual load (kg), optional").performScrollTo().performTextInput("12.5")
        compose.onNodeWithText("Confirm 5 reps & rest").performClick()
        val session=state.value.plans.single().sessions.first();assertEquals(5,session.log!!.sets.single().reps);assertEquals(12.5,session.log!!.sets.single().kg!!,0.0)
        assertEquals(8,session.exercises.first().reps)
        compose.onNodeWithTag("workout-review").performClick();compose.onNodeWithText("Return to guided workout").assertExists()
        assertEquals("in_progress",state.value.plans.single().sessions.first().status)
    }
    @Test fun invalidActualInputDoesNotRecordOrBeginRest() {
        val initial=plan();var writes=0
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { TestWorkout(TrainingState(initializing=false,account=initial.owner,plans=listOf(initial)),initial.id,initial.sessions.first().id,PlanActions(record={_,_,_,_,_->writes++}),{}) } } }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Enter different reps, time or load"));compose.onNodeWithText("Enter different reps, time or load").performClick()
        compose.onNodeWithText("Actual reps").performScrollTo().performTextInput("1.")
        compose.onNodeWithText("Confirm 1. reps & rest").performClick()
        assertEquals(0,writes);compose.onNodeWithTag("player-rest").assertDoesNotExist()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Enter whole repetitions, or leave it blank."))
    }
    @Test fun completedHoldTimerRequiresExplicitActualConfirmation() {
        val base=plan();val initial=base.copy(sessions=base.sessions.map { it.copy(exercises=listOf(ExercisePrescription("hollow-tuck","Hollow tuck hold",1,seconds=1))) })
        val state=mutableStateOf(TrainingState(initializing=false,account=initial.owner,plans=listOf(initial)))
        val actions=PlanActions(record={_,_,sid,log,done -> state.value=state.value.copy(plans=listOf(PlanOperations.record(state.value.plans.single(),sid,log,done).copy(revision=1))) })
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { TestWorkout(state.value,initial.id,initial.sessions.first().id,actions,{}) } } }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Start hold"));compose.onNodeWithText("Start hold").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Hold timer · 0 sec").fetchSemanticsNodes().isNotEmpty() }
        assertNull(state.value.plans.single().sessions.first().log)
        compose.onNodeWithText("Confirm 1 seconds & rest").performClick()
        assertEquals(1,state.value.plans.single().sessions.first().log!!.sets.single().seconds)
        assertEquals("in_progress",state.value.plans.single().sessions.first().status)
    }
    @Test fun playerAndFormInstructionsRemainReachableAtTwoHundredPercentText() {
        val initial=plan()
        compose.runOnUiThread { compose.activity.setContent { val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) { StrideTheme { TestWorkout(TrainingState(initializing=false,account=initial.owner,plans=listOf(initial)),initial.id,initial.sessions.first().id,PlanActions(),{}) } } } }
        compose.onNodeWithText("Confirm 8 reps & rest").assertIsDisplayed()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("How to do this exercise"));compose.onNodeWithText("How to do this exercise").performClick()
        compose.onNodeWithText("Back to workout").performScrollTo().assertIsDisplayed()
        capture("simulation-workout-guide-large-text")
    }
    @androidx.compose.runtime.Composable private fun TestWorkout(state:TrainingState,plan:String,session:String,actions:PlanActions,onBack:()->Unit) {
        androidx.compose.material3.Surface(Modifier.systemBarsPadding()) { WorkoutScreen(state,plan,session,actions,onBack) }
    }
    private fun capture(name:String) {
        compose.waitForIdle();android.os.SystemClock.sleep(350)
        val folder=File(compose.activity.getExternalFilesDir(null),"native-ui-evidence");folder.mkdirs()
        File(folder,"$name.png").outputStream().use { assertTrue(checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
}
