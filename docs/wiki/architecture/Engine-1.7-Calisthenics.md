---
title: "Engine 1.7 — Calisthenics Intelligence"
area: architecture
status: verified
last_verified_commit: "23c3b321"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsPolicyRegistry.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsModels.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsDagCatalog.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsProgressionEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsWorkoutConstructor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine17CalisthenicsProgramBuilder.kt"
tags: [stridelab, engine17, calisthenics, skill-dag, progression]
---

# Engine 1.7 — Calisthenics Intelligence

Engine 1.7 (`ENGINE17_VERSION = "1.7.0"`, `ENGINE17_CALISTHENICS_POLICY`) implements StrideLab's pure-Kotlin Freeletics-inspired calisthenics journey generator, enabled always via `FeatureFlags.USE_ENGINE17_CALISTHENICS_V1` while retaining deterministic validation for legacy `ENGINE15_INDEPENDENT_CALISTHENICS_POLICY` plans.

## Core Architecture
- **Policy Registry (`Engine17CalisthenicsPolicyRegistry`):** Codifies `POL-033`, `POL-044` duration cadence intervals, `POL-046` minimal-input continuity, `POL-047` mastery gates (`>= 2` consecutive `CLEAN` sessions; beginner slow eccentric cap `<= 2` sets × `2–3` reps, `<= 2×/wk` per `McHugh 2003`), `POL-049` conditional deloads, `POL-058` 6-bucket return reentry, and `POL-061` entry bypass & fresh skill ordering.
- **Formal AND/OR Prerequisite Hypergraph (`Engine17CalisthenicsDagCatalog`):**
  - Covers all **8 core Track 6 domains** (`PULL_UP`, `PUSH_UP`, `DIP`, `HANDSTAND_SKILL`, `CORE_AND_L_SIT`, `SINGLE_LEG_SQUAT`, `WEIGHTED_CALISTHENICS`, `ISOMETRIC_STATIC_HOLDS`) unifying baseline seeds with all **30 structured progressions** in `ExpandedExerciseCatalog.calisthenicsRecords` without modifying SHA-256 JSON files.
  - Encodes explicit `RequireAll` (`AND` mobility + strength) gates, `RequireAny` (`OR`) pathways, `>= 3` strict clean rep bypasses (`POL-061`), and unblocks `"Handstand foundations"`.
- **Progression & Regressions (`Engine17CalisthenicsProgressionEngine`):**
  - Strictly separates **Technical Failure** (`BREAKDOWN_OR_PAIN`) from muscular failure, triggering safe DAG parent regression or `~30%` isometric tremor reduction (`Oranchuk 2019` `15 -> 30 -> 45 s`).
  - Handles zero-pull-up Scenario S05 Prerequisite Cluster pairing (`Band-Assisted Pull-Ups + Low-Vol 4 s Eccentrics + Inverted Rows`), vertical suspended `ExternalAddedMass` bridging, and exports directly into Engine 1.6's shared `UnifiedLocalMuscleLedger`.
- **Journey Splits & Duration Compliance (`Engine17CalisthenicsWorkoutConstructor`, `Engine17CalisthenicsProgramBuilder`):**
  - Schedules `1..6` days/week splits placing **Fresh Skill Practice (`5–10 min`) FIRST**, enforces `MOD-13` conservative `T_max <= T_day_max`, honors M1-D hard blocks, avoids em-dashes in copy, and replays deterministically through `validateSaved(plan)`.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Engine-1.6-Strength]] · [[Engine-1.8-Hybrid]] · [[Engine-1.9-Coaching]] · [[Engine-2.0-Adaptive]] · [[Hard-Guardrails]] · [[Strength-and-Calisthenics-Overhaul]]
