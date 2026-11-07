package io.stridelab.privatealpha

import android.content.Context

/**
 * Device-local athlete preference used only for Activity Detail HR-zone display.
 * It is account-scoped when signed in and never changes coaching/engine logic.
 */
internal class AthleteHeartRatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "athlete-heart-rate-preferences-v1", Context.MODE_PRIVATE
    )

    private fun key(account: String?): String =
        "maximum_hr:" + (account?.takeIf { it.isNotBlank() } ?: "device")

    fun maximumHr(account: String?): Int? =
        if (prefs.contains(key(account))) prefs.getInt(key(account), 0)
            .takeIf { it in 100..240 }
        else null

    fun setMaximumHr(account: String?, maximumHr: Int?) {
        require(maximumHr == null || maximumHr in 100..240)
        prefs.edit().apply {
            if (maximumHr == null) remove(key(account))
            else putInt(key(account), maximumHr)
        }.apply()
    }
}
