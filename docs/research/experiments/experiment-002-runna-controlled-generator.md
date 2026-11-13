# Experiment 002 — Current Runna Generator, One-Factor-at-a-Time Sensitivity

**Registration:** 2026-11-02  
**Status:** PROTOCOL REGISTERED / EXECUTION BLOCKED (no authenticated Runna mobile-plan access). **0/16 current-app output cases run.**  
**Previous baseline:** [Experiment 001, eight historical Runna/JustGiving plans](experiment-001-published-runna-plan-baselines.md)  
**Input matrix:** [16 preregistered cases, including baseline repeat](experiment-002-runna-input-matrix.csv)  
**Research-only:** No Android app or coaching engine changes.

## 1. Why this experiment is not yet executable from publicly accessible web pages

Runna's current official account/web documentation [R1] states that **web.runna.com cannot display or complete training plans**, and the full plan/individual workouts are in the **mobile app**. Its plan-creation guide [R2] explicitly routes generation to the mobile `Plan -> Your Plans -> Create Plan` interface. There is no official publicly available, unauthenticated plan-generation interface confirmed during the investigation. **We have not accessed an authenticated Runna mobile session or generated a new current personalized plan.** Do not substitute the historical PDF examples as responses to controlled inputs.

Runna's current plan-management documentation [R3] does describe a legitimate **mobile Drafts** mechanism: compatible app v8.49.0+ (gradual rollout), `Plan -> Your Plans -> Drafts -> Create Draft`, multiple draft plans without activation, optionally preview them without changing the current plan. **Using drafts is preferable to changing an active athlete plan.** Runna [R2] also documents `Plan -> Manage Plan -> PDF -> Share`, but doesn't guarantee that PDFs can be exported for **draft** plans; record `PDF_NOT_SUPPORTED` if unavailable and capture permitted mobile plan preview/individual-session screenshots instead.

This is a preregistered observation design, **not a claim of 16 completed generator tests**. Don't register accounts under false identities, bypass app access controls, use private APIs, or fabricate output.

## 2. Baseline and controlled factors

