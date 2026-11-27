---
title: "Engine 1.6 — Strength Intelligence"
area: architecture
status: verified
last_verified_commit: "23c3b321"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthPolicyRegistry.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthModels.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthCalibrationEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16LocalMuscleLedgerBuilder.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthExerciseCatalog.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthWorkoutConstructor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthProgressionEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine16StrengthProgramBuilder.kt"
tags: [stridelab, engine16, strength, hypertrophy, progression]
---

# Engine 1.6 — Strength Intelligence

Engine 1.6 (`ENGINE16_VERSION = "1.6.0"`, `ENGINE16_STRENGTH_POLICY`) implements StrideLab's pure-Kotlin deterministic strength coaching algorithm (enabled always via `FeatureFlags.USE_ENGINE16_STRENGTH_V1` in debug and release builds), blending Hevy-inspired routine clarity with the mathematical contracts and safety boundaries from Track 6 and the 2026 ACSM Position Stand (`PMC12965823`).

## Core Components
- **Policy Registry (`Engine16StrengthPolicyRegistry`):** Centralizes `POL-033`, `POL-042` through `POL-046`, `POL-049`, `POL-058` through `POL-060`, and the 7-objective evidence matrix (`Maximal strength`, `Hypertrophy`, `General strength`, `Strength endurance`, `Strength maintenance`, `Strength for runners`, `Power development`), plus non-failure defaults (`1–3` RIR hypertrophy, `2–3` RIR strength/runner, blocking `0` RIR failure on axial compounds).
- **Calibration & Load Semantics (`Engine16StrengthModels`, `Engine16StrengthCalibrationEngine`):**
  - Strictly enforces typed `ExternalLoadSemantics` (`TotalBarbellMass`, `DumbbellPerHand`, `MachineStackSetting`, `ResistanceBandTier`, `AssistedBodyweight`, `ExternalAddedMass`, `UnloadedBodyweight`), preventing per-hand vs. combined load mismatches (`MD-02`).
  - Implements `MOD-16` / `POL-042` piecewise Epley and Brzycki provisional e1RM (`reps in 1..10`, `rir in 0..4`, `reps + rir <= 10`, `CLEAN` technique; `11..12` Tier B shadow only; `> 12` rejected) and downgrades cross-context transfers cleanly to `DiscoverByRir`.
- **Unified Local Muscle Ledgers (`Engine16LocalMuscleLedgerBuilder`):** Tracks `directHardSets`, `indirectHardSets`, isometric hold seconds, eccentric-focused sets, plyo contacts, and RIR bins across Strength, Calisthenics, Conditioning, and Freeform sessions (`MD-06`), preserving Observed vs. Projected isolation (`INV-02`, `MD-14`) and computing Tier B `direct + 0.5 * indirect` shadow volume (`MOD-B4`).
- **Exercise Catalog & Bounded Constructor (`Engine16StrengthExerciseCatalog`, `Engine16StrengthWorkoutConstructor`):**
  - Unites `ExerciseLibraryV2` and `ExpandedExerciseCatalog.strengthRecords` under an 8-field descriptor without modifying `catalog.json`.
  - Generates Hevy-style frequency-aware splits across `1–6` days/week (`Full Body`, `Full Body A/B`, `Full Body DUP`, `Upper/Lower A/B`, `UL + PPL`, `Push/Pull/Legs A/B`), strictly ordered (`Power/Skill -> Primary Compound -> Secondary/Unilateral -> Isolation/Core`).
  - Validates conservative `[T_min, T_max]` intervals (`MOD-13` / `POL-044`), packing antagonist supersets or `MED_STRENGTH` (`2` sets/pattern) under tight 30–40 min budgets without clipping warm-ups (`T_max <= T_day_max`, `MD-07`).
- **Progression & Multi-Week Mesocycles (`Engine16StrengthProgressionEngine`, `Engine16StrengthProgramBuilder`):**
  - Implements 9 bounded operators (`CONSOLIDATE_MAINTAIN`, `PROGRESS_ONE_SET`, `PROGRESS_PARTIAL_SETS`, `PROGRESS_ALL_SETS`, `PROGRESS_LOAD_RESET_REPS`, `PROGRESS_SET_VOLUME` with RP distribution-preserving muscle-set allocation, `REDUCE_WORKING_SETS` `POL-049` deloads, `REDUCE_LOAD`, `SUBSTITUTE_MOVEMENT`).
  - Resolves fixed equipment steps `> 12%` via `COARSE_INCREMENT_BRIDGE` (`POL-045`, `MD-03`), minimal-input continuity (`POL-046`, `MD-04`), execution-conflict detection, 6-bucket gapless return curves (`POL-033`, `POL-058`, `MD-13`), and running `MAJOR_STEP` lower-body holds (`POL-059`, `MD-15`).
  - Emits `PLAN_SCHEMA = 1` `PlanSnapshot` drafts with deterministic `validateSaved(plan)` replay from `plan.request` alone.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Engine-1.7-Calisthenics]] · [[Engine-1.8-Hybrid]] · [[Engine-1.9-Coaching]] · [[Engine-2.0-Adaptive]] · [[Hard-Guardrails]] · [[Strength-and-Calisthenics-Overhaul]] · [[Storage-and-FIT-Contracts]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
