package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic records only. Failing assertions specify desired safety behavior; no product fixes. */
class AutonomousReliabilityRegressionTest {
    private val owner="qa-synthetic-owner"
    private val now=Instant.parse("2026-10-28T12:00:00Z")
    private val library=ExerciseLibraryV2.load { path -> java.io.File("src/main/assets",path).inputStream() }
    private fun plan():PlanSnapshot=Engine15IndependentPlanGenerator.generateWithHistory(
        PlanRequest(start=LocalDate.parse("2026-11-02"),runDays=0,strengthDays=2,policyChoice="engine15",timeBudgetEnabled=false,equipment=mapOf("db" to 1)),
        owner,emptyList(),"qa-plan",now,library
    ).copy(status="approved")
    private fun actual(sets:List<SetRecord> = emptyList())=WorkoutLog(now,sets=sets,performedOn=LocalDate.parse("2026-10-28"))
    private fun backup(p:PlanSnapshot)=JSONObject().put("schemaVersion",1).put("plans",JSONArray().put(p.encode())).toString()

    @Test fun completionRejectsEmptyResistanceLog() {
        val p=plan();assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,actual(),true) }
    }
    @Test fun completionRejectsLoadOnlySet() {
        val p=plan();val s=p.sessions.first();assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,actual(listOf(SetRecord(s.exercises.first().id,1,kg=20.0))),true) }
    }
    @Test fun completionRejectsSecondsForRepPrescription() {
        val p=plan();val s=p.sessions.first();val ex=s.exercises.first { it.reps!=null }
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,actual(listOf(SetRecord(ex.id,1,seconds=30))),true) }
    }
    @Test fun completionRejectsZeroPerformedReps() {
        val p=plan();val s=p.sessions.first();val ex=s.exercises.first { it.reps!=null }
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,actual(listOf(SetRecord(ex.id,1,reps=0))),true) }
    }
    @Test fun explicitPositiveSetCompletesAndSecondCompletionIsNoop() {
        val p=plan();val s=p.sessions.first();val ex=s.exercises.first { it.reps!=null }
        val done=PlanOperations.record(p,s.id,actual(listOf(SetRecord(ex.id,1,reps=3))),true)
        assertEquals("completed",done.sessions.first().status)
        assertEquals(done,PlanOperations.record(done,s.id,actual(),true))
    }
    @Test fun unfinishedEmptyLogRemainsInProgress() {
        val p=plan();assertEquals("in_progress",PlanOperations.record(p,p.sessions.first().id,actual(),false).sessions.first().status)
    }
    @Test fun duplicateSetCannotBeSaved() {
        val p=plan();val s=p.sessions.first();val r=SetRecord(s.exercises.first().id,1,reps=2)
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,actual(listOf(r,r)),false) }
    }
    @Test fun invalidLoadCannotReplaceProgress() {
        val p=plan();val s=p.sessions.first();val ex=s.exercises.first()
        for(kg in listOf(-1.0,Double.NaN,Double.POSITIVE_INFINITY))assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,s.id,actual(listOf(SetRecord(ex.id,1,reps=2,kg=kg))),false) }
        assertNull(p.sessions.first().log)
    }
    @Test fun futureActualDateIsRejected() {
        val p=plan();assertThrows(PlanConstraint::class.java) { PlanOperations.record(p,p.sessions.first().id,actual().copy(performedOn=LocalDate.now().plusDays(1)),false) }
    }
    @Test fun missedSessionCannotBeCompletedAndSuggestionNeedsAcceptance() {
        val p=plan();val s=p.sessions.first();val missed=PlanOperations.missed(p,s.id,"Fatigue",false)
        assertThrows(PlanConstraint::class.java) { PlanOperations.record(missed,s.id,actual(),true) }
        assertEquals(s.minutes,missed.sessions.first().minutes)
        assertFalse(missed.sessions.any { it.suggestion?.accepted==true })
    }
    @Test fun restoreRejectsUnknownExerciseInActualLog() {
        val p=plan();val first=p.sessions.first().copy(status="completed",log=actual(listOf(SetRecord("not-prescribed",99,reps=-7))))
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup(p.copy(sessions=listOf(first)+p.sessions.drop(1))),owner) }
    }
    @Test fun restoreRejectsDuplicateActualSets() {
        val p=plan();val s=p.sessions.first();val row=SetRecord(s.exercises.first().id,1,reps=2)
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup(p.copy(sessions=listOf(s.copy(log=actual(listOf(row,row))))+p.sessions.drop(1))),owner) }
    }
    @Test fun restoreRejectsConflictingSessionsInsideApprovedPlan() {
        val p=plan();val a=p.sessions[0];val b=p.sessions[1].copy(date=a.date)
        assertTrue(Engine15IndependentPlanGenerator.conflict(a,b))
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(PlanBackup.read(backup(p.copy(sessions=listOf(a,b)+p.sessions.drop(2))),owner),emptyList()) }
    }
    @Test fun restoreCannotDuplicateExistingSourceRunLink() {
        val p=plan();val linked=p.copy(sessions=listOf(p.sessions.first().copy(linkedActivityId="source-run")))
        val other=linked.copy(id="qa-other",sessions=linked.sessions.map { it.copy(id="other-session") })
        assertThrows(PlanConstraint::class.java) { PlanBackup.additions(listOf(other),listOf(linked)) }
    }
    @Test fun restoreRejectsWrongOwnerBeforeAnyAdditions() {
        assertThrows(PlanConstraint::class.java) { PlanBackup.read(backup(plan()),"different-owner") }
    }
    @Test fun dateBoundaryUsesManilaAndDoesNotInventDuration() {
        val a=ActivityRecord.parse("""{"id":"qa-activity","user_id":"$owner","source":"fit","activity_type":"Trail_Run","started_at":"2026-10-27T16:01:00Z","distance_m":1000}""",owner)
        val window=DashboardSummary.window(listOf(a,a),now,ZoneId.of("Asia/Manila"))
        assertEquals(1,window.sessionCount);assertNull(window.durationSeconds);assertEquals(1,window.missingDuration)
        assertTrue(DashboardSummary.isRun(a));assertEquals(LocalDate.parse("2026-10-28"),a.startedAt.atZone(ZoneId.of("Asia/Manila")).toLocalDate())
    }
    @Test fun sparseGpsAndMissingHeartRateStayAbsent() {
        val raw=JSONObject("""{"user_id":"$owner","activity_id":"a","fetched_at":1,"samples":[{"activity_id":"a","sample_index":0,"elapsed_s":0,"latitude":14.6},{"activity_id":"a","sample_index":1,"elapsed_s":180}],"laps":[]}""")
        val detail=ActivityDetail.parse(raw,owner,"a")
        assertNull(detail.samples[0].longitude);assertNull(detail.samples[0].hr);assertNull(detail.samples[1].latitude)
        assertTrue(detail.laps.isEmpty())
    }
    @Test fun invalidGpsAndNonmonotonicSamplesAreRejected() {
        val raw=JSONObject("""{"user_id":"$owner","activity_id":"a","fetched_at":1,"samples":[{"activity_id":"a","sample_index":0,"elapsed_s":0,"latitude":91}],"laps":[]}""")
        assertThrows(IllegalArgumentException::class.java) { ActivityDetail.parse(raw,owner,"a") }
        raw.getJSONArray("samples").getJSONObject(0).remove("latitude")
        raw.getJSONArray("samples").put(JSONObject("""{"activity_id":"a","sample_index":0,"elapsed_s":1}"""))
        assertThrows(IllegalArgumentException::class.java) { ActivityDetail.parse(raw,owner,"a") }
    }
}
