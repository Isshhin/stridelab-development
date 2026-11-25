package io.stridelab.privatealpha

/**
 * Centralized M1B numerical policy registry constants.
 * Mirrors COACHING-POLICY-REGISTRY.md (POL-001..POL-009) without inventing
 * pending M4/M7 constants (such as POL-063 or POL-064).
 */
internal object Engine15M1PolicyRegistry {
    const val POLICY_VERSION: String = AthleteStateSnapshot.CURRENT_POLICY_VERSION

    // POL-001 & POL-002: Multi-source duplicate candidate screening (requires corroboration to merge)
    const val POL_001_MIN_TEMPORAL_OVERLAP_RATIO: Double = 0.70
    const val POL_002_MAX_DURATION_DIFF_RATIO: Double = 0.15
    const val POL_002_MAX_DISTANCE_DIFF_RATIO: Double = 0.15

    // POL-003 & POL-004: Optical wrist PPG cadence-lock screening & coverage bounds
    const val POL_003_CADENCE_LOCK_TOLERANCE_BPM_SPM: Double = 4.0
    const val POL_004_CADENCE_LOCK_MIN_ACTIVE_FRACTION: Double = 0.40
    const val POL_004_MIN_PAIRED_SAMPLES: Int = 10
    const val POL_004_MIN_ACTIVE_DURATION_SECONDS: Double = 300.0
    const val POL_004_MIN_SUSTAINED_ALIGNMENT_SECONDS: Double = 120.0

    // POL-005 & POL-006: HR telemetry plausibility bounds & dropout/flatline quarantine
    const val POL_005_MIN_PLAUSIBLE_HR_BPM: Double = 35.0
    const val POL_005_MAX_PLAUSIBLE_HR_BPM: Double = 235.0
    const val POL_005_MAX_BAD_HR_SAMPLE_FRACTION: Double = 0.05
    const val POL_006_MIN_HR_DROPOUT_FRACTION: Double = 0.25

    // POL-007 & POL-008: Outdoor GNSS speed spike & dropout degradation
    const val POL_007_MAX_PLAUSIBLE_GNSS_SPEED_MPS: Double = 12.5
    const val POL_007_MIN_SPIKE_DURATION_SECONDS: Double = 5.0
    const val POL_008_MIN_GNSS_DROPOUT_FRACTION: Double = 0.20

    // POL-009: Potential split-recording gap detection window (flag only in M1B; never auto-merges)
    const val POL_009_SPLIT_CANDIDATE_MAX_GAP_SECONDS: Double = 15.0 * 60.0

    // Spatial start/end corroboration tolerance for GNSS duplicate confirmation
    const val CORROBORATION_MAX_ENDPOINT_DISTANCE_METERS: Double = 150.0
    const val SOURCE_ID_COLLISION_MIN_START_DIFF_SECONDS: Long = 5L * 60L
}
