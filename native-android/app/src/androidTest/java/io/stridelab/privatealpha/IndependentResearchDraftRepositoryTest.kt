package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

/**
 * Queued for M10.1 Android instrumentation after full M10 integration.
 * Uses synthetic SQLite, fake cloud transport and no actual device data.
 */
class IndependentResearchDraftRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = "synthetic-m10c-research.db"
    private val prefs = "synthetic-m10c-research-prefs"
    private val cloudOwner = "synthetic-m10c-cloud"
    private lateinit var repo: TrainingRepository
    private val gateway = FakeGateway()

    private fun open() = runBlocking {
        repo = TrainingRepository(context,gateway,database,prefs)
        repo.state.first { !it.initializing }
    }

    @Before fun setup() {
        context.deleteDatabase(database)
        context.getSharedPreferences(prefs,Context.MODE_PRIVATE)
            .edit().clear().commit()
        SessionVault(context).clear(cloudOwner)
        open()
    }
    @After fun cleanup() {
        if (::repo.isInitialized) repo.close()
        context.deleteDatabase(database)
        context.getSharedPreferences(prefs,Context.MODE_PRIVATE)
            .edit().clear().commit()
        SessionVault(context).clear(cloudOwner)
    }

    private val request = PlanRequest(
        goal = "General fitness",experience = 2,
        start = LocalDate.of(2026,11,16),weeks = 5,
        days = setOf(1,2,3,5,6,7),runDays = 6,
        qualityCount = 1,qualityDays = setOf(2),longDay = 6,
        weeklyKm = 60.0,targetWeeklyKm = 80.0,
        timeBudgetEnabled = false
    )
    private val bounds = IndependentAndroidPreviewConstraints(
        selectedRunDays = request.days,
        explicitlyEnteredStartingWeeklyKm = 60.0,
        explicitlyEnteredWeeklyCeilingKm = 80.0,
        explicitlyEnteredMaxKmByDay = request.days.associateWith {
            if (it == 1) 5.0 else 25.0
        },
        explicitlyEnteredPreviousLongRunKm = 17.0,
        explicitlyEnteredLongRunCeilingKm = 25.0,
        explicitlyEnteredMaxLongRunIncreaseKm = 2.0
    )

    private fun count(owner:String, table:String):Int = count(database,owner,table)

    private fun count(databaseName:String,owner:String,table:String):Int =
        context.openOrCreateDatabase(databaseName,Context.MODE_PRIVATE,null).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM $table WHERE account=?",
                arrayOf(owner)).use { c ->
                check(c.moveToFirst());c.getInt(0)
            }
        }

    private fun recordCount(databaseName:String,owner:String,kind:String):Int =
        context.openOrCreateDatabase(databaseName,Context.MODE_PRIVATE,null).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM records WHERE account=? AND kind=?",arrayOf(owner,kind)).use { c ->
                check(c.moveToFirst());c.getInt(0)
            }
        }

    @Test fun savedResearchReviewHasNoExecutablePlanOrOutboxAndReopensOffline() = runBlocking {
        val owner = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        assertTrue(repo.state.value.plans.isEmpty())
        val saved = repo.state.value.researchDrafts.single()
        assertEquals(owner,saved.owner)
        assertFalse(saved.executable)
        assertFalse(saved.authorizedForProduction)
        assertEquals(5,saved.weeks.size)
        assertEquals(0,count(owner,"outbox"))
        assertEquals(1,count(owner,"records"))
        repo.close();open()
        assertEquals(saved,repo.state.value.researchDrafts.single())
        assertTrue(repo.state.value.plans.isEmpty())
        assertEquals(0,count(owner,"outbox"))
    }

    @Test fun selectingExistingCoachDoesNotRewriteResearchOrChangePolicy() = runBlocking {
        val original = request.copy()
        assertTrue(IndependentEngineRouteGate.permitsProductionGeneration(
            IndependentEngineRoute.EXISTING_COACH))
        assertFalse(IndependentEngineRouteGate.permitsProductionGeneration(
            IndependentEngineRoute.RESEARCH_COMPARISON))
        assertTrue(repo.saveResearchReview(request,bounds))
        assertEquals(original,request)
        assertEquals("baseline",request.policyChoice)
        assertTrue(repo.state.value.plans.isEmpty())
    }

    @Test fun cloudSignInHidesLocalResearchAndNeverUploadsIt() = runBlocking {
        val localOwner = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        val local = repo.state.value.researchDrafts.single()
        assertTrue(repo.signIn("synthetic@example.invalid","fixture"))
        assertEquals(cloudOwner,repo.state.value.account)
        assertTrue(repo.state.value.researchDrafts.isEmpty())
        assertTrue(repo.saveResearchReview(request,bounds))
        val cloudResearch = repo.state.value.researchDrafts.single()
        assertEquals(cloudOwner,cloudResearch.owner)
        assertEquals(0,count(localOwner,"outbox"))
        assertEquals(0,count(cloudOwner,"outbox"))
        assertTrue(repo.signOut())
        assertEquals(local,repo.state.value.researchDrafts.single())
        assertNotEquals(cloudResearch.id,repo.state.value.researchDrafts.single().id)
        assertTrue(repo.state.value.plans.isEmpty())
    }

    @Test fun unsupportedSerializedResearchCannotBeReadAsExecutableSavedPlan() = runBlocking {
        val owner = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        val draft = repo.state.value.researchDrafts.single()
        val raw = draft.encode()
        assertFalse(raw.has("sessions"))
        assertFalse(raw.has("engineVersion"))
        assertFalse(raw.has("status"))
        try {
            PlanSnapshot.decode(raw,owner)
            fail("Research must not be loaded as a production PlanSnapshot")
        } catch (_: Exception) { }
        try {
            IndependentResearchDraft.decode(JSONObject(raw.toString())
                .put("authorizedForProduction",true),owner)
            fail("Production authority cannot be imported")
        } catch (_: IllegalArgumentException) { }
        val foreign = "other-device-profile"
        try {
            IndependentResearchDraft.decode(raw,foreign)
            fail("Other owners cannot read research artifacts")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0,count(owner,"outbox"))
    }

    @Test fun invalidResearchRequestCannotMutateApprovedPlansOrCachedResearch() = runBlocking {
        val owner = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        repo.generatePlan(PlanRequest(
            goal="General strength",start=LocalDate.of(2026,11,16),weeks=2,
            runDays=0,strengthDays=2
        ))
        val draftPlan=repo.state.value.plans.single()
        repo.approvePlan(draftPlan.id,draftPlan.revision)
        val approved=repo.state.value.plans.single()
        assertEquals("approved",approved.status)
        val existing = repo.state.value.researchDrafts.single()
        assertFalse(repo.saveResearchReview(
            request.copy(goal="Marathon"),bounds
        ))
        assertFalse(repo.saveResearchReview(
            request,bounds.copy(explicitlyEnteredWeeklyCeilingKm=null)
        ))
        assertEquals(listOf(existing),repo.state.value.researchDrafts)
        assertEquals(listOf(approved),repo.state.value.plans)
        assertEquals(0,count(owner,"outbox"))
    }

    @Test fun mixedBackupRestoresApprovedPlanAndResearchOnlyForMatchingOwner() = runBlocking {
        val owner=requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        repo.generatePlan(PlanRequest(
            goal="General strength",start=LocalDate.of(2026,11,16),weeks=2,
            runDays=0,strengthDays=2
        ))
        val plan=repo.state.value.plans.single()
        repo.approvePlan(plan.id,plan.revision)
        val approved=repo.state.value.plans.single()
        val savedReview=repo.state.value.researchDrafts.single()
        val raw=PlanBackup.encode(repo.state.value.plans,repo.state.value.researchDrafts,owner)

        val restoreDatabase="synthetic-m10c-restore.db"
        context.deleteDatabase(restoreDatabase)
        val restored=TrainingRepository(context,gateway,restoreDatabase,prefs)
        try {
            restored.state.first { !it.initializing }
            restored.restorePlans(raw)
            assertEquals(listOf(approved),restored.state.value.plans)
            assertEquals(listOf(savedReview),restored.state.value.researchDrafts)
            assertFalse(restored.state.value.researchDrafts.single().executable)
            assertEquals(0,count(restoreDatabase,owner,"outbox"))

            assertTrue(restored.signIn("synthetic@example.invalid","fixture"))
            assertFalse(restored.restorePlans(raw))
            assertTrue(restored.state.value.plans.isEmpty())
            assertTrue(restored.state.value.researchDrafts.isEmpty())
            assertEquals(0,count(restoreDatabase,cloudOwner,"outbox"))
            assertTrue(restored.signOut())
            assertEquals(listOf(savedReview),restored.state.value.researchDrafts)
        } finally {
            restored.close()
            context.deleteDatabase(restoreDatabase)
        }
    }

    @Test fun opaqueResearchIdConflictPreventsAnyPartialBackupRestore() = runBlocking {
        val owner=requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.saveResearchReview(request,bounds))
        repo.generatePlan(PlanRequest(
            goal="General strength",start=LocalDate.of(2026,11,16),weeks=2,
            runDays=0,strengthDays=2
        ))
        val draft=repo.state.value.plans.single()
        repo.approvePlan(draft.id,draft.revision)
        val raw=PlanBackup.encode(repo.state.value.plans,repo.state.value.researchDrafts,owner)
        val reviewId=repo.state.value.researchDrafts.single().recordId
        val restoreDatabase="synthetic-m10c-conflict-restore.db"
        context.deleteDatabase(restoreDatabase)
        val restored=TrainingRepository(context,gateway,restoreDatabase,prefs)
        try {
            restored.state.first { !it.initializing }
            context.openOrCreateDatabase(restoreDatabase,Context.MODE_PRIVATE,null).use { db ->
                db.execSQL("INSERT INTO records(account,kind,record_id,payload,server_version,synced_at) VALUES(?,?,?,?,?,?)",
                    arrayOf<Any?>(owner,"exercise",reviewId,"{}",null,0))
            }
            assertFalse(restored.restorePlans(raw))
            assertTrue(restored.state.value.plans.isEmpty())
            assertEquals(0,recordCount(restoreDatabase,owner,"plan"))
            assertEquals(1,recordCount(restoreDatabase,owner,"exercise"))
        } finally {
            restored.close()
            context.deleteDatabase(restoreDatabase)
        }
    }

    private class FakeGateway : TrainingGateway {
        override fun signIn(email:String,password:String) =
            NativeSession("synthetic-m10c-cloud","synthetic-token",
                "synthetic-refresh",Long.MAX_VALUE)
        override fun refresh(session:NativeSession) = session
        override fun verify(session:NativeSession) = Unit
        override fun activities(session:NativeSession,before:ActivityRecord?):JSONArray = JSONArray()
        override fun signOut(session:NativeSession) = Unit
    }
}
