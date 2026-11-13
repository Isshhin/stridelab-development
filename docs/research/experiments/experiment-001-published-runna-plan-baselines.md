# Runna Published Sample Plans — Experiment 001: Workload, Quality-Session Volume and Deloads

**Date of extraction:** 2026-11-02. **Source status:** PUBLISHED HISTORICAL SAMPLES, not current personalized outputs. **Research-only** for the independent StrideLab Coaching Algorithm Research Lab; no production-code changes.

## Objective and experimental limitations

Quantitatively benchmark all eight publicly posted Runna–JustGiving plans (5K, 10K, half marathon, marathon; beginner 3 runs/week and advanced 4 runs/week). Verify **weekly planned running distance, long-run concentration and deload adjustments** from the original page screenshots. Identify candidate constraints without assuming we have recovered Runna's proprietary plan generator.

**This is an observational comparison, NOT a true matched-input black-box experiment.** Beginner vs advanced varies **both ability and nominal running days**, and initial mileage/pace are undisclosed. The examples may predate 2026 Runna Engine logic. We have NOT controlled race times, current mileage, ability, plan version, training preferences or schedule. None of the PDFs exposes complete repetition counts, time at fast pace, rests or pace targets.

## Extracted dataset and calculation method

[Download/view 92-week CSV](runna-justgiving-weekly-matrix-2026-11-02.csv).

Eight PDFs, **92 scheduled weeks**, **84 pre-race training weeks**. For each week we transcribed the top-line distance printed inside each color-coded workout card, grouping into the *scheduled card* categories: `quality_1_session_km`, `quality_2_session_km`, `easy_session_km`, `long_session_km`, `race_km`. **Quality means the full length of a card labeled tempo or intervals**, not continuous hard-running kilometers. The full card includes warm-up, recovery and cool-down, so hard-work time CANNOT be derived. Race distance is separated from long-run distance. On race week the nominal number of scheduled runs may be reduced.

Calculations:
- `weekly_total_km = quality1 + quality2 + easy + long + race`.
- `pre_race_peak = max(weekly_total_km for week < race_week)`.
- `long_run_share = long_session_km / weekly_total_km`, pre-race only.
- `deload_component_delta = 100*(current_component / preceding_component - 1)`. The deload weeks are visually identified *for these sample plans*, not inferred from a hidden vendor field.

**QA checks:** eight plans; 92 CSV rows; every weekly component sum equals the weekly total; race week only has a race distance; no published pace or step details inferred. Original PDF links are present in every CSV row.

## Finding 1: Different goals and nominal abilities show different volume curves

| Published plan | First week km | Highest pre-race week km | Longest pre-race run km | Largest pre-race long-run share |
| --- | ---: | --- | ---: | ---: |
| 5k beginner | 11.2 | 18.5 (wk 6) | 8 | 47.1% |
| 5k advanced | 32.2 | 44.4 (wk 6) | 16 | 37.4% |
| 10k beginner | 14.2 | 25 (wk 8) | 10 | 40.4% |
| 10k advanced | 30 | 48 (wk 7) | 18 | 37.5% |
| hm beginner | 14.6 | 31 (wk 9) | 18 | 58.1% |
| hm advanced | 33 | 49 (wk 7/9) | 22 | 44.9% |
| marathon beginner | 14 | 48.4 (wk 13) | 32 | 66.1% |
| marathon advanced | 35 | 66 (wk 11/13) | 34 | 51.5% |

The **beginner marathon week 13** has a 32 km long run within a 48.4 km week = **66.1%** of weekly planned running distance. The **advanced marathon week 11** has 34 km within 66 km = **51.5%**. These are descriptive properties of the examples, **not recommendations for StrideLab** or independently validated safe percentages. The maximum share may occur in a different week from the largest long run; don't conflate them.

The observed structure is typically **one scheduled faster session + one easy + one long** at 3 days/week, versus **two scheduled faster sessions + one easy + one long** at 4 days/week. Since ability and frequency vary together, this does **not** isolate either parameter's effect.

## Finding 2: Recovery week reductions differ across session categories

Fourteen visually identified mid-plan recovery transitions are:
- 5K: week 4 in both samples.
- 10K: week 5 in both samples.
- HM: weeks 4 and 8 in both samples.
- Marathon: weeks 4, 8 and 12 in both samples.

| Plan | Recovery week | Quality-session total distance change | Easy-card distance change | Long-run distance change | Full-week change |
| --- | ---: | ---: | ---: | ---: | ---: |
| 5k beginner | 4 | -40.4% | 0% | -42.9% | -33.6% |
| 5k advanced | 4 | -14.6% | -14.3% | -28.6% | -19.8% |
| 10k beginner | 5 | -28.6% | -20% | -37.5% | -30% |
| 10k advanced | 5 | -33.3% | 0% | -46.7% | -32.6% |
| hm beginner | 4 | -40.4% | 0% | -30% | -26.6% |
| hm beginner | 8 | -25.7% | -28.6% | -37.5% | -32.7% |
| hm advanced | 4 | -38.1% | -25% | -37.5% | -35.5% |
| hm advanced | 8 | -33.3% | -22.2% | -45.5% | -36.7% |
| marathon beginner | 4 | -3.8% | 0% | -41.7% | -24.5% |
| marathon beginner | 8 | -21.4% | -16.7% | -44.4% | -33.9% |
| marathon beginner | 12 | -45.7% | -28.6% | -53.8% | -47.9% |
| marathon advanced | 4 | -25.2% | 0% | -33.3% | -25.1% |
| marathon advanced | 8 | -30% | -22.2% | -46.2% | -36.4% |
| marathon advanced | 12 | -36.4% | -20% | -58.8% | -45.5% |

