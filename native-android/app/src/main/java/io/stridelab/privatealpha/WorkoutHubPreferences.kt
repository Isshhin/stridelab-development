package io.stridelab.privatealpha

import android.content.Context

internal object WorkoutHubPreferences {
    private const val PREFS_NAME = "workout_hub_prefs_v1"

    private fun favKey(owner: String) = "favorites_$owner"
    private fun audioKey(owner: String) = "audio_cues_$owner"

    fun favoriteIds(context: Context, owner: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(favKey(owner), emptySet()).orEmpty().toSet()
    }

    fun toggleFavorite(context: Context, owner: String, workoutId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(favKey(owner), emptySet()).orEmpty().toMutableSet()
        if (workoutId in current) current.remove(workoutId) else current.add(workoutId)
        prefs.edit().putStringSet(favKey(owner), current).apply()
        return current
    }

    fun audioCuesEnabled(context: Context, owner: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(audioKey(owner), true)
    }

    fun setAudioCuesEnabled(context: Context, owner: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(audioKey(owner), enabled).apply()
    }
}
