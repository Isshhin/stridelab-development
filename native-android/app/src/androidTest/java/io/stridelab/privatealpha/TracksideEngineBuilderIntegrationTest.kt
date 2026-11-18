package io.stridelab.privatealpha

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TracksideEngineBuilderIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun builderCreatesRealPrescriptionsAndTrainingReopensTheApprovedPlanOffline() = exerciseBuilder(null)

    @Test fun explicitTargetSurvivesApprovalAndOfflineReopen() = exerciseBuilder(20.0)

    private fun exerciseBuilder(targetKm: Double?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = "trackside-engine-builder"
        val gateway = LocalGateway(fixture)
        fun clear() {
            context.deleteDatabase(fixture)
            context.getSharedPreferences(fixture, 0).edit().clear().commit()
            SessionVault(context).clear(fixture)
        }
        fun open() = TrainingRepository(context, gateway, fixture, fixture).also { repository ->
            runBlocking { repository.state.first { !it.initializing } }
        }
        clear()
        var repo = open()
        fun showApp() {
            val repository = repo
            compose.runOnUiThread {
                compose.activity.setContent {
                    val scope = rememberCoroutineScope()
                    val actions = PlanActions(
                        generate = { scope.launch { repository.generatePlan(it) } },
                        approve = { id, revision -> scope.launch { repository.approvePlan(id, revision) } }
                    )
                    StrideTheme {
                        NativeStrideContent(repository.state.collectAsState().value, false,
                            NativeActions({}, { _, _ -> }, planning = actions))
                    }
                }
            }
        }
        try {
            assertTrue(runBlocking { repo.signIn("fixture@example.invalid", "fixture") })
            showApp()
            compose.onNodeWithTag("nav-training").performClick()
            compose.onNodeWithText("Create training plan").performScrollTo().performClick()
            fun next() = compose.onNodeWithTag("builder-figma-continue").performClick()
            next() // running
            next() // race
            compose.onNodeWithTag("builder-distance-10K").performClick()
            next()
            compose.onNodeWithTag("builder-experience-1").performClick()
            compose.onNodeWithTag("builder-ability-weekly-slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }
            compose.onNodeWithTag("builder-ability-weekly-slider").performScrollTo()
            compose.onNodeWithTag("builder-ability-longest-slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
            if (targetKm != null) {
                compose.onNodeWithTag("builder-advanced-settings").performScrollTo().performClick()
                compose.onNodeWithTag("builder-target-mileage-slider")
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(targetKm.toFloat()) }
                next() // save advanced preferences
            }
            next() // current ability
            next() // weekly setup
            // Plan Dates now includes the final capacity check and actions.
            compose.onNodeWithTag("builder-inline-schedule").assertExists()
            compose.onNodeWithTag("builder-advanced-scheduling")
                .performScrollTo().performClick()
            compose.onNodeWithTag("available-day-6").assertExists()
            compose.onNodeWithTag("builder-generate-draft-note").assertExists()
            // Research reviews remain available without switching the
            // production coach to a non-executable research route.
            compose.onNodeWithTag("builder-availability-research-toggle")
                .performScrollTo().performClick()
            compose.onNodeWithTag("builder-experimental-research-panel").assertExists()
            compose.onNodeWithTag("builder-availability-research-toggle")
                .performScrollTo().performClick()
            compose.onNodeWithText("Generate My Plan").assertExists()
            next() // generate the saved draft; approval is still separate
            compose.waitUntil(15_000) { repo.state.value.plans.isNotEmpty() }
            val draft = repo.state.value.plans.single()
            assertEquals("draft", draft.status)
            val expectedPolicy = if (BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
                ENGINE15_INDEPENDENT_RUNNING_POLICY else ENGINE15_POLICY
            assertEquals(expectedPolicy, draft.policyId)
            assertEquals(ENGINE15_VERSION, draft.engineVersion)
            assertEquals("Improve 10K", draft.request.goal)
            assertEquals(12.0, draft.request.weeklyKm, 0.0)
            assertEquals(targetKm, draft.request.targetWeeklyKm)
            assertEquals(12, draft.sessions.size)
            val expectedSessions = if (BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE) {
                val decision = Engine15IndependentPlanGenerator.preview(
                    draft.request, fixture, draft.id, draft.createdAt, existingPlans = emptyList()
                )
                assertTrue("Expected an independent running candidate, got $decision",
                    decision is Engine15IndependentPlanDecision.Candidate)
                (decision as Engine15IndependentPlanDecision.Candidate).plan.sessions
            } else Engine15.generate(draft.request, fixture, draft.id, draft.createdAt).sessions
            assertEquals(expectedSessions, draft.sessions)
            val workout = draft.sessions.first()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("builder-preview-session-" + workout.id)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("builder-preview-session-" + workout.id)
                .assertTextContains(workout.effectiveTitle)
            next()
            compose.waitUntil(10_000) { repo.state.value.plans.single().status == "approved" }
            assertTrue(repo.state.value.plans.single().sessions.any { it.status == "planned" })

            compose.runOnUiThread { compose.activity.setContent {} }
            repo.close()
            gateway.offline = true
            repo = open()
            val restored = repo.state.value.plans.single()
            assertEquals("approved", restored.status)
            assertEquals(targetKm, restored.request.targetWeeklyKm)
            assertEquals(draft.sessions, restored.sessions)
            assertTrue(restored.sessions.any { it.id == workout.id && it.status == "planned" })
        } finally {
            repo.close()
            clear()
        }
    }

    private class LocalGateway(val owner: String) : TrainingGateway {
        var offline = false
        private fun connected() { if (offline) throw java.io.IOException("Offline fixture") }
        override fun signIn(email: String, password: String): NativeSession {
            connected()
            return NativeSession(owner, "fixture-token", "fixture-refresh", Long.MAX_VALUE)
        }
        override fun refresh(session: NativeSession): NativeSession { connected(); return session }
        override fun verify(session: NativeSession) = connected()
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
            connected()
            return JSONArray()
        }
        override fun signOut(session: NativeSession) = connected()
    }
}
