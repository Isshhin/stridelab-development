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
    private fun row(id: String = "activity-1", owner: String = account, source:String="manual"): JSONObject = JSONObject()
        .put("id", id).put("user_id", owner).put("source", source).put("title", "Synthetic fixture")
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
    @Test fun localActivityRenameSurvivesRefreshAndRejectsMissingHistory() = runBlocking {
        remote.rows = listOf(row())
        assertFalse(repo.renameActivity("activity-1", "Before login"))
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertFalse(repo.renameActivity("not-saved", "Unknown"))
        assertTrue(repo.renameActivity("activity-1", "  Evening Session  "))
        assertEquals("Evening Session", repo.state.value.activities.single().title)
        assertTrue(repo.refresh())
        assertEquals("Evening Session", repo.state.value.activities.single().title)
        repo.close(); remote.offline = true; open()
        assertEquals("Evening Session", repo.state.value.activities.single().title)
    }

    @Test fun repeatedRefreshDeduplicatesAndRetainsMissingMetrics() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertTrue(repo.refresh()); assertTrue(repo.refresh())
        assertEquals(1, repo.state.value.activities.size)
        assertNull(repo.state.value.activities.single().recordedPace)
    }
    @Test fun successfulSharedHistoryRefreshNeverInvokesRetiredSourceSync() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertNull(repo.state.value.message)
        // The background worker calls this same refresh method, with no UI feedback emitter.
        repeat(2) { assertTrue(repo.refresh()); assertNull(repo.state.value.message) }
        assertEquals(0,remote.syncCount)
        assertNull(repo.state.value.message)
        assertNotNull(repo.state.value.lastSync)
        assertEquals(1, repo.state.value.activities.size)
        assertTrue(repo.signOut())
        assertNull(repo.state.value.message)
    }
    @Test fun failedRefreshKeepsActionableMessageAndCacheAndRecoveryIsSilent() = runBlocking {
        remote.rows = listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        remote.offline = true
        assertFalse(repo.refresh())
        assertTrue(repo.state.value.message!!.contains("retained"))
        assertEquals(1, repo.state.value.activities.size)
        remote.offline = false
        assertTrue(repo.refresh())
        assertNull(repo.state.value.message)
        assertEquals(1, repo.state.value.activities.size)
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
    @Test fun remoteRetiredActivitiesNeverEnterActiveHistory() = runBlocking {
        remote.rows=listOf(row("legacy",source="intervals"),row("garmin-run",source="garmin"))
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertEquals(listOf("garmin-run"),repo.state.value.activities.map { it.id })
        assertEquals(0,remote.syncCount)
    }
    @Test fun previouslyCachedRetiredActivityRemainsOnDiskButInvisibleOffline() = runBlocking {
        remote.rows=listOf(row());assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        repo.close()
        val legacy=row("archived-original",source="intervals")
        val storage=TrainingStore(context,database)
        storage.applyRemotePage(account,"activity",
            listOf(TrainingStore.RemoteRecord("archived-original",legacy.toString(),null)),
            null,System.currentTimeMillis())
        storage.close()
        remote.offline=true;open()
        assertEquals(listOf("activity-1"),repo.state.value.activities.map { it.id })
        val archive=TrainingStore(context,database)
        assertNotNull(archive.get(account,"activity","archived-original"))
        archive.close()
        assertEquals(0,remote.syncCount)
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
    @Test fun concurrentForegroundRefreshesRotateExpiredSessionOnlyOnce() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        SessionVault(context).save(NativeSession(account,"fixture-token","fixture-refresh",1))
        val first = async(kotlinx.coroutines.Dispatchers.IO) { repo.refresh() }
        val second = async(kotlinx.coroutines.Dispatchers.IO) { repo.refresh() }
        assertTrue(first.await()); assertTrue(second.await()); assertEquals(1,remote.refreshCount)
        assertEquals(1,repo.state.value.activities.size)
    }
    @Test fun damagedSessionRequiresReauthenticationWithoutLosingCachedHistory() = runBlocking {
        remote.rows = listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = java.io.File(context.filesDir,"native-sessions/$hash.vault")
        val damaged = byteArrayOf(1,2,3); file.writeBytes(damaged)
        assertFalse(repo.refresh()); assertTrue(repo.state.value.needsSignIn)
        assertEquals(1,repo.state.value.activities.size); assertArrayEquals(damaged,file.readBytes())
    }
    @Test fun largeSavedRecordingsDoNotHideOfflineGarminHistory() = runBlocking {
        remote.rows=(0 until 8).map { row("garmin-$it",source="garmin") }
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val storage=TrainingStore(context,database)
        try {
            for(i in 0 until 8) {
                val id="garmin-$i"
                val sample=JSONObject().put("activity_id",id)
                    .put("sample_index",0).put("elapsed_s",0.0)
                val raw=JSONObject().put("user_id",account)
                    .put("activity_id",id).put("fetched_at",1234)
                    .put("samples",JSONArray().put(sample)).put("laps",JSONArray())
                    // A source-faithful synthetic payload near CursorWindow size.
                    .put("fixture_padding","x".repeat(1_800_000)).toString()
                storage.applyRemotePage(account,"activity_detail",
                    listOf(TrainingStore.RemoteRecord(id,raw,null)),
                    null,System.currentTimeMillis())
            }
        } finally { storage.close() }
        repo.close(); remote.offline=true; open()
        assertEquals(8,repo.state.value.activities.size)
        assertTrue(repo.state.value.activities.all { it.source=="garmin" })
        assertTrue(repo.state.value.details.size<=6)
        assertTrue(repo.loadCachedDetail("garmin-0"))
        assertEquals(1,repo.state.value.details["garmin-0"]!!.samples.size)
        assertFalse(repo.loadCachedDetail("not-a-saved-activity"))
    }

    @Test fun detailsSurviveOfflineRecreationAndRejectCrossAccountReplacement() = runBlocking {
        remote.rows=listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        repo.close(); remote.offline=true; open()
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        assertFalse(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        remote.offline=false; remote.wrongDetailOwner=true
        assertFalse(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        assertTrue(repo.signOut()); assertTrue(repo.state.value.details.isEmpty())
    }
    @Test fun authenticWellnessPersistsOfflineAndIsHiddenOnSignOut() = runBlocking {
        remote.rows=listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.refreshWellness()); assertEquals(7.5,repo.state.value.wellness.single().value,0.0)
        repo.close(); remote.offline=true; open()
        assertEquals("sleep_duration",repo.state.value.wellness.single().type)
        assertFalse(repo.refreshWellness()); assertEquals(1,repo.state.value.wellness.size)
        remote.offline=false; assertTrue(repo.signOut()); assertTrue(repo.state.value.wellness.isEmpty())
    }
    @Test fun retiredWellnessReadingsNeverReenterActiveHistory() = runBlocking {
        remote.wellnessSource="intervals"
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.refreshWellness())
        assertTrue(repo.state.value.wellness.isEmpty())
        assertEquals(0,remote.syncCount)
    }

    @Test fun guestGarminSyncRenameAndHistorySurviveOfflineAndCloudTransitions() = runBlocking {
        assertNull(repo.state.value.account)
        val localOwner=repo.state.value.localGarminOwner!!
        val credentials=object:GarminCredentialStore {
            private var saved:GarminCredentialRecord?=null
            override fun read(accountId:String)=saved?.takeIf { it.accountId==accountId }
            override fun save(record:GarminCredentialRecord) { saved=record }
            override fun clear(accountId:String) { saved=null }
        }
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=GarminBearerToken("guest-fixture-bearer",
                Instant.now().plusSeconds(86_400))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int)=
                listOf(GarminActivity("guest-run-1","Guest Garmin Run","running",
                    Instant.parse("2026-11-11T07:20:00Z"),5000.0,1780.0,1750.0,145.0))
                    .drop(start).take(limit)
        }
        val source=GarminPersonalSource(localOwner,credentials,api)
        assertEquals("connected",source.connect("guest-only-test-key").state)
        assertEquals(1,repo.syncGarmin(source,detailLimit=0).importedCount)
        val id=repo.state.value.activities.single().id
        assertTrue(id in repo.state.value.localGarminIds)
        assertEquals("Guest Garmin Run",repo.state.value.activities.single().title)
        assertTrue(repo.renameActivity(id,"Guest Evening Run"))
        assertEquals(0,repo.syncGarmin(source,detailLimit=0).importedCount)
        assertEquals("Guest Evening Run",repo.state.value.activities.single().title)
        repo.close();remote.offline=true;open()
        assertNull(repo.state.value.account)
        assertEquals(localOwner,repo.state.value.localGarminOwner)
        assertEquals("Guest Evening Run",repo.state.value.activities.single().title)
        // Authenticated cloud history can coexist without rewriting local data.
        remote.offline=false
        remote.rows=listOf(row("separate-cloud-history"))
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertEquals(account,repo.state.value.account)
        assertTrue(repo.state.value.activities.any { it.id==id && it.title=="Guest Evening Run" })
        assertTrue(repo.state.value.activities.any { it.id=="separate-cloud-history" })
        assertTrue(repo.signOut())
        assertNull(repo.state.value.account)
        assertEquals(localOwner,repo.state.value.localGarminOwner)
        assertEquals(1,repo.state.value.activities.size)
        assertEquals("Guest Evening Run",repo.state.value.activities.single().title)
        assertFalse(repo.state.value.activities.any { it.id=="separate-cloud-history" })
    }

    @Test fun signingOutPreservesLegacyEncryptedGarminConnectionLocally() = runBlocking {
        remote.rows=listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val credentials=GarminCredentialVault(context)
        val owner=repo.state.value.localGarminOwner!!
        try {
            credentials.save(GarminCredentialRecord(
                account,"synthetic-legacy-garmin-key","synthetic-legacy-token",
                Instant.now().plusSeconds(3600)))
            assertTrue(repo.signOut())
            assertNull(repo.state.value.account)
            val copied=credentials.read(owner)
            assertNotNull(copied)
            assertEquals(owner,copied!!.accountId)
            assertEquals("synthetic-legacy-garmin-key",copied.apiKey)
            assertEquals("synthetic-legacy-token",copied.bearerToken)
        } finally {
            runCatching { credentials.clear(owner) }
            runCatching { credentials.clear(account) }
        }
    }

    @Test fun guestGarminSyncRejectsUnrelatedSourceOwner() = runBlocking {
        val credentials=object:GarminCredentialStore {
            override fun read(accountId:String):GarminCredentialRecord?=null
            override fun save(record:GarminCredentialRecord) {}
            override fun clear(accountId:String) {}
        }
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=GarminBearerToken("unrelated",Instant.now().plusSeconds(3600))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int)=emptyList<GarminActivity>()
        }
        val foreign=GarminPersonalSource("unrelated-athlete",credentials,api)
        try {
            repo.syncGarmin(foreign,detailLimit=0)
            fail("A foreign Garmin source must never be able to write local history")
        } catch (_: GarminAuthenticationException) { }
        assertTrue(repo.state.value.activities.isEmpty())
    }

    @Test fun garminSummarySyncAppearsInActivityTabAndSurvivesOfflineReopen() = runBlocking {
        remote.rows=listOf(row("intervals-saved",source="intervals"))
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.state.value.activities.isEmpty())
        val credentials=object:GarminCredentialStore {
            var saved:GarminCredentialRecord?=null
            override fun read(accountId:String)=saved?.takeIf { it.accountId==accountId }
            override fun save(record:GarminCredentialRecord) { saved=record }
            override fun clear(accountId:String) { saved=null }
        }
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=GarminBearerToken("fixture-bearer",
                Instant.now().plusSeconds(86_400))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int)=
                listOf(GarminActivity("garmin-original-123","Garmin Run","running",
                    Instant.parse("2026-11-04T02:06:00Z"),3807.0,9560.0,3720.0,138.0))
                    .drop(start).take(limit)
        }
        val source=GarminPersonalSource(account,credentials,api)
        assertEquals("connected",source.connect("test-only-key").state)
        val first=repo.syncGarmin(source)
        assertEquals("connected",first.state)
        assertEquals(1,first.importedCount)
        assertEquals(1,repo.state.value.activities.size)
        val run=repo.state.value.activities.single { it.source=="garmin" }
        assertEquals("Garmin Run",run.title)
        assertTrue(run.id.startsWith("garmin-"))
        assertEquals(9560.0,run.distanceM!!,0.0)
        assertEquals(138.0,run.averageHr!!,0.0)
        // Rename the display title locally without modifying the Garmin record.
        assertFalse(repo.renameActivity(run.id, "  "))
        assertTrue(repo.renameActivity(run.id, "Morning Progression"))
        assertEquals("Morning Progression", repo.state.value.activities.single().title)
        val original = TrainingStore(context,database)
        try {
            val payload = JSONObject(original.get(account,"activity",run.id)!!.payload)
            assertEquals("Garmin Run",payload.getString("title"))
            assertEquals("garmin",payload.getString("source"))
        } finally { original.close() }
        assertEquals(0,repo.syncGarmin(source).importedCount)
        assertEquals(1,repo.state.value.activities.size)
        assertEquals("Morning Progression",repo.state.value.activities.single().title)
        assertNull(repo.state.value.activities.find { it.id=="intervals-saved" })

        repo.close();remote.offline=true;open()
        assertEquals(1,repo.state.value.activities.size)
        assertEquals("Morning Progression",
            repo.state.value.activities.single { it.id==run.id && it.source=="garmin" }.title)
        assertNull(repo.state.value.activities.find { it.id=="intervals-saved" })
    }

    @Test fun duplicateGarminIdsWithinOnePageDoNotPartiallyWriteHistory() = runBlocking {
        remote.rows=listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        val store=object:GarminCredentialStore {
            override fun read(accountId:String)=GarminCredentialRecord(accountId,"key",
                "bearer",Instant.now().plusSeconds(86_400))
            override fun save(record:GarminCredentialRecord) {}
            override fun clear(accountId:String) {}
        }
        val duplicate=GarminActivity("same-source-id","Run","running",
            Instant.parse("2026-11-04T02:06:00Z"),1800.0,5000.0,null,null)
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=GarminBearerToken("bearer",Instant.now().plusSeconds(86_400))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int)=
                listOf(duplicate,duplicate).drop(start).take(limit)
        }
        val source=GarminPersonalSource(account,store,api)
        try { repo.syncGarmin(source); fail("Duplicate source records must fail closed") }
        catch(_:GarminProtocolException) {}
        assertEquals(1,repo.state.value.activities.size)
        assertTrue(repo.state.value.activities.none { it.source=="garmin" })
    }

    private class FakeGateway(val account: String): TrainingGateway {
        var wrongDetailOwner = false
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
        var wellnessSource="health_connect"
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
        override fun wellness(session: NativeSession): JSONArray {
            connected()
            return JSONArray().put(JSONObject().put("id","wellness-1").put("user_id",account).put("source",wellnessSource)
                .put("metric_type","sleep_duration").put("value",7.5).put("unit","hours").put("recorded_at","2026-10-25T12:00:00Z"))
        }
        override fun detail(session: NativeSession, id: String): JSONObject {
            connected()
            return JSONObject().put("user_id",if(wrongDetailOwner) "another" else account).put("activity_id",id).put("fetched_at",1234)
                .put("samples",JSONArray().put(JSONObject().put("activity_id",id).put("sample_index",0).put("elapsed_s",0).put("heart_rate",145)))
                .put("laps",JSONArray())
        }
        override fun signOut(session: NativeSession) { connected(); logoutEntered?.countDown(); if(finishLogout != null) check(finishLogout!!.await(10,TimeUnit.SECONDS)) }
    }
}
