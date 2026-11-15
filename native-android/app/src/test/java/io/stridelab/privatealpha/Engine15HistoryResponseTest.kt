package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15HistoryResponseTest {
    private val monday=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val request=PlanRequest(goal="Aerobic foundation",experience=2,start=monday,weeks=4,
        days=(1..7).toSet(),runDays=6,longDay=7,qualityDays=setOf(2,4),qualityCount=2,
        weeklyKm=80.0,longestKm=24.0,planningPace=360.0,policyChoice="engine15",timeBudgetEnabled=false,
        targetWeeklyKm=80.0,recentQualitySessionsPerWeek=2)
    private fun history(effort:Int=9,owner:String="synthetic",daysAgo:Long=1,illness:Boolean=false):PlanSnapshot {
        val day=monday.minusDays(daysAgo)
        val session=PlannedSession("observed",day,"run","Easy",40,6.0,"easy",
            status=if(illness)"missed" else "completed",missedReason=if(illness)"Illness" else null,
            log=if(illness)null else WorkoutLog(now.minusSeconds(daysAgo*86400),minutes=40.0,effort=effort,performedOn=day),logRevision=1)
        return PlanSnapshot("history",owner,ENGINE15_VERSION,ENGINE15_POLICY,now.minusSeconds(86400),request,listOf(session),emptyList(),status="approved")
    }
    private fun generate(history:List<PlanSnapshot>)=Engine15.generateWithHistory(request,"synthetic",history,"draft",now)
    @Test fun recentDifficultResponseChangesUpcomingWeekButNotExistingRecords() {
        val history=history()
        val result=generate(listOf(history))
        assertTrue(result.sessions.filter { it.date<=monday.plusDays(3) }.none { it.quality })
        assertTrue(generate(emptyList()).sessions.any { it.quality && it.date<=monday.plusDays(3) })
        assertTrue(result.explanations.any { it.contains("RECENT_RESPONSE_REVIEW") })
        assertEquals(9,history.sessions.single().log!!.effort)
        assertTrue(result.sessions.any { it.quality && it.date>=monday.plusWeeks(1) })
    }
    @Test fun otherOwnersStaleOrUndatedRecordsDoNotDriveAdaptation() {
        val unchanged=generate(emptyList())
        assertEquals(unchanged,generate(listOf(history(owner="other"))))
        assertEquals(unchanged,generate(listOf(history(daysAgo=10))))
        val h=history()
        assertEquals(unchanged,generate(listOf(h.copy(sessions=h.sessions.map { it.copy(log=it.log!!.copy(performedOn=null)) }))))
    }
    @Test fun recentExplicitIllnessRequiresReviewBeforeAnImmediateRunningDraft() {
        try { generate(listOf(history(illness=true))); fail("Illness must not silently become normal training") }
        catch(e:PlanConstraint) { assertTrue(e.message.orEmpty().contains("illness",true)) }
    }
    @Test fun easyCompletionDoesNotFabricateNewFitnessOrIncreaseThePlan() {
        assertEquals(generate(emptyList()),generate(listOf(history(effort=3))))
    }
    @Test fun difficultyNeverRedistributesRemovedLongRunLoadIntoOtherDays() {
        val p=request.copy(runDays=3,qualityCount=0,longDay=1,weeklyKm=40.0,longestKm=30.0)
        val baseline=listOf(0L,2L,4L).mapIndexed { i,d -> PlannedSession("s$i",monday.plusDays(d),"run","Run",60,10.0,if(i==0)"long" else "easy") }
        val normal=Engine15RunningProgram.compose(p,baseline)
        val review=Engine15Feedback.DraftReview(monday,monday.plusDays(1),null,listOf("synthetic"))
        val adapted=Engine15RunningProgram.compose(p,baseline,review)
        assertTrue(adapted.sumOf { it.distanceKm!! }<=normal.sumOf { it.distanceKm!! })
        assertTrue(adapted.zip(normal).all { (a,b) -> a.distanceKm!!<=b.distanceKm!! && a.minutes<=b.minutes })
    }
    @Test fun timeOnlyBeginnerRespondsWithoutInventingDistance() {
        val p=request.copy(experience=0,weeklyKm=0.0,longestKm=0.0,runDays=3,qualityCount=0)
        val normal=Engine15.generateWithHistory(p,"synthetic",emptyList(),"draft",now)
        val adapted=Engine15.generateWithHistory(p,"synthetic",listOf(history()),"draft",now)
        val pairs=adapted.sessions.zip(normal.sessions).filter { it.first.date<=monday.plusDays(3) }
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { (a,b) -> a.minutes<b.minutes && a.distanceKm==null })
    }
}
