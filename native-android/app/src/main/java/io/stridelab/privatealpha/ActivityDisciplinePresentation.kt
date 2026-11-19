package io.stridelab.privatealpha

import java.util.Locale

/**
 * Presentation-only discipline routing. Imported source values remain unchanged.
 * Stationary workouts do not acquire a route from a source-reported zero distance.
 */
internal fun activityUsesStationaryDetail(activity: ActivityRecord): Boolean {
    if (activity.supportsPace) return false
    val type = activity.type.lowercase(Locale.ROOT)
        .replace('-', '_').replace(' ', '_')
    return listOf(
        "strength", "weight", "resistance", "calisthenics",
        "conditioning", "functional", "crossfit",
        "yoga", "pilates", "mobility", "stretch",
        "hiit", "gym", "cardio", "elliptical",
        "rowing", "stair_stepper", "indoor_workout"
    ).any { type.contains(it) }
}

/** Only a real positive route distance belongs in the timeline's distance slot. */
internal fun activityHasDisplayDistance(activity: ActivityRecord): Boolean =
    !activityUsesStationaryDetail(activity) &&
        activity.distanceM?.let { it.isFinite() && it > 0.0 } == true

/** Prefer actual moving duration when available, otherwise recorded elapsed duration. */
internal fun activityDisplayDurationSeconds(activity: ActivityRecord): Double? =
    activity.movingTimeS?.takeIf { it.isFinite() && it > 0.0 }
        ?: activity.durationS?.takeIf { it.isFinite() && it > 0.0 }
