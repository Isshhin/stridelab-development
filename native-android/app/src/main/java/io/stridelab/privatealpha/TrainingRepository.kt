package io.stridelab.privatealpha

import android.content.Context
import android.database.sqlite.SQLiteException
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Re-owner validated snapshots only at the explicit cloud boundary. */
internal fun remapCloudSnapshotOwner(payload: String, expected: String, destination: String): String {
    RecordPolicy.identifier(expected)
    RecordPolicy.identifier(destination)
    val raw = JSONObject(payload)
    require(raw.getString("user_id") == expected) { "Snapshot owner mismatch" }
    return raw.put("user_id", destination).toString()
}

internal data class TrainingState(
    val initializing: Boolean = true,
    val account: String? = null,
    /** Optional authenticated StrideLab owner; never the local SQLite namespace. */
    val cloudAccount: String? = null,
    val activities: List<ActivityRecord> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val lastSync: Long? = null,
    val needsSignIn: Boolean = false,
    val loginGeneration: Long = 0,
    val plans: List<PlanSnapshot> = emptyList(),
    val shelf:LibraryShelf = LibraryShelf(),
    val templates:List<SessionTemplate> = emptyList(),
    val workoutDrafts:Map<Pair<String,String>,WorkoutEntryDraft> = emptyMap(),
    val wellness: List<WellnessRecord> = emptyList(),
    val details: Map<String,ActivityDetail> = emptyMap(),
    val gpsThumbnails: Map<String,android.graphics.Bitmap> = emptyMap(),
    val task: String? = null,
    /** SQLite detail identifiers, not their potentially enormous FIT sample payloads. */
    val cachedDetailIds: Set<String> = emptySet(),
    /** False while older SQLite activity-summary pages are hydrating. */
    val historyFullyLoaded: Boolean = true,
    val historyPageFailed: Boolean = false
)

