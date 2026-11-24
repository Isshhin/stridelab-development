# StrideLab — Full Portable Context Pack

> **Generated:** 2026-11-24 10:58 UTC  
> **Verified Commit:** `36a5eeb8`  
> **Included Nodes:** 18  
> **Purpose:** Complete architecture, guardrails, active features, UI system, ADRs, and marked roadmap ideas.

## Table of Contents
1. [StrideLab Knowledge Graph Index](#1-stridelab-knowledge-graph-index) — `00-StrideLab-Index.md` (`verified`)
2. [App Architecture & Data Flow](#2-app-architecture-data-flow) — `architecture/App-Architecture.md` (`verified`)
3. [Engine 1.5 — Deterministic Training Coach](#3-engine-15-deterministic-training-coach) — `architecture/Engine-1.5.md` (`verified`)
4. [Storage, Schemas & FIT Contracts](#4-storage-schemas-fit-contracts) — `architecture/Storage-and-FIT-Contracts.md` (`verified`)
5. [Hard Guardrails & Protected Invariants](#5-hard-guardrails-protected-invariants) — `architecture/Hard-Guardrails.md` (`verified`)
6. [Freeform Workout Logger](#6-freeform-workout-logger) — `features/Freeform-Logger.md` (`verified`)
7. [Garmin Integration & Activity Import](#7-garmin-integration-activity-import) — `features/Garmin-and-Activity-Import.md` (`verified`)
8. [Guided Workout Player & Coach's Debrief](#8-guided-workout-player-coachs-debrief) — `features/Guided-Workout-Player-and-Debrief.md` (`verified`)
9. [Progress, History & Baseline Evidence](#9-progress-history-baseline-evidence) — `features/Progress-and-History.md` (`verified`)
10. [Share Studio](#10-share-studio) — `features/Share-Studio.md` (`verified`)
11. [Templates & Custom Workout Planner](#11-templates-custom-workout-planner) — `features/Templates-and-Custom-Planner.md` (`verified`)
12. [Screens Map & Navigation Routes](#12-screens-map-navigation-routes) — `ui/Screens-Map.md` (`verified`)
13. [UI Design System (AMOLED Compose)](#13-ui-design-system-amoled-compose) — `ui/UI-Design-System.md` (`verified`)
14. [ADR-01: Engine 1.5 Isolation from Freeform and Custom Logging](#14-adr-01-engine-15-isolation-from-freeform-and-custom-logging) — `decisions/ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging.md` (`verified`)
15. [ADR-02: GPS 200 m Default Privacy Trimming in Share Studio](#15-adr-02-gps-200-m-default-privacy-trimming-in-share-studio) — `decisions/ADR-02-GPS-200m-Default-Privacy-Trimming.md` (`verified`)
16. [ADR-03: Offline-First Independent Local Ownership](#16-adr-03-offline-first-independent-local-ownership) — `decisions/ADR-03-Offline-First-Independent-Local-Ownership.md` (`verified`)
17. [ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers](#17-adr-04-retiring-redundant-ui-views-while-preserving-unified-data-layers) — `decisions/ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers.md` (`verified`)
18. [Main & Supporting Plans (Concept)](#18-main-supporting-plans-concept) — `roadmap/Main-and-Supporting-Plans.md` (`idea`)

---

## 1. StrideLab Knowledge Graph Index

- **Source Node:** `docs/wiki/00-StrideLab-Index.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt`, `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java`

# StrideLab Knowledge Graph Index

StrideLab is an **offline-first native Android running and multi-discipline training coach** built in Kotlin and Jetpack Compose (`compileSdk 36`, `targetSdk 35`, `minSdk 26`, Gradle JDK 21). It combines deterministic periodized coaching (**Engine 1.5**), signed-out-capable **Garmin Personal API / `.FIT` ingestion & watch workout export**, a **6-discipline Freeform Logger & Custom Workout Planner**, an **AMOLED Guided Workout Player & Coach's Debrief**, and a **canvas-first Share Studio**.

## Architecture Snapshot
- **Navigation Shell:** 3-tab floating capsule (`Today | Training | More`) backed by `TrainingViewModel` (`StateFlow<TrainingState>`) and single-mutex `TrainingRepository`.
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
- [[App-Architecture]] — 3-tab Compose shell, `TrainingRepository` mutex pipeline, offline ownership, background workers, and JDK 21 verification commands.
- [[Engine-1.5]] — Deterministic running & multi-discipline planning, proportional periodization, 14-archetype workout library, difficulty scaling, `.FIT` auto-matcher, diagnostics, and controlled adaptation with rollback.
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
- [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]] — Why redundant screens (`freeform/evidence`, Library tab, legacy analytics) were retired while keeping `WorkoutEvidence` and baseline calculators active.

### Roadmap (Unimplemented Concepts)
- [[Main-and-Supporting-Plans]] — `status: idea` — Parked concept for pairing 1 Main Plan with up to 2 flexible Supporting tracks using 48-hour Main Plan awareness and 3-tier dynamic scaling.

## Related Nodes
- [[App-Architecture]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[UI-Design-System]] · [[Screens-Map]]

---

## 2. App Architecture & Data Flow

- **Source Node:** `docs/wiki/architecture/App-Architecture.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/StrideApplication.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/PlanActions.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt`

# App Architecture & Data Flow

StrideLab is structured as a unidirectional, offline-first Jetpack Compose application where all persistence, encryption, network sync, and coaching computation are serialized behind a single repository mutex.

## End-to-End Pipeline
```text
Local SQLite (TrainingStore + ManualDisciplineStore) + Encrypted Vaults + Garmin FIT/API
        │
        ▼
TrainingRepository (Mutex + Dispatchers.IO) ──► Engine 1.5 Pure Planners & Validators
        │
        ▼  StateFlow<TrainingState>
TrainingViewModel (StrideApplication.kt) ──► PlanActions / NativeActions / FreeformAccess
        │
        ▼
NativeMainActivity NavHost ──► Jetpack Compose Screens (3-Tab Shell + Detail Routes)
```

## Navigation Shell (`NativeMainActivity.kt`)
- **3 Retained Tab Roots** hosted beneath `TracksideFloatingNavigation` (`68.dp` floating capsule, animated lime indicator):
  1. `home` → **Today** (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`)
  2. `training` → **Training** (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`)
  3. `settings` → **More** (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`)
- **Full-Screen Detail & Workflow Routes** (`showFloatingDock == false`): Workout Detail, Guided Workout Player, Activity Detail, Share Studio dialog, Plan Builder / Blueprint preview, Freeform Logger / Templates / Custom Planner, and More subpages. See [[Screens-Map]].

## State Management & Ownership
- **`TrainingRepository.kt`:** Owns a single Kotlin `Mutex` on `Dispatchers.IO` that serializes all SQLite reads/writes, encrypted vault access, Garmin/Supabase sync, and [[Engine-1.5]] plan mutations. Publishes immutable `TrainingState` snapshots via `MutableStateFlow`.
- **Independent Local Namespaces:** `localGarminOwner` and `localPlanOwner` (`LocalPlanOwnership`) operate independently of StrideLab cloud sign-in (`state.account == null`). See [[ADR-03-Offline-First-Independent-Local-Ownership]].
- **UI Presentation Boundary:** Compose screens never mutate `PlanSnapshot` directly. All mutations route through `PlanActions`, `NativeActions`, or `FreeformAccess`. `Engine15SessionPresentation.forSession(plan, sessionId)` projects active session targets (`ORIGINAL`, `PENDING_REVIEW`, `ACCEPTED_TIME_ONLY`, `ACCEPTED_REST`, `ACCEPTED_PROGRESSION`) without mutating saved prescriptions.
- **Cold-Start Performance (`TodayStartup.kt`):** Owner-scoped plan query resolves the Today card immediately while scalar activity summaries and session restoration run on `Dispatchers.IO`. Secondary caches and bounded GPS route thumbnails hydrate independently via [[Storage-and-FIT-Contracts]].

## Background Work (`StrideApplication.kt`)
- **`HistoryRefreshWorker` (1 h, connected):** Refreshes cloud history only when signed in (`account != null && !needsSignIn`).
- **`GarminAutoSyncWorker` (6 h, connected):** Runs `automaticGarminAndBackup(foreground = false)` even when signed out, syncing summary pages first and then a bounded batch of run recordings (up to 3 per auto-sync, 24 per explicit sync).
- **Offline Map Store (`StrideOfflineMapStore.kt`):** App-scoped MapLibre offline region manager that seeds the APK style before download, tracks progress across screen changes, enforces Wi-Fi checks, and handles stall retries.

## Build & Verification Environment
- **SDK & Tooling:** Kotlin, Jetpack Compose BOM `2025.10.00`, Material 3, Navigation Compose `2.9.5`, WorkManager `2.10.1`, OkHttp `4.12.0`, Garmin FIT SDK `21.218.0`, MapLibre GL `13.7.1`.
- **Sequential JDK 21 Gradle Checks** (run from `native-android/`):
  - `:app:testDebugUnitTest`
  - `:app:assembleDebug`
  - `:app:compileDebugAndroidTestKotlin`
  - `:app:compileReleaseKotlin`

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Screens-Map]] · [[ADR-03-Offline-First-Independent-Local-Ownership]]

---

## 3. Engine 1.5 — Deterministic Training Coach

- **Source Node:** `docs/wiki/architecture/Engine-1.5.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine1Engines.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15IndependentPlanGenerator.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15MacrocyclePeriodization.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DailyLoadShaper.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15WorkoutDifficultyAdjuster.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15ActivityAutoMatcher.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15ExecutionAnalyzer.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15AthleteDiagnosticsEngine.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15PlanAdaptationEngine.kt`

# Engine 1.5 — Deterministic Training Coach

Engine 1.5 (`ENGINE15_VERSION = "1.5.0"`) is StrideLab's pure-Kotlin deterministic training plan generator, daily load shaper, difficulty scaler, post-run execution analyzer, and athlete-approved adaptation engine.

## Routing & Multi-Discipline Planning
- **`Engine1Engines.resolve(request)`** routes running plans to `Engine15IndependentPlanGenerator`, combined plans to `Engine15StaticMultiDisciplinePlanner`, and standalone resistance/conditioning tracks to `Engine15StrengthWorkoutComposer`, `Engine15CalisthenicsWorkoutComposer`, and `Engine15ConditioningWorkoutComposer`.
- **Plan Lifecycle:** Builder drafts live transiently in `TrainingState.planPreview`. Activation validates against the live approved calendar and commits atomically; legacy saved plan drafts are ignored.
- **Hybrid Plan Builder & Runner Support:** Generates independent snapshots for up to 6 tracks (Running, Strength, Plyometrics, Isometrics, Calisthenics, Conditioning), coordinates heavy lower-body/plyometric placement away from key runs, respects compatible fixed pairs and daily time limits, and activates all tracks atomically.

## Running Periodization, Volume & Quality Workouts
- **Zero-Mileage Beginners (`Engine15TimeOnlyRunWalkBuilder`):** `CONFIRMED_ZERO` beginners route to progressive timed Run/Walk cycles (`RunningPrescription` v4 `"TIME_ONLY_NO_DISTANCE_TARGET"`, `distanceKm = null`). Engine 1.5 never fabricates synthetic `runDays * 4.0` km baselines.
- **Proportional Macrocycle (`Engine15MacrocyclePeriodization`):** Scales `BASE` / `BUILD` / `PEAK` / `TAPER` proportionally across short (`4–6w`), medium (`7–11w`), and long (`12–16+w`) horizons. Beginners (`experience == 0`) and `returning == true` runners receive extended `BASE` (`50–65%` of loading weeks) and `0` `PEAK` weeks. "Train Your Way" uses `3:1` loading-to-recovery cycles with zero taper.
- **Volume & Long-Run Caps (`Engine15WeeklyVolumeSkeleton`, `Engine15IndependentVolumePlanner`, `Engine15M5WorkoutComposer`):** Enforces compound loading caps (`3.5%–8.0%`/loading week), 4th-week `0.80x` recovery cutbacks (suppressing double-deloads before taper), dual `10%` long-run progression caps (`Marathon 34 km`, `HM 22 km`, `10K 18 km`, `5K 16 km`), returning-runner `0.75 * longest` starts, and race tapers (`Marathon 0.85/0.70/0.55`, `HM/10K 0.80/0.60`, `5K 0.85`).
- **Day-to-Day Load Shaping (`Engine15DailyLoadShaper`):** Shapes non-long runs (`QUALITY 1.15x`, `AEROBIC_EASY 1.10x`, `STANDARD_EASY 1.00x`, `RECOVERY_POST_HARD 0.76x/0.82x`, `RECOVERY_PRE_LONG 0.76x/0.85x`, `RECOVERY_SANDWICHED 0.68x` including Sunday Long → Monday Recovery).
- **14-Archetype Library & 4-Step Reconciliation:** Covers Easy, Recovery, Strides, Fartlek, Short/Long Hills, Continuous/Broken Tempo, Threshold Cruise, Track Repeats, Classic VO2max, Speed-Endurance Pyramid, Steady Long, and Fast-Finish/Race-Pace Long Runs with energy-system recovery ratios and a 4-step reconciliation ladder preserving physiological warm-up (`>= 8 min`) and cool-down (`>= 5 min`) floors.
- **3-Tier Intensity Zones (`Engine15PaceZoneEstimator`):** (1) Explicit race/TT benchmark → (2) 56-day run blend (Riegel `1.06`) → (3) `request.planningPace`. Optional HR bounds populate strictly from verified 5-zone athlete profiles (never `220 - age`).

## Difficulty Scaling, Matching, Diagnostics & Controlled Adaptation
- **Reversible Difficulty (`Engine15WorkoutDifficultyAdjuster`):** Steps `-2` (*Much easier*) .. `0` (*Coached*) .. `+2` (*Much harder*) always recompute from immutable `PlannedSession.difficultyBaseline` with zero cumulative drift and zero catch-up mileage. `ExerciseAlternatives` supports easier/harder resistance swaps.
- **Confidence-Scored `.FIT` Auto-Matcher (`Engine15ActivityAutoMatcher`):** Scores open running sessions within `[-1, +1]` days (`0.45` date + `0.40` volume + `0.15` plausibility). `>= 0.80` same-day + `>= 0.15` margin → `AUTO_LINKED`; `0.50..0.79` or contention → `NEEDS_CONFIRMATION`; `< 0.50` → `UNMATCHED`. Never overwrites `MANUALLY_LINKED` or `MANUALLY_REJECTED`.
- **Post-Run Execution Analyzer (`Engine15ExecutionAnalyzer`):** Grades linked runs across evidence tiers (`LAP_AND_RECORD` → `RECORD_WINDOW` → `SUMMARY_ONLY`) into intent-specific verdicts (`INTENSITY_MISMATCH_TOO_FAST`, `VOLUME_ONLY_MATCH`, `PARTIALLY_EXECUTED`, etc.) with optional verified-zone HR compliance and `Pa:Hr` decoupling.
- **Confidence-Gated Diagnostics (`Engine15AthleteDiagnosticsEngine`):** Read-only 28d/7d/30d/42d evaluation across `VolumeTolerance`, `QualityExecution`, `EasyDiscipline`, and `AerobicEfficiency`. Physical load counts all runs; quality/easy grading uses linked analyses only; `hiddenFatigueRisk` (`>= 50%` easy gray-zone creep) blocks faster pace checks.
- **Controlled Adaptation & Rollback (`Engine15PlanAdaptationEngine`):** Never auto-mutates active plans or alters past/completed/linked sessions (`effectiveFromDate = asOfDate.plusDays(1)`). Supports 5 playbooks (`MISSED_WEEK_RECOVERY` 3 tiers, `PROACTIVE_DELOAD_SWAP`, `EASY_PACING_LOCK`, `PACE_RECALIBRATION_UP`, `PACE_RECALIBRATION_DOWN`). Every candidate must pass untouched `validateSaved`; `approveAdaptationProposal` / `revertLastAdaptation` archive and restore exact `previousPlanJson` locally.

## Protected Boundaries
Engine 1.5 is completely isolated from [[Freeform-Logger]] and [[Templates-and-Custom-Planner]] (see [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]). Shadow research modules (`Engine15I3..I10`, `IndependentAdaptiveWeek`, `Engine15HistoryInformedPlanner`) remain unwired from production generation.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Guided-Workout-Player-and-Debrief]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]

---

## 4. Storage, Schemas & FIT Contracts

- **Source Node:** `docs/wiki/architecture/Storage-and-FIT-Contracts.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java`, `native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/SharePresetStore.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ActivityThumbnailCache.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitArchive.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitWorkoutEncoder.kt`

# Storage, Schemas & FIT Contracts

StrideLab separates durable athlete data across purpose-built local SQLite databases, encrypted credential vaults, and bounded caches so offline training, manual logging, and Garmin recordings never interfere with one another.

## 1. Primary SQLite Store (`TrainingStore.java`)
- **File:** `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java` (compiled into `native-android/`).
- **Database:** `stridelab-athlete-v1.db` (DB version 1, WAL enabled, foreign keys enabled).
- **Corruption Policy:** Throws `SQLiteDatabaseCorruptException` instead of silently deleting corrupt databases so unsynced athlete records remain recoverable.
- **Tables:**
  - `records(account, kind, record_id, payload, server_version, synced_at)`
  - `outbox(seq, operation_id, account, kind, record_id, payload, base_version, parent_operation_id, state, attempts, last_error, created_at)`
  - `conflicts`, `cursors`, `resolutions`
- **Local-Only `kind = "workout"` Records (Never Queued to `outbox`):**
  - `activity-match-*` (`.FIT` auto-matcher records)
  - `execution-analysis-*` (post-run execution verdicts)
  - `athlete-diagnostic-*` (SHA-256 keyed rolling diagnostics)
  - `plan-adaptation-*` (adaptation proposals and rollback `previousPlanJson` revisions)
  - `player-exec-*` (durable guided workout execution checkpoints and completion receipts)
- **Large FIT Detail Ceiling:** `MAX_LOCAL_RECORDING_BYTES = 16 MiB` UTF-8 JSON for local Garmin FIT recordings, read in UTF-8 byte windows so dense GPS files never hit the shared 2 MiB record cap or create cloud outbox entries.

## 2. Plan & Prescription Contracts (`EngineContracts.kt`)
- **`PLAN_SCHEMA = 1`:** Stable serialized plan snapshot schema. Structured cross-training goals and optional Step 01–06 fields (`stepIntent`, pace/HR bounds, support dose/placement flags) decode backward-compatibly with `null` defaults without a `PLAN_SCHEMA` bump.
- **`validateSaved(plan)`:** Deterministically replays plan generation from `plan.request` (no `activities` argument) and verifies `PlannedSession.difficultyBaseline` plus `Engine15WorkoutDifficultyAdjuster.matchesAdjusted`.

## 3. Isolated Manual & Freeform Store (`ManualDisciplineStore.kt`)
- **Database:** `stridelab-standalone-sessions-v1.db` (DB version 4, backup archive schema 5; schemas 1–4 remain readable).
- **Tables:** `manual_templates`, `freeform_workouts` (indexed by `(owner, modified_at DESC)`), `custom_schedules`.
- **Isolation Contract:** Owner-scoped (`account ?: localPlanOwner`), never writes to `TrainingStore` or Garmin FIT files. Restore runs in a single transaction and rejects conflicting completed records.

## 4. Share Studio & Auxiliary Local Stores
- **`SharePresetStore.kt`:** SharedPreferences `"share-studio-v1"` (`"designs"` JSON, version 1 codec, max 100 presets). Stores visual design preferences only — never source activity data, GPS coordinates, user photos, or account identifiers.
- **Two-Tier GPS Thumbnail Cache (`ActivityThumbnailCache.kt`):** 6 MB in-memory `LruCache<String, ImageBitmap>` backed by local disk PNGs under `cacheDir/gps_thumbs` (`TodayCachedGpsThumbnail`). Synchronous memory read on first frame eliminates reload flashes across `Today` and `Training`.
- **Credential & Archive Stores:** `SessionVault` / `GarminCredentialVault` (encrypted), `GarminFitArchive`, `FitImportCache`, `LocalActivityArchive`, `GarminZonePreferenceStore`, `UserActivityWorkoutLabelStore`, `StrideOfflineMapStore`.

## 5. Garmin `.FIT` Ingestion & Export Contracts
- **Import (`FitFileIntake.kt`, `FitContainerReader.kt`, `FitActivityDecoder.kt`):** Decodes FIT sessions, laps, records, and optional running dynamics (ground contact time, vertical oscillation, step length, vertical ratio) without mutating raw `.FIT` archives.
- **Export (`GarminFitWorkoutEncoder.kt`):** Pure-Kotlin FIT Protocol 2.0 workout encoder mapping prescribed time/distance steps and compressing consecutive identical work/recovery pairs into Garmin repeat steps. Explicit BPM targets require verified cached 5-zone settings; output writes via Android's document picker directly to user-chosen storage (e.g. `/GARMIN/NewFiles/`) without mutating plans or sync state.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Engine-1.5]] · [[Hard-Guardrails]] · [[Garmin-and-Activity-Import]] · [[Freeform-Logger]] · [[Share-Studio]]

---

## 5. Hard Guardrails & Protected Invariants

- **Source Node:** `docs/wiki/architecture/Hard-Guardrails.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `AGENTS.md`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DailyLoadShaper.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15IndependentPlanGenerator.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt`

# Hard Guardrails & Protected Invariants

These invariants are non-negotiable contracts enforced across StrideLab's coaching engine, persistence layer, Garmin pipeline, Share Studio, and UI.

## 1. Engine 1.5 Scientific & Validation Guardrails
- **Weekly Mileage Conservation:** `sum(week.distanceKm) == week.proposedKm` within `1e-6` (enforced by clamped water-filling + tenths reconciliation in `Engine15DailyLoadShaper`).
- **Immutable Baseline & Deterministic Validation:** `PlannedSession.difficultyBaseline` never mutates; `validateSaved(plan)` replays strictly from `plan.request` without reading `activities`.
- **No Synthetic Baselines or Heart-Rate Fabrication:** Never fabricate `runDays * 4.0` km mileage for `CONFIRMED_ZERO`/`UNKNOWN`/`PEAK_WEEK_ONLY` runners, and never compute `220 - age` HR zones (HR targets and HR-based debriefs/Training Effect require verified 5-zone profiles).
- **Safe Bookend Floors:** Quality reconciliation must preserve `>= 8 min` warm-up and `>= 5 min` cool-down (combined `13..90 min`) or explicitly defer rather than silently erasing `qualityCount = 0`.
- **Preview-Only & Athlete-Approved Adaptation:** Feedback, weekly reviews, and Step 06 plan adaptations never auto-mutate active plans or alter past/completed/linked sessions (`effectiveFromDate = asOfDate.plusDays(1)`), and must support atomic rollback via `previousPlanJson`.

## 2. Offline, Storage & Garmin Integrity
- **Offline / Signed-Out Independence:** Garmin Personal API sync, local `.FIT` decoding/export, Engine 1.5 planning, Freeform logging, and Activity Detail must always work with `state.account == null`. See [[ADR-03-Offline-First-Independent-Local-Ownership]].
- **Source Recording Immutability:** Never overwrite or mutate imported Garmin `.FIT` files or synthesize missing GPS/biometric samples.
- **Schema & Store Isolation:** Keep `PLAN_SCHEMA = 1`, `TrainingStore.java` non-destructive corruption retention, `ManualDisciplineStore` isolation from `TrainingStore`, and `SharePresetStore` v1 backward compatibility (never store photos, activity data, or GPS coordinates in presets).

## 3. Share Studio Privacy & Rendering Invariants
- **GPS Endpoint Trimming by Default:** `SharePrivacy.STANDARD` (`200 m`) is enabled by default; `drawCoordinateRoute` and map snapshots consume only already-trimmed `activity.route.segments`. Disabling trimming (`SharePrivacy.OFF`) surfaces an explicit privacy warning (`share-trim-off-warning`). See [[ADR-02-GPS-200m-Default-Privacy-Trimming]].
- **1:1 Preview-to-Export Parity & Pixel Contracts:**
  - `ShareCardRenderer.geometry(state)` bounding math stays locked (`route.height >= 152 px`, stats above footer).
  - `TRANSPARENT` corner `(0, 0)` must remain `alpha == 0`.
  - `MAP` must never paint an opaque overlay over `g.route` (keeping MapLibre attribution/logo visible and pixel-exact).
  - `PHOTO` directional scrims must preserve mid-frame vividness (`(540, 340)` stays vivid over the source photo).
  - Instagram Story safe-area guides (`share-story-safe-guides`) are preview-only and never drawn into exported PNGs.
- **Workout Recap Provenance Honesty:** Recorded laps are explicitly labeled with provenance and never misrepresented as verified `WORK/RECOVERY` intervals unless classified.

## 4. UI, Accessibility & Telemetry Guardrails
- **3-Tab Shell:** Retain `Today | Training | More` (`TracksideFloatingNavigation`); never reintroduce retired `nav-library`, concentric TSS rings, or Intervals.icu sync. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].
- **Moving-Time Telemetry Charts:** Activity Detail charts render continuous source-measured lines on a pause-compressed moving-time x-axis (`ActivityChartMovingTimeAxis.kt`).
- **Accessibility & Touch Targets:** Primary interactive controls maintain `>= 48.dp` touch targets, tabular numerals (`tnum`), and zero horizontal overflow across `320 dp`, `360 dp`, and `412 dp` viewports up to `1.3x` font scale.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Share-Studio]] · [[UI-Design-System]]

---

## 6. Freeform Workout Logger

- **Source Node:** `docs/wiki/features/Freeform-Logger.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/FreeformScreens.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/FreeformEditor.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/FreeformWorkout.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/FreeformAccess.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt`

# Freeform Workout Logger

The Freeform Logger enables offline, ad-hoc workout recording across **six independent disciplines** (`Running`, `Strength`, `Plyometrics`, `Isometrics`, `Calisthenics`, `Conditioning`) without touching [[Engine-1.5]] coached plans.

## Entry & Lazy Draft Lifecycle (`FreeformScreens.kt`, `FreeformAccess.kt`)
- **Modal Discipline Picker:** From the **Workouts** hub (`Start empty workout`), tapping launches a `#121212` `ModalBottomSheet` 2×3 discipline grid so **Workout templates** stays visible above the fold.
- **No Ghost Drafts:** Selecting a discipline navigates to `freeform/new/{discipline}` in memory (`persisted = false`). Navigating Back with zero edits discards cleanly without writing a draft row.
- **Concurrency-Safe Persistence (`FreeformEditor.kt`):** Auto-save (`350 ms` debounce) and manual save/finish serialize behind a per-workout `Mutex` (`persistMutex`) so delayed auto-saves never race with `access.start(...)` or `access.finish(...)`.
- **One-Tap Draft Discard:** Active `In progress` rows in the Workouts hub expose a `48.dp` trailing discard icon (`freeform-discard-row-{id}`), backed by owner-scoped storage (`account ?: localPlanOwner`).

## Hevy-Style Set Logging & Exercise Picker (`FreeformEditor.kt`)
- **True Empty Start:** Empty non-running sessions open to `+ Add exercise` plus quick-add chips (`freeform-quick-add-*`) drawn from recent same-discipline completed workouts — never auto-inserting placeholder movements.
- **Discipline-Aware Modal Bottom-Sheet Picker (`FreeformExercisePicker`):** Filter chips swap cleanly across `<Discipline>`, `Recent`, and `All`. `FreeformExerciseCatalog` ships built-in **Plyometrics** (`Pogo hops`, bounds, depth jumps) and **Isometrics** (`Wall sit`, holds, planks) entries alongside the exercise library JSON (`ExerciseLibraryV2`). Custom named exercises can be added on the fly.
- **Flattened Tabular Set Grid:** Each exercise renders on `#000000` canvas separated by `StrideLabHairline` with a compact `SET | PREV | metrics | ✓` column header, `#121212` active-set focus, dimmed completed rows with `#D8FB70` checkmarks, swipe-to-dismiss / overflow actions, and expandable optional `Target` / `Rest` / `RPE` / `RIR` fields.
- **Auto-Prefilling `+ Add set`:** Appending a set automatically prefills actuals from the previous set, removing redundant duplicate buttons.
- **Non-Blocking Rest Timer (`FreeformRestTimerBar`):** Confirming a set starts a countdown anchored above the bottom footer without blocking edits to subsequent sets or exercises.
- **Running Freeform Mode:** Captures date, distance, duration, optional effort (`1..10`), workout type, and notes.
- **Completion & Immutability:** Review validates actuals (`FreeformWorkout`), promotes confirmed sets into one immutable completed record in [[Storage-and-FIT-Contracts]], and prevents conflict on retry.

## Related Nodes
- [[00-StrideLab-Index]] · [[Templates-and-Custom-Planner]] · [[Storage-and-FIT-Contracts]] · [[Progress-and-History]] · [[UI-Design-System]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]

---

## 7. Garmin Integration & Activity Import

- **Source Node:** `docs/wiki/features/Garmin-and-Activity-Import.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/GarminPersonalApi.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminPersonalSource.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminHistoryImport.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/FitFileIntake.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/FitActivityDecoder.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ActivityDisplayConsolidation.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminFitWorkoutEncoder.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideActivityDetailSurface.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideScrollActivityContent.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ActivityChartMovingTimeAxis.kt`

# Garmin Integration & Activity Import

StrideLab supports full signed-out Garmin Personal API syncing, local `.FIT` file ingestion, multi-source activity deduplication, continuous moving-time telemetry analysis, and offline `.FIT` workout export to Garmin watches.

## Signed-Out Ingestion & Multi-Source Consolidation
- **Sync Path:** `GarminPersonalApi` / `GarminPersonalSource` → `GarminHistoryImport` → `FitFileIntake` / `FitContainerReader` / `FitActivityDecoder` → `ActivityRecord` + `ActivityDetail`.
- **Credential & Archive Independence:** Uses `localGarminOwner` and `GarminCredentialVault`; functions cleanly with `state.account == null`. `GarminAutoSyncWorker` refreshes in the background even when signed out. See [[ADR-03-Offline-First-Independent-Local-Ownership]].
- **Deduplication (`ActiveActivitySources.kt`, `ActivityDisplayConsolidation.kt`):** Consolidates matching Garmin/manual/cloud records for presentation without deleting or rewriting raw `.FIT` recordings.
- **Local Detail Caching:** Validated `.FIT` details write up to `16 MiB` UTF-8 JSON locally and read in UTF-8 byte windows without inflating cloud sync `outbox` limits. Running dynamics (ground contact time, vertical oscillation, step length, vertical ratio) populate optional nullable fields; derived estimates are explicitly labeled.

## Activity Detail, Moving-Time Charts & Route Maps (`TracksideActivityDetailSurface.kt`, `TracksideScrollActivityContent.kt`)
- **Peek & Continuous Sheet:** Collapsible peek height tracks actual summary content height; expanded sheet provides 4 scrolled sections (`Overview`, `Analysis`, `Splits`, `Charts`) with `TracksideActivitySectionPill` (`pinnedTapSection` keeps short-list tab selections sticky until user scroll moves the offset).
- **Pause-Compressed Moving-Time Charts (`ActivityChartMovingTimeAxis.kt`, `ActivityChartDisplayScale.kt`):** Pace, HR, elevation, and cadence render as continuous source-measured curves over active moving time, omitting pause gaps (`> 30 s`) and dropouts from time-weighted comparisons while leaving source samples immutable.
- **Geographic Route (`TracksideGeographicMap.kt`):** Draws chronological moving GPS fixes across timer pauses, drops `(0, 0)` invalid fixes, and handles antimeridian cuts. Route colors support Mode (`Route`, `Pace`, `Elevation`, `HR`) and split-selection highlighting.

## Watch Workout Export (`GarminFitWorkoutEncoder.kt`, `RunningWorkoutFitExport.kt`)
- Encodes current [[Engine-1.5]] running prescriptions (including approved duration/progression targets) directly to FIT Protocol 2.0 wire bytes in pure Kotlin.
- Compresses consecutive identical Work/Recovery pairs into Garmin repeat steps and guards BPM HR targets behind verified 5-zone cached settings.
- Writes directly via Android's Storage Access Framework document picker (`/GARMIN/NewFiles/`) without altering plans or sync state.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Hard-Guardrails]] · [[Engine-1.5]] · [[Progress-and-History]] · [[Share-Studio]] · [[ADR-03-Offline-First-Independent-Local-Ownership]]

---

## 8. Guided Workout Player & Coach's Debrief

- **Source Node:** `docs/wiki/features/Guided-Workout-Player-and-Debrief.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayerEngine.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutExecutionCheckpoint.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DebriefEngine.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/DebriefCopyTemplates.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingEffectCalculator.kt`

# Guided Workout Player & Coach's Debrief

Guided plan execution pairs a durable AMOLED **Workout Player** for non-running disciplines with read-only **Coach's Debrief** and **Training Effect** analyzers grounded strictly in confirmed work and verified telemetry.

## Durable Guided Workout Execution (`WorkoutPlayer.kt`, `WorkoutPlayerEngine.kt`, `WorkoutExecutionCheckpoint.kt`)
- **Pure Reducer & Local Checkpoints:** `WorkoutPlayerEngine` reduces typed work/rest/skip/undo events against immutable prescriptions. Owner-scoped `player-exec-*` JSON checkpoints persist in `TrainingStore` (`kind = "workout"`, local-only). Confirming a set and entering rest commit together; per-second timer ticks stay in memory.
- **Discipline Controls & Timers:** Supports strength reps/load, plyometric ground contacts, isometric holds, and conditioning intervals including **EMOM** (advances clock at interval boundaries without auto-confirming sets) and **AMRAP** (prompts confirmation of completed rounds/reps). Running timers anchor to elapsed realtime on the current boot and wall-clock time across reboots.
- **Independent Receipts (`Partial · X of Y sets`):** Finalization freezes a compatibility log before updating the plan and writes a local outcome receipt separating session lifecycle (`Finish Early` vs full finish) from set coverage (`confirmed` vs `skipped`). Skipped sets stay out of performed logs; process death mid-save recovers from the frozen log match.

## Coach's Debrief & Training Effect (`Engine15DebriefEngine.kt`, `DebriefCopyTemplates.kt`, `TrainingEffectCalculator.kt`)
- **6 Read-Only Analyzers:** Dispatches discipline-specific analyzers (`Running`, `Strength`, `Plyometrics`, `Isometrics`, `Calisthenics`, `Conditioning`). Cross-training debriefs inspect confirmed workout sets/reps/holds/contacts and explicit `RIR: n` notes; stationary Activity Detail renders a debrief only via an exact workout-log link.
- **Running Narrative & Highlights:** Evaluates pause-excluded moving-time streams (`> 30 s` gaps excluded) to produce one concise lead plus up to two ranked highlights (`CoachDebriefCard.kt`). Half-run pace comparisons require `>= 20 min`; pulse/dynamics drift comparisons require `>= 45 min` at comparable pace. Sparse runs omit the card cleanly with zero filler placeholders.
- **Verified-Zone Training Effect (`TrainingEffectCalculator.kt`):** Produces bounded `0.0..5.0` Aerobic (5-zone weights `1/2/3/5/7`, `>= 75%` moving coverage, verified Garmin/FIT zones only) and Anaerobic (Z4–Z5 or pace surges bracketed by lower intensity) transient scores.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[Hard-Guardrails]] · [[Screens-Map]]

---

## 9. Progress, History & Baseline Evidence

- **Source Node:** `docs/wiki/features/Progress-and-History.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideRunningSetup.kt`

# Progress, History & Baseline Evidence

StrideLab aggregates completed workouts across Coached plans, Custom schedules, Freeform logs, and Garmin/FIT recordings into one deduplicated evidence layer without fabricating cross-discipline totals or mutating historical targets.

## Unified Workout Evidence (`WorkoutEvidence.kt`)
- **Cross-Source Projection (`projectWorkoutEvidence`):** Projects owner-scoped Freeform/Custom completions alongside canonicalized Garmin and FIT activity groups. When a plan session is explicitly linked to a recording (`ActivityPlanLink` or accepted match), presentation merges cleanly into one workout while keeping source identities intact; unlinked activities remain independent.
- **Physical Load Integrity:** Canonical recordings count once in weekly totals (`Run km`, `Run hours`, `Runs` on **Today**). If multiple prescriptions reference the same recording, neither earns adherence credit until ambiguity is resolved. Non-running completions show concise active discipline pills (`Strength`, `Calisthenics`, `Conditioning`) rather than fabricated multisport TSS scores.

## Confirmed Running Baseline Suggestions (`HistoryBaselineCard`)
- Inside Running Plan setup ("Where are you starting?" in `TracksideRunningSetup.kt`), `HistoryBaselineCard` surfaces confirmed recent mileage coverage:
  - Estimated typical weekly volume from completed recent weeks (`>= 2` usable weeks required, tagged explicitly as partial when coverage is limited).
  - Longest confirmed run from the past 30 days.
- Values populate plan-builder inputs only on explicit athlete tap — never silently overwriting manual entries or inventing mileage for zero-history runners (see [[Engine-1.5]]).

## Retirement of Standalone `Workout history` UI
- The standalone `Workout history` sub-screen (`freeform/evidence` and `training-workout-history` header link) was retired during UI Modernization to keep history unified directly inside **Today** (`Recent Activities` → `See all`) and **Training** (`StrideLabUnifiedTrainingTimeline`). `WorkoutEvidence.kt` and `HistoryBaselineCard` remain active underneath. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].

## Related Nodes
- [[00-StrideLab-Index]] · [[Freeform-Logger]] · [[Templates-and-Custom-Planner]] · [[Garmin-and-Activity-Import]] · [[Engine-1.5]] · [[Screens-Map]] · [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]]

---

## 10. Share Studio

- **Source Node:** `docs/wiki/features/Share-Studio.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareRoutePresentation.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareMapSnapshot.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/SharePhotoImport.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/SharePresetStore.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareImageExport.kt`

# Share Studio

Share Studio renders fixed-resolution (`1080 × {1920, 1350, 1080}`) athletic share cards where preview and exported PNG pixels match 1:1.

## Templates, Variants, Ratios & Backgrounds (`ShareStudioModels.kt`)
- **4 Templates & 8 Variants:**
  - `ROUTE` (*Route Focus*: `CLEAN`, `PERFORMANCE`, `STORY`)
  - `STATS` (*Stats Focus*: `BALANCED`, `RUN_RECAP`, `PERFORMANCE`)
  - `MINIMAL` (*Minimal*: `POSTER`, `SILHOUETTE`, `DARK_CLEAN`)
  - `WORKOUT` (*Workout Recap*: `RUN_RECAP`, `BALANCED`, `PERFORMANCE`)
- **3 Export Aspect Ratios (`1080 px` width):** `STORY` (`9:16`, `1920 px`), `PORTRAIT` (`4:5`, `1350 px`), `SQUARE` (`1:1`, `1080 px`).
- **5 Backgrounds:** `MAP` (MapLibre Activity Dark snapshot), `DARK` (`#080B0A`), `GRADIENT` (atmospheric dark poster linear + subtle radial glow), `TRANSPARENT` (corner `(0, 0)` strictly alpha `0`), and `PHOTO` (in-memory center-cropped photo with dual directional scrims).

## Editorial Graphic Renderer (`ShareCardRenderer.kt`)
- **Locked Bounding Geometry (`ShareCardRenderer.geometry`):** Preserves safe map/route minimum height (`>= 152 px`), separation between route and stats, and footer margins across all `routeBalance` (`0..100`) and `layout` (`AIRY` / `COMPACT`) settings.
- **Typography & Photo Scrims:** Uses tabular figures (`tnum`), uppercase tracked eyebrows/labels, subtle shadow contrast on `PHOTO`/`TRANSPARENT`, and dual directional `LinearGradient` scrims (top header + bottom metrics) over a `0x16000000` veil — keeping center-upper photos vivid (`(540, 340)` stays vivid red on red fixtures) with `paint.alpha = 255` before applying gradient shaders.
- **Multi-Pass Coordinate Route:** Draws ambient outer glow, dark contrast halo (non-transparent cards), core route or `PACE`/`ELEVATION`/`HR` gradient trace, plus hollow-start / lime-finish markers derived exclusively from trimmed display segments.
- **Workout Recap Provenance Honesty:** `WORKOUT` cards surface exact lap provenance (`RECORDED_ONLY` / classified source, recording origin) and never claim laps are verified `WORK/RECOVERY` intervals unless classified.

## Studio Editor & Privacy Controls (`ShareStudioScreen.kt`, `ShareRoutePresentation.kt`)
- **GPS Endpoint Privacy Trimming:** Defaults to `SharePrivacy.STANDARD` (`200 m` removed from start and finish), with `400 m` / `800 m` options and an explicit amber warning (`share-trim-off-warning`) when toggled off (`SharePrivacy.OFF`). Full-activity summary stats remain unchanged. See [[ADR-02-GPS-200m-Default-Privacy-Trimming]].
- **Map & Offline Fallback:** Uses MapLibre dark snapshot when online or when `StrideOfflineMapStore.hasCompleteCoverage(route)` covers the full trimmed route; otherwise falls back cleanly to a coordinate-only route trace. Never paints opaque overlays over `g.route`.
- **Canvas-First Studio Layout:** Pure `#000000` top bar (plus sticky `share-workout-provenance` when `WORKOUT`), `#0A0B0D`-bezeled responsive canvas stage (capped at `300.dp` height under `340.dp` width so resolution/section controls fit above the fold at `320 dp` `font_scale 1.3`), preview-only Story safe-area guides, `#121212` tool tray with `Canvas` 2×2 wireframe template previews, up to 100 local presets in [[Storage-and-FIT-Contracts]], and an anchored `#121212` `Save Image` / `Export PNG` / `Share` footer (`>= 48.dp` targets).

## Related Nodes
- [[00-StrideLab-Index]] · [[UI-Design-System]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[ADR-02-GPS-200m-Default-Privacy-Trimming]]

---

## 11. Templates & Custom Workout Planner

- **Source Node:** `docs/wiki/features/Templates-and-Custom-Planner.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutTemplateScreens.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/CustomPlannerScreens.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ManualWorkoutTemplates.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/CustomWorkoutSchedule.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt`

# Templates & Custom Workout Planner

Templates and Custom Schedules allow athletes to author reusable multi-discipline routines, schedule one-off or recurring sessions onto the Training calendar, and execute them independently of [[Engine-1.5]] coached plans.

## Reusable Workout Templates (`WorkoutTemplateScreens.kt`, `ManualWorkoutTemplates.kt`)
- **Routine CRUD:** Athletes can create routines per discipline (`ManualDiscipline`), reorder exercises/sets with targets (`WorkoutRecipe`), save templates from completed workouts, or delete templates.
- **Snapshot-on-Start:** Starting a template copies its structure and targets into a new `FreeformWorkout` draft with a template reference and blank actuals. Editing or deleting the template later leaves previously logged workouts intact.
- **Consistency with UI System:** Uses horizontal discipline filter chips, concise exercise previews, and the shared modal bottom-sheet discipline picker from [[UI-Design-System]].

## Custom Scheduling & Recurrence (`CustomPlannerScreens.kt`, `CustomWorkoutSchedule.kt`)
- **One-Off & Weekly Schedules:** Saved into the `custom_schedules` table of `stridelab-standalone-sessions-v1.db` ([[Storage-and-FIT-Contracts]]). Each `CustomSchedule` snapshots its template and stores recurrence windows plus single-date overrides.
- **Deterministic Occurrence Identity:** `CustomOccurrenceKey(seriesId, anchor)` computes a deterministic `workoutId` from the series ID and original occurrence date — even after single-date moves (`CustomScheduleEdit.One`) or future series updates (`CustomScheduleEdit.Future`).
- **Execution Lifecycle:** Calendar views project unstarted occurrences inside bounded windows; once started via `startCustom`, the occurrence links directly to its stored draft or completed `FreeformWorkout`.

## Source Separation & Calendar Integration (`StrideLabUnifiedTrainingTimeline.kt`)
- **Clear Source Labeling:** The Training timeline distinguishes **Custom** (scheduled/executed template occurrences), **Freeform** (ad-hoc manual sessions), **Coached** (Engine 1.5 sessions), and **Garmin / FIT** imported recordings.
- **Backup & Restore:** `ManualDisciplineArchive` (schema v5; schemas 1–4 backward compatible) exports and restores manual workouts, templates, and custom schedules in one transaction without touching `TrainingStore` or Engine 1.5 plans.

## Related Nodes
- [[00-StrideLab-Index]] · [[Freeform-Logger]] · [[Storage-and-FIT-Contracts]] · [[Engine-1.5]] · [[Progress-and-History]] · [[Screens-Map]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]

---

## 12. Screens Map & Navigation Routes

- **Source Node:** `docs/wiki/ui/Screens-Map.md`
- **Area / Status:** `ui` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabTodayScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayDisciplineDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideMoreScreen.kt`

# Screens Map & Navigation Routes

All navigation routes live under `NativeMainActivity.kt` (`NavHost`) reading immutable `TrainingState` (`TrainingRepository`) and dispatching through `PlanActions`, `NativeActions`, and `FreeformAccess`.

## 1. Three Primary Tab Roots (`showFloatingDock == true`)
- **`home` → Today (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`):**
  - Top discipline filter chips scope the Monday–Sunday strip (`TodayWeekCalendar`), selected-day training card, and adherence across all approved plans (`All`, `Running`, `Strength`, `Calisthenics`, `Conditioning`).
  - Uses a 3-section AMOLED layout: `TodayDateContextHeader` + `TodayDailyTrainingCard` (Planned / Recorded / RestDay / Companion CTA + `180 ms` neutral skeletons), centered 3-column `TodayTrainingOverviewCard` (`Run km`, `Run hours`, `Runs` + non-running pills), and hairline-separated `TodayRecentActivitiesSection` (with `TodayCachedGpsThumbnail`, zero redundant circular shoe icons).
  - Surfaces pending `.FIT` auto-match cards (`All` / `Running`) and controlled adaptation review banners (`All`).
- **`training` → Training (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`):**
  - Calendar-first weekly header with symmetrically aligned `<` / `>` week navigation arrows (`training-week-prev` / `training-week-next`) and a centered bottom `"Return to Today"` / `"Show week"` pill (`training-return-today`) separated by `StrideLabHairline`.
  - Flattened hairline-separated timeline rows mixing **Coached**, **Custom**, **Freeform**, and **Garmin / FIT** sessions, plus plan management, Coaching Diagnostics, adaptation review/undo, paused plans, and **Add workout** (`training-add-workout` → Workouts hub).
- **`settings` → More (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`):**
  - Account card (`AccountProfilePresentation.resolve`) displays user name or email (or `"StrideLab Account"` fallback, JWT claim extraction when needed) — never raw UUIDs as the primary title. When signed out, displays `"Not signed in · Saved on this device"` independently alongside Garmin Connect status.
  - Hosts Garmin Connection, `more/offline-maps`, `more/activity-backup`, `more/preferences`, `more/about`, and `auth/*`.

## 2. Full-Screen Detail & Workflow Routes (`showFloatingDock == false`)
- **`workout/{planId}/{sessionId}` (`WorkoutScreen.kt`):** Planned running detail (profile bar chart, `-2..+2` difficulty selector, `WORKOUT STEPS`, FIT watch export) or guided non-running [[Guided-Workout-Player-and-Debrief]] (`WorkoutPlayer`).
- **`activity/{id}` & `activities` (`TracksideActivityDetailSurface.kt`, `TracksideStationaryActivityDetail.kt`):** MapLibre GPS route, moving-time telemetry charts (`Overview | Analysis | Splits | Charts`), Coach's Debrief, Training Effect, manual plan linking, and Share launcher.
- **Share Studio Modal (`ShareStudioScreen.kt`):** Dialog hosted from Activity Detail. See [[Share-Studio]].
- **`builder/{discipline}` & `blueprint-workout/{planId}/{sessionId}/{generatedAt}/{loginGeneration}`:** Multi-step Engine 1.5 / Hybrid Plan Builder (`PlanBuilder.kt`, `TracksideBuilderScreens.kt`) and read-only Blueprint workout preview (`BlueprintWorkout.kt`).
- **`freeform/*` (`FreeformScreens.kt`, `FreeformEditor.kt`, `WorkoutTemplateScreens.kt`, `CustomPlannerScreens.kt`):** Workouts hub, `freeform/new/{discipline}`, `freeform/edit/{id}`, `freeform/history/{kind}/{id}`, template editor, and custom scheduler. See [[Freeform-Logger]] and [[Templates-and-Custom-Planner]].
- **Schedule Management Sheets:** `manage/{planId}/{date}`, `move/{planId}/{sessionId}`, `review-move/...`, `missed/...`.

## 3. Retired Elements (Do Not Reintroduce)
- **`freeform/evidence` & `training-workout-history`:** Retired standalone `Workout history` screen; data stays unified in [[Progress-and-History]].
- **Concentric Weekly TSS Rings & Elevated Telemetry Feed on Today (`All`):** Replaced by verified 3-section `Today` overview.
- **Legacy Library Tab (`nav-library`), Standalone Running Analytics/Comparison Pages & Intervals.icu Sync:** Permanently retired. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[UI-Design-System]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]]

---

## 13. UI Design System (AMOLED Compose)

- **Source Node:** `docs/wiki/ui/UI-Design-System.md`
- **Area / Status:** `ui` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUiPrimitives.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabVisualKit.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideUi.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksidePlannedRunningPreview.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideRunningStepCards.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt`

# UI Design System (AMOLED Compose)

StrideLab uses a pure AMOLED black design system (`StrideLabSurfaceTokens` in `StrideLabUiPrimitives.kt`) emphasizing typographic hierarchy, hairline separation over nested bordered boxes, tabular numerals, and restrained lime-yellow accents.

## Core Surface & Color Tokens (`StrideLabSurfaceTokens`)
| Token | Hex | Usage |
| :--- | :--- | :--- |
| **AMOLED Canvas** | `#000000` | Primary background across Today, Training, Workouts Hub, Freeform Editor, Templates, Share Studio header/stage, and More |
| **Elevated Surface** | `#121212` | Selective elevation for active set rows, modal bottom sheets, Today's hero training card, Share Studio bottom tray, and anchored footers |
| **Subtle Surface** | `#181A1D` | Compact input fields, discipline/template tiles, pill chips |
| **Hairline Divider** | `#1F242B` | `1.dp` `StrideLabHairline` horizontal dividers replacing nested card borders |
| **Lime Accent** | `#D8FB70` | Reserved strictly for active tab/tool states, primary CTAs (`#080A08` text), confirmed checkmarks, active rest timer cues, adherence bars, and GPS trails |
| **Discipline Accents** | `#D8FB70` / `#A855F7` / `#2DD4BF` / `#FB923C` | Running / Strength / Calisthenics / Conditioning markers |
| **Intensity Accents** | `#4AD3DE` / `#45D4C8` / `#F6D32D` | Warm-up & recovery teal / easy teal / threshold & work interval amber |
| **Text Hierarchy** | `#F5F7FA` / `#A0A8B3` / `#6B737E` | Primary ink / secondary labels & metrics / muted captions & headers |

## Typography, Spacing & Touch Discipline
- **Tabular Numerals:** `TabularTextStyle` / `.tabular()` applies `fontFeatureSettings = "tnum"` across weekly metrics, calendar dates, Freeform set grids, rest countdowns, Share Studio counters, and exported share cards.
- **8-Point Grid & Flat Primitives:** Standard spacing tokens (`4`, `8`, `12`, `16`, `24.dp`), `StrideLabSectionHeader`, `StrideLabHairline`, and `StrideLabFlatRow`.
- **Accessibility:** Primary actions, list rows, chip filters, and icon buttons enforce `>= 48.dp` (`minTouchTarget`), tested on `320 dp`, `360 dp`, and `412 dp` viewports up to `1.3x` font scale.

## Zero-Recomposition Neutral Skeletons (`StrideLabUiPrimitives.kt`)
- `neutralSkeletonBrush` draws animated neutral shimmer (`#16181B` / `#23272E` — never lime-tinted) inside `drawWithCache` + `onDrawBehind` to avoid state-driven recomposition loops.
- Gated behind `rememberDelayedSkeletonVisible` (`180 ms` cold-start delay) so cached reads never flash placeholders, and switches cleanly to a static fill when `ANIMATOR_DURATION_SCALE == 0f`.
- Exposes `SkeletonText`, `SkeletonMetric`, `SkeletonWorkoutRow`, `SkeletonExerciseRow`, `SkeletonCalendar`, and `SkeletonActivityRow`.

## Shared Visualization Components
- **Today Route Thumbnails (`ActivityThumbnailCache.kt`, `TodayStartup.kt`):** Synchronously checks the 6 MB in-memory `LruCache` on frame 1 before touching disk cache, eliminating tab-switch flicker.
- **Workout Profile & Step Timeline (`TracksidePlannedRunningPreview.kt`, `TracksideRunningStepCards.kt`):** Pure-Compose proportional bar chart (`flattenProfileBars` + `-2..+2` difficulty selector) synced with `SingleStep` / `RepeatGroup` cards.
- **Floating Navigation Capsule (`TracksideFloatingNavigation.kt`):** `68.dp` pill hosting `Today`, `Training`, and `More`; hidden automatically on full-screen detail, editor, and studio flows.

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[App-Architecture]]

---

## 14. ADR-01: Engine 1.5 Isolation from Freeform and Custom Logging

- **Source Node:** `docs/wiki/decisions/ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging.md`
- **Area / Status:** `decision` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt`

# ADR-01: Engine 1.5 Isolation from Freeform & Custom Logging

## Context
Athletes need both structured, mathematically validated periodized coaching plans (**Engine 1.5**) and flexible ad-hoc workouts, routines, and personal schedules (**Freeform Logger**, **Templates**, and **Custom Planner**) across six disciplines.

## Decision
Keep Engine 1.5 plan snapshots (`TrainingStore` / `PLAN_SCHEMA = 1`) completely isolated from Freeform workouts, templates, and custom schedules (`ManualDisciplineStore` / `stridelab-standalone-sessions-v1.db`).
- Freeform and Custom writes never mutate `PlanSnapshot`, trigger `validateSaved`, or modify Engine 1.5 adaptation history.
- Unified timeline and baseline calculations (`WorkoutEvidence.kt`) project read-only views across both stores, merging presentation without conflating source ownership (`Coached` vs `Custom` vs `Freeform`).
- Running Plan Setup offers confirmed Freeform/Garmin history to the athlete via `HistoryBaselineCard` purely as opt-in builder suggestions.

## Consequences
- Zero risk of manual logging edits corrupting deterministic Engine 1.5 `difficultyBaseline` checks or weekly budget invariants (`sum(week.distanceKm) == week.proposedKm`).
- Freeform, Templates, and Custom Planner can evolve schema independently (archive schema v5, DB v4) without risking `PLAN_SCHEMA` migrations.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Freeform-Logger]] · [[Templates-and-Custom-Planner]] · [[Progress-and-History]]

---

## 15. ADR-02: GPS 200 m Default Privacy Trimming in Share Studio

- **Source Node:** `docs/wiki/decisions/ADR-02-GPS-200m-Default-Privacy-Trimming.md`
- **Area / Status:** `decision` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareRoutePresentation.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt`

# ADR-02: GPS 200 m Default Privacy Trimming in Share Studio

## Context
Athletes frequently share workout route graphics publicly on social feeds. Unmasked GPS tracks expose exact start and finish locations (commonly home or workplace addresses).

## Decision
Default Share Studio's route privacy control to `SharePrivacy.STANDARD` (`200 m` trimmed from both the start and finish of displayed route geometry), with `400 m` and `800 m` presets and full-activity summary stats kept unaltered.
- `ShareRenderableActivity.from(...)` trims coordinates once per privacy selection; `ShareCardRenderer` and `ShareMapSnapshot` consume only those trimmed segments.
- Switching to `SharePrivacy.OFF` preserves the athlete's previous nonzero trim preset in editor memory and immediately renders `share-trim-off-warning`.
- `SharePresetStore` saves visual layout choices only and never persists raw activity coordinates or user photos.

## Consequences
- Publicly shared images protect home/work endpoints out of the box without distorting distance, duration, pace, or elevation metrics.
- Short routes below safe trim thresholds hide the trace cleanly rather than leaking start/finish coordinates.

## Related Nodes
- [[00-StrideLab-Index]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[Storage-and-FIT-Contracts]]

---

## 16. ADR-03: Offline-First Independent Local Ownership

- **Source Node:** `docs/wiki/decisions/ADR-03-Offline-First-Independent-Local-Ownership.md`
- **Area / Status:** `decision` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/GarminCredentialVault.kt`, `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java`

# ADR-03: Offline-First Independent Local Ownership

## Context
Athletes train in areas with unreliable connectivity and may link Garmin Connect or import `.FIT` files without creating or signing into a StrideLab cloud account.

## Decision
Maintain independent local owner namespaces (`localGarminOwner`, `localPlanOwner`) alongside encrypted local credential storage (`GarminCredentialVault`, `SessionVault`) and non-destructive SQLite storage (`TrainingStore`, `ManualDisciplineStore`).
- `GarminAutoSyncWorker`, `.FIT` decoding/export, Engine 1.5 plan generation/diagnostics/adaptation, Guided Workout Player checkpoints, Freeform logging, and Offline Map downloads all operate with `state.account == null`.
- `TracksideMoreScreen` displays StrideLab cloud sign-in status separately from Garmin Connect status.
- Corrupt SQLite files raise `SQLiteDatabaseCorruptException` instead of auto-deleting unsynced local history.

## Consequences
- Zero cloud-account gating blocks core training, planning, or Garmin sync workflows.
- Signed-out and signed-in profiles retain isolated plans, Freeform logs, and workout receipts without accidental cross-profile data mixing.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[Hard-Guardrails]]

---

## 17. ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers

- **Source Node:** `docs/wiki/decisions/ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers.md`
- **Area / Status:** `decision` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt`

# ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers

## Context
Iterative feature additions accumulated overlapping navigation destinations (standalone `Workout history` screen `freeform/evidence`, a 4th Library tab, separate running analytics/comparison pages, and synthetic TSS ring cards) that duplicated **Today** and **Training** views.

## Decision
Consolidate the navigation shell to 3 primary tabs (`Today | Training | More`) and retire redundant UI routes while retaining underlying pure-Kotlin calculation and storage layers:
- Retired `freeform/evidence` and `training-workout-history`, keeping `WorkoutEvidence.kt` and `HistoryBaselineCard` intact to feed **Today**, **Training**, and Running Plan baseline setup.
- Replaced concentric weekly TSS rings on `Today` with verified running totals (`Run km`, `Run hours`, `Runs`), active discipline pills, and strict link-gated adherence.
- Removed duplicate header/list adornments (`WorkoutHistory` header link, redundant recent-row circular icons) and Intervals.icu UI.

## Consequences
- Cleaner navigation, fewer screen hops, and zero duplicate workout lists to keep synchronized.
- Underlying evidence aggregation and baseline calculators remain cleanly testable and reusable without UI coupling.

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[UI-Design-System]] · [[Progress-and-History]] · [[Hard-Guardrails]]

---

## 18. Main & Supporting Plans (Concept)

- **Source Node:** `docs/wiki/roadmap/Main-and-Supporting-Plans.md`
- **Area / Status:** `roadmap` / `idea`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/CustomWorkoutSchedule.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutTemplateScreens.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt`

# Main & Supporting Plans (Concept — `status: idea`)

> [!NOTE]
> **Unimplemented Concept (`status: idea`):** This node documents a future product concept only. None of the tiering or 48-hour scaling behavior described here exists in shipped app code at commit `36a5eeb8`.

## Concept Overview
Pair **1 active Main Plan** (`Running`, `Strength`, or `Calisthenics`) with **up to 2 flexible Supporting tracks** (`Mobility`, `Plyometrics`, `Isometrics`, `Conditioning`, or low-fatigue `Strength`) designed to complement rather than compete with primary training stimulus.

## Intended Design Constraints
- **Engine 1.5 Untouched:** Build read-only on top of [[Templates-and-Custom-Planner]] and [[Progress-and-History]] without modifying [[Engine-1.5]] periodization or `PLAN_SCHEMA` contracts (consistent with [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]).
- **48-Hour Main Plan Awareness:** Inspect upcoming and recently completed Main Plan sessions inside a rolling 48-hour window before surfacing Supporting work.
- **3-Tier Dynamic Scaling:**
  1. **Full:** Normal Supporting dose when 48h Main Plan fatigue exposure is low.
  2. **Light:** Reduced volume/contacts when a key Main session falls within 24–48h.
  3. **Mobility / Freshness:** Switches to low-fatigue mobility/activation only on race/peak or hard consecutive Main Plan days.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Templates-and-Custom-Planner]] · [[Progress-and-History]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