Baseline synthetic athlete (for comparison only; **not** the current user's verified record or a training recommendation):
- **Plan:** 5K Improvement, 12 weeks, same start/end period and app version, same geography/units/environment, pace-target mode.
- **Ability:** Advanced (regularly runs at least 10 km and follows structured workouts); do not infer ability from 27:00 5K pace.
- **Current mileage:** 40 km/week; **recent longest run:** 14 km; **current 5K estimate:** 27:00.
- **Runs/week:** 4, Monday/Wednesday/Friday/Sunday, Sunday long run.
- **Training volume:** Steady; **difficulty:** Balanced; no advanced custom caps, strength plan, B-race, holiday or heat adjustment.
- **Set identical start date/end date**, and record the actual plan type/length accepted by the app. If a 12-week 5K Improvement plan or specified calendar period is unavailable, note `BASELINE_UNSUPPORTED` and register a replacement baseline BEFORE comparisons—not selectively per variant.

**16 cases** (all status `NOT_RUN` until actual output exists):
- **B00+B01:** identical baseline twice, separate drafts to estimate determinism/stability.
- **M25/M65:** change ONLY recent current weekly mileage to 25/65 km; baseline 40.
- **L10/L20:** change ONLY recent repeatable longest run to 10/20 km; baseline 14.
- **DC/DH:** change ONLY Difficulty to Comfortable/Challenging; baseline Balanced.
- **VG/VP:** change ONLY Training Volume to Gradual/Progressive; baseline Steady.
- **F3/F5:** change ONLY frequency to 3/5 runs/week; modify available days only as necessary and label this coupled scheduling constraint.
- **T25/T30:** change ONLY current 5K time to 25:00/30:00; baseline 27:00.
- **AI/AE:** change ONLY running ability to Intermediate/Elite; baseline Advanced.

**Input-boundary result is evidence.** Runna's [Current Mileage](R4) guidance states that permissible mileage/long-run input ranges depend on ability level and number of run days. A high-weekly-mileage profile with few running days may be rejected; keep the `REJECTED_BY_APP` result instead of silently increasing ability or run-day count. Some combinations may produce contradictory physical assumptions (e.g. 25 km/week with a 20 km longest run): exclude such interactions from the primary one-factor comparisons rather than invent a plan.

### Documentation-grounded directional expectations, not measured effects

| Factor | Vendor's stated influence | Falsifiable measurement | Caveat |
| --- | --- | --- | --- |
| Starting weekly mileage | Starting workload, constrained by ability/frequency/plan length [R4] | First week's km, peak, whole build curve, rejection | Not guaranteed 1:1 input = first-week total |
| Longest recent run | Long-run readiness and structure [R4] | First long-run km, longest planned run, long-run share | Could influence total weekly km via feasibility |
| Difficulty | Quality dose, reps, hard session count/long-run fast content [R5] | Hard work minutes, session count, recovery, prescribed pace unchanged? | Exact tier coefficients unknown |
| Volume | Weekly mileage level/build [R5] | Weekly total km and peak, taper/deload | May change session distance composition |
| Runs/week | Session distribution and permitted mileage bounds [R4,R6] | Easy/quality/long count, per-day distances | Different available-day sets unavoidable |
| Current race time | Pace targets without changing weekly mileage [R7] | Rep paces, length of pace-target periods, weekly distance invariant? | Vendor-described; test before asserting exact invariance |
| Ability | Broad run length and load; rebuilds [R8] | Total km, easy/long/hard session sizes, constraint rejections | Not a speed category |

## 3. Capture contract

For EACH generated draft:
- `case_id`, `input_snapshot`, `app_version`, `plan_version_if_shown`, `created_at_tz`, `plan_type`, `start_date`, `end_date`, `actual_accepted_settings`.
- `status`: NOT_RUN, GENERATED, REJECTED_BY_APP, DRAFT_UNAVAILABLE, EXPORT_UNAVAILABLE, or INVALID_COMPARISON.
- `rejection_message` verbatim if rejected; no workaround without another named case.
- `source_capture`: PDF if accessible; else dated screenshots of the week cards and **expanded** individual session step details. Note when preview lacks full detail.
- `plan_week_index`, `session_date`, `session_category`, `total_session_km`, `long_run_km`, `race_km`.
- `workout_step_tree`: repeated sets, warm-up, work, easy-recovery, floating recoveries, walk/stationary rest, between-set rest, cool-down. Preserve distance **and** duration, not a guessed conversion.
- `per-step target pace or RPE`, `fast_work_seconds` only when actual *duration* is provided or can be reproduced from planned work distance and target pace.
- Actual post-run walking recovery distance is NOT the same as scheduled step distance. Runna [R7] explicitly says walking rest may count in completed activity mileage even when it isn't displayed as a distance during the interval.

**Critical invariant:** `quality_session_total_distance` includes warm-up, easy steps, cool-down, and possibly other movement. It is NOT equal to `hard_running_distance`. Classify planned work steps by their intended effort/pace, not generic session titles.

## 4. Pre-registered comparisons and calculations

1. **Determinism check** B00 vs B01: exact input/per-week/workout-step comparison, compute percentage of matching weeks and step trees. If outputs differ, measure natural output variance before drawing small-effect conclusions.
2. **Main effects:** For each input variant, compare each week with B00: `delta_total_week_km`, `delta_first_week_km`, `delta_peak_km`, `delta_long_run_km`, `delta_hard_work_seconds`, `delta_hard_session_count`, `delta_target_pace_seconds_per_km`, `delta_recovery_seconds`. Report unavailable measures as null, never zero.
3. **Magnitude vs shape:** Normalize each non-race week by its own first-week total and peak, then compare ramp/recovery/taper shapes; detect whether a parameter shifts only scale or also deload placement/workout classes.
4. **Composition:** `long_run_share = long_run_km / weekly_total_km`; `quality_card_distance_share = sum(all tempo/interval workout total km) / weekly_total_km` (not hard-time %). `hard_running_time_share` is valid **only** with complete step-level prescriptions.
5. **Constraint boundary:** If M65 or L20 is rejected, record exact feasibility message and date/version. Do not claim generalized maximum values from one rejected point; bracket allowable values only with additional consented valid cases.
6. **Documented independence:** Check whether changing only the race-time estimate leaves planned distances unchanged; whether changing only Difficulty leaves per-rep pace targets unchanged. Exceptions should prompt re-verification of hidden state before claiming vendor contradiction.
7. **Long-run safety:** Independently flag extreme long-run-to-week fraction, closely spaced hard/long sessions, impossible recovery placement, and abrupt post-deload shifts. These are **research flags**, not medical thresholds inferred from Runna.

## 5. Observation states and stop conditions

- On unsupported plan controls: mark `NOT_APPLICABLE`, don't force a substitute plan type and compare as equivalent.
- On a draft preview omitting full workouts: complete *weekly-volume* analyses only; keep dose/intensity/rep results `UNKNOWN`.
- On lack of authenticated app access: keep case statuses `NOT_RUN`, report the access barrier and work on openly available baseline/independent rule models.
- Protect account ownership, training history and credentials. Do not share secrets or publish athlete personally identifying data in GitHub.
- Do not alter the user's real Runna plan, make training commitments or send real activities to force app behavior.

### Additional testable lower bound: lowest Difficulty is not speed-free

Runna's [Drafts/plan-management FAQ](https://support.runna.com/en/articles/15586762-managing-your-current-upcoming-and-draft-plans) states that even the lowest difficulty includes **at least one speed session every other week**. This is a **vendor-documented minimum frequency for its supported plans**, not proof that every selected two-week calendar window contains an exact hard-session count. In the DC (Comfortable) condition, record all quality-labeled sessions and check the number of two-week blocks without any speed workout. If the plan does not support Difficulty or belongs to a beginner/return family, the prediction may not apply.

The same source states **Instant Workouts currently do not count toward plan mileage**, while other Runna material describes free runs contributing to some Mileage Insights calculations. Distinguish `plan_target_mileage`, `logged_mileage`, and `insights_eligible_mileage` rather than assuming one rolling counter. This is a comparison of feature scopes, not verified access to the vendor's database schema.

## 6. What we can conclude today

Confirmed **by current Runna documentation**:
- Access to current plan-generation output is mobile-only [R1–R3].
- Starting mileage/longest-run input bounds interact with ability and running frequency [R4].
- Race-time, volume and difficulty have separately documented functions [R5,R7].
- Drafts can preserve an active plan while exploring alternatives [R3].
- Completed walking-recovery distance can affect actual logged mileage [R7].

**Not established**: Any current output distances for M25/M65/L10/L20/DC/DH, numerical coefficients, statistical repeatability, actual per-workout hard work, or how many plan drafts can export full step-level PDFs.

The benchmark from Experiment 001 is historical and **not a valid substitute** for this controlled data.

## Source register

- [R1] [Runna Signing Up or Logging In — web app limitations](https://support.runna.com/en/articles/14033755-signing-up-or-logging-in-to-runna)
- [R2] [How to Create a Training Plan in Runna](https://support.runna.com/en/articles/15443877-how-to-create-a-training-plan-in-runna)
- [R3] [Managing Current, Upcoming and Draft Plans](https://support.runna.com/en/articles/15586762-managing-your-current-upcoming-and-draft-plans)
- [R4] [How to Use and Manage Current Mileage Settings](https://support.runna.com/en/articles/11598676-how-to-use-and-manage-your-current-mileage-settings)
- [R5] [How to Use Training Preferences](https://support.runna.com/en/articles/10393191-how-to-use-training-preferences)
- [R6] [Adjusting Your Running Schedule](https://support.runna.com/en/articles/6206024-adjusting-your-running-schedule)
- [R7] [How Runna Calculates Your Mileage — and How to Adjust It](https://support.runna.com/en/articles/14666572-how-runna-calculates-your-mileage-and-how-to-adjust-it)
- [R8] [Adjusting Your Running Ability](https://support.runna.com/en/articles/6205993-adjusting-your-running-ability)

**Next actionable step for a real black-box test:** use an authorized Runna mobile account with Drafts and capture at least B00, B01, M25, M65, L10, L20, DC and DH. Until then this is a **preregistered experimental method**, not tested current generator behavior.
