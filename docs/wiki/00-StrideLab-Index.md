---
title: "StrideLab Knowledge Graph Index"
area: architecture
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt"
  - "android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java"
tags: [stridelab, index, hub, knowledge-graph]
---

# StrideLab Knowledge Graph Index

StrideLab is an **offline-first native Android running and multi-discipline training coach** built in Kotlin and Jetpack Compose (`compileSdk 36`, `targetSdk 35`, `minSdk 26`, Gradle JDK 21). It combines deterministic periodized coaching (**Engine 1.5**), signed-out-capable **Garmin Personal API / `.FIT` ingestion & watch workout export**, a **6-discipline Freeform Logger & Custom Workout Planner**, an **AMOLED Guided Workout Player & Coach's Debrief**, and a **canvas-first Share Studio**.

## Architecture Snapshot
- **Navigation Shell:** Edge-to-edge 5-slot docked AMOLED bar (`Today | Training | [ + ] | Activity | More`) with center Quick-Log bottom sheet (`QuickLogSheet.kt`) and read-only `Activity` history tab (`ActivityHistoryTabScreen.kt`), backed by `TrainingViewModel` (`StateFlow<TrainingState>`) and single-mutex `TrainingRepository`.
- **Storage Separation:** `TrainingStore` (`stridelab-athlete-v1.db`) stores plans, activities, drafts, diagnostics, adaptations, and checkpoints; `ManualDisciplineStore` (`stridelab-standalone-sessions-v1.db`) isolates Freeform workouts, templates, and custom schedules; `SharePresetStore` (`share-studio-v1`) stores visual design preferences only.
- **Offline & Privacy First:** Garmin sync, FIT decoding/export, Engine 1.5 generation, Freeform logging, and Share Studio work with `state.account == null`. GPS route sharing defaults to `200 m` endpoint trimming.

## Recommended Reading Order
1. Start with [[App-Architecture]] and [[Hard-Guardrails]] to understand data flow and non-negotiable invariants.
2. Read [[Engine-1.5]] and [[Storage-and-FIT-Contracts]] for coaching math, validation, and persistence schemas.
3. Explore active product subsystems under **Features** and **UI**.
4. Review **Decisions (ADRs)** to understand *why* key boundaries exist before changing behavior.
5. Check **Roadmap** for clearly marked `status: idea` concepts (never confused with shipped code).

## Knowledge Graph Map

### Architecture & Guardrails
- [[App-Architecture]] — 5-slot docked Compose shell (`Today | Training | [ + ] | Activity | More`), `TrainingRepository` mutex pipeline, offline ownership, background workers, and JDK 21 verification commands.
- [[Engine-1.5]] — Deterministic running & multi-discipline planning, proportional periodization, 14-archetype workout library, difficulty scaling, `.FIT` auto-matcher, diagnostics, and controlled adaptation with rollback.
- [[Engine-1.6-Strength]] — Pure-Kotlin Strength Intelligence algorithm (always-on): Hevy-style 1–6 day splits, `MOD-13` conservative duration intervals, `MOD-16` e1RM bounds, 9 progression operators, `COARSE_INCREMENT_BRIDGE`, and unified local muscle ledgers.
- [[Engine-1.7-Calisthenics]] — Pure-Kotlin Calisthenics Intelligence algorithm (always-on): Freeletics-inspired journeys, formal AND/OR prerequisite DAG across 8 domains + 30 catalog progressions, Fresh Skill First, `POL-047` tendon caps, and `Oranchuk 2019` isometric progressions.
- [[Engine-1.8-Hybrid]] — Pure-Kotlin Hybrid Intelligence & 8-Family Conditioning (always-on): `MOD-19` Leximin multi-goal optimization, `POL-036` bidirectional interference & High/Low concentration, `POL-059` 9-axis cross-discipline stressor limits, repaired `POL-048` 40 s EMOM cutoff, `MD-10` modality HR isolation, and cross-discipline `UnifiedLocalMuscleLedger` deduplication.
- [[Engine-1.9-Coaching]] — Pure-Kotlin Coaching Intelligence (always-on): `MOD-22` global Hungarian bipartite matcher & 4-gate stitcher, 8-layer multidimensional adherence, 11-hypothesis non-causal differential, `MOD-21` standardized `ln(rMSSD_ms)` HRV, persistent `H1–H4` health holds with monotonic backup merge, 8-stage composable decision pipeline, OCC transactional revisions, and epistemic phrasing/notification guards.
- [[Engine-2.0-Adaptive]] — Pure-Kotlin Adaptive Intelligence (always-on): `MOD-B1` scaled QR + Joseph covariance CS/$D'$, `MOD-B2/B3` $C^0$ durability decoupling, `E2-03` log-space Bayesian e1RM, `MOD-B4` fractional recoverable muscle bands, `E2-05` 3-state Bayesian fatigue filter, identifiability-gated `MOD-C1` impulse kinetics, Track 8 §11 `CRPS`/`NLL` shadow promotion gates, and `E2-09` bounded autonomy with OCC supervisory defaults.
- [[Storage-and-FIT-Contracts]] — `PLAN_SCHEMA = 1`, `TrainingStore.java`, `ManualDisciplineStore` (DB v4 / archive v5), Garmin `.FIT` intake/export contracts, `SharePresetStore` v1, and two-tier GPS thumbnail `LruCache`.
- [[Hard-Guardrails]] — Non-negotiable engineering, scientific, privacy, offline, and UI invariants enforced across code and tests.

