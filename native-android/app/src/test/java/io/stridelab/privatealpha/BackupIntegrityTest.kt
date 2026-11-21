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
    private val library=ExerciseLibraryV2.load { path -> java.io.File("src/main/assets",path).inputStream() }
    private fun plan()=Engine15IndependentPlanGenerator.generateWithHistory(
        PlanRequest(start=LocalDate.parse("2026-11-02"),runDays=0,strengthDays=2,policyChoice="engine15",timeBudgetEnabled=false,equipment=mapOf("db" to 1)),
        owner,emptyList(),"backup-plan",now,library
    ).copy(status="approved")
    private fun raw(vararg plans:PlanSnapshot)=JSONObject().put("schemaVersion",1).put("plans",JSONArray(plans.map { it.encode() })).toString()
    private fun restored(p:PlanSnapshot)=PlanBackup.read(raw(p),owner).single()
    private fun multi()=Engine15IndependentPlanGenerator.generateWithHistory(
        PlanRequest(start=LocalDate.parse("2026-11-02"),weeks=1,days=(1..7).toSet(),runDays=5,calDays=3,weeklyKm=30.0,longestKm=12.0,policyChoice="engine15",dailyMinutes=90,timeBudgetEnabled=false,recentMileageBasis="SELF_REPORTED_AVERAGE",equipment=mapOf("bands" to 1,"anchor" to 1)),
        owner,emptyList(),"multi-plan",now,library
    ).copy(status="approved")
    private fun pairPlans():Pair<PlanSnapshot,PlanSnapshot> {
        val runPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            PlanRequest(start=LocalDate.parse("2026-11-02"),weeks=1,days=(1..7).toSet(),longDay=1,runDays=1,weeklyKm=8.0,longestKm=10.0,policyChoice="engine15",timeBudgetEnabled=false,recentMileageBasis="SELF_REPORTED_AVERAGE"),
            owner,emptyList(),"pair-run",now,library
        ).copy(status="approved")
        val calPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            PlanRequest(start=LocalDate.parse("2026-11-02"),weeks=1,days=(1..7).toSet(),runDays=0,calDays=1,goal="Calisthenics foundation",policyChoice="engine15",weeklySlots=mapOf("cal" to listOf(4)),timeBudgetEnabled=false,equipment=mapOf("bands" to 1,"anchor" to 1)),
            owner,emptyList(),"pair-cal",now,library
        ).copy(status="approved")
        return runPlan to calPlan
    }

    @Test fun fullMultiSessionSnapshotAndSameDayPairRestoreUnchanged() {
        val p=multi();assertEquals(p,restored(p));assertEquals(listOf(p),PlanBackup.additions(listOf(p),emptyList()))
    }
    @Test fun explicitTargetEqualToRecentMileageSurvivesBackupRestore() {
        val request=PlanRequest(goal="Improve 5K",policyChoice="engine15",start=LocalDate.parse("2026-11-02"),
            weeks=4,days=(1..7).toSet(),runDays=3,longDay=6,weeklyKm=24.0,longestKm=8.0,
            experience=1,targetWeeklyKm=24.0,timeBudgetEnabled=false,recentMileageBasis="SELF_REPORTED_AVERAGE")
        val plan=Engine15IndependentPlanGenerator.generateWithHistory(request,owner,emptyList(),"target-backup",now)
        val recovered=restored(plan)
        assertEquals(plan,recovered)
        assertEquals(24.0,recovered.request.targetWeeklyKm!!,0.0)
        assertEquals(listOf(plan),PlanBackup.additions(listOf(recovered),emptyList()))
    }
    @Test fun compatibleCrossPlanPairIsAccepted() {
        val (a,b)=pairPlans();val rows=PlanBackup.read(raw(a,b),owner)
        assertEquals(listOf(a,b),PlanBackup.additions(rows,emptyList()))
        assertEquals(listOf(b),PlanBackup.additions(listOf(b),listOf(a)))
    }
    @Test fun sameDaySeparatePlansStillConflict() {
        val (a,b)=pairPlans()
        val sameDay=b.copy(sessions=b.sessions.map { it.copy(date=a.sessions.first().date) })
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(sameDay),listOf(a)) }
    }
    @Test fun adjacentApprovedPlansValidateCrossPlanConflictWithoutCrash() {
        val (a,b)=pairPlans()
        val x=a.copy(request=a.request.copy(dailyMinutes=60),sessions=a.sessions.map { it.copy(minutes=40) })
        val y=b.copy(
            request=b.request.copy(dailyMinutes=60,weeklySlots=mapOf("cal" to listOf(a.sessions.first().date.dayOfWeek.value))),
            sessions=b.sessions.map { it.copy(date=a.sessions.first().date,minutes=40) }
        )
        assertEquals(x,restored(x));assertEquals(y,restored(y))
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(y),listOf(x)) }
    }
    @Test fun adjacentDayRecoveryConflictInsideImportedPlanIsRejected() {
        val p=plan();val a=p.sessions.first();val b=p.sessions[1].copy(date=a.date.plusDays(1),lower=true)
        assertThrows(PlanConstraint::class.java) { restored(p.copy(sessions=listOf(a.copy(lower=true),b))) }
    }
    @Test fun alreadyRecordedHistoricalDatesAreRetainedWithoutRescheduling() {
        val p=plan();val a=p.sessions.first().copy(status="completed",log=WorkoutLog(now,minutes=15.0))
        val b=p.sessions[1].copy(date=a.date,status="completed",log=WorkoutLog(now,minutes=12.0))
        val history=p.copy(engineVersion=ENGINE1_VERSION,policyId=ENGINE1_POLICY,sessions=listOf(a,b));assertEquals(history,restored(history))
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
        val p=Engine15IndependentPlanGenerator.generateWithHistory(
            PlanRequest(start=LocalDate.parse("2026-11-02"),runDays=0,calDays=1,goal="Core strength and control",policyChoice="engine15",timeBudgetEnabled=false),
            owner,emptyList(),"hold-plan",now,library
        ).copy(status="approved")
        val session=p.sessions.first();val ex=session.exercises.first { it.seconds!=null }
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(SetRecord(ex.id,1,reps=8))),true) }
        val completed=PlanOperations.record(p,session.id,WorkoutLog(now,sets=listOf(SetRecord(ex.id,1,seconds=12))),true)
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
        val p=plan().let { it.copy(engineVersion=ENGINE1_VERSION,policyId=ENGINE1_POLICY,name=null,request=it.request.copy(policyChoice="baseline")) }
        val j=p.encode();j.remove("name");j.getJSONObject("request").remove("policyChoice");j.getJSONObject("request").remove("disciplineGoals")
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
