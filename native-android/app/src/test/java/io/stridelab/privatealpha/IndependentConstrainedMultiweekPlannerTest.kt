package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class IndependentConstrainedMultiweekPlannerTest {
    private val input = IndependentWeeklyMileageInput(
        approvedStartingWeeklyKm = 60.0,
        approvedWeeklyCeilingKm = 80.0,
        requestedTargetWeeklyKm = 80.0,
        weeks = 5,
        runningDays = setOf(1, 2, 3, 5, 6, 7),
        preferredLongRunDay = 6
    )
    private fun limits(
        mondayCap: Double = 5.0,
        longCap: Double = 25.0,
        priorLong: Double = 17.0,
        maxLongIncrease: Double = 2.0
    ) = IndependentMileageFeasibilityLimits(
        approvedMaxKmByDay = input.runningDays.associateWith {
            if (it == 1) mondayCap else 25.0
        },
        approvedPreviousLongRunKm = priorLong,
        approvedLongRunCeilingKm = longCap,
        approvedMaxBuildLongRunIncreaseKm = maxLongIncrease
    )

    private fun candidate(
        request: IndependentWeeklyMileageInput = input,
        ceilings: IndependentMileageFeasibilityLimits = limits(),
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentConstrainedMultiweekDecision.Candidate {
        val result = IndependentConstrainedMultiweekPlanner.generate(request, ceilings, policy)
        assertTrue(result.toString(), result is IndependentConstrainedMultiweekDecision.Candidate)
        return result as IndependentConstrainedMultiweekDecision.Candidate
    }

    private fun reject(
        id: String,
        request: IndependentWeeklyMileageInput = input,
        ceilings: IndependentMileageFeasibilityLimits = limits()
    ): IndependentConstrainedMultiweekDecision.Deferred {
        val result = IndependentConstrainedMultiweekPlanner.generate(request, ceilings)
        assertTrue(result.toString(), result is IndependentConstrainedMultiweekDecision.Deferred)
        val deferred = result as IndependentConstrainedMultiweekDecision.Deferred
        assertEquals(id, deferred.ruleId)
        return deferred
    }

    @Test fun oneShortMondayCanBeReallocatedAcrossAllFiveWeeks() {
        val baseline = IndependentWeeklyMileagePlanner.generate(input)
            as IndependentWeeklyMileageDecision.Candidate
        assertTrue(IndependentMileageFeasibility.check(baseline, limits())
            is IndependentMileageFeasibilityResult.Conflict)
        val out = candidate()
        assertEquals(5, out.mileage.weeks.size)
        assertEquals(listOf(60.0, 63.0, 66.15, 56.22, 69.45),
            out.mileage.weeks.map { it.weeklyKilometers })
        assertTrue(out.mileage.weeks.all {
            it.dayAllocations.single { a -> a.isoDay == 1 }.kilometers <= 5.0
        })
        assertTrue(IndependentMileageFeasibility.check(out.mileage, limits())
            is IndependentMileageFeasibilityResult.Checked)
    }

    @Test fun weekAndDailyTotalsRemainExactlyReconciledThroughRecovery() {
        val out = candidate()
        for ((week, typed) in out.mileage.weeks.zip(out.baseWorkouts.weeks)) {
            assertEquals(week.weeklyKilometers,
                week.dayAllocations.sumOf { it.kilometers }, 0.00001)
            assertEquals(week.weeklyKilometers, typed.weeklyKilometers, 0.00001)
            assertEquals(week.weeklyKilometers, typed.sessions.sumOf {
                it.workout.total(WorkoutUnit.METERS)!! / 1000.0
            }, 0.00001)
        }
        assertTrue(out.mileage.weeks[3].recoveryWeek)
        assertFalse(out.sourceEvidenceVerified)
        assertFalse(out.authorizedForProduction)
    }

    @Test fun longRunContinuityAndRecoveryCapAreEnforced() {
        val out = candidate()
        val longRuns = out.mileage.weeks.map { it.longRunKilometers!! }
        assertTrue(longRuns.first() <= 19.0 + 0.00001)
        assertTrue(longRuns[1] <= longRuns[0] + 2.0 + 0.00001)
        assertTrue(longRuns[2] <= longRuns[1] + 2.0 + 0.00001)
        assertTrue(longRuns[3] <= longRuns[2] + 0.00001)
        assertTrue(longRuns[4] <= longRuns[2] + 2.0 + 0.00001)
    }

    @Test fun lowAbsoluteLongRunCeilingCanChangeDistributionWithoutDroppingDistance() {
        val bounds = limits(longCap = 17.0)
        val out = candidate(ceilings = bounds)
        assertTrue(out.mileage.weeks.all { it.longRunKilometers!! <= 17.0 + 0.00001 })
        assertEquals(69.45, out.mileage.weeks.last().weeklyKilometers, 0.00001)
        assertTrue(IndependentMileageFeasibility.check(out.mileage, bounds)
            is IndependentMileageFeasibilityResult.Checked)
    }

    @Test fun impossibleDailyDistanceCeilingsDeferWithoutReducingTarget() {
        val caps = limits().copy(approvedMaxKmByDay =
            input.runningDays.associateWith { 5.0 })
        val result = reject("NO_FEASIBLE_DISTANCE_ALLOCATION", ceilings = caps)
        assertEquals(1, result.week)
    }

    @Test fun impossibleLongRunContinuityDefersAtFirstWeek() {
        val result = reject("NO_FEASIBLE_DISTANCE_ALLOCATION",
            ceilings = limits(priorLong = 8.0, maxLongIncrease = 1.0))
        assertEquals(1, result.week)
    }

    @Test fun missingCallerApprovedLimitsNeverBecomeImplicitDefaults() {
        reject("DAY_CEILINGS_NOT_SUPPLIED",
            ceilings = limits().copy(approvedMaxKmByDay = null))
        reject("DAY_CEILINGS_INCOMPLETE",
            ceilings = limits().copy(approvedMaxKmByDay = mapOf(1 to 5.0)))
        reject("LONG_RUN_LIMITS_INCOMPLETE",
            ceilings = limits().copy(approvedPreviousLongRunKm = null))
    }

    @Test fun unapprovedStartingLoadStillFailsOriginalM4Gate() {
        reject("STARTING_VOLUME_NOT_APPROVED",
            request = input.copy(approvedStartingWeeklyKm = null))
        reject("BASELINE_EXCEEDS_APPROVED_CEILING",
            request = input.copy(approvedWeeklyCeilingKm = 40.0))
    }

    @Test fun twoRunningDaysDoNotInventLongRunAndRemainBounded() {
        val request = input.copy(
            approvedStartingWeeklyKm = 10.0,
            approvedWeeklyCeilingKm = 30.0,
            requestedTargetWeeklyKm = 20.0,
            weeks = 4,
            runningDays = setOf(2, 6),
            preferredLongRunDay = 6
        )
        val bounds = IndependentMileageFeasibilityLimits(
            approvedMaxKmByDay = mapOf(2 to 15.0, 6 to 15.0)
        )
        val out = candidate(request, bounds)
        assertEquals(4, out.mileage.weeks.size)
        assertTrue(out.mileage.weeks.all { it.longRunKilometers == null })
        assertTrue(IndependentMileageFeasibility.check(out.mileage, bounds)
            is IndependentMileageFeasibilityResult.Checked)
    }

    @Test fun customPolicyMaximumLongRunShareIsHonored() {
        val policy = IndependentWeeklyMileagePolicy(
            preferredLongRunShare = 0.20, maximumLongRunShare = 0.25
        )
        val out = candidate(policy = policy)
        assertTrue(out.mileage.weeks.all {
            it.longRunKilometers!! <= it.weeklyKilometers * 0.25 + 0.01001
        })
        assertTrue(IndependentMileageFeasibility.check(out.mileage, limits(), policy)
            is IndependentMileageFeasibilityResult.Checked)
    }

    @Test fun aTwelveWeekForecastNeverClaimsFutureCompletionEvidence() {
        val out = candidate(input.copy(weeks = 12))
        assertEquals(12, out.mileage.weeks.size)
        assertTrue("M6A_NO_AUTOMATIC_FUTURE_FEEDBACK" in out.mileage.ruleIds)
        assertFalse(out.authorizedForProduction)
        assertTrue(out.baseWorkouts.weeks.all { it.sessions.all { s ->
            !s.qualityWorkAuthorized && s.estimatedDurationSeconds == null
        } })
    }

    @Test fun sameInputsAreDeterministicAndNotMutated() {
        val bounds = limits()
        val a = IndependentConstrainedMultiweekPlanner.generate(input, bounds)
        val b = IndependentConstrainedMultiweekPlanner.generate(input, bounds)
        assertEquals(a, b)
        assertEquals(60.0, input.approvedStartingWeeklyKm!!, 0.00001)
        assertEquals(5.0, bounds.approvedMaxKmByDay!![1]!!, 0.00001)
    }
}
