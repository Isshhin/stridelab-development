package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15QualityWorkoutDryRunTest {
    private val owner = "tempo-dry-run-owner"
    private val now = Instant.parse("2026-11-17T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-16")

    private fun request(weeks: Int = 8) = PlanRequest(
        goal = "Aerobic foundation", experience = 0, start = monday, weeks = weeks,
        days = setOf(1, 2, 3, 5, 6, 7), runDays = 6, longDay = 6,
        qualityCount = 1, qualityDays = setOf(2), weeklyKm = 75.0,
        longestKm = 20.0, targetWeeklyKm = 80.0,
        timeBudgetEnabled = false, policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun deferred(input: PlanRequest): Engine15IndependentPlanDecision.Deferred {
        val result = Engine15IndependentPlanGenerator.preview(input, owner, "tempo-dry-run", now)
        assertTrue("Expected a deferred, non-persistable quality result, got $result",
            result is Engine15IndependentPlanDecision.Deferred)
        return result as Engine15IndependentPlanDecision.Deferred
    }

    @Test fun eightWeekBeginnerWithoutQualityHistoryGetsOnlyACompleteTimedTempoStructurePreview() {
        val result = deferred(request())
        val selection = result.qualitySelection!!
        val dryRun = result.qualityDryRun!!
        val template = IndependentQualityTemplates.get(
            IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO
        )

        assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", result.reasonCode)
        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, selection.decision)
        assertEquals(listOf(1, 2, 3, 5, 6, 7), selection.candidateSlots.map { it.weekNumber })
        assertEquals(Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE,
            selection.familyPolicyQualification)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, selection.exposureConfidence)
        assertEquals(template.id, dryRun.templateId)
        assertEquals("HEURISTIC", dryRun.templateEvidence)
        assertEquals(template.workout, dryRun.workout)
        assertEquals(template.workout, IndependentWorkoutComposition.decode(dryRun.workout.encode()))
        assertEquals(3, dryRun.workout.elements.size)
        val warmUp = (dryRun.workout.elements[0] as IndependentWorkoutElement.Step).value
        val work = (dryRun.workout.elements[1] as IndependentWorkoutElement.Step).value
        val coolDown = (dryRun.workout.elements[2] as IndependentWorkoutElement.Step).value
        assertEquals(WorkoutPurpose.WARM_UP, warmUp.purpose)
        assertEquals(600.0, warmUp.amount, 0.0)
        assertEquals(WorkoutTarget.EASY, warmUp.target)
        assertEquals(WorkoutPurpose.WORK, work.purpose)
        assertEquals(720.0, work.amount, 0.0)
        assertEquals(WorkoutTarget.TEMPO, work.target)
        assertEquals(WorkoutPurpose.COOL_DOWN, coolDown.purpose)
        assertEquals(480.0, coolDown.amount, 0.0)
        assertEquals(WorkoutTarget.EASY, coolDown.target)
        assertEquals(1_800, dryRun.totalSeconds)
        assertEquals(720, dryRun.workSeconds)
        assertTrue(dryRun.baseWeeklyAccountingReconciled)
        assertFalse(dryRun.qualityDistanceReconciled)
        assertFalse(dryRun.completePlanDistanceAccounting)
        assertNull(dryRun.qualitySessionDistanceKm)
        assertNull(dryRun.weeklyDistanceAfterReplacementKm)
        assertFalse(dryRun.includedInPlan)
        assertFalse(dryRun.scheduled)
        assertFalse(dryRun.executable)
        assertTrue(result.explanation.contains("weekly mileage remain unknown"))
    }

    @Test fun sixteenWeekEnduranceDryRunAccountsForEveryBaseAndRecoveryWeekButNotTempoKilometres() {
        val result = deferred(request(16).copy(goal = "Marathon", experience = 2))
        val dryRun = result.qualityDryRun!!

        assertEquals(16, dryRun.weeklyDistance.size)
        assertEquals((1..16).toList(), dryRun.weeklyDistance.map { it.weekNumber })
        assertEquals(listOf(4, 8, 12, 16), dryRun.weeklyDistance
            .filter { it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY }
            .map { it.weekNumber })
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15),
            result.qualitySelection!!.candidateSlots.map { it.weekNumber })
        assertTrue(dryRun.weeklyDistance.all { kotlin.math.abs(it.budgetKm - it.scheduledRunKm) < 1e-6 })
        assertEquals(1, dryRun.weekNumber)
        assertEquals(Engine15IndependentVolumePlanner.WeekKind.LOADING,
            dryRun.weeklyDistance.single { it.weekNumber == dryRun.weekNumber }.kind)
        assertEquals(Engine15QualityDryRunStatus.STRUCTURE_VALIDATED_DISTANCE_UNRECONCILED,
            dryRun.status)
    }

    @Test fun thresholdCandidateIsNotSilentlyChangedToTheOnlyDryRunFamily() {
        val result = deferred(request().copy(goal = "Improve 5K", experience = 2))
        assertEquals(IndependentQualityFamily.THRESHOLD, result.qualitySelection!!.selectedFamily)
        assertNull(result.qualityDryRun)
        assertTrue(result.explanation.contains("no Tempo substitution"))
        assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", result.reasonCode)
    }

    @Test fun explicitTempoPreferenceIsNotLabeledCoachGenerated() {
        val result = deferred(request().copy(
            preferredQuality = "Tempo", preferredQualityExplicit = true
        ))
        assertEquals(Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE,
            result.qualitySelection!!.selectionBasis)
        assertNull(result.qualityDryRun)
        assertTrue(result.explanation.contains("explicit user direction"))
    }

    @Test fun unavailableQualityWeekdayDefersWithoutCreatingADryRun() {
        val result = deferred(request().copy(qualityDays = setOf(6)))
        assertEquals("QUALITY_SLOT_UNAVAILABLE", result.qualitySelection!!.reasonCode)
        assertNull(result.qualityDryRun)
    }

    @Test fun dryRunPreservesSelectionAndAuthorizationBoundaries() {
        val result = deferred(request())
        val dryRun = result.qualityDryRun!!

        assertEquals(Engine15QualityFamilySelection.POLICY_ID, dryRun.selectionPolicyId)
        assertEquals(Engine15QualityFamilySelection.POLICY_ID, result.qualitySelection!!.policyId)
        assertTrue(dryRun.developmentOnly)
        assertFalse(dryRun.sourceEvidenceVerified)
        assertFalse(dryRun.templateAuthorized)
        assertFalse(dryRun.doseAuthorized)
        assertFalse(dryRun.intensityAuthorized)
        assertFalse(dryRun.scheduled)
        assertFalse(dryRun.includedInPlan)
        assertFalse(dryRun.executable)
        assertEquals(result.qualitySelection!!.candidateSlots.first().sessionId, dryRun.sessionId)
        assertEquals(result.qualitySelection!!.candidateSlots.first().date, dryRun.date)
    }
}
