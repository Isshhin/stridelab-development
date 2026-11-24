package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SkeletonLoadersUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test
    fun todayShowsMatchedHeroAndOverviewSkeletonsWithoutLoadingText() {
        val state = mutableStateOf(
            TrainingState(
                initializing = true,
                todayPlanReady = false,
                activitySummariesReady = false,
                account = null,
                localPlanOwner = "local"
            )
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = state.value,
                        visibleActivities = emptyList(),
                        online = false,
                        onOpenTraining = {},
                        onOpenActivity = {},
                        onOpenWorkout = { _, _ -> },
                        onOpenActivityDetail = {},
                        onOpenMore = {},
                        onSignIn = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("today-hero-skeleton").assertExists()
        compose.onAllNodesWithText("Loading training day...").assertCountEquals(0)
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("today-overview-skeleton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("today-overview-skeleton").assertExists()

        val today = LocalDate.now()
        val plan = PlanSnapshot(
            id = "plan-1",
            owner = "local",
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = Instant.now().minusSeconds(86_400),
            request = PlanRequest(goal = "10K", runDays = 3, days = setOf(1, 3, 5)),
            sessions = listOf(
                PlannedSession(
                    id = "s-today",
                    date = today,
                    discipline = "run",
                    title = "Easy Run",
                    minutes = 40,
                    distanceKm = 6.5,
                    kind = "easy"
                )
            ),
            explanations = emptyList(),
            status = "approved"
        )
        compose.runOnIdle {
            state.value = TrainingState(
                initializing = false,
                todayPlanReady = true,
                activitySummariesReady = true,
                account = null,
                localPlanOwner = "local",
                plans = listOf(plan)
            )
        }

        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("today-workout-action").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("today-hero-skeleton").assertCountEquals(0)
        compose.onAllNodesWithTag("today-overview-skeleton").assertCountEquals(0)
    }

    @Test
    fun activityTabShowsSummaryCardSkeletonsWithoutEmptyStateFlash() {
        val loading = mutableStateOf(true)
        val evidence = mutableStateOf<List<WorkoutEvidence>?>(emptyList())
        val completed = WorkoutEvidence(
            key = "import:garmin-1",
            source = WorkoutEvidenceSource.GARMIN_IMPORTED,
            link = WorkoutEvidenceLink.Imported(
                id = "garmin-1",
                recordingIds = listOf("garmin-1"),
                coached = emptyList()
            ),
            date = LocalDate.of(2026, 11, 23),
            recordedAt = 1_700_000_000_000L,
            title = "Morning Base Run",
            discipline = ManualDiscipline.RUNNING,
            distanceKm = 8.2,
            durationMinutes = 42.0,
            originLabel = "Garmin",
            avgPaceSecPerKm = 307.0,
            avgHrBpm = 148.0,
            movingTimeSeconds = 2520.0
        )

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    ActivityHistoryTabScreen(
                        evidence = evidence.value,
                        loading = loading.value,
                        details = emptyMap(),
                        onOpenActivityDetail = {},
                        onOpenWorkout = { _, _ -> },
                        onOpenFreeformHistory = { _, _ -> },
                        onOpenQuickLog = {}
                    )
                }
            }
        }

        compose.onAllNodesWithTag("activity-empty-state").assertCountEquals(0)
        compose.onAllNodesWithText("No completed workouts yet").assertCountEquals(0)
        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("activity-skeleton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("activity-skeleton").assertIsDisplayed()
        compose.onAllNodesWithTag("activity-empty-state").assertCountEquals(0)

        compose.runOnIdle {
            evidence.value = listOf(completed)
            loading.value = false
        }

        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("activity-evidence-import:garmin-1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("activity-skeleton").assertCountEquals(0)
        compose.onNodeWithText("Mon | Nov 23").assertExists()
        compose.onNodeWithText("GARMIN").assertExists()
    }

    @Test
    fun trainingTabShowsUnifiedSkeletonWhileInitializing() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabUnifiedTrainingTimeline(
                        state = TrainingState(
                            initializing = true,
                            account = null,
                            localPlanOwner = "local"
                        ),
                        activityGroups = emptyList(),
                        actions = PlanActions(),
                        onBuild = {},
                        onOpenWorkout = { _, _ -> },
                        onOpenActivity = {},
                        onManage = { _, _ -> }
                    )
                }
            }
        }

        compose.waitUntil(3_000) {
            compose.onAllNodesWithTag("training-skeleton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("training-skeleton").assertExists()
    }

    @Test
    fun allSkeletonPrimitivesRenderCleanlyWithReducedMotionWithoutOverflow() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    SkeletonShimmerContainer(reducedMotion = true) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SkeletonHeroTrainingCard(Modifier.testTag("skel-hero"), reducedMotion = true)
                            SkeletonOverviewMetricsCard(Modifier.testTag("skel-overview"), reducedMotion = true)
                            SkeletonConsistencyHeatmap(Modifier.testTag("skel-heatmap"), reducedMotion = true)
                            SkeletonActivitySummaryCard(Modifier.testTag("skel-activity-card"), reducedMotion = true)
                            SkeletonActivityRow(Modifier.testTag("skel-activity-row"), reducedMotion = true)
                            SkeletonTrainingHeaderCard(Modifier.testTag("skel-training-header"), reducedMotion = true)
                            SkeletonCoachingCard(Modifier.testTag("skel-coaching"), reducedMotion = true)
                            SkeletonActivityDetailStreams(Modifier.testTag("skel-detail"), reducedMotion = true)
                            SkeletonShareCardPreview(aspectRatio = 9f / 16f, modifier = Modifier.testTag("skel-share"), reducedMotion = true)
                            SkeletonChipRow(modifier = Modifier.testTag("skel-chips"), reducedMotion = true)
                        }
                    }
                }
            }
        }

        val tags = listOf(
            "skel-hero", "skel-overview", "skel-heatmap", "skel-activity-card",
            "skel-activity-row", "skel-training-header", "skel-coaching",
            "skel-detail", "skel-share", "skel-chips"
        )
        tags.forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().assertExists()
            val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag width should be positive", bounds.right > bounds.left)
            assertTrue("$tag height should be positive", bounds.bottom > bounds.top)
        }
    }
}
