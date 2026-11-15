package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15HistoryPatternTest {
    private val day=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val request=PlanRequest(goal="Aerobic foundation",experience=2,start=day,weeks=4,days=(1..7).toSet(),runDays=6,longDay=7,qualityCount=0,weeklyKm=80.0,longestKm=24.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,targetWeeklyKm=80.0)
    private fun history(easy:Double=12.0,owner:String="owner"):PlanSnapshot {
        val sessions=(1..3).flatMap { w -> listOf(1,3,6).map { d ->
            val date=day.minusWeeks(w.toLong()).plusDays(d.toLong())
            PlannedSession("$w-$d",date,"run","Run",60,10.0,if(d==6)"long" else "easy",status="completed",
                log=WorkoutLog(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),distanceKm=if(d==6)24.0 else easy,effort=4,performedOn=date),logRevision=1)
        } }
        return PlanSnapshot("history",owner,ENGINE15_VERSION,ENGINE15_POLICY,now,request,sessions,emptyList(),status="approved")
    }
    @Test fun repeatedEasyPatternChangesAllocationWithoutChangingWeeklyBudget() {
        val short=Engine15.generateWithHistory(request,"owner",listOf(history(8.0)),"draft",now)
        val usual=Engine15.generateWithHistory(request,"owner",listOf(history(12.0)),"draft",now)
        val dir=java.io.File("build/reports/m106-followup");dir.mkdirs()
        java.io.File(dir,"history-pattern-plans.json").writeText(org.json.JSONArray(listOf(short.encode(),usual.encode())).toString(2))
        assertNotEquals(short.sessions.map { it.distanceKm },usual.sessions.map { it.distanceKm })
        assertTrue(usual.explanations.any { it.contains("OBSERVED_RUN_PATTERN") })
        assertEquals(request,usual.request)
    }
    @Test fun profileRejectsOtherOwnersDuplicatesAndFutureLogs() {
        val normal=Engine15.generateWithHistory(request,"owner",emptyList(),"draft",now)
        assertEquals(normal,Engine15.generateWithHistory(request,"owner",listOf(history(owner="other")),"draft",now))
        val h=history(); val duplicate=h.copy(sessions=h.sessions.map { it.copy(linkedActivityId="duplicate") })
        assertEquals(normal,Engine15.generateWithHistory(request,"owner",listOf(duplicate),"draft",now))
        val future=h.copy(sessions=h.sessions.map { it.copy(log=it.log!!.copy(at=now.plusSeconds(1))) })
        assertEquals(normal,Engine15.generateWithHistory(request,"owner",listOf(future),"draft",now))
    }
    @Test fun repeatedLongRunsCanUpdateAStaleSessionExposureWithoutInventingWeeklyFitness() {
        val p=request.copy(longestKm=12.0)
        val without=Engine15.generateWithHistory(p,"owner",emptyList(),"draft",now)
        val withHistory=Engine15.generateWithHistory(p,"owner",listOf(history()),"draft",now)
        val dir=java.io.File("build/reports/m106-followup");dir.mkdirs()
        java.io.File(dir,"long-exposure-plans.json").writeText(org.json.JSONArray(listOf(without.encode(),withHistory.encode())).toString(2))
        assertTrue(withHistory.sessions.filter { it.kind=="long" }.maxOf { it.distanceKm!! }>13.0)
        assertTrue(withHistory.sessions.all { it.distanceKm!!<=24.0 })
        assertTrue(withHistory.sessions.filter { it.kind=="easy" && it.runningPrescription?.role=="EASY" }.all { it.distanceKm!!<=13.0 })
        assertEquals(p.weeklyKm,withHistory.request.weeklyKm,0.0)
        assertTrue(without.sessions.maxOf { it.distanceKm!! }<=13.0)
    }
    @Test fun painfulOrDifficultRunsCannotEstablishToleratedExposure() {
        val h=history().let { h -> h.copy(sessions=h.sessions.map { it.copy(log=it.log!!.copy(discomfort="Pain")) }) }
        assertNull(Engine15RunHistory.assess("owner",listOf(h),now))
    }
    @Test fun consecutiveSundayMondayDoesNotEstablishRepeatedLongRunCapacity() {
        val h=history();val runs=listOf(7L,8L).mapIndexed { i,d -> h.sessions.last().copy(id="near$i",date=day.minusDays(d),log=h.sessions.last().log!!.copy(performedOn=day.minusDays(d))) }
        assertNull(Engine15RunHistory.assess("owner",listOf(h.copy(sessions=runs)),now))
    }
    @Test fun downgradedQualityAlsoRespectsEasyExposureAndLongShare() {
        val p=request.copy(weeklyKm=20.0,targetWeeklyKm=20.0,longestKm=20.0,runDays=4,qualityCount=1,planningPace=180.0)
        val baseline=listOf(0L,2L,4L,6L).mapIndexed { i,d -> PlannedSession("x$i",day.plusDays(d),"run","Run",60,3.0,if(i==0)"quality" else if(i==3)"long" else "easy") }
        val runs=Engine15RunningProgram.compose(p,baseline,pattern=Engine15RunHistory(0.5,20.0,listOf("synthetic")))
        assertTrue(runs.filter { it.runningPrescription?.role=="EASY" }.all { it.distanceKm!!<=1.5 })
        assertTrue(runs.single { it.kind=="long" }.distanceKm!!<=runs.sumOf { it.distanceKm!! }*0.4+1e-8)
    }
    @Test fun laterQualityNeverExceedsFirstDeliveredWorkEnvelopeAcrossObservedPatterns() {
        for(km in listOf(30.0,40.0,60.0,80.0)) for(easy in listOf(0.5,2.0,6.0,12.0)) for(pace in listOf(180.0,360.0,600.0)) {
            val p=request.copy(weeklyKm=km,longestKm=24.0,qualityCount=2,recentQualitySessionsPerWeek=2,qualityDays=setOf(2,4),planningPace=pace)
            val baseline=Engine1.generate(p.copy(policyChoice="baseline"),"synthetic","matrix",now)
            val runs=Engine15RunningProgram.compose(p,baseline.sessions,pattern=Engine15RunHistory(easy,24.0,listOf("synthetic")))
            val work=runs.groupBy { java.time.temporal.ChronoUnit.DAYS.between(day,it.date)/7 }.toSortedMap().values.map { rows -> rows.sumOf { it.runningPrescription!!.workMeters } }
            val first=work.firstOrNull { it>0 } ?: continue
            assertTrue("km=$km easy=$easy pace=$pace work=$work",work.all { it<=first })
        }
    }
}
