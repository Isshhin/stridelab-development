package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.Instant

/** Synthetic display-only history, no backend writes or real athlete claims. */
class ActivityIntelligenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun analysisCheckpointsScrollWithinOneContinuousSheet() {
        val tab = mutableIntStateOf(0)
        val run = ActivityRecord(
            "analysis-tabs", "Recorded run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), 5000.0, 1800.0, 1750.0,
            360.0, 145.0, 0.0
        )
        val recorded = ActivityDetail(
            run.id,
            listOf(
                ActivitySample(0, 0.0, 400.0, 140.0, 10.0, null, null, 170.0),
                ActivitySample(1, 30.0, 390.0, 150.0, 12.0, null, null, 174.0),
                ActivitySample(2, 60.0, 380.0, 160.0, 15.0, null, null, 176.0)
            ),
            listOf(ActivityLap(0, 1000.0, 400.0, 400.0, 140.0)), 0L
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = recorded, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = TrainingState(initializing = false, account = "analysis-tabs"),
                        actions = NativeActions({}, {}, {}, {}, { _, _ -> }), online = false,
                        selectedTab = tab.intValue, onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-overview").assertIsSelected()
        compose.onNodeWithTag("activity-tab-analysis").assertExists().performClick()
        compose.onNodeWithTag("activity-analysis-screen").assertExists()
        // Overview remains in the same LazyColumn; the Analysis section
        // does not duplicate its metrics or create a separate page.
        compose.onNodeWithTag("activity-analysis-run-summary").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-headline").assertExists()
        compose.onNodeWithTag("activity-analysis-explained").assertExists()
        compose.onNodeWithTag("activity-analysis-focus").assertExists()
        compose.onNodeWithTag("activity-analysis-how-it-went").assertExists()
        compose.onNodeWithTag("activity-analysis-heart-rate-zones").assertExists()
        compose.onNodeWithTag("activity-analysis-feel").assertExists()
        compose.onNodeWithTag("activity-analysis-detailed").assertExists()
        compose.onNodeWithTag("activity-workout-type-edit").assertDoesNotExist()
        compose.onNodeWithTag("activity-tab-analysis").assertIsSelected()
        // Checkpoints navigate within the same continuous scroller.
        compose.onNodeWithTag("activity-tab-splits").performClick()
        compose.onNodeWithTag("activity-section-splits").assertExists()
        compose.onNodeWithTag("activity-tab-charts").performClick()
        compose.onNodeWithTag("activity-section-charts").assertExists()
        compose.onNodeWithTag("activity-tab-overview").performClick()
        compose.onNodeWithTag("activity-section-overview").assertExists()
        // Natural scrolling (rather than changing tabs) reaches Analysis.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-analysis-screen"))
        compose.onNodeWithTag("activity-analysis-headline").assertExists()
        // The same scroller still contains Splits farther down.
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-section-splits"))
        compose.onNodeWithTag("activity-section-splits").assertExists()
    }

    @Test fun analysisExplainsMissingEvidenceWithoutInventingHeartRateOrFeedback() {
        val tab = mutableIntStateOf(0)
        val run = ActivityRecord(
            "analysis-sparse", "Recorded run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), null, null, null, null, null, null
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = null, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = TrainingState(initializing = false, account = "analysis-sparse"),
                        actions = NativeActions({}, {}, {}, {}, { _, _ -> }), online = false,
                        selectedTab = tab.intValue, onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        assertTrue(compose.onAllNodesWithText("More recorded activity data", substring = true)
            .fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("No recorded heart-rate stream", substring = true)
            .fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("activity-feedback-unavailable")
            .assertTextContains("Link it to its workout in Overview", substring = true)
        compose.onNodeWithTag("activity-workout-type-edit").assertDoesNotExist()
        compose.onNodeWithText("recovery score", substring = true).assertDoesNotExist()
    }

    @Test fun analysisSavesHowItFeltToTheLinkedPlanRunResponse() {
        val tab = mutableIntStateOf(0)
        val run = ActivityRecord(
            "analysis-feedback", "Recorded run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), 5000.0, 1800.0, 1750.0,
            360.0, 145.0, 0.0
        )
        val session = PlannedSession(
            "linked-run", java.time.LocalDate.parse("2026-10-26"), "run", "Easy run", 30, 5.0, "easy",
            status = "completed", linkedActivityId = run.id
        )
        val plan = PlanSnapshot(
            "feedback-plan", "analysis-feedback", ENGINE15_VERSION, ENGINE15_POLICY,
            Instant.parse("2026-10-26T00:00:00Z"), PlanRequest(policyChoice = "engine15"),
            listOf(session), emptyList(), status = "approved"
        )
        var saved: List<Any?>? = null
        val actions = NativeActions(
            {}, {}, {}, {}, { _, _ -> },
            planning = PlanActions(saveRunResponse = { planId, revision, sessionId, effort, difficulty, discomfort ->
                saved = listOf(planId, revision, sessionId, effort, difficulty, discomfort)
            })
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = null, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = TrainingState(initializing = false, account = "analysis-feedback", plans = listOf(plan)),
                        actions = actions, online = false, selectedTab = tab.intValue,
                        onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithTag("activity-continuous-scroll")
            .performScrollToNode(hasTestTag("activity-analysis-feel"))
        compose.onNodeWithTag("activity-feedback-effort").performTextInput("4")
        compose.onNodeWithText("Harder").performClick()
        compose.onNodeWithText("Pain").performClick()
        compose.onNodeWithTag("activity-feedback-save").performClick()
        assertEquals(listOf("feedback-plan", 1, "linked-run", 4, "Harder", "Pain"), saved)
    }
    @Test fun comparisonIsReachableFromDetailAndCaveatsRemainVisible() {
        val run = ActivityRecord("analysis-current", "Recorded run", "run", "manual", Instant.parse("2026-10-26T00:00:00Z"),5000.0,1800.0,1750.0,360.0,145.0,0.0)
        val rows = listOf(run) + (1..3).map { run.copy(id="analysis-$it", startedAt=run.startedAt.minusSeconds(it*86400L),recordedPace=400.0) }
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-analysis",activities=rows),false,NativeActions({},{},{},{},{_,_->})) } } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline")
            .performScrollToNode(hasTestTag("timeline-recorded-analysis-current"))
        compose.onNodeWithTag("timeline-recorded-analysis-current").performClick()
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithTag("activity-analysis-explained").assertExists()
        compose.onNodeWithTag("activity-sheet-scroll")
            .performScrollToNode(hasTestTag("activity-analysis-detailed"))
        compose.onNodeWithTag("activity-analysis-detailed").assertIsDisplayed()
        compose.onNodeWithTag("activity-analysis-detailed-toggle").performClick()
        compose.onAllNodesWithText("Effort, terrain and conditions may differ.",substring=true)
            .onFirst().assertIsDisplayed()
        compose.onNodeWithText("not a fitness, recovery or training-readiness assessment",substring=true)
            .performScrollTo().assertIsDisplayed()
        // An expanded approved activity sheet consumes Back before the route.
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
    }
}
