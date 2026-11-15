package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15WeeklyReviewTest {
    private val day=LocalDate.of(2026,11,16)
    private val now=Instant.parse("2026-11-16T12:00:00Z")
    private fun plan(effort:Int=9):PlanSnapshot {
        val source=PlannedSession("source",day.minusDays(5),"run","Run",60,10.0,"easy",status="completed",logRevision=1,
            log=WorkoutLog(now.minusSeconds(5*86400),minutes=60.0,distanceKm=10.0,effort=effort,performedOn=day.minusDays(5)))
        val targets=(1..8).map { i -> PlannedSession("t$i",day.plusDays(i.toLong()),"run","Run",60,10.0,if(i==4)"long" else "easy") }
        return PlanSnapshot("plan","owner",ENGINE15_VERSION,ENGINE15_POLICY,now,PlanRequest(policyChoice="engine15"),listOf(source)+targets,emptyList(),status="approved")
    }
    @Test fun weeklyReviewUsesWholeWeekAndRequiresAtomicApproval() {
        val p=plan();val review=Engine15WeeklyReview.preview(p,day,now=now)
        assertTrue(review.changes.isNotEmpty())
        assertTrue(p.sessions.all { it.suggestion==null })
        val accepted=Engine15WeeklyReview.accept(p,review,day,now)
        assertEquals(p.sessions.first(),accepted.sessions.first())
        assertEquals(p.sessions.last(),accepted.sessions.last())
        assertTrue(accepted.sessions.drop(1).take(7).all { it.effectiveMinutes<=60 })
        assertTrue(accepted.sessions.any { it.suggestion?.accepted==true })
        assertEquals(p.sessions.map { it.distanceKm },accepted.sessions.map { it.distanceKm })
        try { Engine15WeeklyReview.accept(accepted,review,day,now);fail("Replayed approval") } catch(expected:PlanConstraint) {}
    }
    @Test fun staleStartedAndForeignPlanCannotReceiveApproval() {
        val p=plan();val review=Engine15WeeklyReview.preview(p,day,now=now)
        for(changed in listOf(p.copy(owner="other"),p.copy(revision=2),p.copy(sessions=p.sessions.map { if(it.id=="t1")it.copy(status="in_progress") else it }))) {
            try { Engine15WeeklyReview.accept(changed,review,day,now);fail("Stale review") } catch(expected:PlanConstraint) {}
        }
    }
    @Test fun schedulingMissDoesNotCauseCatchUpOrFatigueInference() {
        val p=plan(4).let { p -> p.copy(sessions=p.sessions.map { if(it.id=="source")it.copy(status="missed",missedReason="Busy",log=null) else it }) }
        val review=Engine15WeeklyReview.preview(p,day,now=now)
        assertTrue(review.changes.isEmpty())
        assertTrue(review.explanations.any { it.contains("availability",true) })
    }
    @Test fun reportedFatigueAndAvailabilityAreSeparateAndPersistWithApproval() {
        val p=plan(4)
        val input=Engine15WeeklyReview.Input(fatigue=4,unavailableDays=setOf(day.plusDays(2)))
        val review=Engine15WeeklyReview.preview(p,day,input,now=now)
        assertEquals(0,review.changes.single { it.sessionId=="t2" }.minutes)
        assertTrue(review.changes.any { it.sessionId!="t2" && it.minutes in 1..59 })
        val accepted=Engine15WeeklyReview.accept(p,review,day,now)
        assertTrue(accepted.feedback.last().text.contains("fatigue=4"))
    }
    @Test fun overrunOffersReviewEvenAtLowEffortWithoutAutomaticReduction() {
        val p=plan(4).let { p -> p.copy(sessions=p.sessions.map { if(it.id=="source")it.copy(date=day.minusDays(1),log=it.log!!.copy(performedOn=day.minusDays(1),at=now.minusSeconds(86400),distanceKm=14.0)) else it }) }
        val review=Engine15WeeklyReview.preview(p,day,Engine15WeeklyReview.Input(trigger=Engine15WeeklyReview.Trigger.OVERRUN),now=now)
        assertTrue(review.explanations.any { it.contains("overrun",true) })
        assertTrue(review.changes.isEmpty())
    }
    @Test fun structuredFeedbackRoundTripsAndPainDoesNotRequireHighEffort() {
        val log=plan(4).sessions.first().log!!.copy(completionDifficulty="Harder",discomfort="Pain")
        assertEquals(log,WorkoutLog.decode(log.encode()))
        val p=plan(4).let { p -> p.copy(sessions=p.sessions.map { if(it.id=="source")it.copy(log=log) else it }) }
        val review=Engine15WeeklyReview.preview(p,day,now=now)
        assertTrue(review.changes.isNotEmpty())
        assertTrue(review.changes.all { it.minutes==0 })
    }
    @Test fun maintenanceApprovalRetainsFeedbackAndCannotBeReplayed() {
        val p=plan(4);val review=Engine15WeeklyReview.preview(p,day,Engine15WeeklyReview.Input(fatigue=3),now=now)
        val accepted=Engine15WeeklyReview.accept(p,review,day,now)
        assertEquals(p.sessions,accepted.sessions)
        assertEquals(1,accepted.feedback.size)
        assertTrue(accepted.feedback.single().text.contains("fatigue=3"))
    }
    @Test fun unsupportedAndDuplicateMissesDoNotDriveRecovery() {
        val p=plan(4)
        val other=p.copy(id="other",engineVersion="99.0",policyId="unknown",sessions=listOf(p.sessions.first().copy(id="external",status="missed",log=null,missedReason="Illness")))
        assertTrue(Engine15WeeklyReview.preview(p,day,allPlans=listOf(p,other),now=now).changes.isEmpty())
        val duplicate=other.copy(engineVersion=ENGINE15_VERSION,policyId=ENGINE15_POLICY,sessions=listOf(other.sessions.first().copy(id="source")))
        assertTrue(Engine15WeeklyReview.preview(p,day,allPlans=listOf(p,duplicate),now=now).changes.isEmpty())
    }
    @Test fun futureTimestampWithinTodayCannotDriveReview() {
        val p=plan(4).let { p -> p.copy(sessions=p.sessions.map { if(it.id=="source")it.copy(date=day,log=it.log!!.copy(performedOn=day,at=now.plusSeconds(3600),discomfort="Pain")) else it }) }
        assertTrue(Engine15WeeklyReview.preview(p,day,now=now).changes.isEmpty())
    }
    @Test fun protectedSessionsAndUnchangedPrescriptionSurviveRoundTrip() {
        val p=plan().let { p -> p.copy(request=p.request.copy(lockedSessionDays=mapOf("t1" to day.plusDays(1).toString())),sessions=p.sessions.map { if(it.id=="t2")it.copy(status="in_progress") else if(it.id=="t3")it.copy(linkedActivityId="activity") else it }) }
        val review=Engine15WeeklyReview.preview(p,day,Engine15WeeklyReview.Input(fatigue=5),now=now)
        assertFalse(review.changes.any { it.sessionId in setOf("source","t1","t2","t3","t8") })
        val accepted=Engine15WeeklyReview.accept(p,review,day,now)
        assertEquals(accepted,PlanSnapshot.decode(accepted.encode(),"owner"))
        for(id in listOf("source","t1","t2","t3","t8")) assertEquals(p.sessions.single { it.id==id },accepted.sessions.single { it.id==id })
    }
}
