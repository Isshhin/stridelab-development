# StrideLab — UI Design System, Screens & Manual Logging Pack

> **Generated:** 2026-11-24 14:35 UTC  
> **Verified Commit:** `aaf8f51d`  
> **Included Nodes:** 6  
> **Purpose:** AMOLED UI system, screens map, Freeform Logger, Templates/Custom Planner, and guardrails.

## Table of Contents
1. [StrideLab Knowledge Graph Index](#1-stridelab-knowledge-graph-index) — `00-StrideLab-Index.md` (`verified`)
2. [UI Design System (AMOLED Compose)](#2-ui-design-system-amoled-compose) — `ui/UI-Design-System.md` (`verified`)
3. [Screens Map & Navigation Routes](#3-screens-map-navigation-routes) — `ui/Screens-Map.md` (`verified`)
4. [Freeform Workout Logger](#4-freeform-workout-logger) — `features/Freeform-Logger.md` (`verified`)
5. [Templates & Custom Workout Planner](#5-templates-custom-workout-planner) — `features/Templates-and-Custom-Planner.md` (`verified`)
6. [Hard Guardrails & Protected Invariants](#6-hard-guardrails-protected-invariants) — `architecture/Hard-Guardrails.md` (`verified`)

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

## 2. UI Design System (AMOLED Compose)

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
- **Docked Navigation Bar (`TracksideFloatingNavigation.kt`, `QuickLogSheet.kt`):** Edge-to-edge flat `#0A0B0D` bar topped by a `1.dp` `#1F242B` `StrideLabHairline` and `navigationBarsPadding()` with 5 equal slots (`Today | Training | [ + ] | Activity | More`), solid `#D8FB70` center `+` CTA (`48.dp × 48.dp`), `>= 48.dp` touch targets, single-line labels, and `#121212` `QuickLogBottomSheet` modal; automatically hidden on full-screen detail, editor, and studio flows.
- **Anatomical Muscle Map (`AnatomicalMuscleMap.kt`, `ExerciseInfoSheet.kt`):** Pure-Compose side-by-side `FRONT` / `BACK` vector silhouettes using `#181A1D` / `#1F242B` base geometry and the unified `#D8FB70` lime luminance scale (`0.28f..0.95f` alpha), responsive legend stacking below `340 dp`, and a `#121212` `ExerciseInfoSheet` modal. See [[Anatomical-Muscle-Map]].
- **Multi-Discipline 28-Day Consistency Heatmap (`StrideLabVisualKit.kt`):** Hosted on `Today -> All`; fills each active day with its discipline accent color and splits multi-discipline days horizontally into crisp color-coded segments separated by `1.dp` `#000000` hairlines, paired with a dynamic legend for active disciplines.
- **Activity Tab Discipline Pill Strip (`ActivityHistoryTabScreen.kt`):** Custom AMOLED `34.dp` capsule pills centered in `>= 48.dp` touch targets (`#121212` / `#1F242B` hairline + discipline dot when inactive; solid signature discipline fill with `#080A08` bold ink and tabular count when selected).

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[App-Architecture]]

---

## 3. Screens Map & Navigation Routes

- **Source Node:** `docs/wiki/ui/Screens-Map.md`
- **Area / Status:** `ui` / `verified`
- **Key Files:** `native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabTodayScreen.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TodayDisciplineDashboard.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt`, `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideMoreScreen.kt`

# Screens Map & Navigation Routes

All navigation routes live under `NativeMainActivity.kt` (`NavHost`) reading immutable `TrainingState` (`TrainingRepository`) and dispatching through `PlanActions`, `NativeActions`, and `FreeformAccess`.

## 1. Primary Tab Roots & Center Quick-Log (`showFloatingDock == true`)
- **`home` → Today (`StrideLabTodayScreen.kt`, `TodayAdaptiveDashboard.kt`, `TodayDisciplineDashboard.kt`):**
  - Top discipline filter chips scope the Monday–Sunday strip (`TodayWeekCalendar`), selected-day training card, and adherence across all approved plans (`All`, `Running`, `Strength`, `Calisthenics`, `Conditioning`).
  - Uses an AMOLED layout on `All`: `TodayDateContextHeader` + `TodayDailyTrainingCard` (Planned / Recorded / RestDay / Companion CTA + `180 ms` neutral skeletons), centered 3-column `TodayTrainingOverviewCard` (`Run km`, `Run hours`, `Runs` + non-running pills), multi-discipline `28-DAY CONSISTENCY` heatmap (`ConsistencyHeatmapStrip`, splitting days with multiple disciplines into color-coded segments + dynamic discipline legend), and hairline-separated `TodayRecentActivitiesSection` (with `TodayCachedGpsThumbnail`, zero redundant circular shoe icons, and `See all` opening the `Activity` tab).
  - Surfaces pending `.FIT` auto-match cards (`All` / `Running`) and controlled adaptation review banners (`All`); `Running` omits the standalone Coaching Diagnostics card and per-discipline Consistency card.
- **`training` → Training (`StrideLabUnifiedTrainingTimeline.kt`, `TracksideParityTraining.kt`):**
  - Focused on forward-looking plans, calendar-first weekly scheduling (`training-week-prev` / `training-week-next`, centered `"Return to Today"` / `"Show week"` pill separated by `StrideLabHairline`), custom schedules, Coaching Diagnostics, and athlete-approved plan adaptations. The header `Add Workout` button is removed so the center `[ + ]` Quick-Log sheet is the single primary logger.
- **Center `[ + ]` Quick-Log (`QuickLogSheet.kt`):**
  - Opens a `#121212` `ModalBottomSheet` (`quick-log-sheet`) directly over any primary tab: conditional `In progress` drafts (resume / `48.dp` discard), 2×3 discipline empty-workout grid (`freeform/new/{discipline}` with lazy no-ghost-draft persistence), and direct shortcuts to `Workout templates` & Workouts hub.
- **`activities` → Activity (`ActivityHistoryTabScreen.kt`):**
  - Primary read-only history tab backed by `WorkoutEvidence.kt` (`projectWorkoutEvidence`) unifying Garmin / `.FIT`, Coached, Custom, and Freeform completions with a StrideLab AMOLED capsule discipline filter strip (`34.dp` visual pill inside `>= 48.dp` touch target, color dot indicators, animated active discipline fills, tabular session counts), `TodayCachedGpsThumbnail` route previews for imported runs, and direct tap routing to `activity/{id}`, `workout/{planId}/{sessionId}`, or `freeform/history/{kind}/{id}` (signed-out capable per [[ADR-03-Offline-First-Independent-Local-Ownership]]).
- **`settings` → More (`TracksideMoreScreen.kt`, `MoreFeatureUi.kt`):**
  - Account card (`AccountProfilePresentation.resolve`) displays user name or email (or `"StrideLab Account"` fallback, JWT claim extraction when needed) — never raw UUIDs as the primary title. When signed out, displays `"Not signed in · Saved on this device"` independently alongside Garmin Connect status.
  - Hosts Garmin Connection, `more/offline-maps`, `more/activity-backup`, `more/preferences`, `more/about`, and `auth/*`.

## 2. Full-Screen Detail & Workflow Routes (`showFloatingDock == false`)
- **`workout/{planId}/{sessionId}` (`WorkoutScreen.kt`):** Planned running detail (profile bar chart, `-2..+2` difficulty selector, `WORKOUT STEPS`, FIT watch export) or guided non-running [[Guided-Workout-Player-and-Debrief]] (`CoachedSetTable` default for Strength & Calisthenics with `ExerciseInfoSheet` triggers, collapsible `SessionMuscleFocusSection`, and scoped `WorkoutPlayer` hold-timer handoff; `WorkoutPlayer` default for timer-heavy disciplines).
- **`activity/{id}` & `activities` (`TracksideActivityDetailSurface.kt`, `TracksideStationaryActivityDetail.kt`):** MapLibre GPS route, moving-time telemetry charts (`Overview | Analysis | Splits | Charts`), Coach's Debrief, Training Effect, manual plan linking (plus confirmed-only `SessionMuscleFocusSection` when linked to a completed stationary workout), and Share launcher.
- **Share Studio Modal (`ShareStudioScreen.kt`):** Dialog hosted from Activity Detail. See [[Share-Studio]].
- **`builder/{discipline}` & `blueprint-workout/{planId}/{sessionId}/{generatedAt}/{loginGeneration}`:** Multi-step Engine 1.5 / Hybrid Plan Builder (`PlanBuilder.kt`, `TracksideBuilderScreens.kt`) and read-only Blueprint workout preview (`BlueprintWorkout.kt`, including `SessionMuscleFocusSection` for planned non-running workouts).
- **`freeform/*` (`FreeformScreens.kt`, `FreeformEditor.kt`, `WorkoutTemplateScreens.kt`, `CustomPlannerScreens.kt`):** Workouts hub, `freeform/new/{discipline}`, `freeform/edit/{id}`, `freeform/history/{kind}/{id}`, template editor, and custom scheduler. See [[Freeform-Logger]] and [[Templates-and-Custom-Planner]].
- **Schedule Management Sheets:** `manage/{planId}/{date}`, `move/{planId}/{sessionId}`, `review-move/...`, `missed/...`.

## 3. Retired Elements (Do Not Reintroduce)
- **`freeform/evidence` & `training-workout-history`:** Retired standalone `Workout history` screen; data stays unified in [[Progress-and-History]].
- **Concentric Weekly TSS Rings & Elevated Telemetry Feed on Today (`All`):** Replaced by verified 3-section `Today` overview.
- **Legacy Library Tab (`nav-library`), Standalone Running Analytics/Comparison Pages & Intervals.icu Sync:** Permanently retired. See [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]].

## Related Nodes
- [[00-StrideLab-Index]] · [[App-Architecture]] · [[UI-Design-System]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]]

---

## 4. Freeform Workout Logger

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
- **Discipline-Aware Modal Bottom-Sheet Picker (`FreeformExercisePicker`):** Filter chips swap cleanly across `<Discipline>`, `Recent`, and `All`. `FreeformExerciseCatalog` ships built-in **Plyometrics** (`Pogo hops`, bounds, depth jumps) and **Isometrics** (`Wall sit`, holds, planks) entries alongside the exercise library JSON (`ExerciseLibraryV2`). Rows in the picker, `FreeformEditor`, and `WorkoutTemplateEditor` expose `>= 48.dp` info triggers opening [[Anatomical-Muscle-Map]] `ExerciseInfoSheet` without disturbing active sets or drafts; `FreeformHistoryDetail` shows confirmed-only `SessionMuscleFocusSection` when mapped muscles exist. Custom named exercises can be added on the fly.
- **Flattened Tabular Set Grid:** Each exercise renders on `#000000` canvas separated by `StrideLabHairline` with a compact `SET | PREV | metrics | ✓` column header, `#121212` active-set focus, dimmed completed rows with `#D8FB70` checkmarks, swipe-to-dismiss / overflow actions, and expandable optional `Target` / `Rest` / `RPE` / `RIR` fields.
- **Auto-Prefilling `+ Add set`:** Appending a set automatically prefills actuals from the previous set, removing redundant duplicate buttons.
- **Non-Blocking Rest Timer (`FreeformRestTimerBar`):** Confirming a set starts a countdown anchored above the bottom footer without blocking edits to subsequent sets or exercises.
- **Running Freeform Mode:** Captures date, distance, duration, optional effort (`1..10`), workout type, and notes.
- **Completion & Immutability:** Review validates actuals (`FreeformWorkout`), promotes confirmed sets into one immutable completed record in [[Storage-and-FIT-Contracts]], and prevents conflict on retry.

## Related Nodes
- [[00-StrideLab-Index]] · [[Templates-and-Custom-Planner]] · [[Storage-and-FIT-Contracts]] · [[Progress-and-History]] · [[UI-Design-System]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]

---

## 5. Templates & Custom Workout Planner

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

## 6. Hard Guardrails & Protected Invariants

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
