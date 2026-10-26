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

/** Isolated real SQLite/Keystore and fake auth transport. Never contacts an athlete backend. */
class Engine1RepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val database="synthetic-engine1.db";private val prefs="synthetic-engine1";private val owner="synthetic-engine1-owner"
    private lateinit var repo:TrainingRepository;private val gateway=Gateway()
    private fun open()=runBlocking { repo=TrainingRepository(context,gateway,database,prefs);repo.state.first { !it.initializing } }
    @Before fun setup() { context.deleteDatabase(database);context.getSharedPreferences(prefs,0).edit().clear().commit();SessionVault(context).clear(owner);gateway.owner=owner;gateway.offline=false;open() }
    @After fun cleanup() { repo.close();context.deleteDatabase(database);context.getSharedPreferences(prefs,0).edit().clear().commit();SessionVault(context).clear(owner);SessionVault(context).clear("synthetic-engine1-other") }
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
    private class Gateway:TrainingGateway {
        var owner="synthetic-engine1-owner";var offline=false
        private fun connected() { if(offline)throw java.io.IOException("Synthetic offline") }
        override fun signIn(email:String,password:String):NativeSession { connected();return NativeSession(owner,"synthetic-token","synthetic-refresh",Long.MAX_VALUE) }
        override fun refresh(session:NativeSession)=session
        override fun verify(session:NativeSession) { connected() }
        override fun activities(session:NativeSession,before:ActivityRecord?):JSONArray { connected();return JSONArray() }
        override fun intervals(session:NativeSession,action:String):JSONObject { connected();return JSONObject().put("configured",false) }
        override fun signOut(session:NativeSession) { connected() }
    }
}
