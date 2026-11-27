package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.foundation.layout.Column
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class Engine15CoachingUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val date = LocalDate.of(2026, 11, 22)

    private fun plan(session: PlannedSession = PlannedSession("session-1", date, "run", "Threshold intervals", 45, 8.0, "quality", quality = true)) =
        PlanSnapshot("plan-1", "local", ENGINE15_VERSION, ENGINE15_INDEPENDENT_RUNNING_POLICY, Instant.EPOCH, PlanRequest(start = date), listOf(session), emptyList(), status = "approved")

    private fun matchState(): TrainingState {
        val activity = ActivityRecord("activity-1", "Morning Run", "run", "fit", Instant.parse("2026-11-22T06:00:00Z"), 7900.0, 2700.0, null, null, null, null)
        val record = ActivityMatchRecord("activity-1", null, ActivityMatchStatus.NEEDS_CONFIRMATION, .74, 0, listOf("DATE_AND_DISTANCE_CLOSE"), candidates = listOf(ActivityMatchCandidate("session-1", .74, 0, emptyList())))
        return TrainingState(initializing = false, activities = listOf(activity), plans = listOf(plan()), activityMatches = mapOf(activity.id to record))
    }

    private fun diagnostic(advisory: RecalibrationAdvisory = RecalibrationAdvisory.HOLD) = AthleteDiagnosticSnapshot(
        planId = "plan-1", asOfDate = date, overallConfidence = DiagnosticConfidence.INSUFFICIENT, pendingConfirmationCount = 0,
        volumeTolerance = VolumeToleranceDiagnostic(28, 20.0, 10.0, .5, 4, 2, 1, 5.0, 2.0, null, 4.0, ExposureStatus.INSUFFICIENT_DATA, DiagnosticConfidence.INSUFFICIENT, emptyList()),
        qualityExecution = QualityExecutionDiagnostic(0, 0, null, 0, 0, 0, 0, LongitudinalPaceTrend.INSUFFICIENT_DATA, null, DiagnosticConfidence.INSUFFICIENT, emptyList()),
        easyDiscipline = EasyDisciplineDiagnostic(0, 0, null, null, false, DiagnosticConfidence.INSUFFICIENT, emptyList()),
        aerobicEfficiency = AerobicEfficiencyDiagnostic(false, 0, null, null, null, null, AerobicEfficiencyTrend.UNAVAILABLE, DiagnosticConfidence.INSUFFICIENT, emptyList()),
        recalibrationAdvisory = advisory, insights = emptyList(), reasonCodes = emptyList()
    )

    private fun analysis(verdict: ExecutionVerdict) = WorkoutExecutionAnalysis(
        plannedSessionId = "session-1", activityId = "activity-1", evidenceTier = ExecutionEvidenceTier.SUMMARY_ONLY, verdict = verdict,
        volumeRatio = .98, plannedKm = 8.0, actualKm = 7.8, plannedMinutes = 45, actualMinutes = 46.0,
        workStepPaceAdherencePct = 81.0, qualityStimulusVerified = verdict != ExecutionVerdict.VOLUME_ONLY_MATCH,
        paceBias = ExecutionPaceBias.TOO_FAST, hrZoneCompliancePct = null, aerobicDecouplingPct = null,
        stepBreakdowns = emptyList(), reasonCodes = emptyList(), coachingSummary = "Run volume was close to plan."
    )

    private fun proposal(readOnly: Boolean) = PlanAdaptationProposal(
        proposalId = "proposal-1", planId = "plan-1", type = if (readOnly) AdaptationType.EASY_PACING_LOCK else AdaptationType.PACE_RECALIBRATION_UP,
        status = AdaptationProposalStatus.PENDING, createdAtDate = date, effectiveFromDate = date.plusDays(1), expiresAtDate = date.plusDays(7),
        disruptionTier = DisruptionTier.NONE, disruptionFingerprint = null, sourceDiagnosticDate = date, sourceConfidence = DiagnosticConfidence.MODERATE,
        title = if (readOnly) "Easy pacing lock" else "Small pace adjustment", coachingRationale = "Easy days exceeded the current pace zone.",
        diff = PlanAdaptationDiff(date.plusDays(1), 2, 20.0, 19.0, listOf(AdaptationWeekDiff(1, "BUILD", "BUILD", 20.0, 19.0, 1, 1, "Protect recovery.")), listOf(AdaptationPaceDiff(RunningStepIntent.THRESHOLD, 300, 330, 294, 320)), true, emptyList()),
        candidatePlanJson = if (readOnly) null else "{}", reasonCodes = emptyList()
    )

    @Test fun pendingMatchIsVisibleAndButtonsDelegateToPlanActions() {
        var confirmed = ""
        var rejected = ""
        compose.activity.setContent { StrideTheme {
            PendingActivityMatchCards(matchState(), PlanActions(
                confirmActivityMatch = { id, revision, session, activity -> confirmed = "$id:$revision:$session:$activity" },
                rejectActivityMatch = { activity, session -> rejected = "$activity:$session" }
            ))
        } }
        compose.onNodeWithTag("activity-match-activity-1-session-1").assertExists()
        compose.onNodeWithText("74% match").assertExists()
        compose.onNodeWithText("Confirm Link").performClick()
        assertEquals("plan-1:1:session-1:activity-1", confirmed)
        compose.onNodeWithText("Not This Workout").performClick()
        assertEquals("activity-1:session-1", rejected)
    }

    @Test fun matchCardIsAbsentWithoutConfirmationItems() {
        compose.activity.setContent { StrideTheme { PendingActivityMatchCards(TrainingState(), PlanActions()) } }
        compose.onNodeWithText("CONFIRM WORKOUT LINK").assertDoesNotExist()
    }

    @Test fun executionVerdictsRenderAndOmitNullHrMetrics() {
        compose.activity.setContent { StrideTheme {
            Column { WorkoutExecutionVerdictCard(analysis(ExecutionVerdict.VOLUME_ONLY_MATCH)); WorkoutExecutionVerdictCard(analysis(ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST)) }
        } }
        compose.onNodeWithText("Volume Only").assertExists()
        compose.onNodeWithText("Intensity mismatch too fast").assertExists()
        compose.onNodeWithText("HR zone compliance").assertDoesNotExist()
    }

    @Test fun insufficientDiagnosticsAndEasyPacingLockRenderWithoutHrMetrics() {
        compose.activity.setContent { StrideTheme { AthleteDiagnosticsCard(diagnostic(RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST)) } }
        compose.onNodeWithTag("coaching-diagnostics").assertExists()
        compose.onNodeWithText("INSUFFICIENT").assertExists()
        compose.onNodeWithTag("easy-pacing-lock").assertExists()
        compose.onNodeWithText("HR efficiency").assertDoesNotExist()
    }

    @Test fun proposalDiffOffersApplyAndDismissOrReadOnlyDismissal() {
        var applied = ""
        var dismissed = ""
        compose.activity.setContent { StrideTheme {
            AdaptationProposalSheet(proposal(false), false, {}, { id, proposalId -> applied = "$id:$proposalId" }, { id, proposalId -> dismissed = "$id:$proposalId" })
        } }
        compose.onNodeWithText("Apply Adjustment").assertExists().performClick()
        assertEquals("plan-1:proposal-1", applied)
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals("plan-1:proposal-1", dismissed)

        compose.activity.setContent { StrideTheme {
            AdaptationProposalSheet(proposal(true), false, {}, { _, _ -> error("read-only proposal cannot apply") }, { id, proposalId -> dismissed = "$id:$proposalId" })
        } }
        compose.onNodeWithText("Got It / Dismiss").assertExists()
        compose.onNodeWithText("Apply Adjustment").assertDoesNotExist()
    }

    @Test fun undoAppearsOnlyForUnrevertedRevision() {
        val plan = plan()
        val revision = PlanAdaptationRevision(revisionId = "revision-1", planId = plan.id, proposalId = "proposal-1", appliedAtDate = date, adaptationType = AdaptationType.PACE_RECALIBRATION_UP, previousPlanJson = "{}", appliedPlanJson = "{}")
        compose.activity.setContent { StrideTheme { UndoAdaptationButton(plan, listOf(revision), false, {}) } }
        compose.onNodeWithTag("undo-last-adaptation").assertExists()
        compose.activity.setContent { StrideTheme { UndoAdaptationButton(plan, listOf(revision.copy(revertedAtDate = date.toString())), false, {}) } }
        compose.onNodeWithTag("undo-last-adaptation").assertDoesNotExist()
    }

    @Test fun workoutStepCardsKeepDistanceStridesHillsAndRacePaceReadable() {
        val session = plan().sessions.single().copy(kind = "long")
        val steps = listOf(
            RunningStepV2("WORK", "Threshold", meters = 1000, stepIntent = RunningStepIntent.THRESHOLD),
            RunningStepV2("WORK", "Fast relaxed stride", seconds = 20, stepIntent = RunningStepIntent.STRIDE),
            RunningStepV2("WORK", "Run uphill at a steady grade", seconds = 60, stepIntent = RunningStepIntent.HILL),
            RunningStepV2("TRANSITION", "Half marathon race pace finish", meters = 4000, stepIntent = RunningStepIntent.RACE_PACE)
        )
        val items = buildWorkoutStepItems(session, segments = null, mixedSegments = steps, textFallback = emptyList())
        compose.activity.setContent { StrideTheme { TracksideRunningStepCards(items) } }
        compose.onNodeWithText("1.0 km").assertExists()
        compose.onNodeWithText("Stride").assertExists()
        compose.onNodeWithText("Hill Repeat").assertExists()
        compose.onNodeWithText("Run uphill at a steady grade").assertExists()
        compose.onNodeWithText("Race Pace").assertExists()
        compose.onNodeWithText("4.0 km").assertExists()
    }
}
