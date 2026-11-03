# Research addendum — Workout feedback is not plan adaptation; recovery-distance accounting

**Reviewed:** 2026-11-03 (Asia/Manila)  
**Evidence:** Runna vendor documentation only; no independently observed current-app output  
**Scope:** StrideLab independent Coaching Algorithm Research Lab, research only  
**Production changes:** None

## 1. Runna Workout Insights is explicitly non-adaptive

**DOCUMENTED (vendor claim).** Runna's support page [What Are Workout Insights?](https://support.runna.com/en/articles/10494265-what-are-workout-insights) (page says updated over a week ago when accessed 2026-11-03) distinguishes post-workout AI-generated feedback from plan adaptation: it states that Workout Insights feedback **does not affect the training plan**. The feature compares planned and completed workouts, may include weather/elevation and uses third-party providers with consent. It is available only for certain completed planned sessions and excludes free runs and several plan categories. A user rates the completed workout to unlock the insight.

This is not interchangeable with [Pace Insights](https://support.runna.com/en/articles/14656203-what-are-pace-insights-and-how-do-they-work), which can suggest a runner-approved pace-target adjustment, or [Mileage Insights](https://support.runna.com/en/articles/11794078-what-are-mileage-insights), which can suggest volume/long-run/frequency changes for supported race plans. All three labels describe *different observable outcomes*, not evidence of three internal vendor software modules.

**StrideLab Engine 1.5 implication (PROPOSED, not implemented):** keep descriptive workout feedback separate from automatic changes to training plan. Display evidence and confidence before recommending a change; no change solely because a natural-language summary sounds confident.

**Engine 2.0 research implication:** evaluate a typed decision ledger distinguishing `observation`, `feedback`, `pace_recommendation`, `volume_recommendation`, `accepted_plan_revision`. Do not train a plan-change model on feedback text as if it were a confirmed adaptive action. Study whether user approval is a stable invariant across versions.

## 2. Interval recovery movement contributes to completed weekly mileage

**DOCUMENTED (vendor claim).** Runna's [How Runna Calculates Your Mileage — and How to Adjust It](https://support.runna.com/en/articles/14666572-how-runna-calculates-your-mileage-and-how-to-adjust-it) (dated 2026-08-31; accessed 2026-11-03) explicitly says walking rests during intervals contribute to **completed** weekly mileage, even though rest-distance may not be shown in the workout presentation. It also says changes to *Current Race Times* affect pace targets rather than total mileage; Running Ability affects workload/session lengths more broadly.

**Important distinction:** This does not prove how *planned* weekly mileage includes future walking rests, or how pace/HR is evaluated during walking. It is an accounting observation, not a recovered workout-construction formula.

**Reproducible illustrative arithmetic (HYPOTHETICAL, not Runna output):**
- Work repetitions: 8 × 400 m = 3.20 km.
- Recovery: 7 × 90 s = 630 s = 10.5 min.
- If the runner walks at 4.0 km/h, rest distance = 4.0 × (10.5/60) = **0.70 km**.
- Work plus recovery = **3.90 km**, before warm-up/cool-down.
- If the runner stands still, recovery distance ≈ **0 km** and total ≈ **3.20 km**.
- Difference is **0.70 km**, or **21.875% of work distance** (not 21.875% of total completed distance).

The example shows why an observed completed distance cannot be inferred exactly from prescribed work repetitions alone. It does **not** imply a 4 km/h walking pace, seven rests, or a mileage allowance is Runna's actual prescription.

**Engine 1.5 implication (PROPOSED):** distinguish `planned_work_distance`, `planned_recovery_duration`, `estimated_session_distance`, and `actual_total_distance` where the data model permits. Never invent actual recovery distance from duration alone. For the recent private FIT validation, the separately discovered lap elapsed-vs-timer bug must be corrected before lap-pace or pace-at-HR evaluation; that local research work is independent of this GitHub repository.

**Engine 2.0 research implication:** test whether mileage-feedback eligibility depends on the full recorded activity distance or only structured segments, and whether extra walking recovery causes a recommendation. Current vendor text establishes full-distance accounting, **not** the hidden thresholds or a causal response.

## 3. Garmin comparator status

No new controlled **Garmin Run Coach** plan outputs or quantitative workout-selection thresholds were obtained in this review. Garmin Run Coach remains distinct from Daily Suggested Workouts and Expert Coach plans. Do not infer Run Coach selection logic from DSW examples or from completed FIT activity records alone.

## Highest-value next tests

1. Capture one *current* Runna interval workout with prescribed rep/recovery durations and completed distance; compare work-only, estimated recovery, full recorded distance, and Mileage Insights status without altering the plan.
2. Compare an eligible completed workout's **Workout Insights feedback** with Pace Insights and Mileage Insights states before/after; check whether feedback alone ever changes a plan in the current version (vendor says no).
3. Capture Garmin **Run Coach** recommendation snapshots before/after a naturally occurring easy run or rest day, with exact device/firmware, watch/Connect sync timestamps, recovery metrics actually supported, and training phase. Do not mislabel DSW as Run Coach.
4. For high-mileage runners seeking faster easy pace, test a matched-HR aerobic-efficiency endpoint stratified by temperature, route, gradient, stops and fatigue; do not equate faster loop pace or a vendor mileage recommendation with improved aerobic capacity.

**Evidence boundaries:** The two Runna sources above are public vendor statements, not independent experiments. No original private algorithm reconstruction or production change is claimed. No new controlled Runna generator samples were acquired. 