**13 of 14** transitions reduce **total quality-card distance proportionally more than easy-card distance**; the exception is **beginner half marathon, week 8** (quality −25.7%, easy −28.6%). Median across these 14 comparisons: quality-card distance **-31.6%**, easy-card distance **-18.3%**, long run **-42.3%**. These medians are descriptive of the convenience sample; the rows are not statistically independent athlete outcomes.

**Important:** Greater reduction in quality-*session distance* does not directly measure the reduction in hard-*work seconds*. Runna's [2026 recovery documentation](https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan) separately claims harder-session *distance* is reduced more aggressively than easy-run distance during deload. The published samples are broadly consistent but not uniformly so.

## Finding 3: A universal 10%-from-previous-week cap cannot describe these samples

For **76 pre-race week-to-week transitions**, **33** involve a greater-than-10% *increase* in scheduled weekly mileage; **20** increase by more than 20%. The largest rebounds are after very light deload weeks: beginner marathon week 12→13 **+120.0%**, advanced marathon week 12→13 **+83.3%**.

**This is NOT evidence that Runna's current 2026 algorithm ignores progression ceilings**. These are older published examples; its official page describes a ceiling based on current mileage and separate long-run controls. The relevant baseline for progression could be a previous *build* week, rather than the immediate preceding *recovery* week, or the engine version could differ. We therefore should **not** implement a universal fixed 10%-from-last-week rule in StrideLab merely because it is easy to code.

The exact rule and any `post_deload_rebound` ceiling remain **UNKNOWN** without current controlled output.

## Finding 4: Published distance distributions do NOT tell us the hard-training intensity distribution

An advanced sample may list two tempo/interval sessions totaling 20 km in a 50 km week. That is **40% of weekly distance within quality-labeled workout cards**, NOT 40% hard training; it contains warm-ups, cool-downs and recovery and may mix efforts. Treat any inference of an 80/20 intensity split from these PDFs as invalid.

## Candidate original StrideLab model to challenge (NOT Runna source code)

Construct separately:
1. `volume_trajectory` anchored to recent *repeatable* workload and athlete constraints.
2. `scheduled_quality_session_count` and a **step-level hard-work-dose budget**.
3. `long_run_trajectory` with independent absolute and relative safety checks.
4. `planned_recovery_week` reductions in different components, with protected ramp-back constraints.
5. `taper` and race-week schedule changes.

Test candidate rules against held-out summary curves, **but also against independent sports science and other athlete profiles**. High similarity to a commercial plan is not proof of safety or efficacy. This dataset is too small and confounded to fit/validate proprietary coefficients.

## What the next *true* controlled experiment requires

With legitimate access to Runna's current plan-generation UI, generate the **same baseline** twice to establish repeatability, then vary ONE parameter at a time:
- Baseline: 5K 27:00; past four-week ~40 km/week; recent longest run 14 km; intermediate; 4 days/week; same goal, length and volume/difficulty preferences.
- A: change only mileage to 25 vs 40 vs 65 km/week.
- B: change only recent longest run to 10 vs 14 vs 20 km.
- C: change only running frequency to 3 vs 4 vs 5.
- D: change only Difficulty; compare actual full interval-step dose and paces.
- E: change only estimated race time; compare per-step pace targets and scheduled distances.
- F: change only general training focus (where available), with otherwise matched settings.

For each capture app version, date, exact submitted inputs, any input rejection, *full* workout-step export, weekly schedule, and side-by-side differences. **No account, current personalized generated plans or controlled output were available in this session**, so no such experiment is claimed. Do not access private APIs or invent time/pace observations.

## Sources

All eight PDFs are linked in the [92-week CSV](runna-justgiving-weekly-matrix-2026-11-02.csv); the original [JustGiving Runna plan hub](https://www.justgiving.com/hub/running-for-good/training-plans) lists them. Current vendor methodology:
- [How Does Runna Build Your Training Plan Around Your Current Fitness?](https://support.runna.com/en/articles/15231838-how-does-runna-build-your-training-plan-around-your-current-fitness)
- [How Does Runna Build Recovery Into Your Training Plan?](https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan)
- [How To Use Training Preferences](https://support.runna.com/en/articles/10393191-how-to-use-training-preferences)

**Research conclusion:** Clear evidence of multi-component workload scheduling and phase-sensitive reductions in **these published examples**. No identified causal input response, current generator coefficients, step-level hard-work durations or demonstrated athlete benefit.