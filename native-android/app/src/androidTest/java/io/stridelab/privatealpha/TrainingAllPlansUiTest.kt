package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TrainingAllPlansUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun allModeShowsFourTracksAndCanFilterAndManageAnExactPlan() {
        val now = LocalDate.now()
        fun plan(id: String, discipline: String, title: String, offset: Long, weeks: Int) = PlanSnapshot(
            id, "training-qa", ENGINE1_VERSION, ENGINE1_POLICY, Instant.EPOCH,
            PlanRequest(start = now.plusDays(offset), weeks = weeks),
            listOf(PlannedSession("shared", now, discipline, title, 25, null, "easy")),
            emptyList(), status = "approved", name = title
        )
        val plans = listOf(
            plan("base", "run", "Aerobic run", -3, 2),
            plan("strength", "str", "Full Body", -2, 3),
            plan("jump", "str", "Plyometrics", -1, 1),
            plan("iso", "str", "Isometrics", 0, 4)
        )
        var managed: Pair<String, LocalDate>? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabUnifiedTrainingTimeline(
                        TrainingState(initializing = false, account = "training-qa", plans = plans),
                        emptyList(), PlanActions(), {}, { _, _ -> }, {},
                        { id, date -> managed = id to date }
                    )
                }
            }
        }
        val timeline = compose.onNodeWithTag("training-unified-timeline")
        compose.onNodeWithText("All active plans").assertExists()
        timeline.performScrollToNode(hasTestTag("timeline-day-$now"))
        compose.onAllNodesWithTag("timeline-planned-shared").assertCountEquals(4)
        for (plan in plans) compose.onNodeWithTag("timeline-session-${plan.id}-shared", useUnmergedTree = true).assertExists()
        timeline.performScrollToNode(hasTestTag("training-switch-plan"))
        compose.onNodeWithTag("training-switch-plan").performClick()
        timeline.performScrollToNode(hasTestTag("training-discipline-plyo"))
        compose.onNodeWithTag("training-discipline-plyo").performClick()
        timeline.performScrollToNode(hasTestTag("timeline-day-$now"))
        compose.onAllNodesWithTag("timeline-planned-shared").assertCountEquals(1)
        compose.onNodeWithTag("timeline-session-jump-shared", useUnmergedTree = true).assertExists()
        timeline.performScrollToNode(hasTestTag("training-manage-plan"))
        compose.onNodeWithTag("training-manage-plan").performClick()
        compose.runOnIdle { assertEquals("jump" to now, managed) }
        compose.onNodeWithTag("training-create-plan").assertExists()
        compose.onNodeWithTag("training-plan-tools").assertDoesNotExist()
    }

    @Test fun workoutGroupingOnScreenPreservesTheExportedPrescription() {
        val date = LocalDate.of(2026, 11, 23)
        val stamp = Instant.parse("2026-11-23T00:00:00Z")
        val stride = RunningStepV2("TRANSITION", "Smooth stride", seconds = 20,
            stepIntent = RunningStepIntent.STRIDE, targetPaceMinSecPerKm = 240, targetPaceMaxSecPerKm = 270)
        val recovery = RunningStepV2("RECOVERY", "Easy jog", seconds = 60)
        val steps = List(4) { listOf(stride, recovery) }.flatten()
        val target = RunningPrescription("EASY", null, emptyList(), mixedSegments = steps)
        val session = PlannedSession("strides", date, "run", "Strides", 6, null, "easy",
            runningPrescription = target)
        val draft = PlanSnapshot("stride-qa", "coach-qa", ENGINE1_VERSION, ENGINE1_POLICY, stamp,
            PlanRequest(start = date), listOf(session), emptyList())
        val bytes = GarminFitWorkoutEncoder.encode(session.title, target, stamp)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    BlueprintWorkoutScreen(TrainingState(account = draft.owner, planPreview = draft),
                        draft.id, session.id, stamp.toString(), 0) {}
                }
            }
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-mixed-repeat-0"))
        compose.onNodeWithText("4 × Interval Set", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4 REPS", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4:00–4:30 /km", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(steps, session.runningPrescription?.mixedSegments)
        assertArrayEquals(bytes, GarminFitWorkoutEncoder.encode(session.title, target, stamp))
    }

    @Test fun coachingAndMatchActionsAreReachableFromThePrimaryCalendar() {
        val date = LocalDate.now()
        val session = PlannedSession("review-run", date, "run", "Planned run", 40, 6.0, "easy")
        val plan = PlanSnapshot("review", "coach-qa", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY,
            Instant.EPOCH, PlanRequest(start = date), listOf(session), emptyList(), status = "approved")
        val activity = ActivityRecord("fit-one", "Recorded run", "run", "fit",
            Instant.now(), 6000.0, 2400.0, 2390.0, null, null, null)
        val match = ActivityMatchRecord(activity.id, session.id, ActivityMatchStatus.NEEDS_CONFIRMATION,
            .74, 0, emptyList())
        val proposal = PlanAdaptationProposal(
            proposalId = "adjust", planId = plan.id, type = AdaptationType.PACE_RECALIBRATION_UP,
            status = AdaptationProposalStatus.PENDING, createdAtDate = date,
            effectiveFromDate = date.plusDays(1), expiresAtDate = date.plusDays(7),
            disruptionTier = DisruptionTier.NONE, disruptionFingerprint = null,
            sourceDiagnosticDate = date, sourceConfidence = DiagnosticConfidence.MODERATE,
            title = "Pace adjustment", coachingRationale = "Review next week's targets.",
            diff = PlanAdaptationDiff(date.plusDays(1), 0, 10.0, 10.0, emptyList(), emptyList(), true, emptyList()),
            candidatePlanJson = "{}", reasonCodes = emptyList()
        )
        val revision = PlanAdaptationRevision(revisionId = "revision", planId = plan.id,
            proposalId = proposal.proposalId, appliedAtDate = date, adaptationType = proposal.type,
            previousPlanJson = "{}", appliedPlanJson = "{}")
        val state = TrainingState(initializing = false, account = plan.owner,
            plans = listOf(plan), activities = listOf(activity), activityMatches = mapOf(activity.id to match),
            pendingAdaptationProposals = mapOf(plan.id to proposal),
            adaptationRevisions = mapOf(plan.id to listOf(revision)))
        val calls = mutableListOf<String>()
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabUnifiedTrainingTimeline(state, listOf(DisplayActivity(activity)),
                        PlanActions(
                            approveAdaptationProposal = { p, a -> calls.add("apply:$p:$a") },
                            dismissAdaptationProposal = { p, a -> calls.add("dismiss:$p:$a") },
                            revertLastAdaptation = { calls.add("undo:$it") },
                            confirmActivityMatch = { p, _, s, a -> calls.add("match:$p:$s:$a") }
                        ), {}, { _, _ -> }, {}, { _, _ -> })
                }
            }
        }
        val timeline = compose.onNodeWithTag("training-unified-timeline")
        timeline.performScrollToNode(hasTestTag("coach-week-review"))
        compose.onNodeWithTag("coach-week-review").assertExists()
        timeline.performScrollToNode(hasText("Review Adjustment"))
        compose.onNodeWithText("Review Adjustment").performClick()
        compose.onNodeWithText("Apply Adjustment").performClick()
        compose.onNodeWithText("Review Adjustment").performClick()
        compose.onNodeWithText("Dismiss", useUnmergedTree = true).performClick()
        timeline.performScrollToNode(hasTestTag("undo-last-adaptation"))
        compose.onNodeWithTag("undo-last-adaptation").performClick()
        compose.onNodeWithText("Undo").performClick()
        timeline.performScrollToNode(hasText("Confirm Link"))
        compose.onNodeWithText("Confirm Link").performClick()
        compose.runOnIdle { assertEquals(listOf("apply:review:adjust", "dismiss:review:adjust",
            "undo:review", "match:review:review-run:fit-one"), calls) }
    }
}
