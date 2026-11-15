package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15MileageOptionsTest {
    private val day=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private fun request()=PlanRequest(goal="Aerobic foundation",experience=2,start=day,weeks=4,days=(1..7).toSet(),runDays=6,longDay=7,weeklyKm=40.0,longestKm=18.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,targetWeeklyKm=45.0)
    @Test fun missedGoalOffersLowerTargetAndAnActuallyGeneratedLongerTimeline() {
        val p=Engine15.generate(request(),"synthetic","draft",now)
        assertTrue(p.explanations.any { it.startsWith("LOWER_TARGET_OPTION:") })
        val option=p.explanations.single { it.startsWith("LONGER_TIMELINE_OPTION:") }
        assertFalse(option.contains("not established"))
        assertEquals(request(),p.request)
    }
    @Test fun exposureBottleneckCannotBeSolvedByInventingExtraWeeks() {
        val p=Engine15.generate(request().copy(runDays=2,weeklyKm=80.0,longestKm=12.0,targetWeeklyKm=80.0),"synthetic","draft",now)
        assertTrue(p.explanations.single { it.startsWith("LONGER_TIMELINE_OPTION:") }.contains("not established"))
        assertTrue(p.explanations.any { it.startsWith("LOWER_TARGET_OPTION:") })
    }
}
