package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Engine15CoachControlsTest {
    @get:Rule val compose=createAndroidComposeRule<NativeMainActivity>()
    private val day=LocalDate.now()
    private fun plan():PlanSnapshot {
        val source=PlannedSession("source",day.minusDays(1),"run","Easy",60,10.0,"easy",status="completed",logRevision=1,
            log=WorkoutLog(Instant.now().minusSeconds(86400),minutes=80.0,distanceKm=14.0,effort=9,performedOn=day.minusDays(1)))
        val target=PlannedSession("target",day.plusDays(1),"run","Easy run",60,10.0,"easy")
        return PlanSnapshot("coach-plan","local-fixture",ENGINE15_VERSION,ENGINE15_POLICY,Instant.now(),PlanRequest(policyChoice="engine15",targetWeeklyKm=45.0),listOf(source,target),emptyList(),status="approved")
    }
    private fun show(content:@Composable ()->Unit) { compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() } } } } } }
    private fun capture(name:String) {
        compose.waitForIdle()
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        java.io.File(dir,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun weeklyPreviewAndApprovalPreserveOriginalRecords() {
        val original=plan();var saved by mutableStateOf(original);var approvals=0
        val actions=PlanActions(previewWeek={_,input->Engine15WeeklyReview.preview(saved,day,input)},acceptWeek={ review -> approvals++;saved=Engine15WeeklyReview.accept(saved,review,day,Instant.now()).copy(revision=2) })
        show { Engine15CoachReviewCard(saved,actions,false) }
        compose.onNodeWithText("Review this week").performClick()
        compose.onNodeWithText("5",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Preview weekly decision").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0,approvals);assertNull(saved.sessions.last().suggestion) }
        compose.onNodeWithText("Approve these changes").performScrollTo().assertIsEnabled()
        capture("coach-weekly-review")
        compose.onNodeWithText("Approve these changes").performClick()
        compose.runOnIdle { assertEquals(1,approvals);assertEquals(original.sessions.first(),saved.sessions.first());assertEquals(10.0,saved.sessions.last().distanceKm!!,0.0);assertTrue(saved.sessions.last().suggestion!!.accepted) }
    }
    @Test fun overrunReviewRetainsTheCurrentFatigueChoice() {
        val p=plan();var input:Engine15WeeklyReview.Input?=null
        show { Engine15CoachReviewCard(p,PlanActions(previewWeek={_,i->input=i;Engine15WeeklyReview.preview(p,day,i)}),false) }
        compose.onNodeWithText("Review this week").performClick()
        compose.onNodeWithText("5",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Review extra running").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5,input!!.fatigue);assertEquals(Engine15WeeklyReview.Trigger.OVERRUN,input!!.trigger) }
    }
    @Test fun workoutFeedbackReachesTheSavedLogAndDraft() {
        val p=plan().copy(sessions=listOf(PlannedSession("target",day,"run","Threshold",75,14.0,"quality",quality=true)))
        var recorded:WorkoutLog?=null;val drafts=mutableListOf<WorkoutEntryDraft>()
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { WorkoutScreen(TrainingState(initializing=false,plans=listOf(p)),p.id,"target",PlanActions(record={_,_,_,log,_->recorded=log},stageWorkoutDraft={drafts+=it}),{}) } } } }
        compose.onNodeWithTag("workout-actual-distance").performScrollTo().performTextInput("14")
        compose.onNodeWithText("Effort 1–10, optional").performScrollTo().performTextInput("7")
        compose.onNodeWithText("As planned").performScrollTo().performClick()
        compose.onNodeWithText("None").performScrollTo().performClick()
        compose.onNodeWithText("Actual quality work (km), optional").performScrollTo().performTextInput("9")
        compose.onNodeWithText("Actual quality work (km), optional").performImeAction()
        compose.waitUntil(5000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        capture("coach-post-run")
        compose.onNodeWithText("Save completed workout").performClick()
        compose.waitUntil(5000) { drafts.any { it.qualityWorkKm=="9" } }
        compose.runOnIdle { assertEquals("As planned",recorded!!.completionDifficulty);assertEquals("None",recorded!!.discomfort);assertEquals(9.0,recorded!!.qualityWorkKm!!,0.0) }
    }
    @Test fun goalButtonsCreateRequestsWithoutApprovingOrChangingMode() {
        val p=plan().copy(status="draft",mileageOptions=Engine15MileageOptions(35.0,45.0,8));var chosen:PlanRequest?=null
        show { MileageGoalChoices(p,false) { chosen=it } }
        compose.onNodeWithText("Review 35.0 km within 4 weeks").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(35.0,chosen!!.targetWeeklyKm!!,0.0);assertEquals(p.request.goal,chosen!!.goal) }
        compose.onNodeWithText("Review 45.0 km over 8 weeks").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(8,chosen!!.weeks);assertEquals(45.0,chosen!!.targetWeeklyKm!!,0.0) }
        capture("coach-goal-options")
    }
    @Test fun historyNeedsExplicitCompletedWeekConfirmation() {
        val start=day.with(DayOfWeek.MONDAY).minusWeeks(1)
        val p=plan().copy(sessions=listOf(plan().sessions.first().copy(date=start,log=WorkoutLog(Instant.now().minusSeconds(86400),distanceKm=10.0,performedOn=start))))
        var selected:Double?=null
        show { RunningHistoryConfirmation(TrainingState(initializing=false,plans=listOf(p))) { selected=it } }
        compose.onNodeWithText("Confirm complete weeks").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(start.toString(),substring=true).performScrollTo().performClick()
        compose.onNodeWithText("Use confirmed average · 10.0 km/week").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(10.0,selected!!,0.0) }
        capture("coach-history-confirmation")
    }
    @Test fun unansweredDraftFeedbackDoesNotResurrectPreviouslySavedPain() {
        val session=PlannedSession("target",day,"run","Easy",60,10.0,"easy",status="in_progress",logRevision=1,log=WorkoutLog(Instant.now(),completionDifficulty="Harder",discomfort="Pain"))
        val p=plan().copy(sessions=listOf(session))
        val draft=WorkoutEntryDraft(p.owner,p.id,session.id,1,"","","","",day.toString(),"{}",1)
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { WorkoutScreen(TrainingState(initializing=false,plans=listOf(p),workoutDrafts=mapOf((p.id to session.id) to draft)),p.id,session.id,PlanActions(),{}) } } } }
        compose.onNodeWithText("How did completion feel?").performScrollTo()
        compose.onAllNodes(hasText("Unanswered") and isSelected()).assertCountEquals(2)
    }
    @Test fun completedLinkedRunAcceptsFeedbackWithoutEditingMeasurements() {
        val p=plan();var saved:Triple<Int?,String?,String?>?=null
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { WorkoutScreen(TrainingState(initializing=false,plans=listOf(p)),p.id,"source",PlanActions(saveRunResponse={_,_,_,effort,difficulty,pain->saved=Triple(effort,difficulty,pain)}),{}) } } } }
        compose.onNodeWithText("As planned").performScrollTo().performClick()
        compose.onNodeWithText("None").performScrollTo().performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save run feedback"))
        capture("coach-completed-feedback")
        compose.onNodeWithText("Save run feedback").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("As planned",saved!!.second);assertEquals("None",saved!!.third);assertEquals(14.0,p.sessions.first().log!!.distanceKm!!,0.0) }
    }
}
