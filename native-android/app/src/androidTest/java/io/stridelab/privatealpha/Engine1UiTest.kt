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
    private fun openLibrary(state: TrainingState = TrainingState(initializing = false), actions: PlanActions = PlanActions(), onTemplate: (String) -> Unit = {}) {
        compose.runOnUiThread {
            compose.activity.setContent { StrideTheme { LibraryScreen(state, actions, onTemplate = onTemplate) } }
        }
        compose.waitUntil(30000) { compose.onAllNodesWithTag("library-ready").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openPlanBuilder() {
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline")
            .performScrollToNode(hasTestTag("training-create-plan"))
        compose.onNodeWithTag("training-create-plan").performClick()
    }
    private fun openPlannedWorkout(id: String) {
        val timeline=compose.onNodeWithTag("training-unified-timeline")
        timeline.performScrollToNode(hasTestTag("timeline-planned-$id"))
        // The fixed floating dock overlays the lower edge of the scroll viewport.
        timeline.performTouchInput { swipeUp(startY=height*.8f,endY=height*.35f) }
        compose.onNodeWithTag("timeline-planned-$id").assertIsDisplayed().performClick()
    }
    private fun workoutScreen(title: String) = compose.onNode(
        hasTestTag("screen") and SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle,title)
    )
    private fun scrollWorkoutUntil(title: String, matcher: SemanticsMatcher) {
        workoutScreen(title).performScrollToNode(matcher)
        compose.onNode(matcher).assertIsDisplayed()
    }
    private fun openWorkoutLogForm(plannedRun: Boolean, title: String) {
        if(plannedRun) {
            scrollWorkoutUntil(title,hasTestTag("workout-log-toggle"))
            compose.onNodeWithTag("workout-log-toggle").performClick()
        }
        scrollWorkoutUntil(title,hasTestTag("workout-record-form"))
    }
    private fun enterActualDuration(value:String) {
        val field=compose.onNodeWithTag("workout-actual-minutes")
        field.performTextReplacement(value)
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    }

    @Test fun multiSessionPlanBuilderKeepsSameDayLogsIndependent() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-multi-builder-ui"))
        val actions=PlanActions(
            generate={p->Engine1Engines.validate(p);state.value=state.value.copy(plans=state.value.plans+Engine1Engines.resolve(p).generate(p,"synthetic-multi-builder-ui"))},
            approve={id,_->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.approve(it,state.value.plans).copy(revision=it.revision+1)else it })},
            move={id,_,sid,date->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.move(it,sid,date,state.value.plans).copy(revision=it.revision+1)else it })},
            record={id,_,sid,log,done->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.record(it,sid,log,done).copy(revision=it.revision+1)else it })}
        )
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,false,NativeActions({},{_,_->},planning=actions)) } } }
        openPlanBuilder()
        compose.onNodeWithTag("discipline-cal").performClick()
        fun next()=compose.onNodeWithTag("builder-figma-continue").performClick()
        next() // disciplines
        next() // running goal
        next() // race goal
        next() // running ability
        compose.onNodeWithTag("builder-run-plus").performClick()
        compose.onNodeWithTag("builder-run-plus").performClick()
        compose.onNodeWithTag("builder-running-day-1").performClick()
        compose.onNodeWithTag("builder-running-advanced-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("builder-running-decide").performScrollTo().performClick()
        next() // running setup
        next() // cross-training goals
        next() // calisthenics setup
        next() // plan dates
        compose.onNodeWithTag("available-day-7").performScrollTo().performClick()
        compose.onNodeWithTag("builder-advanced-scheduling").performScrollTo().performClick()
        compose.onNodeWithTag("builder-enable-multi-session").performScrollTo().performClick()
        next() // availability
        compose.waitUntil(15000) { state.value.plans.isNotEmpty() || compose.onAllNodesWithTag("plan-form-error").fetchSemanticsNodes().isNotEmpty() }
        assertTrue("Plan builder failed: "+compose.onRoot().printToString(),state.value.plans.isNotEmpty())
        val draft=state.value.plans.single()
        assertEquals(if(BuildConfig.ENGINE15_ACTIVE)ENGINE15_MULTI_POLICY else MULTI_SESSION_POLICY,draft.policyId)
        assertEquals(7*draft.request.weeks,draft.sessions.size)
        val firstWeek=draft.sessions.filter { it.date in draft.request.start..draft.request.start.plusDays(6) }
        assertEquals(7,firstWeek.size)
        assertEquals(5,firstWeek.map { it.date }.distinct().size)
        compose.onNodeWithText("Review your training plan").assertExists()
        compose.onNodeWithText("Finalize Plan").assertExists()
        next()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        val approved=state.value.plans.single()
        val sameDay=firstWeek.groupBy { it.date }.values.first { it.size==2 }
        val run=sameDay.single { it.discipline=="run" }
        val cal=sameDay.single { it.discipline=="cal" }
        compose.onNodeWithTag("calendar-day-${run.date}").performClick()
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("timeline-planned-${run.id}"))
        compose.onNodeWithTag("timeline-planned-${run.id}").assertExists()
        compose.onNodeWithTag("timeline-planned-${cal.id}").assertExists()

        openPlannedWorkout(cal.id)
        compose.onNodeWithTag("workout-review").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Move workout"))
        compose.onNodeWithText("Move workout",useUnmergedTree=true).performClick()
        // The approved move sheet previews a destination before entering the
        // separate coaching-checks review screen. No move is saved on failure.
        val conflictingDay = firstWeek.single { it.discipline=="run" && it.kind=="long" }.date
        compose.onNodeWithTag("move-destination-$conflictingDay").performClick()
        compose.onNodeWithTag("move-review").performClick()
        compose.onNodeWithTag("move-review").performClick()
        compose.onNodeWithTag("review-run-checks").performClick()
        assertEquals(run.date,state.value.plans.single().sessions.single { it.id==cal.id }.date)
        assertNotNull(state.value.plans.single().sessions.single { it.id==cal.id })
        compose.onNodeWithText(
            "This move conflicts with another workout or its recovery spacing. Choose a different date.",
            substring=true
        ).assertExists()
        compose.onNodeWithTag("plan-flow-back").performClick()
        compose.onNodeWithText("Cancel",useUnmergedTree=true).performClick()

        compose.onNodeWithTag("screen").performScrollToNode(hasText("Back to Training"))
        compose.onNodeWithText("Back to Training",useUnmergedTree=true).performClick()
        openPlannedWorkout(run.id)
        openWorkoutLogForm(plannedRun=true,title="Workout details")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        enterActualDuration("20")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"))
        compose.onNodeWithText("Save completed workout",useUnmergedTree=true).performClick()
        assertEquals("completed",state.value.plans.single().sessions.single { it.id==run.id }.status)
        assertEquals("planned",state.value.plans.single().sessions.single { it.id==cal.id }.status)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Back to Training"))
        compose.onNodeWithText("Back to Training",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("timeline-planned-${cal.id}").performClick()
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
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,false,NativeActions({},{_,_->},planning=planning)) } } }
        openPlanBuilder()
        compose.onNodeWithText("Choose your",substring=true).assertExists()
        capture("phase1-builder-disciplines")
        fun next()=compose.onNodeWithTag("builder-figma-continue").performClick()
        repeat(5) { next() } // disciplines, running goal, race goal, ability, running schedule
        compose.onNodeWithText("Set your plan dates").assertExists()
        compose.onNodeWithText("Generate My Plan").performClick()
        compose.waitUntil(10000) { state.value.plans.isNotEmpty() }
        compose.onNodeWithText("Review your training plan").assertExists()
        compose.onNodeWithText("Finalize Plan").assertExists()
        capture("phase1-builder-review")
        assertTrue("The builder should save a draft before the final review",state.value.plans.singleOrNull()?.status=="draft")
        capture("phase1-generated-preview")
        compose.onNodeWithText("Finalize Plan").performClick()
        compose.waitUntil(10000) { state.value.plans.singleOrNull()?.status=="approved" }
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        assertEquals("approved",state.value.plans.single().status)
        val plan=state.value.plans.single();val session=plan.sessions.first { it.status=="planned" && it.discipline=="run" }
        capture("phase1-week-calendar")
        // A rest day may be selected by default; explicitly choose the workout date.
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("calendar-day-${session.date}"))
        compose.onNodeWithTag("calendar-day-${session.date}").performClick()
        openPlannedWorkout(session.id)
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        openWorkoutLogForm(plannedRun=true,title="Workout details")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Actual duration (minutes), optional"));enterActualDuration("18")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Save completed workout"));compose.onNodeWithText("Save completed workout",useUnmergedTree=true).performClick()
        assertEquals(1,state.value.plans.single().sessions.count { it.status=="completed" })
        assertEquals(18.0,state.value.plans.single().sessions.single { it.id==session.id }.log!!.minutes!!,0.0)
        capture("phase1-saved-workout")
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("timeline-planned-${session.id}"))
        compose.onNodeWithTag("timeline-planned-${session.id}").assertExists()
        capture("phase1-workout-history")
    }
    @Test fun threePrimaryDestinationsAndAcceptedLibraryModalRemainFunctional() {
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-roots-ui"),true,NativeActions({},{_,_->})) } } }
        openLibrary()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("curated entries",substring=true));compose.onNodeWithText("curated entries",substring=true).assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("TECHNIQUE & STRUCTURE").fetchSemanticsNodes().isNotEmpty() }
        capture("phase1-library-modal")
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().performClick()
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-roots-ui"),true,NativeActions({},{_,_->})) } } }
        listOf("home" to "Today","training" to "Training","settings" to "More").forEach { (tag,title) ->
            compose.onNodeWithTag("nav-$tag").performClick()
            when(tag) {
                "home" -> compose.onNodeWithTag("today-week-swipe").assertExists()
                "training" -> compose.onNodeWithTag("training-unified-timeline").assertExists()
                else -> compose.onNodeWithTag("screen-title").assertTextEquals(title)
            }
        }
        compose.onNodeWithTag("nav-library").assertDoesNotExist()
    }
    @Test fun calendarAndInstructionalModalReflowAtTwoHundredPercentText() {
        val request=PlanRequest(start=LocalDate.now(),runDays=0,strengthDays=2,calDays=2,days=setOf(1,2,4,6))
        val plan=Engine1.generate(request,"synthetic-large-ui").copy(status="approved")
        compose.runOnUiThread { compose.activity.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,2f)) {
                StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-large-ui",plans=listOf(plan)),false,NativeActions({},{_,_->})) }
            }
        } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline").performScrollToNode(hasTestTag("timeline-day-${LocalDate.now()}"))
        capture("phase1-calendar-200pct")
        compose.onNodeWithTag("training-plan-tools").assertExists()
        openLibrary()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("curated entries",substring=true));compose.onNodeWithText("curated entries",substring=true).assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.onNodeWithText("MAKE IT EASIER").performScrollTo().assertIsDisplayed()
        capture("phase1-library-modal-200pct")
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
    }
    @Test fun acceptedLibraryFiltersFavoritesCollectionsAndSessionsWorkNatively() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-shelf-ui"))
        val actions=PlanActions(shelf={id,c,remove -> state.value=state.value.copy(shelf=state.value.shelf.change(id,c,remove)) })
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{_,_->},planning=actions)) } } }
        openLibrary(state.value, actions)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("curated entries",substring=true));compose.onNodeWithText("curated entries",substring=true).assertExists()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Search exercises"));compose.onNodeWithText("Search exercises").performTextInput("Incline");compose.onNodeWithText("Search exercises").performImeAction()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up"));compose.onNodeWithText("Incline push-up",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Save favorite",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Collection name").performScrollTo().performTextInput("Home practice")
        compose.onNodeWithText("Add to collection",useUnmergedTree=true).performScrollTo().performClick()
        assertTrue("ip" in state.value.shelf.favorites);assertEquals(setOf("ip"),state.value.shelf.collections["Home practice"])
        compose.onNodeWithText("Close",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Saved"));compose.onNodeWithText("Saved",useUnmergedTree=true).performClick()
        openLibrary(state.value, actions)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Incline push-up ★"));compose.onNodeWithText("Incline push-up ★").assertExists()
        capture("phase1-saved-library")
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Sessions"));compose.onNodeWithText("Sessions",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasText("No workout logs saved for this account yet. Start with an approved training plan."))
    }
    @Test fun workoutAlternativeRequiresReviewAndKeepsOriginalTarget() {
        val generated=Engine1.generate(PlanRequest(start=LocalDate.now(),weeks=1,runDays=0,strengthDays=2,days=(1..7).toSet()),"synthetic-swap-ui")
        val target=generated.sessions.first { session -> session.status=="planned" && session.exercises.any { ExerciseAlternatives.easier(it)!=null } }
        val p=PlanOperations.approve(generated.copy(sessions=generated.sessions.map { if(it.id==target.id) it.copy(exercises=listOf(it.exercises.first { ex -> ExerciseAlternatives.easier(ex)!=null })) else it }),emptyList())
        val state=mutableStateOf(TrainingState(initializing=false,account=p.owner,plans=listOf(p)))
        val actions=PlanActions(swap={id,_,sid,ex->state.value=state.value.copy(plans=state.value.plans.map { if(it.id==id)PlanOperations.swapExercise(it,sid,ex).copy(revision=it.revision+1)else it })})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick()
        openPlannedWorkout(target.id)
        assertTrue("The test fixture must be an approved, editable plan",Engine1Engines.canManage(p) && p.status=="approved" && target.status=="planned")
        compose.onNodeWithTag("workout-player").assertExists(compose.onRoot().printToString())
        compose.onNodeWithTag("workout-review").performClick()
        scrollWorkoutUntil(target.effectiveTitle,hasText("Choose an easier alternative"))
        compose.onNodeWithText("Choose an easier alternative").assertIsDisplayed().performClick()
        compose.onNodeWithText("Review easier alternative").assertExists()
        assertTrue(state.value.plans.single().sessions.single { it.id==target.id }.swaps.isEmpty())
        compose.onNodeWithText("Use easier alternative",useUnmergedTree=true)
            .assertExists().performClick()
        val session=state.value.plans.single().sessions.single { it.id==target.id };assertEquals(p.sessions.single { it.id==target.id }.exercises,session.exercises);assertEquals(1,session.swaps.size)
        compose.onNodeWithText(session.effectiveExercises.first().name).performScrollTo().assertExists()
        capture("phase1-reviewed-workout-alternative")
    }
    @Test fun retiredExerciseLibraryIsNotReachableFromMoreOrTraining() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        TrainingState(initializing=false,account="synthetic-practice-ui"),
                        true,NativeActions({},{_,_->})
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        compose.onNodeWithTag("nav-library").assertDoesNotExist()
        compose.onNodeWithText("Exercise Library").assertDoesNotExist()
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
    }
    @Test fun runningPrimaryAndStrengthSecondaryGoalProduceActualDifferentPrescriptions() {
        val state=mutableStateOf(TrainingState(initializing=false,account="synthetic-goals-ui"))
        val actions=PlanActions(generate={p->state.value=state.value.copy(plans=listOf(Engine1Engines.resolve(p).generate(p,"synthetic-goals-ui")))})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{_,_->},planning=actions)) } } }
        openPlanBuilder()
        // The frozen Trackside wizard collects one discipline at a time. The old
        // combined goal chip page and coordinated-only policy are no longer its entry point.
        fun next() = compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("discipline-str").performClick()
        repeat(5) { next() } // disciplines, running goal, race, ability, weekly setup
        compose.onNodeWithTag("builder-strength-goal-1").performScrollTo().performClick()
        repeat(3) { next() } // strength goal, strength setup, cross-training
        compose.onNodeWithText("Set your plan dates").assertExists()
        compose.onNodeWithText("Generate My Plan").performClick()
        compose.waitUntil(10000) { state.value.plans.isNotEmpty() }
        val plan=state.value.plans.single()
        compose.onNodeWithText("Review your training plan").assertExists()
        compose.onNodeWithText("Finalize Plan").assertExists()
        assertEquals("draft",plan.status)
        assertEquals(if(BuildConfig.ENGINE15_ACTIVE) ENGINE15_POLICY else CoordinatedEngine1.policyId, plan.policyId)
        assertEquals("Muscle development", plan.request.disciplineGoals["str"])
        assertTrue(plan.sessions.any { it.discipline=="run" });assertTrue(plan.sessions.filter { it.discipline=="str" }.flatMap { it.exercises }.filter { it.reps!=null }.all { it.reps==10 })
        capture("phase1-coordinated-goals-preview")
    }
    @Test fun savedEducationalGuideRemainsVisibleWithoutBecomingAnExecutableWorkout() {
        val guide=readLibrary(InstrumentationRegistry.getInstrumentation().targetContext).first { it.guide }
        val state=TrainingState(initializing=false,account="synthetic-guide-ui",shelf=LibraryShelf(favorites=setOf(guide.id)))
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state,true,NativeActions({},{_,_->})) } } }
        openLibrary(state)
        compose.onNodeWithTag("screen").performScrollToNode(hasText("Saved"));compose.onNodeWithText("Saved",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("library-entry-${guide.id}"));compose.onNodeWithTag("library-entry-${guide.id}").performClick()
        compose.onNodeWithText("Educational reference.",substring=true).performScrollTo().assertExists()
        compose.onNodeWithText("Prepare practice session").assertDoesNotExist();compose.onNodeWithText("Build a running plan").assertDoesNotExist()
    }
    @Test fun switchingTabsRestoresUnconfirmedWorkoutEntryWithoutMarkingItPerformed() {
        val plan=PlanOperations.approve(Engine1.generate(PlanRequest(start=LocalDate.now(),weeks=1,runDays=0,strengthDays=1,days=(1..7).toSet()),"synthetic-entry-ui"),emptyList());val session=plan.sessions.single { it.status=="planned" && it.discipline!="rest" && it.exercises.isNotEmpty() }
        val state=mutableStateOf(TrainingState(initializing=false,account=plan.owner,plans=listOf(plan)))
        val actions=PlanActions(stageWorkoutDraft={draft -> state.value=state.value.copy(workoutDrafts=state.value.workoutDrafts+(draft.key to draft))})
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(state.value,true,NativeActions({},{_,_->},planning=actions)) } } }
        compose.onNodeWithTag("nav-training").performClick();openPlannedWorkout(session.id)
        compose.onNodeWithTag("workout-player").assertExists(compose.onRoot().printToString())
        compose.onNodeWithTag("workout-review").performClick()
        openWorkoutLogForm(plannedRun=false,title=session.effectiveTitle)
        compose.onNodeWithText("How did it feel?").performTextInput("Unconfirmed entry survives tab change")
        compose.onNodeWithTag("nav-home").performClick()
        compose.waitUntil(10000) { state.value.workoutDrafts.isNotEmpty() }
        assertNull(state.value.plans.single().sessions.single { it.id==session.id }.log);assertEquals("planned",state.value.plans.single().sessions.single { it.id==session.id }.status)
        compose.onNodeWithTag("nav-training").performClick();openPlannedWorkout(session.id)
        if(compose.onAllNodesWithTag("workout-player").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithTag("workout-review").performClick()
        openWorkoutLogForm(plannedRun=false,title=session.effectiveTitle)
        compose.onNodeWithText("Unconfirmed entry survives tab change").performScrollTo().assertExists()
        capture("phase1-restored-entry-draft")
    }
    private fun capture(name:String) {
        compose.mainClock.advanceTimeBy(600);compose.waitForIdle();compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(350)
        val folder=File(compose.activity.getExternalFilesDir(null),"native-ui-evidence");folder.mkdirs()
        File(folder,"$name.png").outputStream().use { assertTrue(checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
}
