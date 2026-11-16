package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

/**
 * Q3: qualified completed-work evidence, not estimated VO2max, threshold pace,
 * injury tolerance or permission to increase workload automatically.
 */
class Engine15Q3VerifiedQualityHistoryTest {
    private val date=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private val owner="q3-synthetic"

    private fun request(family:String="Threshold")=PlanRequest(
        goal="Improve 5K",experience=2,start=date,weeks=8,
        days=(1..7).toSet(),runDays=6,longDay=7,qualityDays=setOf(2),
        qualityCount=1,recentQualitySessionsPerWeek=1,
        weeklyKm=80.0,longestKm=24.0,targetWeeklyKm=80.0,
        planningPace=360.0,preferredQuality=family,
        allowedQualityTypes=setOf(family),policyChoice="engine15",
        timeBudgetEnabled=false
    )

    private fun sourceSession(
        age:Long,
        family:String="Threshold",
        workKm:Double=5.0,
        id:String="q3-$family-$age"
    ):PlannedSession {
        val performed=date.minusDays(age)
        val rx=RunningPrescription("QUALITY",family,listOf(
            RunningSegment("WARM_UP",2000,"Easy"),
            RunningSegment("WORK",6000,"Controlled"),
            RunningSegment("COOL_DOWN",2000,"Easy")
        ))
        return PlannedSession(
            id,performed,"run","Historic $family",60,10.0,"quality",
            quality=true,status="completed",logRevision=1,runningPrescription=rx,
            log=WorkoutLog(
                performed.atTime(8,0).toInstant(ZoneOffset.UTC),
                minutes=60.0,distanceKm=10.0,effort=6,performedOn=performed,
                completionDifficulty="As planned",discomfort="None",
                qualityWorkKm=workKm
            )
        )
    }

    private fun history(
        family:String="Threshold",
        owner:String=this.owner,
        sessions:List<PlannedSession> = listOf(sourceSession(8,family),sourceSession(15,family))
    )=PlanSnapshot("observed-$family",owner,ENGINE15_VERSION,ENGINE15_POLICY,now,
        request(family),sessions,emptyList(),status="approved")

    private fun qualityWork(plan:PlanSnapshot):Int=plan.sessions.filter { it.quality }
        .first().runningPrescription!!.workMeters

    @Test fun repeatedVerifiedThresholdWorkRaisesDoseWithoutExceedingActualEvidence() {
        val p=request()
        val baseline=Engine15.generate(p,owner,"draft",now)
        val sources=history()
        val adapted=Engine15.generateWithHistory(p,owner,listOf(sources),"draft",now)
        val first=qualityWork(adapted)
        assertTrue("Q3 should increase the overconservative Q2 work only with verified history",
            first>qualityWork(baseline))
        assertTrue("Observed 5 km is a ceiling, not an automatic progression",first<=5000)
        assertTrue(adapted.explanations.any { it.startsWith("VERIFIED_QUALITY_HISTORY:") })
        assertTrue(adapted.sessions.any { it.reason.contains("VERIFIED_QUALITY_WORK:") })
        assertEquals(p,adapted.request)
        assertEquals(sources.sessions,listOf(sources).single().sessions)
        assertEquals(adapted,PlanSnapshot.decode(adapted.encode(),owner))
    }

    @Test fun matchingIntervalWorkUsesActualLogButCannotAuthorizeThresholdWork() {
        val history=history("Intervals")
        val observed=Engine15RunHistory.assess(owner,listOf(history),now)!!
        assertEquals(5.0,observed.verifiedWorkKm("Intervals")!!,1e-8)
        assertNull(observed.verifiedWorkKm("Threshold"))
        val threshold=request()
        val normal=Engine15.generate(threshold,owner,"draft",now)
        val unrelated=Engine15.generateWithHistory(threshold,owner,listOf(history),"draft",now)
        assertEquals("Different workout families must not borrow work-dose tolerance",
            normal.sessions,unrelated.sessions)
        val intervals=request("Intervals")
        val without=Engine15.generate(intervals,owner,"intervals",now)
        val with=Engine15.generateWithHistory(intervals,owner,listOf(history),"intervals",now)
        assertTrue(qualityWork(with)>qualityWork(without))
    }

    @Test fun staleSingleWeekPainAndAcceptedAdjustmentsNeverQualify() {
        val recent=sourceSession(8)
        val older=sourceSession(15)
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(recent)) ),now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(
            recent,sourceSession(9))) ),now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(
            sourceSession(21),sourceSession(28))) ),now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(
            recent.copy(log=recent.log!!.copy(discomfort="Pain")),older))) ,now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(
            recent.copy(suggestion=SessionSuggestion(25,"Reviewed",accepted=true)),older))),now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(sessions=listOf(
            recent.copy(log=recent.log!!.copy(qualityWorkKm=null)),older))),now))
    }

    @Test fun duplicateSourceOrOtherOwnerCannotChangeQualityWork() {
        val h=history()
        val duplicate=h.copy(sessions=h.sessions.map { it.copy(linkedActivityId="same-source") })
        assertNull(Engine15RunHistory.assess(owner,listOf(duplicate),now))
        assertNull(Engine15RunHistory.assess(owner,listOf(history(owner="other")),now))
        val baseline=Engine15.generate(request(),owner,"draft",now)
        val other=Engine15.generateWithHistory(request(),owner,listOf(history(owner="other")),"draft",now)
        assertEquals(baseline,other)
    }

    @Test fun verifiedHigherWorkRetainsQ2TimedSupportAndWeeklySafetyCeilings() {
        val p=request()
        val plan=Engine15.generateWithHistory(p,owner,listOf(history()),"draft",now)
        for(week in 0 until p.weeks) {
            val runs=plan.sessions.filter { it.discipline=="run" &&
                it.date>=date.plusWeeks(week.toLong()) &&
                it.date<date.plusWeeks((week+1).toLong()) }
            val weeklyKm=runs.sumOf { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0 }
            val work=runs.sumOf { it.runningPrescription?.workMeters ?: 0 }
            assertTrue("Work remains <=20% of the mixed weekly estimate",work<=weeklyKm*200+1e-8)
            assertTrue(weeklyKm<=Engine15.runningCeiling(week,p.weeklyKm,p.targetWeeklyKm)+1e-8)
            assertTrue(runs.single { it.kind=="long" }.distanceKm!!<=weeklyKm*0.4+0.11)
            for(quality in runs.filter { it.quality }) {
                val steps=quality.runningPrescription!!.mixedSegments!!
                assertNull(quality.distanceKm)
                assertTrue(quality.runningPrescription!!.workMeters<=5000)
                assertTrue(steps.single { it.purpose=="WARM_UP" }.seconds in 600..720)
                assertTrue(steps.single { it.purpose=="COOL_DOWN" }.seconds in 300..480)
                assertNull(steps.single { it.purpose=="COOL_DOWN" }.meters)
            }
        }
        assertTrue("A history ceiling never implies automatic later progression",
            plan.sessions.filter { it.quality }.all { it.runningPrescription!!.workMeters<=5000 })
    }
}
