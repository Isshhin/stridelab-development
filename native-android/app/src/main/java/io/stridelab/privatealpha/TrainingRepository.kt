package io.stridelab.privatealpha

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

internal data class GarminRecordingLoad(val activityId: String, val error: String? = null)

internal data class TrainingState(
    val initializing: Boolean = true,
    val account: String? = null,
    /** Persistent on-device Garmin namespace, independent of cloud sign-in. */
    val localGarminOwner: String? = null,
    /** Stable on-device plan namespace, distinct from cloud and local Garmin. */
    val localPlanOwner: String? = null,
    val localGarminIds: Set<String> = emptySet(),
    val activities: List<ActivityRecord> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val lastSync: Long? = null,
    val needsSignIn: Boolean = false,
    val loginGeneration: Long = 0,
    val plans: List<PlanSnapshot> = emptyList(),
    /** Owner-scoped research reviews, explicitly never executable plans. */
    val researchDrafts: List<IndependentResearchDraft> = emptyList(),
    val shelf:LibraryShelf = LibraryShelf(),
    val templates:List<SessionTemplate> = emptyList(),
    val workoutDrafts:Map<Pair<String,String>,WorkoutEntryDraft> = emptyMap(),
    val wellness: List<WellnessRecord> = emptyList(),
    val details: Map<String,ActivityDetail> = emptyMap(),
    val activityMatches: Map<String, ActivityMatchRecord> = emptyMap(),
    val executionAnalyses: Map<String, WorkoutExecutionAnalysis> = emptyMap(),
    val athleteDiagnostics: Map<String, AthleteDiagnosticSnapshot> = emptyMap(),
    val pendingAdaptationProposals: Map<String, PlanAdaptationProposal> = emptyMap(),
    val adaptationRevisions: Map<String, List<PlanAdaptationRevision>> = emptyMap(),
    val adaptationCooldowns: List<AdaptationCooldownRecord> = emptyList(),
    val task: String? = null,
    val garminRecordingLoad: GarminRecordingLoad? = null,
    val planPreview: PlanSnapshot? = null,
    val todayPlanReady: Boolean = !initializing,
    val activitySummariesReady: Boolean = !initializing,
    val groupedActivitySummaries: List<DisplayActivity>? = null
) {
    val activeAthleteDiagnostic: AthleteDiagnosticSnapshot?
        get() {
            val activePlan = plans.firstOrNull { it.status == "approved" }
                ?: plans.firstOrNull { it.status != "archived" }
                ?: return null
            return athleteDiagnostics[activePlan.id]
        }

    val activeAdaptationProposal: PlanAdaptationProposal?
        get() {
            val activePlan = plans.firstOrNull { it.status == "approved" }
                ?: plans.firstOrNull { it.status != "archived" }
                ?: return null
            return pendingAdaptationProposals[activePlan.id]
        }
}

