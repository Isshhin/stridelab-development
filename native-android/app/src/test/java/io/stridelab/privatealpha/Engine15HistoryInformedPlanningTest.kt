package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15HistoryInformedPlanningTest {
    private val owner = "history-informed-runner"
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")

    private fun baseRequest(qualityCount: Int = 1) = PlanRequest(
        goal = "Improve 10K",
        experience = 2,
        start = monday,
        weeks = 12,
        days = setOf(1, 2, 4, 6, 7),
        runDays = 5,
        longDay = 6,
        qualityCount = qualityCount,
        qualityDays = setOf(2),
        weeklyKm = 36.0,
        longestKm = 12.0,
        targetWeeklyKm = 36.0,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    @Test fun missingHistoryPreservesManualInputsAndHoldsQualityAtIntro() {
        val result = Engine15HistoryInformedPlanner.preview(
            request = baseRequest(qualityCount = 1),
            owner = owner,
            activities = emptyList(),
            allowedActivityIds = emptySet(),
            detailsByActivityId = emptyMap(),
            existingPlans = emptyList(),
            userLabels = emptyMap(),
            id = "hist-empty",
            now = now
        )
        assertTrue(result.advisories.any { it.code == "MISSING_HISTORY_PRESERVES_MANUAL_INPUT" })
        assertTrue(result.advisories.any { it.code == "NO_CONFIRMED_QUALITY_EXPOSURE_HOLDS_INTRO" })
        val deferred = result.decision as Engine15IndependentPlanDecision.Deferred
        assertTrue(deferred.qualityDryRun!!.alternatives.all {
            it.progressionBasis == Engine15QualityProgressionBasis.HOLD_NO_REPEATED_EXPOSURE
        })
    }

    @Test fun confirmedLinkedThresholdHistoryEnablesBoundedProgressionWithoutMutatingManualMileage() {
        val thr1 = ActivityRecord("thr-a", "Threshold 1", "running", "garmin", now.minusSeconds(10 * 86400L), 9_000.0, 2_700.0, 2_700.0, 300.0, 165.0, 30.0)
        val thr2 = ActivityRecord("thr-b", "Threshold 2", "running", "garmin", now.minusSeconds(3 * 86400L), 11_500.0, 3_450.0, 3_450.0, 300.0, 166.0, 35.0)
        val prescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = emptyList(),
            mixedSegments = listOf(
                RunningStepV2("WARM_UP", "Easy", seconds = 600, distanceEquivalentMeters = 1500),
                RunningStepV2("WORK", "Threshold", seconds = 240, distanceEquivalentMeters = 900),
                RunningStepV2("RECOVERY", "Easy", seconds = 120, distanceEquivalentMeters = 300),
                RunningStepV2("WORK", "Threshold", seconds = 240, distanceEquivalentMeters = 900),
                RunningStepV2("RECOVERY", "Easy", seconds = 120, distanceEquivalentMeters = 300),
                RunningStepV2("WORK", "Threshold", seconds = 240, distanceEquivalentMeters = 900),
                RunningStepV2("COOL_DOWN", "Easy", seconds = 480, distanceEquivalentMeters = 1200)
            ),
            reconciledDistanceMeters = 6000
        )
        val priorPlan = PlanSnapshot(
            id = "prior-plan",
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
            createdAt = now.minusSeconds(21 * 86400L),
            request = PlanRequest(start = LocalDate.parse("2026-10-26"), weeks = 4),
            sessions = listOf(
                PlannedSession("p-s1", LocalDate.parse("2026-11-10"), "run", "Threshold", 34, 6.0, "quality", quality = true, status = "completed", linkedActivityId = "thr-a", runningPrescription = prescription),
                PlannedSession("p-s2", LocalDate.parse("2026-11-17"), "run", "Threshold", 34, 6.0, "quality", quality = true, status = "completed", linkedActivityId = "thr-b", runningPrescription = prescription)
            ),
            explanations = listOf("Prior plan"),
            status = "approved"
        )

        val result = Engine15HistoryInformedPlanner.preview(
            request = baseRequest(qualityCount = 1),
            owner = owner,
            activities = listOf(thr1, thr2),
            allowedActivityIds = setOf("thr-a", "thr-b"),
            detailsByActivityId = emptyMap(),
            existingPlans = listOf(priorPlan),
            userLabels = emptyMap(),
            id = "hist-progressed",
            now = now
        )
        assertTrue(result.advisories.any { it.code == "CONFIRMED_QUALITY_EXPOSURE_ENABLES_BOUNDED_PROGRESSION" })
        assertTrue(result.advisories.any { it.code == "RECORDED_LONGEST_RUN_SUPPORTS_BASELINE" })
        assertTrue(result.advisories.any { it.code == "RECORDED_VOLUME_BELOW_DECLARED" })
        val dryRun = (result.decision as Engine15IndependentPlanDecision.Deferred).qualityDryRun!!
        assertTrue(dryRun.alternatives.any {
            it.progressionBasis == Engine15QualityProgressionBasis.EVIDENCE_BOUNDED_PROGRESSION &&
                it.workSeconds!! > 720
        })
        assertEquals(Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION, dryRun.alternatives.last().progressionBasis)
    }
}
