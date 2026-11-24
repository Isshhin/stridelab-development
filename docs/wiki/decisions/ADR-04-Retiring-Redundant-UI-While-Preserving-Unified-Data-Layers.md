---
title: "ADR-04: Retiring Redundant UI Views While Preserving Unified Data Layers"
area: decision
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/NativeMainActivity.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt"
tags: [stridelab, adr, ui, navigation, cleanup]
---

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
