package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class CompactSchedulePolicyTest {
    @Test fun ordinarySingleDisciplineWeekCanContinue() {
        val request=PlanRequest(
            policyChoice="coordinated",days=(1..7).toSet(),
            runDays=3,strengthDays=0,calDays=0,conditioningDays=0,
            qualityCount=0
        )
        assertNull(compactScheduleError(request))
    }

    @Test fun noAvailabilityOrOverCapacityMustBeReviewedBeforeGeneration() {
        val request=PlanRequest(
            policyChoice="coordinated",days=(1..6).toSet(),
            runDays=6,strengthDays=2,calDays=0,conditioningDays=0,
            qualityCount=0
        )
        assertNotNull(compactScheduleError(request))
        assertNotNull(compactScheduleError(request.copy(days=emptySet())))
    }
}
