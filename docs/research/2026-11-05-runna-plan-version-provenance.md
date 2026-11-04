# Runna plan-version provenance: controlled experiment addendum (2026-11-05)

**Evidence status:** DOCUMENTED vendor behavior; no new independently observed plan-generation algorithm. **Scope:** research only.

## Primary source

Runna, “How to Update My Plan Version and Access New Features,” Product Managers, **2026-08-30**, accessed **2026-11-05**: https://support.runna.com/en/articles/10670455-how-to-update-my-plan-version-and-access-new-features

Runna states that (1) race and distance-goal plans on app **8.15.0+** may expose a Plan Versions control; (2) updating a plan version may change upcoming training; (3) a user can revert the immediately preceding version after a version update; (4) that version-revert mechanism does **not** undo manual changes to running ability, elevation or other Manage Plan inputs; (5) old plans may need version updates to expose newer training preferences.

**Interpretation:** This introduces a concrete *experimental confounder*: matching user inputs and app version does not necessarily match the underlying **plan version**. It does not prove a particular numerical progression or workout-selection algorithm. Do not infer that a version rollback reproduces a historical plan after unrelated manual edits.

## Amend controlled Runna experiment 002 capture protocol

For each authorized manual generated-plan capture, record:
- capture timestamp, app version, plan type, plan-version identifier/status as displayed (or `NOT_VISIBLE`), and whether the plan is newly generated, updated, or legacy;
- all existing 16-case input matrix fields, race date, units, timezone, and full weekly session prescriptions;
- whether a version-update prompt appeared, whether accepted, and the before/after plan snapshots;
- whether any manual Manage Plan change occurred between snapshots.

**Version sensitivity test (optional, only where UI allows):** Save a complete baseline snapshot; apply an offered plan-version update with *no other edits*; save updated snapshot; if allowed, revert and save a third snapshot. Compare each session by stable week/day and workout content. Do not claim deterministic reversibility unless all fields match. Avoid generating fictitious outputs.

Suggested reproducible measures, for each week `w`:
- `weekly_km(w) = sum(distance_km of all prescribed running sessions in w)`;
- `delta_km(w) = updated_weekly_km(w) - baseline_weekly_km(w)`;
- `delta_pct(w) = 100 * delta_km(w) / baseline_weekly_km(w)` only when baseline is positive;
- `long_run_share(w) = long_run_km(w) / weekly_km(w)` only when denominator is positive;
- `session_type_change_count = number of aligned sessions whose prescribed workout type differs`.

Include warm-up/cooldown consistently and mark time-based workouts without known distances as `DISTANCE_UNKNOWN` rather than imputing. Compare same plan week and date; exclude race weeks from ordinary long-run progression statistics unless labeled.

## Implications

- **Engine 1.5:** No algorithm or production change. Existing versioned persistence and explicit athlete approval remain appropriate.
- **Engine 2.0:** Require plan-generator version and configuration provenance in every observational record; test longitudinal changes and reversible proposals separately from manual input changes. For 60–70 km/week matched-HR efficiency studies, do not mistake vendor plan-version changes for physiological adaptation.

**Outstanding:** No authenticated paired Runna plans with different plan versions; no Garmin Run Coach recommendation histories; proprietary threshold functions remain UNKNOWN. Garmin Run Coach must remain distinct from Garmin DSW and Expert plans. This addendum is a methodological improvement, not proof of a new coaching rule.
