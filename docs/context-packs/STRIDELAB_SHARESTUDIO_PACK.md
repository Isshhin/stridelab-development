# StrideLab — Share Studio & Privacy Architecture Pack

> **Generated:** 2026-11-26 14:49 UTC  
> **Verified Commit:** `bef05a7c`  
> **Included Nodes:** 5  
> **Purpose:** Share Studio canvas renderer, dark studio tray, privacy trimming, pixel invariants, and UI design system.

## Table of Contents
1. [StrideLab Knowledge Graph Index](#1-stridelab-knowledge-graph-index) — `00-StrideLab-Index.md` (`verified`)
2. [Share Studio](#2-share-studio) — `features/Share-Studio.md` (`verified`)
3. [UI Design System (AMOLED Compose)](#3-ui-design-system-amoled-compose) — `ui/UI-Design-System.md` (`verified`)
4. [Hard Guardrails & Protected Invariants](#4-hard-guardrails-protected-invariants) — `architecture/Hard-Guardrails.md` (`verified`)
5. [ADR-02: GPS 200 m Default Privacy Trimming in Share Studio](#5-adr-02-gps-200-m-default-privacy-trimming-in-share-studio) — `decisions/ADR-02-GPS-200m-Default-Privacy-Trimming.md` (`verified`)

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

## 2. Share Studio

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

## 3. UI Design System (AMOLED Compose)

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
- `SkeletonShimmerContainer` shares a single `1200 ms` `rememberInfiniteTransition` (`LocalSkeletonShimmerProgress`) across all sibling skeleton bones in a card or screen; `neutralSkeletonBrush` draws the neutral `#16181B` / `#23272E` sweep (never lime-tinted) inside `drawWithCache` + `onDrawBehind` to avoid state-driven recomposition loops.
- `rememberDelayedSkeletonVisible` (`180 ms` cold-start delay) prevents cached fast-path reads from flashing placeholders, `SkeletonCrossfade` smooths skeleton-to-content handoffs (`180 ms`, `0 ms` in reduced motion), and `ANIMATOR_DURATION_SCALE == 0f` renders a static `#16181B` fill cleanly.
- Exposes layout-matched placeholders across Today, Activity (`loading = evidence == null || state.initializing || !state.activitySummariesReady` so cold starts never flash the empty state), Training, Activity Detail (Garmin `.FIT` stream downloads), and Share Studio (replacing raw spinners): `SkeletonHeroTrainingCard`, `SkeletonOverviewMetricsCard`, `SkeletonConsistencyHeatmap`, `SkeletonActivitySummaryCard`, `SkeletonActivityRow`, `SkeletonTrainingHeaderCard`, `SkeletonCoachingCard`, `SkeletonActivityDetailStreams`, `SkeletonShareCardPreview`, `SkeletonChipRow`, `SkeletonText`, `SkeletonMetric`, `SkeletonWorkoutRow`, `SkeletonExerciseRow`, and `SkeletonCalendar`.

## Shared Visualization Components
- **Today Route Thumbnails (`ActivityThumbnailCache.kt`, `TodayStartup.kt`):** Synchronously checks the 6 MB in-memory `LruCache` on frame 1 before touching disk cache, eliminating tab-switch flicker.
- **Workout Profile & Step Timeline (`TracksidePlannedRunningPreview.kt`, `TracksideRunningStepCards.kt`):** Pure-Compose proportional bar chart (`flattenProfileBars` + `-2..+2` difficulty selector) synced with `SingleStep` / `RepeatGroup` cards.
- **Docked Navigation Bar (`TracksideFloatingNavigation.kt`, `QuickLogSheet.kt`):** Edge-to-edge flat `#0A0B0D` bar topped by a `1.dp` `#1F242B` `StrideLabHairline` and `navigationBarsPadding()` with 5 equal slots (`Today | Training | [ + ] | Activity | More`), solid `#D8FB70` center `+` CTA (`48.dp × 48.dp`), `>= 48.dp` touch targets, single-line labels, and `#121212` `QuickLogBottomSheet` modal; automatically hidden on full-screen detail, editor, and studio flows.
- **Anatomical Muscle Map (`AnatomicalMuscleMap.kt`, `ExerciseInfoSheet.kt`):** Pure-Compose side-by-side `FRONT` / `BACK` vector silhouettes using `#181A1D` / `#1F242B` base geometry and the unified `#D8FB70` lime luminance scale (`0.28f..0.95f` alpha), responsive legend stacking below `340 dp`, and a `#121212` `ExerciseInfoSheet` modal. See [[Anatomical-Muscle-Map]].
- **Multi-Discipline 28-Day Consistency Heatmap (`StrideLabVisualKit.kt`):** Hosted on `Today -> All`; fills each active day with its discipline accent color and splits multi-discipline days horizontally into crisp color-coded segments separated by `1.dp` `#000000` hairlines, paired with a dynamic legend for active disciplines.
- **Activity Tab Discipline Pill Strip & Flat Rows (`ActivityHistoryTabScreen.kt`):** Custom AMOLED `34.dp` capsule pills centered in `>= 48.dp` touch targets (`#121212` / `#1F242B` hairline + discipline dot when inactive; solid signature discipline fill with `#080A08` bold ink and tabular count when selected), paired with flat `#000000` `StrideLabHairline`-separated `ActivityHistoryRow` items (no card boxes) showing `EEE | MMM d` (`Mon | Nov 23`) date headers, real source provider labels (never `IMPORTED`), `TodayCachedGpsThumbnail` previews, and 3-column tabular metric strips (`DISTANCE | AVG PACE | AVG HR` for runs).
- **App-Wide Press Highlight & Visual Coaching Cards (`StrideLabUiPrimitives.kt`, `Engine15CoachControls.kt`, `Engine15ScheduleReview.kt`, `NativeMainActivity.kt`):** Interactive cards and flat rows use `StrideLabInteractiveCard` / `Modifier.strideLabPressFeedback` (`0.978f` spring press scale, elevated press fill, accent border highlight, haptic tap, reduced-motion safe), paired with fluid `NavHost` detail/editor push/pop slide+scale+fade transitions (instant bottom-tab root switches) and low-text `#121212` coaching cards (KPI meter tiles, 5-segment fatigue selector bar, and 7-day visual availability/off-day strips).

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[Freeform-Logger]] · [[Share-Studio]] · [[Hard-Guardrails]] · [[App-Architecture]]

---

## 4. Hard Guardrails & Protected Invariants

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

## 5. ADR-02: GPS 200 m Default Privacy Trimming in Share Studio

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
