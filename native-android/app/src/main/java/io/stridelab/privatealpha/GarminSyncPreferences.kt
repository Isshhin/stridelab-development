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

    /** Only after a server-confirmed insert/duplicate-ignore response. */
    fun acknowledgeCloudUpload(account: String, kind: String, id: String, payload: String) {
        require(kind == "activity" || kind == "activity_detail")
        check(store.edit().putString(
            key(account, "receipt:" + kind + ":" + hash(id)), hash(payload)
        ).commit())
    }
}
