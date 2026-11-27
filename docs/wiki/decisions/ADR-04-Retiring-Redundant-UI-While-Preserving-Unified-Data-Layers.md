---
title: "ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers"
area: decision
status: verified
last_verified_commit: "1f712a2a"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFloatingNavigation.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/QuickLogSheet.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ActivityHistoryTabScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt"
tags: [stridelab, adr, ui, navigation, cleanup]
---

# ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers

## Context
Iterative feature additions accumulated overlapping navigation destinations (standalone `Workout history` screen `freeform/evidence`, a 4th Library tab, separate running analytics/comparison pages, and synthetic TSS ring cards) that duplicated **Today** and **Training** views.

## Decision
Consolidate overlapping views and retire redundant UI routes while retaining underlying pure-Kotlin calculation and storage layers:
- Retired `freeform/evidence` and `training-workout-history`, keeping `WorkoutEvidence.kt` and `HistoryBaselineCard` intact to feed **Today**, **Activity**, **Training**, and Running Plan baseline setup.
- Replaced concentric weekly TSS rings on `Today` with verified running totals (`Run km`, `Run hours`, `Runs`), active discipline pills, and strict link-gated adherence.
- Removed duplicate header/list adornments (`WorkoutHistory` header link, redundant recent-row circular icons) and Intervals.icu UI.

## Amendment — 5-Slot Docked Navigation Shell & Unified Activity Tab
To separate forward-looking **Training** (coached plans, upcoming weeks, custom schedules, diagnostics, and adaptations) from chronological completed history without reintroducing `nav-library` or legacy standalone analytics:
- Evolved the primary shell in `TracksideFloatingNavigation.kt` and `NativeMainActivity.kt` from a 3-tab floating capsule (`Today | Training | More`) to an edge-to-edge docked AMOLED bar with 5 balanced slots: **`Today | Training | [ + ] | Activity | More`**.
- The center `#D8FB70` `[ + ]` button opens a `#121212` `QuickLogBottomSheet` over the current tab (in-progress drafts, 2×3 empty workout discipline grid, and templates/custom scheduler shortcuts).
- **Activity** (`activities`) is promoted to a primary read-only tab (`ActivityHistoryTabScreen.kt`) backed directly by `projectWorkoutEvidence` (`WorkoutEvidence.kt`), keeping `Today`'s verified 3-section layout untouched and preserving offline/signed-out parity ([[ADR-03-Offline-First-Independent-Local-Ownership]]).

## Consequences
- Cleaner navigation, fewer screen hops, and zero duplicate workout lists to keep synchronized.
- Underlying evidence aggregation and baseline calculators remain cleanly testable and reusable without UI coupling.

## Related Nodes
- [[00-StrideLab-Index]] · [[Screens-Map]] · [[UI-Design-System]] · [[Progress-and-History]] · [[Hard-Guardrails]]
