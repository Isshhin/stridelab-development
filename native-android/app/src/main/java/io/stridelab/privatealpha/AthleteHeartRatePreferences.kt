package io.stridelab.privatealpha

import android.content.Context

internal enum class HeartRateZoneMethod(
    val storageValue: String,
    val displayName: String
) {
    MAX_BPM("max_bpm", "MAX BPM"),
    LTHR_PERCENT("lthr_percent", "LTHR%"),
    HRR_PERCENT("hrr_percent", "HRR%");

    companion object {
        fun fromStorage(value: String?): HeartRateZoneMethod =
            entries.firstOrNull { it.storageValue == value } ?: MAX_BPM
    }
}

internal data class AthleteHeartRateZoneSettings(
    val method: HeartRateZoneMethod = HeartRateZoneMethod.MAX_BPM,
    val maximumHr: Int? = null,
    val lactateThresholdHr: Int? = null,
    val restingHr: Int? = null
) {
    val ready: Boolean
        get() = when (method) {
            HeartRateZoneMethod.MAX_BPM ->
                maximumHr?.let { it in 100..240 } == true
            HeartRateZoneMethod.LTHR_PERCENT ->
                lactateThresholdHr?.let { it in 100..230 } == true
            HeartRateZoneMethod.HRR_PERCENT -> {
                val max = maximumHr
                val resting = restingHr
                max != null && max in 100..240 &&
                    resting != null && resting in 30..120 &&
                    resting < max
            }
        }
}

/**
 * Device-local athlete preference used only for Activity Detail HR-zone display.
 * It is account-scoped when signed in and never changes coaching/engine logic.
 */
internal class AthleteHeartRatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "athlete-heart-rate-preferences-v1", Context.MODE_PRIVATE
    )

    private fun scope(account: String?): String =
        account?.takeIf { it.isNotBlank() } ?: "device"

    private fun key(name: String, account: String?): String =
        name + ":" + scope(account)

    fun maximumHr(account: String?): Int? =
        prefs.getInt(key("maximum_hr", account), 0).takeIf { it in 100..240 }

    fun lactateThresholdHr(account: String?): Int? =
        prefs.getInt(key("lactate_threshold_hr", account), 0).takeIf { it in 100..230 }

    fun restingHr(account: String?): Int? =
        prefs.getInt(key("resting_hr", account), 0).takeIf { it in 30..120 }

    fun method(account: String?): HeartRateZoneMethod =
        HeartRateZoneMethod.fromStorage(
            prefs.getString(key("zone_method", account), null)
        )

    fun settings(account: String?): AthleteHeartRateZoneSettings =
        AthleteHeartRateZoneSettings(
            method = method(account),
            maximumHr = maximumHr(account),
            lactateThresholdHr = lactateThresholdHr(account),
            restingHr = restingHr(account)
        )

    fun save(account: String?, settings: AthleteHeartRateZoneSettings) {
        require(settings.maximumHr == null || settings.maximumHr in 100..240)
        require(
            settings.lactateThresholdHr == null ||
                settings.lactateThresholdHr in 100..230
        )
        require(settings.restingHr == null || settings.restingHr in 30..120)
        require(
            settings.maximumHr == null || settings.restingHr == null ||
                settings.restingHr < settings.maximumHr
        )
        prefs.edit().apply {
            putString(key("zone_method", account), settings.method.storageValue)
            writeOptionalInt(key("maximum_hr", account), settings.maximumHr)
            writeOptionalInt(
                key("lactate_threshold_hr", account),
                settings.lactateThresholdHr
            )
            writeOptionalInt(key("resting_hr", account), settings.restingHr)
        }.apply()
    }

    fun setMaximumHr(account: String?, maximumHr: Int?) {
        save(account, settings(account).copy(maximumHr = maximumHr))
    }

    fun clear(account: String?) {
        prefs.edit()
            .remove(key("zone_method", account))
            .remove(key("maximum_hr", account))
            .remove(key("lactate_threshold_hr", account))
            .remove(key("resting_hr", account))
            .apply()
    }

    private fun android.content.SharedPreferences.Editor.writeOptionalInt(
        key: String,
        value: Int?
    ) {
        if (value == null) remove(key) else putInt(key, value)
    }
}
