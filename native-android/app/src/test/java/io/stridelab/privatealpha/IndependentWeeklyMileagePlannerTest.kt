package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentWeeklyMileagePlannerTest {
    private fun input(
        baseline: Double? = 60.0,
        ceiling: Double? = 80.0,
        target: Double? = 80.0,
        weeks: Int = 8,
        days: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 6
    ) = IndependentWeeklyMileageInput(
        approvedStartingWeeklyKm = baseline,
        approvedWeeklyCeilingKm = ceiling,
        requestedTargetWeeklyKm = target,
        weeks = weeks,
        runningDays = days,
        preferredLongRunDay = longDay
    )
    private fun plan(
        input: IndependentWeeklyMileageInput = input(),
        policy: IndependentWeeklyMileagePolicy = IndependentWeeklyMileagePolicy()
    ): IndependentWeeklyMileageDecision.Candidate {
        val result = IndependentWeeklyMileagePlanner.generate(input, policy)
        assertTrue(result.toString(), result is IndependentWeeklyMileageDecision.Candidate)
        return result as IndependentWeeklyMileageDecision.Candidate
    }
    private fun defer(
        rule: String, value: IndependentWeeklyMileageInput
    ) {
        val result = IndependentWeeklyMileagePlanner.generate(value)
        assertTrue(result.toString(), result is IndependentWeeklyMileageDecision.Deferred)
        assertEquals(rule, (result as IndependentWeeklyMileageDecision.Deferred).ruleId)
    }

    @Test fun unapprovedM3CapacityCannotSilentlyBecomeTrainingMileage() {
        defer("STARTING_VOLUME_NOT_APPROVED", input(baseline = null))
        defer("WEEKLY_CEILING_NOT_APPROVED", input(ceiling = null))
        defer("RETURN_TO_RUN_BASELINE_REQUIRED", input(baseline = 0.0))
        defer("BASELINE_EXCEEDS_APPROVED_CEILING", input(baseline = 90.0, ceiling = 80.0))
    }

    @Test fun weekOneStartsAtApprovedVolumeNotTarget() {
        val result = plan()
        assertEquals(60.0, result.weeks.first().weeklyKilometers, 0.001)
        assertEquals(80.0, result.requestedTargetWeeklyKm!!, 0.001)
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun increasesAreBoundedByExplicitCeilingAndTarget() {
        val result = plan(input(baseline = 60.0, ceiling = 70.0, target = 100.0, weeks = 12))
        assertTrue(result.weeks.all { it.weeklyKilometers <= 70.0 })
        assertEquals(false, result.targetReachedOnBuildWeek)
        assertTrue("TARGET_EXCEEDS_APPROVED_CEILING" in result.ruleIds)
        assertTrue("REQUESTED_TARGET_NOT_REACHED" in result.ruleIds)
        val builds = result.weeks.filter { !it.recoveryWeek }
        assertTrue(builds.zipWithNext().all { (a, b) ->
            b.buildReferenceKilometers <= a.buildReferenceKilometers * 1.05 + 0.001
        })
    }

    @Test fun recoveryWeekDropsAndNoCatchUpIsAdded() {
        val result = plan(input(weeks = 6))
        assertEquals(60.0, result.weeks[0].weeklyKilometers, 0.001)
        assertEquals(63.0, result.weeks[1].weeklyKilometers, 0.001)
        assertEquals(66.15, result.weeks[2].weeklyKilometers, 0.001)
        assertTrue(result.weeks[3].recoveryWeek)
        assertEquals(56.22, result.weeks[3].weeklyKilometers, 0.001)
        assertEquals(69.45, result.weeks[4].weeklyKilometers, 0.001)
        assertTrue("HEURISTIC_RECOVERY_WITHOUT_CATCHUP" in result.weeks[3].ruleIds)
    }

    @Test fun targetLowerThanBaselineIsHonoredWithoutForcedGrowth() {
        val result = plan(input(baseline = 70.0, ceiling = 80.0, target = 50.0, weeks = 3))
        assertTrue(result.weeks.all { it.weeklyKilometers == 50.0 })
        assertEquals(true, result.targetReachedOnBuildWeek)
        assertTrue("LOWER_TARGET_OVERRIDES_START" in result.ruleIds)
    }

    @Test fun noRequestedTargetHoldsBaselineExceptRecovery() {
        val result = plan(input(target = null, weeks = 5))
        assertEquals(listOf(60.0, 60.0, 60.0, 51.0, 60.0),
            result.weeks.map { it.weeklyKilometers })
        assertNull(result.targetReachedOnBuildWeek)
    }

    @Test fun dayKilometersAlwaysSumToWeeklyAndNoUnscheduledDaysAppear() {
        val result = plan(input(baseline = 37.13, ceiling = 80.0, target = 80.0))
        for (week in result.weeks) {
            assertEquals(week.weeklyKilometers,
                week.dayAllocations.sumOf { it.kilometers }, 0.00001)
            assertEquals(listOf(1, 2, 3, 5, 6, 7),
                week.dayAllocations.map { it.isoDay })
            assertEquals(1, week.dayAllocations.count { it.longRunCandidate })
            assertTrue(week.longRunKilometers!! <= week.weeklyKilometers * 0.35 + 0.01)
            assertTrue(week.dayAllocations.all { it.kilometers >= 0.0 })
        }
    }

    @Test fun twoRunningDaysAreEvenWithNoInventedLongRun() {
        val result = plan(input(baseline = 20.0, ceiling = 30.0, target = 30.0,
            weeks = 1, days = setOf(3, 6), longDay = 6))
        assertEquals(listOf(10.0, 10.0), result.weeks.single()
            .dayAllocations.map { it.kilometers })
        assertNull(result.weeks.single().longRunKilometers)
        assertTrue(result.weeks.single().dayAllocations.none { it.longRunCandidate })
    }

    @Test fun missingTrainingDaysAndUnavailableLongDayDefer() {
        defer("INSUFFICIENT_RUN_DAYS", input(days = setOf(6)))
        defer("LONG_DAY_UNAVAILABLE", input(days = setOf(1, 3, 5), longDay = 6))
    }

    @Test fun noForcedOneWeekJumpToUnrealisticTarget() {
        val result = plan(input(baseline = 25.0, ceiling = 150.0,
            target = 150.0, weeks = 1, days = setOf(1, 3, 5), longDay = 5))
        assertEquals(25.0, result.weeks.single().weeklyKilometers, 0.001)
        assertEquals(false, result.targetReachedOnBuildWeek)
    }

    @Test fun inputsAreDeterministicAndDoNotMutateOwnedData() {
        val requested = input()
        val first = plan(requested)
        val second = plan(requested)
        assertEquals(first, second)
        assertEquals(60.0, requested.approvedStartingWeeklyKm!!, 0.001)
        assertEquals("HEURISTIC_UNVALIDATED", first.policyEvidence)
    }

    @Test fun impossibleLongShareDefersRatherThanDistributingUncheckedLoad() {
        val strict = IndependentWeeklyMileagePolicy(
            preferredLongRunShare = 0.20, maximumLongRunShare = 0.20)
        val output = IndependentWeeklyMileagePlanner.generate(
            input(days = setOf(1, 3, 5), longDay = 5), strict
        )
        assertEquals("LONG_RUN_SHARE_CONFLICT",
            (output as IndependentWeeklyMileageDecision.Deferred).ruleId)
    }
}
