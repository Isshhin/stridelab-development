package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

class AutomaticRunningBaselineTest {
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")

    private fun run(id:String,date:LocalDate,km:Double?):ActivityRecord {
        return ActivityRecord(id,"Training run","running","garmin",
            date.atStartOfDay(ZoneOffset.UTC).toInstant(),
            km?.times(1000),3600.0,3600.0,null,null,null)
    }

    private fun week(weeksBack:Long,kms:List<Double?>):List<ActivityRecord> {
        val date=monday.minusWeeks(weeksBack)
        return kms.mapIndexed { i,km -> run("wk$weeksBack-$i",
            date.plusDays((i*6 / kms.size).toLong()),km) }
    }

    @Test fun recentCompletedWeeksAreSuggestedWithoutSelectingChips() {
        val history=week(1,listOf(10.0,10.0,19.6))+
            week(2,listOf(28.0,28.0,28.1))+
            week(3,listOf(24.0,24.0,23.6))+
            week(0,listOf(5.0,5.0,5.0,5.0))
        val result=automaticRunningBaseline(history,now,
            zone=ZoneOffset.UTC)!!
        assertEquals(65.1,result.weeklyKm,0.001)
        assertEquals(3,result.weeks.size)
        assertFalse(result.weeks.any { it.start==monday })
    }

    @Test fun oneWeekOrIncompleteDistanceDoesNotProveUsualMileage() {
        assertNull(automaticRunningBaseline(
            week(1,listOf(9.0,9.0,9.0)),now,zone=ZoneOffset.UTC))
        assertNull(automaticRunningBaseline(
            week(1,listOf(9.0,9.0,9.0))+
                week(2,listOf(10.0,null,10.0)),now,zone=ZoneOffset.UTC))
        assertNull(automaticRunningBaseline(
            week(1,listOf(12.0,12.0))+
                week(2,listOf(12.0,12.0)),now,zone=ZoneOffset.UTC))
    }

    @Test fun incompleteCurrentWeekIsNeverUsedEvenWithManyRuns() {
        assertNull(automaticRunningBaseline(
            week(0,listOf(10.0,10.0,10.0,10.0))+
                week(1,listOf(10.0,10.0,10.0)),
            now,zone=ZoneOffset.UTC))
    }
}
