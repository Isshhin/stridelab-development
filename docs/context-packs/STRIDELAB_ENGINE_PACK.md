# StrideLab — Architecture, Engine 1.5 & FIT Contracts Pack

> **Generated:** 2026-11-24 16:01 UTC  
> **Verified Commit:** `c0703101`  
> **Included Nodes:** 8  
> **Purpose:** App architecture, deterministic Engine 1.5 coaching rules, SQLite/FIT contracts, Guided Player, and ADRs.

## Table of Contents
1. [StrideLab Knowledge Graph Index](#1-stridelab-knowledge-graph-index) — `00-StrideLab-Index.md` (`verified`)
2. [App Architecture & Data Flow](#2-app-architecture-data-flow) — `architecture/App-Architecture.md` (`verified`)
3. [Engine 1.5 — Deterministic Training Coach](#3-engine-15-deterministic-training-coach) — `architecture/Engine-1.5.md` (`verified`)
4. [Storage, Schemas & FIT Contracts](#4-storage-schemas-fit-contracts) — `architecture/Storage-and-FIT-Contracts.md` (`verified`)
5. [Hard Guardrails & Protected Invariants](#5-hard-guardrails-protected-invariants) — `architecture/Hard-Guardrails.md` (`verified`)
6. [Guided Workout Player & Coach's Debrief](#6-guided-workout-player-coachs-debrief) — `features/Guided-Workout-Player-and-Debrief.md` (`verified`)
7. [ADR-01: Engine 1.5 Isolation from Freeform and Custom Logging](#7-adr-01-engine-15-isolation-from-freeform-and-custom-logging) — `decisions/ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging.md` (`verified`)
8. [ADR-03: Offline-First Independent Local Ownership](#8-adr-03-offline-first-independent-local-ownership) — `decisions/ADR-03-Offline-First-Independent-Local-Ownership.md` (`verified`)

---

## 1. StrideLab Knowledge Graph Index

- **Source Node:** `docs/wiki/00-StrideLab-Index.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt`, `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java`

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

---

## 2. App Architecture & Data Flow

- **Source Node:** `docs/wiki/architecture/App-Architecture.md`
- **Area / Status:** `architecture` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/StrideApplication.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/PlanActions.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/QuickLogSheet.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/ActivityHistoryTabScreen.kt`

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
NativeMainActivity NavHost ──► Jetpack Compose Screens (5-Slot Docked Shell + Detail Routes)
```

## Navigation Shell (`NativeMainActivity.kt`)
- **4 Retained Tab Roots + Center Quick-Log CTA** hosted above `TracksideFloatingNavigation` (edge-to-edge `#0A0B0D` docked AMOLED bar with `1.dp` `#1F242B` hairline):
  1. `home` → **Today** (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`)
  2. `training` → **Training** (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`)
  3. Center `[ + ]` Quick-Log (`nav-quick-log`) → opens `#121212` `QuickLogBottomSheet` (`QuickLogSheet.kt`)
  4. `activities` → **Activity** (`ActivityHistoryTabScreen.kt`, backed read-only by `WorkoutEvidence.kt`, signed-out capable)
  5. `settings` → **More** (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`)
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
- **Read-Only Strength & Calisthenics Progression (`StrengthCalisthenicsProgression.kt`):** Preserves static plan blocks (`PLAN_SCHEMA = 1`) while projecting next-session overlays from confirmed `WorkoutLog` and freeform history keyed by canonical exercise ID (`ExerciseProgressionHistoryReader`). `StrengthProgressionProjector` delivers Session 1 effort-anchored benchmarks, double progression, and bounded 3–10 rep Epley e1RM dual options snapped to `2.5 kg` / `5 lb` plates; `CalisthenicsProgressionProjector` offers opt-in `ExerciseAlternatives.harder` upgrades when rep/hold ceilings are met; `ExerciseMuscleCatalog` supplies disjoint `primaryMuscles` / `secondaryMuscles` tags across library and built-in Plyometrics/Isometrics movements.
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
- **5-Slot Docked Shell:** Retain the edge-to-edge AMOLED docked bar `Today | Training | [ + ] | Activity | More` (`TracksideFloatingNavigation`); never reintroduce retired `nav-library`, concentric TSS rings on `Today`, legacy standalone running analytics/comparison pages, or Intervals.icu sync. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].
- **Moving-Time Telemetry Charts:** Activity Detail charts render continuous source-measured lines on a pause-compressed moving-time x-axis (`ActivityChartMovingTimeAxis.kt`).
- **Accessibility & Touch Targets:** Primary interactive controls maintain `>= 48.dp` touch targets, tabular numerals (`tnum`), and zero horizontal overflow across `320 dp`, `360 dp`, and `412 dp` viewports up to `1.3x` font scale.

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Share-Studio]] · [[UI-Design-System]]

---

## 6. Guided Workout Player & Coach's Debrief

- **Source Node:** `docs/wiki/features/Guided-Workout-Player-and-Debrief.md`
- **Area / Status:** `feature` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayerEngine.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutExecutionCheckpoint.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DebriefEngine.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/DebriefCopyTemplates.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingEffectCalculator.kt`

# Guided Workout Player & Coach's Debrief

Guided plan execution pairs a durable AMOLED **Workout Player** for non-running disciplines with read-only **Coach's Debrief** and **Training Effect** analyzers grounded strictly in confirmed work and verified telemetry.

## Durable Guided Workout Execution (`WorkoutPlayer.kt`, `WorkoutPlayerEngine.kt`, `WorkoutExecutionCheckpoint.kt`)
- **Pure Reducer & Local Checkpoints:** `WorkoutPlayerEngine` reduces typed work/rest/skip/undo events (including table-driven `FocusSet`, `CompleteSetAt`, `SaveUnconfirmedPartialHold`, `SkipSetAt`, `UnconfirmSetAt`, `AddExtraSet`, and `StartNextSet`) against immutable prescriptions. Owner-scoped `player-exec-*` JSON checkpoints persist in `TrainingStore` (`kind = "workout"`, local-only). Confirming a set and entering rest commit together; per-second timer ticks stay in memory.
- **Set-Table Default & Scoped Hold-Timer Handoff (`CoachedSetTable.kt`, `DurableWorkoutPlayer.kt`):** Coached Strength and Calisthenics workouts default in `WorkoutScreen.kt` to the Hevy-style flattened AMOLED set grid (`CoachedSetTable`) backed directly by `WorkoutExecution`, while timer-heavy tracks keep `WorkoutPlayer` first. Exercise headers provide `>= 48.dp` `ExerciseInfoSheet` triggers ([[Anatomical-Muscle-Map]]) alongside a collapsible `MUSCLE FOCUS` summary, never rendering inline silhouettes inside active set rows. Timed holds expose a `>= 48.dp` **Timer** button opening `WorkoutPlayer` in `scopedToTable` mode with three controls (**Back to Table**, **Play / Pause** + `Log Xs`, **Start Next Set**); pausing or backing out after `>= 3 s` auto-saves an unconfirmed `PENDING` hold excluded from `WorkoutLog` / `Engine15DebriefEngine` (taps `< 3 s` are ignored).
- **Discipline Controls & Timers:** Supports strength reps/load, plyometric ground contacts, isometric holds, and conditioning intervals including **EMOM** (advances clock at interval boundaries without auto-confirming sets) and **AMRAP** (prompts confirmation of completed rounds/reps). Running timers anchor to elapsed realtime on the current boot and wall-clock time across reboots.
- **Independent Receipts (`Partial · X of Y sets`):** Finalization freezes a compatibility log before updating the plan and writes a local outcome receipt separating session lifecycle (`Finish Early` vs full finish) from set coverage (`confirmed` vs `skipped`). Skipped sets stay out of performed logs; process death mid-save recovers from the frozen log match.

## Coach's Debrief & Training Effect (`Engine15DebriefEngine.kt`, `DebriefCopyTemplates.kt`, `TrainingEffectCalculator.kt`)
- **6 Read-Only Analyzers:** Dispatches discipline-specific analyzers (`Running`, `Strength`, `Plyometrics`, `Isometrics`, `Calisthenics`, `Conditioning`). Cross-training debriefs inspect confirmed workout sets/reps/holds/contacts and explicit `RIR: n` notes; stationary Activity Detail renders a debrief only via an exact workout-log link.
- **Running Narrative & Highlights:** Evaluates pause-excluded moving-time streams (`> 30 s` gaps excluded) to produce one concise lead plus up to two ranked highlights (`CoachDebriefCard.kt`). Half-run pace comparisons require `>= 20 min`; pulse/dynamics drift comparisons require `>= 45 min` at comparable pace. Sparse runs omit the card cleanly with zero filler placeholders.
- **Verified-Zone Training Effect (`TrainingEffectCalculator.kt`):** Produces bounded `0.0..5.0` Aerobic (5-zone weights `1/2/3/5/7`, `>= 75%` moving coverage, verified Garmin/FIT zones only) and Anaerobic (Z4–Z5 or pace surges bracketed by lower intensity) transient scores.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[Hard-Guardrails]] · [[Screens-Map]]

---

## 7. ADR-01: Engine 1.5 Isolation from Freeform and Custom Logging

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

## 8. ADR-03: Offline-First Independent Local Ownership

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
