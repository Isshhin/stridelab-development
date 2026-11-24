---
title: "Anatomical Muscle Map"
area: roadmap
status: verified
last_verified_commit: "75ce697e"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/MuscleFocusProjector.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/AnatomicalMuscleMap.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseInfoSheet.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrengthCalisthenicsProgression.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseLibraryV2.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseAlternatives.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/CoachedSetTable.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformEditor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutTemplateScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/BlueprintWorkout.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideActivityDetailSurface.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideStationaryActivityDetail.kt"
tags: [stridelab, roadmap, ui, anatomy, muscle-map, compose]
---

# Anatomical Muscle Map (`status: verified`)

> [!NOTE]
> **Verified Implementation (`status: verified`):** Shipped and verified across Placements 1 and 2 (`MuscleFocusProjector.kt`, `AnatomicalMuscleMap.kt`, `ExerciseInfoSheet.kt`). Preserves `PLAN_SCHEMA = 1`, offline parity ([[ADR-03-Offline-First-Independent-Local-Ownership]]), and confirmed-only set honesty ([[Hard-Guardrails]]). Placement 3 (7-day rolling heatmap on `Training`) remains parked for v2.

## 1. Audited Canonical Muscle Keys (100% Coverage)
All 66 records in `exercise-library-v2.json` and all 19 built-in Plyometrics/Isometrics entries in `FreeformExerciseCatalog` (`FreeformEditor.kt`) share the exact 28 canonical `snake_case` keys listed in `ExerciseMuscleCatalog.STANDARD_MUSCLES` (`StrengthCalisthenicsProgression.kt`):

`abdominals`, `adductors`, `anterior_deltoid`, `calves`, `elbow_flexors`, `foot_stabilizers`, `forearm_grip`, `gluteus_maximus`, `gluteus_medius`, `hamstrings`, `hip_flexors`, `lateral_trunk_muscles`, `latissimus_dorsi`, `lower_trapezius`, `neck_rotators`, `obliques`, `pectoralis_major`, `posterior_deltoid`, `quadriceps`, `rhomboids`, `serratus_anterior`, `shoulder_stabilizers`, `spinal_extensors`, `tibialis_anterior`, `trapezius`, `triceps`, `trunk_rotators`, `trunk_stabilizers`.

## 2. Pure-Kotlin Projection (`MuscleFocusProjector.kt`)
- **Regions (`AnatomicalMuscleRegion`, `BodySide`):** Maps 100% of the 28 canonical keys deterministically across concrete `FRONT` and `BACK` silhouette regions with zero silently dropped keys. Resolves legacy ID aliases, `swap-*-regression` / `swap-*-progression` wrappers, and case-insensitive movement name fallbacks.
- **Projectors (`MuscleFocusMap`):**
  - `forExercise`: Maps `primaryMuscles` to `1.0f` and `secondaryMuscles` to `0.35f` (primary wins on region overlap).
  - `forPlannedSession`: Weights target sets (`Primary = 1.0f * sets`, `Secondary = 0.4f * sets`) and normalizes `0f..1f`.
  - `forConfirmedSession` / `forConfirmedExecution` / `forConfirmedFreeformWorkout`: Aggregates **confirmed sets only**, strictly excluding skipped sets and unconfirmed `SetDisposition.PENDING` partial holds (matching `Engine15DebriefEngine`). Unmapped custom exercises or empty sessions yield `isEmpty == true` so callers omit the section cleanly.

## 3. Pure-Compose Side-by-Side Vector Map (`AnatomicalMuscleMap.kt`)
- **AMOLED Visual Contract ([[UI-Design-System]]):**
  - Side-by-side **FRONT** and **BACK** athletic silhouettes are always visible simultaneously (no Flip toggle).
  - Base silhouette & inactive muscle groups render in `#181A1D` with `#1F242B` hairlines (`1.dp`).
  - Active focus uses the **unified `#D8FB70` lime luminance scale** (`0.28f` secondary/low stimulus up to `0.95f` primary/peak stimulus) everywhere, keeping discipline tint strictly on small text pills.
  - `MuscleFocusCardContent` adapts smoothly across `412 dp (1.0x)`, `360 dp (1.3x)`, and `320 dp (1.3x)` viewports with zero horizontal overflow.

## 4. Scoped v1 Placements (Placements 1 & 2)
- **Placement 1 — On-Demand Exercise Info Sheet (`ExerciseInfoSheet.kt`):**
  - `#121212` `ModalBottomSheet` displaying exercise title, discipline pill, metric type badge, side-by-side Front/Back map, coaching cues from `ExerciseLibraryV2`, and `ExerciseAlternatives` easier/harder chips.
  - Triggered via `>= 48.dp` controls (`exercise-info-trigger-*`) from `CoachedSetTable`, `FreeformEditor`, `FreeformExercisePicker`, and `WorkoutTemplateScreens` without rendering inline silhouettes inside active set rows or disturbing live set state / `persistMutex`.
- **Placement 2 — Whole-Session Focus (`SessionMuscleFocusSection`):**
  - Flat `#000000` hairline-bounded `MUSCLE FOCUS` summary with honest provenance (`"Planned session focus"` vs. `"Based on N confirmed sets"`) in `BlueprintWorkout`, non-running `WorkoutScreen` (including a compact collapsed toggle in `CoachedSetTable`), `FreeformHistoryDetail` (`FreeformScreens.kt`), and `TracksideStationaryActivityDetail`.

## 5. Explicitly Parked for v2 (Out of Scope)
- **Placement 3 (7-Day Rolling Heatmap on `Training` / `Today`):** Deferred to a future pass until hybrid running-load representation is designed, keeping [[ADR-04-Retiring-Redundant-UI-While-Preserving-Unified-Data-Layers]] and the `Today` / `Training` layouts untouched in v1.

## Related Nodes
- [[00-StrideLab-Index]] · [[Strength-and-Calisthenics-Overhaul]] · [[UI-Design-System]] · [[Screens-Map]] · [[Freeform-Logger]] · [[Guided-Workout-Player-and-Debrief]] · [[Hard-Guardrails]]
