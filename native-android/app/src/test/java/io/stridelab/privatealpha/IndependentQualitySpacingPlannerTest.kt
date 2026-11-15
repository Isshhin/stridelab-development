package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class IndependentQualitySpacingPlannerTest {
    private val input = IndependentWeeklyMileageInput(
        60.0, 80.0, 80.0, 5,
        setOf(1, 2, 3, 5, 6, 7), 6
    )
    private val bounds = IndependentMileageFeasibilityLimits(
        input.runningDays.associateWith { if (it == 1) 5.0 else 25.0 },
        17.0, 25.0, 2.0
    )
    private fun base(
        plan: IndependentWeeklyMileageInput = input,
        limits: IndependentMileageFeasibilityLimits = bounds
    ): IndependentConstrainedMultiweekDecision.Candidate {
        val result = IndependentConstrainedMultiweekPlanner.generate(plan, limits)
        assertTrue(result.toString(), result is IndependentConstrainedMultiweekDecision.Candidate)
        return result as IndependentConstrainedMultiweekDecision.Candidate
    }
    private fun accept(
        request: IndependentQualitySpacingRequest,
        candidate: IndependentConstrainedMultiweekDecision.Candidate = base()
    ): IndependentQualitySpacingDecision.Candidate {
        val result = IndependentQualitySpacingPlanner.propose(candidate, request)
        assertTrue(result.toString(), result is IndependentQualitySpacingDecision.Candidate)
        return result as IndependentQualitySpacingDecision.Candidate
    }
    private fun reject(
        code: String,
        request: IndependentQualitySpacingRequest,
        candidate: IndependentConstrainedMultiweekDecision.Candidate = base()
    ): IndependentQualitySpacingDecision.Deferred {
        val result = IndependentQualitySpacingPlanner.propose(candidate, request)
        assertTrue(result.toString(), result is IndependentQualitySpacingDecision.Deferred)
        val d = result as IndependentQualitySpacingDecision.Deferred
        assertEquals(code, d.ruleId)
        return d
    }

    @Test fun sixDaySaturdayLongPlanPicksTuesdayAsOneQualityOpportunity() {
        val result = accept(IndependentQualitySpacingRequest(1))
        assertEquals(5, result.weeks.size)
        assertEquals(listOf(2), result.weeks[0].opportunities.map { it.isoDay })
        assertTrue(result.weeks[3].heldForRecovery)
        assertTrue(result.weeks[3].opportunities.isEmpty())
        assertEquals(listOf(2), result.weeks[4].opportunities.map { it.isoDay })
    }

    @Test fun twoQualityOpportunitiesAreSeparatedAndNotAdjacentToLongRun() {
        val result = accept(IndependentQualitySpacingRequest(2))
        for (week in result.weeks.filter { !it.recoveryWeek }) {
            assertEquals(listOf(1, 3), week.opportunities.map { it.isoDay })
            assertTrue(week.opportunities.all { it.isoDay !in setOf(5, 6, 7) })
        }
    }

    @Test fun preferredDayCanOverrideEqualDistanceTieWithoutChangingWorkouts() {
        val result = accept(IndependentQualitySpacingRequest(
            1, preferredQualityDays = setOf(3)
        ))
        assertEquals(3, result.weeks.first().opportunities.single().isoDay)
        assertEquals(60.0, base().mileage.weeks.first().weeklyKilometers, 0.00001)
    }

    @Test fun explicitBlockedTuesdayMovesOpportunityToWednesday() {
        val result = accept(IndependentQualitySpacingRequest(
            1, blockedDaysByWeek = mapOf(1 to setOf(2))
        ))
        assertEquals(3, result.weeks.first().opportunities.single().isoDay)
        assertEquals(2, result.weeks[1].opportunities.single().isoDay)
    }

    @Test fun impossibleBlockedCalendarDefersWholeResearchCandidate() {
        val d = reject("NO_SPACED_QUALITY_OPPORTUNITIES",
            IndependentQualitySpacingRequest(
                1, blockedDaysByWeek = mapOf(2 to setOf(1, 2, 3))
            ))
        assertEquals(2, d.week)
    }

    @Test fun higherMinimumSeparationCanMakeTwoQualitySlotsImpossible() {
        val d = reject("NO_SPACED_QUALITY_OPPORTUNITIES",
            IndependentQualitySpacingRequest(
                2, minimumCalendarDaySeparation = 3
            ))
        assertEquals(1, d.week)
    }

    @Test fun noQualityRequestedProducesNoSlotsAndNoFakeDose() {
        val result = accept(IndependentQualitySpacingRequest(0))
        assertTrue(result.weeks.all { it.opportunities.isEmpty() })
        assertFalse(result.qualityDoseApproved)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun everyProposedSlotReferencesAnExistingEasyDistanceRunWithoutMutatingIt() {
        val original = base()
        val result = accept(IndependentQualitySpacingRequest(1), original)
        for ((week, scheduled) in original.baseWorkouts.weeks.zip(result.weeks)) {
            for (opportunity in scheduled.opportunities) {
                val baseRun = week.sessions.single { it.isoDay == opportunity.isoDay }
                assertEquals(IndependentRunSessionKind.EASY, baseRun.kind)
                assertEquals(baseRun.allocatedKilometers,
                    opportunity.preexistingEasyRunKm, 0.00001)
                assertFalse(opportunity.executable)
                assertTrue(opportunity.requiresSourceAndDoseReview)
            }
        }
        assertFalse(result.sourceEvidenceVerified)
        assertFalse(result.authorizedForProduction)
    }

    @Test fun blockingOutOfHorizonWeekIsExplicitlyRejected() {
        reject("BLOCKED_WEEK_OUTSIDE_HORIZON",
            IndependentQualitySpacingRequest(
                1, blockedDaysByWeek = mapOf(8 to setOf(2))
            ))
    }

    @Test fun twoRunDayScheduleCannotImplyQualityEligibility() {
        val plan = input.copy(
            approvedStartingWeeklyKm = 10.0,
            approvedWeeklyCeilingKm = 20.0,
            requestedTargetWeeklyKm = 20.0,
            runningDays = setOf(2, 6), preferredLongRunDay = 6, weeks = 2
        )
        val limit = IndependentMileageFeasibilityLimits(mapOf(2 to 15.0, 6 to 15.0))
        val d = reject("QUALITY_SLOT_INSUFFICIENT_RUN_DAYS",
            IndependentQualitySpacingRequest(1), base(plan, limit))
        assertEquals(1, d.week)
    }

    @Test fun adjacentQualitySlotsAcrossTwoDifferentWeeksAreRejected() {
        val plan = input.copy(
            runningDays = (1..7).toSet(),
            preferredLongRunDay = 3, weeks = 2
        )
        val limit = IndependentMileageFeasibilityLimits(
            plan.runningDays.associateWith { 25.0 }, 17.0, 25.0, 2.0
        )
        val d = reject("NO_SPACED_QUALITY_OPPORTUNITIES",
            IndependentQualitySpacingRequest(1,
                preferredQualityDays = setOf(7),
                blockedDaysByWeek = mapOf(2 to setOf(5, 6, 7))),
            base(plan, limit))
        assertEquals(2, d.week)
    }

    @Test fun deterministicResultDoesNotAuthorizeTimeIntensityOrMileage() {
        val request = IndependentQualitySpacingRequest(1)
        val original = base()
        val a = IndependentQualitySpacingPlanner.propose(original, request)
        val b = IndependentQualitySpacingPlanner.propose(original, request)
        assertEquals(a, b)
        assertTrue("NO_QUALITY_DISTANCE_RECONCILIATION" in
            (a as IndependentQualitySpacingDecision.Candidate).ruleIds)
        assertTrue(a.weeks.all { week ->
            week.opportunities.all { it.proposedOnly && !it.executable }
        })
    }
}
