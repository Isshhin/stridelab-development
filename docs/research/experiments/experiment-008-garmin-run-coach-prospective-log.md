# Experiment 008 — Prospective Garmin Run Coach recommendation-to-outcome log

**Registered:** 2026-11-04 (Asia/Manila). **Status:** CAPTURE TEMPLATE ONLY; 0 valid paired Run Coach observations. Research-only; no Android/Engine 1.5/Trackside changes.

## Which Garmin system is eligible

Only **Garmin Run Coach**, explicitly identified in the athlete's active Garmin Connect plan, is eligible for this study. **Garmin Daily Suggested Workouts (DSW), named Garmin Coach Expert Plans (Greg/Jeff/Amy), static self-guided running plans and Garmin Fitness Coach are different systems** and must not be merged into this sample. If the athlete currently uses DSW rather than Run Coach, label that source `OUT_OF_SCOPE_DSW` in separate notes, not as a qualifying Run Coach row. Garmin's [Running Plans support page](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) explicitly distinguishes Run Coach from Expert Plans and self-guided plans, including supported workout target types and setup. Garmin describes Run Coach as day-to-day adaptive based on fitness, health and load, but this is **vendor documentation**, not a captured recommendation or independent proof of efficacy ([Garmin Run Coach](https://www.garmin.com/en-PH/garmin-technology/garmin-coach/garmin-run-coach/)).

## Prospective capture contract

1. **Before running** and **before opening/updating post-run metrics**, inspect the active plan family, device and app software. Record what the plan recommends **at that time**. Save an authorized screenshot reference and local capture timestamp. Do not retrospectively infer the recommendation from the performed FIT/GPX activity or a later plan screen.
2. Capture exact workout category, duration or distance, target mode (HR, pace or Auto), prescribed intensity ranges, plan phase, goal/event date, scheduled date, recent completed sessions, available acute/chronic load and recovery metrics, sleep/HRV/training-readiness state if displayed. Leave unavailable metrics empty or `NOT_EXPOSED`; do not substitute fabricated values. Avoid connecting unrelated watch data to an unverified Run Coach plan.
3. **After the workout**, enter actual completed/skipped/substituted status and measured dose with separate evidence. Then capture the next Run Coach recommendation **after the plan refresh**, noting the post-run capture time, settings changes, firmware/app updates, manual edits, missed sessions and strength/cross-training. No assumed immediate refresh: if the next recommendation is not visible, mark `NEXT_NOT_YET_VISIBLE`.
4. Pair rows via a non-personal `observation_id`. Keep screenshots and raw activities private unless authorized, redacted and appropriately handled. Never upload account IDs, tokens, personal activity tracks or identifying watch screenshots to a public repo.

Tables: [pre-workout CSV](experiment-008-garmin-run-coach-pre.csv) and [post-workout CSV](experiment-008-garmin-run-coach-post.csv). Both are header-only while there are zero observations.

## Analysis and evidence gates

- For a valid pair: a Run Coach pre-workout recommendation must be timestamped **before** actual start, with a linked plan-family screen and preserved original prescription; the post row must reference the **same** observation ID and recorded activity status. For adaptation claims, the next plan recommendation also needs a timestamp and independent screenshot; **changed recommendation alone cannot identify the cause** without measuring confounders.
- Compare prescribed versus completed workout type, planned versus completed distance/duration, intensity targets, day-to-day recommendation changes, available recovery/load, and intervening strength or missed sessions. Do not backfill a pre-capture from the after screen.
- Distinguish **documented vendor behavior** from **direct app observations** and **StrideLab causal hypotheses**. Correlations between recovery metrics and a changed workout are descriptive, not proof of Garmin's internal decision rule.
- Retain exact device model, firmware and software version for replication. An unsupported watch or an unenrolled athlete yields `BLOCKED_NO_RUN_COACH_PLAN`, not a DSW substitution.
- Prospectively prioritize several consecutive genuine workouts rather than deliberately skipping sessions or fabricating low-recovery conditions. Do not trigger harmful workouts for an experiment.
- Before translating observations to Engine 2.0 research policies, preregister independent evaluation using consented athlete outcomes and matched routes/heart rates/weather where relevant. No proprietary thresholds or coefficients are established here.

**Status statement:** Zero prospective pre-workout captures, zero paired outcomes, zero validated adaptations. This protocol creates a reliable observation method, not observed app behavior.
