---
title: "Engine 1.9 — Coaching Intelligence"
area: architecture
status: verified
last_verified_commit: "HEAD"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19CoachingPolicyRegistry.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19ActivityMatcherAndStitcher.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19ExecutionAndDifferentialEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19HealthEscalationAndState.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19CoachingDecisionEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine19ExplanationAndNotificationPolicy.kt"
tags: [stridelab, engine19, coaching, adherence, differential, occ, health-holds]
---

# Engine 1.9 — Coaching Intelligence

Engine 1.9 (`ENGINE19_VERSION = "1.9.0"`, `ENGINE19_COACHING_POLICY`, always-on via `FeatureFlags.USE_ENGINE19_COACHING_V1 = true`) implements Track 7's pure-Kotlin, human-in-the-loop supervisory coaching decision layer across Running (1.5), Strength (1.6), Calisthenics (1.7), and Hybrid / 8-Family Conditioning (1.8), while keeping `PLAN_SCHEMA = 1`, deterministic `validateSaved(plan)`, `TrainingStore.java`, and `ManualDisciplineStore` untouched.

## Core Architecture
- **Central Policy Registry (`Engine19CoachingPolicyRegistry`):** Codifies `POL-050` unique `CanonicalActivityId` confirmation dwell (`m = 2` default, `m = 3` Pathway B un-trapping, `1` verified race, `0` safety hold, `28 d` freshness), `POL-051` per-class cooldowns & `14 d` `STABILITY_HOLD` after `>= 2` accept/reject reversals, `POL-052` `7 d` proposal TTL, `POL-053` / `MOD-21` HRV floors (`>= 5.0 ms` rMSSD, `>= 4/7 d` [min `3`], `>= 14/28 d` baseline, `>= 14 d` post-illness HR quarantine), `POL-054` optional prompt caps (`<= 1/sess`, `<= 2/wk`), and `POL-063` (`[B]/[C]` bipartite matching parameters).
- **Global 1-to-1 Bipartite Matcher & Stitcher (`Engine19ActivityMatcherAndStitcher`):**
  - Enforces the 4-condition stitching gate (`CD-02`: identical discipline, non-overlapping sequential order, complementary roles `WU + Main + CD` or interrupted segments, positive device/GPS/ID continuity).
  - Solves exact $(m+n) \times (m+n)$ Hungarian min-cost 1-to-1 assignment (`MOD-22`, `CD-03`) across `±2 cal d` with explicit unmatched sinks & missed sources, flagging near-ties as `AMBIGUOUS_USER_CONFIRM` while keeping 100% of activities in `observedWorkloadActivityIds`.
- **8-Layer Execution Assessment, `MOD-21` HRV & 11-Hypothesis Differential (`Engine19ExecutionAndDifferentialEngine`):**
  - Tracks 12 structurally separated descriptive dimensions (`Measured`, `Unknown`, `NotApplicable`) banning scalar `"% adherence"` or `"0-100 readiness"` composite scores (`EXCL-D`).
  - Evaluates standardized `ln(rMSSD_ms)` HRV baselines without mixing protocols or `SDNN`, and scores all 11 Track 7 non-causal differential hypotheses (`CD-14`) detecting subjective/objective conflicts conservatively.
- **Persistent 4-Tier Health Escalation (`Engine19HealthEscalationAndState`):**
  - Implements `H1 POTENTIAL_EMERGENCY`, `H2 CLINICAL_EVALUATION_REQUIRED` (blocks self-directed test runs until `USER_REPORTED_CLINICAL_CLEARANCE` while preserving unaffected localized modalities [`D13`, `D28`, `CD-11`]), `H3 TEMPORARY_CONSERVATIVE_HOLD` (post-febrile resolution check + `>= 14 d` HR quarantine [`D14`]), and `H4 ORDINARY_FATIGUE_OR_SORENESS`, backed by `m1-coach-*` local records (`M1LocalRecordBackend`) and monotonic anti-rollback backup merging (`CD-10`).
- **8-Stage Composable Decision Engine & OCC Protocol (`Engine19CoachingDecisionEngine`, `Engine19ExplanationAndNotificationPolicy`):**
  - Non-short-circuiting pipeline (`CD-04`, `CD-15`) supporting all 16 actions, 5 advancement pathways (`D03` single-axis Pathway B un-trapping, `POL-046` minimal-input `MINOR`-only continuity [`CD-05`]), Action Compatibility Matrix, 9-precedence Weekly Review, 8-dimension Conditional Branch activation-time revalidation (`CD-08`, `D26`), OCC `inputStateHash` + full transitive downstream validation (`CD-06`, `CD-07`, `CD-09`), strict epistemic copy auditing (`CD-13`), and safety/blocking exempt `POL-054` notifications (`CD-16`).
  - Paired with `TrainingRepository` mutex-serialized coaching helpers and `Engine15DebriefEngine` cross-training debrief parity (`Engine1Engines.supportsCoachingLoop`).

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Engine-1.6-Strength]] · [[Engine-1.7-Calisthenics]] · [[Engine-1.8-Hybrid]] · [[Engine-2.0-Adaptive]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]] · [[Guided-Workout-Player-and-Debrief]]
