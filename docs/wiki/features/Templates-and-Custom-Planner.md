---
title: "Templates & Custom Workout Planner"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutTemplateScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/CustomPlannerScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualWorkoutTemplates.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/CustomWorkoutSchedule.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrideLabUnifiedTrainingTimeline.kt"
tags: [stridelab, feature, templates, custom-planner, scheduling]
---

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
