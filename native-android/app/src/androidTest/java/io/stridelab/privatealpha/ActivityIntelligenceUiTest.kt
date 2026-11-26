package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.Instant

/** Synthetic display-only history, no backend writes or real athlete claims. */
class ActivityIntelligenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun comparisonAndTimelineShowOnlySupportedEvidence() {
        val run = ActivityRecord("comparison", "Intervals", "run", "fit", Instant.EPOCH,
            9800.0, 1800.0, null, null, null, null)
        val session = PlannedSession("planned", java.time.LocalDate.of(2026, 10, 26),
            "run", "Intervals", 30, 10.0, "quality")
        val recorded = ActivityDetail(run.id, emptyList(),
            listOf(ActivityLap(0, 1000.0, 240.0, 240.0, null, workoutStepIndex = 0)), 0L,
            workoutSteps = listOf(
                ActivityWorkoutStep(0, "Interval 1", "active", null, null, null),
                ActivityWorkoutStep(1, "Unexecuted step", "recovery", null, null, null)))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    androidx.compose.foundation.layout.Column {
                        ActivityWorkoutComparisonCard(run, session,
                            ActivityWorkoutAdherence("SUMMARY_ONLY", listOf("Recorded evidence"), 1, 0))
                        ActivityRecordedWorkoutTimeline(recorded,
                            ActivityWorkoutAssessment(ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                                evidence = emptyList())))
                    }
                }
            }
        }
        compose.onNodeWithText("98% of prescribed distance recorded").assertExists()
        compose.onNodeWithText("Workout structure").assertExists()
        compose.onNodeWithText("Interval 1 · 1000m").assertExists()
        compose.onNodeWithText("Observed · 4:00 · 4:00/km").assertExists()
        compose.onNodeWithText("Unexecuted step").assertDoesNotExist()
        compose.onNodeWithText("Recorded evidence").assertDoesNotExist()
        compose.onNodeWithText("View comparison").performClick()
        compose.onNodeWithText("Recorded evidence").assertExists()
    }

    @Test fun steadyLinkedAutolapsDoNotShowWorkoutStructure() {
        val detail = ActivityDetail("easy", emptyList(),
            List(4) { ActivityLap(it, 1000.0, 360.0, 360.0, null, workoutStepIndex = 0) }, 0L,
            workoutSteps = listOf(ActivityWorkoutStep(0, "Easy run", "active", null, null, null)))
        compose.runOnUiThread {
            compose.activity.setContent {
                ActivityRecordedWorkoutTimeline(detail,
                    ActivityWorkoutAssessment(ActivityWorkoutEvidence(ActivityWorkoutKind.EASY_RUN,
                        evidence = emptyList())))
            }
        }
        compose.onNodeWithTag("activity-recorded-workout").assertDoesNotExist()
    }

    @Test fun coachDebriefShowsVerdictSummaryAndThreeInsights() {
        val insights = DebriefTone.entries.mapIndexed { index, tone ->
            DebriefInsight(DebriefCategory.EXECUTION, "Insight title $index", "Recorded evidence $index.", tone, 95 - index)
        }
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    CoachDebriefCard(requireNotNull(SessionDebrief.fromInsights(insights))
                        .copy(paragraphs = listOf("Your stride stayed steady through the finish."),
                            nextFocus = "Keep opening the first 2 km gently."))
                }
            }
        }
        compose.onNodeWithText("COACH'S DEBRIEF").assertIsDisplayed()
        compose.onNodeWithText("Your stride stayed steady through the finish.").assertExists()
        compose.onNodeWithTag("coach-debrief-next-focus").assertExists()
        compose.onNodeWithText("Keep opening the first 2 km gently.").assertExists()
        compose.onNodeWithTag("coach-debrief-headline").assertTextEquals(insights.first().title)
        compose.onNodeWithTag("coach-debrief-summary").assertTextEquals(insights.first().body)
        compose.onAllNodesWithText(insights.first().title).assertCountEquals(1)
        compose.onAllNodesWithText(insights.first().body).assertCountEquals(1)
        insights.drop(1).forEachIndexed { index, insight ->
            compose.onNodeWithTag("coach-debrief-insight-$index").assertExists()
            compose.onNodeWithText(insight.title).assertExists()
            compose.onNodeWithText(insight.body).assertExists()
        }
        compose.onNodeWithTag("coach-debrief-insight-2").assertDoesNotExist()
        compose.onNodeWithText("IN THE DATA").assertDoesNotExist()
    }

    @Test fun coachDebriefWithOneFindingShowsNoSupportingBullet() {
        val lead = DebriefInsight(DebriefCategory.EXECUTION, "Work pace on target",
            "89% of measured work.", DebriefTone.POSITIVE, 90)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme { CoachDebriefCard(requireNotNull(SessionDebrief.fromInsights(listOf(lead)))) }
            }
        }
        compose.onNodeWithTag("coach-debrief-summary").assertTextContains("89%", substring = true)
        compose.onAllNodesWithText(lead.title).assertCountEquals(1)
        compose.onNodeWithTag("coach-debrief-insight-0").assertDoesNotExist()
    }

    @Test fun analysisCheckpointsScrollWithinOneContinuousSheet() {
        val tab = mutableIntStateOf(0)
        val run = ActivityRecord(
            "analysis-tabs", "Recorded run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), 10000.0, 3850.0, 3850.0,
            385.0, 145.0, 0.0
        )
        val recorded = ActivityDetail(
            run.id,
            listOf(
                ActivitySample(0, 0.0, 400.0, 140.0, 10.0, null, null, 170.0),
                ActivitySample(1, 30.0, 390.0, 150.0, 12.0, null, null, 174.0),
                ActivitySample(2, 60.0, 380.0, 160.0, 15.0, null, null, 176.0)
            ),
            (0 until 10).map { val pace = if (it < 5) 400.0 else 370.0; ActivityLap(it, 1000.0, pace, pace, 140.0) }, 0L
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = recorded, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = TrainingState(initializing = false, account = "analysis-tabs"),
                        actions = NativeActions({}, { _, _ -> }), online = false,
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
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coach-debrief").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("coach-debrief-summary").assertTextContains("30 sec/km", substring = true)
        compose.onNodeWithTag("activity-analysis-focus").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-how-it-went").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-heart-rate-zones").assertExists()
        compose.onNodeWithTag("activity-analysis-feel").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-detailed").assertDoesNotExist()
        compose.onNodeWithTag("activity-workout-label-edit").assertExists()
        compose.onNodeWithTag("activity-tab-analysis").assertIsSelected()
        // Checkpoints navigate within the same continuous scroller.
        compose.onNodeWithTag("activity-tab-splits").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("activity-tab-splits").fetchSemanticsNode()
                .config.getOrElse(SemanticsProperties.Selected) { false }
        }
        assertEquals(2, tab.intValue)
        compose.onNodeWithTag("activity-tab-splits").assertIsSelected()
        compose.onNodeWithTag("activity-section-splits").assertExists()
        compose.onNodeWithTag("activity-tab-charts").performClick()
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("activity-tab-charts").fetchSemanticsNode()
                .config.getOrElse(SemanticsProperties.Selected) { false }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("activity-section-charts").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-tab-charts").assertIsSelected()
        compose.onNodeWithTag("activity-section-charts").assertExists()
        listOf("Pace", "Heart rate", "Elevation", "Cadence").forEach { metric ->
            val key = metric.lowercase(java.util.Locale.ROOT).replace(" ", "-")
            compose.onNodeWithTag("activity-continuous-scroll").performScrollToNode(hasTestTag("activity-chart-$key"))
            compose.onNodeWithText("$metric Analysis").assertDoesNotExist()
        }
        compose.onNodeWithText("Scale focuses on typical recorded values; extreme points remain inspectable.")
            .assertDoesNotExist()
        compose.onNodeWithTag("activity-tab-overview").performClick()
        compose.onNodeWithTag("activity-section-overview").assertExists()
        // Short real scroll gestures verify active checkpoints as the same
        // continuous list crosses Analysis and then Splits.
        fun isSelected(name: String) = compose.onNodeWithTag("activity-tab-$name")
            .fetchSemanticsNode().config.getOrElse(SemanticsProperties.Selected) { false }
        fun scrollUntilSelected(name: String) {
            repeat(24) {
                if (!isSelected(name)) {
                    compose.onNodeWithTag("activity-continuous-scroll").performTouchInput {
                        swipeUp(startY = centerY, endY = centerY - 180f, durationMillis = 500)
                    }
                    compose.waitForIdle()
                }
            }
        }
        scrollUntilSelected("analysis")
        compose.onNodeWithTag("coach-debrief-headline").assertExists()
        compose.onNodeWithTag("activity-tab-analysis").assertIsSelected()
        scrollUntilSelected("splits")
        compose.onNodeWithTag("activity-tab-splits").assertIsSelected()
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
                        actions = NativeActions({}, { _, _ -> }), online = false,
                        selectedTab = tab.intValue, onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithTag("coach-debrief").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-heart-rate-zones").assertDoesNotExist()
        compose.onNodeWithText("How Did It Feel?").assertDoesNotExist()
        compose.onNodeWithText("Want to save how this run felt?", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("activity-workout-detected").assertTextEquals("Workout type unclear")
        compose.onNodeWithTag("activity-workout-label-edit").assertExists()
        compose.onNodeWithText("recovery score", substring = true).assertDoesNotExist()
    }

    @Test fun userWorkoutLabelPersistsSeparatelyFromImportedActivity() {
        val tab = mutableIntStateOf(0)
        val owner = "analysis-user-label-owner"
        val trainingState = mutableStateOf(TrainingState(initializing = false, account = owner))
        val run = ActivityRecord(
            "analysis-user-label", "Imported run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), 5000.0, 1800.0, 1750.0,
            360.0, 145.0, 0.0
        )
        val store = UserActivityWorkoutLabelStore(compose.activity)
        store.clear(owner, run.id)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = null, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = trainingState.value,
                        actions = NativeActions({}, { _, _ -> }), online = false,
                        selectedTab = tab.intValue, onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithText("Best match from the recording").assertDoesNotExist()
        compose.onNodeWithText("Correct workout type").assertDoesNotExist()
        compose.onNodeWithTag("activity-workout-label-edit").performClick()
        compose.onNodeWithText("Correct workout type").assertExists()
        compose.onNodeWithText("Intervals").performScrollTo().performClick()

        val saved = UserActivityWorkoutLabel(ActivityWorkoutKind.QUALITY_RUN,
            ActivityQualitySubtype.INTERVALS)
        compose.onNodeWithTag("activity-workout-user-label").assertTextEquals("Your label: Quality Run · Intervals")
        assertEquals(saved, UserActivityWorkoutLabelStore(compose.activity).load(owner, run.id))
        assertEquals(null, store.load("another-owner", run.id))
        compose.runOnUiThread { trainingState.value = trainingState.value.copy(account = "another-owner") }
        compose.waitForIdle()
        compose.onNodeWithTag("activity-workout-user-label").assertDoesNotExist()
        compose.runOnUiThread { trainingState.value = trainingState.value.copy(account = owner) }
        compose.waitForIdle()
        compose.onNodeWithTag("activity-workout-user-label").assertTextEquals("Your label: Quality Run · Intervals")
        assertEquals("Imported run", run.title)
        assertEquals(5000.0, run.distanceM!!, 0.0)
        store.clear(owner, run.id)
    }

    @Test fun analysisShowsRunningMetricsWithoutTheFeedbackEditorOrUnverifiedAerobicScore() {
        val tab = mutableIntStateOf(0)
        val run = ActivityRecord("analysis-dynamics", "Recorded run", "run", "manual",
            Instant.parse("2026-10-26T00:00:00Z"), 5000.0, 1800.0, 1800.0, 360.0, 145.0, null)
        val recording = ActivityDetail(run.id, (0..1800 step 10).mapIndexed { i, seconds ->
            ActivitySample(i, seconds.toDouble(), 360.0, 145.0, null, null, null, 180.0)
        }, emptyList(), 0L, runningDynamics = RunningDynamics(247.0, 8.2, 1.15, 7.1, 180.0))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity = run, recorded = recording, detailSource = run,
                        matchedSourceIds = setOf(run.id), history = emptyList(),
                        state = TrainingState(initializing = false),
                        actions = NativeActions({}, { _, _ -> }), online = false,
                        selectedTab = tab.intValue, onTab = { tab.intValue = it },
                        matte = false, onToggleMatte = {}, onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-sheet-scroll").performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithTag("activity-training-effect").assertExists()
        compose.onNodeWithTag("activity-running-dynamics").assertExists()
        compose.onNodeWithText("247 ms").assertExists()
        compose.onNodeWithText("Aerobic").assertDoesNotExist()
        compose.onNodeWithText("How Did It Feel?").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-feel").assertDoesNotExist()
    }

    @Test fun sparseActivityOmitsDebriefEvenWithComparableHistory() {
        val run = ActivityRecord("analysis-current", "Recorded run", "run", "manual", Instant.parse("2026-10-26T00:00:00Z"),5000.0,1800.0,1750.0,360.0,145.0,0.0)
        val rows = listOf(run) + (1..3).map { run.copy(id="analysis-$it", startedAt=run.startedAt.minusSeconds(it*86400L),recordedPace=400.0) }
        compose.runOnUiThread { compose.activity.setContent { StrideTheme { NativeStrideContent(TrainingState(initializing=false,account="synthetic-analysis",activities=rows),false,NativeActions({},{_,_->})) } } }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline")
            .performScrollToNode(hasTestTag("timeline-recorded-analysis-current"))
        compose.onNodeWithTag("timeline-recorded-analysis-current").performClick()
        compose.onNodeWithTag("activity-sheet-scroll")
            .performSemanticsAction(SemanticsActions.Expand)
        compose.onNodeWithTag("activity-tab-analysis").performClick()
        compose.onNodeWithTag("activity-workout-label-edit").assertExists()
        compose.onNodeWithTag("coach-debrief").assertDoesNotExist()
        compose.onNodeWithTag("activity-analysis-detailed").assertDoesNotExist()
        // An expanded approved activity sheet consumes Back before the route.
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithTag("activity-map-back").performClick()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
    }
}
