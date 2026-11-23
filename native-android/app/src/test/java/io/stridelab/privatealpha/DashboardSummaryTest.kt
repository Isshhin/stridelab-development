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
    private fun native(discipline:String="str",date:String?="2026-10-25",minutes:Double?=30.0,distance:Double?=null,linked:String?=null,status:String="completed"):PlanSnapshot {
        val request=PlanRequest(start=java.time.LocalDate.parse("2026-10-19"),weeks=2)
        val session=PlannedSession("native-session",request.start,discipline,"User-confirmed workout",45,9.0,"practice",status=status,log=WorkoutLog(now,minutes=minutes,distanceKm=distance,performedOn=date?.let(java.time.LocalDate::parse)),linkedActivityId=linked)
        return PlanSnapshot("native-plan","synthetic-owner",ENGINE1_VERSION,ENGINE1_POLICY,now,request,listOf(session),emptyList(),status="approved")
    }
    @Test fun sourceAndDatedNativeConfirmationsShareActualTotalsWithoutInventingStartTimes() {
        val source=listOf(row("run","2026-10-26T00:00:00Z",duration=1200.0))
        val window=DashboardSummary.window(source,now,ZoneId.of("UTC"),plans=listOf(native()))
        assertEquals(2,window.sessionCount);assertEquals(2,window.activeDays);assertEquals(3000.0,window.durationSeconds!!,0.0);assertEquals(5000.0,window.runningDistanceM!!,0.0)
        assertEquals(1,window.records.size);assertEquals(1,window.nativeLogs.size);assertEquals(java.time.LocalDate.parse("2026-10-25"),window.nativeLogs.single().date)
        assertEquals(mapOf("Running" to 1,"Strength" to 1),window.distribution);assertEquals(2,window.daily(ZoneId.of("UTC")).sumOf { it.sessions })
    }
    @Test fun linkedSourceTakesPrecedenceAndDuplicateSnapshotInputCannotDoubleNativeVolume() {
        val source=listOf(row("run","2026-10-25T00:00:00Z"))
        val linked=DashboardSummary.window(source,now,ZoneId.of("UTC"),plans=listOf(native("run",distance=5.0,linked="run")))
        assertEquals(1,linked.sessionCount);assertTrue(linked.nativeLogs.isEmpty());assertEquals(5000.0,linked.runningDistanceM!!,0.0)
        val unlinked=native("run",distance=4.0)
        val window=DashboardSummary.window(emptyList(),now,ZoneId.of("UTC"),plans=listOf(unlinked,unlinked))
        assertEquals(1,window.sessionCount);assertEquals(4000.0,window.runningDistanceM!!,0.0)
    }
    @Test fun missingDatesPartialLogsAndFuturePerformanceDoNotBecomeRecordedTraining() {
        val undated=DashboardSummary.window(emptyList(),now,ZoneId.of("UTC"),plans=listOf(native(date=null)))
        assertEquals(0,undated.sessionCount);assertNull(undated.durationSeconds);assertEquals(1,undated.omittedUndatedLogs)
        assertEquals(0,DashboardSummary.window(emptyList(),now,ZoneId.of("UTC"),plans=listOf(native(status="in_progress"))).sessionCount)
        assertEquals(0,DashboardSummary.window(emptyList(),now,ZoneId.of("UTC"),plans=listOf(native(date="2026-10-27"))).sessionCount)
        val noMetrics=DashboardSummary.window(emptyList(),now,ZoneId.of("UTC"),plans=listOf(native("run",minutes=null)))
        assertEquals(1,noMetrics.sessionCount);assertEquals(1,noMetrics.missingDuration);assertEquals(1,noMetrics.missingRunDistance);assertNull(noMetrics.durationSeconds);assertNull(noMetrics.runningDistanceM)
    }
    @Test fun nativeActualDatesStayInTheirOwnLocalCalendarWindow() {
        val plans=listOf(native(date="2026-10-19"))
        val current=DashboardSummary.window(emptyList(),now,ZoneId.of("Asia/Manila"),plans=plans)
        val prior=DashboardSummary.window(emptyList(),now,ZoneId.of("Asia/Manila"),7,plans)
        assertEquals(0,current.sessionCount);assertEquals(1,prior.sessionCount)
        assertEquals(java.time.LocalDate.parse("2026-10-19"),prior.nativeLogs.single().date)
    }
    @Test fun mileageHintUsesOnlyActualDatedRunDistancesAndNeverThePlannedTarget() {
        val plan=native("run",distance=4.0)
        val sessions=(0..2).map { i -> plan.sessions.first().copy(id="logged-$i",date=java.time.LocalDate.parse("2026-11-02"),distanceKm=99.0,log=plan.sessions.first().log!!.copy(performedOn=java.time.LocalDate.parse("2026-10-25").minusDays(i.toLong()))) }
        val history=listOf(plan.copy(sessions=sessions))
        assertNull(historyWeeklyKm(emptyList(),now,history,ZoneId.of("UTC")))
        assertEquals(12.0,historyWeeklyKm(emptyList(),now,history,ZoneId.of("UTC"),setOf(java.time.LocalDate.parse("2026-10-19")))!!,0.0)
        assertNull(historyWeeklyKm(emptyList(),now,listOf(plan.copy(sessions=sessions.map { it.copy(log=it.log!!.copy(performedOn=null)) })),ZoneId.of("UTC")))
    }
}
