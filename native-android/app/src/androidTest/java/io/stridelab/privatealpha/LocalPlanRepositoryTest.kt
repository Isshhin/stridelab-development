package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test fun savedGearPersistsUnderTheCurrentOwnerWithoutEditingApprovedPlans() = runBlocking {
        val guest = requireNotNull(repo.state.value.localPlanOwner)
        assertTrue(repo.generatePlan(request()))
        val draft = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(draft.id, draft.revision))
        val approved = repo.state.value.plans.single().encode().toString()
        assertEquals(mapOf("db" to 1), builderEquipmentFor(repo.state.value, request()))
        assertTrue(repo.saveBuilderEquipment(guest, emptyMap()))
        repo.close()
        open()
        assertEquals(emptyMap<String, Int>(), repo.state.value.savedEquipment)
        assertEquals(approved, repo.state.value.plans.single().encode().toString())
        assertTrue(repo.signIn("synthetic@example.invalid", "fixture"))
        assertNull(repo.state.value.savedEquipment)
        assertTrue(repo.saveBuilderEquipment(cloudOwner, mapOf("bb" to 1)))
        assertEquals(0, outboxCount(cloudOwner))
        assertTrue(repo.signOut())
        assertEquals(emptyMap<String, Int>(), repo.state.value.savedEquipment)
        assertTrue(repo.signIn("synthetic@example.invalid", "fixture"))
        assertEquals(mapOf("bb" to 1), repo.state.value.savedEquipment)
        assertEquals(0, outboxCount(guest))
    }

    @Test fun sixTrackActivationRollsBackOnCollisionAndRestoresWithIdenticalRunningFit() = runBlocking {
        val owner = requireNotNull(repo.state.value.localPlanOwner)
        val start = LocalDate.now().plusWeeks(1)
        val request = PlanRequest(
            goal = "General fitness", start = start, weeks = 2, days = (1..7).toSet(),
            runDays = 1, longDay = 7, qualityCount = 0, qualityDays = emptySet(),
            weeklyKm = 8.0, targetWeeklyKm = 8.0, longestKm = 10.0, experience = 1,
            timeBudgetEnabled = false, strengthDays = 1, calDays = 1, conditioningDays = 1,
            disciplineGoals = mapOf("str" to "Upper Body & Posture", "cal" to "Core / Anti-Rotation",
                "cond" to "Steady aerobic cross-conditioning")
        )
        val requests = HybridPlanBuilder.split(request, setOf("plyo", "iso"))
        assertTrue(repo.generateHybridPlan(requests))
        val preview = requireNotNull(repo.state.value.hybridPlanPreview)
        assertEquals(6, preview.drafts.size)
        val colliding = preview.drafts.last().second
        val testStore = TrainingStore(context, databaseName)
        try {
            testStore.applyRemotePage(owner, "plan",
                listOf(TrainingStore.RemoteRecord(colliding.id, colliding.encode().toString(), null)), null, 0L)
            assertFalse(repo.approveHybridPlan(preview.id))
            assertEquals(1, rawPlanCount(owner))
            assertTrue(repo.state.value.plans.isEmpty())
            testStore.deleteRecord(owner, "plan", colliding.id)
        } finally { testStore.close() }
        assertTrue(repo.approveHybridPlan(preview.id))
        assertEquals(6, rawPlanCount(owner))
        assertEquals(0, outboxCount(owner))
        val snapshots = repo.state.value.plans.associateBy { it.id }
        val backup = PlanBackup.encode(snapshots.values.toList(), emptyList(), owner)
        val savedRun = snapshots.values.single { it.request.runDays == 1 }.sessions.first()
        val stamp = Instant.parse("2026-11-23T00:00:00Z")
        val expected = GarminFitWorkoutEncoder.encode(savedRun.title, savedRun.runningPrescription, stamp)
        repo.close()
        context.deleteDatabase(databaseName)
        open()
        assertTrue(repo.restorePlans(backup))
        val restored = repo.state.value.plans.associateBy { it.id }
        assertEquals(snapshots, restored)
        restored.values.forEach { Engine15IndependentPlanGenerator.validateSaved(it) }
        val restoredRun = restored.values.single { it.request.runDays == 1 }.sessions.first()
        assertArrayEquals(expected, GarminFitWorkoutEncoder.encode(restoredRun.title, restoredRun.runningPrescription, stamp))
        assertEquals(0, outboxCount(owner))
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
        assertEquals(saved.outcome, recoveredSave.outcome)
        assertEquals(PrescriptionOutcome.PARTIALLY_PERFORMED, repo.state.value.prescriptionOutcome(plan.id, session.id))
        assertEquals(committed, repo.state.value.plans.single())
        assertEquals(1, rawPlanCount(plan.owner))
        assertEquals(0, outboxCount(plan.owner))
    }

    @Test fun concurrentRunnerSupportPreviewAndAdjustmentPersistAndRollback() = runBlocking {
        val today = LocalDate.now()
        val running = PlanRequest(
            goal = "Improve 10K", start = today.plusDays(4), weeks = 8, experience = 1,
            days = listOf(1L, 3L, 5L).map { today.plusDays(it).dayOfWeek.value }.toSet(),
            runDays = 3, longDay = today.plusDays(3).dayOfWeek.value, qualityCount = 0,
            weeklyKm = 30.0, longestKm = 11.0, targetWeeklyKm = 34.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE", timeBudgetEnabled = false
        )
        assertTrue(repo.generatePlan(running))
        val runningPreview = requireNotNull(repo.state.value.planPreview)
        assertTrue(repo.approvePlan(runningPreview.id, runningPreview.revision))
        val approvedRun = repo.state.value.plans.single()
        val exportedRun = approvedRun.sessions.first().runningPrescription
        val fitAt = Instant.parse("2026-11-23T12:00:00Z")
        val fit = GarminFitWorkoutEncoder.encode("Run", exportedRun, fitAt)

        val support = PlanRequest(goal = "Strength for Runners", start = today, weeks = 8,
            days = setOf(today.dayOfWeek.value), runDays = 0, strengthDays = 1,
            timeBudgetEnabled = false)
        assertTrue(repo.generatePlan(support))
        val review = requireNotNull(repo.state.value.planPreviewSupportReview)
        assertFalse(review.blocked)
        assertTrue(review.candidate.sessions.any { it.runnerSupportDose == RunnerSupportDose.LIGHT })
        assertTrue(repo.approvePlan(review.candidate.id, review.candidate.revision))
        val plan = repo.state.value.plans.single { it.id == review.candidate.id }
        assertEquals(review.candidate.sessions, plan.sessions)
        val workout = plan.sessions.first()
        var execution = requireNotNull(repo.executeWorkout(plan.id, workout.id, null, null))
        repeat(execution.sets.size) {
            val target = execution.current.target
            execution = requireNotNull(repo.executeWorkout(plan.id, workout.id, execution.revision,
                PlayerEvent.CompleteSet(SetActual(target.reps, seconds = target.seconds, rir = 4))))
            execution = requireNotNull(repo.executeWorkout(plan.id, workout.id, execution.revision, PlayerEvent.SkipRest))
        }
        assertEquals(PlayerPhase.Reviewing, execution.phase)
        val saved = requireNotNull(repo.executeWorkout(plan.id, workout.id, execution.revision,
            PlayerEvent.FinalizeSession(Instant.now(), 6, "", today)))
        assertTrue(saved.receipt)
        val proposal = requireNotNull(repo.state.value.pendingAdaptationProposals[plan.id])
        assertEquals(AdaptationType.RUNNER_SUPPORT_DOSE, proposal.type)
        val before = repo.state.value.plans.single { it.id == plan.id }
        val applied = repo.approveAdaptationProposal(plan.id, proposal.proposalId)
        assertTrue(repo.state.value.message, applied)
        repo.close()
        open()
        withTimeout(10_000) { repo.state.first { it.adaptationRevisions[plan.id].orEmpty().isNotEmpty() } }
        val reloaded = repo.state.value.plans.single { it.id == plan.id }
        assertTrue(reloaded.sessions.any { it.runnerSupportDose == RunnerSupportDose.PROGRESS })
        Engine15IndependentPlanGenerator.validateSaved(reloaded)
        assertTrue(repo.revertLastAdaptation(plan.id))
        assertEquals(before.sessions, repo.state.value.plans.single { it.id == plan.id }.sessions)
        assertEquals(approvedRun, repo.state.value.plans.single { it.id == approvedRun.id })
        assertArrayEquals(fit, GarminFitWorkoutEncoder.encode("Run",
            repo.state.value.plans.single { it.id == approvedRun.id }.sessions.first().runningPrescription, fitAt))
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
        val protected = Engine15IndependentPlanGenerator.generateWithHistory(request(), deviceOwner, emptyList(), "synthetic-opaque-local", exerciseLibrary = library()).copy(status = "approved")
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
