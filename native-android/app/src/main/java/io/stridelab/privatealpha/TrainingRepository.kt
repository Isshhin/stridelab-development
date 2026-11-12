package io.stridelab.privatealpha

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

internal data class TrainingState(
    val initializing: Boolean = true,
    val account: String? = null,
    /** Persistent on-device Garmin namespace, independent of cloud sign-in. */
    val localGarminOwner: String? = null,
    val localGarminIds: Set<String> = emptySet(),
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
    val task: String? = null
)

/** All network/database/encryption work is serialized off the main thread, including workers. */
internal class TrainingRepository(context: Context, private val gateway: TrainingGateway = SupabaseGateway(),
    databaseName: String = TrainingStore.DATABASE, preferencesName: String = "native-account-v1") {
    private val store = TrainingStore(context, databaseName)
    private val appContext = context.applicationContext
    private val vault = SessionVault(context)
    internal val garminSyncPreferences = GarminSyncPreferences(context)
    private val garminCloud = GarminCloudBackupGateway()
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val localGarminOwner: String = preferences.getString("local-garmin-owner-v1", null)
        ?.takeIf { runCatching { RecordPolicy.identifier(it); it.startsWith("local-garmin-") }.getOrDefault(false) }
        ?: ("local-garmin-" + java.util.UUID.randomUUID().toString()).also {
            check(preferences.edit().putString("local-garmin-owner-v1", it).commit())
        }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { mutex.withLock {
        val account = preferences.getString("active", null)
        try {
            mutable.value = TrainingState(initializing = true, account = account, localGarminOwner = localGarminOwner)
            if (account != null) {
                loadCache(account)
                // Retired Intervals.icu connection settings remain untouched on
                // disk but can no longer trigger a provider connection.
                if(vault.read(account) == null) mutable.value = mutable.value.copy(needsSignIn = true,
                    message = "Sign in again for cloud features. Saved Garmin activities remain available.")
            } else {
                loadLocalGarminCache()
            }
            mutable.value = mutable.value.copy(initializing = false)
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(initializing = false, localGarminOwner = localGarminOwner,
                needsSignIn = account != null,
                message = "Some saved data could not be loaded. Existing files were retained.")
            runCatching { loadLocalGarminCache() }
        }
    } } }

    private fun loadCache(account: String) {
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
        val orderedRecords=records.sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id })
        // Summaries are independent of GPS streams, plans and secondary caches.
        // Publish them first so a secondary read failure cannot erase history
        // from the visible state. This never mutates any persisted record.
        mutable.value=mutable.value.copy(activities=orderedRecords)
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
        val titledRecords = orderedRecords.map { record ->
            localTitles[record.id]?.let { record.copy(title=it) } ?: record
        }
        mutable.value=mutable.value.copy(activities=titledRecords)
        val allowedIds=records.mapTo(HashSet<String>()) { it.id }
        val details = mutable.value.details.filterKeys { it in allowedIds }.toMutableMap()
        // The full recording cache can exceed tens of MB. Query IDs only:
        // reading every large payload through one CursorWindow used to abort
        // loadCache before the 201+ independent activity summaries appeared.
        val cachedDetailIds=HashSet<String>()
        store.readableDatabase.query("records",arrayOf("record_id"),"account=? AND kind=?",arrayOf(account,"activity_detail"),null,null,null).use { c ->
            while(c.moveToNext())cachedDetailIds.add(c.getString(0))
        }
        // Warm a small, recent set for cards. Older recordings stay on disk
        // and are fetched in bounded chunks when their card/detail is viewed.
        orderedRecords.asSequence().map { it.id }
            .filter { it in cachedDetailIds && it !in details }
            .take((6-details.size).coerceAtLeast(0))
            .forEach { id ->
                runCatching {
                    store.getActivityDetailPayloadChunked(account,id)
                        ?.let { ActivityDetail.parse(JSONObject(it),account,id) }
                }.getOrNull()?.let { details[id]=it }
            }
        val wellness=mutableListOf<WellnessRecord>()
        store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=?",arrayOf(account,"wellness"),null,null,null).use { c ->
            while(c.moveToNext()) runCatching {
                WellnessRecord.parse(JSONObject(c.getString(0)),account)?.let {
                    if(ActiveActivitySources.accepts(it.source))wellness.add(it)
                }
            }
        }
        val plans=mutableListOf<PlanSnapshot>();var unsupportedPlans=0
        store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=?",arrayOf(account,"plan"),null,null,null).use { c ->
            while(c.moveToNext()) runCatching { PlanSnapshot.decode(JSONObject(c.getString(0)),account) }.onSuccess { plans.add(it) }.onFailure { unsupportedPlans++ }
        }
        val shelf=store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=? AND record_id=?",arrayOf(account,"exercise","library-shelf-v1"),null,null,null).use { c -> if(c.moveToFirst())LibraryShelf.decode(JSONObject(c.getString(0)),account) else LibraryShelf() }
        val templates=mutableListOf<SessionTemplate>()
        store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=? AND record_id LIKE ?",arrayOf(account,"exercise","session-template-%"),null,null,null).use { c -> while(c.moveToNext())runCatching { SessionTemplate.decode(JSONObject(c.getString(0)),account) }.onSuccess { templates.add(it) } }
        val drafts=mutableMapOf<Pair<String,String>,WorkoutEntryDraft>()
        store.readableDatabase.query("records",arrayOf("record_id","payload"),"account=? AND kind=? AND record_id LIKE ?",arrayOf(account,"workout","entry-draft-%"),null,null,null).use { c -> while(c.moveToNext())runCatching { WorkoutEntryDraft.decode(JSONObject(c.getString(1)),account).also { require(it.recordId==c.getString(0)) } }.onSuccess { if(validDraft(it,plans))drafts[it.key]=it } }
        val cursor = store.cursor(account, "activity")
        val last = cursor?.let { runCatching { JSONObject(it).getLong("completedAt") }.getOrNull() }
        mutable.value = mutable.value.copy(activities = titledRecords, lastSync = last, details = details, wellness = wellness, shelf=shelf, templates=templates, workoutDrafts=drafts, plans = plans.sortedByDescending { it.createdAt },
            message = if(unsupportedPlans>0) "$unsupportedPlans saved plans require a supported contract and were retained for review." else if(rejected > 0) "$rejected saved activities contain unsupported data and were retained for review." else mutable.value.message)
        loadLocalGarminCache()
    }

    /** Reads only on-device Garmin history. Cloud-user records remain isolated. */
    private fun loadLocalGarminCache() {
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
        val previous = mutable.value
        val activities = (previous.activities.filterNot {
            it.id in previous.localGarminIds || it.id in ids
        } + titled).sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id })
        val details = previous.details.filterKeys { id ->
            id !in ids || id in previous.localGarminIds
        }.toMutableMap()
        // Only recent details are warmed. Huge FIT payloads are read in chunks.
        val recent = titled.sortedByDescending { it.startedAt }.map { it.id }
            .filter { it !in details }.take(6)
        for (id in recent) runCatching {
            store.getActivityDetailPayloadChunked(localGarminOwner,id)
                ?.let { ActivityDetail.parse(JSONObject(it),localGarminOwner,id) }
        }.getOrNull()?.let { details[id] = it }
        mutable.value = previous.copy(activities=activities, details=details,
            localGarminOwner=localGarminOwner, localGarminIds=ids)
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
            if(mutable.value.activities.none { it.id==id })return@withLock false
            if(id in mutable.value.details)return@withLock true
            val cached=runCatching {
                store.getActivityDetailPayloadChunked(account,id)
                    ?.let { ActivityDetail.parse(JSONObject(it),account,id) }
            }.getOrNull() ?: return@withLock false
            val recent=LinkedHashMap(mutable.value.details)
            recent.remove(id)
            recent[id]=cached
            while(recent.size>12)recent.remove(recent.keys.first())
            mutable.value=mutable.value.copy(details=recent)
            true
        }
    }

    suspend fun loadDetail(id: String) = operation("Loading recorded streams…") {
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        require(mutable.value.activities.any { it.id == id })
        val raw = authenticated { gateway.detail(it,id) }
        val parsed = ActivityDetail.parse(raw,account,id)
        store.applyRemotePage(account,"activity_detail",listOf(TrainingStore.RemoteRecord(id,raw.toString(),null)),null,System.currentTimeMillis())
        mutable.value = mutable.value.copy(details = mutable.value.details + (id to parsed))
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
    suspend fun signIn(email: String, password: String) = operation("Signing in securely…") {
        require(email.isNotBlank() && password.isNotEmpty())
        val session = try {
            gateway.signIn(email, password).also { gateway.verify(it) }
        } catch(e: ServiceFailure) {
            // A failed login attempt must not invalidate a different, still-active session.
            throw ServiceFailure(e.status, e.message ?: "Sign-in was not accepted", requiresSignIn = false)
        }
        vault.save(session) // No account activation unless encryption succeeds.
        check(preferences.edit().putString("active", session.account).commit())
        mutable.value = TrainingState(initializing = false, account = session.account, busy = true, loginGeneration = mutable.value.loginGeneration + 1)
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
            // Deprioritize source-specific FIT failures. Otherwise the same
            // invalid early records can permanently starve later runs.
            val orderedPending = pending.sortedBy {
                if (garminSyncPreferences.readyForDetail(account, it.id)) 0 else 1
            }
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

    /** Cloud backup requires StrideLab sign-in; Garmin itself remains local. */
    internal suspend fun backupGarminCloud(maxRecords: Int = 12): Int =
        withContext(Dispatchers.IO) {
            require(maxRecords in 1..24)
            mutex.withLock {
                val cloudAccount = mutable.value.account
                    ?: throw GarminAuthenticationException("Sign in to StrideLab to use cloud backup")
                require(garminSyncPreferences.cloudBackup(localGarminOwner)) {
                    "Enable optional Garmin cloud backup before uploading records"
                }
                // Local Garmin history is never rewritten just to create a
                // cloud snapshot. Translate only a copy of the payload to the
                // authenticated cloud owner before gateway validation/RLS.
                val candidates = mutableListOf<Triple<String, String, String>>()
                val db = store.readableDatabase
                for(owner in listOf(localGarminOwner,cloudAccount).distinct()) {
                    db.query("records", arrayOf("kind", "record_id", "payload"),
                        "account=? AND kind IN (?,?)",
                        arrayOf(owner, "activity", "activity_detail"),
                        null, null, "kind ASC,record_id ASC").use { cursor ->
                        while(cursor.moveToNext() && candidates.size < maxRecords) {
                            val kind=cursor.getString(0)
                            val id=cursor.getString(1)
                            val payload=cursor.getString(2)
                            if(kind=="activity") {
                                if(runCatching { JSONObject(payload).optString("source") }
                                        .getOrNull()!="garmin") continue
                            } else {
                                val summary=store.get(owner,"activity",id) ?: continue
                                if(runCatching { JSONObject(summary.payload).optString("source") }
                                        .getOrNull()!="garmin") continue
                            }
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
            require(garminSyncPreferences.cloudBackup(localGarminOwner)) {
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
                        if(store.get(localGarminOwner,kind,row.recordId)!=null) continue
                        if(kind=="activity_detail" &&
                            store.get(localGarminOwner,"activity",row.recordId)==null) continue
                        val localPayload = JSONObject(row.payload)
                            .put("user_id",localGarminOwner).toString()
                        // Recheck the translated local identity, source and ID.
                        if(kind=="activity") {
                            val activity=ActivityRecord.parse(localPayload,localGarminOwner)
                            require(activity.id==row.recordId && activity.source=="garmin")
                        } else ActivityDetail.parse(
                            JSONObject(localPayload),localGarminOwner,row.recordId)
                        store.applyRemotePage(localGarminOwner,kind,
                            listOf(TrainingStore.RemoteRecord(
                                row.recordId,localPayload,null)),
                            null,System.currentTimeMillis())
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
            val account = source.accountId
            if(account != localGarminOwner && account != mutable.value.account)
                throw GarminAuthenticationException("Garmin source does not belong to this profile")
            val activity = mutable.value.activities.firstOrNull {
                it.id == id && it.source == "garmin" &&
                    ((it.id in mutable.value.localGarminIds)==(account==localGarminOwner))
            } ?: throw GarminProtocolException("Garmin source is not saved for this profile")
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
        val savedPayload = store.getActivityDetailPayloadChunked(account,id)
        val previous = if (savedPayload != null) {
            try {
                ActivityDetail.parse(JSONObject(savedPayload), account, id)
            } catch (_: Exception) {
                throw GarminProtocolException(
                    "Previously saved Garmin recording needs review; the original was retained")
            }
        } else null
        if(previous != null && (!upgradeCadence ||
            previous.samples.any { it.cadenceSpm != null })) {
            mutable.value = mutable.value.copy(
                details = mutable.value.details + (id to previous))
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
        val storedPayload = if (savedPayload != null) {
            // Only cadence may be added to an existing cache. If the FIT
            // sample identity or timestamps differ, keep the original intact.
            val oldRaw = JSONObject(savedPayload)
            val merged = try {
                GarminGpsDetail.mergeRecordedCadence(oldRaw, raw, account, id)
            } catch (_: Exception) {
                throw GarminProtocolException(
                    "Garmin FIT samples changed; saved GPS and measurements were retained")
            }
            if (merged == null) {
                val unchanged = requireNotNull(previous)
                mutable.value = mutable.value.copy(
                    details = mutable.value.details + (id to unchanged))
                return unchanged
            }
            merged
        } else raw
        val checked = if (savedPayload != null)
            ActivityDetail.parse(storedPayload, account, id) else parsed
        store.applyRemotePage(
            account, "activity_detail",
            listOf(TrainingStore.RemoteRecord(id, storedPayload.toString(), null)),
            null, System.currentTimeMillis()
        )
        mutable.value = mutable.value.copy(details = mutable.value.details + (id to checked))
        return checked
    }

    internal fun close() { scope.cancel(); store.close() }

    suspend fun signOut() = operation("Signing out…") {
        val account = mutable.value.account ?: return@operation
        val saved = runCatching { vault.read(account) }.getOrNull()
        // End local access before waiting for an unavailable network to revoke the server session.
        vault.clear(account)
        check(preferences.edit().remove("active").commit())
        mutable.value = TrainingState(initializing = false,busy = true,
            localGarminOwner=localGarminOwner,task = "Finishing sign-out…")
        loadLocalGarminCache() // Hide cloud records, retain local Garmin history.
        val revoked = saved != null && runCatching { gateway.signOut(saved); true }.getOrDefault(false)
        mutable.value = mutable.value.copy(message = if(revoked) null else "Signed out on this device. Server session revocation was unavailable; saved files were retained.")
    }
}

/** Stable, bounded local metadata key; never changes the Garmin source ID. */
private fun localActivityTitleKey(activityId:String):String {
    RecordPolicy.identifier(activityId)
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(activityId.toByteArray(Charsets.UTF_8))
    return "local-activity-title-" + digest.joinToString("") {
        "%02x".format(it.toInt() and 0xff)
    }
}

private fun validatedActivityTitle(input:String):String {
    val title = input.trim()
    require(title.isNotBlank() && title.length <= 100 &&
        title.none { Character.isISOControl(it) }) {
        "Activity title must contain 1–100 printable characters."
    }
    return title
}
