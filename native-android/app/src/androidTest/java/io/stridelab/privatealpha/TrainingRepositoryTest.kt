package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
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
    @Test fun coldReopenLoadsRecentPageThenOlderHistoryWithoutBlockingGps() = runBlocking {
        remote.rows = listOf(row("latest"))
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.loadDetail("latest"))
        val sourceRequests = remote.requestCount
        val detailRequests = remote.detailRequests
        repo.close()

        // Inject a synthetic account-owned history using the real SQLite store.
        // Every fixture is older than the cached "latest" detail.
        val sqlite = TrainingStore(context,database)
        val additions = (0 until 127).map { index ->
            val raw = row("older-$index")
                .put("started_at",
                    Instant.parse("2026-10-01T12:00:00Z")
                        .minusSeconds(index.toLong() * 60).toString())
            TrainingStore.RemoteRecord("older-$index",raw.toString(),null)
        }
        sqlite.applyRemotePage(account,"activity",additions,null,System.currentTimeMillis())
        sqlite.close()
        remote.offline = true
        open()

        val first = repo.state.value
        assertEquals(account,first.account)
        assertEquals("latest",first.activities.first().id)
        assertTrue("latest" in first.cachedDetailIds)
        if (!first.historyFullyLoaded) {
            assertEquals(40,first.activities.size)
            assertFalse(first.activities.any { it.id == "older-126" })
        }
        assertTrue(first.details.isEmpty())
        // While Activity Detail is on screen, page hydration must yield
        // entirely. This avoids competing with FIT parsing and map rendering.
        repo.setActivityDetailVisible(true)
        try {
            // Opening real locally cached GPS must work before older summaries
            // finish loading, without a server or Garmin request.
            assertTrue(repo.loadCachedDetail("latest"))
            assertEquals(1,repo.state.value.details.getValue("latest").samples.size)
            assertEquals(detailRequests,remote.detailRequests)
            if (!first.historyFullyLoaded) {
                delay(2_500L)
                assertEquals(40,repo.state.value.activities.size)
                assertFalse(repo.state.value.historyFullyLoaded)
                // A restored older Activity Detail must be retrievable by ID
                // before older history pages are displayed.
                assertTrue(repo.loadCachedSummary("older-126"))
                assertTrue(repo.state.value.activities.any { it.id == "older-126" })
                assertFalse(repo.loadCachedSummary("not-in-sqlite"))
            }
        } finally {
            repo.setActivityDetailVisible(false)
        }

        withTimeout(25_000L) {
            repo.state.first { it.historyFullyLoaded && it.activities.size == 128 }
        }
        assertEquals(128,repo.state.value.activities.map { it.id }.distinct().size)
        assertEquals("older-126",repo.state.value.activities.last().id)
        assertEquals(sourceRequests,remote.requestCount)
        assertEquals(detailRequests,remote.detailRequests)
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
        // The initial page is bounded; remaining local summaries hydrate
        // without network requests or changing saved records.
        withTimeout(20_000L) {
            repo.state.first { it.historyFullyLoaded && it.activities.size == 200 }
        }
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
    @Test fun thumbnailReopensOfflineWithoutLoadingDetailOrProvider() = runBlocking {
        GpsThumbnailCache(context).remove(account, "activity-1")
        remote.rows = listOf(row())
        remote.gpsDetail = true
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        assertTrue(repo.loadDetail("activity-1"))
        repo.loadGpsThumbnail("activity-1")
        withTimeout(10_000L) { repo.state.first { "activity-1" in it.gpsThumbnails } }
        val requests = remote.detailRequests
        repo.close(); remote.offline = true; open()
        assertTrue(repo.state.value.details.isEmpty())
        repo.loadGpsThumbnail("activity-1")
        assertNotNull(repo.state.value.gpsThumbnails["activity-1"])
        assertTrue(repo.state.value.details.isEmpty())
        assertEquals(requests, remote.detailRequests)
        val sqlite = TrainingStore(context, database)
        try { assertEquals(2, JSONObject(sqlite.get(account, "activity_detail", "activity-1").payload).getJSONArray("samples").length()) }
        finally { sqlite.close() }
    }

    @Test fun detailsSurviveOfflineRecreationAndRejectCrossAccountReplacement() = runBlocking {
        remote.rows=listOf(row())
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        val providerCalls=remote.detailRequests
        repo.close(); remote.offline=true; open()
        // Cold startup indexes detail IDs without parsing any FIT streams.
        assertTrue(repo.state.value.details.isEmpty())
        assertTrue("activity-1" in repo.state.value.cachedDetailIds)
        assertTrue(repo.loadCachedDetail("activity-1"))
        assertEquals(providerCalls,remote.detailRequests)
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        assertFalse(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        remote.offline=false; remote.wrongDetailOwner=true
        assertFalse(repo.loadDetail("activity-1"))
        assertEquals(1,repo.state.value.details["activity-1"]!!.samples.size)
        assertTrue(repo.signOut())
        assertTrue(repo.state.value.details.isEmpty())
        assertTrue(repo.state.value.cachedDetailIds.isEmpty())
    }
    @Test fun largeRecordedDetailOpensOfflineAfterDatabaseReopen() = runBlocking {
        val localAccount=repo.state.value.account!!
        repo.close()
        val samples=JSONArray()
        repeat(15000) { i -> samples.put(JSONObject().put("activity_id","activity-1")
            .put("sample_index",i).put("elapsed_s",i).put("heart_rate",145)
            .put("cadence_spm",178).put("latitude",14.123456).put("longitude",121.123456)
            .put("timestamp","2026-11-08T00:00:00Z")) }
        val detail=JSONObject().put("user_id",localAccount).put("activity_id","activity-1")
            .put("fetched_at",1234).put("samples",samples)
            .put("laps",JSONArray().put(JSONObject().put("activity_id","activity-1").put("lap_index",0)))
            .put("pause_intervals",JSONArray().put(JSONObject().put("start_s",10).put("end_s",20)))
        val raw=detail.toString()
        assertTrue(raw.toByteArray().size > 2 * 1024 * 1024)
        val sqlite=TrainingStore(context,database)
        sqlite.applyRemotePage(localAccount,"activity",listOf(TrainingStore.RemoteRecord("activity-1",row(owner=localAccount).toString(),null)),null,1234)
        sqlite.applyRemotePage(localAccount,"activity_detail",listOf(TrainingStore.RemoteRecord("activity-1",raw,null)),null,1234)
        sqlite.close()
        remote.offline=true; open()
        assertTrue(repo.loadCachedDetail("activity-1"))
        val loaded=repo.state.value.details.getValue("activity-1")
        assertEquals(15000,loaded.samples.size)
        assertEquals(178.0,loaded.samples.last().cadenceSpm!!,0.0)
        assertEquals(14.123456,loaded.samples.last().latitude!!,0.0)
        assertEquals(1,loaded.laps.size)
        assertEquals(1,loaded.pauseIntervals.size)
        val reopened=TrainingStore(context,database)
        try { assertEquals(raw,reopened.get(localAccount,"activity_detail","activity-1").payload) }
        finally { reopened.close() }
    }
    @Test fun cachedDetailReadFailureShowsRetainedRecordWarning() = runBlocking {
        val localAccount=repo.state.value.account!!
        repo.close()
        val seed=TrainingStore(context,database)
        seed.applyRemotePage(localAccount,"activity",listOf(TrainingStore.RemoteRecord("activity-1",row(owner=localAccount).toString(),null)),null,1)
        val detail=JSONObject().put("user_id",localAccount).put("activity_id","activity-1")
            .put("fetched_at",1).put("samples",JSONArray()).put("laps",JSONArray())
        seed.applyRemotePage(localAccount,"activity_detail",listOf(TrainingStore.RemoteRecord("activity-1",detail.toString(),null)),null,1)
        seed.close(); remote.offline=true; open()
        val sqlite=TrainingStore(context,database)
        try { sqlite.writableDatabase.execSQL("ALTER TABLE records RENAME TO retained_records") }
        finally { sqlite.close() }
        assertFalse(repo.loadCachedDetail("activity-1"))
        assertTrue(repo.state.value.message.orEmpty().contains("original local record was retained"))
        val retained=TrainingStore(context,database)
        try {
            retained.readableDatabase.rawQuery("SELECT count(*) FROM retained_records WHERE kind='activity_detail'",null).use {
                assertTrue(it.moveToFirst()); assertEquals(1,it.getInt(0))
            }
        } finally { retained.close() }
    }
    @Test fun coldStartKeepsMultipleActivityDetailsOnDiskUntilOpened() = runBlocking {
        remote.rows=listOf(row("a"),row("b"))
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.loadDetail("a"))
        assertTrue(repo.loadDetail("b"))
        assertEquals(2,repo.state.value.cachedDetailIds.size)
        repo.close()
        remote.offline=true
        open()
        assertEquals(2,repo.state.value.activities.size)
        assertEquals(setOf("a","b"),repo.state.value.cachedDetailIds)
        assertTrue(repo.state.value.details.isEmpty())
        val requests=remote.detailRequests
        assertTrue(repo.loadCachedDetail("b"))
        assertEquals(setOf("b"),repo.state.value.details.keys)
        assertEquals(requests,remote.detailRequests)
        assertFalse(repo.loadCachedDetail("unknown"))
        assertEquals(setOf("b"),repo.state.value.details.keys)
    }

    @Test fun openedDetailMemoryCacheIsBoundedWithoutDeletingSqliteRecords() = runBlocking {
        val ids=(1..6).map { "fixture-$it" }
        remote.rows=ids.map { row(it) }
        assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        ids.forEach { id -> assertTrue(repo.loadDetail(id)) }
        assertEquals(6,repo.state.value.cachedDetailIds.size)
        assertTrue(repo.state.value.details.size <= 4)
        assertTrue(repo.loadCachedDetail(ids.first()))
        assertTrue(ids.first() in repo.state.value.details)
        assertEquals(6,repo.state.value.cachedDetailIds.size)
    }

    @Test fun authenticWellnessPersistsOfflineAndIsHiddenOnSignOut() = runBlocking {
        remote.rows=listOf(row()); assertTrue(repo.signIn("fixture@example.invalid","fixture"))
        assertTrue(repo.refreshWellness()); assertEquals(7.5,repo.state.value.wellness.single().value,0.0)
        repo.close(); remote.offline=true; open()
        withTimeout(10_000L) { repo.state.first { it.wellness.isNotEmpty() } }
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
        assertEquals(0,repo.syncGarmin(source).importedCount)
        assertEquals(1,repo.state.value.activities.size)
        assertNull(repo.state.value.activities.find { it.id=="intervals-saved" })

        repo.close();remote.offline=true;open()
        assertEquals(1,repo.state.value.activities.size)
        assertNotNull(repo.state.value.activities.find { it.id==run.id && it.source=="garmin" })
        assertNull(repo.state.value.activities.find { it.id=="intervals-saved" })
    }

    @Test fun garminSyncDownloadsNewestFitBeforeOlderHistoryAndResumesBackfill() = runBlocking {
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        val credentials = object : GarminCredentialStore {
            override fun read(accountId: String) = GarminCredentialRecord(
                accountId, "fixture-key", "fixture-bearer", Instant.now().plusSeconds(86_400)
            )
            override fun save(record: GarminCredentialRecord) {}
            override fun clear(accountId: String) {}
        }
        // Deliberately unsorted within the first page: local priority must
        // not depend on Garmin's ordering or on historic SQLite hydration.
        val newest = Instant.parse("2026-11-07T12:00:00Z")
        fun activity(index: Long) = GarminActivity(
            "recent-fit-priority-$index", "Garmin Run $index", "running",
            newest.minusSeconds(index * 3600L), 1800.0, 5000.0,
            1750.0, 142.0, numericActivityId = index
        )
        val history = ((100L downTo 1L) + (102L downTo 101L)).map(::activity)
        val events = mutableListOf<String>()
        val api = object : GarminApi {
            override fun exchange(apiKey: String) = GarminBearerToken(
                "fixture-bearer", Instant.now().plusSeconds(86_400)
            )
            override fun activities(token: GarminBearerToken, start: Int, limit: Int): List<GarminActivity> {
                events += "page:$start"
                return history.drop(start).take(limit)
            }
            override fun downloadActivity(token: GarminBearerToken, numericId: Long): ByteArray {
                events += "fit:$numericId"
                // Missing individual exports (404) must not abort the batch.
                if (numericId == 1L) throw GarminServiceException(404)
                // Other invalid FITs are also scoped to their source record.
                throw GarminProtocolException("Synthetic unavailable FIT")
            }
        }
        val source = GarminPersonalSource(account, credentials, api)

        val first = repo.syncGarmin(source, pageLimit = 2, detailLimit = 3)
        assertEquals(102, first.importedCount)
        assertEquals(3, first.failedDetails)
        assertEquals(
            listOf("page:0", "fit:1", "fit:2", "fit:3", "page:100"),
            events
        )
        assertEquals(102, repo.state.value.activities.count { it.source == "garmin" })

        events.clear()
        val second = repo.syncGarmin(source, pageLimit = 2, detailLimit = 3)
        assertEquals(0, second.importedCount)
        assertEquals(
            listOf("page:0", "fit:4", "fit:5", "fit:6", "page:100"),
            events
        )
        // A failed FIT attempt must never delete or replace its source summary.
        assertEquals(102, repo.state.value.activities.count { it.source == "garmin" })
    }

    @Test fun interruptedGarminFitSyncKeepsSavedSummaryWithoutClaimingSuccess() = runBlocking {
        assertTrue(repo.signIn("fixture@example.invalid", "fixture"))
        val credentials = object : GarminCredentialStore {
            override fun read(accountId: String) = GarminCredentialRecord(
                accountId, "fixture-key", "fixture-bearer", Instant.now().plusSeconds(86_400)
            )
            override fun save(record: GarminCredentialRecord) {}
            override fun clear(accountId: String) {}
        }
        val api = object : GarminApi {
            override fun exchange(apiKey: String) = GarminBearerToken(
                "fixture-bearer", Instant.now().plusSeconds(86_400)
            )
            override fun activities(
                token: GarminBearerToken, start: Int, limit: Int
            ) = listOf(
                GarminActivity(
                    "interrupted-fit-original", "Recorded Garmin Run", "running",
                    Instant.parse("2026-11-04T02:06:00Z"), 1800.0, 5000.0,
                    1750.0, 140.0, numericActivityId = 123L
                )
            ).drop(start).take(limit)
            override fun downloadActivity(
                token: GarminBearerToken, numericId: Long
            ): ByteArray {
                throw GarminRateLimitException()
            }
        }
        val source = GarminPersonalSource(account, credentials, api)
        val result = repo.syncGarmin(source, detailLimit = 1)
        assertEquals("connected", result.state) // The linked provider is still connected.
        assertEquals(1, result.importedCount)
        assertEquals(1, result.failedDetails)
        assertNotNull(result.error)
        assertNull(result.lastSuccessfulSync) // Partial FIT sync is not full completion.
        assertEquals(1, repo.state.value.activities.count { it.source == "garmin" })
        repo.close(); remote.offline = true; open()
        assertEquals(1, repo.state.value.activities.count { it.source == "garmin" })
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
        var gpsDetail = false
        var wrongDetailOwner = false
        var detailRequests = 0
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
            detailRequests++
            val samples = JSONArray().put(JSONObject().put("activity_id",id).put("sample_index",0).put("elapsed_s",0).put("heart_rate",145)
                .apply { if (gpsDetail) put("latitude", 14.0).put("longitude", 121.0) })
            if (gpsDetail) samples.put(JSONObject().put("activity_id",id).put("sample_index",1).put("elapsed_s",10).put("latitude",14.01).put("longitude",121.01))
            return JSONObject().put("user_id",if(wrongDetailOwner) "another" else account).put("activity_id",id).put("fetched_at",1234)
                .put("samples",samples)
                .put("laps",JSONArray())
        }
        override fun signOut(session: NativeSession) { connected(); logoutEntered?.countDown(); if(finishLogout != null) check(finishLogout!!.await(10,TimeUnit.SECONDS)) }
    }
}
