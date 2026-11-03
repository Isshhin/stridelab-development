package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Batch D evidence: real account-scoped SQLite/outbox + repository operations on a disposable
 * Android emulator with a synthetic transport. Never connects to an athlete backend.
 */
class Engine15FeedbackRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = "synthetic-engine15-feedback.db"
    private val preferences = "synthetic-engine15-feedback"
    private val owner = "synthetic-engine15-owner"
    private val alternateOwner = "synthetic-engine15-other"
    private val today = LocalDate.now()
    private val sourceDate = today.minusDays(1)
    private val targetDate = today.plusDays(1)
    private val transport = FixtureGateway()
    private lateinit var repository: TrainingRepository

    private fun open() = runBlocking {
        repository = TrainingRepository(context, transport, database, preferences)
        repository.state.first { !it.initializing }
    }

    @Before fun setUp() {
        context.deleteDatabase(database)
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(owner)
        SessionVault(context).clear(alternateOwner)
        transport.account = owner
        transport.offline = false
        open()
    }

    @After fun tearDown() {
        repository.close()
        context.deleteDatabase(database)
        context.getSharedPreferences(preferences, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(owner)
        SessionVault(context).clear(alternateOwner)
    }

    private val request get() = PlanRequest(
        goal = "Improve 5K", policyChoice = "engine15",
        start = today.minusDays(8), weeks = 4, days = (1..7).toSet(),
        runDays = 2, longDay = 6, experience = 2, weeklyKm = 30.0,
        longestKm = 10.0, minutes = 90, qualityCount = 0
    )

    // Isolates repository approval/storage behavior from Engine 1.0's calendar choices.
    private val fixtureEngine = object : PlanGenerationEngine {
        override val version = ENGINE15_VERSION
        override val policyId = ENGINE15_POLICY
        override fun generate(
            request: PlanRequest, owner: String, id: String, now: Instant
        ): PlanSnapshot = PlanSnapshot(
            "engine15-feedback-fixture", owner, version, policyId, now, request,
            listOf(
                PlannedSession("observed-run", sourceDate, "run", "Recorded run",
                    40, 6.0, "easy"),
                PlannedSession("future-run", targetDate, "run", "Future easy run",
                    60, 8.0, "easy")
            ), listOf("Synthetic data for offline repository validation; no real training advice.")
        )
    }

    private suspend fun confirmedSource(): PlanSnapshot {
        assertTrue(repository.signIn("engine15@example.invalid", "fixture"))
        assertTrue(repository.generatePlan(request, fixtureEngine))
        var plan = repository.state.value.plans.single()
        assertEquals(ENGINE15_POLICY, plan.policyId)
        assertTrue(repository.approvePlan(plan.id, plan.revision))
        plan = repository.state.value.plans.single()
        assertTrue(repository.recordWorkout(
            plan.id, plan.revision, "observed-run",
            WorkoutLog(
                Instant.now(), minutes = 35.0, effort = 9,
                performedOn = sourceDate, notes = "High subjective effort"
            ), true
        ))
        return repository.state.value.plans.single()
    }

    @Test fun previewIsPureStageIsRevisionCheckedAndPendingSuggestionSurvivesOfflineReopen() = runBlocking {
        val completed = confirmedSource()
        val candidates = repository.previewEngine15Adjustments(completed.id, today)
        assertEquals(1, candidates.size)
        val proposal = candidates.single()
        assertEquals(45, proposal.suggestedMinutes)
        assertNull(completed.sessions.single { it.id == "future-run" }.suggestion)
        assertEquals(completed, repository.state.value.plans.single())

        assertTrue(repository.stageEngine15Adjustment(completed.id, completed.revision, proposal, today))
        val staged = repository.state.value.plans.single()
        assertEquals(completed.revision + 1, staged.revision)
        assertEquals(60, staged.sessions.last().minutes)
        assertEquals(60, staged.sessions.last().effectiveMinutes)
        assertFalse(staged.sessions.last().suggestion!!.accepted)
        assertEquals(completed.sessions.first(), staged.sessions.first())

        assertFalse(repository.stageEngine15Adjustment(completed.id, completed.revision, proposal, today))
        assertEquals(staged, repository.state.value.plans.single())
        repository.close()
        transport.offline = true
        open()
        val reopened = repository.state.value.plans.single()
        assertEquals(staged, reopened)
        assertEquals(1, reopened.sessions.count { it.suggestion != null })
        assertEquals(0, repository.previewEngine15Adjustments(reopened.id, today).size)
    }

    @Test fun explicitAcceptanceAndDismissalRemainDurableWithoutConnectivity() = runBlocking {
        val recorded = confirmedSource()
        val proposal = repository.previewEngine15Adjustments(recorded.id, today).single()
        assertTrue(repository.stageEngine15Adjustment(recorded.id, recorded.revision, proposal, today))
        var plan = repository.state.value.plans.single()
        transport.offline = true
        assertTrue(repository.acceptSuggestion(plan.id, plan.revision, "future-run"))
        plan = repository.state.value.plans.single()
        assertEquals(45, plan.sessions.last().effectiveMinutes)
        assertEquals(60, plan.sessions.last().minutes)
        assertEquals(8.0, plan.sessions.last().distanceKm!!, 0.0)
        assertEquals("planned", plan.sessions.last().status)
        assertTrue(plan.sessions.last().suggestion!!.accepted)
        assertEquals(recorded.sessions.first(), plan.sessions.first())
        assertFalse(repository.dismissStagedEngine15Adjustment(plan.id, plan.revision, "future-run"))
        repository.close()
        open()
        assertEquals(plan, repository.state.value.plans.single())
        assertEquals(plan, PlanSnapshot.decode(plan.encode(), owner))
        assertTrue(repository.previewEngine15Adjustments(plan.id, today).isEmpty())
    }

    @Test fun dismissalRestoresOriginalSessionAndAuditsSourceEvidenceOffline() = runBlocking {
        val recorded = confirmedSource()
        val proposal = repository.previewEngine15Adjustments(recorded.id, today).single()
        assertTrue(repository.stageEngine15Adjustment(recorded.id, recorded.revision, proposal, today))
        var plan = repository.state.value.plans.single()
        transport.offline = true
        assertTrue(repository.dismissStagedEngine15Adjustment(plan.id, plan.revision, "future-run"))
        plan = repository.state.value.plans.single()
        assertNull(plan.sessions.last().suggestion)
        assertEquals(60, plan.sessions.last().effectiveMinutes)
        assertEquals(60, plan.sessions.last().minutes)
        assertEquals(1, plan.feedback.size)
        assertTrue(plan.feedback.single().text.contains("Evidence: session observed-run, log revision 1"))
        assertEquals(0, repository.previewEngine15Adjustments(plan.id, today).size)
        repository.close()
        open()
        assertEquals(plan, repository.state.value.plans.single())
        assertEquals(plan, PlanSnapshot.decode(plan.encode(), owner))
    }

    @Test fun accountSwitchCannotStageOrRestoreOtherOwnersPlanAndOriginalSurvives() = runBlocking {
        val completed = confirmedSource()
        val proposal = repository.previewEngine15Adjustments(completed.id, today).single()
        assertTrue(repository.stageEngine15Adjustment(completed.id, completed.revision, proposal, today))
        val saved = repository.state.value.plans.single()
        val backup = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(saved.encode())).toString()

        assertTrue(repository.signOut())
        transport.account = alternateOwner
        assertTrue(repository.signIn("other@example.invalid", "fixture"))
        assertTrue(repository.state.value.plans.isEmpty())
        assertTrue(repository.previewEngine15Adjustments(saved.id, today).isEmpty())
        assertFalse(repository.stageEngine15Adjustment(saved.id, saved.revision, proposal, today))
        assertFalse(repository.restorePlans(backup))
        assertTrue(repository.state.value.plans.isEmpty())

        assertTrue(repository.signOut())
        transport.account = owner
        assertTrue(repository.signIn("engine15@example.invalid", "fixture"))
        transport.offline = true
        repository.close()
        open()
        val reopened = repository.state.value.plans.single()
        assertEquals(saved, reopened)
        assertFalse(reopened.sessions.last().suggestion!!.accepted)
        assertEquals(60, reopened.sessions.last().effectiveMinutes)
    }

    @Test fun duplicateSourceBackupRestorationIsAtomicAndOfflineCacheRemainsUntouched() = runBlocking {
        val initial = confirmedSource()
        val src = initial.sessions.first()
        val safe = initial.copy(
            id = "additional-safe", revision = 1,
            sessions = listOf(src.copy(id = "imported-safe", linkedActivityId = "duplicate-activity"))
        )
        val collision = initial.copy(
            id = "additional-conflict", revision = 1,
            sessions = listOf(src.copy(id = "imported-duplicate", linkedActivityId = "duplicate-activity"))
        )
        val invalidBackup = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(safe.encode()).put(collision.encode())).toString()
        transport.offline = true
        assertFalse(repository.restorePlans(invalidBackup))
        assertEquals(listOf(initial), repository.state.value.plans)
        repository.close()
        open()
        assertEquals(listOf(initial), repository.state.value.plans)

        val safeBackup = JSONObject().put("schemaVersion", 1)
            .put("plans", JSONArray().put(safe.encode())).toString()
        assertTrue(repository.restorePlans(safeBackup))
        assertEquals(2, repository.state.value.plans.size)
        assertEquals(initial, repository.state.value.plans.single { it.id == initial.id })
        repository.close()
        open()
        assertEquals(2, repository.state.value.plans.size)
        assertEquals(safe, repository.state.value.plans.single { it.id == safe.id })
        assertEquals(initial, repository.state.value.plans.single { it.id == initial.id })
    }

    @Test fun missedReportRequiresExplicitReviewAndAcceptedTimeOnlyTargetReopensOffline() = runBlocking {
        assertTrue(repository.signIn("engine15@example.invalid", "fixture"))
        assertTrue(repository.generatePlan(request, fixtureEngine))
        var plan = repository.state.value.plans.single()
        assertTrue(repository.approvePlan(plan.id, plan.revision))
        plan = repository.state.value.plans.single()

        assertTrue(repository.markMissed(plan.id, plan.revision, "observed-run", "Fatigue", false))
        plan = repository.state.value.plans.single()
        assertEquals("missed", plan.sessions.first().status)
        assertNull("No legacy auto-stage on Engine 1.5", plan.sessions.last().suggestion)
        assertEquals(60, plan.sessions.last().effectiveMinutes)
        val proposal = repository.previewEngine15Adjustments(plan.id, today).single()
        assertEquals(42, proposal.suggestedMinutes)

        transport.offline = true
        assertTrue(repository.stageEngine15Adjustment(plan.id, plan.revision, proposal, today))
        plan = repository.state.value.plans.single()
        val pending = Engine15SessionPresentation.forSession(plan, "future-run")
        assertEquals(Engine15SessionPresentation.State.PENDING_REVIEW, pending.state)
        assertEquals(60, pending.currentMinutes)
        assertEquals(8.0, pending.currentDistanceTargetKm!!, 0.0)

        assertTrue(repository.acceptSuggestion(plan.id, plan.revision, "future-run"))
        plan = repository.state.value.plans.single()
        val approved = Engine15SessionPresentation.forSession(plan, "future-run")
        assertEquals(Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY, approved.state)
        assertEquals(42, approved.currentMinutes)
        assertNull(approved.currentDistanceTargetKm)
        assertEquals(8.0, approved.originalDistanceKm!!, 0.0)
        assertEquals("missed", plan.sessions.first().status)
        assertEquals(60, plan.sessions.last().minutes)
        assertEquals(8.0, plan.sessions.last().distanceKm!!, 0.0)
        repository.close()
        open()
        assertEquals(plan, repository.state.value.plans.single())
        assertEquals(approved,
            Engine15SessionPresentation.forSession(repository.state.value.plans.single(), "future-run"))
    }

    @Test fun changedSourceEvidenceCannotBeAcceptedAndPendingStateSurvivesOffline() = runBlocking {
        val recorded = confirmedSource()
        val proposal = repository.previewEngine15Adjustments(recorded.id, today).single()
        assertTrue(repository.stageEngine15Adjustment(recorded.id, recorded.revision, proposal, today))
        var plan = repository.state.value.plans.single()
        val stagedRevision = plan.revision
        transport.offline = true

        // Simulate a reviewed correction to recorded subjective effort after staging.
        assertTrue(repository.changePlan(plan.id, plan.revision) { current ->
            current.copy(sessions = current.sessions.map { session ->
                if (session.id == "observed-run")
                    session.copy(
                        log = session.log!!.copy(effort = 3),
                        logRevision = session.logRevision + 1
                    )
                else session
            })
        })
        plan = repository.state.value.plans.single()
        assertEquals(stagedRevision + 1, plan.revision)
        assertFalse(repository.acceptSuggestion(plan.id, plan.revision, "future-run"))
        assertFalse(plan.sessions.last().suggestion!!.accepted)
        assertEquals(60, repository.state.value.plans.single().sessions.last().effectiveMinutes)
        repository.close()
        open()
        assertEquals(plan, repository.state.value.plans.single())
        assertEquals(60, repository.state.value.plans.single().sessions.last().effectiveMinutes)
    }

    private inner class FixtureGateway : TrainingGateway {
        var account = owner
        var offline = false
        private fun online() {
            if (offline) throw java.io.IOException("Synthetic offline fixture")
        }
        override fun signIn(email: String, password: String): NativeSession {
            online()
            return NativeSession(account, "fake-token", "fake-refresh", Long.MAX_VALUE)
        }
        override fun refresh(session: NativeSession) = session
        override fun verify(session: NativeSession) { online() }
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
            online()
            return JSONArray()
        }
        override fun intervals(session: NativeSession, action: String): JSONObject {
            online()
            return JSONObject().put("configured", false)
        }
        override fun signOut(session: NativeSession) { online() }
    }
}
