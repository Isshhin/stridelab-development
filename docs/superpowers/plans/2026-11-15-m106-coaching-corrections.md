# M10.6 whole-week coaching corrections

Starting checkpoint: 9122fa8655d890531b372f61c77b1e23ead663a7. User authorized implementation after the read-only M10.5 audit, then clarified: improve the whole generated plan (easy, recovery, long and quality), aiming toward Runna/Garmin competitiveness; no UI changes yet. No merging, experimental activation or phone installation.

Design: preserve calendar identities, equipment prescriptions and Engine1/2 rollback boundaries. Replace per-run scaling with a week composer: weekly budget, purpose-aware roles, all-session exposure/availability limits, distance allocation, then exact native-distance quality structures. All future weeks remain provisional. Distinguish actual source observations from prescription metadata.

Acceptance:
- Every distance run has positive reconciled segments; work/recovery/warm-up/cool-down sum exactly to its target. No time-to-distance assertion is treated as intensity evidence.
- Per-session exposure and final long-run share bind after rounding. Known availability binds including single-run days in multi-session mode. Weekly mileage stays within the inherited weekly envelope; no catch-up mileage.
- Recovery roles follow demanding days across week boundaries; optional secondary endurance stimulus occurs away from quality/long/lower-body work. Zero-baseline beginners retain run/walk and actual lighter weeks.
- Families and goal-specific structures differ meaningfully. Preferred/allowed choices hold, second quality needs separately reported recent frequency, and a mileage build cannot inflate the initial weekly work envelope. Unknown fitness is not fabricated.
- New drafts may reuse current owner-scoped explicit difficult/illness evidence from existing feedback rules. Historical plans remain unchanged; stale/undated/other-owner records and easy completion never imply improved fitness.
- Research recovery cannot restore old load after consecutive reduced weeks. Increments bind to demonstrated load; repeated partial builds can repeat actual exposure; quality review requires the immediately preceding complete weeks and separately checked frequency/workload. All research authorization flags stay false.

Test-first sequence: audit counterexamples -> composer/serialization -> independent critique counterexamples -> recent-response draft integration -> full JVM/Android build and instrumentation compilation -> comparative scenario report -> independent review. Existing assertions that encoded equal-share allocation or zero-distance positive-time behavior are replaced by explicit new contracts, not removed without a substitute.

Scope exclusions from later user steering: PlanBuilder history default/UI controls, Today/WeekReview formatting and visual design are unchanged. The existing incomplete-import default remains an audit finding. Running hold and recent-quality frequency fields are additive backend inputs only; exposing their controls is separate UI work. No legacy Figma, Garmin auth/import, GPS, cloud/login, signing or saved-data migration changes.

Evidence limits: role weights, dose budgets, return/recovery gates and progression envelopes are provisional policies. Distance budget fractions are not measured intensity distribution. Runna/Garmin documents are capability references, not proof of matching their outputs. No synthetic pass establishes athlete effectiveness or injury prevention.
