package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Isolated real SQLite/Keystore and fake auth transport. Never contacts an athlete backend. */
class Engine1RepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val databaseName="synthetic-engine1.db";private val prefs="synthetic-engine1";private val owner="synthetic-engine1-owner"
    private lateinit var repo:TrainingRepository;private val gateway=Gateway()
    private fun open()=runBlocking { repo=TrainingRepository(context,gateway,databaseName,prefs);repo.state.first { !it.initializing } }
    @Before fun setup() { context.deleteDatabase(databaseName);context.getSharedPreferences(prefs,0).edit().clear().commit();SessionVault(context).clear(owner);gateway.owner=owner;gateway.offline=false;gateway.rows=JSONArray();open() }
    @After fun cleanup() { repo.close();context.deleteDatabase(databaseName);context.getSharedPreferences(prefs,0).edit().clear().commit();SessionVault(context).clear(owner);SessionVault(context).clear("synthetic-engine1-other") }
    private fun request()=PlanRequest(start=LocalDate.parse("2026-11-02"),runDays=0,strengthDays=2,weeks=4,equipment=mapOf("db" to 1))
    @Test fun generationDraftApprovalCalendarAndActualLoggingSurviveOfflineReopen()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertTrue(repo.generatePlan(request()))
        val draft=repo.state.value.plans.single();assertEquals("draft",draft.status);assertEquals(8,draft.sessions.size)
        assertTrue(repo.approvePlan(draft.id,draft.revision));val approved=repo.state.value.plans.single();val session=approved.sessions.first()
        val ex=session.exercises.first();val log=WorkoutLog(Instant.parse("2026-11-02T00:00:00Z"),minutes=24.0,effort=4,sets=listOf(SetRecord(ex.id,1,reps=8,kg=5.0)),notes="Controlled")
        assertTrue(repo.recordWorkout(approved.id,approved.revision,session.id,log,false))
        repo.close();gateway.offline=true;open()
        val reopened=repo.state.value.plans.single();assertEquals("approved",reopened.status);assertEquals(approved.sessions.map { it.id },reopened.sessions.map { it.id })
        assertEquals(5.0,reopened.sessions.first().log!!.sets.single().kg!!,0.0);assertEquals("in_progress",reopened.sessions.first().status)
        assertTrue(repo.recordWorkout(reopened.id,reopened.revision,session.id,log,true));val done=repo.state.value.plans.single()
        assertEquals(1,done.sessions.count { it.status=="completed" });assertNull(done.sessions.first().log!!.distanceKm)
        assertFalse(repo.recordWorkout(reopened.id,reopened.revision,session.id,log,true));assertEquals(1,repo.state.value.plans.single().sessions.count { it.status=="completed" })
        repo.close();open();assertEquals("completed",repo.state.value.plans.single().sessions.first().status)
    }
    @Test fun multiSessionPlanIndependentLogsPersistInSqliteAndReopenOffline()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        val start=LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
        val request=PlanRequest(goal="Improve 5K",start=start,weeks=1,days=(1..7).toSet(),runDays=5,calDays=3,longDay=6,weeklyKm=30.0,longestKm=12.0,policyChoice="multi-session",dailyMinutes=90)
        assertTrue(repo.generatePlan(request))
        var plan=repo.state.value.plans.single()
        assertEquals(8,plan.sessions.size)
        assertEquals(7,plan.sessions.map { it.date }.distinct().size)
        assertTrue(repo.approvePlan(plan.id,plan.revision));plan=repo.state.value.plans.single()
        val pair=plan.sessions.groupBy { it.date }.values.single { it.size==2 }
        val run=pair.single { it.discipline=="run" };val cal=pair.single { it.discipline=="cal" }
        assertTrue(repo.recordWorkout(plan.id,plan.revision,run.id,WorkoutLog(Instant.now(),minutes=20.0,distanceKm=3.1),true))
        plan=repo.state.value.plans.single()
        assertTrue(repo.recordWorkout(plan.id,plan.revision,cal.id,WorkoutLog(Instant.now(),minutes=25.0,effort=4),true))
        plan=repo.state.value.plans.single()
        assertEquals("completed",plan.sessions.single { it.id==run.id }.status)
        assertEquals("completed",plan.sessions.single { it.id==cal.id }.status)
        assertEquals(3.1,plan.sessions.single { it.id==run.id }.log!!.distanceKm!!,0.0)
        assertNull(plan.sessions.single { it.id==cal.id }.log!!.distanceKm)

        val persistedDatabase=context.openOrCreateDatabase(databaseName,Context.MODE_PRIVATE,null)
        try {
            persistedDatabase.query("records",arrayOf("payload"),"account=? AND kind=? AND record_id=?",arrayOf(owner,"plan",plan.id),null,null,null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                val persisted=PlanSnapshot.decode(JSONObject(cursor.getString(0)),owner)
                assertEquals("completed",persisted.sessions.single { it.id==run.id }.status)
                assertEquals(20.0,persisted.sessions.single { it.id==run.id }.log!!.minutes!!,0.0)
                assertEquals("completed",persisted.sessions.single { it.id==cal.id }.status)
                assertEquals(25.0,persisted.sessions.single { it.id==cal.id }.log!!.minutes!!,0.0)
            }
        } finally { persistedDatabase.close() }

        repo.close();gateway.offline=true;open()
        val reopened=repo.state.value.plans.single()
        assertEquals(plan,reopened)
        assertEquals(2,reopened.sessions.count { it.status=="completed" })
    }
    @Test fun repositoryMovementRejectsAnotherApprovedPlansIncompatibleSession()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        val start=LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
        val calRequest=PlanRequest(goal="Calisthenics foundation",start=start,weeks=1,days=(1..7).toSet(),runDays=0,calDays=1,policyChoice="multi-session",dailyMinutes=90)
        assertTrue(repo.generatePlan(calRequest))
        var calPlan=repo.state.value.plans.single()
        assertTrue(repo.approvePlan(calPlan.id,calPlan.revision));calPlan=repo.state.value.plans.single()
        val runRequest=PlanRequest(goal="Improve 5K",start=start,weeks=1,days=(1..7).toSet(),runDays=1,longDay=6,policyChoice="baseline")
        assertTrue(repo.generatePlan(runRequest))
        var runPlan=repo.state.value.plans.first { it.id!=calPlan.id }
        assertTrue(repo.approvePlan(runPlan.id,runPlan.revision));runPlan=repo.state.value.plans.first { it.id==runPlan.id }
        val cal=calPlan.sessions.single()
        val longRun=runPlan.sessions.single { it.kind=="long" }
        assertNotEquals(cal.date,longRun.date)
        assertFalse(repo.moveSession(calPlan.id,calPlan.revision,cal.id,longRun.date))
        assertEquals(cal.date,repo.state.value.plans.single { it.id==calPlan.id }.sessions.single { it.id==cal.id }.date)
        assertTrue(repo.state.value.plans.single { it.id==runPlan.id }.sessions.single { it.id==longRun.id }.date==longRun.date)
    }
    @Test fun plansAndPrivateFeedbackAreOwnerIsolatedAndPreservedAcrossAccountSwitches()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertTrue(repo.generatePlan(request()))
        val p=repo.state.value.plans.single();assertTrue(repo.saveFeedback(p.id,p.revision,"Usability","The calendar is readable"))
        val saved=repo.state.value.plans.single();assertEquals(1,saved.feedback.size)
        assertTrue(repo.signOut());assertTrue(repo.state.value.plans.isEmpty())
        gateway.owner="synthetic-engine1-other";assertTrue(repo.signIn("other@example.invalid","fixture"));assertTrue(repo.state.value.plans.isEmpty())
        gateway.owner=owner;assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertEquals(saved,repo.state.value.plans.single())
        assertEquals(saved,PlanSnapshot.decode(saved.encode(),owner))
    }
    @Test fun conflictsAndInvalidPrescriptionsDoNotReplaceTheLastAcceptedPlan()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertTrue(repo.generatePlan(request()))
        val p=repo.state.value.plans.single();assertTrue(repo.approvePlan(p.id,p.revision));val accepted=repo.state.value.plans.single()
        assertFalse(repo.generatePlan(request().copy(strengthDays=4,calDays=3,days=setOf(1,2))))
        assertEquals(accepted,repo.state.value.plans.single());assertTrue(repo.state.value.message!!.contains("no discipline will be dropped"))
        assertTrue(repo.generatePlan(request()));val other=repo.state.value.plans.first { it.status=="draft" }
        assertFalse(repo.approvePlan(other.id,other.revision));assertEquals(1,repo.state.value.plans.count { it.status=="approved" })
        val first=accepted.sessions.first();val second=accepted.sessions[1]
        assertFalse(repo.moveSession(accepted.id,accepted.revision,first.id,second.date));assertEquals(accepted,repo.state.value.plans.find { it.id==accepted.id })
    }
    @Test fun missReasonSuggestionsAndFeedbackStayDurableWithoutNetwork()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertTrue(repo.generatePlan(request()));var p=repo.state.value.plans.single()
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single();gateway.offline=true
        assertTrue(repo.markMissed(p.id,p.revision,p.sessions.first().id,"Fatigue",false));p=repo.state.value.plans.single()
        val next=p.sessions.first { it.suggestion!=null };assertFalse(next.suggestion!!.accepted)
        assertTrue(repo.acceptSuggestion(p.id,p.revision,next.id));p=repo.state.value.plans.single()
        assertTrue(repo.saveFeedback(p.id,p.revision,"Inappropriate prescription","Review the session after fatigue",next.id))
        repo.close();open();p=repo.state.value.plans.single();assertEquals(1,p.feedback.size);assertTrue(p.sessions.find { it.id==next.id }!!.suggestion!!.accepted)
    }
    @Test fun libraryShelfAndNonDestructiveBackupRestoreSurviveOfflineReopen()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        assertTrue(repo.changeShelf("sq"));assertTrue(repo.changeShelf("sq","Home practice"))
        assertTrue(repo.generatePlan(request()));var p=repo.state.value.plans.single()
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single()
        assertTrue(repo.recordWorkout(p.id,p.revision,p.sessions.first().id,WorkoutLog(Instant.now(),minutes=20.0),true));p=repo.state.value.plans.single()
        val later=Engine1.generate(request().copy(start=request().start.plusWeeks(5)),owner)
        val raw=JSONObject().put("schemaVersion",1).put("plans",JSONArray().put(p.copy(feedback=emptyList()).encode()).put(later.encode())).toString()
        gateway.offline=true
        assertTrue(repo.restorePlans(raw));assertEquals(2,repo.state.value.plans.size);assertEquals(p,repo.state.value.plans.find { it.id==p.id })
        assertTrue(repo.restorePlans(raw));assertEquals(2,repo.state.value.plans.size)
        assertFalse(repo.restorePlans(raw.replace(owner,"different-owner")));assertEquals(2,repo.state.value.plans.size)
        repo.close();open();assertTrue("sq" in repo.state.value.shelf.favorites);assertEquals(setOf("sq"),repo.state.value.shelf.collections["Home practice"])
        assertEquals(1,repo.state.value.plans.flatMap { it.sessions }.count { it.status=="completed" })
        gateway.offline=false;gateway.owner="synthetic-engine1-other";assertTrue(repo.signIn("other@example.invalid","fixture"));assertTrue(repo.state.value.shelf.favorites.isEmpty());assertTrue(repo.state.value.plans.isEmpty())
    }
    @Test fun manualSourceRunLinksAreUniqueAndReversibleWithoutLosingOfflineRecords()=runBlocking {
        gateway.rows=JSONArray().put(JSONObject().put("id","synthetic-source-run").put("user_id",owner).put("title","Source run fixture").put("source","intervals").put("activity_type","run").put("started_at","2026-10-24T00:00:00Z").put("distance_m",3000).put("duration_s",1200))
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertEquals(1,repo.state.value.activities.size)
        assertTrue(repo.generatePlan(PlanRequest(start=request().start)));var p=repo.state.value.plans.single()
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single();val first=p.sessions.first().id;val second=p.sessions[1].id
        assertTrue(repo.linkActivity(p.id,p.revision,first,"synthetic-source-run"));p=repo.state.value.plans.single()
        assertEquals("completed",p.sessions.first { it.id==first }.status);assertNull(p.sessions.first { it.id==first }.log)
        assertFalse(repo.linkActivity(p.id,p.revision,second,"synthetic-source-run"));assertFalse(repo.linkActivity(p.id,p.revision,second,"unknown-source"))
        gateway.offline=true;repo.close();open();p=repo.state.value.plans.single()
        assertEquals("synthetic-source-run",p.sessions.first { it.id==first }.linkedActivityId)
        assertTrue(repo.linkActivity(p.id,p.revision,first,null));p=repo.state.value.plans.single()
        assertEquals("planned",p.sessions.first { it.id==first }.status);assertEquals(1,repo.state.value.activities.size)
    }
    @Test fun easierExerciseAndItsActualSetReopenOfflineWithOriginalPrescriptionIntact()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"));assertTrue(repo.generatePlan(request()));var p=repo.state.value.plans.single()
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single();val original=p.sessions.first().exercises.first();val sid=p.sessions.first().id
        assertTrue(repo.swapExercise(p.id,p.revision,sid,original.id));p=repo.state.value.plans.single();val replacement=p.sessions.first().effectiveExercises.first()
        assertTrue(repo.recordWorkout(p.id,p.revision,sid,WorkoutLog(Instant.now(),sets=listOf(SetRecord(replacement.id,1,reps=5))),false))
        repo.close();gateway.offline=true;open();p=repo.state.value.plans.single()
        assertEquals(original,p.sessions.first().exercises.first());assertEquals(replacement,p.sessions.first().effectiveExercises.first());assertEquals(replacement.id,p.sessions.first().log!!.sets.single().exerciseId)
        assertFalse(repo.swapExercise(p.id,p.revision,sid,original.id));assertEquals(1,repo.state.value.plans.single().sessions.first().swaps.size)
    }
    @Test fun savedTemplatePracticeApprovalAndWorkoutRemainIsolatedAndDurableOffline()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        val template=SessionTemplate("synthetic-session-template","Squat practice","str",30,listOf(ExercisePrescription("sq","Bodyweight squat",2,6)))
        assertTrue(repo.saveTemplate(template));gateway.offline=true;repo.close();open();assertEquals(template,repo.state.value.templates.single())
        assertTrue(repo.prepareSession(template,PlanRequest(),LocalDate.now()));var p=repo.state.value.plans.single();assertEquals("draft",p.status)
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single();assertTrue(repo.recordWorkout(p.id,p.revision,p.sessions.first().id,WorkoutLog(Instant.now(),minutes=20.0),true))
        assertTrue(repo.saveTemplate(template.copy(name="Changed future template",exercises=listOf(ExercisePrescription("sq","Bodyweight squat",3,8)))))
        repo.close();open();assertEquals(2,repo.state.value.plans.single().sessions.first().exercises.single().sets);assertEquals(3,repo.state.value.templates.single().exercises.single().sets)
        gateway.offline=false;gateway.owner="synthetic-engine1-other";assertTrue(repo.signIn("other@example.invalid","fixture"));assertTrue(repo.state.value.templates.isEmpty());assertTrue(repo.state.value.plans.isEmpty())
    }
    @Test fun coordinatedGoalsAndFallbackSnapshotsKeepTheirOwnPolicyAndActualRecordsOffline()=runBlocking {
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        val request=request().copy(policyChoice="coordinated",disciplineGoals=mapOf("str" to "Muscle development"),strengthExperience=1)
        assertTrue(repo.generatePlan(request));var p=repo.state.value.plans.single();assertEquals(CoordinatedEngine1.policyId,p.policyId)
        assertTrue(p.sessions.flatMap { it.exercises }.filter { it.reps!=null }.all { it.reps==10 })
        assertTrue(repo.approvePlan(p.id,p.revision));p=repo.state.value.plans.single();assertTrue(repo.recordWorkout(p.id,p.revision,p.sessions.first().id,WorkoutLog(Instant.now(),minutes=20.0),true));val saved=repo.state.value.plans.single()
        gateway.offline=true;assertTrue(repo.generatePlan(request.copy(start=request.start.plusWeeks(5),policyChoice="baseline")));repo.close();open()
        assertEquals(saved,repo.state.value.plans.find { it.id==saved.id });val fallback=repo.state.value.plans.first { it.id!=saved.id };assertEquals(ENGINE1_POLICY,fallback.policyId);assertTrue(fallback.sessions.flatMap { it.exercises }.filter { it.reps!=null }.all { it.reps==8 })
    }
    private class Gateway:TrainingGateway {
        var owner="synthetic-engine1-owner";var offline=false;var rows=JSONArray()
        private fun connected() { if(offline)throw java.io.IOException("Synthetic offline") }
        override fun signIn(email:String,password:String):NativeSession { connected();return NativeSession(owner,"synthetic-token","synthetic-refresh",Long.MAX_VALUE) }
        override fun refresh(session:NativeSession)=session
        override fun verify(session:NativeSession) { connected() }
        override fun activities(session:NativeSession,before:ActivityRecord?):JSONArray { connected();return if(before==null) rows else JSONArray() }
        override fun intervals(session:NativeSession,action:String):JSONObject { connected();return JSONObject().put("configured",false) }
        override fun signOut(session:NativeSession) { connected() }
    }
}
