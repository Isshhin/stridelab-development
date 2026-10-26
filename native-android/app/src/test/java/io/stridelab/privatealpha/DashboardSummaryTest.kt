package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardSummaryTest {
    private val now=Instant.parse("2026-10-26T01:00:00Z")
    private fun row(id:String,time:String,type:String="run",distance:Double?=5000.0,duration:Double?=1800.0)=ActivityRecord(id,id,type,"fit",Instant.parse(time),distance,duration,null,null,null,null)
    @Test fun localCalendarWindowIncludesSixPreviousDaysAndExcludesFutureAndOlderRecords() {
        val rows=listOf(row("inside","2026-10-19T16:00:00Z"),row("outside","2026-10-19T15:59:59Z"),row("future","2026-10-26T02:00:00Z"))
        val window=DashboardSummary.window(rows,now,ZoneId.of("Asia/Manila"))
        assertEquals(listOf("inside"),window.records.map { it.id }); assertEquals(1,window.activeDays)
    }
    @Test fun countsDistinctDaysAndDoesNotCountStrengthOrWalkingAsRunDistance() {
        val rows=listOf(row("1","2026-10-25T00:00:00Z"),row("2","2026-10-25T02:00:00Z","strength",distance=9999.0),row("3","2026-10-24T00:00:00Z","walk"))
        val window=DashboardSummary.window(rows+rows,now,ZoneId.of("UTC"))
        assertEquals(2,window.activeDays); assertEquals(3,window.records.size)
        assertEquals(5000.0,window.runningDistanceM!!,0.0)
        assertEquals(5400.0,window.durationSeconds!!,0.0)
    }
    @Test fun missingMetricsAreNotConvertedIntoZeroAndPreviousWindowDoesNotOverlap() {
        val rows=listOf(row("1","2026-10-25T00:00:00Z",distance=null,duration=null),row("old","2026-10-19T00:00:00Z"))
        val window=DashboardSummary.window(rows,now,ZoneId.of("UTC"))
        assertNull(window.durationSeconds);assertNull(window.runningDistanceM);assertEquals(1,window.missingRunDistance)
        val previous=DashboardSummary.window(rows,now,ZoneId.of("UTC"),7)
        assertEquals(listOf("old"),previous.records.map { it.id })
    }
    @Test fun dstUsesDatesRatherThanSevenTimes24Hours() {
        val window=DashboardSummary.window(listOf(row("1","2026-11-01T04:00:00Z")),Instant.parse("2026-11-02T05:00:00Z"),ZoneId.of("America/New_York"))
        assertEquals(1,window.activeDays)
    }
}