/** All network/database/encryption work is serialized off the main thread, including workers. */
internal class TrainingRepository(context: Context, private val gateway: TrainingGateway = SupabaseGateway(),
    databaseName: String = TrainingStore.DATABASE, preferencesName: String = "native-account-v1") {
    private val store = TrainingStore(context, databaseName)
    private val appContext = context.applicationContext
    private val engine15ExerciseLibrary by lazy {
        try { ExerciseLibraryV2.load { path -> appContext.assets.open(path) } }
        catch (_: Exception) { throw PlanConstraint("STRENGTH_CATALOG_UNAVAILABLE: The shared offline exercise catalog could not be read; no substitute was generated.") }
    }
    private val vault = SessionVault(context)
    internal val garminSyncPreferences = GarminSyncPreferences(context)
    private val garminCloud = GarminCloudBackupGateway()
    private val preferences by lazy { appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
    private val localGarminOwner: String by lazy {
        preferences.getString("local-garmin-owner-v1", null)
            ?.takeIf { runCatching { RecordPolicy.identifier(it); it.startsWith("local-garmin-") }.getOrDefault(false) }
            ?: ("local-garmin-" + java.util.UUID.randomUUID().toString()).also {
                check(preferences.edit().putString("local-garmin-owner-v1", it).commit())
            }
    }
    private val localPlanOwner: String by lazy {
        preferences.getString(LocalPlanOwnership.PREF_KEY, null)
            ?.takeIf(LocalPlanOwnership::isLocalPlanOwner)
            ?: LocalPlanOwnership.create().also {
                // A replacement identity is isolated; no owner migration is attempted.
                check(preferences.edit().putString(LocalPlanOwnership.PREF_KEY, it).commit())
            }
    }
    // Reserve the mutation lock before callers can enqueue work.
    private val mutex = Mutex(locked = true)
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            try {
                hydrateStartup()
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun hydrateStartup() = supervisorScope {
        try {
            val account = preferences.getString("active", null)
            val owner = account ?: localPlanOwner
            mutable.value = TrainingState(account = account,
                localGarminOwner = localGarminOwner, localPlanOwner = localPlanOwner)
            val summaries = async { traceStartup("Today activity summaries") { readSummaries(account) } }
            val session = account?.let { async {
                runCatching { vault.read(it) }.getOrNull()?.also { _ -> copyLegacyGarminConnection(it) }
            } }
            val plans = traceStartup("Today plan cache") { readPlans(owner) }
            publishPlans(plans)
            val secondary = async {
                traceStartup("Training secondary caches") { readSecondary(owner, account, plans.plans) }
            }
            publishSummaries(summaries.await())
            publishSecondary(secondary.await())
            if (session != null && session.await() == null) {
                mutable.value = mutable.value.copy(needsSignIn = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(
                needsSignIn = mutable.value.account != null,
                message = "Some saved data could not be loaded. Existing files were retained.")
            if (!mutable.value.activitySummariesReady) {
                runCatching { publishSummaries(readSummaries(null)) }
            }
        } finally {
            mutable.value = mutable.value.copy(initializing = false,
                todayPlanReady = true, activitySummariesReady = true)
        }
    }

    /** One-way encrypted on-device migration, never removes the legacy vault. */
    private fun copyLegacyGarminConnection(cloudAccount:String) {
        runCatching {
            val credentials=GarminCredentialVault(appContext)
            if(credentials.read(localGarminOwner)==null) {
                credentials.read(cloudAccount)?.let {
                    credentials.save(it.copy(accountId=localGarminOwner))
                }
            }
        }
        // Keystore failure must never block offline history or cloud sign-out.
    }

    private data class CachedPlans(val plans: List<PlanSnapshot>, val unsupported: Int)

    private fun readPlans(owner: String): CachedPlans {
        val plans = mutableListOf<PlanSnapshot>()
        var unsupported = 0
        store.readableDatabase.query(
            "records", arrayOf("payload"), "account=? AND kind=?",
            arrayOf(owner, "plan"), null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                runCatching { PlanSnapshot.decode(JSONObject(c.getString(0)), owner) }
                    .onSuccess { if (it.status != "draft") plans.add(it) }.onFailure { unsupported++ }
            }
        }
        return CachedPlans(plans.sortedByDescending { it.createdAt }, unsupported)
    }

    private fun publishPlans(cache: CachedPlans) {
        mutable.value = mutable.value.copy(plans = cache.plans, todayPlanReady = true,
            message = if (cache.unsupported > 0)
                "${cache.unsupported} saved plans require a supported contract and were retained for review."
            else mutable.value.message)
    }

    private data class CachedSummaries(
        val activities: List<ActivityRecord>,
        val groups: List<DisplayActivity>,
        val localIds: Set<String>,
        val rejected: Int
    )

    private fun readCloudSummaries(account: String): Pair<List<ActivityRecord>, Int> {
        val records = mutableListOf<ActivityRecord>()
        var rejected = 0
        // No fixed history cap. Stream cursor rows without materializing raw JSON twice.
        store.readableDatabase.query("records", arrayOf("payload"), "account=? AND kind=?", arrayOf(account, "activity"), null, null, null).use { c ->
            while(c.moveToNext()) {
                try {
                    val record=ActivityRecord.parse(c.getString(0),account)
                    if(ActiveActivitySources.accepts(record.source))records+=record
                    // Preserve retired source rows in the original SQLite cache.
                } catch (_: Exception) { rejected++ } // Retain invalid raw records; never invent replacement metrics.
            }
        }
        // Local title edits are stored separately from provider-owned measurements.
        // Failure to read this small optional index must never hide Garmin history.
        val localTitles = HashMap<String, String>()
        runCatching {
            store.readableDatabase.query(
                "records", arrayOf("record_id", "payload"),
                "account=? AND kind=? AND record_id LIKE ?",
                arrayOf(account, "exercise", "local-activity-title-%"),
                null, null, null
            ).use { cursor ->
                while(cursor.moveToNext()) {
                    runCatching {
                        val metadata = JSONObject(cursor.getString(1))
                        val id = metadata.getString("activity_id")
                        val title = validatedActivityTitle(metadata.getString("title"))
                        if(metadata.optString("user_id")==account &&
                            metadata.optString("kind")=="local_activity_title" &&
                            cursor.getString(0)==localActivityTitleKey(id)) {
                            localTitles[id]=title
                        }
                    }
                }
            }
        }
        val titledRecords = records.map { record ->
            localTitles[record.id]?.let { record.copy(title=it) } ?: record
        }
        return titledRecords to rejected
    }

    private fun readSummaries(account: String?): CachedSummaries {
        val (cloud, rejected) = account?.let(::readCloudSummaries) ?: (emptyList<ActivityRecord>() to 0)
        val local = mutableListOf<ActivityRecord>()
        store.readableDatabase.query("records", arrayOf("payload"),
            "account=? AND kind=?", arrayOf(localGarminOwner, "activity"),
            null, null, null).use { cursor ->
            while(cursor.moveToNext()) runCatching {
                ActivityRecord.parse(cursor.getString(0), localGarminOwner)
                    .takeIf { it.source == "garmin" }?.let(local::add)
            }
        }
        val titles = HashMap<String,String>()
        store.readableDatabase.query("records", arrayOf("record_id","payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(localGarminOwner,"exercise","local-activity-title-%"),
            null,null,null).use { cursor ->
            while(cursor.moveToNext()) runCatching {
                val j=JSONObject(cursor.getString(1))
                val id=j.getString("activity_id")
                if(j.optString("user_id")==localGarminOwner &&
                    j.optString("kind")=="local_activity_title" &&
                    cursor.getString(0)==localActivityTitleKey(id)) {
                    titles[id]=validatedActivityTitle(j.getString("title"))
                }
            }
        }
        val titled = local.map { record ->
            titles[record.id]?.let { record.copy(title=it) } ?: record
        }
        val ids = titled.mapTo(HashSet<String>()) { it.id }
        val activities = mergeActivitySummaries(cloud, titled)
        return CachedSummaries(activities, ActivityDisplayConsolidation.groups(activities), ids, rejected)
    }

    private fun publishSummaries(cache: CachedSummaries) {
        val prior = mutable.value
        val ids = cache.activities.mapTo(HashSet()) { it.id }
        mutable.value = prior.copy(activities = cache.activities, activitySummariesReady = true,
            groupedActivitySummaries = cache.groups,
            localGarminIds = cache.localIds,
            details = prior.details.filterKeys { it in ids && (it in prior.localGarminIds) == (it in cache.localIds) },
            message = if (cache.rejected > 0 && prior.message == null)
                "${cache.rejected} saved activities contain unsupported data and were retained for review."
            else prior.message)
    }

    private fun loadCache(account: String) {
        val plans = readPlans(account)
        publishSummaries(readSummaries(account))
        publishPlans(plans)
        publishSecondary(readSecondary(account, account, plans.plans))
    }

    /** Read only exact-current-owner non-executable research records. */
    private fun loadResearchDrafts(owner: String): List<IndependentResearchDraft> {
        val drafts = mutableListOf<IndependentResearchDraft>()
        store.readableDatabase.query(
            "records", arrayOf("record_id","payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner,"exercise","engine15-research-%"),
            null,null,null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    IndependentResearchDraft.decode(
                        JSONObject(cursor.getString(1)), owner
                    ).also { require(it.recordId == cursor.getString(0)) }
                }.onSuccess { drafts.add(it) }
                // Unsupported or corrupt records remain on disk; do not
                // fabricate a substitute or migrate owners automatically.
            }
        }
        return drafts.sortedByDescending { it.createdAt }
    }

    private fun loadLocalPlanCache() {
        val plans = readPlans(localPlanOwner)
        publishPlans(plans)
        publishSecondary(readSecondary(localPlanOwner, null, plans.plans))
    }

    private data class SecondaryCaches(
        val templates: List<SessionTemplate>, val shelf: LibraryShelf,
        val drafts: Map<Pair<String, String>, WorkoutEntryDraft>, val research: List<IndependentResearchDraft>,
        val wellness: List<WellnessRecord>, val lastSync: Long?, val execution: ExecutionPersistenceBundle
    )

    private fun readSecondary(owner: String, account: String?, plans: List<PlanSnapshot>): SecondaryCaches {
        val templates = mutableListOf<SessionTemplate>()
        store.readableDatabase.query(
            "records", arrayOf("payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "exercise", "session-template-%"),
            null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                runCatching { SessionTemplate.decode(JSONObject(c.getString(0)), owner) }
                    .onSuccess { templates.add(it) }
            }
        }
        val shelf = runCatching {
            store.readableDatabase.query(
                "records", arrayOf("payload"),
                "account=? AND kind=? AND record_id=?",
                arrayOf(owner, "exercise", "library-shelf-v1"),
                null, null, null
            ).use { c ->
                if (c.moveToFirst()) LibraryShelf.decode(JSONObject(c.getString(0)), owner)
                else LibraryShelf()
            }
        }.getOrDefault(LibraryShelf())
        val drafts = mutableMapOf<Pair<String, String>, WorkoutEntryDraft>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "entry-draft-%"),
            null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                runCatching {
                    WorkoutEntryDraft.decode(JSONObject(c.getString(1)), owner)
                        .also { require(it.recordId == c.getString(0)) }
                }.onSuccess {
                    if (validDraft(it, plans)) drafts[it.key] = it
                }
            }
        }
        val wellness=mutableListOf<WellnessRecord>()
        if (account != null) store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=?",arrayOf(account,"wellness"),null,null,null).use { c ->
            while(c.moveToNext()) runCatching {
                WellnessRecord.parse(JSONObject(c.getString(0)),account)?.let {
                    if(ActiveActivitySources.accepts(it.source))wellness.add(it)
                }
            }
        }
        val last = account?.let { store.cursor(it, "activity") }?.let {
            runCatching { JSONObject(it).getLong("completedAt") }.getOrNull()
        }
        return SecondaryCaches(templates, shelf, drafts, loadResearchDrafts(owner),
            wellness, last, loadExecutionRecords(owner))
    }

    private fun publishSecondary(cache: SecondaryCaches) {
        val bundle = cache.execution
        mutable.value = mutable.value.copy(
            templates = cache.templates, shelf = cache.shelf, workoutDrafts = cache.drafts,
            researchDrafts = cache.research, wellness = cache.wellness, lastSync = cache.lastSync,
            activityMatches = bundle.matches, executionAnalyses = bundle.analyses,
            athleteDiagnostics = bundle.diagnostics, pendingAdaptationProposals = bundle.pendingProposals,
            adaptationRevisions = bundle.revisions, adaptationCooldowns = bundle.cooldowns
        )
    }

    /**
     * Local-only writes never enter the cloud outbox, even if the user signs in
     * later. Cloud-account writes keep their existing transactional outbox path.
     */
    private fun saveOwnedRecord(owner: String, kind: String, id: String, payload: String) {
        check(owner == LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner))
        if (mutable.value.account == null) {
            store.applyRemotePage(
                owner, kind, listOf(TrainingStore.RemoteRecord(id, payload, null)),
                null, System.currentTimeMillis()
            )
        } else {
            store.enqueue(
                owner, kind, id, java.util.UUID.randomUUID().toString(),
                payload, null, System.currentTimeMillis()
            )
        }
    }

    /** Reads only on-device Garmin history. Cloud-user records remain isolated. */
    private fun loadLocalGarminCache() {
        publishSummaries(readSummaries(mutable.value.account))
    }
    /**
     * Private, owner-scoped activity archive. The repository lock prevents
     * switching accounts or running a source sync during export/restore.
     */
    internal suspend fun exportLocalActivityArchive(
        scope: LocalActivityArchiveScope
    ): java.io.File = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = when(scope) {
                LocalActivityArchiveScope.LOCAL_GARMIN -> localGarminOwner
                LocalActivityArchiveScope.CLOUD_ACCOUNT -> mutable.value.account
                    ?: throw IllegalStateException("Sign in to export cloud-account activity data.")
            }
            val output = java.io.File.createTempFile(
                "stridelab-activity-", ".zip", appContext.cacheDir)
            val service = LocalActivityArchive(appContext)
            try {
                output.outputStream().buffered().use { service.export(owner,scope,it) }
                require(output.length() > 0L &&
                    output.length() <= LocalActivityArchive.MAX_ARCHIVE_BYTES) {
                    "The complete archive exceeds the supported ZIP size; partial file removed."
                }
                output
            } catch(error: Exception) {
                output.delete()
                throw error
            } finally {
                service.close()
            }
        }
    }

    internal suspend fun restoreLocalActivityArchive(
        scope: LocalActivityArchiveScope, archive: java.io.File
    ): LocalActivityArchiveCounts = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = when(scope) {
                LocalActivityArchiveScope.LOCAL_GARMIN -> localGarminOwner
                LocalActivityArchiveScope.CLOUD_ACCOUNT -> mutable.value.account
                    ?: throw IllegalStateException("Sign in to the matching cloud account before restore.")
            }
            val service = LocalActivityArchive(appContext)
            try {
                val result = service.restore(archive,owner,scope)
                if(result.newlyRestored > 0) {
                    if(scope == LocalActivityArchiveScope.LOCAL_GARMIN)
                        loadLocalGarminCache()
                    else loadCache(owner)
                }
                result
            } finally {
                service.close()
            }
        }
    }

    suspend fun restorePlans(raw:String)=operation("Restoring reviewed plan backup…") {
        val account=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        val backup=PlanBackup.readBundle(raw,account)
        val additions=PlanBackup.additions(backup.plans.filter { it.status != "draft" },mutable.value.plans)
        val reviewAdditions=PlanBackup.researchAdditions(
            backup.researchReviews,mutable.value.researchDrafts
        )
        if(additions.isEmpty() && reviewAdditions.isEmpty()) {
            mutable.value=mutable.value.copy(message="This backup contains no new records. Existing records were kept unchanged.")
            return@operation
        }
        val now=System.currentTimeMillis()
        store.restoreReviewedBackup(
            account,
            additions.map { TrainingStore.RemoteRecord(it.id,it.encode().toString(),null) },
            reviewAdditions.map { TrainingStore.RemoteRecord(it.recordId,it.encode().toString(),null) },
            mutable.value.account!=null,
            now
        )
        if(mutable.value.account==null)loadLocalPlanCache() else loadCache(account)
        mutable.value=mutable.value.copy(message="Restored ${additions.size} new plans and ${reviewAdditions.size} non-executable research reviews. Existing records were kept unchanged.")
    }
    suspend fun changeShelf(id:String,collection:String?=null,remove:Boolean=false)=operation("Saving your library…") {
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        val shelf=mutable.value.shelf.change(id,collection,remove)
        saveOwnedRecord(owner,"exercise","library-shelf-v1",shelf.encode(owner).toString())
        mutable.value=mutable.value.copy(shelf=shelf)
    }
    suspend fun saveTemplate(template:SessionTemplate)=operation("Saving your session template…") {
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        template.validate()
        if(mutable.value.templates.size>=50 && mutable.value.templates.none { it.id==template.id })throw PlanConstraint("This profile has fifty saved templates. Reuse an existing template.")
        saveOwnedRecord(owner,"exercise","session-template-${template.id}",template.encode(owner).toString())
        mutable.value=mutable.value.copy(templates=mutable.value.templates.filterNot { it.id==template.id }+template)
    }
    suspend fun prepareSession(template:SessionTemplate,request:PlanRequest,date:java.time.LocalDate)=operation("Preparing your reviewed session…") {
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        mutable.value = mutable.value.copy(planPreview = template.draft(owner,request,date))
    }
    private fun validDraft(draft:WorkoutEntryDraft,plans:List<PlanSnapshot>):Boolean {
        val p=plans.find { it.id==draft.planId && it.owner==draft.owner } ?: return false
        val s=p.sessions.find { it.id==draft.sessionId } ?: return false
        return Engine1Engines.canManage(p) && p.status in setOf("approved","paused") && s.status in setOf("planned","in_progress") && s.logRevision==draft.logRevision
    }
    /** Local input staging is deliberately not a confirmed workout or a cloud mutation. */
    suspend fun stageWorkoutDraft(draft:WorkoutEntryDraft):Boolean=withContext(Dispatchers.IO) { mutex.withLock {
        val account=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        if(draft.owner!=account || !validDraft(draft,mutable.value.plans))return@withLock false
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
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        if(plan.owner!=owner)throw PlanConstraint("The plan belongs to a different local profile or cloud account.")
        val encoded=plan.encode()
        require(encoded.getString("id")==plan.id && encoded.getString("user_id")==owner)
        saveOwnedRecord(owner,"plan",plan.id,encoded.toString())
        val all=(mutable.value.plans.filterNot { it.id==plan.id }+plan).sortedByDescending { it.createdAt }
        mutable.value=mutable.value.copy(plans=all,workoutDrafts=mutable.value.workoutDrafts.filterValues { validDraft(it,all) })
    }
    /**
     * Unlike a PlanSnapshot, a research review is saved only under kind=exercise
     * and never added to the cloud outbox, executable calendar or plan list.
     * Recompute inside the serialized repository operation: never trust a
     * client-supplied preview as proof that limits were checked.
     */
    suspend fun saveResearchReview(
        request: PlanRequest,
        constraints: IndependentAndroidPreviewConstraints
    ) = operation("Saving non-executable research comparison…") {
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        if(mutable.value.researchDrafts.size >= 20)
            throw PlanConstraint("Research comparison limit reached. Existing reviews were retained.")
        val preview=when (val result =
            IndependentAndroidResearchBridge.preview(request,constraints)) {
            is IndependentAndroidPreviewDecision.Deferred ->
                throw PlanConstraint("Research comparison held: " + result.ruleId + ". " + result.explanation)
            is IndependentAndroidPreviewDecision.ReadOnly -> result
        }
        val draft=IndependentResearchDraft.fromPreview(
            owner,request,preview,constraints
        )
        val raw=draft.encode().toString()
        RecordPolicy.record(owner,"exercise",draft.recordId,raw)
        // Deliberate local cache write for both signed-in and signed-out
        // profiles. Cloud account selection does NOT authorize upload.
        store.applyRemotePage(
            owner,"exercise",listOf(
                TrainingStore.RemoteRecord(draft.recordId,raw,null)
            ),null,System.currentTimeMillis()
        )
        mutable.value=mutable.value.copy(
            researchDrafts=(mutable.value.researchDrafts+draft)
                .sortedByDescending { it.createdAt },
            message="Research comparison saved privately on this device. No workouts or approved plan changed."
        )
    }

    suspend fun generatePlan(request:PlanRequest,engine:PlanGenerationEngine?=null) = operation("Building your plan…") {
        val owner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        val state=mutable.value
        val selected=engine ?: Engine15IndependentPlanGenerator
        val ownerRows = state.activities.filter { activityCorrectionOwner(it.id, state) == owner }
        val allowedIds = ownerRows.mapTo(mutableSetOf()) { it.id }
        val userLabels = if (ownerRows.isEmpty()) emptyMap() else {
            val labels = UserActivityWorkoutLabelStore(appContext)
            ownerRows.mapNotNull { row -> labels.load(owner, row.id)?.let { row.id to it } }.toMap()
        }
        val detections = if (ownerRows.isEmpty()) emptyList() else {
            Engine15WorkoutDetection.detectAll(
                owner = owner,
                activities = ownerRows,
                allowedActivityIds = allowedIds,
                detailsByActivityId = state.details,
                plans = state.plans,
                userLabels = userLabels,
                asOf = java.time.Instant.now()
            )
        }
        val qualityEvidence = if (detections.isEmpty()) emptyList() else {
            Engine15WorkoutDetection.toQualitySegmentEvidence(owner, detections)
        }
        val activityEvidence = if (selected === Engine15IndependentPlanGenerator &&
            request.runDays > 0 && request.strengthDays == 0 &&
            request.calDays == 0 && request.conditioningDays == 0 &&
            request.disciplineGoals.keys.all { it == "run" }) {
            if (ownerRows.isEmpty()) null else {
                ActivityAthleteEvidence.profile(
                    owner, ownerRows, allowedIds, userLabels, java.time.Instant.now()
                )
            }
        } else null
        mutable.value = mutable.value.copy(planPreview = null)
        val preview = when(selected) {
            Engine15IndependentPlanGenerator -> Engine15IndependentPlanGenerator.generateWithHistory(
                request, owner, mutable.value.plans,
                exerciseLibrary = if (request.strengthDays > 0 || request.calDays > 0 || request.conditioningDays > 0)
                    engine15ExerciseLibrary else null,
                activityEvidence = activityEvidence,
                qualityEvidence = qualityEvidence,
                activities = ownerRows,
                detections = detections
            )
            else -> selected.generate(request,owner)
        }
        mutable.value = mutable.value.copy(planPreview = preview)
    }
    suspend fun changePlan(id:String,revision:Int,action:(PlanSnapshot)->PlanSnapshot) = operation("Saving your plan…") {
        val activeOwner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        val plan=mutable.value.plans.find { it.id==id && it.owner==activeOwner }
            ?: throw PlanConstraint("This plan is unavailable for the current local profile or account.")
        if(!Engine1Engines.canManage(plan))throw PlanConstraint("This saved plan needs its compatible engine before editing. Its original snapshot is retained.")
        if(plan.revision!=revision)throw PlanConstraint("The saved plan changed. Reopen it before editing.")
        val updated=action(plan)
        require(updated.id==plan.id && updated.owner==plan.owner && updated.engineVersion==plan.engineVersion && updated.policyId==plan.policyId)
        if (updated != plan) {
            savePlan(updated.copy(revision = plan.revision + 1))
            if (updated.sessions.any { it.discipline != "run" }) reconcileActivityMatchesAndAnalysis()
        }
    }
    suspend fun swapExercise(id:String,revision:Int,sessionId:String,exerciseId:String,direction:ExerciseAlternatives.SwapDirection=ExerciseAlternatives.SwapDirection.EASIER)=changePlan(id,revision) { PlanOperations.swapExercise(it,sessionId,exerciseId,direction=direction) }
    suspend fun linkActivity(id:String,revision:Int,sessionId:String,activityId:String?): Boolean {
        val priorLinked = mutable.value.plans.find { it.id == id }
            ?.sessions?.find { it.id == sessionId }?.linkedActivityId
        val priorAutoLinked = mutable.value.activityMatches.values
            .firstOrNull {
                it.plannedSessionId == sessionId &&
                    (it.matchStatus == ActivityMatchStatus.AUTO_LINKED ||
                        it.matchStatus == ActivityMatchStatus.MANUALLY_LINKED)
            }?.activityId
        val ok = changePlan(id,revision) { plan ->
            if(activityId!=null) {
                val activity=mutable.value.activities.find { it.id==activityId } ?: throw PlanConstraint("This source activity is not cached for your account.")
                if(!DashboardSummary.isRun(activity))throw PlanConstraint("Only a recorded run can be linked to a planned run.")
                if(mutable.value.plans.any { p -> p.sessions.any { s -> s.linkedActivityId==activityId && (p.id!=id || s.id!=sessionId) } })throw PlanConstraint("This source run is already linked to another workout. Unlink it there first.")
            }
            PlanOperations.linkActivity(plan,sessionId,activityId)
        }
        if (ok) {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    if (activityId == null) {
                        val rejectedId = priorLinked ?: priorAutoLinked
                        if (rejectedId != null) markManualRejectionLocked(rejectedId, sessionId)
                    } else {
                        markManualLinkLocked(activityId, sessionId)
                    }
                    reconcileActivityMatchesAndAnalysis()
                }
            }
        }
        return ok
    }
    suspend fun confirmActivityMatch(id: String, revision: Int, sessionId: String, activityId: String): Boolean =
        linkActivity(id, revision, sessionId, activityId)
    suspend fun rejectActivityMatch(activityId: String, plannedSessionId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            markManualRejectionLocked(activityId, plannedSessionId)
            reconcileActivityMatchesAndAnalysis()
        }
    }
    suspend fun approveAdaptationProposal(planId: String, proposalId: String): Boolean =
        operation("Applying approved plan adaptation…") {
            val current = mutable.value
            val plan = current.plans.find { it.id == planId }
                ?: throw PlanConstraint("This plan is unavailable.")
            val proposal = current.pendingAdaptationProposals[planId]
                ?.takeIf { it.proposalId == proposalId }
                ?: throw PlanConstraint("This adaptation proposal is no longer pending.")
            val today = java.time.LocalDate.now()
            val applied = Engine15PlanAdaptationEngine.applyProposal(plan, proposal, today)
            savePlan(applied.appliedPlan)
            persistAdaptationProposalLocked(applied.updatedProposal)
            persistAdaptationRevisionLocked(applied.revision)
            persistAdaptationCooldownLocked(applied.cooldown)
            val updatedRevisions = current.adaptationRevisions.toMutableMap().apply {
                put(planId, getOrDefault(planId, emptyList()) + applied.revision)
            }
            mutable.value = mutable.value.copy(
                pendingAdaptationProposals = mutable.value.pendingAdaptationProposals - planId,
                adaptationRevisions = updatedRevisions,
                adaptationCooldowns = (mutable.value.adaptationCooldowns.filterNot {
                    it.planId == planId && it.adaptationType == applied.cooldown.adaptationType &&
                        it.disruptionFingerprint == applied.cooldown.disruptionFingerprint
                } + applied.cooldown)
            )
            reconcileActivityMatchesAndAnalysis()
        }
    suspend fun dismissAdaptationProposal(planId: String, proposalId: String): Boolean =
        operation("Dismissing adaptation suggestion…") {
            val current = mutable.value
            val proposal = current.pendingAdaptationProposals[planId]
                ?.takeIf { it.proposalId == proposalId }
                ?: return@operation
            val today = java.time.LocalDate.now()
            val dismissed = proposal.copy(status = AdaptationProposalStatus.DISMISSED)
            val cooldown = AdaptationCooldownRecord(
                planId = planId,
                adaptationType = proposal.type,
                lastActionStatus = AdaptationProposalStatus.DISMISSED,
                actionDate = today,
                cooldownUntilDate = today.plusDays(14L),
                disruptionFingerprint = proposal.disruptionFingerprint
            )
            persistAdaptationProposalLocked(dismissed)
            persistAdaptationCooldownLocked(cooldown)
            mutable.value = mutable.value.copy(
                pendingAdaptationProposals = current.pendingAdaptationProposals - planId,
                adaptationCooldowns = current.adaptationCooldowns.filterNot {
                    it.planId == planId && it.adaptationType == cooldown.adaptationType &&
                        it.disruptionFingerprint == cooldown.disruptionFingerprint
                } + cooldown
            )
        }
    suspend fun revertLastAdaptation(planId: String): Boolean =
        operation("Reverting last adaptation…") {
            val current = mutable.value
            val plan = current.plans.find { it.id == planId }
                ?: throw PlanConstraint("This plan is unavailable.")
            val revisions = current.adaptationRevisions[planId].orEmpty()
            val latest = revisions.lastOrNull { it.revertedAtDate == null }
                ?: throw PlanConstraint("No adaptation revision is available to revert.")
            val reverted = Engine15PlanAdaptationEngine.revertRevision(plan, latest, java.time.LocalDate.now())
            savePlan(reverted.restoredPlan)
            persistAdaptationRevisionLocked(reverted.updatedRevision)
            val updatedList = revisions.map {
                if (it.revisionId == latest.revisionId) reverted.updatedRevision else it
            }
            mutable.value = mutable.value.copy(
                adaptationRevisions = current.adaptationRevisions + (planId to updatedList),
                pendingAdaptationProposals = current.pendingAdaptationProposals - planId
            )
            reconcileActivityMatchesAndAnalysis()
        }
    suspend fun discardPlanPreview() = withContext(Dispatchers.IO) {
        mutex.withLock { mutable.value = mutable.value.copy(planPreview = null) }
    }

    suspend fun approvePlan(id: String, revision: Int) = operation("Activating your plan…") {
        val current = mutable.value
        val owner = LocalPlanOwnership.activeOwner(current.account, localPlanOwner)
        val plan = (current.planPreview?.takeIf { it.id == id } ?: current.plans.find { it.id == id })
            ?.takeIf { it.owner == owner && it.revision == revision }
            ?: throw PlanConstraint("This plan preview is no longer available.")
        val approved = PlanOperations.approve(plan, current.plans)
        savePlan(approved.copy(revision = plan.revision + 1))
        if (current.planPreview?.id == id) mutable.value = mutable.value.copy(planPreview = null)
        reconcileActivityMatchesAndAnalysis()
    }
    suspend fun pausePlan(id:String,revision:Int)=changePlan(id,revision) { it.copy(status="paused") }
    suspend fun archivePlan(id:String,revision:Int)=changePlan(id,revision) { it.copy(status="archived") }
    suspend fun deletePlan(id:String,revision:Int)=operation("Deleting plan…") {
        val activeOwner=LocalPlanOwnership.activeOwner(mutable.value.account,localPlanOwner)
        val plan=mutable.value.plans.find { it.id==id && it.owner==activeOwner }
            ?: throw PlanConstraint("This plan is unavailable for the current local profile or account.")
        if(plan.revision!=revision)throw PlanConstraint("The saved plan changed. Reopen it before deleting.")
        store.deleteRecord(activeOwner,"plan",plan.id)
        val staleDrafts=mutable.value.workoutDrafts.filter { (_,draft) -> draft.planId==plan.id && draft.owner==activeOwner }
        staleDrafts.values.forEach { draft -> store.deleteRecord(activeOwner,"workout",draft.recordId) }
        mutable.value=mutable.value.copy(
            plans=mutable.value.plans.filterNot { it.id==plan.id },
            workoutDrafts=mutable.value.workoutDrafts-staleDrafts.keys
        )
    }
    suspend fun moveSession(id:String,revision:Int,sessionId:String,date:java.time.LocalDate)=changePlan(id,revision) { plan->PlanOperations.move(plan,sessionId,date,mutable.value.plans) }
    suspend fun adjustWorkoutDifficulty(id:String,revision:Int,sessionId:String,delta:Int)=changePlan(id,revision) { plan->PlanOperations.adjustDifficulty(plan,sessionId,delta) }
    suspend fun setSessionLock(id:String,revision:Int,sessionId:String,locked:Boolean)=changePlan(id,revision) { plan->PlanOperations.setSessionLock(plan,sessionId,locked) }
    fun previewAvailabilityChange(id:String,newDays:Set<Int>):Engine15Availability.Impact? {
        val plan=mutable.value.plans.find {
            it.id==id && LocalPlanOwnership.isVisibleOwner(it.owner,mutable.value.account,localPlanOwner)
        } ?: return null
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
    suspend fun executeWorkout(planId: String, sessionId: String, expectedRevision: Long?, event: PlayerEvent?): WorkoutExecution? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
                val plan = mutable.value.plans.singleOrNull { it.id == planId && it.owner == owner }
                    ?: throw PlanConstraint("This workout is unavailable for the current profile.")
                val session = plan.sessions.singleOrNull { it.id == sessionId }
                    ?: throw PlanConstraint("This workout is no longer on the plan.")
                val key = WorkoutExecutionCheckpoint.key(planId, sessionId)
                val now = android.os.SystemClock.elapsedRealtime()
                val bootCount = runCatching {
                    android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
                }.getOrNull()
                val saved = store.get(owner, "workout", key)?.let {
                    WorkoutExecutionCheckpoint.decode(it.payload, owner, planId, sessionId, now, System.currentTimeMillis(), bootCount)
                }
                if (saved?.receipt == true) return@withLock saved
                if (saved?.finalLog == null && (!Engine1Engines.canManage(plan) ||
                        plan.status !in setOf("approved", "paused") || session.status !in setOf("planned", "in_progress"))) {
                    return@withLock null
                }
                fun persist(state: WorkoutExecution) {
                    val wall = System.currentTimeMillis()
                    val payload = WorkoutExecutionCheckpoint.encode(owner, state, android.os.SystemClock.elapsedRealtime(), wall, bootCount)
                    store.applyRemotePage(owner, "workout", listOf(TrainingStore.RemoteRecord(key, payload, null)), null, wall)
                }
                val baseline = saved ?: WorkoutExecution.create(java.util.UUID.randomUUID().toString(), planId, session,
                    java.util.UUID.randomUUID().toString()).copy(openedAtMs = System.currentTimeMillis())
                val before = if (saved != null && baseline.finalLog == null && baseline.phase != PlayerPhase.Completed) {
                    val currentTargets = WorkoutExecution.create(baseline.executionId, planId, session, baseline.finalizationId)
                    if (baseline.sets.map { it.target to it.number } == currentTargets.sets.map { it.target to it.number }) baseline
                    else {
                        if (!baseline.canChangePrescription)
                            throw PlanConstraint("The workout prescription changed after you started. Your original execution was retained.")
                        currentTargets.copy(revision = baseline.revision + 1, openedAtMs = baseline.openedAtMs)
                    }
                } else baseline
                val engine = WorkoutPlayerEngine(android.os.SystemClock::elapsedRealtime)
                val input = if (saved == null && event == null) PlayerEvent.StartSession else event
                val next = if (input != null && (expectedRevision == null || expectedRevision == before.revision))
                    engine.reduce(before, input) else before
                val finalLog = next.finalLog
                if (finalLog == null) {
                    if (saved == null || next.revision != saved.revision) persist(next)
                    return@withLock next
                }

                // Persist the frozen log and operation identity before touching the plan. A retry
                // compares that exact log against the current session before attempting another write.
                val completed = reconcileWorkoutFinalization(plan, sessionId, finalLog)
                if (next != before) persist(next)
                if (completed != plan) savePlan(completed.copy(revision = plan.revision + 1))
                val receipt = next.copy(phase = PlayerPhase.Completed, receipt = true)
                persist(receipt)
                if (completed != plan) reconcileActivityMatchesAndAnalysis()
                receipt
            }
        }

    suspend fun recordWorkout(id:String,revision:Int,sessionId:String,log:WorkoutLog,complete:Boolean)=changePlan(id,revision) { PlanOperations.record(it,sessionId,log,complete) }
    suspend fun saveRunResponse(id:String,revision:Int,sessionId:String,effort:Int?,difficulty:String?,discomfort:String?)=changePlan(id,revision) { plan ->
        val session=plan.sessions.singleOrNull { it.id==sessionId } ?: throw PlanConstraint("This workout is unavailable.")
        val performed=session.log?.performedOn ?: mutable.value.activities.singleOrNull { it.id==session.linkedActivityId }?.startedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
        PlanOperations.runResponse(plan,sessionId,effort,difficulty,discomfort,performed)
    }
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
        val p=mutable.value.plans.find {
            it.id==id && LocalPlanOwnership.isVisibleOwner(it.owner,mutable.value.account,localPlanOwner)
        } ?: return emptyList()
        return Engine15Feedback.preview(p,asOf,mutable.value.plans)
    }
    fun previewEngine15Week(id:String,asOf:java.time.LocalDate=java.time.LocalDate.now(),input:Engine15WeeklyReview.Input=Engine15WeeklyReview.Input()):Engine15WeeklyReview.Review {
        val p=mutable.value.plans.find { it.id==id && LocalPlanOwnership.isVisibleOwner(it.owner,mutable.value.account,localPlanOwner) }
            ?: throw PlanConstraint("This plan is unavailable.")
        return Engine15WeeklyReview.preview(p,asOf,input,mutable.value.plans,activities=mutable.value.activities)
    }
    suspend fun acceptEngine15Week(id:String,revision:Int,review:Engine15WeeklyReview.Review)=changePlan(id,revision) { p ->
        Engine15WeeklyReview.accept(p,review,java.time.LocalDate.now(),java.time.Instant.now(),mutable.value.plans,activities=mutable.value.activities)
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
        val account=mutable.value.account ?: throw IllegalStateException("Sign in first")
        val raw=authenticated { gateway.wellness(it) }
        val seen=HashSet<String>(); val rows=ArrayList<TrainingStore.RemoteRecord>()
        for(i in 0 until raw.length()) {
            val row=raw.getJSONObject(i)
            val record=WellnessRecord.parse(row,account) ?: continue
            if(!ActiveActivitySources.accepts(record.source))continue
            require(seen.add(record.id))
            rows+=TrainingStore.RemoteRecord(record.id,row.toString(),null)
        }
        store.applyRemotePage(account,"wellness",rows,null,System.currentTimeMillis())
        loadCache(account)
    }
    /**
     * Persist a local display-name override without modifying the original
     * provider activity, FIT measurements, or cloud backup payload.
     * This record is account-scoped, and Garmin sync cannot overwrite it.
     */
    internal suspend fun renameActivity(id:String, requestedTitle:String):Boolean =
        operation("Saving activity title…") {
            val account = if(id in mutable.value.localGarminIds) localGarminOwner else mutable.value.account
                ?: throw PlanConstraint("This activity is not in the current local or cloud history.")
            if(mutable.value.activities.none { it.id==id })
                throw PlanConstraint("This activity is not in the current account's saved history.")
            val title = validatedActivityTitle(requestedTitle)
            val payload = JSONObject()
                .put("kind", "local_activity_title")
                .put("user_id", account)
                .put("activity_id", id)
                .put("title", title)
            store.applyRemotePage(
                account, "exercise",
                listOf(TrainingStore.RemoteRecord(
                    localActivityTitleKey(id), payload.toString(), null
                )),
                null, System.currentTimeMillis()
            )
            mutable.value = mutable.value.copy(
                groupedActivitySummaries = null,
                activities = mutable.value.activities.map { activity ->
                    if(activity.id==id) activity.copy(title=title) else activity
                }
            )
        }

    /** Load an existing, account-owned FIT recording without any network call. */
    internal suspend fun loadCachedDetail(id:String):Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val account=if(id in mutable.value.localGarminIds) localGarminOwner
                else mutable.value.account ?: return@withLock false
            val activity = mutable.value.activities.firstOrNull { it.id == id } ?: return@withLock false
            if(id in mutable.value.details)return@withLock true
            val cached = try {
                val payload = store.getActivityDetailPayloadChunked(account,id)
                if (activity.source == "garmin") GarminGpsDetail.cachedRecording(payload, account, id)
                else payload?.let { ActivityDetail.parse(JSONObject(it),account,id) }
            } catch (_: Exception) {
                if (activity.source == "garmin") mutable.value = mutable.value.copy(
                    garminRecordingLoad = GarminRecordingLoad(id, garminCacheReadError))
                null
            } ?: return@withLock false
            val recent=LinkedHashMap(mutable.value.details)
            recent.remove(id)
            recent[id]=cached
            while(recent.size>12)recent.remove(recent.keys.first())
            publishCachedDetail(id, recent)
            if (activity.source == "garmin") reconcileActivityMatchesAndAnalysis()
            true
        }
    }

    private val thumbnailIO = Dispatchers.IO.limitedParallelism(2)

    /** Read one route for presentation without populating the detail cache or running the engine. */
    internal suspend fun loadActivityThumbnail(id: String): RecordedGpsRoute? = withContext(thumbnailIO) {
        val snapshot = state.value
        val owner = if (id in snapshot.localGarminIds) snapshot.localGarminOwner else snapshot.account
        owner ?: return@withContext null
        val activity = snapshot.activities.firstOrNull { it.id == id } ?: return@withContext null
        val route = traceStartup("Activity thumbnail") {
            runCatching {
                val detail = snapshot.details[id] ?: store.getActivityDetailPayloadChunked(owner, id)?.let {
                    if (activity.source == "garmin") GarminGpsDetail.cachedRecording(it, owner, id)
                    else ActivityDetail.parse(JSONObject(it), owner, id)
                }
                detail?.thumbnailRoute()
            }.getOrNull()
        }
        val current = state.value
        val currentOwner = if (id in current.localGarminIds) current.localGarminOwner else current.account
        route.takeIf { currentOwner == owner && current.activities.any { activity -> activity.id == id } }
    }

    suspend fun loadDetail(id: String) = operation("Loading recorded streams…") {
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        require(mutable.value.activities.any { it.id == id })
        val raw = authenticated { gateway.detail(it,id) }
        val parsed = ActivityDetail.parse(raw,account,id)
        store.applyRemotePage(account,"activity_detail",listOf(TrainingStore.RemoteRecord(id,raw.toString(),null)),null,System.currentTimeMillis())
        mutable.value = mutable.value.copy(details = mutable.value.details + (id to parsed))
        reconcileActivityMatchesAndAnalysis()
    }
    private suspend fun operation(label: String, action: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutable.value = mutable.value.copy(busy = true, message = null, task = label)
            try { action(); true }
            catch (e: CancellationException) { throw e }
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
    suspend fun signIn(email: String, password: String) = operation("Signing in securely…") {
        require(email.isNotBlank() && password.isNotEmpty())
        val session = try {
            gateway.signIn(email, password).also { gateway.verify(it) }
        } catch(e: ServiceFailure) {
            // A failed login attempt must not invalidate a different, still-active session.
            throw ServiceFailure(e.status, e.message ?: "Sign-in was not accepted", requiresSignIn = false)
        }
        vault.save(session) // No account activation unless encryption succeeds.
        copyLegacyGarminConnection(session.account)
        check(preferences.edit().putString("active", session.account).commit())
        mutable.value = TrainingState(initializing = false, account = session.account,
            localPlanOwner = localPlanOwner, busy = true,
            loginGeneration = mutable.value.loginGeneration + 1)
        loadCache(session.account)
        // Authentication is complete even if this first cache refresh fails.
        fetchActivities()
    }
    private fun session(): NativeSession {
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        val saved = try { vault.read(account) } catch (_: java.io.IOException) {
            throw ServiceFailure(401,"Your saved session could not be verified. Sign in again; cached activities are retained.")
        } ?: throw ServiceFailure(401,"Sign in again to synchronize. Saved activities remain available.")
        val current = if(saved.expiresAtSeconds <= System.currentTimeMillis()/1000 + 90) renew(saved) else saved
        return current
    }
    private fun renew(saved: NativeSession): NativeSession {
        val fresh = gateway.refresh(saved)
        if(fresh.account != saved.account || fresh.account != mutable.value.account) throw ServiceFailure(401,"The renewed session did not match this account. Sign in again; cached data is retained.")
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
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        var before: ActivityRecord? = null
        var received = 0
        val seen = HashSet<String>()
        while(true) {
            val page = authenticated { gateway.activities(it, before) }
            require(page.length() <= 200)
            val rows = ArrayList<TrainingStore.RemoteRecord>()
            var last: ActivityRecord? = null
            for(i in 0 until page.length()) {
                val raw = page.getJSONObject(i)
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
            loadCache(account) // Each complete page is already durable, including partial failure.
            if(completed) break
            require(last != null && last != before)
            before = last
        }
        // Shared by manual refresh, sign-in, provider sync and background work.
        // Success updates the cache timestamp in place; banners are for actionable problems.
        mutable.value = mutable.value.copy(needsSignIn = false)
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
        detailLimit: Int = 24
    ): GarminConnectionStatus = withContext(Dispatchers.IO) {
        require(pageLimit in 1..100 && detailLimit in 0..24)
        mutex.withLock {
            // The source must use this installation's local Garmin owner or
            // the currently signed-in legacy owner. Never accept another ID.
            val account=source.accountId
            if(account!=localGarminOwner && account!=mutable.value.account)
                throw GarminAuthenticationException("Garmin connection does not belong to this local profile")
            // Zone refresh is independent from activity import and manual zone settings.
            // A failed request retains cached BPM floors and cannot fail the run sync.
            val zoneStore=GarminZonePreferenceStore(appContext)
            val lastZoneSync=zoneStore.load(account)?.syncedAtMillis ?: 0L
            val now=System.currentTimeMillis()
            if(now-lastZoneSync>=24L*60*60*1000) {
                runCatching {
                    GarminHeartRateZones.forRunning(source.heartRateZones(account))?.let {
                        zoneStore.save(account,it,now)
                    }
                }
            }
            var cursor:String?=null
            var pages=0
            var imported=0
            val seen=HashSet<String>()
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
                if(account==localGarminOwner) loadLocalGarminCache()
                else loadCache(account) // Durable, offline-accessible after each completed page.
                pages++
                val next=page.nextCursor
                if(next!=null && (next==cursor || next.toIntOrNull()==null))
                    throw GarminProtocolException("Garmin pagination did not advance safely")
                cursor=next
            } while(cursor!=null)
            // An explicit sync also caches a bounded number of genuine FIT
            // recordings for Garmin runs. The next tap resumes from missing
            // details; no previously saved source summary is modified.
            val pending = mutable.value.activities.filter { activity ->
                activity.source == "garmin" && DashboardSummary.isRun(activity) &&
                    (activity.id in mutable.value.localGarminIds)==(account==localGarminOwner) &&
                    activity.id.startsWith("garmin-") &&
                    !mutable.value.details.containsKey(activity.id)
            }
            var completedDetails = 0
            var failedDetails = 0
            var interruption: String? = null
            // Prefer missing recordings even when startup has left the memory detail cache empty.
            // Existing rows remain eligible for placeholder repair and validation.
            val savedRecordingIds = HashSet<String>()
            store.readableDatabase.query("records", arrayOf("record_id"),
                "account=? AND kind=?", arrayOf(account, "activity_detail"), null, null, null
            ).use { rows ->
                while (rows.moveToNext()) savedRecordingIds.add(rows.getString(0))
            }
            val orderedPending = pending.sortedWith(
                compareBy<ActivityRecord> { !garminSyncPreferences.readyForDetail(account, it.id) }
                    .thenBy { it.id in savedRecordingIds }
            )
            for (activity in orderedPending.take(detailLimit)) {
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
                    // Malformed FIT, missing numeric IDs, and source mismatches
                    // are held behind healthy records for the next day.
                    garminSyncPreferences.noteDetailFailure(account, activity.id)
                    failedDetails++
                }
            }
            val remaining = pending.count {
                !mutable.value.details.containsKey(it.id)
            }
            if (pageLimit == 100) garminSyncPreferences.acknowledgeManualGarminSync(account)
            reconcileActivityMatchesAndAnalysis()
            GarminConnectionStatus(
                state = "connected",
                lastSuccessfulSync = java.time.Instant.now(),
                importedCount = imported,
                error = interruption,
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
            val account = localGarminOwner
            var success = true
            val interval = if (foreground) 30L * 60_000 else 6L * 60 * 60_000
            if (garminSyncPreferences.automatic(account)) {
                val source = GarminPersonalSource(
                    account, GarminCredentialVault(appContext), GarminPersonalClient())
                val connected = runCatching { source.status().state == "connected" }
                    .getOrDefault(false)
                // Do not consume the automatic-check window while Garmin is
                // disconnected; a newly linked account should remain eligible.
                if (connected &&
                    garminSyncPreferences.claim(account, "garmin", interval)) {
                    try {
                        val result = syncGarmin(source, pageLimit = 2, detailLimit = 3)
                        if (result.error != null) success = false
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The original offline cache stays intact. An app-resume
                        // check never overwrites foreground error messages.
                        success = false
                    }
                }
            }
            if (ready.account != null && !ready.needsSignIn &&
                garminSyncPreferences.cloudBackup(ready.account) &&
                garminSyncPreferences.claim(ready.account, "cloud", interval)) {
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

    /** Cloud backup requires StrideLab sign-in; Garmin itself remains local. */
    internal suspend fun backupGarminCloud(maxRecords: Int = 12): Int =
        withContext(Dispatchers.IO) {
            require(maxRecords in 1..24)
            mutex.withLock {
                val cloudAccount = mutable.value.account
                    ?: throw GarminAuthenticationException("Sign in to StrideLab to use cloud backup")
                require(garminSyncPreferences.cloudBackup(cloudAccount)) {
                    "Enable optional Garmin cloud backup before uploading records"
                }
                // Local Garmin history is never rewritten just to create a
                // cloud snapshot. Translate only a copy of the payload to the
                // authenticated cloud owner before gateway validation/RLS.
                val candidates = mutableListOf<Triple<String, String, String>>()
                val db = store.readableDatabase
                for(owner in listOf(localGarminOwner,cloudAccount).distinct()) {
                    db.query("records", arrayOf("kind", "record_id"),
                        "account=? AND kind IN (?,?)",
                        arrayOf(owner, "activity", "activity_detail"),
                        null, null, "kind ASC,record_id ASC").use { cursor ->
                        while(cursor.moveToNext() && candidates.size < maxRecords) {
                            val kind=cursor.getString(0)
                            val id=cursor.getString(1)
                            val summary = store.get(owner, "activity", id) ?: continue
                            if (runCatching { JSONObject(summary.payload).optString("source") }
                                    .getOrNull() != "garmin") continue
                            val payload = if (kind == "activity_detail")
                                store.getActivityDetailPayloadChunked(owner, id) ?: continue
                            else summary.payload
                            if (!GarminCloudBackupGateway.fitsUpload(payload)) continue
                            val cloudPayload = if(owner==cloudAccount) payload else
                                JSONObject(payload).put("user_id",cloudAccount).toString()
                            // Large FIT streams are retained locally, not truncated.
                            if(!GarminCloudBackupGateway.fitsUpload(cloudPayload)) continue
                            if(garminSyncPreferences.needsCloudUpload(
                                cloudAccount,kind,id,cloudPayload)) {
                                candidates.add(Triple(kind,id,cloudPayload))
                            }
                        }
                    }
                    if(candidates.size>=maxRecords) break
                }
                var uploaded=0
                for((kind,id,payload) in candidates) {
                    authenticated { session -> garminCloud.upload(session,kind,id,payload) }
                    garminSyncPreferences.acknowledgeCloudUpload(cloudAccount,kind,id,payload)
                    uploaded++
                }
                uploaded
            }
        }

    /**
     * Insert-only cloud recovery, translated into the local Garmin namespace.
     * Never overwrites a local original, cloud account history or FIT recording.
     */
    internal suspend fun restoreGarminCloud(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cloudAccount = mutable.value.account
                ?: throw GarminAuthenticationException("Sign in to StrideLab to restore cloud backup")
            require(garminSyncPreferences.cloudBackup(cloudAccount)) {
                "Enable optional Garmin cloud backup before restoring"
            }
            // A different cloud login must never resume another user's offsets.
            val restoreKey = "garmin-restore:" + cloudAccount
            var restored=0
            for(kind in listOf("activity","activity_detail")) {
                val size=if(kind=="activity") 25 else 1
                var offset=garminSyncPreferences.restoreOffset(restoreKey,kind)
                val batches=if(kind=="activity") 2 else 10
                for(batch in 0 until batches) {
                    val page=authenticated { session ->
                        garminCloud.readPage(session,kind,offset,size)
                    }
                    for(row in page) {
                        val exists = if (kind == "activity_detail")
                            store.getActivityDetailPayloadChunked(localGarminOwner, row.recordId) != null
                        else store.get(localGarminOwner,kind,row.recordId) != null
                        if (exists) continue
                        if(kind=="activity_detail" &&
                            store.get(localGarminOwner,"activity",row.recordId)==null) continue
                        val localPayload = JSONObject(row.payload)
                            .put("user_id",localGarminOwner).toString()
                        // Recheck the translated local identity, source and ID.
                        if(kind=="activity") {
                            val activity=ActivityRecord.parse(localPayload,localGarminOwner)
                            require(activity.id==row.recordId && activity.source=="garmin")
                            store.applyRemotePage(localGarminOwner,kind,
                                listOf(TrainingStore.RemoteRecord(row.recordId,localPayload,null)),
                                null,System.currentTimeMillis())
                        } else {
                            ActivityDetail.parse(JSONObject(localPayload),localGarminOwner,row.recordId)
                            store.saveLocalGarminRecording(localGarminOwner, row.recordId, localPayload,
                                System.currentTimeMillis())
                        }
                        restored++
                    }
                    offset+=page.size
                    garminSyncPreferences.setRestoreOffset(restoreKey,kind,offset)
                    if(page.size<size) {
                        garminSyncPreferences.setRestoreOffset(restoreKey,kind,0)
                        break
                    }
                }
            }
            if(restored>0)loadLocalGarminCache()
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
            mutable.value = mutable.value.copy(garminRecordingLoad = GarminRecordingLoad(id))
            try {
                val account = source.accountId
                if(account != localGarminOwner && account != mutable.value.account)
                    throw GarminAuthenticationException("Garmin source does not belong to this profile")
                val activity = mutable.value.activities.firstOrNull {
                    it.id == id && it.source == "garmin" &&
                        ((it.id in mutable.value.localGarminIds)==(account==localGarminOwner))
                } ?: throw GarminProtocolException("Garmin source is not saved for this profile")
                cacheGarminRecording(account, activity, source, upgradeCadence = true)
            } catch (error: CancellationException) {
                mutable.value = mutable.value.copy(garminRecordingLoad = null)
                throw error
            } catch (error: Exception) {
                val message = (error as? GarminException)?.let { GarminConnectionStatus.fromError(it).error }
                    ?: "Garmin recording could not be loaded. Retry when connected."
                mutable.value = mutable.value.copy(garminRecordingLoad = GarminRecordingLoad(id, message))
                throw error
            }
        }

    /** Sync keeps the original cache. Explicit on-demand load may only upgrade
     * verified cadence, never replace existing GPS/pace/HR/lap measurements. */
    private fun cacheGarminRecording(
        account: String, activity: ActivityRecord, source: GarminPersonalSource,
        upgradeCadence: Boolean = false
    ): ActivityDetail {
        require(activity.source == "garmin")
        val id = activity.id
        val savedPayload: String?
        val previous = try {
            savedPayload = store.getActivityDetailPayloadChunked(account,id)
            GarminGpsDetail.cachedRecording(savedPayload, account, id)
        } catch (_: Exception) {
            throw GarminProtocolException(garminCacheReadError)
        }
        if(previous != null && (!upgradeCadence ||
            (previous.runningDynamicsChecked && previous.samples.any { it.cadenceSpm != null } &&
                previous.workoutSteps.isNotEmpty() &&
                previous.sourceWorkoutReferences.isNotEmpty()))) {
            publishCachedDetail(id, mutable.value.details + (id to previous))
            if (upgradeCadence) reconcileActivityMatchesAndAnalysis()
            return previous
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
        // The separate Garmin workout endpoint is reference context, not proof
        // that any defined step was actually completed. Never block FIT
        // import if optional workout metadata is unavailable.
        val workoutRefs = if (upgradeCadence) runCatching {
            source.workoutDefinitionsForSource(account, externalId, numericId)
        }.getOrDefault(emptyList()) else emptyList()
        val raw = try {
            GarminGpsDetail.payload(account, activity, fit, System.currentTimeMillis(), workoutRefs)
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin FIT source, timing or distance could not be verified")
        }
        val parsed = try {
            ActivityDetail.parse(raw, account, id)
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin recording samples failed validation")
        }
        val storedPayload = if (previous != null) {
            // Cadence or actual FIT workout-step evidence may be added to an existing
            // cache, but only when its sample/lap identity matches exactly.
            // Previously imported GPS, pace, HR and source summaries remain untouched.
            val oldRaw = JSONObject(requireNotNull(savedPayload))
            val merged = try {
                GarminGpsDetail.mergeRecordedCadence(oldRaw, raw, account, id)
            } catch (_: Exception) {
                throw GarminProtocolException(
                    "Garmin FIT samples changed; saved GPS and measurements were retained")
            }
            if (merged == null) {
                publishCachedDetail(id, mutable.value.details + (id to previous))
                reconcileActivityMatchesAndAnalysis()
                return previous
            }
            merged
        } else raw
        val checked = if (previous != null)
            ActivityDetail.parse(storedPayload, account, id) else parsed
        // Keep the validated original Garmin FIT bytes for future local backup.
        // Old previously cached recordings may not have original FIT bytes.
        // Never replace a different cached FIT source or modify a saved summary.
        try {
            FitImportCache(appContext).saveSelected(bytes, account)
            store.saveLocalGarminRecording(account, id, storedPayload.toString(), System.currentTimeMillis())
        } catch (_: Exception) {
            throw GarminProtocolException("Garmin recording could not be saved to the offline cache. Retry loading it.")
        }
        publishCachedDetail(id, mutable.value.details + (id to checked))
        reconcileActivityMatchesAndAnalysis()
        return checked
    }

    private val garminCacheReadError
        get() = "The saved Garmin recording could not be read. The original was retained."

    private fun publishCachedDetail(id: String, details: Map<String, ActivityDetail>) {
        val current = mutable.value
        mutable.value = current.copy(details = details,
            garminRecordingLoad = current.garminRecordingLoad?.takeUnless { it.activityId == id })
    }

    private data class ExecutionPersistenceBundle(
        val matches: Map<String, ActivityMatchRecord>,
        val analyses: Map<String, WorkoutExecutionAnalysis>,
        val diagnostics: Map<String, AthleteDiagnosticSnapshot>,
        val pendingProposals: Map<String, PlanAdaptationProposal>,
        val revisions: Map<String, List<PlanAdaptationRevision>>,
        val cooldowns: List<AdaptationCooldownRecord>
    )

    private fun loadExecutionRecords(owner: String): ExecutionPersistenceBundle {
        val matches = LinkedHashMap<String, ActivityMatchRecord>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "activity-match-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = ActivityMatchRecord.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == activityMatchRecordId(decoded.activityId))
                    decoded
                }.onSuccess { matches[it.activityId] = it }
            }
        }
        val analyses = LinkedHashMap<String, WorkoutExecutionAnalysis>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "execution-analysis-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = WorkoutExecutionAnalysis.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == executionAnalysisRecordId(decoded.plannedSessionId))
                    decoded
                }.onSuccess { analyses[it.plannedSessionId] = it }
            }
        }
        val diagnostics = LinkedHashMap<String, AthleteDiagnosticSnapshot>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "athlete-diagnostic-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = AthleteDiagnosticSnapshot.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == athleteDiagnosticRecordId(decoded.planId))
                    decoded
                }.onSuccess { diagnostics[it.planId] = it }
            }
        }
        val today = java.time.LocalDate.now()
        val pendingProposals = LinkedHashMap<String, PlanAdaptationProposal>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "adaptation-proposal-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = PlanAdaptationProposal.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == adaptationProposalRecordId(decoded.proposalId))
                    decoded
                }.onSuccess { prop ->
                    if (prop.status == AdaptationProposalStatus.PENDING && !today.isAfter(prop.expiresAtDate)) {
                        pendingProposals[prop.planId] = prop
                    }
                }
            }
        }
        val revisionsByPlan = LinkedHashMap<String, MutableList<PlanAdaptationRevision>>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "adaptation-revision-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = PlanAdaptationRevision.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == adaptationRevisionRecordId(decoded.revisionId))
                    decoded
                }.onSuccess { rev ->
                    revisionsByPlan.getOrPut(rev.planId) { mutableListOf() }.add(rev)
                }
            }
        }
        val revisions = revisionsByPlan.mapValues { (_, list) ->
            list.sortedWith(compareBy<PlanAdaptationRevision> { it.appliedAtDate }.thenBy { it.revisionId })
        }
        val cooldowns = ArrayList<AdaptationCooldownRecord>()
        store.readableDatabase.query(
            "records", arrayOf("record_id", "payload"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, "workout", "adaptation-cooldown-%"),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching {
                    val decoded = AdaptationCooldownRecord.decode(JSONObject(cursor.getString(1)), owner)
                    require(cursor.getString(0) == adaptationCooldownRecordId(decoded))
                    decoded
                }.onSuccess { cooldowns += it }
            }
        }
        return ExecutionPersistenceBundle(matches, analyses, diagnostics, pendingProposals, revisions, cooldowns)
    }

    private fun markManualLinkLocked(activityId: String, sessionId: String) {
        val current = mutable.value
        val existing = current.activityMatches[activityId]
        val activity = current.activities.find { it.id == activityId }
        val session = current.plans.flatMap { it.sessions }.find { it.id == sessionId }
        val offset = if (activity != null && session != null) {
            java.time.temporal.ChronoUnit.DAYS.between(
                session.date,
                activity.startedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            ).toInt().takeIf { it in -1..1 }
        } else null
        val record = ActivityMatchRecord(
            activityId = activityId,
            plannedSessionId = sessionId,
            matchStatus = ActivityMatchStatus.MANUALLY_LINKED,
            confidenceScore = 1.0,
            dateOffsetDays = offset,
            reasonCodes = listOf("MANUAL_OVERRIDE_LINKED"),
            isManualOverride = true,
            candidates = existing?.candidates ?: emptyList(),
            evaluatedAtEpochMs = System.currentTimeMillis()
        )
        persistActivityMatchesLocked(mapOf(activityId to record))
        mutable.value = mutable.value.copy(
            activityMatches = current.activityMatches + (activityId to record)
        )
    }

    private fun markManualRejectionLocked(activityId: String, plannedSessionId: String) {
        val current = mutable.value
        val existing = current.activityMatches[activityId]
        val record = ActivityMatchRecord(
            activityId = activityId,
            plannedSessionId = plannedSessionId,
            matchStatus = ActivityMatchStatus.MANUALLY_REJECTED,
            confidenceScore = 0.0,
            dateOffsetDays = null,
            reasonCodes = listOf("MANUAL_OVERRIDE_REJECTED"),
            isManualOverride = true,
            candidates = existing?.candidates?.filterNot { it.plannedSessionId == plannedSessionId }
                ?: emptyList(),
            evaluatedAtEpochMs = System.currentTimeMillis()
        )
        persistActivityMatchesLocked(mapOf(activityId to record))
        val owner = LocalPlanOwnership.activeOwner(current.account, localPlanOwner)
        store.deleteRecord(owner, "workout", executionAnalysisRecordId(plannedSessionId))
        mutable.value = mutable.value.copy(
            activityMatches = current.activityMatches + (activityId to record),
            executionAnalyses = current.executionAnalyses - plannedSessionId
        )
    }

    private fun reconcileActivityMatchesAndAnalysis() {
        val current = mutable.value
        val plan = current.plans.firstOrNull { it.status == "approved" }
            ?: current.plans.firstOrNull { it.status != "archived" }
            ?: return
        val nowMs = System.currentTimeMillis()
        val matched = Engine15ActivityAutoMatcher.match(
            activities = current.activities,
            sessions = plan.sessions,
            detailsByActivityId = current.details,
            existingMatches = current.activityMatches,
            nowEpochMs = nowMs
        )
        val mergedMatches = LinkedHashMap(current.activityMatches).apply {
            putAll(matched)
        }

        val sessionById = plan.sessions.associateBy { it.id }
        val activityById = current.activities.associateBy { it.id }
        val updatedAnalyses = LinkedHashMap(current.executionAnalyses)

        for (match in matched.values) {
            if (match.matchStatus != ActivityMatchStatus.AUTO_LINKED &&
                match.matchStatus != ActivityMatchStatus.MANUALLY_LINKED) {
                continue
            }
            val sessionId = match.plannedSessionId ?: continue
            val session = sessionById[sessionId] ?: continue
            val activity = activityById[match.activityId] ?: continue
            val detail = current.details[activity.id]
            // The in-memory detail window is bounded. Eviction is not a loss of evidence.
            if (detail == null && updatedAnalyses[sessionId]?.activityId == activity.id) continue
            val hrZones = resolveExecutionHrZones(detail)
            val analysis = Engine15ExecutionAnalyzer.analyze(
                session = session,
                activity = activity,
                detail = detail,
                athleteHrZones = hrZones,
                analyzedAtEpochMs = nowMs
            )
            updatedAnalyses[sessionId] = analysis
        }

        val changedMatches = mergedMatches.filter { (k, v) -> current.activityMatches[k] != v }
        val changedAnalyses = updatedAnalyses.filter { (k, v) -> current.executionAnalyses[k] != v }
        if (changedMatches.isNotEmpty()) persistActivityMatchesLocked(changedMatches)
        if (changedAnalyses.isNotEmpty()) persistExecutionAnalysesLocked(changedAnalyses)

        val hrZones = resolveExecutionHrZones(
            current.details.values.firstOrNull { it.sourceHeartRateZones != null }
        )
        val newDiagnostic = Engine15AthleteDiagnosticsEngine.evaluate(
            plan = plan,
            activities = current.activities,
            matchesByActivityId = mergedMatches,
            analysesBySessionId = updatedAnalyses,
            athleteHrZones = hrZones,
            asOfDate = java.time.LocalDate.now(),
            evaluatedAtEpochMs = nowMs
        )
        val prevDiagnostic = current.athleteDiagnostics[plan.id]
        val diagnosticChanged = prevDiagnostic == null ||
            prevDiagnostic.copy(evaluatedAtEpochMs = 0L) != newDiagnostic.copy(evaluatedAtEpochMs = 0L)
        val updatedDiagnostics = if (diagnosticChanged) {
            persistAthleteDiagnosticsLocked(mapOf(plan.id to newDiagnostic))
            current.athleteDiagnostics + (plan.id to newDiagnostic)
        } else {
            current.athleteDiagnostics
        }

        val today = java.time.LocalDate.now()
        val existingForPlan = current.pendingAdaptationProposals[plan.id]
            ?.takeIf { !today.isAfter(it.expiresAtDate) }
        val newProposal = Engine15PlanAdaptationEngine.evaluateProposal(
            plan = plan,
            diagnostic = newDiagnostic,
            activities = current.activities,
            matchesByActivityId = mergedMatches,
            cooldowns = current.adaptationCooldowns,
            existingProposals = listOfNotNull(existingForPlan),
            asOfDate = today
        )
        val updatedPendingProposals = LinkedHashMap(current.pendingAdaptationProposals)
        val proposalChanged = if (newProposal != null) {
            val prev = existingForPlan
            if (prev == null || prev.copy(createdAtDate = today, expiresAtDate = today) != newProposal.copy(createdAtDate = today, expiresAtDate = today)) {
                persistAdaptationProposalLocked(newProposal)
                updatedPendingProposals[plan.id] = newProposal
                true
            } else false
        } else {
            val hadExpiredOrObsolete = current.pendingAdaptationProposals.containsKey(plan.id)
            if (hadExpiredOrObsolete) {
                current.pendingAdaptationProposals[plan.id]?.let { old ->
                    if (today.isAfter(old.expiresAtDate)) {
                        persistAdaptationProposalLocked(old.copy(status = AdaptationProposalStatus.EXPIRED))
                    }
                }
                updatedPendingProposals.remove(plan.id)
                true
            } else false
        }

        if (changedMatches.isNotEmpty() || changedAnalyses.isNotEmpty() || diagnosticChanged || proposalChanged) {
            mutable.value = mutable.value.copy(
                activityMatches = mergedMatches,
                executionAnalyses = updatedAnalyses,
                athleteDiagnostics = updatedDiagnostics,
                pendingAdaptationProposals = updatedPendingProposals
            )
        }
    }

    private fun resolveExecutionHrZones(detail: ActivityDetail?): ExecutionHrZoneProfile? {
        val owner = mutable.value.account ?: localGarminOwner
        val cachedGarmin = GarminZonePreferenceStore(appContext).load(owner)?.profile
            ?: GarminZonePreferenceStore(appContext).load(localGarminOwner)?.profile
        ExecutionHrZoneProfile.fromGarmin(cachedGarmin)?.let { return it }
        return ExecutionHrZoneProfile.fromActivitySourceZones(detail?.sourceHeartRateZones)
    }

    private fun persistActivityMatchesLocked(records: Map<String, ActivityMatchRecord>) {
        if (records.isEmpty()) return
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val rows = records.values.map { record ->
            val id = activityMatchRecordId(record.activityId)
            val raw = record.encode(owner).toString()
            RecordPolicy.record(owner, "workout", id, raw)
            TrainingStore.RemoteRecord(id, raw, null)
        }
        store.applyRemotePage(owner, "workout", rows, null, System.currentTimeMillis())
    }

    private fun persistExecutionAnalysesLocked(records: Map<String, WorkoutExecutionAnalysis>) {
        if (records.isEmpty()) return
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val rows = records.values.map { record ->
            val id = executionAnalysisRecordId(record.plannedSessionId)
            val raw = record.encode(owner).toString()
            RecordPolicy.record(owner, "workout", id, raw)
            TrainingStore.RemoteRecord(id, raw, null)
        }
        store.applyRemotePage(owner, "workout", rows, null, System.currentTimeMillis())
    }

    private fun persistAthleteDiagnosticsLocked(records: Map<String, AthleteDiagnosticSnapshot>) {
        if (records.isEmpty()) return
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val rows = records.values.map { record ->
            val id = athleteDiagnosticRecordId(record.planId)
            val raw = record.encode(owner).toString()
            RecordPolicy.record(owner, "workout", id, raw)
            TrainingStore.RemoteRecord(id, raw, null)
        }
        store.applyRemotePage(owner, "workout", rows, null, System.currentTimeMillis())
    }

    private fun persistAdaptationProposalLocked(proposal: PlanAdaptationProposal) {
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val id = adaptationProposalRecordId(proposal.proposalId)
        val raw = proposal.encode(owner).toString()
        RecordPolicy.record(owner, "workout", id, raw)
        store.applyRemotePage(owner, "workout", listOf(TrainingStore.RemoteRecord(id, raw, null)), null, System.currentTimeMillis())
    }

    private fun persistAdaptationRevisionLocked(revision: PlanAdaptationRevision) {
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val id = adaptationRevisionRecordId(revision.revisionId)
        val raw = revision.encode(owner).toString()
        RecordPolicy.record(owner, "workout", id, raw)
        store.applyRemotePage(owner, "workout", listOf(TrainingStore.RemoteRecord(id, raw, null)), null, System.currentTimeMillis())
    }

    private fun persistAdaptationCooldownLocked(cooldown: AdaptationCooldownRecord) {
        val owner = LocalPlanOwnership.activeOwner(mutable.value.account, localPlanOwner)
        val id = adaptationCooldownRecordId(cooldown)
        val raw = cooldown.encode(owner).toString()
        RecordPolicy.record(owner, "workout", id, raw)
        store.applyRemotePage(owner, "workout", listOf(TrainingStore.RemoteRecord(id, raw, null)), null, System.currentTimeMillis())
    }

    internal fun close() { scope.cancel(); store.close() }

    suspend fun signOut() = operation("Signing out…") {
        val account = mutable.value.account ?: return@operation
        val saved = runCatching { vault.read(account) }.getOrNull()
        // Preserve a previously linked Garmin connection in this device's
        // separate local namespace without retaining cloud authentication.
        copyLegacyGarminConnection(account)
        // End cloud access before waiting for server session revocation.
        vault.clear(account)
        check(preferences.edit().remove("active").commit())
        mutable.value = TrainingState(initializing = false,busy = true,
            localGarminOwner=localGarminOwner,localPlanOwner=localPlanOwner,
            task = "Finishing sign-out…")
        loadLocalPlanCache() // Show only local plans; cloud plans remain under their cloud owner.
        loadLocalGarminCache() // Hide cloud activities, retain local Garmin history.
        val revoked = saved != null && runCatching { gateway.signOut(saved); true }.getOrDefault(false)
        mutable.value = mutable.value.copy(message = if(revoked) null else "Signed out on this device. Server session revocation was unavailable; saved files were retained.")
    }
}

