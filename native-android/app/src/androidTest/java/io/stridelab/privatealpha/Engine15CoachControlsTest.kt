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
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=context.getExternalFilesDir(null) ?: context.cacheDir
        java.io.File(dir,"$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }

    @Test fun builderAuthorsAndSavesARepeatedUserDefinedWorkoutByStableSlot() {
        val saved=mutableStateOf<Map<String,RunningPrescription>>(emptyMap())
        show {
            TracksideStructuredWorkoutAuthoring(weeks=2,qualityCount=1,saved=saved.value,
                onSave={saved.value=it},onRemove={key->saved.value=saved.value-key})
        }
        compose.onNodeWithTag("builder-workout-add-warm_up").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-amount-1").performScrollTo().performTextInput("2000")
        compose.onNodeWithTag("builder-workout-effort-1").performTextInput("Easy by feel")
        compose.onNodeWithTag("builder-workout-add-cool_down").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-amount-2").performScrollTo().performTextInput("2000")
        compose.onNodeWithTag("builder-workout-add-repeat-group").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-down-2").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-save").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-error").assertDoesNotExist()
        compose.onNodeWithTag("builder-workout-copy-missing").performScrollTo().performClick()
        compose.runOnIdle {
            val workout=checkNotNull(saved.value["w1-run1"])
            assertEquals("Threshold",workout.family)
            assertEquals(4,workout.segments.count { it.purpose=="WORK" })
            assertEquals(3,workout.segments.count { it.purpose=="RECOVERY" })
            assertEquals(8400,workout.meters)
            assertEquals(setOf("w1-run1","w2-run1"),saved.value.keys)
            assertEquals(workout,saved.value["w2-run1"])
            assertEquals(workout,PlanRequest.decode(PlanRequest(policyChoice="engine15",userDefinedQualityWorkouts=saved.value).encode()).userDefinedQualityWorkouts["w1-run1"])
        }
    }

    @Test fun builderRequiresExplicitDistanceEquivalentsForTimedRepeatGroups() {
        val saved=mutableStateOf<Map<String,RunningPrescription>>(emptyMap())
        show {
            TracksideStructuredWorkoutAuthoring(weeks=1,qualityCount=1,saved=saved.value,
                onSave={saved.value=it},onRemove={key->saved.value=saved.value-key})
        }
        compose.onNodeWithTag("builder-workout-add-warm_up").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-amount-1").performScrollTo().performTextInput("2000")
        compose.onNodeWithTag("builder-workout-add-cool_down").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-amount-2").performScrollTo().performTextInput("2000")
        compose.onNodeWithText("Time repeats").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-group-work-equivalent").performScrollTo().performTextInput("800")
        compose.onNodeWithTag("builder-workout-group-recovery-equivalent").performScrollTo().performTextInput("400")
        compose.onNodeWithTag("builder-workout-add-repeat-group").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-down-2").performScrollTo().performClick()
        compose.onNodeWithTag("builder-workout-save").performScrollTo().performClick()
        compose.runOnIdle {
            val workout=checkNotNull(saved.value["w1-run1"])
            val steps=checkNotNull(workout.mixedSegments)
            assertEquals(3,workout.encode().getInt("version"))
            assertEquals(8400,workout.reconciledDistanceMeters)
            assertEquals(4,steps.count { it.purpose=="WORK" })
            assertEquals(3,steps.count { it.purpose=="RECOVERY" })
        }
    }
    @Test fun plannedWorkoutShortcutsUseRealMoveAndMissedCallbacks() {
        val p=plan()
        var moved=0
        var markedMissed=0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    WorkoutScreen(
                        TrainingState(initializing=false,plans=listOf(p)),
                        p.id,"target",PlanActions(),
                        onBack={},
                        onMissed={ markedMissed++ },
                        onMove={ moved++ }
                    )
                }
            }
        }
        compose.onNodeWithTag("workout-planned-move").performScrollTo().performClick()
        compose.onNodeWithTag("workout-planned-missed").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1,moved)
            assertEquals(1,markedMissed)
        }
    }

    @Test fun plannedWorkoutHidesLegacyFormsUntilUserOpensThem() {
        val p=plan()
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    WorkoutScreen(
                        TrainingState(initializing=false,plans=listOf(p)),
                        p.id,"target",PlanActions(),{}
                    )
                }
            }
        }
        compose.onNodeWithTag("workout-planned-title")
            .assertTextEquals("Easy run")
        compose.onNodeWithTag("workout-recommendations-accordion").assertDoesNotExist()
        compose.onNodeWithText("Preview recommendations").assertDoesNotExist()
        compose.onNodeWithTag("workout-record-form").assertDoesNotExist()
        compose.onNodeWithTag("workout-log-toggle").performClick()
        compose.onNodeWithTag("workout-record-form").assertExists()
        compose.onNodeWithTag("workout-actual-date").assertExists()
    }

    @Test fun plannedRunPreviewHasReadableTargetsAndOmitsCoachNotes() {
        val session=PlannedSession(
            "preview",day.plusDays(1),"run","Aerobic endurance run",
            109,13.6,"easy",
            steps=listOf("1. 13600 m easy running"),
            reason="The easy aerobic workout supports endurance.",
            runningPrescription=RunningPrescription("EASY",null,
                listOf(RunningSegment("EASY",13600,"Comfortable conversational running")))
        )
        val p=plan().copy(sessions=listOf(session))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    WorkoutScreen(
                        TrainingState(initializing=false,plans=listOf(p)),
                        p.id,session.id,PlanActions(),{}
                    )
                }
            }
        }
        compose.onNodeWithTag("workout-planned-summary").assertExists()
        compose.onNodeWithTag("workout-planned-primary-target")
            .assertTextEquals("13.6 km")
        compose.onNodeWithTag("workout-planned-duration")
            .assertTextEquals("109 min")
        compose.onNodeWithText("13600 m easy running").assertDoesNotExist()
        compose.onNodeWithTag("workout-planned-structure").assertExists()
        compose.onNodeWithTag("workout-planned-coach-notes").assertDoesNotExist()
        compose.onNodeWithTag("workout-planned-notes-toggle").assertDoesNotExist()
        compose.onNodeWithTag("workout-private-feedback").assertDoesNotExist()
    }

    @Test fun repeatedIntervalsRenderAsOneGroupWithExactSteps() {
        val warm=RunningSegment("WARM_UP",1300,"Easy conversational running")
        val work=RunningSegment("WORK",400,"Steady controlled effort")
        val recovery=RunningSegment("RECOVERY",200,"Easy jog or walk")
        val cool=RunningSegment("COOL_DOWN",900,"Easy running")
        val segments=buildList {
            add(warm)
            repeat(5) { index ->
                add(work)
                if(index<4) add(recovery)
            }
            add(cool)
        }
        val session=PlannedSession("interval",day.plusDays(1),"run",
            "Interval workout",70,5.0,"quality",quality=true,
            runningPrescription=RunningPrescription("QUALITY","Intervals",segments))
        val p=plan().copy(sessions=listOf(session))
        show {
            TracksidePlannedRunningPreview(
                session,Engine15SessionPresentation.forSession(p,session.id),
                session.title, onBack={}
            )
        }
        compose.onNodeWithTag("workout-planned-structure").assertExists()
        compose.onNodeWithTag("workout-step-single-0").assertExists()
        compose.onNodeWithTag("workout-repeat-group-1").assertExists()
        compose.onNodeWithTag("workout-repeat-count-1")
            .assertTextEquals("5 × Interval Set")
        compose.onNodeWithTag("workout-repeat-work-1").assertExists()
        compose.onNodeWithTag("workout-repeat-recovery-1").assertExists()
        compose.onNodeWithText("Between repetitions").assertExists()
        compose.onNodeWithTag("workout-step-single-2").assertExists()
    }

    @Test fun mixedWorkoutKeepsTransitionBetweenDistinctBlocks() {
        val steps=listOf(
            RunningStepV2("WARM_UP","Easy",meters=2000),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("RECOVERY","Easy jog",meters=400),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("RECOVERY","Easy jog",meters=400),
            RunningStepV2("WORK","User threshold target",meters=1000),
            RunningStepV2("TRANSITION","Easy between blocks",seconds=180,distanceEquivalentMeters=300),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("RECOVERY","Easy jog",meters=200),
            RunningStepV2("WORK","User speed target",meters=200),
            RunningStepV2("COOL_DOWN","Easy",meters=6600)
        )
        val session=PlannedSession("two-block",day.plusDays(1),"run",
            "Threshold and speed workout",75,13.3,"quality",quality=true,
            runningPrescription=RunningPrescription("QUALITY","Threshold",emptyList(),steps,
                reconciledDistanceMeters=13300))
        val p=plan().copy(sessions=listOf(session))
        show {
            TracksidePlannedRunningPreview(
                session,Engine15SessionPresentation.forSession(p,session.id),
                session.title, onBack={}
            )
        }
        compose.onNodeWithTag("workout-mixed-step-0").assertExists()
        compose.onNodeWithTag("workout-mixed-repeat-1").assertExists()
        compose.onNodeWithTag("workout-mixed-step-2").assertExists()
        compose.onNodeWithText("Transition").assertExists()
        compose.onNodeWithTag("workout-mixed-repeat-3").assertExists()
        compose.onNodeWithTag("workout-mixed-step-4").assertExists()
    }

    @Test fun reviewedTimeOnlyPreviewNeverShowsOriginalDistanceAsCurrentTarget() {
        val session=PlannedSession(
            "shortened",day.plusDays(1),"run","Easy run",60,10.0,"easy",
            suggestion=SessionSuggestion(45,Engine15Feedback.RULE_ID+": accepted",true)
        )
        val p=plan().copy(sessions=listOf(session))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    WorkoutScreen(
                        TrainingState(initializing=false,plans=listOf(p)),
                        p.id,session.id,PlanActions(),{}
                    )
                }
            }
        }
        compose.onNodeWithTag("workout-planned-primary-target")
            .assertTextEquals("Time-based")
        compose.onNodeWithTag("workout-planned-duration")
            .assertTextEquals("45 min")
        compose.onNodeWithText("Planned target: 10.0 km",substring=true)
            .assertDoesNotExist()
    }
    @Test fun weeklyPreviewAndApprovalPreserveOriginalRecords() {
        val original=plan();var saved by mutableStateOf(original);var approvals=0
        val actions=PlanActions(previewWeek={_,input->Engine15WeeklyReview.preview(saved,day,input)},acceptWeek={ review -> approvals++;saved=Engine15WeeklyReview.accept(saved,review,day,Instant.now()).copy(revision=2) })
        show { Engine15CoachReviewCard(saved,actions,false) }
        compose.onNodeWithText("Review week").performClick()
        compose.onNodeWithText("5",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Preview weekly decision").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0,approvals);assertNull(saved.sessions.last().suggestion) }
        compose.onNodeWithText("Approve these changes").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Approve these changes").performClick()
        compose.runOnIdle { assertEquals(1,approvals);assertEquals(original.sessions.first(),saved.sessions.first());assertEquals(10.0,saved.sessions.last().distanceKm!!,0.0);assertTrue(saved.sessions.last().suggestion!!.accepted) }
    }
    @Test fun overrunReviewRetainsTheCurrentFatigueChoice() {
        val p=plan();var input:Engine15WeeklyReview.Input?=null
        show { Engine15CoachReviewCard(p,PlanActions(previewWeek={_,i->input=i;Engine15WeeklyReview.preview(p,day,i)}),false) }
        compose.onNodeWithText("Review week").performClick()
        compose.onNodeWithText("5",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Review extra running").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(5,input!!.fatigue);assertEquals(Engine15WeeklyReview.Trigger.OVERRUN,input!!.trigger) }
    }
    @Test fun workoutFeedbackReachesTheSavedLogAndDraft() {
        val p=plan().copy(sessions=listOf(PlannedSession("target",day,"run","Threshold",75,14.0,"quality",quality=true)))
        var recorded:WorkoutLog?=null;val drafts=mutableListOf<WorkoutEntryDraft>()
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { WorkoutScreen(TrainingState(initializing=false,plans=listOf(p)),p.id,"target",PlanActions(record={_,_,_,log,_->recorded=log},stageWorkoutDraft={drafts+=it}),{}) } } } }
        compose.onNodeWithTag("workout-log-toggle").performClick()
        compose.onNodeWithTag("workout-record-form").assertExists()
        compose.onNodeWithTag("workout-actual-date").assertExists()
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
        compose.onNodeWithText("Use selected average · 10.0 km/week").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(10.0,selected!!,0.0) }
    }
    @Test fun unansweredDraftFeedbackDoesNotResurrectPreviouslySavedPain() {
        val session=PlannedSession("target",day,"run","Easy",60,10.0,"easy",status="in_progress",logRevision=1,log=WorkoutLog(Instant.now(),completionDifficulty="Harder",discomfort="Pain"))
        val p=plan().copy(sessions=listOf(session))
        val draft=WorkoutEntryDraft(p.owner,p.id,session.id,1,"","","","",day.toString(),"{}",1)
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { androidx.compose.material3.Surface { WorkoutScreen(TrainingState(initializing=false,plans=listOf(p),workoutDrafts=mapOf((p.id to session.id) to draft)),p.id,session.id,PlanActions(),{}) } } } }
        compose.onNodeWithTag("workout-log-toggle").performClick()
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
