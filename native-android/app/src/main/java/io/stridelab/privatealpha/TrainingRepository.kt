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
    val loginGeneration: Long = 0
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
        val cursor = store.cursor(account, "activity")
        val last = cursor?.let { runCatching { JSONObject(it).getLong("completedAt") }.getOrNull() }
        mutable.value = mutable.value.copy(activities = records.sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id }), lastSync = last,
            message = if(rejected > 0) "$rejected saved activities contain unsupported data and were retained for review." else mutable.value.message)
    }
    private suspend fun operation(action: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutable.value = mutable.value.copy(busy = true, message = null)
            try { action(); true }
            catch (e: ServiceFailure) {
                mutable.value = mutable.value.copy(message = e.message, needsSignIn = e.requiresSignIn || mutable.value.needsSignIn); false
            } catch (_: java.io.IOException) {
                mutable.value = mutable.value.copy(message = "Connection or secure storage is unavailable. Saved activities are retained; retry when connected."); false
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = "This operation could not be verified. Existing records were retained. Please retry."); false
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    suspend fun signIn(email: String, password: String) = operation {
        require(email.isNotBlank() && password.isNotEmpty())
        val session = gateway.signIn(email, password)
        gateway.verify(session)
        vault.save(session) // No account activation unless encryption succeeds.
        check(preferences.edit().putString("active", session.account).commit())
        mutable.value = TrainingState(initializing = false, account = session.account, busy = true, loginGeneration = mutable.value.loginGeneration + 1)
        loadCache(session.account)
        // Authentication is complete even if this first cache refresh fails.
        fetchActivities(session)
    }
    private fun session(): NativeSession {
        val account = mutable.value.account ?: throw IllegalStateException("Sign in first")
        val saved = vault.read(account) ?: throw ServiceFailure(401, "Sign in again to synchronize. Saved activities remain available.")
        val current = if(saved.expiresAtSeconds <= System.currentTimeMillis()/1000 + 90) gateway.refresh(saved).also { vault.save(it) } else saved
        return current
    }
    private fun fetchActivities(session: NativeSession) {
        var before: ActivityRecord? = null
        var received = 0
        val seen = HashSet<String>()
        while(true) {
            val page = gateway.activities(session, before)
            require(page.length() <= 200)
            val rows = ArrayList<TrainingStore.RemoteRecord>()
            var last: ActivityRecord? = null
            for(i in 0 until page.length()) {
                val raw = page.getJSONObject(i)
                val parsed = ActivityRecord.parse(raw.toString(), session.account)
                require(seen.add(parsed.id)) { "Repeated page; synchronization stopped safely" }
                // These server rows have no contractual revision token. Never fabricate one for edits.
                rows += TrainingStore.RemoteRecord(parsed.id, raw.toString(), null)
                last = parsed
            }
            val completed = page.length() < 200
            val marker = if(completed) JSONObject().put("completedAt", System.currentTimeMillis()).toString() else null
            store.applyRemotePage(session.account, "activity", rows, marker, System.currentTimeMillis())
            received += page.length()
            loadCache(session.account) // Each complete page is already durable, including partial failure.
            if(completed) break
            require(last != null && last != before)
            before = last
        }
        mutable.value = mutable.value.copy(needsSignIn = false, message = "History refreshed · $received activities checked")
    }
    suspend fun refresh() = operation { fetchActivities(session()) }
    suspend fun syncIntervals() = operation {
        val session = session()
        val result = gateway.intervals(session, "sync")
        if(result.optBoolean("ok", true).not() || result.has("error")) throw java.io.IOException("Integration sync did not complete")
        fetchActivities(session)
    }
    internal fun close() { scope.cancel(); store.close() }

    suspend fun signOut() = operation {
        val account = mutable.value.account ?: return@operation
        // Always end local access even offline. Server logout is best effort and explicitly reported.
        val revoked = runCatching { vault.read(account)?.let { gateway.signOut(it) }; true }.getOrDefault(false)
        vault.clear(account)
        check(preferences.edit().remove("active").commit())
        mutable.value = TrainingState(initializing = false, busy = true, message = if(revoked) "Signed out. Saved activity files were retained." else "Signed out on this device. Server session revocation was unavailable; saved files were retained.")
    }
}