/** Stable, bounded local metadata key; never changes the Garmin source ID. */
private fun sha256PrefixKey(prefix: String, id: String): String {
    RecordPolicy.identifier(id)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8))
    return prefix + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun localActivityTitleKey(activityId: String): String =
    sha256PrefixKey("local-activity-title-", activityId)

private fun activityMatchRecordId(activityId: String): String =
    sha256PrefixKey("activity-match-", activityId)

private fun executionAnalysisRecordId(plannedSessionId: String): String =
    sha256PrefixKey("execution-analysis-", plannedSessionId)

private fun athleteDiagnosticRecordId(planId: String): String =
    sha256PrefixKey("athlete-diagnostic-", planId)

private fun adaptationProposalRecordId(proposalId: String): String =
    sha256PrefixKey("adaptation-proposal-", proposalId)

private fun adaptationRevisionRecordId(revisionId: String): String =
    sha256PrefixKey("adaptation-revision-", revisionId)

private fun adaptationCooldownRecordId(cooldown: AdaptationCooldownRecord): String =
    sha256PrefixKey(
        "adaptation-cooldown-",
        "${cooldown.planId}:${cooldown.adaptationType.name}:${cooldown.disruptionFingerprint ?: ""}"
    )

private fun validatedActivityTitle(input:String):String {
    val title = input.trim()
    require(title.isNotBlank() && title.length <= 100 &&
        title.none { Character.isISOControl(it) }) {
        "Activity title must contain 1–100 printable characters."
    }
    return title
}
