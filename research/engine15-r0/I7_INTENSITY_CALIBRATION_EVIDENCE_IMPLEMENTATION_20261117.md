# Engine 1.5 I7 — Calibration Evidence Intake Implementation (2026-11-17)

Branch feature/engine15-i7-intensity-calibration-review-20261117, stacked on I6 PR #126 (user's full selected I6 and M3-M6 regression passed in 12 seconds). Preflight frozen in I7_INTENSITY_CALIBRATION_EVIDENCE_PREFLIGHT_20261117.md.

New Engine15I7IntensityCalibrationReview.inspect reruns the same I6/I5/I4/I3 and original M4B/M5B/M6B independent chain before evaluating one asserted owner/family-specific calibration claim.

The claim includes origin, as-of-related local test date, subject source reference, protocol reference, claimed SHA-256-format artifact fingerprint, threshold HR and pace and optional resting/max HR. Only claimed lab or structured field tests can become a packet awaiting EXTERNAL review. Device estimates or manual entries defer. The 90-day time bound and HR/pace numeric bounds are administrative/numeric guardrails only, not inferred physiology or validated exercise guidance.

Optional athlete HR preferences are used for contradictions only: display-only LTHR and max/rest HR cannot independently qualify a runner or overwrite claimed test data.

Source artifact bytes are NOT read or verified. A supplied SHA-256-looking string and asserted lab test origin do NOT prove provenance, protocol or athlete ownership. Source workout family labels still come from caller declarations and recorded HR/pace patterns are NOT converted into threshold or tempo intensity targets.

Result is ExternalQualificationRequired with original I6 and claim preserved, explicit outstanding verification gaps, and all key authority flags FALSE: ownerSourceAuthenticated, calibrationArtifactVerified, calibrationProtocolVerified, thresholdIntensityVerified, workDoseAuthorized, athleteResponseVerified, futureDistanceVerified, executable, authorizedForProduction. No PlanSnapshot, app database, ActivityDetail, ActivityRecord, preferences, Engine1 or production Engine15 routing changes.

11 focused synthetic tests cover consistent claimed field/lab records without authority, empty/watch/manual claim, wrong owner/family, date window, metadata/fingerprint, missing/invalid HR and pace, internal resting/max conflicts, display-only zone conflicts, I6 upstream HR/quality deferral, and deterministic unchanged plan evidence.

Local I7 tests NOT YET RUN. Validate I7 + I6/I5/I4/I3/IndependentQualitySourceBinding/I2/I1/M3/M4A/M4B/M5A-C/M6A-D/Q2/Q3/R0 selected classes on Q3 checkout. Pass of these synthetic tests never authorizes executable quality workouts or Engine1 retirement. Next genuine blocker is source artifact/protocol attestation and valid athlete-specific coaching eligibility/target approval; do not suggest that increasing shadow evidence checks alone closes that gap.
