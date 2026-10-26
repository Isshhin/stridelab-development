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
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("native-history-refresh-v1", ExistingPeriodicWorkPolicy.KEEP, request)
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
class TrainingViewModel(application: Application): AndroidViewModel(application) {
    private val repo = (application as StrideApplication).repository
    internal val state = repo.state
    internal val online = (application as StrideApplication).connectivity.online
    fun signIn(email: String, password: String) { viewModelScope.launch { repo.signIn(email, password) } }
    fun refresh() { viewModelScope.launch { repo.refresh() } }
    fun checkIntervals() { viewModelScope.launch { repo.checkIntervals() } }
    fun syncIntervals() { viewModelScope.launch { repo.syncIntervals() } }
    fun loadDetail(id: String) { viewModelScope.launch { repo.loadDetail(id) } }
    fun signOut() { viewModelScope.launch { repo.signOut() } }
}
