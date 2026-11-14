package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeekReviewDistanceTest {
    private fun run(km: Double?) = PlannedSession("run",LocalDate.of(2026,11,16),"run","Easy",30,km,"easy")
    @Test fun sumsOnlyDisplayedRunningPrescriptions() {
        assertEquals(12.5, weekReviewDistance(listOf(run(5.0),run(7.5),run(99.0).copy(discipline="str")))!!,0.00001)
    }
    @Test fun unknownDistanceNeverBecomesZeroOrPartialTotal() {
        assertNull(weekReviewDistance(listOf(run(5.0),run(null))))
        assertNull(weekReviewDistance(listOf(run(Double.NaN))))
        assertNull(weekReviewDistance(listOf(run(-1.0))))
    }
    @Test fun emptyRunningWeekHasZeroPlannedDistance() {
        assertEquals(0.0, weekReviewDistance(emptyList())!!,0.00001)
    }
}
