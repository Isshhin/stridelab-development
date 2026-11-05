# Runna Mileage Insights: recommendation reset and admissibility boundaries (2026-11-05)

**Evidence:** DOCUMENTED vendor support text, not independently observed app output or proprietary algorithm. **Source:** Runna, “What Are Mileage Insights?”, dated 2026-09-10, accessed 2026-11-05: https://support.runna.com/en/articles/11794078-what-are-mileage-insights

## Incremental findings relative to existing research
- Mileage Insights counts completed planned runs, Free Runs and Instant Workouts; its recommendations currently apply to **race-based plans**, not general fitness, beginner/return or post-injury plans.
- Vendor states recommendations may alter **weekly mileage, long-run progression and runs/week**, and can be **dismissed**. This is structurally different from Pace Insights' pace-target recalibration and from informational Workout Insights.
- **Recommendation state resets** when an athlete accepts or dismisses a recommendation or manually changes the plan (e.g., mileage, run frequency, skipping weeks). Consequently, absence of an insight after a plan edit is not evidence of no underlying training-response signal.
- An acceptance **error** can occur when proposed mileage or long-run distance exceeds the allowed range for the selected running ability and scheduled running days. The vendor suggests raising ability or adding days. This documents an **admissibility constraint**, not its numeric boundary or the internal order of validation.
- Accepted Mileage Insights updates have **no automatic revert**; manual plan edits rebuild the plan. Recommendation presentation may show expected impact on projected race time, but prediction accuracy is unverified.
- Walking recovery distance in interval recordings should not meaningfully influence Mileage Insights when following the prescribed workout, according to the vendor; this is not a measured tolerance threshold.

## Controlled observation protocol
For authorized race-plan captures, save before/after plan snapshots and fields: app version, plan version, athlete ability, days/week, last four weeks completed mileage (including free/instant runs), recommendation type, recommended weekly km/long-run km/run frequency, acceptance/dismissal, any error text, and insight state after action. Keep non-race plan cases as explicit eligibility controls. Never intentionally raise ability merely to force acceptance.

Reproducible metrics: weekly_km = sum(prescribed session distances); long_run_share = long_run_km / weekly_km for weekly_km > 0; recommendation_delta_km = proposed_weekly_km - current_weekly_km; relative_delta_pct = 100 * recommendation_delta_km / current_weekly_km for current_weekly_km > 0. For unknown distance prescriptions, report UNKNOWN rather than estimate. Capture reset timing and reappearance as categorical observations; no data or numeric bounds are currently available.

## StrideLab implications
**Engine 1.5:** no production change. Existing review-only suggestions and explicit approval are consistent independent choices; do not copy undocumented thresholds. **Engine 2.0:** investigate recommendation eligibility, hard constraints, state resets, and reversibility as separately observable components. For 60–70 km/week runners, compare a missed long run against an equivalent shortfall spread across easy runs while holding total completed km equal; this could isolate whether long-run history matters beyond total volume.

**Unresolved:** exact thresholds, lookback window, recommendation trigger, race-time prediction accuracy, and whether reset discards history or merely restarts presentation are UNKNOWN. No authenticated app plans or new Garmin Run Coach outputs were acquired. Keep Garmin Run Coach distinct from DSW and Expert Coach. Research only; no algorithm reconstruction.
