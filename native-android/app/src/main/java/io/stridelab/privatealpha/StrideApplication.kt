package io.stridelab.privatealpha

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class StrideApplication: Application() {
    internal val connectivity by lazy { ConnectivityMonitor(this) }
    internal val repository by lazy { TrainingRepository(this) }
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<HistoryRefreshWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "native-history-refresh-v1", ExistingPeriodicWorkPolicy.KEEP, request)
        // Separate from backend refresh; 6-hour periodic Garmin/backup checks
        // also work for a signed-out local Garmin profile; cloud backup stays gated.
        val garminWork = PeriodicWorkRequestBuilder<GarminAutoSyncWorker>(
            6, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "garmin-auto-sync-v1", ExistingPeriodicWorkPolicy.KEEP, garminWork)
    }
}
class HistoryRefreshWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as StrideApplication).repository
        // Do not use background work as a provider-ingestion scheduler; the backend already owns it.
        val ready = repo.state.first { !it.initializing }
        if(ready.account == null || ready.needsSignIn) return Result.success()
        return if(repo.refresh()) Result.success() else if(repo.state.value.needsSignIn) Result.failure() else Result.retry()
    }
}
/** Account-scoped, bounded sync. No background UI, credentials or foreground service. */
class GarminAutoSyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as StrideApplication).repository
        val ready = repo.state.first { !it.initializing }
        if (ready.localGarminOwner == null) return Result.success()
        // On failure the local cache stays unchanged and the next scheduled
        // check retries. Never enter an aggressive request loop.
        repo.automaticGarminAndBackup(foreground = false)
        return Result.success()
    }
}
class TrainingViewModel(application: Application): AndroidViewModel(application) {
    private val repo = (application as StrideApplication).repository
    internal val state = repo.state
    internal val online = (application as StrideApplication).connectivity.online
    fun signIn(email: String, password: String) { viewModelScope.launch { repo.signIn(email, password) } }
    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch { repo.signUp(name, email, password) }
    }
    fun requestPasswordReset(email: String) {
        viewModelScope.launch { repo.requestPasswordReset(email) }
    }
    fun refresh() { viewModelScope.launch { repo.refresh() } }
    fun onAppResumed() {
        viewModelScope.launch { repo.automaticGarminAndBackup(foreground = true) }
    }
    internal suspend fun backupGarminCloud(): Int = repo.backupGarminCloud()
    internal suspend fun restoreGarminCloud(): Int = repo.restoreGarminCloud()
    /** UI-triggered foreground Garmin history import; completed pages remain offline. */
    internal suspend fun syncGarmin(source:GarminPersonalSource):GarminConnectionStatus =
        repo.syncGarmin(source)
    fun refreshWellness() { viewModelScope.launch { repo.refreshWellness() } }
    fun loadDetail(id: String) { viewModelScope.launch { repo.loadDetail(id) } }
    internal suspend fun renameActivity(id:String,title:String):Boolean = repo.renameActivity(id,title)
    internal suspend fun loadCachedDetail(id:String):Boolean = repo.loadCachedDetail(id)
    /** Foreground, account-scoped FIT GPS import, never bulk or scheduled. */
    internal fun loadGarminGps(id:String,source:GarminPersonalSource) {
        viewModelScope.launch { repo.loadGarminGps(id,source) }
    }
    internal fun planActions()=PlanActions(
        generate={p->viewModelScope.launch { repo.generatePlan(p) }},
        approve={id,r->viewModelScope.launch { repo.approvePlan(id,r) }},
        pause={id,r->viewModelScope.launch { repo.pausePlan(id,r) }},
        archive={id,r->viewModelScope.launch { repo.archivePlan(id,r) }},
        move={id,r,s,d->viewModelScope.launch { repo.moveSession(id,r,s,d) }},
        record={id,r,s,log,done->viewModelScope.launch { repo.recordWorkout(id,r,s,log,done) }},
        missed={id,r,s,reason,skipped->viewModelScope.launch { repo.markMissed(id,r,s,reason,skipped) }},
        acceptSuggestion={id,r,s->viewModelScope.launch { repo.acceptSuggestion(id,r,s) }},
        feedback={id,r,category,text,s->viewModelScope.launch { repo.saveFeedback(id,r,category,text,s) }},
        shelf={id,c,remove->viewModelScope.launch { repo.changeShelf(id,c,remove) }},
        restore={raw->viewModelScope.launch { repo.restorePlans(raw) }},
        link={id,r,s,a->viewModelScope.launch { repo.linkActivity(id,r,s,a) }},
        swap={id,r,s,e->viewModelScope.launch { repo.swapExercise(id,r,s,e) }},
        saveTemplate={t->viewModelScope.launch { repo.saveTemplate(t) }},
        prepareSession={t,p,d->viewModelScope.launch { repo.prepareSession(t,p,d) }},
        lockWorkout={id,r,s,locked->viewModelScope.launch { repo.setSessionLock(id,r,s,locked) }},
        previewAvailability={id,days->kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { repo.previewAvailabilityChange(id,days) }},
        applyAvailability={id,r,days,impact->viewModelScope.launch { repo.updateAvailability(id,r,days,impact) }},
        longDay={id,r,day->viewModelScope.launch { repo.setLongRunDay(id,r,day) }},
        qualityDays={id,r,days->viewModelScope.launch { repo.setQualityDays(id,r,days) }},
        previewAdjustments={id->repo.previewEngine15Adjustments(id)},
        stageAdjustment={p->viewModelScope.launch { repo.stageEngine15Adjustment(p.planId,p.planRevision,p) }},
        dismissPreview={p->viewModelScope.launch { repo.dismissEngine15Adjustment(p.planId,p.planRevision,p) }},
        dismissPending={id,r,s->viewModelScope.launch { repo.dismissStagedEngine15Adjustment(id,r,s) }},
        pendingError={id,s->
            val snapshot=state.value
            val plan=snapshot.plans.find { it.id==id && it.owner==snapshot.account }
            if(plan==null) "This plan is unavailable." else try {
                Engine15Feedback.requireCurrentPendingAcceptance(plan,s,java.time.LocalDate.now(),snapshot.plans)
                null
            } catch(e:PlanConstraint) { e.message }
        },
        stageWorkoutDraft={draft->viewModelScope.launch { repo.stageWorkoutDraft(draft) }}
    )
    fun signOut() { viewModelScope.launch { repo.signOut() } }
}
