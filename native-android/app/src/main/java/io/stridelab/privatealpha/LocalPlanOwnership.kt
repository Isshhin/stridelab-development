package io.stridelab.privatealpha

import java.util.UUID

/**
 * Local-only plans live under a stable, device-specific identity, never
 * under a cloud user's ID and never under the Garmin import namespace.
 * Storage authentication remains separate. Signing in does not silently
 * migrate, upload, reparent or expose another owner's plans.
 */
internal object LocalPlanOwnership {
    const val PREF_KEY = "local-plan-owner-v1"
    private const val PREFIX = "local-plan-"

    fun create(): String = PREFIX + UUID.randomUUID().toString()

    fun isLocalPlanOwner(value: String): Boolean {
        if (!value.startsWith(PREFIX)) return false
        val suffix = value.removePrefix(PREFIX)
        return runCatching { UUID.fromString(suffix).toString() == suffix }
            .getOrDefault(false)
    }

    fun activeOwner(account: String?, persistentLocalOwner: String): String {
        require(isLocalPlanOwner(persistentLocalOwner))
        return account ?: persistentLocalOwner
    }

    fun isVisibleOwner(owner: String, account: String?, persistentLocalOwner: String) =
        owner == activeOwner(account, persistentLocalOwner)
}
