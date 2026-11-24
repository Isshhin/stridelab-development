---
title: "Progress, History & Baseline Evidence"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutEvidence.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TodayAdaptiveDashboard.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TracksideRunningSetup.kt"
tags: [stridelab, feature, history, progress, evidence, baseline]
---

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
