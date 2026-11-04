# Research addendum — Pace Insights eligibility, device provenance and intensity measurement

Reviewed: 2026-11-04 (Asia/Manila). Research-only; vendor documentation and peer-reviewed study, not independently captured app output.

## New primary-source evidence

**DOCUMENTED (Runna; page marked updated this week at review):** Pace Insights evaluates planned intervals, tempos, time trials and *some* pace-targeted long runs, but not every workout. A free run manually linked to a planned workout is ineligible for pace analysis. This is an **observation-eligibility constraint**, not a known pace-adjustment threshold. Source: https://support.runna.com/en/articles/10855216-why-am-i-not-receiving-pace-insights (accessed 2026-11-04; exact revision date not exposed).

**DOCUMENTED (Runna; page shown published last month at review):** Amazfit integration guidance distinguishes activity sync from structured-workout provenance. For unsupported direct structured-workout transfer, Runna recommends recording pace-target sessions in the Runna phone app; easy runs may be synced as ordinary activities. The page states that pace recommendations require suitable lap/pace information and athlete acceptance, rather than automatic wholesale plan adaptation. Source: https://support.runna.com/en/articles/13176839-how-to-use-your-amazfit-watch-with-runna (accessed 2026-11-04; publication month displayed as October 2026, exact day unverified). This is vendor documentation, **not** evidence that all Amazfit models or other integrations behave identically.

**DOCUMENTED (Runna):** The vendor's Pace Insights explanation indicates that accepting pace changes changes target paces rather than the underlying schedule, and that manually changing estimated race time resets pace-status history. The support page also warns that GPS quality can produce inconsistent recommendations. Source: https://support.runna.com/nl/articles/10854865-wat-zijn-pace-insight-aanbevelingen-en-hoe-werken-ze (accessed 2026-11-04; exact publication date unverified). Confirm English-version equivalence before treating translated wording as a stable contract.

## Independent measurement caution

**PEER-REVIEWED (not vendor behavior):** Strepp et al. (2024), randomized 7-day HIIT shock-microcycle trial, found that the apparent distribution of intensity differed depending on whether velocity, running power or heart rate defined the zones. This short, high-intensity protocol does not establish a prescription for high-mileage Zone 2 runners. DOI: https://doi.org/10.1186/s40798-024-00761-1 (published 2024-09-05). For StrideLab, do not mix pace-derived and HR-derived zone percentages as though interchangeable.

## Reproducible example (HYPOTHETICAL; not app output)

If a 65 km week includes 12 km of manually linked free runs and 53 km of correctly recorded planned sessions, 12/65 = **18.4615%** of distance is in the manually linked category. This does **not** mean 18.4615% of Pace Insights' eligible data was lost: eligibility is workout- and data-type-specific, not distance-proportional. The example illustrates why weekly distance alone cannot establish recommendation coverage.

## Implications and open questions

- **Engine 1.5:** No production change. Preserve clear provenance/eligibility distinctions in future QA; do not silently infer a pace change from generic completed distance.
- **Engine 2.0:** Candidate data schema: `planned_workout_id`, `recording_source`, `structured_workout_match`, `lap_data_available`, `pace_target_present`, `quality_flags`, `eligible_for_pace_calibration`, `recommendation_status`. These are research proposals, not recovered Runna internals.
- **High-mileage Zone 2:** Easy runs may inform aerobic-efficiency trends while not qualifying for Runna Pace Insights. StrideLab should evaluate matched-HR easy pace separately from interval/tempo target recalibration; control heat, humidity, hills and GPS quality.
- **Garmin Run Coach:** No new controlled Run Coach output or adaptation thresholds obtained. Do not transfer DSW/Expert observations to Run Coach.

Highest-value next test: capture one planned structured pace session and one manually linked free run with identical intended workout structure, logging the source, eligible/ineligible state, and Pace Insights feedback. Separately capture a real Garmin Run Coach recommendation before activity. No proprietary algorithm formula or current generated-plan output was verified.
