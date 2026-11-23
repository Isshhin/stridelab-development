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

    @Test fun multiDisciplineDoubleDaysAreAllowedWhileEmptyDaysOrZeroSessionsRequireInput() {
        val multiDay=PlanRequest(
            policyChoice="coordinated",days=(1..6).toSet(),
            runDays=6,strengthDays=2,calDays=0,conditioningDays=0,
            qualityCount=0
        )
        assertNull(compactScheduleError(multiDay))
        assertNotNull(compactScheduleError(multiDay.copy(days=emptySet())))
        assertNotNull(compactScheduleError(multiDay.copy(runDays=0,strengthDays=0)))
    }
}
