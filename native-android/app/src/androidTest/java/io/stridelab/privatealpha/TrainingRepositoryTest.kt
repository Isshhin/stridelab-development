package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    @Test fun historicalPaginationHandlesShortServerPagesAndTimestampTies() = runBlocking {
        remote.rows = (0 until 405).map { row("activity-%04d".format(it)) }; remote.pageSize = 37
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertEquals(405,repo.state.value.activities.size); assertNotNull(repo.state.value.lastSync)
        assertEquals(12,remote.requestCount) // 11 data pages + explicit empty end page.
        assertTrue(repo.refresh()); assertEquals(405,repo.state.value.activities.size)
    }
    @Test fun accessTokenRejectionRenewsOnceAndRetriesWithoutSigningOut() = runBlocking {
        remote.rows = listOf(row()); remote.rejectNextData = true
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertEquals(1,remote.refreshCount); assertFalse(repo.state.value.needsSignIn)
        assertEquals("rotated-token",SessionVault(context).read(account)!!.accessToken)
    }
    @Test fun repeated401StopsAfterOneRenewalAndKeepsCache() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val last = repo.state.value.lastSync; remote.rejectAllData = true
        assertFalse(repo.refresh()); assertEquals(1,remote.refreshCount)
        assertTrue(repo.state.value.needsSignIn); assertEquals(1,repo.state.value.activities.size); assertEquals(last,repo.state.value.lastSync)
    }
    @Test fun changedRefreshOwnerIsNotSavedOrActivated() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val oldToken = SessionVault(context).read(account)!!.accessToken
        remote.rejectNextData = true; remote.changeRefreshOwner = true
        assertFalse(repo.refresh()); assertEquals(account,repo.state.value.account)
        assertEquals(oldToken,SessionVault(context).read(account)!!.accessToken)
    }
    @Test fun malformedCursorOrderRetainsPreviousCacheAndCompletionMarker() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val last = repo.state.value.lastSync
        remote.orderCorrectly = false; remote.rows = listOf(row("a"),row("z"))
        assertFalse(repo.refresh()); assertEquals(1,repo.state.value.activities.size); assertEquals(last,repo.state.value.lastSync)
    }
    @Test fun disconnectedSourceDoesNotTriggerSyncOrClaimSuccess() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.checkIntervals()); assertFalse(repo.state.value.intervals!!.configured)
        assertFalse(repo.syncIntervals()); assertEquals(0,remote.syncCount)
        assertEquals(1,repo.state.value.activities.size)
    }
    @Test fun realConnectionStateIsCachedPerAccountAndReopenedOffline() = runBlocking {
        remote.rows = listOf(row()); remote.configured = true
        assertTrue(repo.signIn("fixture@example.invalid","fixture")); assertTrue(repo.checkIntervals())
        repo.close(); remote.offline = true; open()
        assertTrue(repo.state.value.intervals!!.configured); assertTrue(repo.state.value.intervals!!.checkedAt > 0)
    }
    @Test fun logoutHidesDataBeforeWaitingForServerRevocation() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        remote.logoutEntered = CountDownLatch(1); remote.finishLogout = CountDownLatch(1)
        val pending = async(kotlinx.coroutines.Dispatchers.IO) { repo.signOut() }
        try {
            assertTrue(remote.logoutEntered!!.await(10,TimeUnit.SECONDS))
            assertNull(repo.state.value.account); assertTrue(repo.state.value.activities.isEmpty())
            assertNull(SessionVault(context).read(account))
        } finally { remote.finishLogout!!.countDown() }
        assertTrue(pending.await())
    }
    private class FakeGateway(val account: String): TrainingGateway {
        var rows: List<JSONObject> = emptyList()
        var offline = false
        var failAfterFirstPage = false
        var expires = Long.MAX_VALUE
        var refreshCount = 0
        var rejectVerification = false
        var rejectSignIn = false
        var pageSize = 200
        var requestCount = 0
        var rejectNextData = false
        var rejectAllData = false
        var changeRefreshOwner = false
        var orderCorrectly = true
        var configured = false
        var syncCount = 0
        var logoutEntered: CountDownLatch? = null
        var finishLogout: CountDownLatch? = null
        private fun connected() { if(offline) throw IOException("Synthetic offline") }
        override fun signIn(email: String, password: String): NativeSession { connected(); if(rejectSignIn) throw ServiceFailure(400, "Synthetic rejected credentials", requiresSignIn = true); return NativeSession(account, "fixture-token", "fixture-refresh", expires) }
        override fun verify(session: NativeSession) { connected(); if(rejectVerification) throw ServiceFailure(401, "Synthetic verification rejection") }
        override fun refresh(session: NativeSession): NativeSession { connected(); refreshCount++; return NativeSession(if(changeRefreshOwner) "wrong-owner" else account, "rotated-token", "rotated-refresh", Long.MAX_VALUE) }
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
            connected(); requestCount++
            if(rejectNextData || rejectAllData) { rejectNextData = false; throw ServiceFailure(401,"Synthetic expired access") }
            if(before != null && failAfterFirstPage) throw IOException("Synthetic page interruption")
            val ordered = if(orderCorrectly) rows.sortedWith(compareByDescending<JSONObject> { Instant.parse(it.getString("started_at")) }.thenByDescending { it.getString("id") }) else rows
            return JSONArray(ordered.filter {
                before == null || Instant.parse(it.getString("started_at")) < before.startedAt ||
                    (Instant.parse(it.getString("started_at")) == before.startedAt && it.getString("id") < before.id)
            }.take(pageSize))
        }
        override fun intervals(session: NativeSession, action: String): JSONObject {
            connected()
            if(action == "status") return JSONObject().put("configured",configured).put("syncStatus",if(configured) "connected" else "disconnected")
            syncCount++; return JSONObject().put("ok",true)
        }
        override fun signOut(session: NativeSession) { connected(); logoutEntered?.countDown(); if(finishLogout != null) check(finishLogout!!.await(10,TimeUnit.SECONDS)) }
    }
}
