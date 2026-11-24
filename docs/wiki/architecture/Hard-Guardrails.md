---
title: "Hard Guardrails & Protected Invariants"
area: architecture
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "AGENTS.md"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DailyLoadShaper.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15IndependentPlanGenerator.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareCardRenderer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ShareStudioModels.kt"
tags: [stridelab, guardrails, invariants, privacy, regression-guards]
---

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
