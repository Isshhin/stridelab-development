---
title: "Freeform Workout Logger"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformScreens.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformEditor.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformWorkout.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformAccess.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ManualDisciplineStore.kt"
tags: [stridelab, feature, freeform, logger, strength, multi-discipline]
---

# Freeform Workout Logger

The Freeform Logger enables offline, ad-hoc workout recording across **six independent disciplines** (`Running`, `Strength`, `Plyometrics`, `Isometrics`, `Calisthenics`, `Conditioning`) without touching [[Engine-1.5]] coached plans.

## Entry & Lazy Draft Lifecycle (`FreeformScreens.kt`, `FreeformAccess.kt`)
- **Modal Discipline Picker:** From the **Workouts** hub (`Start empty workout`), tapping launches a `#121212` `ModalBottomSheet` 2×3 discipline grid so **Workout templates** stays visible above the fold.
- **No Ghost Drafts:** Selecting a discipline navigates to `freeform/new/{discipline}` in memory (`persisted = false`). Navigating Back with zero edits discards cleanly without writing a draft row.
- **Concurrency-Safe Persistence (`FreeformEditor.kt`):** Auto-save (`350 ms` debounce) and manual save/finish serialize behind a per-workout `Mutex` (`persistMutex`) so delayed auto-saves never race with `access.start(...)` or `access.finish(...)`.
- **One-Tap Draft Discard:** Active `In progress` rows in the Workouts hub expose a `48.dp` trailing discard icon (`freeform-discard-row-{id}`), backed by owner-scoped storage (`account ?: localPlanOwner`).

## Hevy-Style Set Logging & Exercise Picker (`FreeformEditor.kt`)
- **True Empty Start:** Empty non-running sessions open to `+ Add exercise` plus quick-add chips (`freeform-quick-add-*`) drawn from recent same-discipline completed workouts — never auto-inserting placeholder movements.
- **Discipline-Aware Modal Bottom-Sheet Picker (`FreeformExercisePicker`):** Filter chips swap cleanly across `<Discipline>`, `Recent`, and `All`. `FreeformExerciseCatalog` ships built-in **Plyometrics** (`Pogo hops`, bounds, depth jumps) and **Isometrics** (`Wall sit`, holds, planks) entries alongside the exercise library JSON (`ExerciseLibraryV2`). Custom named exercises can be added on the fly.
- **Flattened Tabular Set Grid:** Each exercise renders on `#000000` canvas separated by `StrideLabHairline` with a compact `SET | PREV | metrics | ✓` column header, `#121212` active-set focus, dimmed completed rows with `#D8FB70` checkmarks, swipe-to-dismiss / overflow actions, and expandable optional `Target` / `Rest` / `RPE` / `RIR` fields.
- **Auto-Prefilling `+ Add set`:** Appending a set automatically prefills actuals from the previous set, removing redundant duplicate buttons.
- **Non-Blocking Rest Timer (`FreeformRestTimerBar`):** Confirming a set starts a countdown anchored above the bottom footer without blocking edits to subsequent sets or exercises.
- **Running Freeform Mode:** Captures date, distance, duration, optional effort (`1..10`), workout type, and notes.
- **Completion & Immutability:** Review validates actuals (`FreeformWorkout`), promotes confirmed sets into one immutable completed record in [[Storage-and-FIT-Contracts]], and prevents conflict on retry.

## Related Nodes
- [[00-StrideLab-Index]] · [[Templates-and-Custom-Planner]] · [[Storage-and-FIT-Contracts]] · [[Progress-and-History]] · [[UI-Design-System]] · [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]
