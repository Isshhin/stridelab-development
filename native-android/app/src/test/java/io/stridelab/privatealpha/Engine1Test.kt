package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine1Test {
    private val start=LocalDate.parse("2026-11-02");private val now=Instant.parse("2026-10-26T00:00:00Z")
    private fun generate(p:PlanRequest)=Engine1.generate(p,"synthetic-owner","stable-plan",now)
    @Test fun beginnerWithoutWatchGetsActualRunWalkAndNoInventedMileageOrQuality() {
        val p=generate(PlanRequest(start=start,weeklyKm=0.0))
        assertEquals(12,p.sessions.size);assertTrue(p.sessions.all { it.steps.size>=3&&it.minutes in 10..45 })
        assertTrue(p.sessions.all { it.distanceKm==null&&!it.quality });assertEquals("draft",p.status)
        assertEquals(ENGINE1_VERSION,p.engineVersion);assertEquals(ENGINE1_POLICY,p.policyId)
    }
    @Test fun experiencedMileageQualityAndLongDayRemainTruthfulAndSpaced() {
        val p=generate(PlanRequest(start=start,experience=2,weeklyKm=30.0,longestKm=10.0,runDays=4,qualityCount=1,qualityDays=setOf(2),days=setOf(1,2,4,5,6),longDay=6))
        assertEquals(16,p.sessions.size)
        assertTrue(p.sessions.filter { it.kind=="long" }.all { it.date.dayOfWeek.value==6 })
        assertTrue(p.sessions.filter { it.quality }.all { it.date.dayOfWeek.value==2 })
        assertTrue(p.sessions.all { it.minutes<=45 });assertTrue(p.sessions.all { it.distanceKm!=null })
        assertFalse(p.sessions.any { a->p.sessions.any { b->a.id!=b.id&&Engine1.conflict(a,b) } })
    }
    @Test fun hybridKeepsEveryDisciplineAndDoesNotPutLegWorkBeforeLongOrNextToQuality() {
        val request=PlanRequest(start=start,experience=1,weeklyKm=25.0,longestKm=8.0,runDays=3,strengthDays=2,calDays=1,conditioningDays=1,days=(1..7).toSet(),qualityCount=1,qualityDays=setOf(2),minutes=60)
        val plan=generate(request)
        assertEquals(28,plan.sessions.size)
        assertEquals(setOf("run","str","cal","cond"),plan.sessions.map { it.discipline }.toSet())
        assertFalse(plan.sessions.any { a->plan.sessions.any { b->a.id!=b.id&&Engine1.conflict(a,b) } })
        assertTrue(plan.sessions.filter { it.discipline=="str"||it.discipline=="cal" }.all { it.exercises.size>=2&&it.exercises.all { e->e.sets>0&&(e.reps!=null||e.seconds!=null) } })
    }
    @Test fun impossibleRequestsExplainRatherThanDropSessions() {
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,runDays=3,strengthDays=2,calDays=2,days=setOf(1,2,4))) }
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,experience=2,weeklyKm=30.0,longestKm=8.0,runDays=3,qualityCount=1,qualityDays=setOf(2),longDay=3,days=setOf(1,2,3))) }
    }
    @Test fun equipmentQuantitiesAndNoEquipmentOptionsAreReal() {
        val bare=generate(PlanRequest(start=start,runDays=0,strengthDays=2))
        assertFalse(bare.sessions.flatMap { it.exercises }.any { it.name.contains("Inverted row")||it.equipment.contains("Dumbbell",true) })
        val one=generate(PlanRequest(start=start,runDays=0,strengthDays=2,equipment=mapOf("db" to 1)))
        assertTrue(one.sessions.flatMap { it.exercises }.any { it.id=="gs" });assertFalse(one.sessions.flatMap { it.exercises }.any { it.id=="fp" })
    }
    @Test fun beginnerCalisthenicsUsesAssistanceAndNeverAutoPromotesByWeek() {
        val p=generate(PlanRequest(start=start,runDays=0,calDays=2,weeks=8,calLevel=0))
        assertTrue(p.sessions.all { s->s.exercises.any { it.name=="Wall push-up" } })
        assertFalse(p.sessions.flatMap { it.exercises }.any { it.name=="Pull-up"||it.name.contains("Archer") })
        assertThrows(PlanConstraint::class.java) { generate(PlanRequest(start=start,goal="Pull-up progression",runDays=0,calDays=2)) }
    }
    @Test fun generationIsDeterministicAndExclusionsAreRespected() {
        val request=PlanRequest(start=start,runDays=0,strengthDays=2,excluded=setOf("sq"))
        assertEquals(generate(request),generate(request));assertFalse(generate(request).sessions.flatMap { it.exercises }.any { it.id=="sq" })
    }
    @Test fun savedContractKeepsPrescriptionsUnderUnknownFutureEngineAndRejectsOtherOwner() {
        val original=generate(PlanRequest(start=start));val j=original.encode().put("engineVersion","2.0-future")
        j.put("futurePolicy",org.json.JSONObject().put("opaqueRule",7))
        j.getJSONArray("sessions").getJSONObject(0).put("futureSession", "retain me")
        val decoded=PlanSnapshot.decode(j,"synthetic-owner")
        assertEquals(original.sessions,decoded.sessions);assertEquals("2.0-future",decoded.engineVersion)
        assertEquals(7,decoded.encode().getJSONObject("futurePolicy").getInt("opaqueRule"))
        assertEquals("retain me",decoded.encode().getJSONArray("sessions").getJSONObject(0).getString("futureSession"))
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(decoded,emptyList()) }
        assertThrows(PlanConstraint::class.java) { PlanOperations.acceptSuggestion(decoded,decoded.sessions.first().id) }
        assertThrows(IllegalArgumentException::class.java) { PlanSnapshot.decode(j,"another-owner") }
    }
    @Test fun approvalIsExplicitAndConflictingAcceptedPlansAreRetained() {
        val draft=generate(PlanRequest(start=start));assertEquals("draft",draft.status)
        val approved=PlanOperations.approve(draft,emptyList());assertEquals("approved",approved.status)
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(draft.copy(id="other"),listOf(approved)) }
        assertEquals(draft.sessions,approved.sessions)
    }
    @Test fun realLogsStayMissingUntilEnteredAndCompletionCannotDuplicateOrRegenerate() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList());val s=p.sessions.first()
        val log=WorkoutLog(now,minutes=18.0,effort=3,notes="Felt comfortable")
        val done=PlanOperations.record(p,s.id,log,true)
        assertEquals(1,done.sessions.count { it.status=="completed" });assertNull(done.sessions.first { it.id==s.id }.log!!.distanceKm)
        assertEquals(done,PlanOperations.record(done,s.id,log,true));assertEquals(p.sessions.map { it.id },done.sessions.map { it.id })
    }
    @Test fun missedReasonCreatesOnlyPendingReductionAndPreservesOriginalPrescription() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList());val first=p.sessions.first()
        val missed=PlanOperations.missed(p,first.id,"Fatigue",false)
        val suggested=missed.sessions.first { it.suggestion!=null }
        assertFalse(suggested.suggestion!!.accepted);assertEquals(p.sessions.find { it.id==suggested.id }!!.minutes,suggested.minutes)
        val accepted=PlanOperations.acceptSuggestion(missed,suggested.id).sessions.first { it.id==suggested.id }
        assertTrue(accepted.effectiveMinutes<=accepted.minutes);assertEquals(p.sessions.find { it.id==suggested.id }!!.steps,accepted.steps)
    }
    @Test fun feedbackRecordsEngineContextAndInvalidActualsAreRejected() {
        val p=PlanOperations.approve(generate(PlanRequest(start=start)),emptyList())
        val feedback=PlanOperations.feedback(p,"Inappropriate prescription","This session needs review",p.sessions.first().id,now)
        assertEquals(1,feedback.feedback.size);assertEquals(p.policyId,feedback.policyId)
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,minutes=Double.NaN),true) }
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,effort=11),true) }
    }
}
