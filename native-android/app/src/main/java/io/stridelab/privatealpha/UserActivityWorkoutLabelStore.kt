package io.stridelab.privatealpha

import android.content.Context
import java.security.MessageDigest

internal class UserActivityWorkoutLabelStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "activity-workout-labels-v1", Context.MODE_PRIVATE
    )

    fun load(owner: String?, activityId: String): UserActivityWorkoutLabel? {
        val key = key(owner, activityId) ?: return null
        return preferences.getString(key, null)?.let(UserActivityWorkoutLabelCodec::decode)
    }

    fun save(owner: String?, activityId: String, label: UserActivityWorkoutLabel): Boolean {
        val key = key(owner, activityId) ?: return false
        return preferences.edit().putString(key, UserActivityWorkoutLabelCodec.encode(label)).commit()
    }

    fun clear(owner: String?, activityId: String): Boolean {
        val key = key(owner, activityId) ?: return false
        return preferences.edit().remove(key).commit()
    }

    private fun key(owner: String?, activityId: String): String? {
        if (owner.isNullOrBlank() || owner.length > 256 ||
            activityId.isBlank() || activityId.length > 512) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$owner\u0000$activityId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "label:$digest"
    }
}
