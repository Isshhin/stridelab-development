package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/**
 * Multi-step synthetic trajectory regression tests; comparison is between
 * heuristic research candidates, NOT between proven athlete-safe policies.
 */
class IndependentMultiweekTrajectoryComparisonTest {
    private val initialMonday = LocalDate.of(2026, 11, 16)
    private val volume = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 1, setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val limits = IndependentMileageFeasibilityLimits(
        volume.runningDays.associateWith { 25.0 }, 20.0, 25.0, 2.0
    )

    private fun observed(
        planning: LocalDate, priorPlanned: Double, priorActual: Double,
        recentPlanned: Double, recentActual: Double,
        recovery: Boolean = false
    ) = listOf(
        IndependentObservedTrainingWeek(planning.minusWeeks(2),
            priorPlanned, priorActual, true, IndependentObservedWeekIntent.BUILD,
            true, IndependentWeekResponse.TOLERATED),
        IndependentObservedTrainingWeek(planning.minusWeeks(1),
            recentPlanned, recentActual, true,
            if (recovery) IndependentObservedWeekIntent.PLANNED_RECOVERY
                else IndependentObservedWeekIntent.BUILD,
            recovery, IndependentWeekResponse.TOLERATED)
    )

    private fun preview(
        planning: LocalDate, weeks: List<IndependentObservedTrainingWeek>,
        increase: Double = 0.0
    ): IndependentMultiweekResponseDecision.Preview {
        val decision = IndependentMultiweekResponse.preview(
            IndependentMultiweekResponseInput(volume, limits, planning, weeks,
                increase, if (increase > 0.0) "synthetic-reviewed-progress" else null)
        )
        assertTrue(decision.toString(), decision is IndependentMultiweekResponseDecision.Preview)
        return decision as IndependentMultiweekResponseDecision.Preview
    }

    @Test fun multiweekMissedRepeatRebuildSequenceDoesNotCatchUpToOldBaseline() {
        // Two 30/60 km undercompletions cause a reduced repeat proposal.
        val first = preview(initialMonday, observed(
            initialMonday, 60.0, 30.0, 60.0, 30.0
        ))
        assertEquals(30.0, first.volume.weeks.single().weeklyKilometers, 0.00001)

        // The athlete then completes two 30 km build weeks. Successful
        // completion must NOT restore the original 60 km starting assertion.
        val nextMonday = initialMonday.plusWeeks(2)
        val second = preview(nextMonday, observed(
            nextMonday, 30.0, 30.0, 30.0, 30.0
        ))
        assertEquals(30.0, second.volume.weeks.single().weeklyKilometers, 0.00001)

        // Even with further completion, only an independent explicitly
        // reviewed 2 km increase may change the research candidate.
        val thirdMonday = initialMonday.plusWeeks(4)
        val third = preview(thirdMonday, observed(
            thirdMonday, 30.0, 30.0, 30.0, 30.0
        ), increase = 2.0)
        assertEquals(32.0, third.volume.weeks.single().weeklyKilometers, 0.00001)
        assertTrue(listOf(first, second, third).all {
            !it.authorizedForProduction && !it.sourceEvidenceVerified
        })
    }

    @Test fun plannedRecoveryVsMissedBuildWithSameFortyKmAreDifferentCases() {
        val recovery = preview(initialMonday, observed(
            initialMonday, 60.0, 60.0, 40.0, 40.0, recovery = true
        ))
        assertEquals(60.0, recovery.volume.weeks.single().weeklyKilometers, 0.00001)
        assertEquals("REVIEWED_RECOVERY_RESUME_PRIOR_BUILD_NO_CATCHUP", recovery.reason)

        val missed = IndependentMultiweekResponse.preview(
            IndependentMultiweekResponseInput(volume, limits, initialMonday,
                observed(initialMonday, 60.0, 60.0, 60.0, 40.0),
                0.0, null)
        )
        assertTrue(missed.toString(), missed is IndependentMultiweekResponseDecision.Deferred)
        assertEquals("MIXED_OR_PARTIAL_COMPLETION_REVIEW",
            (missed as IndependentMultiweekResponseDecision.Deferred).ruleId)
    }

    @Test fun theOldTwoWeekMinimumUnderloadsThePlannedRecoveryExample() {
        val monday = initialMonday
        val evidence = IndependentRunningHistoryEvidence(
            listOf(60.0, 60.0, 60.0, 40.0).mapIndexed { index, km ->
                IndependentRunningWeekEvidence(
                    monday.minusWeeks((4 - index).toLong()),
                    true, 6, 6, km, 0, km / 4.0, 0, km
                )
            }
        )
        val old = IndependentAdaptiveWeek.respond(volume, limits,
            evidence, monday, IndependentWeekResponse.TOLERATED)
        assertTrue(old.toString(), old is IndependentWeeklyMileageDecision.Candidate)
        assertEquals(40.0,
            (old as IndependentWeeklyMileageDecision.Candidate).weeks.single().weeklyKilometers,
            0.00001)
        val new = preview(monday, observed(monday, 60.0, 60.0,
            40.0, 40.0, recovery = true))
        assertEquals(60.0, new.volume.weeks.single().weeklyKilometers, 0.00001)
        // Difference is intent-awareness, not an externally validated
        // recommendation that 60 km is safe for this athlete.
    }
}
