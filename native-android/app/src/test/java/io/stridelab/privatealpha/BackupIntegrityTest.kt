package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BackupIntegrityTest {
    private val owner="backup-test-owner"
    private val now=Instant.parse("2026-10-28T00:00:00Z")
    private fun plan()=Engine1.generate(PlanRequest(start=LocalDate.parse("2026-11-02"),runDays=0,strengthDays=2),owner,"backup-plan",now).copy(status="approved")
    private fun raw(vararg plans:PlanSnapshot)=JSONObject().put("schemaVersion",1).put("plans",JSONArray(plans.map { it.encode() })).toString()
    private fun restored(p:PlanSnapshot)=PlanBackup.read(raw(p),owner).single()
    private fun multi()=MultiSessionEngine1.generate(PlanRequest(start=LocalDate.parse("2026-11-02"),weeks=1,days=(1..7).toSet(),runDays=5,calDays=3,weeklyKm=30.0,longestKm=12.0,policyChoice="multi-session",dailyMinutes=90),owner,"multi-plan",now).copy(status="approved")
    private fun pairPlans():Pair<PlanSnapshot,PlanSnapshot> {
        val p=multi();val pair=p.sessions.groupBy { it.date }.values.first { it.size==2 }
        return p.copy(id="pair-run",sessions=listOf(pair.single { it.discipline=="run" })) to p.copy(id="pair-cal",sessions=listOf(pair.single { it.discipline=="cal" }))
    }

    @Test fun fullMultiSessionSnapshotAndSameDayPairRestoreUnchanged() {
        val p=multi();assertEquals(p,restored(p));assertEquals(listOf(p),PlanBackup.additions(listOf(p),emptyList()))
    }
    @Test fun explicitTargetEqualToRecentMileageSurvivesBackupRestore() {
        val request=PlanRequest(goal="Improve 5K",policyChoice="engine15",start=LocalDate.parse("2026-11-02"),
            weeks=4,days=(1..7).toSet(),runDays=3,longDay=6,weeklyKm=24.0,longestKm=8.0,
            experience=1,targetWeeklyKm=24.0)
        val plan=Engine15.generate(request,owner,"target-backup",now)
        val recovered=restored(plan)
        assertEquals(plan,recovered)
        assertEquals(24.0,recovered.request.targetWeeklyKm!!,0.0)
        assertEquals(listOf(plan),PlanBackup.additions(listOf(recovered),emptyList()))
    }
    @Test fun compatibleCrossPlanMultiSessionPairIsAccepted() {
        val (a,b)=pairPlans();val rows=PlanBackup.read(raw(a,b),owner)
        assertEquals(listOf(a,b),PlanBackup.additions(rows,emptyList()))
        assertEquals(listOf(b),PlanBackup.additions(listOf(b),listOf(a)))
    }
    @Test fun mixedPolicySameDayPairStillConflicts() {
        val (a,b)=pairPlans();assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(b.copy(policyId=ENGINE1_POLICY)),listOf(a)) }
    }
    @Test fun individualAndSharedDailyBudgetsAreEnforced() {
        val (a,b)=pairPlans()
        val x=a.copy(request=a.request.copy(dailyMinutes=60),sessions=a.sessions.map { it.copy(minutes=40) })
        val y=b.copy(request=b.request.copy(dailyMinutes=60),sessions=b.sessions.map { it.copy(minutes=40) })
        assertEquals(x,restored(x));assertEquals(y,restored(y))
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(y),listOf(x)) }
        assertThrows(PlanConstraint::class.java) { restored(x.copy(sessions=x.sessions+y.sessions)) }
    }
    @Test fun adjacentDayRecoveryConflictInsideImportedPlanIsRejected() {
        val p=plan();val a=p.sessions.first();val b=p.sessions[1].copy(date=a.date.plusDays(1),lower=true)
        assertThrows(PlanConstraint::class.java) { restored(p.copy(sessions=listOf(a.copy(lower=true),b))) }
    }
    @Test fun alreadyRecordedHistoricalDatesAreRetainedWithoutRescheduling() {
        val p=plan();val a=p.sessions.first().copy(status="completed",log=WorkoutLog(now,minutes=15.0))
        val b=p.sessions[1].copy(date=a.date,status="completed",log=WorkoutLog(now,minutes=12.0))
        val history=p.copy(sessions=listOf(a,b));assertEquals(history,restored(history))
    }
    @Test fun legacyDurationOnlyCompletedRecordRemainsReadableButNewCompletionRequiresActuals() {
        val p=plan();val historical=p.copy(sessions=p.sessions.map { it.copy(status="completed",log=WorkoutLog(now,minutes=15.0)) })
        assertEquals(historical,restored(historical))
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now,minutes=15.0),true) }
    }
    @Test fun unfinishedLoadOnlyProgressRestoresWithoutBeingConfirmed() {
        val p=plan();val s=p.sessions.first();val log=WorkoutLog(now,sets=listOf(SetRecord(s.exercises.first().id,1,kg=5.0)))
        val partial=PlanOperations.record(p,s.id,log,false);assertEquals("in_progress",partial.sessions.first().status);assertEquals(partial,restored(partial))
    }
    @Test fun holdCompletionRequiresHoldMeasurementAndAllowsOnePerformedSet() {
        val p=plan();val ex=ExercisePrescription("qa-hold","Hold",3,seconds=30)
        val timed=p.copy(sessions=listOf(p.sessions.first().copy(exercises=listOf(ex))))
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(timed,timed.sessions.first().id,WorkoutLog(now,sets=listOf(SetRecord(ex.id,1,reps=8))),true) }
        val completed=PlanOperations.record(timed,timed.sessions.first().id,WorkoutLog(now,sets=listOf(SetRecord(ex.id,1,seconds=12))),true)
        assertEquals("completed",completed.sessions.first().status);assertEquals(completed,restored(completed))
    }
    @Test fun swappedPrescriptionActualsRestoreWithOriginalTargetsIntact() {
        val p=plan();val s=p.sessions.first();val swapped=PlanOperations.swapExercise(p,s.id,s.exercises.first().id,now)
        val ex=swapped.sessions.first().effectiveExercises.first()
        val done=PlanOperations.record(swapped,s.id,WorkoutLog(now,sets=listOf(SetRecord(ex.id,1,reps=5))),true)
        assertEquals(done,restored(done));assertEquals(s.exercises,done.sessions.first().exercises)
    }
    @Test fun importedSetBoundsAndNegativeLoadUseRecordingValidation() {
        val p=plan();val s=p.sessions.first();val ex=s.exercises.first()
        for(row in listOf(SetRecord(ex.id,0,reps=5),SetRecord(ex.id,ex.sets+1,reps=5),SetRecord(ex.id,1,reps=501),SetRecord(ex.id,1,seconds=3601),SetRecord(ex.id,1,reps=5,kg=-1.0))) {
            val bad=p.copy(sessions=listOf(s.copy(log=WorkoutLog(now,sets=listOf(row))))+p.sessions.drop(1))
            val original=bad.encode().toString();assertThrows(PlanConstraint::class.java) { restored(bad) };assertEquals(original,bad.encode().toString())
        }
    }
    @Test fun optionalLegacyFieldsKeepTheirDecodeDefaults() {
        val p=plan();val j=p.encode();j.remove("name");j.getJSONObject("request").remove("policyChoice");j.getJSONObject("request").remove("disciplineGoals")
        j.getJSONArray("sessions").objects().forEach { it.remove("swaps");it.remove("logRevision") }
        val read=PlanBackup.read(JSONObject().put("schemaVersion",1).put("plans",JSONArray().put(j)).toString(),owner).single()
        assertEquals(p,read)
    }
    @Test fun unknownPolicyRetainsOpaqueSnapshotAndRemainsReadOnly() {
        val j=plan().encode().put("policyId","future-readonly-policy").put("futureExtra",JSONObject().put("value",7))
        val p=PlanBackup.read(JSONObject().put("schemaVersion",1).put("plans",JSONArray().put(j)).toString(),owner).single()
        assertFalse(Engine1Engines.canManage(p));assertEquals(j.toString(),p.encode().toString())
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,WorkoutLog(now),false) }
    }
    @Test fun existingPlanIdentityIsSkippedWithoutReplacingHistoricalLogs() {
        val p=plan();val saved=p.copy(sessions=p.sessions.map { it.copy(status="completed",log=WorkoutLog(now,minutes=15.0)) })
        assertTrue(PlanBackup.additions(listOf(p),listOf(saved)).isEmpty());assertEquals(15.0,saved.sessions.first().log!!.minutes!!,0.0)
    }
}
