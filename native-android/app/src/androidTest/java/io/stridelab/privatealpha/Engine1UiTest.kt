package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import android.graphics.Bitmap
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

class Engine1UiTest {
    @get:Rule val compose=createAndroidComposeRule<NativeMainActivity>()
    private fun enterActualDuration(value:String) {
        val field=compose.onNodeWithTag("workout-actual-minutes")
        field.performTextReplacement(value)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    }

    @Test fun multiSessionPlanBuilderGeneratesEightSessionsAndKeepsSameDayLogsIndependent() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-multi-builder-ui"))
        val actions=PlanActions(
            generate={p->Engine1Engines.validate(p);state.value=state.value.copy(plans=state.value.plans+Engine1Engines.resolve(p).generate(p,"synthetic-multi-builder-ui"))},
            approve={id,_->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.approve(it,state.value.plans).copy(revision=it.revision+1)else it })},
            move={id,_,sid,date->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.move(it,sid,date,state.value.plans).copy(revision=it.revision+1)else it })},
            record={id,_,sid,log,done->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.record(it,sid,log,done).copy(revision=it.revision+1)else it })}
        )
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,false,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithText("Create a plan",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("discipline-cal").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"))
        compose.onNodeWithText("Continue",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("frequency-run-5").performClick()
        compose.onNodeWithTag("frequency-cal-3").performClick()
        compose.onNodeWithText("Multi-session scheduling",useUnmergedTree=true).performClick()
        repeat(1) {
            compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"))
            compose.onNodeWithText("Continue",useUnmergedTree=true).performClick()
        }
        compose.onNodeWithTag("available-day-3").performClick()
        compose.onNodeWithTag("available-day-7").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"))
        compose.onNodeWithText("Continue",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Generate plan"))
        compose.onNodeWithText("Generate plan",useUnmergedTree=true).performClick()
        compose.waitUntil(15000) { state.value.plans.isNotEmpty() }
        val draft=state.value.plans.single()
        assertEquals(MULTI_SESSION_POLICY,draft.policyId)
        assertEquals(8*draft.request.weeks,draft.sessions.size)
        val firstWeek=draft.sessions.filter { it.date in draft.request.start..draft.request.start.plusDays(6) }
        assertEquals(8,firstWeek.size)
        assertEquals(7,firstWeek.map { it.date }.distinct().size)
        compose.onNodeWithTag("screen-title").assertTextEquals("Your plan")
        compose.onNodeWithText("Multi-session scheduling").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Approve plan"))
        compose.onNodeWithText("Approve plan",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Training")
        val approved=state.value.plans.single()
        val sameDay=firstWeek.groupBy { it.date }.values.single { it.size==2 }
        val run=sameDay.single { it.discipline=="run" }
        val cal=sameDay.single { it.discipline=="cal" }
        compose.onNodeWithTag("calendar-day-${run.date}").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${run.id}"))
        compose.onNodeWithTag("workout-${run.id}").assertExists()
        compose.onNodeWithTag("workout-${cal.id}").assertExists()
        compose.onNodeWithText("View run plan and logging options →",useUnmergedTree=true).assertExists()

        compose.onNodeWithTag("workout-${cal.id}").performClick()
        compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Move workout"))
        compose.onNodeWithText("Move workout",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Scheduled date (YYYY-MM-DD)").performTextReplacement(
            firstWeek.single { it.discipline=="run"&&it.kind=="long" }.date.toString()
        )
        compose.onNodeWithText("Save move",useUnmergedTree=true).performClick()
        assertEquals(run.date,state.value.plans.single().sessions.single { it.id==cal.id }.date)
        assertNotNull(state.value.plans.single().sessions.single { it.id==cal.id })
        compose.onAllNodesWithText("This move conflicts with another workout or its recovery spacing. Choose a different date.").assertCountEquals(2)
        compose.onNodeWithText("Cancel",useUnmergedTree=true).performClick()

        compose.onNodeWithTag("screen").performScrollToNode(hasText("Back to Training"))
        compose.onNodeWithText("Back to Training",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("workout-${run.id}").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        enterActualDuration("20")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        compose.onNodeWithText("Save completed workout",useUnmergedTree=true).performClick()
        assertEquals("completed",state.value.plans.single().sessions.single { it.id==run.id }.status)
        assertEquals("planned",state.value.plans.single().sessions.single { it.id==cal.id }.status)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Back to Training"))
        compose.onNodeWithText("Back to Training",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("workout-${cal.id}").performClick()
        compose.onNodeWithTag("workout-player").assertExists()
        val calExercise=state.value.plans.single().sessions.single { it.id==cal.id }.effectiveExercises.first { it.reps!=null }
        val actual=calExercise.reps!!+1
        compose.onNodeWithText("Adjust set").performClick()
        compose.onNodeWithText("Actual reps").performScrollTo().performTextInput(actual.toString())
        compose.onNodeWithText("Use these values").performScrollTo().performClick()
        compose.onNodeWithText("Confirm $actual reps & rest").performClick()
        compose.waitUntil(5000) { state.value.plans.single().sessions.single { it.id==cal.id }.log?.sets?.isNotEmpty()==true }
        assertEquals("in_progress",state.value.plans.single().sessions.single { it.id==cal.id }.status)
        compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        enterActualDuration("25")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        compose.onNodeWithText("Save completed workout",useUnmergedTree=true).performClick()
        val completed=state.value.plans.single()
        assertEquals("completed",completed.sessions.single { it.id==run.id }.status)
        assertEquals("completed",completed.sessions.single { it.id==cal.id }.status)
        val runMinutes=completed.sessions.single { it.id==run.id }.log?.minutes
        val calMinutes=completed.sessions.single { it.id==cal.id }.log?.minutes
        assertNotNull("Actual run duration should be saved",runMinutes)
        assertNotNull("Actual calisthenics duration should be saved",calMinutes)
        assertEquals(20.0,runMinutes ?: 0.0,0.0)
        assertEquals(25.0,calMinutes ?: 0.0,0.0)
        assertEquals(actual,completed.sessions.single { it.id==cal.id }.log!!.sets.single().let { it.seconds ?: it.reps })
    }

    @Test fun beginnerGeneratesReviewsApprovesOpensCalendarAndLogsWithoutWatch() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-engine1-ui"))
        val planning=PlanActions(
            generate={p->state.value=state.value.copy(plans=listOf(Engine1Engines.resolve(p).generate(p,"synthetic-engine1-ui")))},
            approve={id,_->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.approve(it,emptyList()).copy(revision=it.revision+1)else it })},
            record={id,_,sid,log,done->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.record(it,sid,log,done).copy(revision=it.revision+1)else it })}
        )
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,false,NativeActions({},{},{},{},{_,_->},planning=planning)) } } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithText("Create a plan",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Choose disciplines")
        capture("phase1-builder-disciplines")
        repeat(3) { index -> if(index==1)compose.onNodeWithText("General strength").assertDoesNotExist();compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"));compose.onNodeWithText("Continue",useUnmergedTree=true).performClick() }
        compose.onNodeWithTag("screen-title").assertTextEquals("Review your choices")
        capture("phase1-builder-review")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Generate plan"));compose.onNodeWithText("Generate plan",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { state.value.plans.isNotEmpty() }
        compose.onNodeWithTag("screen-title").assertTextEquals("Your plan")
        assertEquals("draft",state.value.plans.single().status)
        capture("phase1-generated-preview")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Approve plan"));compose.onNodeWithText("Approve plan",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Training")
        assertEquals("approved",state.value.plans.single().status)
        compose.onNodeWithText("Month",useUnmergedTree=true).performClick()
        capture("phase1-month-calendar")
        val plan=state.value.plans.single();val session=plan.sessions.first()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${session.id}"));compose.onNodeWithTag("workout-${session.id}").performClick()
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Actual duration (minutes), optional"));enterActualDuration("18")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"));compose.onNodeWithText("Save completed workout",useUnmergedTree=true).performClick()
        assertEquals(1,state.value.plans.single().sessions.count { it.status=="completed" })
        assertEquals(18.0,state.value.plans.single().sessions.first().log!!.minutes!!,0.0)
        capture("phase1-saved-workout")
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("WORKOUT LOGS · ON THIS DEVICE"))
        compose.onNodeWithTag("workout-${session.id}").assertExists()
        capture("phase1-workout-history")
    }
    @Test fun fourRootsAndAcceptedLibraryModalRemainFunctional() {
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false),true,NativeActions({},{},{},{},{_,_->})) } } }
        compose.onNodeWithTag("nav-library").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("23 curated entries"));compose.onNodeWithText("23 curated entries").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Incline push-up — original schematic").fetchSemanticsNodes().isNotEmpty() }
        capture("phase1-library-modal")
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().performClick()
        listOf("home" to "Your training","training" to "Training","activities" to "Activities","library" to "Library").forEach { (tag,title) -> compose.onNodeWithTag("nav-$tag").performClick();compose.onNodeWithTag("screen-title").assertTextEquals(title) }
    }
    @Test fun calendarAndInstructionalModalReflowAtTwoHundredPercentText() {
        val request=PlanRequest(start=LocalDate.now(),runDays=0,strengthDays=2,calDays=2,days=setOf(1,2,4,6))
        val plan=Engine1.generate(request,"synthetic-large-ui").copy(status="approved")
        compose.runOnUiThread { compose.activity.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-large-ui",plans=listOf(plan)),false,NativeActions({},{},{},{},{_,_->})) }
            }
        } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithText("Month",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-calendar"))
        capture("phase1-calendar-200pct")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("PRIVATE TESTING FEEDBACK"))
        compose.onNodeWithText("PRIVATE TESTING FEEDBACK").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("23 curated entries"));compose.onNodeWithText("23 curated entries").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.onNodeWithText("MAKE IT EASIER").performScrollTo().assertIsDisplayed()
        capture("phase1-library-modal-200pct")
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
    }
    @Test fun acceptedLibraryFiltersFavoritesCollectionsAndSessionsWorkNatively() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-shelf-ui"))
        val actions=PlanActions(shelf={id,c,remove -> state.value=state.value.copy(shelf=state.value.shelf.change(id,c,remove)) })
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-library").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("23 curated entries"));compose.onNodeWithText("23 curated entries").assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Save favorite",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Collection name").performScrollTo().performTextInput("Home practice")
        compose.onNodeWithText("Add to collection",useUnmergedTree=true).performScrollTo().performClick()
        assertTrue("ip" in state.value.shelf.favorites);assertEquals(setOf("ip"),state.value.shelf.collections["Home practice"])
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Saved"));compose.onNodeWithText("Saved",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up ★"));compose.onNodeWithText("Incline push-up ★").assertExists()
        capture("phase1-saved-library")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Sessions"));compose.onNodeWithText("Sessions",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("No workout logs saved for this account yet. Start with an approved training plan."))
    }
    @Test fun workoutAlternativeRequiresReviewAndKeepsOriginalTarget() {
        val p=PlanOperations.approve(Engine1.generate(PlanRequest(start=LocalDate.now(),weeks=1,runDays=0,strengthDays=2,days=(1..7).toSet()),"synthetic-swap-ui"),emptyList())
        val state=mutableStateOf(TrainingState(initializing=false,account=p.owner,plans=listOf(p)))
        val actions=PlanActions(swap={id,_,sid,ex->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.swapExercise(it,sid,ex).copy(revision=it.revision+1)else it })})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${p.sessions.first().id}"));compose.onNodeWithTag("workout-${p.sessions.first().id}").performClick()
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Choose an easier alternative"));compose.onNodeWithText("Choose an easier alternative",useUnmergedTree=true).performClick()
        assertTrue(state.value.plans.single().sessions.first().swaps.isEmpty())
        compose.onNodeWithText("Use easier alternative",useUnmergedTree=true).performClick()
        val session=state.value.plans.single().sessions.first();assertEquals(p.sessions.first().exercises,session.exercises);assertEquals(1,session.swaps.size)
        compose.onNodeWithTag("screen").performScrollToNode(hasText(session.effectiveExercises.first().name));compose.onNodeWithText(session.effectiveExercises.first().name).assertExists()
        capture("phase1-reviewed-workout-alternative")
    }
    @Test fun libraryPracticeCreatesReviewedDraftAndReusableTemplateBeforeApproval() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-practice-ui"))
        val actions=PlanActions(saveTemplate={t->state.value=state.value.copy(templates=listOf(t))},prepareSession={t,p,d->state.value=state.value.copy(plans=listOf(t.draft("synthetic-practice-ui",p,d)))},approve={id,_->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.approve(it,emptyList())else it })})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-library").performClick();compose.waitUntil(10000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Bodyweight squat");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("library-entry-sq"));compose.onNodeWithTag("library-entry-sq").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("TECHNIQUE & STRUCTURE").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Prepare practice session",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Prepare a session")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save reusable template"));compose.onNodeWithText("Save reusable template",useUnmergedTree=true).performClick();assertEquals(1,state.value.templates.size)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Create practice draft"));compose.onNodeWithText("Create practice draft",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { state.value.plans.isNotEmpty() };compose.onNodeWithTag("screen-title").assertTextEquals("Practice preview");assertEquals("draft",state.value.plans.single().status)
        capture("phase1-practice-preview")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Approve practice session"));compose.onNodeWithText("Approve practice session",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Training");assertEquals("approved",state.value.plans.single().status)
    }
    @Test fun runningPrimaryAndStrengthSecondaryGoalProduceActualDifferentPrescriptions() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-goals-ui"))
        val actions=PlanActions(generate={p->state.value=state.value.copy(plans=listOf(Engine1Engines.resolve(p).generate(p,"synthetic-goals-ui")))})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick();compose.onNodeWithText("Create a plan",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Strength"));compose.onNodeWithText("Strength",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"));compose.onNodeWithText("Continue",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("goal-str-Muscle development"));compose.onNodeWithTag("goal-str-Muscle development").performClick()
        repeat(2) {compose.onNodeWithTag("screen").performScrollToNode(hasText("Continue"));compose.onNodeWithText("Continue",useUnmergedTree=true).performClick()}
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Generate plan"));compose.onNodeWithText("Generate plan",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { state.value.plans.isNotEmpty() };val plan=state.value.plans.single();assertEquals(CoordinatedEngine1.policyId,plan.policyId)
        assertTrue(plan.sessions.any { it.discipline=="run" });assertTrue(plan.sessions.filter { it.discipline=="str" }.flatMap { it.exercises }.filter { it.reps!=null }.all { it.reps==10 })
        capture("phase1-coordinated-goals-preview")
    }
    @Test fun savedEducationalGuideRemainsVisibleWithoutBecomingAnExecutableWorkout() {
        val guide=readLibrary(InstrumentationRegistry.getInstrumentation().targetContext).first { it.guide }
        val state=TrainingState(initializing=false,account="synthetic-guide-ui",shelf=LibraryShelf(favorites=setOf(guide.id)))
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state,true,NativeActions({},{},{},{},{_,_->})) } } }
        compose.onNodeWithTag("nav-library").performClick();compose.waitUntil(10000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Saved"));compose.onNodeWithText("Saved",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("library-entry-${guide.id}"));compose.onNodeWithTag("library-entry-${guide.id}").performClick()
        compose.onNodeWithText("Educational reference. This is not an executable workout.").performScrollTo().assertExists()
        compose.onNodeWithText("Prepare practice session").assertDoesNotExist();compose.onNodeWithText("Build a running plan").assertDoesNotExist()
    }
    @Test fun switchingTabsRestoresUnconfirmedWorkoutEntryWithoutMarkingItPerformed() {
        val plan=PlanOperations.approve(Engine1.generate(PlanRequest(start=LocalDate.now(),weeks=1,runDays=0,strengthDays=1,days=(1..7).toSet()),"synthetic-entry-ui"),emptyList());val session=plan.sessions.first()
        val state=mutableStateOf(TrainingState(initializing=false,account=plan.owner,plans=listOf(plan)))
        val actions=PlanActions(stageWorkoutDraft={draft -> state.value=state.value.copy(workoutDrafts=state.value.workoutDrafts+(draft.key to draft))})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{},{},{},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick();compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${session.id}"));compose.onNodeWithTag("workout-${session.id}").performClick()
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("How did it feel?"));compose.onNodeWithText("How did it feel?").performTextInput("Unconfirmed entry survives tab change")
        compose.onNodeWithTag("nav-home").performClick()
        compose.waitUntil(10000) { state.value.workoutDrafts.isNotEmpty() }
        assertNull(state.value.plans.single().sessions.first().log);assertEquals("planned",state.value.plans.single().sessions.first().status)
        compose.onNodeWithTag("nav-training").performClick();compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${session.id}"));compose.onNodeWithTag("workout-${session.id}").performClick()
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("How did it feel?"));compose.onNodeWithText("Unconfirmed entry survives tab change").assertExists()
        capture("phase1-restored-entry-draft")
    }
    private fun capture(name:String) {
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle();compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(350)
        val folder=File(compose.activity.getExternalFilesDir(null),"native-ui-evidence");folder.mkdirs()
        File(folder,"$name.png").outputStream().use { assertTrue(checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
}
