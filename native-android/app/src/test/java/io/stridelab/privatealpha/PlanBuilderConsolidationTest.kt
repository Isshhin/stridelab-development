package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanBuilderConsolidationTest {
    @Test
    fun legacySavedRequestsWithConditioningStillRoundTripAndValidateCleanly() {
        val request = PlanRequest(
            runDays = 3,
            strengthDays = 1,
            calDays = 1,
            conditioningDays = 1,
            experience = 1,
            planningPace = 330.0,
            weeklyKm = 28.0,
            longestKm = 10.0,
            targetWeeklyKm = 34.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false,
            goal = "10K",
            days = setOf(1, 2, 3, 4, 5, 7),
            longDay = 7
        )
        val decoded = PlanRequest.decode(request.encode())
        assertEquals(1, decoded.conditioningDays)
        Engine15IndependentPlanGenerator.validateRequest(decoded)
        assertEquals(4, HybridPlanBuilder.split(decoded, emptySet()).size)
    }
}