/** All network/database/encryption work is serialized off the main thread, including workers. */
internal class TrainingRepository(context: Context, private val gateway: TrainingGateway = SupabaseGateway(),
    databaseName: String = TrainingStore.DATABASE, preferencesName: String = "native-account-v1") {
    private val store = TrainingStore(context, databaseName)
    private val thumbnails = GpsThumbnailCache(context)
    private val thumbnailMutex = Mutex()
    private val appContext = context.applicationContext
    private val vault = SessionVault(context)
    internal val garminSyncPreferences = GarminSyncPreferences(context)
    private val garminCloud = GarminCloudBackupGateway()
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hotDetails = ArrayDeque<String>()
    private val hotDetailLimit = 4
    private val recentPageSize = 40

    /** Chronological SQLite keyset cursor, never an activity's raw FIT payload. */
    private data class HistoryCursor(val startedAt: String, val id: String)
    private data class HistoryPage(
        val records: List<ActivityRecord>,
        val next: HistoryCursor?,
        val hasMore: Boolean,
        val rejected: Int
    )
    private var nextHistoryCursor: HistoryCursor? = null
    // This is a presentation hint only; it never changes SQLite or providers.
    @Volatile private var activityDetailVisible: Boolean = false
    internal fun setActivityDetailVisible(visible: Boolean) {
        activityDetailVisible = visible
    }

    /**
     * Latest summaries first. JSON extraction runs inside SQLite; Kotlin only
     * parses the bounded result page. Invalid original rows are preserved.
     * The PK remains untouched; there is no migration or data replacement.
     */
    private fun readHistoryPage(account: String, after: HistoryCursor?): HistoryPage {
        val started = "CASE WHEN json_valid(payload) THEN " +
            "COALESCE(json_extract(payload, '$.started_at'), '') ELSE '' END"
        val source = "CASE WHEN json_valid(payload) THEN " +
            "LOWER(COALESCE(json_extract(payload, '$.source'), '')) ELSE '' END"
        val filter = if (after == null) "" else
            " AND ($started < ? OR ($started = ? AND record_id < ?))"
        val sql = "SELECT record_id,payload,$started AS started FROM records " +
            "WHERE account=? AND kind='activity' AND $source != 'intervals'$filter " +
            "ORDER BY started DESC, record_id DESC LIMIT ${recentPageSize + 1}"
        val arguments = if (after == null) arrayOf(account) else
            arrayOf(account,after.startedAt,after.startedAt,after.id)
        val records = ArrayList<ActivityRecord>(recentPageSize)
        var rejected = 0
        var hasMore = false
        var last: HistoryCursor? = null
        store.readableDatabase.rawQuery(sql,arguments).use { cursor ->
            var rows = 0
            while (cursor.moveToNext()) {
                if (rows == recentPageSize) {
                    hasMore = true
                    break
                }
                rows++
                last = HistoryCursor(cursor.getString(2) ?: "",cursor.getString(0))
                try {
                    val record = ActivityRecord.parse(cursor.getString(1),account)
                    if (ActiveActivitySources.accepts(record.source)) records += record
                } catch (_: Exception) {
                    rejected++
                    // Keep invalid source rows intact, not surfaced as fake data.
                }
            }
        }
        return HistoryPage(records,last.takeIf { hasMore },hasMore,rejected)
    }

    /** Debug-only aggregate timings; never log account IDs, activity IDs or payloads. */
    private fun traceCache(stage: String, startedAt: Long, count: Int) {
        if ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d("StrideLabStartup",
                "$stage ms=${SystemClock.elapsedRealtime() - startedAt} count=$count")
        }
    }

    private fun retainHotDetail(id: String, detail: ActivityDetail) {
        hotDetails.remove(id)
        hotDetails.addLast(id)
        while (hotDetails.size > hotDetailLimit) hotDetails.removeFirst()
        val retained = hotDetails.toSet()
        mutable.value = mutable.value.copy(
            details = (mutable.value.details + (id to detail)).filterKeys { it in retained },
            cachedDetailIds = mutable.value.cachedDetailIds + id
        )
    }

    init {
        scope.launch {
            // A device-local profile exists independently of any network login.
            // Existing account namespaces are preserved on upgrade.
            val account = preferences.getString("active", null) ?: run {
                val created = "local-" + java.util.UUID.randomUUID().toString()
                check(preferences.edit().putString("active", created).commit())
                created
            }
            val savedCloud = preferences.getString("cloud_account", null)
                ?: account.takeUnless { it.startsWith("local-") }
            val cloudAvailable = savedCloud != null &&
                runCatching { vault.read(savedCloud) != null }.getOrDefault(false)
            mutex.withLock {
                try {
                    mutable.value = TrainingState(
                        initializing = true, account = account,
                        cloudAccount = savedCloud.takeIf { cloudAvailable },
                        needsSignIn = savedCloud != null && !cloudAvailable
                    )
                    if (account.isNotBlank()) {
                        // First render at most one recent chronological page.
                        // Older activity summaries are loaded only after the
                        // cached Today screen has had a chance to draw.
                        val recentStarted = SystemClock.elapsedRealtime()
                        val page = try {
                            readHistoryPage(account,null)
                        } catch (error: SQLiteException) {
                            if (error.message?.contains("no such function",true) == true) {
                                // Older Android SQLite without JSON1 retains
                                // the original full-scan behavior, no data loss.
                                null
                            } else throw error
                        }
                        traceCache("recent_sqlite_page",recentStarted,page?.records?.size ?: 0)
                        loadCache(account,publishEssentials=true,recentPage=page)
                        // Cloud credentials are optional. Their absence must
                        // never lock a local plan, workout, activity or Garmin sync.
                    }
                    mutable.value = mutable.value.copy(initializing = false)
                } catch (_: Exception) {
                    mutable.value = mutable.value.copy(
                        initializing = false,
                        message = "Saved offline data could not be fully loaded. Existing files were retained."
                    )
                }
            }

            if (account != null) {
                // Do not hold the repository mutex over startup's second stage:
                // current GPS detail requests can run as soon as the first
                // recent page is published.
                delay(850L)
                mutex.withLock {
                    if (mutable.value.account == account) {
                        try {
                            loadAuxiliaryCache(account)
                        } catch (_: Exception) {
                            mutable.value = mutable.value.copy(
                                message = "Some saved library or wellness records could not be loaded. Original local data was retained."
                            )
                        }
                    }
                }
                delay(1200L)
                while (true) {
                    // Route/map work takes precedence over background history.
                    // Do not decode or publish more pages while a detail is open.
                    if (activityDetailVisible) {
                        delay(250L)
                        continue
                    }
                    val more = mutex.withLock {
                        if (mutable.value.account != account) return@withLock false
                        if (activityDetailVisible) return@withLock true
                        val after = nextHistoryCursor ?: return@withLock false
                        val started = SystemClock.elapsedRealtime()
                        val page = try {
                            readHistoryPage(account,after)
                        } catch (_: SQLiteException) {
                            // Existing cached rows stay available. Do not
                            // repeat a failed query in an unbounded loop.
                            mutable.value = mutable.value.copy(
                                historyFullyLoaded = false, historyPageFailed = true,
                                message = "Older activity history could not finish loading. Your saved records remain on this device."
                            )
                            return@withLock false
                        }
                        val byId = mutable.value.activities.associateBy { it.id }.toMutableMap()
                        page.records.forEach { byId[it.id] = it }
                        nextHistoryCursor = page.next
                        mutable.value = mutable.value.copy(
                            activities = byId.values.sortedWith(
                                compareByDescending<ActivityRecord> { it.startedAt }
                                    .thenByDescending { it.id }
                            ),
                            historyFullyLoaded = !page.hasMore,
                            historyPageFailed = false
                        )
                        traceCache("older_history_page",started,page.records.size)
                        page.hasMore
                    }
                    if (!more) break
                    // Leave an explicit scheduling window for the current
                    // Activity Detail / Today GPS load ahead of older pages.
                    delay(180L)
                }
            }
        }
    }

    /**
     * Cache-first loading. Publish locally saved activity summaries and plans
     * before decoding secondary collections. Large FIT streams stay in SQLite
     * until an Activity Detail destination requests one specific record.
     */
    private fun loadCache(
        account: String,
        publishEssentials: Boolean = false,
        recentPage: HistoryPage? = null
    ) {
        val started = SystemClock.elapsedRealtime()
        val records = recentPage?.records?.toMutableList() ?: mutableListOf<ActivityRecord>()
        var rejected = recentPage?.rejected ?: 0
        if (recentPage == null) store.readableDatabase.query(
            "records", arrayOf("payload"), "account=? AND kind=?",
            arrayOf(account,"activity"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val record = ActivityRecord.parse(cursor.getString(0),account)
                    if (ActiveActivitySources.accepts(record.source)) records += record
                } catch (_: Exception) {
                    rejected++
                } // Original unsupported rows stay untouched in SQLite.
            }
        }
        traceCache("activity_summaries", started, records.size)
        val allowedIds = records.mapTo(HashSet<String>()) { it.id }
        val cachedDetailIds = HashSet<String>()
        // Read IDs ONLY: don't materialize, parse or copy recorded FIT streams.
        store.readableDatabase.query(
            "records", arrayOf("record_id"), "account=? AND kind=?",
            arrayOf(account,"activity_detail"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (recentPage != null || id in allowedIds) cachedDetailIds += id
            }
        }
        val preservedDetails = mutable.value.details.filterKeys {
            it in allowedIds && it in cachedDetailIds
        }
        val plans = mutableListOf<PlanSnapshot>()
        var unsupportedPlans = 0
        store.readableDatabase.query(
            "records", arrayOf("payload"), "account=? AND kind=?",
            arrayOf(account,"plan"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { PlanSnapshot.decode(JSONObject(cursor.getString(0)),account) }
                    .onSuccess { plans += it }
                    .onFailure { unsupportedPlans++ }
            }
        }
        val marker = store.cursor(account,"activity")
        val last = marker?.let {
            runCatching { JSONObject(it).getLong("completedAt") }.getOrNull()
        }
        val activities = records.sortedWith(
            compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id }
        )
        val sortedPlans = plans.sortedByDescending { it.createdAt }
        nextHistoryCursor = recentPage?.next
        val historyFullyLoaded = recentPage?.hasMore != true
        val warning = when {
            unsupportedPlans > 0 ->
                "$unsupportedPlans saved plans require a supported contract and were retained for review."
            rejected > 0 ->
                "$rejected saved activities contain unsupported data and were retained for review."
            else -> mutable.value.message
        }
        if (publishEssentials) {
            // The Today/Training/Activity roots can render before less urgent
            // wellness, exercise templates and local workout drafts are decoded.
            mutable.value = mutable.value.copy(
                activities=activities, plans=sortedPlans, details=preservedDetails,
                cachedDetailIds=cachedDetailIds, lastSync=last,
                initializing=false, message=warning,
                historyFullyLoaded=historyFullyLoaded,
                historyPageFailed=false
            )
            traceCache("first_cached_content",started,activities.size)
        } else {
            mutable.value = mutable.value.copy(
                activities=activities, plans=sortedPlans,
                details=preservedDetails, cachedDetailIds=cachedDetailIds,
                lastSync=last, message=warning,
                historyFullyLoaded=historyFullyLoaded,
                historyPageFailed=false
            )
        }
        if (recentPage != null) return
        loadAuxiliaryCache(account)
        traceCache("all_cache_metadata",started,activities.size)
    }

    /** Secondary local collections are not on the critical path to GPS detail. */
    private fun loadAuxiliaryCache(account: String) {
        val plans = mutable.value.plans
        val wellness = mutableListOf<WellnessRecord>()
        store.readableDatabase.query(
            "records",arrayOf("payload"),"account=? AND kind=?",
            arrayOf(account,"wellness"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching {
                WellnessRecord.parse(JSONObject(cursor.getString(0)),account)?.let {
                    if (ActiveActivitySources.accepts(it.source)) wellness += it
                }
            }
        }
        val shelf = store.readableDatabase.query(
            "records",arrayOf("payload"),
            "account=? AND kind=? AND record_id=?",
            arrayOf(account,"exercise","library-shelf-v1"),null,null,null
        ).use { cursor ->
            if (cursor.moveToFirst()) LibraryShelf.decode(
                JSONObject(cursor.getString(0)),account
            ) else LibraryShelf()
        }
        val templates = mutableListOf<SessionTemplate>()
        store.readableDatabase.query(
            "records",arrayOf("payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(account,"exercise","session-template-%"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching {
                SessionTemplate.decode(JSONObject(cursor.getString(0)),account)
            }.onSuccess { templates += it }
        }
        val drafts = mutableMapOf<Pair<String,String>,WorkoutEntryDraft>()
        store.readableDatabase.query(
            "records",arrayOf("record_id","payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(account,"workout","entry-draft-%"),null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) runCatching {
                WorkoutEntryDraft.decode(JSONObject(cursor.getString(1)),account).also {
                    require(it.recordId == cursor.getString(0))
                }
            }.onSuccess { if (validDraft(it,plans)) drafts[it.key] = it }
        }
        mutable.value = mutable.value.copy(
            wellness=wellness, shelf=shelf, templates=templates,
            workoutDrafts=drafts
        )
    }

    /**
     * A restored Activity Detail destination may point to an older activity
     * outside the initial recent page. Fetch only that account-owned summary,
     * so process recreation cannot temporarily label a saved activity missing.
     */
    internal suspend fun loadCachedSummary(id: String): Boolean =
        withContext(Dispatchers.IO) { mutex.withLock {
            val account = mutable.value.account ?: return@withLock false
            if (mutable.value.activities.any { it.id == id }) return@withLock true
            val row = store.get(account,"activity",id) ?: return@withLock false
            val parsed = runCatching {
                ActivityRecord.parse(row.payload,account)
            }.getOrNull() ?: return@withLock false
            if (!ActiveActivitySources.accepts(parsed.source)) return@withLock false
            mutable.value = mutable.value.copy(
                activities=(mutable.value.activities + parsed).sortedWith(
                    compareByDescending<ActivityRecord> { it.startedAt }
                        .thenByDescending { it.id }
                )
            )
            true
        } }

    /** Cache hits open only the small image. Misses parse one local detail off the UI thread. */
    internal suspend fun loadGpsThumbnail(id: String): Unit = withContext(Dispatchers.IO) {
        val account = mutable.value.account ?: return@withContext
        try {
        val started = SystemClock.elapsedRealtime()
        suspend fun lookup(): Boolean = mutex.withLock {
            if (mutable.value.account != account || id !in mutable.value.cachedDetailIds) return@withLock true
            if (id in mutable.value.gpsThumbnails) return@withLock true
            if (thumbnails.hasNoRoute(account, id)) return@withLock true
            val image = thumbnails.read(account, id) ?: return@withLock false
            mutable.value = mutable.value.copy(gpsThumbnails = mutable.value.gpsThumbnails + (id to image))
            true
        }
        if (lookup()) { traceCache("thumbnail_hit", started, 1); return@withContext }
        traceCache("thumbnail_miss", started, 1)
        thumbnailMutex.withLock {
            if (lookup()) return@withLock
            while (activityDetailVisible) delay(250)
            val saved = mutex.withLock {
                if (mutable.value.account == account) store.get(account, "activity_detail", id)?.payload else null
            } ?: return@withLock
            val detail = runCatching { ActivityDetail.parse(JSONObject(saved), account, id) }.getOrNull() ?: return@withLock
            val route = RecordedGpsRoute.from(detail.samples, detail.pauseIntervals)
            val generation = SystemClock.elapsedRealtime()
            val fingerprint = thumbnails.fingerprint(route)
            val encoded = thumbnails.render(route)
            mutex.withLock save@ {
                if (mutable.value.account != account || store.get(account, "activity_detail", id)?.payload != saved) return@save
                thumbnails.save(account, id, fingerprint, encoded)
                val bitmap = thumbnails.read(account, id)
                if (bitmap != null) {
                    mutable.value = mutable.value.copy(gpsThumbnails = mutable.value.gpsThumbnails + (id to bitmap))
                }
            }
            traceCache("thumbnail_generation", generation, 1)
        }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The original recording remains available for a later retry.
        }
    }

    private fun prepareThumbnailWrite(account: String, id: String, detail: ActivityDetail) {
        val route = RecordedGpsRoute.from(detail.samples, detail.pauseIntervals)
        if (thumbnails.storedFingerprint(account, id) != thumbnails.fingerprint(route)) {
            invalidateThumbnail(account, id)
        }
    }

    private fun invalidateThumbnail(account: String, id: String) {
        thumbnails.remove(account, id)
        mutable.value = mutable.value.copy(gpsThumbnails = mutable.value.gpsThumbnails - id)
    }

    /**
     * Open only the requested cached detail; never call a provider on the
     * cached-data path. The original SQLite payload is preserved on error.
     */
    internal suspend fun loadCachedDetail(id: String): Boolean =
        withContext(Dispatchers.IO) { mutex.withLock {
            val started = SystemClock.elapsedRealtime()
            val account = mutable.value.account ?: return@withLock false
            if (mutable.value.activities.none { it.id == id }) return@withLock false
            if (mutable.value.details.containsKey(id)) return@withLock true
            if (id !in mutable.value.cachedDetailIds) return@withLock false
            val saved = store.get(account,"activity_detail",id) ?: run {
                // SQLite is authoritative if a detail was removed by an
                // explicit operation after the lightweight ID query.
                mutable.value = mutable.value.copy(
                    cachedDetailIds=mutable.value.cachedDetailIds - id
                )
                return@withLock false
            }
            val parsed = try {
                ActivityDetail.parse(JSONObject(saved.payload),account,id)
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(
                    message="The saved activity details could not be opened. The original local record was retained."
                )
                return@withLock false
            }
            retainHotDetail(id,parsed)
            traceCache("on_demand_activity_detail",started,parsed.samples.size)
            true
        } }

    /**
     * Reconcile only affected activity summaries after a durable remote page.
     * Read back the SQLite winner so pending local edits/conflicts are never
     * overwritten by the display cache. No detail streams or plans are parsed.
     */
    private fun publishActivityPage(
        account: String,
        rows: List<TrainingStore.RemoteRecord>,
        completedMarker: String? = null
    ) {
        val start = SystemClock.elapsedRealtime()
        val active = mutable.value.activities.associateBy { it.id }.toMutableMap()
        rows.forEach { row ->
            val stored = store.get(account,"activity",row.id)
            val parsed = stored?.let {
                runCatching { ActivityRecord.parse(it.payload,account) }.getOrNull()
            }
            if (parsed != null && ActiveActivitySources.accepts(parsed.source)) {
                active[row.id] = parsed
            } else {
                active.remove(row.id)
            }
        }
        val currentIds = active.keys
        val completed = completedMarker?.let {
            runCatching { JSONObject(it).getLong("completedAt") }.getOrNull()
        }
        mutable.value = mutable.value.copy(
            activities=active.values.sortedWith(
                compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id }
            ),
            // Keep the disk detail index for older history pages still
            // hydrating; the full SQLite cache remains account-scoped.
            cachedDetailIds=mutable.value.cachedDetailIds,
            details=mutable.value.details.filterKeys { it in currentIds },
            lastSync=completed ?: mutable.value.lastSync
        )
        traceCache("incremental_activity_sync",start,rows.size)
    }

    suspend fun restorePlans(raw:String)=operation("Restoring reviewed plan backup…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in to the backup's original account before restoring.")
        val additions=PlanBackup.additions(PlanBackup.read(raw,account),mutable.value.plans)
        val db=store.writableDatabase
        db.beginTransaction()
        try {
            // Some stored plan formats intentionally remain on disk when this build cannot
            // decode them. Such IDs are absent from mutable.value.plans and must never
            // be overwritten by a backup with the same ID.
            additions.forEach { p ->
                db.query("records",arrayOf("record_id"),"account=? AND kind=? AND record_id=?",
                    arrayOf(account,"plan",p.id),null,null,null).use { cursor ->
                    if(cursor.moveToFirst())throw PlanConstraint(
                        "A saved plan already uses this ID but could not be restored into the current view. " +
                            "Its original record was retained; no backup records were changed."
                    )
                }
            }
            additions.forEach { p -> store.enqueue(account,"plan",p.id,java.util.UUID.randomUUID().toString(),p.encode().toString(),null,System.currentTimeMillis()) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        loadCache(account)
        mutable.value=mutable.value.copy(message="Restored ${additions.size} new plans. Existing plan IDs and all saved records were kept unchanged.")
    }
    suspend fun changeShelf(id:String,collection:String?=null,remove:Boolean=false)=operation("Saving your library…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in once to keep your saved library isolated to your account; it then works offline.")
        val shelf=mutable.value.shelf.change(id,collection,remove)
        store.enqueue(account,"exercise","library-shelf-v1",java.util.UUID.randomUUID().toString(),shelf.encode(account).toString(),null,System.currentTimeMillis())
        mutable.value=mutable.value.copy(shelf=shelf)
    }
    suspend fun saveTemplate(template:SessionTemplate)=operation("Saving your session template…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in once to keep templates private to your account.")
        template.validate()
        if(mutable.value.templates.size>=50 && mutable.value.templates.none { it.id==template.id })throw PlanConstraint("This account has fifty saved templates. Reuse an existing template.")
        store.enqueue(account,"exercise","session-template-${template.id}",java.util.UUID.randomUUID().toString(),template.encode(account).toString(),null,System.currentTimeMillis())
        mutable.value=mutable.value.copy(templates=mutable.value.templates.filterNot { it.id==template.id }+template)
    }
    suspend fun prepareSession(template:SessionTemplate,request:PlanRequest,date:java.time.LocalDate)=operation("Preparing your reviewed session…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in once to create account-isolated workouts.")
        savePlan(template.draft(account,request,date))
    }
    private fun validDraft(draft:WorkoutEntryDraft,plans:List<PlanSnapshot>):Boolean {
        val p=plans.find { it.id==draft.planId && it.owner==draft.owner } ?: return false
        val s=p.sessions.find { it.id==draft.sessionId } ?: return false
        return Engine1Engines.canManage(p) && p.status in setOf("approved","paused") && s.status in setOf("planned","in_progress") && s.logRevision==draft.logRevision
    }
    /** Local input staging is deliberately not a confirmed workout or a cloud mutation. */
    suspend fun stageWorkoutDraft(draft:WorkoutEntryDraft):Boolean=withContext(Dispatchers.IO) { mutex.withLock {
        val account=mutable.value.account
        if(account==null || draft.owner!=account || !validDraft(draft,mutable.value.plans))return@withLock false
        try {
            draft.validate()
            val previous=store.get(account,"workout",draft.recordId)
            val saved=if(previous!=null)try { WorkoutEntryDraft.decode(JSONObject(previous.payload),account) }catch(_:Exception){throw PlanConstraint("Existing entry data requires a supported version and was retained. Use explicit Save progress for your current entries.")}else null
            if(saved!=null && saved.logRevision==draft.logRevision && draft.editedAt<saved.editedAt)return@withLock false
            if(saved==draft)return@withLock true
            store.applyRemotePage(account,"workout",listOf(TrainingStore.RemoteRecord(draft.recordId,draft.encode().toString(),null)),null,System.currentTimeMillis())
            mutable.value=mutable.value.copy(workoutDrafts=mutable.value.workoutDrafts+(draft.key to draft))
            true
        } catch(e:Exception) {
            mutable.value=mutable.value.copy(message=if(e is PlanConstraint)e.message else "Entry autosave could not finish. Existing records are retained; use Save progress and retry.")
            false
        }
    } }
    private fun savePlan(plan:PlanSnapshot) {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in once to keep plans isolated to your account. Planning and logging then work offline.")
        if(plan.owner!=account)throw PlanConstraint("The plan belongs to a different account.")
        // Existing transactional local-record/outbox contract. No cloud replay is claimed here.
        val encoded=plan.encode()
        require(encoded.getString("id")==plan.id && encoded.getString("user_id")==account)
        store.enqueue(account,"plan",plan.id,java.util.UUID.randomUUID().toString(),encoded.toString(),null,System.currentTimeMillis())
        val all=(mutable.value.plans.filterNot { it.id==plan.id }+plan).sortedByDescending { it.createdAt }
        mutable.value=mutable.value.copy(plans=all,workoutDrafts=mutable.value.workoutDrafts.filterValues { validDraft(it,all) })
    }
    suspend fun generatePlan(request:PlanRequest,engine:PlanGenerationEngine?=null) = operation("Building your plan…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in once to create an account-isolated plan. A watch is not required; planning works offline.")
        savePlan((engine ?: Engine1Engines.resolve(request)).generate(request,account))
    }
    suspend fun changePlan(id:String,revision:Int,action:(PlanSnapshot)->PlanSnapshot) = operation("Saving your plan…") {
        val plan=mutable.value.plans.find { it.id==id } ?: throw PlanConstraint("This plan is unavailable for the current account.")
        if(!Engine1Engines.canManage(plan))throw PlanConstraint("This saved plan needs its compatible engine before editing. Its original snapshot is retained.")
        if(plan.revision!=revision)throw PlanConstraint("The saved plan changed. Reopen it before editing.")
        val updated=action(plan)
        require(updated.id==plan.id && updated.owner==plan.owner && updated.engineVersion==plan.engineVersion && updated.policyId==plan.policyId)
        if(updated!=plan)savePlan(updated.copy(revision=plan.revision+1))
    }
    suspend fun swapExercise(id:String,revision:Int,sessionId:String,exerciseId:String)=changePlan(id,revision) { PlanOperations.swapExercise(it,sessionId,exerciseId) }
    suspend fun linkActivity(id:String,revision:Int,sessionId:String,activityId:String?)=changePlan(id,revision) { plan ->
        if(activityId!=null) {
            val activity=mutable.value.activities.find { it.id==activityId } ?: throw PlanConstraint("This source activity is not cached for your account.")
            if(!DashboardSummary.isRun(activity))throw PlanConstraint("Only a recorded run can be linked to a planned run.")
            if(mutable.value.plans.any { p -> p.sessions.any { s -> s.linkedActivityId==activityId && (p.id!=id || s.id!=sessionId) } })throw PlanConstraint("This source run is already linked to another workout. Unlink it there first.")
        }
        PlanOperations.linkActivity(plan,sessionId,activityId)
    }
    suspend fun approvePlan(id:String,revision:Int)=changePlan(id,revision) { PlanOperations.approve(it,mutable.value.plans) }
    suspend fun pausePlan(id:String,revision:Int)=changePlan(id,revision) { it.copy(status="paused") }
    suspend fun archivePlan(id:String,revision:Int)=changePlan(id,revision) { it.copy(status="archived") }
    suspend fun moveSession(id:String,revision:Int,sessionId:String,date:java.time.LocalDate)=changePlan(id,revision) { plan->PlanOperations.move(plan,sessionId,date,mutable.value.plans) }
    suspend fun setSessionLock(id:String,revision:Int,sessionId:String,locked:Boolean)=changePlan(id,revision) { plan->PlanOperations.setSessionLock(plan,sessionId,locked) }
    fun previewAvailabilityChange(id:String,newDays:Set<Int>):Engine15Availability.Impact? {
        val plan=mutable.value.plans.find { it.id==id && it.owner==mutable.value.account }
            ?: return null
        return Engine15Availability.preview(plan,newDays,mutable.value.plans)
    }
    suspend fun updateAvailability(id:String,revision:Int,days:Set<Int>,expectedImpact:Engine15Availability.Impact?=null)=changePlan(id,revision) { plan->
        val plans=mutable.value.plans
        val today=java.time.LocalDate.now()
        if(expectedImpact!=null && Engine15Availability.preview(plan,days,plans,today)!=expectedImpact)
            throw PlanConstraint("The schedule changed. Preview availability again before applying.")
        PlanOperations.updateAvailability(plan,days,plans,today)
    }
    suspend fun setLongRunDay(id:String,revision:Int,day:Int)=changePlan(id,revision) { plan->PlanOperations.setLongRunDay(plan,day) }
    suspend fun setQualityDays(id:String,revision:Int,days:Set<Int>)=changePlan(id,revision) { plan->PlanOperations.setQualityDays(plan,days) }
    suspend fun recordWorkout(id:String,revision:Int,sessionId:String,log:WorkoutLog,complete:Boolean)=changePlan(id,revision) { PlanOperations.record(it,sessionId,log,complete) }
    suspend fun markMissed(id:String,revision:Int,sessionId:String,reason:String,skipped:Boolean)=changePlan(id,revision) { PlanOperations.missed(it,sessionId,reason,skipped) }
    suspend fun acceptSuggestion(id:String,revision:Int,sessionId:String)=changePlan(id,revision) { plan ->
        Engine15Feedback.requireCurrentPendingAcceptance(
            plan,sessionId,java.time.LocalDate.now(),mutable.value.plans
        )
        PlanOperations.acceptSuggestion(plan,sessionId)
    }

    /**
     * Pure Engine 1.5 preview: no persistence, plan mutation or network access.
     * Consumers must pass the same plan revision to stage/dismiss.
     */
    fun previewEngine15Adjustments(
        id:String, asOf:java.time.LocalDate=java.time.LocalDate.now()
    ):List<Engine15Feedback.Proposal> {
        val p=mutable.value.plans.find { it.id==id && it.owner==mutable.value.account }
            ?: return emptyList()
        return Engine15Feedback.preview(p,asOf,mutable.value.plans)
    }
    suspend fun stageEngine15Adjustment(
        id:String,revision:Int,proposal:Engine15Feedback.Proposal,
        asOf:java.time.LocalDate=java.time.LocalDate.now()
    )=changePlan(id,revision) { p ->
        Engine15Feedback.stage(p,proposal,asOf,mutable.value.plans)
    }
    suspend fun dismissEngine15Adjustment(
        id:String,revision:Int,proposal:Engine15Feedback.Proposal,
        asOf:java.time.LocalDate=java.time.LocalDate.now(),
        now:java.time.Instant=java.time.Instant.now()
    )=changePlan(id,revision) { p ->
        Engine15Feedback.dismiss(p,proposal,asOf,now,mutable.value.plans)
    }
    suspend fun dismissStagedEngine15Adjustment(
        id:String,revision:Int,sessionId:String,
        now:java.time.Instant=java.time.Instant.now()
    )=changePlan(id,revision) { p ->
        Engine15Feedback.dismissStaged(p,sessionId,now)
    }

    suspend fun saveFeedback(id:String,revision:Int,category:String,text:String,sessionId:String?=null)=changePlan(id,revision) { PlanOperations.feedback(it,category,text,sessionId) }
    suspend fun refreshWellness() = operation("Refreshing wellness records…") {
        val account=requireNotNull(mutable.value.account)
        val cloud=session().account
        val raw=authenticated { gateway.wellness(it) }
        val seen=HashSet<String>(); val rows=ArrayList<TrainingStore.RemoteRecord>()
        for(i in 0 until raw.length()) {
            val remote=raw.getJSONObject(i)
            require(remote.getString("user_id") == cloud) { "Cloud wellness owner mismatch" }
            val row=JSONObject(remote.toString()).put("user_id",account)
            val record=WellnessRecord.parse(row,account) ?: continue
            if(!ActiveActivitySources.accepts(record.source))continue
            require(seen.add(record.id))
            rows+=TrainingStore.RemoteRecord(record.id,row.toString(),null)
        }
        store.applyRemotePage(account,"wellness",rows,null,System.currentTimeMillis())
        loadCache(account)
    }
    suspend fun loadDetail(id: String) = operation("Loading recorded streams…") {
        val account = requireNotNull(mutable.value.account)
        require(mutable.value.activities.any { it.id == id })
        val cloud = session().account
        val remote = authenticated { gateway.detail(it,id) }
        require(remote.getString("user_id") == cloud) { "Cloud detail owner mismatch" }
        val raw = JSONObject(remote.toString()).put("user_id", account)
        val parsed = ActivityDetail.parse(raw,account,id)
        prepareThumbnailWrite(account, id, parsed)
        store.applyRemotePage(account,"activity_detail",listOf(TrainingStore.RemoteRecord(id,raw.toString(),null)),null,System.currentTimeMillis())
        retainHotDetail(id,parsed)
        scope.launch { loadGpsThumbnail(id) }
    }
    private suspend fun operation(label: String, action: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutable.value = mutable.value.copy(busy = true, message = null, task = label)
            try { action(); true }
            catch (e: PlanConstraint) { mutable.value = mutable.value.copy(message=e.message); false }
            catch (e: GarminException) {
                val error=GarminConnectionStatus.fromError(e)
                mutable.value=mutable.value.copy(message=error.heading()+": "+error.error.orEmpty())
                false
            }
            catch (e: ServiceFailure) {
                mutable.value = mutable.value.copy(message = e.message, needsSignIn = e.requiresSignIn || mutable.value.needsSignIn); false
            } catch (_: java.io.IOException) {
                mutable.value = mutable.value.copy(message = "Connection or secure storage is unavailable. Saved activities are retained; retry when connected."); false
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = "This operation could not be verified. Existing records were retained. Please retry."); false
            } finally { mutable.value = mutable.value.copy(busy = false, task = null) }
        }
    }
    suspend fun signUp(name: String, email: String, password: String) = operation("Creating account…") {
        require(name.isNotBlank() && email.isNotBlank() && password.isNotEmpty())
        gateway.signUp(name, email, password)
        // Signup does not activate an account or persist an unverified token.
        mutable.value = mutable.value.copy(
            message = "Account request submitted. Verify your email if required, then sign in."
        )
    }
    suspend fun requestPasswordReset(email: String) = operation("Requesting password reset…") {
        require(email.isNotBlank())
        gateway.requestPasswordReset(email)
        // Avoid account enumeration: the same confirmation is shown to everyone.
        mutable.value = mutable.value.copy(
            message = "If this address has an account, reset instructions will be sent by email."
        )
    }
    /**
     * Online authentication only connects cloud services. Do not switch the
     * active local profile or fetch remote history over a local workout.
     */
    suspend fun signIn(email: String, password: String) = operation("Connecting cloud backup…") {
        require(email.isNotBlank() && password.isNotEmpty())
        val session = try {
            gateway.signIn(email, password).also { gateway.verify(it) }
        } catch(e: ServiceFailure) {
            throw ServiceFailure(e.status, e.message ?: "Sign-in was not accepted", requiresSignIn = false)
        }
        val local = requireNotNull(mutable.value.account)
        val bound = preferences.getString("profile_cloud_binding", null)
            ?: local.takeUnless { it.startsWith("local-") }
        if (bound != null && bound != session.account) {
            throw ServiceFailure(
                403, "This local profile belongs to a different cloud account. Local data was not shared.",
                requiresSignIn = false
            )
        }
        vault.save(session)
        // Binding prevents switching accounts from silently uploading another
        // account's offline workouts. These preferences contain no tokens.
        check(preferences.edit()
            .putString("cloud_account", session.account)
            .putString("profile_cloud_binding", session.account)
            .commit())
        mutable.value = mutable.value.copy(
            cloudAccount = session.account, needsSignIn = false,
            loginGeneration = mutable.value.loginGeneration + 1,
            message = "Cloud account connected. Your local training remains unchanged."
        )
    }
    private fun session(): NativeSession {
        val cloud = mutable.value.cloudAccount
            ?: throw ServiceFailure(401, "Sign in to use cloud backup.", requiresSignIn = true)
        val saved = try { vault.read(cloud) } catch (_: java.io.IOException) {
            throw ServiceFailure(401, "Cloud session unavailable. Local training remains accessible.", requiresSignIn = true)
        } ?: throw ServiceFailure(401, "Sign in to use cloud backup.", requiresSignIn = true)
        return if(saved.expiresAtSeconds <= System.currentTimeMillis()/1000 + 90) renew(saved) else saved
    }
    private fun renew(saved: NativeSession): NativeSession {
        val fresh = gateway.refresh(saved)
        if(fresh.account != saved.account || fresh.account != mutable.value.cloudAccount)
            throw ServiceFailure(401,"Cloud session owner changed. Local data was retained.",requiresSignIn=true)
        vault.save(fresh)
        return fresh
    }
    private fun <T> authenticated(action: (NativeSession) -> T): T {
        val current = session()
        return try { action(current) } catch(e: ServiceFailure) {
            if(e.status != 401) throw e
            // Retry exactly once after an authentication rejection, not after timeouts/5xx.
            action(renew(current))
        }
    }
    private fun fetchActivities() {
        val account = requireNotNull(mutable.value.account)
        val cloud = session().account
        val started = SystemClock.elapsedRealtime()
        var before: ActivityRecord? = null
        var received = 0
        val seen = HashSet<String>()
        try {
        while(true) {
            val page = authenticated { gateway.activities(it, before) }
            require(page.length() <= 200)
            val rows = ArrayList<TrainingStore.RemoteRecord>()
            var last: ActivityRecord? = null
            for(i in 0 until page.length()) {
                val remote = page.getJSONObject(i)
                require(remote.getString("user_id") == cloud) { "Cloud activity owner mismatch" }
                val raw = JSONObject(remote.toString()).put("user_id", account)
                val parsed = ActivityRecord.parse(raw.toString(), account)
                require((last ?: before)?.let { parsed.isBefore(it) } != false) { "History page is not in stable cursor order" }
                require(seen.add(parsed.id)) { "Repeated page; synchronization stopped safely" }
                // Advance the history cursor across retired-source rows,
                // but never re-import new Intervals.icu activities.
                if(ActiveActivitySources.accepts(parsed.source))
                    rows += TrainingStore.RemoteRecord(parsed.id, raw.toString(), null)
                last = parsed
            }
            val completed = page.length() == 0 // A short page can reflect a lower server page limit.
            val marker = if(completed) JSONObject().put("completedAt", System.currentTimeMillis()).toString() else null
            store.applyRemotePage(account, "activity", rows, marker, System.currentTimeMillis())
            received += page.length()
            mutable.value = mutable.value.copy(task = "Checking history · $received activities")
            publishActivityPage(account,rows,marker) // SQLite page committed first.
            if(completed) break
            require(last != null && last != before)
            before = last
        }
        // Shared by manual refresh, sign-in, provider sync and background work.
        // Success updates the cache timestamp in place; banners are for actionable problems.
        mutable.value = mutable.value.copy(needsSignIn = false)
        } finally {
            traceCache("history_refresh_total",started,received)
        }
    }
    suspend fun refresh() = operation("Refreshing history…") { fetchActivities() }
    /**
     * Explicit, foreground Garmin source sync. Source summaries are cached under
     * provider-derived IDs for offline Activity browsing. No backend mutation,
     * automatic provider merge, training-plan change, or invented measurements.
     * The repository lock preserves sign-out and account isolation throughout.
     */
    internal suspend fun syncGarmin(
        source: GarminPersonalSource,
        pageLimit: Int = 100,
        detailLimit: Int = 24,
        manualRetry: Boolean = false
    ): GarminConnectionStatus = withContext(Dispatchers.IO) {
        require(pageLimit in 1..100 && detailLimit in 0..24)
        mutex.withLock {
            val account=mutable.value.account
                ?: throw GarminAuthenticationException("Sign in to StrideLab before importing Garmin activity")
            var cursor:String?=null
            var pages=0
            var imported=0
            val seen=HashSet<String>()
            var completedDetails = 0
            var failedDetails = 0
            var interruption: String? = null
            var fitAttempts = 0
            var recoverableFitFailures = false
            val attemptedFitIds = HashSet<String>()

            // Fetch full recordings as soon as their summary page is saved.
            // The newest eligible activities win each bounded FIT budget;
            // older missing recordings are picked up on subsequent syncs.
            fun importPendingFit() {
                if (fitAttempts >= detailLimit || interruption != null) return
                val pending = mutable.value.activities.asSequence()
                    .filter { activity ->
                        activity.source == "garmin" && DashboardSummary.isRun(activity) &&
                            activity.id.startsWith("garmin-") &&
                            activity.id !in mutable.value.cachedDetailIds &&
                            activity.id !in attemptedFitIds &&
                            (manualRetry || garminSyncPreferences.readyForDetail(account, activity.id))
                    }
                    .sortedWith(
                        compareBy<ActivityRecord> {
                            if (garminSyncPreferences.readyForDetail(account, it.id)) 0 else 1
                        }.thenByDescending { it.startedAt }
                            .thenByDescending { it.id }
                    )
                    .take(detailLimit - fitAttempts)
                    .toList()
                for (activity in pending) {
                    attemptedFitIds.add(activity.id)
                    fitAttempts++
                    try {
                        cacheGarminRecording(account, activity, source)
                        garminSyncPreferences.clearDetailFailure(account, activity.id)
                        completedDetails++
                    } catch (error: GarminAuthenticationException) {
                        failedDetails++
                        interruption = GarminConnectionStatus.fromError(error).error
                        break
                    } catch (error: GarminRateLimitException) {
                        failedDetails++
                        interruption = GarminConnectionStatus.fromError(error).error
                        break
                    } catch (error: GarminNetworkException) {
                        failedDetails++
                        interruption = GarminConnectionStatus.fromError(error).error
                        break
                    } catch (error: GarminServiceException) {
                        failedDetails++
                        interruption = GarminConnectionStatus.fromError(error).error
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Back off for one day on invalid FIT/source failures.
                        // The next healthy, newer recording must not be starved.
                        garminSyncPreferences.noteDetailFailure(account, activity.id)
                        failedDetails++
                        recoverableFitFailures = true
                    }
                }
            }
            do {
                if(pages>=pageLimit) {
                    if (pageLimit == 100) throw GarminProtocolException(
                        "Garmin history exceeds the bounded 10,000-record sync. Earlier pages are saved; contact support for the remaining range.")
                    break // Bounded automatic check; manual sync still covers all history.
                }
                val page=source.activities(account,cursor,100)
                if(page.accountId!=account)throw GarminProtocolException(
                    "Garmin activity page belongs to a different account")
                val rows=ArrayList<TrainingStore.RemoteRecord>()
                for(input in page.records) {
                    if(input.accountId!=account || input.provider!="garmin")
                        throw GarminProtocolException("Garmin returned incompatible source identity")
                    val raw=try { GarminHistoryImport.payload(input) }
                        catch(error:Exception) { throw GarminProtocolException(
                            "Garmin returned an activity that cannot be saved safely") }
                    val id=raw.getString("id")
                    if(!seen.add(id))throw GarminProtocolException(
                        "Garmin returned a duplicate activity across pages")
                    val old=store.get(account,"activity",id)
                    if(old!=null) {
                        val existing=JSONObject(old.payload)
                        if(existing.optString("source")!="garmin" ||
                            existing.optString("external_id")!=input.externalId)
                            throw GarminProtocolException("Garmin activity ID conflicts with saved history")
                        continue // Source-owned original remains unchanged on repeated sync.
                    }
                    rows+=TrainingStore.RemoteRecord(id,raw.toString(),null)
                }
                store.applyRemotePage(account,"activity",rows,null,System.currentTimeMillis())
                imported+=rows.size
                publishActivityPage(account,rows) // Durable, offline-accessible after each page.
                // Save the latest complete FIT files BEFORE paging through older history.
                importPendingFit()
                pages++
                if (interruption != null) break
                val next=page.nextCursor
                if(next!=null && (next==cursor || next.toIntOrNull()==null))
                    throw GarminProtocolException("Garmin pagination did not advance safely")
                cursor=next
            } while(cursor!=null)
            // Failed recordings remain missing until explicitly re-requested or
            // their one-day backoff expires; manual/on-demand load bypasses it.
            val remaining = mutable.value.activities.count { activity ->
                activity.source == "garmin" && DashboardSummary.isRun(activity) &&
                    activity.id.startsWith("garmin-") &&
                    activity.id !in mutable.value.cachedDetailIds
            }
            if (pageLimit == 100) garminSyncPreferences.acknowledgeManualGarminSync(account)
            GarminConnectionStatus(
                state = "connected",
                lastSuccessfulSync = java.time.Instant.now(),
                importedCount = imported,
                error = interruption ?: if (recoverableFitFailures)
                    "Some Garmin recordings could not be verified or stored. Open a missing activity and tap Load Garmin recording to retry."
                    else null,
                detailsImported = completedDetails,
                detailsRemaining = remaining,
                failedDetails = failedDetails
            )
        } }

    /**
     * WorkManager and app-resume share this due-time claim. Automatic checks
     * intentionally fetch at most 200 latest summaries and 3 FIT files so a
     * background task does not monopolize the device or hit Garmin aggressively.
     * User-initiated manual sync retains its existing full-history behavior.
     */
    internal suspend fun automaticGarminAndBackup(foreground: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val ready = state.first { !it.initializing }
            val account = ready.account ?: return@withContext true
            // Local Garmin synchronization continues when StrideLab cloud
            // authentication is absent or expired.
            var success = true
            val interval = if (foreground) 30L * 60_000 else 6L * 60 * 60_000
            if (garminSyncPreferences.automatic(account) &&
                garminSyncPreferences.due(account, "garmin", interval)) {
                val source = GarminPersonalSource(
                    account, GarminCredentialVault(appContext), GarminPersonalClient())
                val connected = runCatching { source.status().state == "connected" }
                    .getOrDefault(false)
                // Do not consume the automatic-check window while Garmin is
                // disconnected; a newly linked account should remain eligible.
                if (connected &&
                    garminSyncPreferences.claim(account, "garmin", interval)) {
                    val syncStarted = SystemClock.elapsedRealtime()
                    try {
                        val result = syncGarmin(source, pageLimit = 2, detailLimit = 3)
                        if (result.error != null) success = false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The original offline cache stays intact. An app-resume
                        // check never overwrites foreground error messages.
                        success = false
                    } finally {
                        traceCache("automatic_garmin_sync",syncStarted,0)
                    }
                }
            }
            if (mutable.value.cloudAccount != null && !mutable.value.needsSignIn &&
                garminSyncPreferences.cloudBackup(account) &&
                garminSyncPreferences.claim(account, "cloud", interval)) {
                try {
                    backupGarminCloud(maxRecords = 6)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    success = false
                }
            }
            success
        }

    /** Cloud upload is opt-in. Every uploaded row first exists in local SQLite. */
    internal suspend fun backupGarminCloud(maxRecords: Int = 12): Int =
        withContext(Dispatchers.IO) {
            require(maxRecords in 1..24)
            mutex.withLock {
                val account = requireNotNull(mutable.value.account)
                require(mutable.value.cloudAccount != null && !mutable.value.needsSignIn) {
                    "Sign in to StrideLab only when you want cloud backup"
                }
                require(garminSyncPreferences.cloudBackup(account)) {
                    "Enable Garmin cloud backup before uploading records"
                }
                val candidates = mutableListOf<Triple<String, String, String>>()
                val database = store.readableDatabase
                database.query(
                    "records", arrayOf("kind", "record_id", "payload"),
                    "account=? AND kind IN (?,?)",
                    arrayOf(account, "activity", "activity_detail"),
                    null, null, "kind ASC,record_id ASC"
                ).use { cursor ->
                    while (cursor.moveToNext() && candidates.size < maxRecords) {
                        val kind = cursor.getString(0)
                        val id = cursor.getString(1)
                        val payload = cursor.getString(2)
                        if (kind == "activity") {
                            if (runCatching { JSONObject(payload).optString("source") }
                                    .getOrNull() != "garmin") continue
                        } else {
                            val summary = store.get(account, "activity", id) ?: continue
                            if (runCatching { JSONObject(summary.payload).optString("source") }
                                    .getOrNull() != "garmin") continue
                        }
                        // Large FIT streams stay local; they must not block
                        // backup of other activities or be falsely marked uploaded.
                        if (!GarminCloudBackupGateway.fitsUpload(payload)) continue
                        if (garminSyncPreferences.needsCloudUpload(account, kind, id, payload))
                            candidates.add(Triple(kind, id, payload))
                    }
                }
                var uploaded = 0
                for ((kind, id, payload) in candidates) {
                    // Resolve a verified, renewable StrideLab session for each
                    // request. No Garmin credential ever leaves this device.
                    authenticated { session ->
                        val cloudPayload = remapCloudSnapshotOwner(payload, account, session.account)
                        garminCloud.upload(session, kind, id, cloudPayload)
                    }
                    garminSyncPreferences.acknowledgeCloudUpload(account, kind, id, payload)
                    uploaded++
                }
                uploaded
            }
        }

    /**
     * Explicit, insert-only cloud recovery. Validates owner, kind, IDs and
     * source measurements before adding rows. Never replaces existing records.
     * Bounded pages and durable offsets let later taps resume recovery.
     */
    internal suspend fun restoreGarminCloud(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val account = requireNotNull(mutable.value.account)
            require(mutable.value.cloudAccount != null && !mutable.value.needsSignIn) {
                "Sign in to StrideLab only when you want cloud restore"
            }
            require(garminSyncPreferences.cloudBackup(account)) {
                "Enable Garmin cloud backup before restoring"
            }
            var restored = 0
            for (kind in listOf("activity", "activity_detail")) {
                val size = if (kind == "activity") 25 else 1
                var offset = garminSyncPreferences.restoreOffset(account, kind)
                val batches = if (kind == "activity") 2 else 10
                for (batch in 0 until batches) {
                    val page = authenticated { session ->
                        garminCloud.readPage(session, kind, offset, size)
                    }
                    for (row in page) {
                        if (store.get(account, kind, row.recordId) != null) continue
                        if (kind == "activity_detail" &&
                            store.get(account, "activity", row.recordId) == null) continue
                        // Cloud owner is verified at the gateway; local owner
                        // is set only when persisting into this device profile.
                        val cloud = requireNotNull(mutable.value.cloudAccount)
                        val localPayload = remapCloudSnapshotOwner(row.payload, cloud, account)
                        if (kind == "activity") {
                            ActivityRecord.parse(localPayload, account)
                        } else {
                            ActivityDetail.parse(JSONObject(localPayload), account, row.recordId)
                            invalidateThumbnail(account, row.recordId)
                        }
                        store.applyRemotePage(
                            account, kind,
                            listOf(TrainingStore.RemoteRecord(
                                row.recordId, localPayload, null)),
                            null, System.currentTimeMillis()
                        )
                        restored++
                    }
                    offset += page.size
                    garminSyncPreferences.setRestoreOffset(account, kind, offset)
                    if (page.size < size) {
                        // A completed scan will start at the beginning next
                        // time, allowing newly created cloud rows to be seen.
                        garminSyncPreferences.setRestoreOffset(account, kind, 0)
                        break
                    }
                }
            }
            if (restored > 0) loadCache(account)
            restored
        }
    }

    /**
     * One on-demand Garmin FIT import for a cached source record. The original
     * Garmin summary is never replaced, and Activity Detail is saved offline
     * only after FIT checksum, source, dates, GPS and account have been checked.
     */
    internal suspend fun loadGarminGps(id:String,source:GarminPersonalSource) =
        operation("Loading recorded Garmin activity…") {
            val account = mutable.value.account
                ?: throw GarminAuthenticationException("Sign in before loading Garmin details")
            val activity = mutable.value.activities.firstOrNull {
                it.id == id && it.source == "garmin"
            } ?: throw GarminProtocolException("Garmin source is not saved for this account")
            cacheGarminRecording(account, activity, source, upgradeCadence = true)
        }

    /** Sync keeps the original cache. Explicit on-demand load may only upgrade
     * verified cadence, never replace existing GPS/pace/HR/lap measurements. */
    private fun cacheGarminRecording(
        account: String, activity: ActivityRecord, source: GarminPersonalSource,
        upgradeCadence: Boolean = false
    ): ActivityDetail {
        require(activity.source == "garmin")
        val id = activity.id
        fun publishIfOpened(recorded: ActivityDetail): ActivityDetail {
            if (upgradeCadence || id in mutable.value.details) {
                retainHotDetail(id,recorded)
            } else {
                // WorkManager / due-time Garmin import only warms SQLite.
                // Never fill RAM with unvisited FIT streams.
                mutable.value = mutable.value.copy(
                    cachedDetailIds=mutable.value.cachedDetailIds + id
                )
            }
            return recorded
        }
        val saved = store.get(account, "activity_detail", id)
        val previous = if (saved != null) {
            try {
                ActivityDetail.parse(JSONObject(saved.payload), account, id)
            } catch (_: Exception) {
                throw GarminProtocolException(
                    "Previously saved Garmin recording needs review; the original was retained")
            }
        } else null
        if(previous != null && (!upgradeCadence ||
            previous.samples.any { it.cadenceSpm != null })) {
            return publishIfOpened(previous)
        }
        val original = store.get(account, "activity", id)
            ?: throw GarminProtocolException("Garmin source record is unavailable")
        val metadata = JSONObject(original.payload)
        if (metadata.optString("source") != "garmin" ||
            metadata.optString("id") != id ||
            metadata.optString("user_id") != account)
            throw GarminProtocolException("Garmin source ownership could not be verified")
        val savedExternal = metadata.optString("external_id")
            .takeIf { it.isNotBlank() }
        val savedNumeric = metadata.optLong("garmin_activity_id", 0L)
            .takeIf { it > 0L }
        val legacy = if (savedExternal == null && savedNumeric == null)
            source.resolveLegacySource(account, activity) else null
        val externalId = savedExternal ?: legacy?.externalId
            ?: savedNumeric?.toString()
            ?: throw GarminProtocolException("Garmin source identity is unavailable")
        val numericId = savedNumeric ?: legacy?.numericActivityId
        val archive = source.downloadFitForSource(account, externalId, numericId)
        val bytes = try {
            GarminFitArchive.extractSingleFit(archive)
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin FIT archive could not be safely unpacked")
        }
        val fit = try {
            FitActivityDecoder.decode(bytes, account)
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin FIT file failed validation; existing history was retained")
        }
        val raw = try {
            GarminGpsDetail.payload(account, activity, fit, System.currentTimeMillis())
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin FIT source, timing or distance could not be verified")
        }
        val parsed = try {
            ActivityDetail.parse(raw, account, id)
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin recording samples failed validation")
        }
        val storedPayload = if (saved != null) {
            // Only cadence may be added to an existing cache. If the FIT
            // sample identity or timestamps differ, keep the original intact.
            val oldRaw = JSONObject(saved.payload)
            val merged = try {
                GarminGpsDetail.mergeRecordedCadence(oldRaw, raw, account, id)
            } catch (_: Exception) {
                throw GarminProtocolException(
                    "Garmin FIT samples changed; saved GPS and measurements were retained")
            }
            if (merged == null) {
                val unchanged = requireNotNull(previous)
                return publishIfOpened(unchanged)
            }
            merged
        } else raw
        val checked = if (saved != null)
            ActivityDetail.parse(storedPayload, account, id) else parsed
        prepareThumbnailWrite(account, id, checked)
        store.applyRemotePage(
            account, "activity_detail",
            listOf(TrainingStore.RemoteRecord(id, storedPayload.toString(), null)),
            null, System.currentTimeMillis()
        )
        val result = publishIfOpened(checked)
        scope.launch { loadGpsThumbnail(id) }
        return result
    }

    internal fun close() { scope.cancel(); store.close() }

    suspend fun signOut() = operation("Signing out of cloud backup…") {
        val cloud = mutable.value.cloudAccount ?: return@operation
        val saved = runCatching { vault.read(cloud) }.getOrNull()
        vault.clear(cloud)
        check(preferences.edit().remove("cloud_account").commit())
        // Local owner, Garmin credential, saved activities, and workout state
        // are intentionally unchanged by online account sign-out.
        mutable.value = mutable.value.copy(cloudAccount = null, needsSignIn = false)
        val revoked = saved != null && runCatching { gateway.signOut(saved); true }.getOrDefault(false)
        mutable.value = mutable.value.copy(message =
            if (revoked) "Cloud account disconnected. Local training is still available."
            else "Cloud account disconnected on this device. Local training was retained.")
    }
}
