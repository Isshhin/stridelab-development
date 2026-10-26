package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.IOException

/** Real SQLite/Keystore with an isolated fake network; no athlete backend is accessed. */
@RunWith(AndroidJUnit4::class)
class TrainingRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val account = "synthetic-native-repository-test"
    private val database = "synthetic-native-repository-test.db"
    private val prefs = "synthetic-native-repository-test"
    private lateinit var repo: TrainingRepository
    private lateinit var remote: FakeGateway
    private fun row(id: String = "activity-1", owner: String = account): JSONObject = JSONObject()
        .put("id", id).put("user_id", owner).put("source", "intervals").put("title", "Synthetic fixture")
        .put("activity_type", "run").put("started_at", "2026-10-25T12:00:00Z").put("distance_m", 5000)
    private fun open() = runBlocking {
        repo = TrainingRepository(context, remote, database, prefs)
        repo.state.first { !it.initializing }
    }
    @Before fun setup() {
        context.deleteDatabase(database); context.getSharedPreferences(prefs, 0).edit().clear().commit()
        SessionVault(context).clear(account)
        remote = FakeGateway(account); open()
    }
    @After fun cleanup() {
        repo.close(); SessionVault(context).clear(account)
        context.deleteDatabase(database); context.getSharedPreferences(prefs, 0).edit().clear().commit()
    }
    @Test fun cachedHistoryAndSessionSurviveRepositoryRecreationOffline() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertEquals(1, repo.state.value.activities.size)
        assertNull(repo.state.value.activities.single().averageHr)
        repo.close(); remote.offline = true; open()
        assertEquals(account, repo.state.value.account)
        assertEquals("activity-1", repo.state.value.activities.single().id)
        assertFalse(repo.refresh())
        assertEquals(1, repo.state.value.activities.size)
    }
    @Test fun repeatedRefreshDeduplicatesAndRetainsMissingMetrics() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertTrue(repo.refresh()); assertTrue(repo.refresh())
        assertEquals(1, repo.state.value.activities.size)
        assertNull(repo.state.value.activities.single().recordedPace)
    }
    @Test fun partialImportPersistsCompletedPageWithoutClaimingCompletion() = runBlocking {
        remote.rows = (0..200).map { row("activity-$it") }; remote.failAfterFirstPage = true
        assertFalse(repo.signIn("fixture@example.invalid", "fixture"))
        assertEquals(200, repo.state.value.activities.size)
        assertNull(repo.state.value.lastSync)
        repo.close(); remote.offline = true; open()
        assertEquals(200, repo.state.value.activities.size)
    }
    @Test fun wrongOwnerAndDuplicatePageNeverOverwriteCachedRecords() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        remote.rows = listOf(row("different", "another-athlete")); assertFalse(repo.refresh())
        assertEquals("activity-1", repo.state.value.activities.single().id)
        remote.rows = listOf(row("duplicate"), row("duplicate")); assertFalse(repo.refresh())
        assertEquals("activity-1", repo.state.value.activities.single().id)
    }
    @Test fun rejectedAccountSwitchKeepsExistingSessionAndCacheUsable() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        remote.rejectSignIn = true
        assertFalse(repo.signIn("other@example.invalid", "incorrect-fixture"))
        assertEquals(account, repo.state.value.account)
        assertFalse(repo.state.value.needsSignIn)
        assertEquals(1, repo.state.value.activities.size)
        assertNotNull(SessionVault(context).read(account))
    }
    @Test fun serverVerificationFailureDoesNotActivateOrSaveSession() = runBlocking {
        remote.rejectVerification = true
        assertFalse(repo.signIn("fixture@example.invalid", "fixture"))
        assertNull(repo.state.value.account)
        assertNull(SessionVault(context).read(account))
    }
    @Test fun expiredSessionRefreshIsPersistedBeforeDataRequest() = runBlocking {
        remote.expires = 1; remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertTrue(repo.refresh())
        assertEquals(1, remote.refreshCount)
        assertEquals("rotated-token", SessionVault(context).read(account)!!.accessToken)
    }
    @Test fun logoutOfflineHidesCacheButPreservesRecordsForSameAccount() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        remote.offline = true; assertTrue(repo.signOut())
        assertNull(repo.state.value.account); assertTrue(repo.state.value.activities.isEmpty())
        assertNull(SessionVault(context).read(account))
        remote.offline = false; remote.rows = emptyList()
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertEquals(1, repo.state.value.activities.size) // Empty remote page never implies deletion.
    }
    private class FakeGateway(val account: String): TrainingGateway {
        var rows: List<JSONObject> = emptyList()
        var offline = false
        var failAfterFirstPage = false
        var expires = Long.MAX_VALUE
        var refreshCount = 0
        var rejectVerification = false
        var rejectSignIn = false
        private fun connected() { if(offline) throw IOException("Synthetic offline") }
        override fun signIn(email: String, password: String): NativeSession { connected(); if(rejectSignIn) throw ServiceFailure(400, "Synthetic rejected credentials", requiresSignIn = true); return NativeSession(account, "fixture-token", "fixture-refresh", expires) }
        override fun verify(session: NativeSession) { connected(); if(rejectVerification) throw ServiceFailure(401, "Synthetic verification rejection") }
        override fun refresh(session: NativeSession): NativeSession { connected(); refreshCount++; return NativeSession(account, "rotated-token", "rotated-refresh", Long.MAX_VALUE) }
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
            connected(); if(before != null && failAfterFirstPage) throw IOException("Synthetic page interruption")
            val offset = if(before == null) 0 else rows.indexOfFirst { it.getString("id") == before.id } + 1
            return JSONArray(rows.drop(offset).take(200))
        }
        override fun intervals(session: NativeSession, action: String): JSONObject { connected(); return JSONObject().put("ok", true) }
        override fun signOut(session: NativeSession) { connected() }
    }
}
