---
title: "Freeform Workout Logger"
area: feature
status: verified
last_verified_commit: "bef05a7c"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformEditor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformWorkout.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformAccess.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt"
tags: [stridelab, feature, freeform, logger, strength, multi-discipline]
---

# Freeform Workout Logger

The Freeform Logger enables offline, universal ad-hoc workout recording across all non-running disciplines (`Strength`, `Calisthenics`, `Plyometrics`, `Isometrics`, `Conditioning`, and custom exercises) in one unified session without touching [[Engine-1.5]] coached plans. (`Running` is omitted from manual workout logging and template creation while preserving legacy storage compatibility.)

## Entry & Lazy Draft Lifecycle (`FreeformScreens.kt`, `FreeformAccess.kt`)
- **Direct Universal Entry:** Tapping **`Start empty workout`** from Quick-Log (`QuickLogBottomSheet`) or the **Workouts** hub (`FreeformHub`) navigates straight into the universal logger (`persisted = false`) without an upfront discipline-category sheet; **Create template** opens the universal template editor directly.
- **No Ghost Drafts:** Navigating Back with zero edits discards cleanly without writing a draft row.
- **Concurrency-Safe Persistence (`FreeformEditor.kt`):** Auto-save (`350 ms` debounce) and manual save/finish serialize behind a per-workout `Mutex` (`persistMutex`) so delayed auto-saves never race with `access.start(...)` or `access.finish(...)`.
- **One-Tap Draft Discard:** Active `In progress` rows in Quick-Log and the Workouts hub expose a `48.dp` trailing discard icon (`freeform-discard-row-{id}`), backed by owner-scoped storage (`account ?: localPlanOwner`).

## Modern AMOLED Logger & Universal Exercise Picker (`FreeformEditor.kt`)
- **AMOLED Logger Shell:** Features an obsidian circular back button, formatted date subtitle (`EEE, MMM d`), top-right discard action, live 3-column session summary strip (`EXERCISES` · `CONFIRMED` · `VOLUME`/`TIME`), obsidian empty-state card with recent quick-add chips across all non-running completions, collapsible `Session details` (optional workout name, date picker, effort, notes), and a unified `#0A0B0D` bottom dock combining the capsule rest timer (`FreeformRestTimerBar` + countdown progress line) with `Save draft` and `Finish workout`.
- **Universal Exercise Picker (`FreeformExercisePicker`):** Defaults to **`All`** (all non-running exercises from `ExerciseLibraryV2` + built-in Plyometrics & Isometrics, excluding Running) with optional capsule filter pills for **`Recent`**, **`Strength`**, **`Calisthenics`**, **`Plyometrics`**, **`Isometrics`**, and **`Conditioning`**. Rows in the picker, `FreeformEditor`, and `WorkoutTemplateEditor` expose `>= 48.dp` info triggers opening [[Anatomical-Muscle-Map]] `ExerciseInfoSheet` without disturbing active sets or drafts; `FreeformHistoryDetail` shows confirmed-only `SessionMuscleFocusSection` when mapped muscles exist. Custom named exercises can be added on the fly with `REPS | KG | SEC` inputs.
- **Flattened Tabular Set Grid:** Each exercise renders on `#000000` canvas with a discipline-colored accent bar, `SET | PREV | metrics | ✓` column header, `#101214` recessed input cells, `#121212` active-set focus, subtle `#D8FB70` tinted completed rows with filled checkmarks, swipe-to-dismiss / overflow actions, and expandable optional `Target` / `Rest` / `RPE` / `RIR` fields.
- **Auto-Prefilling `+ Add set`:** Appending a set automatically prefills actuals from the previous set, removing redundant duplicate buttons.
- **Completion & Immutability:** Review validates actuals (`FreeformWorkout`), infers a uniform discipline when applicable (otherwise storing mixed workouts cleanly without DB migration), promotes confirmed sets into one immutable completed record in [[Storage-and-FIT-Contracts]], and prevents conflict on retry.

## Related Nodes
- [[00-StrideLab-Index]] · [[Templates-and-Custom-Planner]] · [[Storage-and-FIT-Contracts]] · [[Progress-and-History]] · [[UI-Design-System]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
