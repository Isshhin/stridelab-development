package io.stridelab.privatealpha

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class TrainingState(
    val initializing: Boolean = true,
    val account: String? = null,
    val activities: List<ActivityRecord> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val lastSync: Long? = null,
    val needsSignIn: Boolean = false,
    val loginGeneration: Long = 0,
    val intervals: IntervalConnection? = null,
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
    private val vault = SessionVault(context)
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { mutex.withLock {
        val account = preferences.getString("active", null)
        try {
            mutable.value = TrainingState(initializing = true, account = account)
            if (account != null) {
                loadCache(account)
                val cachedConnection = preferences.getString("intervals.$account",null)
                if(cachedConnection != null) runCatching {
                    val cached = JSONObject(cachedConnection)
                    mutable.value = mutable.value.copy(intervals = IntervalConnection.parse(cached,cached.getLong("checkedAt")))
                }
                if(vault.read(account) == null) mutable.value = mutable.value.copy(needsSignIn = true, message = "Sign in again to synchronize. Your saved activities remain available.")
            }
            mutable.value = mutable.value.copy(initializing = false)
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(initializing = false, needsSignIn = true, message = "Saved data could not be fully loaded. Existing files were retained. Try signing in again.")
        }
    } } }

    private fun loadCache(account: String) {
        val records = mutableListOf<ActivityRecord>()
        var rejected = 0
        // No fixed history cap. Stream cursor rows without materializing raw JSON twice.
        store.readableDatabase.query("records", arrayOf("payload"), "account=? AND kind=?", arrayOf(account, "activity"), null, null, null).use { c ->
            while(c.moveToNext()) {
                try { records += ActivityRecord.parse(c.getString(0), account) }
                catch (_: Exception) { rejected++ } // Retain invalid raw records; never invent replacement metrics.
            }
        }
        val details = mutable.value.details.toMutableMap()
        store.readableDatabase.query("records",arrayOf("record_id","payload"),"account=? AND kind=?",arrayOf(account,"activity_detail"),null,null,null).use { c ->
            while(c.moveToNext()) if(!details.containsKey(c.getString(0))) runCatching { details[c.getString(0)] = ActivityDetail.parse(JSONObject(c.getString(1)),account,c.getString(0)) }
        }
        val wellness=mutableListOf<WellnessRecord>()
        store.readableDatabase.query("records",arrayOf("payload"),"account=? AND kind=?",arrayOf(account,"wellness"),null,null,null).use { c ->
            while(c.moveToNext()) runCatching { WellnessRecord.parse(JSONObject(c.getString(0)),account)?.let { wellness.add(it) } }
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
        mutable.value = mutable.value.copy(activities = records.sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id }), lastSync = last, details = details, wellness = wellness, shelf=shelf, templates=templates, workoutDrafts=drafts, plans = plans.sortedByDescending { it.createdAt },
            message = if(unsupportedPlans>0) "$unsupportedPlans saved plans require a supported contract and were retained for review." else if(rejected > 0) "$rejected saved activities contain unsupported data and were retained for review." else mutable.value.message)
    }
    suspend fun restorePlans(raw:String)=operation("Restoring reviewed plan backup…") {
        val account=mutable.value.account ?: throw PlanConstraint("Sign in to the backup's original account before restoring.")
        val additions=PlanBackup.additions(PlanBackup.read(raw,account),mutable.value.plans)
        val db=store.writableDatabase
        db.beginTransaction()
        try {
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
    suspend fun moveSession(id:String,revision:Int,sessionId:String,date:java.time.LocalDate)=changePlan(id,revision) { PlanOperations.move(it,sessionId,date) }
    suspend fun recordWorkout(id:String,revision:Int,sessionId:String,log:WorkoutLog,complete:Boolean)=changePlan(id,revision) { PlanOperations.record(it,sessionId,log,complete) }
    suspend fun markMissed(id:String,revision:Int,sessionId:String,reason:String,skipped:Boolean)=changePlan(id,revision) { PlanOperations.missed(it,sessionId,reason,skipped) }
    suspend fun acceptSuggestion(id:String,revision:Int,sessionId:String)=changePlan(id,revision) { PlanOperations.acceptSuggestion(it,sessionId) }
    suspend fun saveFeedback(id:String,revision:Int,category:String,text:String,sessionId:String?=null)=changePlan(id,revision) { PlanOperations.feedback(it,category,text,sessionId) }
    suspend fun refreshWellness() = operation("Refreshing wellness records…") {
        val account=mutable.value.account ?: throw IllegalStateException("Sign in first")
        val raw=authenticated { gateway.wellness(it) }
        val seen=HashSet<String>(); val rows=ArrayList<TrainingStore.RemoteRecord>()
        for(i in 0 until raw.length()) {
            val row=raw.getJSONObject(i)
            val record=WellnessRecord.parse(row,account) ?: continue
            require(seen.add(record.id))
            rows+=TrainingStore.RemoteRecord(record.id,row.toString(),null)
        }
        store.applyRemotePage(account,"wellness",rows,null,System.currentTimeMillis())
        loadCache(account)
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
            catch (e: ServiceFailure) {
                mutable.value = mutable.value.copy(message = e.message, needsSignIn = e.requiresSignIn || mutable.value.needsSignIn); false
            } catch (_: java.io.IOException) {
                mutable.value = mutable.value.copy(message = "Connection or secure storage is unavailable. Saved activities are retained; retry when connected."); false
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = "This operation could not be verified. Existing records were retained. Please retry."); false
            } finally { mutable.value = mutable.value.copy(busy = false, task = null) }
        }
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
                // These server rows have no contractual revision token. Never fabricate one for edits.
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
    private fun connection(): IntervalConnection {
        val current = authenticated { gateway.intervals(it,"status") }
        val parsed = IntervalConnection.parse(current,System.currentTimeMillis())
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        check(preferences.edit().putString("intervals.$account",parsed.encode()).commit())
        mutable.value = mutable.value.copy(intervals = parsed)
        return parsed
    }
    suspend fun checkIntervals() = operation("Checking Intervals.icu…") { connection() }
    suspend fun syncIntervals() = operation("Synchronizing Intervals.icu…") {
        if(!connection().configured) throw ServiceFailure(409,"Connect Intervals.icu in your existing integration settings before synchronizing.")
        val result = authenticated { gateway.intervals(it,"sync") }
        if(result.optBoolean("ok",false).not() || result.has("error")) throw java.io.IOException("Integration sync did not complete")
        fetchActivities()
    }
    internal fun close() { scope.cancel(); store.close() }

    suspend fun signOut() = operation("Signing out…") {
        val account = mutable.value.account ?: return@operation
        val saved = runCatching { vault.read(account) }.getOrNull()
        // End local access before waiting for an unavailable network to revoke the server session.
        vault.clear(account)
        check(preferences.edit().remove("active").commit())
        mutable.value = TrainingState(initializing = false,busy = true,task = "Finishing sign-out…")
        val revoked = saved != null && runCatching { gateway.signOut(saved); true }.getOrDefault(false)
        mutable.value = mutable.value.copy(message = if(revoked) null else "Signed out on this device. Server session revocation was unavailable; saved files were retained.")
    }
}
