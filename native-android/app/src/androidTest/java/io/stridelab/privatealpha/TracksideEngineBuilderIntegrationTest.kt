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

    @Test fun builderCreatesRealPrescriptionsAndTrainingReopensTheApprovedPlanOffline() {
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
                            NativeActions({}, {}, {}, {}, { _, _ -> }, planning = actions))
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
            next() // current ability
            next() // weekly setup
            next() // dates
            next() // availability
            compose.onNodeWithText("Generate My Plan").assertExists()
            next()
            compose.waitUntil(15_000) { repo.state.value.plans.isNotEmpty() }
            val draft = repo.state.value.plans.single()
            assertEquals("draft", draft.status)
            assertEquals(ENGINE15_POLICY, draft.policyId)
            assertEquals(ENGINE15_VERSION, draft.engineVersion)
            assertEquals("Improve 10K", draft.request.goal)
            assertEquals(12.0, draft.request.weeklyKm, 0.0)
            assertEquals(null, draft.request.targetWeeklyKm)
            assertEquals(12, draft.sessions.size)
            val expectedEngine15 = Engine15.generate(draft.request,
                fixture, draft.id, draft.createdAt)
            assertEquals(expectedEngine15.sessions, draft.sessions)
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
