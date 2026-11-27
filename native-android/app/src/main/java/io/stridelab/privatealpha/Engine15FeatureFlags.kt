package io.stridelab.privatealpha

/**
 * Runtime feature flags gating incremental Engine 1.5 architecture rollouts.
 * Rollback cleanly disables new unified-state paths inside Engine 1.5 without
 * re-enabling retired Engine 1.0 plan generation.
 */
internal object FeatureFlags {
    val USE_UNIFIED_ATHLETE_STATE_V1: Boolean
        get() = BuildConfig.USE_UNIFIED_ATHLETE_STATE_V1

    val USE_ENGINE16_STRENGTH_V1: Boolean
        get() = BuildConfig.USE_ENGINE16_STRENGTH_V1

    val USE_ENGINE17_CALISTHENICS_V1: Boolean
        get() = BuildConfig.USE_ENGINE17_CALISTHENICS_V1

    val USE_ENGINE18_HYBRID_V1: Boolean
        get() = BuildConfig.USE_ENGINE18_HYBRID_V1

    val USE_ENGINE19_COACHING_V1: Boolean
        get() = BuildConfig.USE_ENGINE19_COACHING_V1

    val USE_ENGINE20_ADAPTIVE_V1: Boolean
        get() = BuildConfig.USE_ENGINE20_ADAPTIVE_V1
}
