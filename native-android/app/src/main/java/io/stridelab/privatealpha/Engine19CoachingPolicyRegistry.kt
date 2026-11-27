package io.stridelab.privatealpha

/**
 * Authoritative central policy registry for Engine 1.9 Coaching Intelligence (Track 7 & Refinements).
 *
 * All constants preserve Track 9 provenance separation:
 * - `[A]` published biological/statistical criteria (e.g., HRV sample requirements)
 * - `[B]`/`[C]` versioned coaching heuristics and pending empirical calibration parameters (`POL-063`)
 */
internal object Engine19CoachingPolicyRegistry {
    const val POLICY_VERSION = "policy-registry-1.9.0-v1-coaching"

    // -------------------------------------------------------------------------
    // POL-050 Confirmation Dwell & Freshness Window
    // -------------------------------------------------------------------------
    const val CONFIRMATION_DWELL_DEFAULT = 2
    const val CONFIRMATION_DWELL_PATHWAY_B = 3
    const val CONFIRMATION_DWELL_VERIFIED_RACE = 1
    const val CONFIRMATION_DWELL_SAFETY_HOLD = 0
    const val CONFIRMATION_FRESHNESS_DAYS = 28L

    // -------------------------------------------------------------------------
    // POL-051 Cooldowns & Reversal Dampening (Scoped strictly to discretionary churn)
    // -------------------------------------------------------------------------
    const val SCOPE_D_COOLDOWN_DAYS = 10L
    const val REVERSAL_WINDOW_DAYS = 14L
    const val REVERSAL_TRIGGER_COUNT = 2
    const val STABILITY_HOLD_DAYS = 14L

    // -------------------------------------------------------------------------
    // POL-052 Proposal Expiration & OCC State Integrity
    // -------------------------------------------------------------------------
    const val CANDIDATE_PROPOSAL_TTL_DAYS = 7L

    // -------------------------------------------------------------------------
    // POL-053 / MOD-21 Standardized HRV & Post-Illness Quarantine
    // -------------------------------------------------------------------------
    const val HRV_MIN_VALID_RMSSD_MS = 5.0
    const val HRV_ROLLING_7D_PREFERRED_DAYS = 4
    const val HRV_ROLLING_7D_MIN_DAYS = 3
    const val HRV_BASELINE_28D_MIN_DAYS = 14
    const val POST_ILLNESS_MIN_HR_QUARANTINE_DAYS = 14L

    // -------------------------------------------------------------------------
    // POL-054 Priority-Budgeted Optional Notification Caps
    // (Safety H1-H3 & Blocking Clarification are 100% exempt!)
    // -------------------------------------------------------------------------
    const val MAX_OPTIONAL_PROMPTS_PER_SESSION = 1
    const val MAX_OPTIONAL_PROMPTS_PER_WEEK = 2

    // -------------------------------------------------------------------------
    // POL-063 / MOD-22 Global Bipartite Activity Matching & Stitching
    // Explicit [B]/[C] versioned heuristics pending retrospective calibration (IR-05)
    // -------------------------------------------------------------------------
    const val MATCH_WINDOW_CALENDAR_DAYS = 2L
    const val STITCH_MAX_GAP_SECONDS = 20L * 60L
    const val STITCH_MAX_GPS_GAP_METERS = 250.0
    const val MATCH_WEIGHT_DATE = 0.45
    const val MATCH_WEIGHT_DURATION = 0.35
    const val MATCH_WEIGHT_STRUCTURE = 0.20
    const val MATCH_COMPATIBILITY_FLOOR = 0.50
    const val MATCH_AUTO_LINK_THRESHOLD = 0.80
    const val MATCH_AMBIGUITY_MARGIN = 0.12
    const val MATCH_UNMATCHED_COST = -0.35
    const val MATCH_MISSED_COST = 0.0

    // -------------------------------------------------------------------------
    // POL-046 & Pathway B Progression Guardrails
    // -------------------------------------------------------------------------
    const val MINOR_RUN_WORK_REL_CAP = 0.10
    const val BOUNDED_RUN_WORK_REL_CAP = 0.20
}
