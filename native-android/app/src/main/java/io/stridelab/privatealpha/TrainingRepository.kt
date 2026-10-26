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
        val cursor = store.cursor(account, "activity")
        val last = cursor?.let { runCatching { JSONObject(it).getLong("completedAt") }.getOrNull() }
        mutable.value = mutable.value.copy(activities = records.sortedWith(compareByDescending<ActivityRecord> { it.startedAt }.thenByDescending { it.id }), lastSync = last,
            message = if(rejected > 0) "$rejected saved activities contain unsupported data and were retained for review." else mutable.value.message)
    }
    private suspend fun operation(label: String, action: () -> Unit): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutable.value = mutable.value.copy(busy = true, message = null, task = label)
            try { action(); true }
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
        val saved = vault.read(account) ?: throw ServiceFailure(401, "Sign in again to synchronize. Saved activities remain available.")
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
        mutable.value = mutable.value.copy(needsSignIn = false, message = "History refreshed · $received activities checked")
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
        mutable.value = mutable.value.copy(message = if(revoked) "Signed out. Saved activity files were retained." else "Signed out on this device. Server session revocation was unavailable; saved files were retained.")
    }
}
