package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.ComponentActivity
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

    @Test fun trainingOmitsRetiredHistoryButtonWhileRecentActivitiesRemainOnToday() {
        val (initial, access) = openRepository()
        val at = today.atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant()
        val imported = ActivityRecord(
            "history-garmin", "Park loop", "running", "garmin", at,
            5000.0, 1800.0, null, null, null, null
        )
        val snapshot = initial.copy(
            activities = listOf(imported),
            localGarminIds = setOf(imported.id),
            groupedActivitySummaries = null
        )
        compose.setContent {
            StrideTheme { NativeStrideContent(snapshot, false, NativeActions({}, { _, _ -> }, freeform = access)) }
        }
        await("today-recent-activities").assertIsDisplayed()
        node("nav-training").performClick()
        await("training-unified-timeline").assertIsDisplayed()
        compose.onAllNodesWithTag("training-workout-history").assertCountEquals(0)
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

    private suspend fun logRun(access: FreeformAccess, owner: String, date: LocalDate, km: String) {
        val saved = access.start(FreeformWorkout.start(owner, ManualDiscipline.RUNNING, date, Instant.now().toEpochMilli()))
        access.finish(saved.copy(distanceKm = km, durationMinutes = "60", modifiedAt = saved.modifiedAt + 1))
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
