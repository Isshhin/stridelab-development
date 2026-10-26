package io.stridelab.privatealpha

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Extra CI-only test APK source. Never included in production or the regular test suite. */
class NativeProcessProbeTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()
    private val account = "synthetic-native-process-probe"
    private val title = "Synthetic offline process check"
    @Test fun explicitProcessProbe() = runBlocking {
        check(BuildConfig.DEBUG)
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.FINGERPRINT.contains("emulator") || android.os.Build.HARDWARE == "ranchu") { "Use an isolated emulator only" }
        val context = compose.activity.applicationContext
        val app = context as StrideApplication
        app.repository.state.first { !it.initializing }
        val phase = InstrumentationRegistry.getArguments().getString("processPhase")
        when(phase) {
            "reachability" -> withContext(Dispatchers.IO) {
                // Public key + no bearer: verifies Android HTTPS and RLS boundary, NOT a user login.
                val transport = HttpsBackendTransport()
                val rows = JSONArray(transport.execute("/rest/v1/activities?select=id&limit=1",null,null))
                assertEquals("Anonymous access must not expose athlete history",0,rows.length())
                try { transport.execute("/auth/v1/user",null,null); fail("Anonymous request must not identify an athlete") }
                catch(expected: ServiceFailure) { assertEquals(401,expected.status) }
            }
            "seed" -> {
                assertNull(app.repository.state.value.account)
                TrainingStore(context).use { db ->
                    db.readableDatabase.rawQuery("SELECT DISTINCT account FROM records",null).use { rows ->
                        while(rows.moveToNext()) check(rows.getString(0).startsWith("synthetic-")) { "Refusing to write into an athlete installation" }
                    }
                }
                withFakeRepository(context) { repository ->
                    assertTrue(repository.signIn("synthetic@example.invalid","synthetic-only"))
                    assertEquals(2,repository.state.value.activities.size)
                    val request=PlanRequest(start=java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)),weeks=1)
                    assertTrue(repository.generatePlan(request))
                    var plan=repository.state.value.plans.single()
                    assertTrue(repository.approvePlan(plan.id,plan.revision));plan=repository.state.value.plans.single()
                    assertTrue(repository.recordWorkout(plan.id,plan.revision,plan.sessions.first().id,WorkoutLog(java.time.Instant.now(),minutes=12.0,effort=3,notes="Synthetic offline persistence fixture"),true))
                    plan=repository.state.value.plans.single()
                    assertTrue(repository.saveFeedback(plan.id,plan.revision,"Usability","Synthetic private feedback fixture"))
                }
            }
            "offline" -> {
                waitForConnection(context,false)
                assertEquals(account,app.repository.state.value.account)
                assertEquals(2,app.repository.state.value.activities.size)
                assertNull(app.repository.state.value.activities.first().averageHr)
                assertEquals("synthetic-process-access",SessionVault(context).read(account)!!.accessToken)
                val savedPlan=app.repository.state.value.plans.single()
                assertEquals("approved",savedPlan.status);assertEquals(ENGINE1_VERSION,savedPlan.engineVersion)
                assertEquals(12.0,savedPlan.sessions.first().log!!.minutes!!,0.0);assertEquals(1,savedPlan.feedback.size)
                compose.onNodeWithTag("nav-training").performClick()
                compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("workout-${savedPlan.sessions.first().id}"))
                compose.onNodeWithTag("workout-${savedPlan.sessions.first().id}").performClick()
                compose.onNodeWithTag("screen").performScrollToNode(hasText("Actual duration (minutes), optional"))
                compose.onNodeWithText("Actual duration (minutes), optional").assertExists()
                val planDirectory=File(compose.activity.getExternalFilesDir(null),"native-ui-evidence");planDirectory.mkdirs()
                File(planDirectory,"engine1-workout-after-forced-offline-restart.png").outputStream().use { assertTrue(displayBitmap().compress(Bitmap.CompressFormat.PNG,100,it)) }
                compose.onNodeWithTag("nav-home").performClick()
                compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("activity-synthetic-process-activity"))
                compose.onNodeWithTag("activity-synthetic-process-activity").assertExists()
                compose.onNodeWithTag("offline-status").assertExists()
                val directory = File(compose.activity.getExternalFilesDir(null),"native-ui-evidence"); directory.mkdirs()
                File(directory,"offline-after-process-restart.png").outputStream().use { output ->
                    assertTrue(displayBitmap().compress(Bitmap.CompressFormat.PNG,100,output))
                }
                // No fake token can reach production after the CI script restores networking.
                compose.onNodeWithTag("nav-activities").performClick()
                compose.onNodeWithTag("screen").performScrollToNode(hasText(title))
                compose.onNodeWithText(title).performClick()
                compose.onNodeWithTag("screen").performScrollToNode(hasText("Maximum heart rate"))
                compose.onNodeWithText("Maximum heart rate").assertExists()
                File(directory,"activity-detail-offline.png").outputStream().use { output ->
                    assertTrue(displayBitmap().compress(Bitmap.CompressFormat.PNG,100,output))
                }
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                compose.onNodeWithTag("screen-title").assertTextEquals("Activities")
                compose.onNodeWithTag("screen").performScrollToNode(hasText("Synthetic strength check"))
                compose.onNodeWithText("Synthetic strength check").performClick()
                compose.onNodeWithText("Average pace").assertDoesNotExist()
                compose.onNodeWithText("Moving time").assertDoesNotExist()
                File(directory,"strength-detail-offline.png").outputStream().use { output ->
                    assertTrue(displayBitmap().compress(Bitmap.CompressFormat.PNG,100,output))
                }
                SessionVault(context).clear(account)
            }
            "reconnected" -> {
                waitForConnection(context,true)
                assertTrue(app.repository.state.value.needsSignIn) // fixture token was removed while offline
                withFakeRepository(context) { repository ->
                    assertEquals(2,repository.state.value.activities.size)
                    assertTrue(repository.signIn("synthetic@example.invalid","synthetic-only"))
                    assertTrue(repository.refresh()); assertTrue(repository.refresh())
                    assertEquals(2,repository.state.value.activities.size)
                    assertEquals(2500.0,repository.state.value.activities.first().distanceM!!,0.0)
                    assertNull(repository.state.value.activities.first().maximumHr)
                    val plan=repository.state.value.plans.single();assertEquals(1,plan.sessions.count { it.status=="completed" });assertEquals(1,plan.feedback.size)
                }
                SessionVault(context).clear(account)
            }
            else -> error("Explicit CI processPhase is required")
        }
    }
    private fun displayBitmap(): Bitmap {
        compose.mainClock.advanceTimeBy(600); compose.waitForIdle()
        compose.runOnUiThread { compose.activity.window.decorView.invalidate() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(350)
        return checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    }
    private fun waitForConnection(context: Context, connected: Boolean) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 15000
        while((manager.activeNetwork != null) != connected && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100)
        assertEquals("Expected explicit emulator connectivity state",connected,manager.activeNetwork != null)
    }
    private suspend fun withFakeRepository(context: Context, action: suspend (TrainingRepository) -> Unit) {
        val gateway = object: TrainingGateway {
            override fun signIn(email: String,password: String) = NativeSession(account,"synthetic-process-access","synthetic-process-refresh",2000000000)
            override fun verify(session: NativeSession) = Unit
            override fun refresh(session: NativeSession) = signIn("","")
            override fun activities(session: NativeSession,before: ActivityRecord?): JSONArray {
                if(before != null) return JSONArray()
                return JSONArray().put(JSONObject().put("id","synthetic-process-activity").put("user_id",account)
                    .put("title",title).put("source","manual").put("activity_type","running")
                    .put("started_at","2026-10-26T00:00:00Z").put("distance_m",2500).put("duration_s",900))
                    .put(JSONObject().put("id","synthetic-strength-activity").put("user_id",account)
                        .put("title","Synthetic strength check").put("source","manual").put("activity_type","training")
                        .put("started_at","2026-10-25T00:00:00Z").put("duration_s",1200).put("avg_hr",95).put("max_hr",115)
                        .put("avg_pace_sec_per_km",123)) // source field is retained, but pace must not be shown for strength
            }
            override fun intervals(session: NativeSession,action: String) = JSONObject().put("configured",false).put("syncStatus","disconnected")
            override fun signOut(session: NativeSession) = Unit
        }
        val repository = TrainingRepository(context,gateway)
        try { repository.state.first { !it.initializing }; action(repository) } finally { repository.close() }
    }
}
