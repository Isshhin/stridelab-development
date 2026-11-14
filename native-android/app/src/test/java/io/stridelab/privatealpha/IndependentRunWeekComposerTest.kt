package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentRunWeekComposerTest {
    private fun allocation(
        baseline: Double = 60.0,
        ceiling: Double = 80.0,
        target: Double = 80.0,
        weeks: Int = 5,
        days: Set<Int> = setOf(1, 2, 3, 5, 6, 7),
        longDay: Int = 6
    ): IndependentWeeklyMileageDecision.Candidate {
        val generated = IndependentWeeklyMileagePlanner.generate(
            IndependentWeeklyMileageInput(
                approvedStartingWeeklyKm = baseline,
                approvedWeeklyCeilingKm = ceiling,
                requestedTargetWeeklyKm = target,
                weeks = weeks,
                runningDays = days,
                preferredLongRunDay = longDay
            )
        )
        assertTrue(generated.toString(), generated is IndependentWeeklyMileageDecision.Candidate)
        return generated as IndependentWeeklyMileageDecision.Candidate
    }
    private fun constraints(
        caps: Map<Int, Double>? = setOf(1, 2, 3, 5, 6, 7).associateWith { 25.0 },
        previousLong: Double? = 15.0,
        longCeiling: Double? = 25.0,
        longIncrease: Double? = 2.0
    ) = IndependentMileageFeasibilityLimits(caps, previousLong, longCeiling, longIncrease)

    private fun generate(
        a: IndependentWeeklyMileageDecision.Candidate = allocation(),
        l: IndependentMileageFeasibilityLimits = constraints()
    ): IndependentRunWeekCompositionDecision =
        IndependentRunWeekComposer.generate(a, l)

    private fun deferred(
        expected: String,
        result: IndependentRunWeekCompositionDecision
    ) {
        assertTrue(result.toString(), result is IndependentRunWeekCompositionDecision.Deferred)
        assertEquals(expected, (result as IndependentRunWeekCompositionDecision.Deferred).ruleId)
    }

    @Test fun verifiedFeasibilityProducesDeterministicTypedEasyAndLongDistanceWorkouts() {
        val result = generate()
        assertTrue(result.toString(), result is IndependentRunWeekCompositionDecision.Candidate)
        val composed = result as IndependentRunWeekCompositionDecision.Candidate
        assertEquals(5, composed.weeks.size)
        assertEquals(6, composed.weeks.first().sessions.size)
        assertTrue(composed.weeks.first().sessions.any {
            it.isoDay == 6 && it.kind == IndependentRunSessionKind.LONG
        })
        assertTrue(composed.weeks.first().sessions.filter { it.isoDay != 6 }.all {
            it.kind == IndependentRunSessionKind.EASY
        })
        assertEquals(60.0, composed.weeks.first().weeklyKilometers, 0.00001)
        assertFalse(composed.authorizedForProduction)
        assertFalse(composed.sourceEvidenceVerified)
        assertTrue("NO_UNVERIFIED_QUALITY_EXPOSURE" in composed.ruleIds)
        assertEquals(composed, generate())
    }

    @Test fun everyWorkoutIsAMeterBasedEasyWorkStepAndMatchesDayAllocation() {
        val result = generate() as IndependentRunWeekCompositionDecision.Candidate
        for (week in result.weeks) for (session in week.sessions) {
            assertEquals(session.allocatedKilometers * 1000.0,
                session.workout.total(WorkoutUnit.METERS)!!, 0.00001)
            val step = (session.workout.elements.single() as IndependentWorkoutElement.Step).value
            assertEquals(WorkoutUnit.METERS, step.unit)
            assertEquals(WorkoutPurpose.WORK, step.purpose)
            assertEquals(WorkoutTarget.EASY, step.target)
            assertEquals(1, step.repetitions)
            assertFalse(session.qualityWorkAuthorized)
            assertNull(session.estimatedDurationSeconds)
            assertNull(session.workout.total(WorkoutUnit.SECONDS))
        }
    }

    @Test fun weeklyTotalsMatchDailyWorkoutsEvenOnDeload() {
        val result = generate() as IndependentRunWeekCompositionDecision.Candidate
        for (week in result.weeks) {
            assertEquals(week.weeklyKilometers, week.sessions.sumOf {
                it.allocatedKilometers
            }, 0.00001)
        }
        assertTrue(result.weeks[3].recoveryWeek)
        assertTrue(result.weeks[3].weeklyKilometers < result.weeks[2].weeklyKilometers)
    }

    @Test fun missingDayCeilingsDefersRatherThanCreatingWorkouts() {
        deferred("FEASIBILITY_UNCHECKED_DAY_CEILINGS_NOT_SUPPLIED",
            generate(l = constraints(caps = null)))
    }

    @Test fun excessiveDailyMileageBlocksEntireWeekBeforeWorkoutCreation() {
        val caps = setOf(1, 2, 3, 5, 6, 7).associateWith {
            if (it == 1) 8.0 else 25.0
        }
        deferred("FEASIBILITY_CONFLICT_DAY_DISTANCE_CEILING_EXCEEDED",
            generate(l = constraints(caps = caps)))
    }

    @Test fun excessiveLongRunGrowthCannotBeHiddenByWorkoutComposition() {
        deferred("FEASIBILITY_CONFLICT_LONG_RUN_BUILD_INCREMENT_EXCEEDED",
            generate(l = constraints(previousLong = 10.0, longIncrease = 1.0)))
    }

    @Test fun sourceLimitAbsenceCannotBeSkippedAtComposerBoundary() {
        deferred("FEASIBILITY_UNCHECKED_PRIOR_LONG_RUN_NOT_SUPPLIED",
            generate(l = constraints(previousLong = null)))
    }

    @Test fun twoDayWeekProducesEasyRunsWithoutInventedLongRun() {
        val a = allocation(baseline = 20.0, ceiling = 25.0, target = 25.0,
            weeks = 2, days = setOf(3, 6), longDay = 6)
        val result = generate(a, constraints(
            caps = mapOf(3 to 15.0, 6 to 15.0),
            previousLong = null, longCeiling = null, longIncrease = null
        )) as IndependentRunWeekCompositionDecision.Candidate
        assertEquals(2, result.weeks.size)
        assertTrue(result.weeks.all { week ->
            week.sessions.size == 2 &&
                week.sessions.all { it.kind == IndependentRunSessionKind.EASY }
        })
    }

    @Test fun zeroDistanceAllocationsAreNotEncodedAsZeroAmountWorkoutSteps() {
        // A one-centi-kilometer week on two run days inevitably leaves
        // one day at 0.00 km. The composer must defer instead of
        // constructing a zero-amount WorkoutStep.
        val b = allocation(baseline = 0.01, ceiling = 0.01, target = 0.01,
            weeks = 1, days = setOf(3, 6), longDay = 6)
        deferred("ZERO_DISTANCE_RUN_DAY", generate(b, constraints(
            caps = mapOf(3 to 1.0, 6 to 1.0),
            previousLong = null, longCeiling = null, longIncrease = null
        )))
    }

    @Test fun unsupportedManipulatedWeekTotalsDoNotPassRecheck() {
        val original = allocation(weeks = 1)
        val first = original.weeks.single()
        val bad = first.copy(weeklyKilometers = first.weeklyKilometers + 1.0)
        deferred("FEASIBILITY_CONFLICT_WEEKLY_TOTAL_MISMATCH",
            generate(original.copy(weeks = listOf(bad))))
    }

    @Test fun composingDoesNotMutateM4AllocationOrProductionPlanData() {
        val original = allocation()
        val before = original.copy(weeks = original.weeks.map { it.copy(
            dayAllocations = it.dayAllocations.toList()
        ) })
        generate(original)
        assertEquals(before, original)
    }
}
