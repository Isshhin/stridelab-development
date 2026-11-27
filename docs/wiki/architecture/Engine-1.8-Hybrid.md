---
title: "Engine 1.8 — Hybrid Intelligence & 8-Family Conditioning"
area: architecture
status: verified
last_verified_commit: "HEAD"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine18HybridPolicyRegistry.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine18HybridGoalOptimizer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine18ConditioningWorkoutConstructor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine18HybridCoordinationEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine18HybridProgramBuilder.kt"
tags: [stridelab, engine18, hybrid, conditioning, coordination, multi-discipline]
---

# Engine 1.8 — Hybrid Intelligence & 8-Family Conditioning

Engine 1.8 (`ENGINE18_VERSION = "1.8.0"`, `ENGINE18_HYBRID_POLICY`, `ENGINE18_CONDITIONING_POLICY`) coordinates multi-discipline running, strength, calisthenics, and conditioning plans as well as standalone 8-family conditioning plans (`FeatureFlags.USE_ENGINE18_HYBRID_V1 = true` in debug and release builds), while preserving deterministic validation (`validateSaved(plan)`) for legacy `ENGINE15_INDEPENDENT_COMBINED_POLICY` and `ENGINE15_INDEPENDENT_CONDITIONING_POLICY` snapshots.

## Core Architecture
- **Central Policy Registry (`Engine18HybridPolicyRegistry`):** Codifies `POL-036` / `POL-038` bidirectional lower-body/running spacing (`>= 48 h` preferred, `>= 24 h` / 2 calendar days floor, configurable `>= 6 h` AM/PM High/Low separation), repaired `POL-048` / `MOD-17` EMOM `40.0 s/min` cutoff protecting `>= 20.0 s` rest per minute, `POL-059` 9-axis cross-discipline stressor limits (`<= 1` lower-body `MAJOR_STEP`, `<= 1` systemic `MAJOR_STEP`), `MOD-19` `5%` Leximin shortfall buckets, and `UNIFIED_LEDGER_HIGH_OVERLAP_THRESHOLD_SETS = 10`.
- **Multi-Goal & Pareto Optimizer (`Engine18HybridGoalOptimizer`):**
  - Short-circuited normalized goal shortfall `s_g in [0.0, 2.0]` eliminating `0/0` edge cases when `T* == m_g == 0` or `achievedDoseQ >= T*`.
  - Two-pass strictly transitive `leximinComparator` across 5% quantized buckets -> raw descending shortfalls -> interference penalty -> candidate ID, without arbitrary scalar cross-discipline weights.
  - Secondary Development preservation when primary shortfall is zero, Spiering (2021) alternating 4-week emphasis blocks, Scenario S15 Pareto-frontier alternatives (`Run-Biased`, `Strength/Skill-Biased`, `Balanced Hybrid`), and Scenario S19 modeled capacity infeasibility diagnostics.
- **Complete 8-Family Conditioning Constructor (`Engine18ConditioningWorkoutConstructor`):**
  - Supports all 8 Track 6 §8 families (`CONTINUOUS_AEROBIC`, `MODALITY_HIIT`, `STRUCTURED_CIRCUITS`, `EMOM`, `QUALITY_PACED_AMRAP`, `WORK_REST_INTERVALS`, `MIXED_MODAL_CONDITIONING`, `MUSCULAR_ENDURANCE_CIRCUITS`) backed by `ExpandedExerciseCatalog.conditioningRecords` and safe bodyweight seeds.
  - Enforces `MD-10` strict physiological modality HR isolation (zero cross-application of running HR to bike/swim/rowing/circuits), `MD-08` novice complex-skill gating, `MD-09` single-axis conditioning progression, `MD-06` `UnifiedLocalMuscleLedger` export, and standalone `buildSoloPlan` / `validateSavedSolo`.
- **Bidirectional Interference & Coordination Engine (`Engine18HybridCoordinationEngine`):**
  - Enforces `POL-036` bidirectional spacing, non-zero systemic fatigue handling on upper axial compounds, Scenario S18 fixed-commitment conflict resolution (`Option A` / `Option B` / `Option C`), `POL-059` 9-axis evaluation (Axis 4 correlated lower-strength bundle counted once), `MD-15` / `S09` taper lower-strength hold/drop, `MD-06` cross-discipline muscle aggregation, and `MD-13` / `S17` independent per-discipline continuity with cycling-to-running tissue mismatch warnings.
- **Unified Program Builder & Coaching Loop (`Engine18HybridProgramBuilder`, `Engine1Engines.supportsCoachingLoop`):**
  - Coordinates Engine 1.5 Running, Engine 1.6 Strength, Engine 1.7 Calisthenics, and Engine 1.8 Conditioning behind `Engine15IndependentPlanGenerator` with shared week-1 muscle overlap filtering (`blockedForCond`), quality-run weekday preservation, daily load shaping, `HybridPlanBuilder` cross-track ledger warnings, and `Engine15Feedback` / `Engine15WeeklyReview` / `Engine15PlanAdaptationEngine` compatibility.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Engine-1.6-Strength]] · [[Engine-1.7-Calisthenics]] · [[Engine-1.9-Coaching]] · [[Engine-2.0-Adaptive]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]]
