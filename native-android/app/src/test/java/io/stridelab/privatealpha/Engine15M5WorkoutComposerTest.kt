package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.floor
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * M5A shadow-only acceptance. The candidate is NOT a calendar, quality dose
 * decision, executable running prescription or source-verified approval.
 */
class Engine15M5WorkoutComposerTest {
    private val composer = Engine15M5WorkoutComposer

    private fun source(
        recentKm: Double = 75.0,
        targetKm: Double = 80.0,
        longestKm: Double = 20.0,
        runningDays: Int = 6,
        weeks: Int = 8,
        returning: Boolean = false,
        basis: String = "SELF_REPORTED_AVERAGE",
        recoveryHold: Boolean = false,
        experience: Int = 2
    ): Engine15IndependentVolumePlanner.Result {
        val request = PlanRequest(
            goal = "Aerobic foundation", experience = experience,
            start = LocalDate.of(2026, 11, 16),
            weeks = weeks, days = setOf(1, 2, 3, 4, 5, 6, 7),
            runDays = runningDays, longDay = 7, qualityCount = 0,
            weeklyKm = recentKm, targetWeeklyKm = targetKm,
            longestKm = longestKm, returning = returning,
            policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = basis, runningRecoveryHold = recoveryHold
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(request),
            Engine15IndependentVolumePlanner.Input(
                weeks, runningDays, targetKm, returning, recoveryHold
            )
        )
    }

    private fun candidate(
        result: Engine15IndependentVolumePlanner.Result
    ): Engine15M5CompositionDecision.Candidate {
        val decision = composer.compose(result)
        assertTrue("Expected candidate, got $decision",
            decision is Engine15M5CompositionDecision.Candidate)
        return decision as Engine15M5CompositionDecision.Candidate
    }

    private fun deferred(
        result: Engine15IndependentVolumePlanner.Result,
        expected: String
    ) {
        val decision = composer.compose(result)
        assertTrue("Expected deferral, got $decision",
            decision is Engine15M5CompositionDecision.Deferred)
        assertEquals(expected,
            (decision as Engine15M5CompositionDecision.Deferred).reasonCode)
    }

    private fun assertBounded(
        source: Engine15IndependentVolumePlanner.Result,
        result: Engine15M5CompositionDecision.Candidate
    ) {
        assertFalse(result.scheduled)
        assertFalse(result.qualityAuthorized)
        assertFalse(result.authorizedForProduction)
        assertEquals(source.weeks.size, result.weeks.size)
        val longest = source.athleteBaseline.reportedLongestRunKm!!
        val easyMax = floor((longest + 1.0) * 8.0 + 1e-8) / 10.0
        val longMax = floor((longest + 1.0) * 10.0 + 1e-8) / 10.0
        for ((index, week) in result.weeks.withIndex()) {
            val m4b = source.weeks[index]
            assertEquals(m4b.proposedKm!!, week.budgetKm, 1e-9)
            assertEquals(m4b.kind, week.kind)
            assertEquals(source.input.runningDays, week.slots.size)
            assertEquals(week.budgetKm, week.slots.sumOf { it.distanceKm }, 1e-8)
            assertTrue(week.budgetKm <= m4b.existingPolicyCeilingKm!! + 1e-8)
            val longs = week.slots.filter { it.role == Engine15M5RunRole.LONG }
            assertEquals(if (source.input.runningDays >= 3) 1 else 0, longs.size)
            for (slot in week.slots) {
                assertFalse(slot.dateAssigned)
                assertFalse(slot.qualityAuthorized)
                assertTrue(slot.distanceKm > 0.0)
                assertEquals(slot.distanceKm * 10.0,
                    floor(slot.distanceKm * 10.0 + 1e-8), 1e-8)
                assertEquals(slot.distanceKm * 1000.0,
                    slot.workout.total(WorkoutUnit.METERS)!!, 1e-6)
                assertNull(slot.workout.total(WorkoutUnit.SECONDS))
                val step = (slot.workout.elements.single() as IndependentWorkoutElement.Step).value
                assertEquals(WorkoutPurpose.WORK, step.purpose)
                assertEquals(WorkoutUnit.METERS, step.unit)
                assertEquals(WorkoutTarget.EASY, step.target)
                assertEquals(1, step.repetitions)
                if (slot.role == Engine15M5RunRole.LONG) {
                    assertTrue(slot.distanceKm <= longMax + 1e-8)
                    assertTrue(slot.distanceKm <= week.budgetKm * 0.4 + 1e-8)
                } else {
                    assertTrue(slot.distanceKm <= easyMax + 1e-8)
                }
            }
            if (week.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY) {
                assertTrue(week.budgetKm <= result.weeks[index-1].budgetKm * 0.8 + 1e-8)
                if (longs.isNotEmpty()) {
                    val prior = result.weeks[index-1].slots.single {
                        it.role == Engine15M5RunRole.LONG
                    }
                    assertTrue(longs.single().distanceKm <= prior.distanceKm + 1e-8)
                }
            }
        }
    }

