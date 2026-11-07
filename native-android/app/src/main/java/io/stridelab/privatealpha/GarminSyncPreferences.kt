package io.stridelab.privatealpha

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Account-isolated preferences, due-time claims and upload receipts.
 * No Garmin keys, bearer tokens, refresh tokens, FIT bytes or raw account IDs.
 * SQLite is the source of truth; receipts are only a retry optimization.
 */
internal class GarminSyncPreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(
        "garmin-sync-v1", Context.MODE_PRIVATE
    )
    private val lock = Any()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun key(account: String, suffix: String): String {
        require(account.isNotBlank() && account.length <= 256)
        return hash(account) + ":" + suffix
    }

    fun automatic(account: String): Boolean = store.getBoolean(key(account, "automatic"), true)
    fun cloudBackup(account: String): Boolean = store.getBoolean(key(account, "cloud"), false)

    fun setAutomatic(account: String, enabled: Boolean) {
        check(store.edit().putBoolean(key(account, "automatic"), enabled).commit())
    }

    fun setCloudBackup(account: String, enabled: Boolean) {
        check(store.edit().putBoolean(key(account, "cloud"), enabled).commit())
    }

    /**
     * A non-mutating due check lets app resume skip unnecessary status traffic.
     * The final claim still arbitrates races with WorkManager after validation.
     */
    fun due(account: String, scope: String, minimumIntervalMs: Long,
            now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        require(scope == "garmin" || scope == "cloud")
        require(minimumIntervalMs >= 0)
        val previous = store.getLong(key(account, "attempt-" + scope), 0L)
        previous <= 0 || now < previous || now - previous >= minimumIntervalMs
    }

    /**
     * Claim before network access, preventing resume/WorkManager duplicates.
     * Scheduling is inexact; interval is a minimum, never a promised timer.
     */
    fun claim(account: String, scope: String, minimumIntervalMs: Long,
              now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        require(scope == "garmin" || scope == "cloud")
        require(minimumIntervalMs >= 0)
        val k = key(account, "attempt-" + scope)
        val previous = store.getLong(k, 0L)
        if (previous > 0 && now >= previous && now - previous < minimumIntervalMs)
            return@synchronized false
        check(store.edit().putLong(k, now).commit())
        true
    }

    fun acknowledgeManualGarminSync(account: String, now: Long = System.currentTimeMillis()) {
        check(store.edit().putLong(key(account, "attempt-garmin"), now).commit())
    }

    fun needsCloudUpload(account: String, kind: String, id: String, payload: String): Boolean {
        require(kind == "activity" || kind == "activity_detail")
        return store.getString(
            key(account, "receipt:" + kind + ":" + hash(id)), null
        ) != hash(payload)
    }

    /**
     * Failed recordings are deprioritized for one day so a bad FIT never
     * blocks the rest of the user's history. Manual sync can still retry them
     * after healthy pending records are handled.
     */
    fun readyForDetail(account: String, id: String,
                       now: Long = System.currentTimeMillis()): Boolean {
        val last = store.getLong(key(account, "detail-failure:" + hash(id)), 0L)
        return last == 0L || now < last || now - last >= 24L * 60 * 60_000
    }

    fun noteDetailFailure(account: String, id: String,
                          now: Long = System.currentTimeMillis()) {
        check(store.edit().putLong(
            key(account, "detail-failure:" + hash(id)), now
        ).commit())
    }

    fun clearDetailFailure(account: String, id: String) {
        check(store.edit().remove(
            key(account, "detail-failure:" + hash(id))
        ).commit())
    }

    fun restoreOffset(account: String, kind: String): Int {
        require(kind == "activity" || kind == "activity_detail")
        return store.getInt(key(account, "restore-offset-" + kind), 0).coerceAtLeast(0)
    }

    fun setRestoreOffset(account: String, kind: String, offset: Int) {
        require(kind == "activity" || kind == "activity_detail")
        require(offset >= 0)
        check(store.edit().putInt(key(account, "restore-offset-" + kind), offset).commit())
    }

    /** Only after a server-confirmed insert/duplicate-ignore response. */
    fun acknowledgeCloudUpload(account: String, kind: String, id: String, payload: String) {
        require(kind == "activity" || kind == "activity_detail")
        check(store.edit().putString(
            key(account, "receipt:" + kind + ":" + hash(id)), hash(payload)
        ).commit())
    }
}
