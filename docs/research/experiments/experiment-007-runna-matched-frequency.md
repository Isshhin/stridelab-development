# Experiment 007 — Matched-frequency Runna plan generation (5 / 6 / 7 days)

**Registered:** 2026-11-04, Asia/Manila. **Status:** REGISTERED / EXECUTION BLOCKED, 0/6 current-app drafts captured. **Evidence:** vendor statements and synthetic analysis-method tests only, NOT observed Runna outputs or established physiological outcomes. Research-only; keep PR #34 draft and unmerged.

## Question and primary contrast

Runna states that six- and seven-day runners receive a more conservative weekly build rate ([vendor statement, 2026-08-31](https://support.runna.com/en/articles/15279082-how-does-runna-reduce-the-risk-of-injury-and-overtraining)). **Falsifiable hypothesis H1:** when recent mileage and athlete state are matched, ordinary build-to-build weekly mileage progression will be smaller for six and/or seven runs per week than for five. **H0:** no discernible reduction in these prescribed weekly progression percentages. A descriptive null result does not disprove undocumented safeguards, which may only bind at particular limits; an observed reduction does not prove why it occurred.

The prior [Experiment 002](experiment-002-runna-controlled-generator.md) is a *different* 40 km/week, four-day baseline sensitivity experiment (0/16 Runna cases observed). It is not silently repurposed here.

## Preregistered input control

All six drafts are for the **same synthetic study athlete**, not verified personal training history: recent 65 km/week; longest repeatable recent run 18 km; advanced ability; current 5K time estimate 27:00; a 5K race goal on **2027-01-31**; 12-week plan from **2026-11-09** if accepted; difficulty Balanced; volume Steady; no strength/cross-training/holidays/B-races or schedule exceptions. All settings including units, timezone, geography/heat settings, week-start, plan mode and app version must be held fixed. These are test inputs, not recommended training doses.

Cases: `F5A F6A F7A F7B F6B F5B` in that execution order to partially balance timing/order. A/B are **independent draft-generation attempts with identical inputs**, not exported copies. The only intended manipulation is 5, 6 or 7 running days/week. Preferred design: mark all seven weekdays as available and change only the run-frequency control *if the app supports it*. If the app requires assigned training days, use **nested assigned days**: F5 = Tue/Wed/Thu/Fri/Sun; F6 adds Sat; F7 adds Mon; Sunday remains long-run day. Mark `AVAILABILITY_COUPLED`: schedule/rest-day topology is then an unavoidable confound, so this is not a perfectly isolated frequency effect. Do not silently swap days to make the plan acceptable. Document actual accepted weekdays, run day placement and quality/long-run sequencing.

**Feasibility is evidence.** If Runna disallows 65 km/week at five days, rejects seven days, selects a different plan family or changes any matched factor, record the original case as `REJECTED_BY_APP`, `DRAFT_UNAVAILABLE` or `INVALID_COMPARISON` with verbatim warning, before a separately preregistered new cohort. Do not compare a 60 km five-day plan against 65 km six/seven-day plans as though matched. Do not substitute a Run Further/Train Your Way target-week plan for a 5K race plan; target mileage can mean a final-week goal and is not the same as recent baseline mileage ([Runna target explanation](https://support.runna.com/en/articles/11598695-how-to-use-and-manage-your-target-mileage-settings-on-run-further-and-train-your-way-plans)).

## Legitimate capture method and provenance

Runna's [Drafts documentation](https://support.runna.com/en/articles/15586762-managing-your-current-upcoming-and-draft-plans) permits previewing drafts without activating them, but feature availability may depend on version/rollout. Use **authorized mobile-app access only**. No private APIs, mass scraping, credentials in GitHub, false identities, force-activating training plans or manipulation of a real athlete's workouts. Mobile screenshots/manual transcription are acceptable if authorized; redact identity, account IDs, timestamps tied to a person, personal FIT/GPS and identifying imagery before research upload. Do not assume drafts support PDF exports. Store a dated local/source reference; analyst verification of captures is a separate step.

For each draft, capture exact screen/version, actual accepted 65 km baseline and 18 km long-run input, goal, phase, dates, frequency, day assignment, chosen volume/difficulty and any warnings, then **every week card and expanded session**. Use the [case matrix](experiment-007-runna-frequency-cases.csv) and [weekly observations](experiment-007-runna-frequency-weeks.csv). For each week enter `total_km`, `long_run_km`, `quality_count`, `quality_types`, `easy_count`, `run_days`, `recovery_days` (days with no running), `phase`, warning and a distinct week screenshot reference. Preserve the complete individual session category, planned total distance and planned hard steps in the local capture bundle; do not confuse workout-card km with hard-running km. Record distance rounding and kilometer/mile conversion. If hidden, leave measures missing and mark incomplete, never enter zero.

**Phase tags:** BUILD, DELOAD, TAPER, RACE, RECOVERY, OTHER. Identify deload/taper from plan annotations and workout composition, not by forcing an every-fourth-week assumption. Recovery days are days without a run, **not necessarily no other physical activity**. All week indices must be in the same Monday–Sunday calendar definition. Explicitly note when "weekly" volume includes a race, a skipped week, an extra free run or non-plan activity; never compare planned and logged volume without labeling.

## Prespecified measurements

For each consecutive pair of weeks:

- `weekly_progression_pct = 100 * (weekly_km[n] - weekly_km[n-1]) / weekly_km[n-1]`
- `long_progression_pct = 100 * (long_run_km[n] - long_run_km[n-1]) / long_run_km[n-1]` when the prior long run is nonzero.
- Record raw deload/taper/recovery/race transitions separately, **exclude transitions touching such weeks from the ordinary BUILD→BUILD comparison**, including immediate post-deload rebounds.
- Match same week index and same A/B replicate against five-day control; `delta_pp = progression_pct[6_or_7] - progression_pct[5]`. Compare mean matched BUILD→BUILD deltas across weeks and A/B, with per-week variation and long-run progression analyzed separately.
- Compare session category counts, easy distribution, run/no-run days, quality types, long-run share, deload placement, taper timing and any warning/rejection. Do not interpret quality-session total km as hard-work km.

**Reporting gate:** Analyze no real output until six distinct authorized draft captures, all 12 weeks for each and matching app version/config are present. Three plan frequencies × two replicates = 72 week rows expected. The analyzer reports `BLOCKED_NO_CURRENT_APP_OUTPUT` on the registered empty template. A complete matched capture remains `DESCRIPTIVE_ONLY_PENDING_CAPTURE_REVIEW` until source evidence is independently inspected; programmatic CSV checks cannot authenticate screenshots or prove they came from the Runna app. **Actual exposure gate:** if any BUILD week contains fewer or more running days than the configured five/six/seven, the primary analysis is `BLOCKED_FREQUENCY_NOT_REALIZED`, and the observed schedule deviations are reported. Taper/race deload day changes are reported but do not alone invalidate BUILD-week matching. Do not call two repeats a confidence interval or an outcome trial. If input ranges prohibit a matched set, the primary contrast is **not identifiable** in that cohort.

## Analysis execution and quality controls

```bash
python research/validation/runna_frequency_comparison.py \
  --cases docs/research/experiments/experiment-007-runna-frequency-cases.csv \
  --weeks docs/research/experiments/experiment-007-runna-frequency-weeks.csv \
  --output runna-frequency-report.json
python -m unittest discover -s research/validation -p 'test_runna_frequency_comparison.py' -v
```

The eleven synthetic unit tests exercise the **data-analysis harness only**: blocked empty cases, 5/6/7 contrast arithmetic, deload/taper exclusion, missing weeks, invalid captures, matching input, app-version checks, BUILD-week frequency realization, taper day-count exceptions, A/B repeatability, and transition auditing. They do not simulate proprietary Runna training plans or validate results. The report is preliminary and must not be promoted to an Engine 2.0 policy.

### Newly auditable descriptive outputs

The capture analyzer records **first, peak and final weekly mileage**, peak long run, quality-session counts and types, easy-session counts, recovery days, long-run share, deload/taper/race week indices, warnings, and **every** consecutive weekly/long-run percentage transition with BUILD-to-BUILD or excluded status. It also computes A-versus-B repeatability at each frequency: number of equal weekly totals, mean and maximum absolute variation, and phase-sequence identity. Repeatability is descriptive, not statistical proof of random variation. A confidence interval is **not justified** by two repeated drafts.

The comparison remains limited by a coupled **assigned-weekday confound** when the app cannot preserve identical availability settings across frequencies. Even under full matched availability, selecting more run days changes scheduling structure as part of the treatment; do not claim it isolates a physiological dose-response effect.

## Secondary planned-session reconciliation

The primary endpoint remains BUILD-to-BUILD weekly mileage progression at matched frequency. A separate [session-level audit protocol](experiment-007-runna-session-audit.md) describes the secondary capture of individual workout dates, total prescribed distances, long-run identity and per-session distribution. It refuses to treat seven sessions on six calendar days as seven running days, and blocks apparent volume matches if the sum of recorded sessions disagrees with weekly cards. This supplementary lane requires independently reviewable authorized captures; it cannot infer the proprietary generator or an athlete's injury hazard from prescribed workouts.

## StrideLab research decision rule

- If valid matched outputs consistently show lower ordinary build-to-build progression for 6/7 days, record **directly observed app behavior** with capture provenance and move to independent safety/athlete-outcome research. Do **not** copy an inferred Runna coefficient.
- If mixed/non-monotonic, test whether total workload, long-run load, bound activation, plan phase, and day sequencing explain variability; preregister the next study first.
- If rejected, changed inputs, missing outputs or drift between app versions, report **not identifiable / blocked**.
- Do not treat vendor statements as independent scientific evidence for injury prevention, performance, matched-HR aerobic efficiency, or causal benefit. Weather, terrain, training status and strength interference require **independent longitudinal validation**.

## Second priority once capture is possible

Start a prospective *Garmin Run Coach* pre-workout log (device model, firmware, plan family, prescribed type/dose/target, recent sessions/load/recovery; then completed session and next recommendation). Do not backfill an adaptive recommendation from a completed FIT file, and do not conflate Run Coach with Daily Suggested Workouts or Garmin Coach Expert Plans.

**Sources and evidence class:** public vendor documentation linked above = DOCUMENTED VENDOR STATEMENT. All input matrix entries = STRIDELAB SYNTHETIC STUDY DESIGN. Captured outputs = 0 (DIRECTLY OBSERVED APP BEHAVIOR unfilled). INDEPENDENT SCIENTIFIC/REAL ATHLETE OUTCOME evidence = none in this experiment.
