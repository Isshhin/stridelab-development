---
title: "ADR-01: Engine 1.5 Isolation from Freeform and Custom Logging"
area: decision
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt"
tags: [stridelab, adr, architecture, isolation, engine15, freeform]
---

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
