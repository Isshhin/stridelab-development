package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15MarathonTidEvaluatorTest {
    private val owner = "tid-evaluator-runner"
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val monday = LocalDate.parse("2026-11-23")

    private fun baseRequest(goal: String, weeks: Int, qualityCount: Int) = PlanRequest(
        goal = goal,
        experience = 2,
        start = monday,
        weeks = weeks,
        days = setOf(1, 2, 4, 6, 7),
        runDays = 5,
        longDay = 6,
        qualityCount = qualityCount,
        qualityDays = setOf(2),
        weeklyKm = 40.0,
        longestKm = 14.0,
        targetWeeklyKm = 40.0,
        timeBudgetEnabled = false,
        policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun claimsForDryRun(dryRun: Engine15QualityWorkoutDryRun) = dryRun.alternatives.map { alt ->
        val workout = alt.prescription!!.step.workout
        val phases = mutableListOf<WorkoutStep>()
        for (el in workout.elements) when (el) {
            is IndependentWorkoutElement.Step -> repeat(el.value.repetitions) { phases += el.value }
            is IndependentWorkoutElement.Intervals -> repeat(el.value.repetitions) { i ->
                phases += el.value.work
                if (i < el.value.repetitions - 1) phases += el.value.recovery
            }
        }
        val totalMeters = kotlin.math.round(alt.originalEasyDistanceKm * 1000.0).toInt()
        val weights = phases.map { it.amount * if (it.purpose == WorkoutPurpose.WORK) 1.25 else 1.0 }
        val sumWeights = weights.sum()
        val raw = weights.dropLast(1).map { kotlin.math.floor(it / sumWeights * totalMeters) }
        Engine15M5SegmentDistanceClaim(
            alt.weekNumber, alt.slotNumber, raw + (totalMeters - raw.sum()),
            null, Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
        )
    }

    @Test fun easyOnlyMarathonPlanEvaluatesAsLowIntensityOnlyAndFlagsShortHorizon() {
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            baseRequest("Marathon", weeks = 8, qualityCount = 0), owner, emptyList(), "tid-easy", now
        )
        val eval = Engine15MarathonTidEvaluator.evaluate(plan)
        assertEquals(Engine15TidPattern.LOW_INTENSITY_ONLY, eval.pattern)
        assertEquals(1.0, eval.overall.zone1Fraction, 1e-6)
        assertEquals(0.0, eval.overall.zone2ModerateMeters, 1e-6)
        assertEquals(0.0, eval.overall.zone3HighMeters, 1e-6)
        assertTrue(eval.readOnlyAdvisory && !eval.mutatesPlan && !eval.physiologyVerified)
        assertTrue(eval.advisories.any { it.contains("Short marathon horizon") })
    }

    @Test fun sixteenWeekMarathonTempoPlanEvaluatesAsPyramidalAndFiveKIntervalsAsPolarized() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)

        val marathonReq = baseRequest("Marathon", weeks = 16, qualityCount = 1)
        val marathonPreview = Engine15IndependentPlanGenerator.preview(marathonReq, owner, "tid-m16", now)
            as Engine15IndependentPlanDecision.Deferred
        val marathonPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            marathonReq.copy(qualityDistanceClaims = claimsForDryRun(marathonPreview.qualityDryRun!!)),
            owner, emptyList(), "tid-m16", now
        )
        val marathonTid = Engine15MarathonTidEvaluator.evaluate(marathonPlan)
        assertEquals(Engine15TidPattern.PYRAMIDAL, marathonTid.pattern)
        assertTrue(marathonTid.overall.zone1Fraction >= 0.75)
        assertTrue(marathonTid.overall.zone2ModerateMeters > 0.0)
        assertEquals(0.0, marathonTid.overall.zone3HighMeters, 1e-6)
        assertEquals(0.0, marathonTid.overall.unclassifiedMeters, 1e-6)

        val fiveKReq = baseRequest("Improve 5K", weeks = 8, qualityCount = 1)
        val fiveKPreview = Engine15IndependentPlanGenerator.preview(fiveKReq, owner, "tid-5k", now)
            as Engine15IndependentPlanDecision.Deferred
        val fiveKPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            fiveKReq.copy(qualityDistanceClaims = claimsForDryRun(fiveKPreview.qualityDryRun!!)),
            owner, emptyList(), "tid-5k", now
        )
        val fiveKTid = Engine15MarathonTidEvaluator.evaluate(fiveKPlan)
        assertEquals(Engine15TidPattern.POLARIZED, fiveKTid.pattern)
        assertTrue(fiveKTid.overall.zone1Fraction >= 0.75)
        assertTrue(fiveKTid.overall.zone3HighMeters > 0.0)
        assertEquals(0.0, fiveKTid.overall.zone2ModerateMeters, 1e-6)
    }

    @Test fun missingSegmentAccountingReportsIncompletePrescriptionDataWithoutGuessing() {
        val session = PlannedSession(
            id = "q-1", date = monday.plusDays(1), discipline = "run", title = "Unlabelled quality",
            minutes = 40, distanceKm = 8.0, kind = "quality", quality = true, runningPrescription = null
        )
        val plan = PlanSnapshot(
            id = "tid-inc", owner = owner, engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY, createdAt = now,
            request = baseRequest("Marathon", weeks = 4, qualityCount = 1),
            sessions = listOf(session), explanations = listOf("Test")
        )
        val eval = Engine15MarathonTidEvaluator.evaluate(plan)
        assertEquals(Engine15TidPattern.INCOMPLETE_PRESCRIPTION_DATA, eval.pattern)
        assertEquals(8_000.0, eval.overall.unclassifiedMeters, 1e-6)
    }
}