    @Test fun sixDaySeventyFiveToEightyIsExactUnscheduledAndWithinAllBounds() {
        val budget = source()
        val result = candidate(budget)
        assertEquals(listOf(67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9),
            result.weeks.map { it.budgetKm })
        assertBounded(budget, result)
        assertEquals(78.7, result.weeks.maxOf { it.budgetKm }, 1e-8)
        assertEquals(1.3, budget.shortfallToRequestedTargetKm!!, 1e-8)
        assertEquals(Engine15IndependentVolumePlanner.TargetStatus.TARGET_NOT_REACHED_IN_BUDGET,
            budget.targetStatus)
    }

    @Test fun threeDayFiftyFourPointSixIsExactlyExposureLimited() {
        val budget = source(runningDays = 3)
        val result = candidate(budget)
        val first = result.weeks.first()
        assertEquals(54.6, first.budgetKm, 1e-8)
        assertEquals(listOf(16.8, 16.8, 21.0),
            first.slots.map { it.distanceKm })
        assertBounded(budget, result)
    }

    @Test fun twoDayUpperBoundIsNotAutomaticallyFeasible() {
        deferred(source(runningDays = 2), "TWO_DAY_EXPOSURE_CONFLICT")
    }

    @Test fun smallTwoDayWeekHasNoFabricatedLongRun() {
        val budget = source(recentKm = 20.0, targetKm = 25.0,
            runningDays = 2, weeks = 1)
        val result = candidate(budget)
        assertEquals(listOf(9.0, 9.0), result.weeks.first().slots.map { it.distanceKm })
        assertBounded(budget, result)
    }

    @Test fun missingLongestDoesNotAuthorizeAnyWorkout() {
        deferred(source(longestKm = 0.0), "LONGEST_RUN_EXPOSURE_UNKNOWN")
    }

    @Test fun unknownLegacyZeroAndRecoveryHoldNeverCreateNumericWorkouts() {
        deferred(source(recentKm = 0.0, basis = "UNKNOWN"),
            "M4B_NON_DISTANCE_STATUS")
        deferred(source(recentKm = 0.0, longestKm = 0.0,
            basis = "CONFIRMED_ZERO", runningDays = 3, experience = 0),
            "M4B_NON_DISTANCE_STATUS")
        deferred(source(basis = "LEGACY_UNSPECIFIED"),
            "M4B_NON_DISTANCE_STATUS")
        deferred(source(recoveryHold = true), "M4B_NON_DISTANCE_STATUS")
    }

    @Test fun tamperedM4bWeekCannotEnterM5() {
        val original = source()
        val changed = original.copy(
            weeks = original.weeks.mapIndexed { index, week ->
                if (index == 0) week.copy(proposedKm = 100.0) else week
            }
        )
        deferred(changed, "M4B_RESULT_MISMATCH")
    }

    @Test fun returningRunnerRemainsBoundedAndRecoveryDoesNotIncreaseLong() {
        val budget = source(recentKm = 40.0, targetKm = 50.0,
            longestKm = 12.0, runningDays = 4, returning = true)
        val result = candidate(budget)
        assertEquals(24.0, result.weeks.first().budgetKm, 1e-8)
        assertEquals(21.1, result.weeks[3].budgetKm, 1e-8)
        assertBounded(budget, result)
    }

    @Test fun profilesMayDeferButNeverEmitInvalidSessions() {
        var accepted = 0
        var deferred = 0
        for (weekly in listOf(10.0, 20.0, 30.0, 50.0, 75.0, 100.0))
            for (days in listOf(2, 3, 4, 6))
                for (returning in listOf(false, true)) {
                    val longest = maxOf(1.0, weekly / days * 1.2)
                    val input = source(recentKm = weekly, targetKm = 80.0,
                        longestKm = longest, runningDays = days,
                        returning = returning, weeks = 8)
                    when (val decision = composer.compose(input)) {
                        is Engine15M5CompositionDecision.Candidate -> {
                            accepted++
                            assertBounded(input, decision)
                        }
                        is Engine15M5CompositionDecision.Deferred -> {
                            deferred++
                            assertTrue(decision.reasonCode.isNotBlank())
                        }
                    }
                }
        assertTrue(accepted > 0)
        assertTrue(deferred > 0)
        assertEquals(48, accepted + deferred)
    }
}
