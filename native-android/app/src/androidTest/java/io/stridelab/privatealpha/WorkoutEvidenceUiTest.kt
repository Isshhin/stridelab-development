package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class WorkoutEvidenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = "evidence-ui-${UUID.randomUUID()}"
    private val database = "$fixture.db"
    private val today = LocalDate.now()
    private var repository: TrainingRepository? = null

    @After fun cleanup() {
        repository?.close()
        context.deleteDatabase(database)
        context.deleteDatabase("$database-manual")
        context.deleteSharedPreferences(fixture)
    }

    @Test fun trainingOpensFilteredHistoryAndShowsConfirmedProgressAcrossSources() {
        val (initial, access) = openRepository()
        val owner = requireNotNull(initial.freeformOwner)
        val at = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        val imported = ActivityRecord("history-garmin", "Park loop", "running", "garmin", at,
            5000.0, 1800.0, null, null, null, null)
        val session = PlannedSession("history-coach", today, "run", "Coached loop", 45, 9.0, "easy",
            status = "completed", log = WorkoutLog(at, minutes = 30.0, distanceKm = 5.0),
            linkedActivityId = imported.id)
        val plan = PlanSnapshot("history-plan", owner, ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            at, PlanRequest(start = today), listOf(session), emptyList(), status = "approved")
        val originalPlan = plan.encode().toString()
        val manual = runBlocking {
            val first = logLift(access, owner, today.minusDays(1), "6")
            val template = ManualWorkoutTemplate.empty(owner, ManualDiscipline.STRENGTH)
                .copy(name = "Planned strength")
            val schedule = CustomSchedule.create(template, ScheduleRule(today), at.toEpochMilli())
            access.saveSchedule(schedule)
            val draft = access.startCustom(owner, CustomOccurrenceKey(schedule.id, today), today)
            val newer = access.finish(draft.copy(exercises = listOf(lift("8")), modifiedAt = draft.modifiedAt + 1))
            first.id to newer.id
        }
        val snapshot = mutableStateOf(initial.copy(plans = listOf(plan), activities = listOf(imported),
            localGarminIds = setOf(imported.id), groupedActivitySummaries = null))
        compose.setContent {
            StrideTheme { NativeStrideContent(snapshot.value, false, NativeActions({}, { _, _ -> }, freeform = access)) }
        }
        openHistory()
        val list = node("workout-evidence")
        val linkedTag = "evidence-import:${imported.id}"
        await(linkedTag).assert(hasText("Imported · Coached", substring = true))
        val customTag = "evidence-manual:${manual.second}"
        list.performScrollToNode(hasTestTag(customTag))
        node(customTag).assert(hasText("Custom · Strength", substring = true))
        list.performScrollToNode(hasTestTag("workout-evidence-source"))
        node("workout-evidence-source").performClick()
        compose.onNodeWithText("Freeform").performClick()
        val freeformTag = "evidence-manual:${manual.first}"
        list.performScrollToNode(hasTestTag(freeformTag))
        node(freeformTag).assert(hasText("6 reps", substring = true)).performClick()
        await("freeform-history-summary").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        list.performScrollToNode(hasTestTag("workout-evidence-view-progress"))
        node("workout-evidence-view-progress").performClick()
        list.performScrollToNode(hasText("3 completed workouts recorded"))
        list.performScrollToNode(hasText("Latest", substring = true) and hasText("8 reps", substring = true))
        list.performScrollToNode(hasText("Previous", substring = true) and hasText("6 reps", substring = true))
        assertEquals(originalPlan, plan.encode().toString())

        compose.runOnIdle {
            snapshot.value = initial.copy(account = "another-signed-in-profile", loginGeneration = initial.loginGeneration + 1)
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("No completed workouts in this view.").fetchSemanticsNodes().isNotEmpty()
        }
        node(freeformTag).assertDoesNotExist()
        node(customTag).assertDoesNotExist()
        node(linkedTag).assertDoesNotExist()
    }

    @Test fun nativeBuilderLeavesLoggedBaselineSuggestionsEditableAndRequiresAnExplicitChoice() {
        val (initial, access) = openRepository()
        val owner = requireNotNull(initial.freeformOwner)
        val monday = tracksideCalendarMonday(today)
        runBlocking {
            logRun(access, owner, monday.minusWeeks(1), "8")
            logRun(access, owner, monday.minusWeeks(2), "10")
        }
        compose.setContent {
            StrideTheme { NativeStrideContent(initial, false, NativeActions({}, { _, _ -> }, freeform = access)) }
        }
        node("nav-training").performClick()
        compose.onNodeWithText("Create training plan").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        val focus = await("discipline-run").performScrollTo()
        if (!focus.fetchSemanticsNode().config[SemanticsProperties.Selected]) focus.performClick()
        next()
        await("builder-option-custom").performScrollTo().performClick()
        next()
        slider("weekly", 0f)
        slider("longest", 0f)
        await("builder-use-history-weekly").performScrollTo().performClick()
        slider("weekly", 9f)
        slider("longest", 0f)
        await("builder-use-history-longest").performScrollTo().performClick()
        slider("longest", 10f)
        node("builder-ability-weekly-slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(11f) }
        slider("weekly", 11f)
        assertEquals(emptyList<PlanSnapshot>(), repository?.state?.value?.plans)
    }

    private fun openRepository(): Pair<TrainingState, FreeformAccess> {
        val repo = TrainingRepository(context, databaseName = database, preferencesName = fixture)
        repository = repo
        val state = runBlocking { withTimeout(20_000) { repo.state.first { !it.initializing } } }
        return state to repo.freeformAccess()
    }

    private fun lift(reps: String) = FreeformExercise(name = "Goblet squat", sets = listOf(
        FreeformSet(target = FreeformMeasure(quantity = "15", kg = "50"),
            actual = FreeformMeasure(quantity = reps, kg = "12"), completed = true)
    ))

    private suspend fun logLift(access: FreeformAccess, owner: String, date: LocalDate, reps: String): FreeformWorkout {
        val saved = access.start(FreeformWorkout.start(owner, ManualDiscipline.STRENGTH, date, Instant.now().toEpochMilli()))
        return access.finish(saved.copy(workoutName = "Evening lift", exercises = listOf(lift(reps)), modifiedAt = saved.modifiedAt + 1))
    }

    private suspend fun logRun(access: FreeformAccess, owner: String, date: LocalDate, km: String) {
        val saved = access.start(FreeformWorkout.start(owner, ManualDiscipline.RUNNING, date, Instant.now().toEpochMilli()))
        access.finish(saved.copy(distanceKm = km, durationMinutes = "60", modifiedAt = saved.modifiedAt + 1))
    }

    private fun openHistory() {
        node("nav-training").performClick()
        await("training-workout-history").assertIsDisplayed().performClick()
        await("workout-evidence")
    }

    private fun next() = await("builder-figma-continue").performClick()

    private fun slider(field: String, value: Float) {
        val slider = node("builder-ability-$field-slider").performScrollTo()
        assertEquals(value, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun await(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return node(tag)
    }
}
