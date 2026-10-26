package io.stridelab.privatealpha

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.Instant
import org.json.JSONObject

class CoordinatedEngine1Test {
    private val start=LocalDate.parse("2026-11-02")
    private val now=Instant.parse("2026-10-27T00:00:00Z")
    private fun request()=PlanRequest(goal="Improve 5K",policyChoice="coordinated",start=start,runDays=3,strengthDays=1,calDays=1,weeks=4,weeklyKm=30.0,longestKm=12.0,experience=1,strengthExperience=1,qualityCount=1,disciplineGoals=mapOf("str" to "Muscle development","cal" to "Core strength and control"))
    @Test fun secondaryGoalsChangeTheirOwnPrescriptionsWithinTheSameSharedSchedule() {
        val p=request();val result=CoordinatedEngine1.generate(p,"synthetic-owner","goals",now);val base=Engine1.generate(p,"synthetic-owner","goals",now)
        assertEquals(CoordinatedEngine1.policyId,result.policyId);assertEquals(p,result.request);assertEquals(base.sessions.map { it.id to it.date },result.sessions.map { it.id to it.date })
        assertEquals(20,result.sessions.size)
        assertTrue(result.sessions.filter { it.discipline=="str" }.flatMap { it.exercises }.filter { it.reps!=null }.all { it.reps==10 })
        assertTrue(result.sessions.filter { it.discipline=="cal" }.all { it.exercises.any { it.id=="deadbug" } && it.exercises.none { it.id=="wall-push" } })
        assertEquals(base.sessions.filter { it.discipline=="run" }.map { it.distanceKm to it.quality },result.sessions.filter { it.discipline=="run" }.map { it.distanceKm to it.quality })
        for(a in result.sessions)for(b in result.sessions)if(a.id!=b.id)assertFalse(Engine1.conflict(a,b))
    }
    @Test fun fallbackRemainsExplicitAndDoesNotSilentlyReplaceAnInvalidGoal() {
        val p=request().copy(policyChoice="baseline")
        assertSame(Engine1,Engine1Engines.resolve(p));assertSame(CoordinatedEngine1,Engine1Engines.resolve(request()))
        assertEquals(Engine1.generate(p,"synthetic-owner","fallback",now),Engine1Engines.resolve(p).generate(p,"synthetic-owner","fallback",now))
        assertThrows(PlanConstraint::class.java) { Engine1Engines.resolve(p.copy(policyChoice="unknown")) }
        assertThrows(PlanConstraint::class.java) { CoordinatedEngine1.generate(request().copy(disciplineGoals=mapOf("str" to "Marathon")),"synthetic-owner") }
    }
    @Test fun separatePullingGoalStillRequiresItsRealEquipmentAndSupport() {
        val p=request().copy(disciplineGoals=mapOf("cal" to "Pull-up progression"))
        assertThrows(PlanConstraint::class.java) { CoordinatedEngine1.generate(p,"synthetic-owner") }
        assertThrows(PlanConstraint::class.java) { CoordinatedEngine1.generate(p.copy(equipment=mapOf("bar" to 1)),"synthetic-owner") }
        val ready=CoordinatedEngine1.generate(p.copy(equipment=mapOf("bar" to 1,"support" to 1)),"synthetic-owner")
        assertTrue(ready.sessions.filter { it.discipline=="cal" }.all { it.exercises.any { e->e.id=="supported-hang" } })
    }
    @Test fun acceptedGoalsAndActualLogsRoundtripWithoutRegenerationAndOldInputsStayReadable() {
        val draft=CoordinatedEngine1.generate(request(),"synthetic-owner","saved",now);val approved=PlanOperations.approve(draft,emptyList())
        val done=PlanOperations.record(approved,approved.sessions.first().id,WorkoutLog(now,minutes=24.0),true)
        assertEquals(done,PlanSnapshot.decode(done.encode(),"synthetic-owner"))
        val old=Engine1.generate(PlanRequest(start=start),"synthetic-owner","old",now).encode()
        old.getJSONObject("request").remove("policyChoice");old.getJSONObject("request").remove("disciplineGoals")
        val decoded=PlanSnapshot.decode(old,"synthetic-owner");assertEquals("baseline",decoded.request.policyChoice);assertTrue(decoded.request.disciplineGoals.isEmpty());assertEquals(ENGINE1_POLICY,decoded.policyId)
    }
    @Test fun unknownPolicyUnderTheSameEngineVersionIsOpaqueAndReadOnly() {
        val raw=Engine1.generate(PlanRequest(start=start),"synthetic-owner","future",now).encode().put("policyId","stridelab.engine1.future-policy")
        raw.put("futureRule",JSONObject().put("retain",true));raw.getJSONArray("sessions").getJSONObject(0).put("futureTarget",42)
        val future=PlanSnapshot.decode(raw,"synthetic-owner")
        assertFalse(Engine1Engines.canManage(future));assertTrue(future.encode().getJSONObject("futureRule").getBoolean("retain"));assertEquals(42,future.encode().getJSONArray("sessions").getJSONObject(0).getInt("futureTarget"))
        assertThrows(PlanConstraint::class.java) { PlanOperations.approve(future,emptyList()) }
    }
    @Test fun returningRequestReducesEarlyResistanceWithoutAddingCatchUpOrChangingSavedFallback() {
        val p=request().copy(returning=true,strengthExperience=2,weeks=4)
        val plan=CoordinatedEngine1.generate(p,"synthetic-owner","returning",now)
        val early=plan.sessions.filter { it.date<start.plusWeeks(2) && it.exercises.isNotEmpty() }
        assertTrue(early.isNotEmpty());assertTrue(early.flatMap { it.exercises }.all { it.sets<=2 });assertTrue(early.all { it.minutes<=p.minutes })
        assertEquals(20,plan.sessions.size);assertTrue(plan.sessions.filter { it.date<start.plusWeeks(2) && it.discipline=="run" }.none { it.quality })
        assertSame(Engine1,Engine1Engines.resolve(p.copy(policyChoice="baseline")))
    }
}
