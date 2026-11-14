package io.stridelab.privatealpha

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Two invocations separated by an externally verified force-stop; synthetic data only. */
class Engine15ProcessRestartTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val args = InstrumentationRegistry.getArguments()
    private val runId get() = requireNotNull(args.getString("fixture_id"))
        .also { require(it.matches(Regex("[a-z0-9]{1,30}"))) }
    private val owner get() = "synthetic-restart-$runId"
    private val database get() = "synthetic-restart-$runId.db"
    private val preferences get() = "synthetic-restart-$runId"
    private val evidence get() = context.getSharedPreferences("synthetic-restart-evidence-$runId", 0)

    @Before fun requireExternalRestartHarness() {
        assumeTrue("Run through tools/run-engine15-process-restart.ps1", args.getString("fixture_id") != null)
    }

    private fun open(offline: Boolean): TrainingRepository = TrainingRepository(
        context, Gateway(owner, offline), database, preferences
    ).also { repo -> runBlocking { withTimeout(20_000) { repo.state.first { !it.initializing } } } }

    @Test fun prepare() = runBlocking {
        assertNull("Use a fresh synthetic fixture ID", evidence.getString("snapshot", null))
        val scenario = requireNotNull(args.getString("scenario"))
        require(scenario in setOf("draft", "approved", "equal", "completed", "feedback", "accepted", "dismissed"))
        val repo = open(false)
        try {
            assertTrue(repo.signIn("restart@example.invalid", "fixture"))
            val request = PlanRequest(goal="Improve 5K", policyChoice="engine15",
                start=LocalDate.now().plusDays(1), weeks=4, days=(1..7).toSet(),
                runDays=3, longDay=6, experience=1, weeklyKm=20.0, longestKm=8.0,
                targetWeeklyKm=if (scenario == "equal") 20.0 else 25.0, qualityCount=0)
            val recordedDay = LocalDate.now().minusDays(1)
            val futureDay = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            val feedbackEngine = object : PlanGenerationEngine {
                override val version = ENGINE15_VERSION
                override val policyId = ENGINE15_POLICY
                override fun generate(request: PlanRequest, owner: String, id: String, now: Instant) =
                    PlanSnapshot(id, owner, version, policyId, now, request, listOf(
                        PlannedSession("observed-run", recordedDay, "run", "Recorded run", 40, 6.0, "easy"),
                        PlannedSession("future-run", futureDay, "run", "Future easy run", 60, 8.0, "easy")
                    ), listOf("Synthetic fixture for process-restart verification."))
            }
            assertTrue(repo.generatePlan(request, if (scenario in setOf("draft", "approved", "equal")) Engine15 else feedbackEngine))
            var plan = repo.state.value.plans.single()
            assertEquals(request.targetWeeklyKm, plan.request.targetWeeklyKm)
            if (scenario != "draft") {
                assertTrue(repo.approvePlan(plan.id, plan.revision))
                plan = repo.state.value.plans.single()
            } else assertEquals("draft", plan.status)
            if (scenario in setOf("completed", "feedback", "accepted", "dismissed")) {
                assertTrue(repo.recordWorkout(plan.id, plan.revision, "observed-run",
                    WorkoutLog(Instant.now(), minutes=35.0, effort=9, performedOn=recordedDay,
                        notes="Synthetic high effort"), true))
                plan = repo.state.value.plans.single()
                assertEquals("completed", plan.sessions.first().status)
            }
            if (scenario == "feedback") {
                assertTrue(repo.saveFeedback(plan.id, plan.revision, "Usability", "Synthetic feedback"))
                plan = repo.state.value.plans.single()
                assertEquals(1, plan.feedback.size)
            }
            if (scenario in setOf("accepted", "dismissed")) {
                val proposal = repo.previewEngine15Adjustments(plan.id, LocalDate.now()).single()
                assertTrue(repo.stageEngine15Adjustment(plan.id, plan.revision, proposal, LocalDate.now()))
                plan = repo.state.value.plans.single()
                if (scenario == "accepted") assertTrue(repo.acceptSuggestion(plan.id, plan.revision, "future-run"))
                else assertTrue(repo.dismissStagedEngine15Adjustment(plan.id, plan.revision, "future-run"))
                plan = repo.state.value.plans.single()
                if (scenario == "accepted") assertTrue(plan.sessions.last().suggestion!!.accepted)
                else assertNull(plan.sessions.last().suggestion)
            }
            assertEquals(ENGINE15_POLICY, plan.policyId)
            assertTrue(evidence.edit().putString("snapshot", plan.encode().toString())
                .putInt("pid", Process.myPid()).commit())
        } finally { repo.close() }
    }

    @Test fun verifyAfterRestart() {
        val original = requireNotNull(evidence.getString("snapshot", null))
        assertNotEquals("The writer process must have exited", evidence.getInt("pid", -1), Process.myPid())
        val expected = PlanSnapshot.decode(JSONObject(original), owner)
        val repo = open(true)
        try {
            val actual = repo.state.value.plans.single()
            assertEquals(expected, actual)
            assertEquals(ENGINE15_VERSION, actual.engineVersion)
            assertEquals(ENGINE15_POLICY, actual.policyId)
            assertEquals(actual.sessions.size, actual.sessions.map { it.id }.distinct().size)
        } finally { repo.close() }
        val sibling = TrainingRepository(context, Gateway("$owner-sibling", false), database, "$preferences-sibling")
        try {
            runBlocking {
                withTimeout(20_000) { sibling.state.first { !it.initializing } }
                assertTrue(sibling.signIn("other@example.invalid", "fixture"))
                assertTrue(sibling.state.value.plans.isEmpty())
            }
        } finally { sibling.close() }
    }

    private class Gateway(private val owner: String, private val offline: Boolean) : TrainingGateway {
        private fun checkOnline() { if (offline) throw java.io.IOException("synthetic offline") }
        override fun signIn(email: String, password: String): NativeSession {
            checkOnline(); return NativeSession(owner, "fixture-token", "fixture-refresh", Long.MAX_VALUE)
        }
        override fun refresh(session: NativeSession): NativeSession { checkOnline(); return session }
        override fun verify(session: NativeSession) = checkOnline()
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray { checkOnline(); return JSONArray() }
        override fun signOut(session: NativeSession) = checkOnline()
    }
}