### Active Features
- [[Freeform-Logger]] — 6-discipline offline workout logger, lazy `Mutex`-guarded draft persistence, modal bottom-sheet discipline & exercise pickers, auto-prefill set grid, and non-blocking rest timer.
- [[Templates-and-Custom-Planner]] — Reusable routine templates, one-off & weekly custom schedules, occurrence overrides, and strict source labeling distinct from Engine 1.5 coached sessions.
- [[Share-Studio]] — Canvas-first 1:1 preview/export studio (`1080 × {1920, 1350, 1080}`), 4 templates / 8 variants, MapLibre dark snapshots + offline fallback, GPS endpoint trimming, dual directional photo scrims, and honest Workout Recap provenance.
- [[Garmin-and-Activity-Import]] — Signed-out Garmin Personal API sync, `.FIT` decoding, multi-source activity consolidation, 16 MiB local detail cache, moving-time telemetry charts, and FIT 2.0 watch workout export.
- [[Progress-and-History]] — Unified read-only completed-workout evidence projection across Coached, Custom, Freeform, and Garmin sources plus opt-in Running baseline suggestions.
- [[Guided-Workout-Player-and-Debrief]] — Durable checkpointed AMOLED Workout Player for non-running sessions, execution outcome receipts, Training Effect calculator, and 6-discipline read-only Coach's Debrief.

### UI System & Screens
- [[UI-Design-System]] — Pure AMOLED `#000000` canvas, selective `#121212` surfaces, `#1F242B` hairlines, restrained `#D8FB70` lime accent discipline, tabular numerals (`tnum`), `48.dp` touch targets, and `180 ms` zero-recomposition skeletons.
- [[Screens-Map]] — Screen-by-screen navigation routes, owning Compose files, `UiState` projections, and retired elements not to reintroduce.

### Architecture Decision Records (ADRs)
- [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]] — Why Freeform workouts, templates, and custom schedules live in an isolated store and never mutate Engine 1.5 plans.
- [[ADR-02-GPS-200m-Default-Privacy-Trimming]] — Why Share Studio trims 200 m from GPS start/finish by default while keeping full-activity stats intact.
- [[ADR-03-Offline-First-Independent-Local-Ownership]] — Why `localGarminOwner` and `localPlanOwner` operate independently of StrideLab cloud authentication.
- [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]] — Why redundant views (`freeform/evidence`, Library tab, legacy analytics) stay retired while promoting the read-only `Activity` tab (`WorkoutEvidence.kt`) and center Quick-Log sheet in the 5-slot docked bar.

### Roadmap (Shipped Overhauls & Planned / Parked Concepts)
- [[Strength-and-Calisthenics-Overhaul]] — `status: verified` — Set-Table default (`CoachedSetTable.kt`) over `WorkoutPlayerEngine`, scoped hold-timer handoff, effort-anchored / e1RM double progression (`StrengthCalisthenicsProgression.kt`), opt-in Calisthenics upgrades, and muscle metadata prep. Follow-ups: [[Anatomical-Muscle-Map]] and [[Main-and-Supporting-Plans]].
- [[Anatomical-Muscle-Map]] — `status: verified` — Pure-Compose side-by-side Front/Back vector silhouettes (`#D8FB70` luminance scale), on-demand `ExerciseInfoSheet`, and Placement 2 planned/confirmed session focus (`MuscleFocusProjector.kt`; Placement 3 weekly heatmap parked for v2).
- [[Main-and-Supporting-Plans]] — `status: idea` — Parked concept for pairing 1 Main Plan with up to 2 flexible Supporting tracks using 48-hour Main Plan awareness and 3-tier dynamic scaling.

## Related Nodes
- [[App-Architecture]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[UI-Design-System]] · [[Screens-Map]]
