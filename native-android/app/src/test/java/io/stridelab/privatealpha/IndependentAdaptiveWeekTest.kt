package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class IndependentAdaptiveWeekTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val input = IndependentWeeklyMileageInput(60.0, 100.0, 80.0, 1, setOf(1,2,3,5,6,7), 7)
    private fun limits(shortDay: Boolean = false) = IndependentMileageFeasibilityLimits(
        input.runningDays.associateWith { if (shortDay && it == 1) 5.0 else 20.0 }, 17.0, 20.0, 1.0)
    private fun history(a: Double?, b: Double?) = IndependentRunningHistoryEvidence(
        listOf(60.0,60.0,a,b).mapIndexed { i, km -> IndependentRunningWeekEvidence(
            monday.minusWeeks((4-i).toLong()), km != null, 6, 6, km ?: 0.0, 0,
            km?.div(4), 0, km) })

    @Test fun constraintSearchFindsAllocationRejectedByPostHocBaseline() {
        val baseline = IndependentWeeklyMileagePlanner.generate(input) as IndependentWeeklyMileageDecision.Candidate
        assertTrue(IndependentMileageFeasibility.check(baseline, limits(true)) is IndependentMileageFeasibilityResult.Conflict)
        val result = IndependentAdaptiveWeek.allocate(input, limits(true)) as IndependentWeeklyMileageDecision.Candidate
        assertEquals(60.0, result.weeks.single().weeklyKilometers, 0.00001)
        assertTrue(IndependentMileageFeasibility.check(result, limits(true)) is IndependentMileageFeasibilityResult.Checked)
    }

    @Test fun identicalTargetRespondsToRecentCompletionWithoutMakingUpMissedDistance() {
        fun plan(a: Double, b: Double) = IndependentAdaptiveWeek.respond(input, limits(), history(a,b), monday,
            IndependentWeekResponse.TOLERATED) as IndependentWeeklyMileageDecision.Candidate
        assertEquals(60.0, plan(60.0,60.0).weeks.single().weeklyKilometers, 0.001)
        assertEquals(30.0, plan(30.0,30.0).weeks.single().weeklyKilometers, 0.001)
        assertFalse(plan(60.0,60.0).authorizedForProduction)
    }

    @Test fun unknownZeroStaleAndDifficultAreDistinctDeferrals() {
        fun rule(h: IndependentRunningHistoryEvidence, response: IndependentWeekResponse, date: LocalDate = monday) =
            (IndependentAdaptiveWeek.respond(input, limits(), h, date, response) as IndependentWeeklyMileageDecision.Deferred).ruleId
        assertEquals("RECENT_COVERAGE_UNKNOWN", rule(history(null,30.0), IndependentWeekResponse.TOLERATED))
        assertEquals("RETURN_TO_RUN_REVIEW", rule(history(0.0,0.0), IndependentWeekResponse.TOLERATED))
        assertEquals("RESPONSE_UNKNOWN", rule(history(60.0,60.0), IndependentWeekResponse.UNKNOWN))
        assertEquals("RECOVERY_REVIEW", rule(history(60.0,60.0), IndependentWeekResponse.DIFFICULT))
        assertEquals("STALE_HISTORY", rule(history(60.0,60.0), IndependentWeekResponse.TOLERATED, monday.plusWeeks(1)))
    }

    @Test fun noInventedFutureFeedbackOrIncompleteCeilings() {
        assertTrue(IndependentAdaptiveWeek.allocate(input.copy(weeks=2), limits()) is IndependentWeeklyMileageDecision.Deferred)
        assertTrue(IndependentAdaptiveWeek.allocate(input, limits().copy(approvedMaxKmByDay=null)) is IndependentWeeklyMileageDecision.Deferred)
    }

    @Test fun responseCannotRepairContradictoryApprovalAssertions() {
        val result = IndependentAdaptiveWeek.respond(input.copy(approvedWeeklyCeilingKm=40.0), limits(),
            history(30.0,30.0), monday, IndependentWeekResponse.TOLERATED)
        assertTrue(result is IndependentWeeklyMileageDecision.Deferred)
        assertEquals("BASELINE_EXCEEDS_APPROVED_CEILING", (result as IndependentWeeklyMileageDecision.Deferred).ruleId)
    }

    @Test fun deterministicSweepReconcilesAndRespectsEverySuppliedLimit() {
        for (days in 2..7) for (km in listOf(0.01,1.0,10.0,30.0,80.0,150.0)) {
            val request = input.copy(approvedStartingWeeklyKm=km, approvedWeeklyCeilingKm=200.0,
                requestedTargetWeeklyKm=km, runningDays=(1..days).toSet(), preferredLongRunDay=days)
            val bounds = IndependentMileageFeasibilityLimits((1..days).associateWith { 60.0 },60.0,60.0,0.0)
            val result = IndependentAdaptiveWeek.allocate(request,bounds)
            assertEquals(result, IndependentAdaptiveWeek.allocate(request,bounds))
            if (result is IndependentWeeklyMileageDecision.Candidate) {
                assertTrue(IndependentMileageFeasibility.check(result,bounds) is IndependentMileageFeasibilityResult.Checked)
                assertEquals(result.weeks.single().weeklyKilometers, result.weeks.single().dayAllocations.sumOf { it.kilometers }, 0.00001)
                assertTrue(result.weeks.single().dayAllocations.all { it.kilometers > 0 })
            }
        }
    }
}
