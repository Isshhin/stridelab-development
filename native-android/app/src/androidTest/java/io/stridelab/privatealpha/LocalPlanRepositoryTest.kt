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

/**
 * M10b regression tests committed for M10.1 instrumented execution.
 * Synthetic SQLite namespace, no athlete data, no real network.
 */
class LocalPlanRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "synthetic-m10b-local-plans.db"
    private val prefs = "synthetic-m10b-local-plans"
    private val cloudOwner = "synthetic-m10b-cloud-athlete"
    private lateinit var repo: TrainingRepository
    private val gateway = FakeGateway()

    private fun open() = runBlocking {
        repo = TrainingRepository(context, gateway, databaseName, prefs)
        repo.state.first { !it.initializing }
    }

    @Before fun before() {
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(cloudOwner)
        gateway.owner = cloudOwner
        open()
    }

    @After fun after() {
        if (::repo.isInitialized) repo.close()
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(cloudOwner)
    }

    private fun library() = ExerciseLibraryV2.load { path -> context.assets.open(path) }

    private fun request() = PlanRequest(
        goal = "General fitness",
        start = LocalDate.now().plusWeeks(1),
        weeks = 4, runDays = 0, strengthDays = 2,
        equipment = mapOf("db" to 1),
        timeBudgetEnabled = false
    )

    private fun rawPlanCount(owner: String): Int =
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM records WHERE account=? AND kind='plan'",
                arrayOf(owner)
            ).use { c -> check(c.moveToFirst()); c.getInt(0) }
        }

    private fun outboxCount(owner: String): Int =
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM outbox WHERE account=?",
                arrayOf(owner)
            ).use { c -> check(c.moveToFirst()); c.getInt(0) }
        }

    @Test fun signedOutGenerationAndApprovalPersistAfterReopeningWithNoCloudOutbox() = runBlocking {
        assertNull(repo.state.value.account)
        val deviceOwner = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(LocalPlanOwnership.isLocalPlanOwner(deviceOwner))
        assertTrue(repo.generatePlan(request()))
        var plan = requireNotNull(repo.state.value.planPreview)
        assertEquals(deviceOwner, plan.owner)
        assertEquals("draft", plan.status)
        assertEquals(0, rawPlanCount(deviceOwner))
        assertTrue(repo.state.value.plans.isEmpty())
        assertTrue(repo.approvePlan(plan.id, plan.revision))
        plan = repo.state.value.plans.single()
        assertEquals("approved", plan.status)
        assertEquals(1, rawPlanCount(deviceOwner))
        assertEquals(0, outboxCount(deviceOwner))
        repo.close()
        open()
        assertNull(repo.state.value.account)
        assertEquals(deviceOwner, repo.state.value.localPlanOwner)
        assertEquals(plan, repo.state.value.plans.single())
        assertEquals(0, outboxCount(deviceOwner))
    }

    @Test fun abandonedPreviewDoesNotSurviveAndLegacyDraftIsHidden() = runBlocking {
        assertTrue(repo.generatePlan(request()))
        val preview = requireNotNull(repo.state.value.planPreview)
        repo.discardPlanPreview()
        assertNull(repo.state.value.planPreview)
        assertEquals(0, rawPlanCount(preview.owner))
        TrainingStore(context, databaseName).use { store ->
            store.applyRemotePage(preview.owner, "plan",
                listOf(TrainingStore.RemoteRecord(preview.id, preview.encode().toString(), null)), null, 1L)
        }
        repo.close()
        open()
        assertTrue(repo.state.value.plans.isEmpty())
        assertNull(repo.state.value.planPreview)
    }

    @Test fun localEntryDraftAndFeedbackRemainPrivateAndSurviveRestart() = runBlocking {
        assertTrue(repo.generatePlan(request()))
        var plan = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(plan.id, plan.revision))
        plan = repo.state.value.plans.single()
        val session = plan.sessions.first()
        val savedDraft = WorkoutEntryDraft(
            owner = plan.owner,
            planId = plan.id,
            sessionId = session.id,
            logRevision = session.logRevision,
            minutes = "25", distance = "", effort = "4",
            notes = "Saved locally", performedOn = "", entries = "{}",
            editedAt = 1
        )
        assertTrue(repo.stageWorkoutDraft(savedDraft))
        assertTrue(repo.saveFeedback(plan.id, plan.revision, "Usability", "Offline training works"))
        assertEquals(0, outboxCount(plan.owner))
        repo.close()
        open()
        assertNotNull(repo.state.value.workoutDrafts[plan.id to session.id])
        val restored = repo.state.value.plans.single()
        assertEquals(1, restored.feedback.size)
        assertEquals("Offline training works", restored.feedback.single().text)
        assertEquals(0, outboxCount(plan.owner))
    }

    @Test fun guidedSetAndRestRecoverTogetherAndFinalSaveRemainsLocal() = runBlocking {
        assertTrue(repo.generatePlan(request()))
        val preview = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(preview.id, preview.revision))
        val plan = repo.state.value.plans.single()
        val session = plan.sessions.first()
        val started = requireNotNull(repo.executeWorkout(plan.id, session.id, null, null))
        val target = started.current.target
        val actual = SetActual(reps = target.reps, seconds = target.seconds)
        val confirmed = requireNotNull(repo.executeWorkout(plan.id, session.id, started.revision, PlayerEvent.CompleteSet(actual)))
        assertEquals(PlayerPhase.Resting, confirmed.phase)
        assertEquals(0, outboxCount(plan.owner))
        repo.close()
        open()

        val recovered = requireNotNull(repo.executeWorkout(plan.id, session.id, null, null))
        assertEquals(confirmed.executionId, recovered.executionId)
        assertEquals(PlayerPhase.Resting, recovered.phase)
        assertEquals(actual, recovered.current.actual)
        val review = requireNotNull(repo.executeWorkout(plan.id, session.id, recovered.revision, PlayerEvent.FinishEarly))
        val final = PlayerEvent.FinalizeSession(Instant.now(), 5, "", LocalDate.now())
        val saved = requireNotNull(repo.executeWorkout(plan.id, session.id, review.revision, final))
        assertTrue(saved.receipt)
        val committed = repo.state.value.plans.single()
        val repeated = requireNotNull(repo.executeWorkout(plan.id, session.id, review.revision, final))
        assertEquals(saved, repeated)
        assertEquals(committed, repo.state.value.plans.single())

        // Recreate the durable state immediately after the plan commit, before the receipt write.
        val frozen = saved.copy(receipt = false, phase = PlayerPhase.Reviewing)
        val key = WorkoutExecutionCheckpoint.key(plan.id, session.id)
        val wall = System.currentTimeMillis()
        val payload = WorkoutExecutionCheckpoint.encode(plan.owner, frozen, android.os.SystemClock.elapsedRealtime(), wall)
        TrainingStore(context, databaseName).use { store ->
            store.applyRemotePage(plan.owner, "workout", listOf(TrainingStore.RemoteRecord(key, payload, null)), null, wall)
        }
        repo.close()
        open()
        val recoveredSave = requireNotNull(repo.executeWorkout(plan.id, session.id, review.revision, final.copy(effort = 10)))
        assertTrue(recoveredSave.receipt)
        assertEquals(saved.finalizationId, recoveredSave.finalizationId)
        assertEquals(saved.finalLog, recoveredSave.finalLog)
        assertEquals(committed, repo.state.value.plans.single())
        assertEquals(1, rawPlanCount(plan.owner))
        assertEquals(0, outboxCount(plan.owner))
    }

    @Test fun localPlansHideDuringCloudSignInAndReappearAfterSignOut() = runBlocking {
        assertTrue(repo.generatePlan(request()))
        val deviceOwner = requireNotNull(repo.state.value.localPlanOwner)
        val devicePreview = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(devicePreview.id, devicePreview.revision))
        val devicePlan = repo.state.value.plans.single()
        assertTrue(repo.signIn("synthetic@example.invalid", "fixture"))
        assertEquals(cloudOwner, repo.state.value.account)
        assertEquals(deviceOwner, repo.state.value.localPlanOwner)
        assertTrue(repo.state.value.plans.isEmpty())
        assertTrue(repo.generatePlan(request()))
        val cloudPreview = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(cloudPreview.id, cloudPreview.revision))
        val cloudPlan = repo.state.value.plans.single()
        assertEquals(cloudOwner, cloudPlan.owner)
        assertNotEquals(devicePlan.id, cloudPlan.id)
        assertEquals(1, rawPlanCount(deviceOwner))
        assertEquals(1, rawPlanCount(cloudOwner))
        assertEquals(0, outboxCount(deviceOwner))
        assertTrue(repo.signOut())
        assertNull(repo.state.value.account)
        assertEquals(listOf(devicePlan), repo.state.value.plans)
        assertFalse(repo.state.value.plans.any { it.owner == cloudOwner })
    }

    @Test fun manualLocalBackupRestoreRequiresSameOriginalDeviceOwner() = runBlocking {
        val deviceOwner = requireNotNull(repo.state.value.localPlanOwner)
        val incoming = Engine15IndependentPlanGenerator.generateWithHistory(request(), deviceOwner, emptyList(), "synthetic-local-import", exerciseLibrary = library()).copy(status = "approved")
        val file = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(incoming.encode())).toString()
        assertTrue(repo.restorePlans(file))
        assertEquals(incoming, repo.state.value.plans.single())
        assertEquals(0, outboxCount(deviceOwner))
        val foreign = Engine15IndependentPlanGenerator.generateWithHistory(request(), cloudOwner, emptyList(), "synthetic-foreign-import", exerciseLibrary = library())
        val foreignFile = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(foreign.encode())).toString()
        assertFalse(repo.restorePlans(foreignFile))
        assertEquals(listOf(incoming), repo.state.value.plans)
        assertEquals(0, outboxCount(deviceOwner))
    }

    @Test fun opaqueLocalRecordCannotBeOverwrittenByManualRestore() = runBlocking {
        val deviceOwner = requireNotNull(repo.state.value.localPlanOwner)
        val protected = Engine15IndependentPlanGenerator.generateWithHistory(request(), deviceOwner, emptyList(), "synthetic-opaque-local", exerciseLibrary = library())
        val raw = protected.encode().put("schemaVersion", 999).toString()
        TrainingStore(context, databaseName).use { localStore ->
            localStore.applyRemotePage(
                deviceOwner, "plan",
                listOf(TrainingStore.RemoteRecord(protected.id, raw, null)),
                null, System.currentTimeMillis()
            )
        }
        repo.close()
        open()
        assertTrue(repo.state.value.plans.isEmpty())
        val backup = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(protected.encode())).toString()
        assertFalse(repo.restorePlans(backup))
        assertTrue(repo.state.value.plans.isEmpty())
        assertEquals(0, outboxCount(deviceOwner))
        TrainingStore(context, databaseName).use { localStore ->
            assertEquals(raw, localStore.get(deviceOwner, "plan", protected.id).payload)
        }
    }

    private class FakeGateway : TrainingGateway {
        var owner = "synthetic-m10b-cloud-athlete"
        override fun signIn(email: String, password: String): NativeSession =
            NativeSession(owner, "synthetic-token", "synthetic-refresh", Long.MAX_VALUE)
        override fun refresh(session: NativeSession): NativeSession = session
        override fun verify(session: NativeSession) = Unit
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray = JSONArray()
        override fun signOut(session: NativeSession) = Unit
    }
}
