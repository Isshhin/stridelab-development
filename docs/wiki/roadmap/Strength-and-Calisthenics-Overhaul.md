---
title: "Strength & Calisthenics Overhaul"
area: roadmap
status: verified
last_verified_commit: "5defc09e"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/StrengthCalisthenicsProgression.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/CoachedSetTable.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15StrengthWorkoutComposer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15CalisthenicsWorkoutComposer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseAlternatives.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayerEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/DurableWorkoutPlayer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/ExerciseLibraryV2.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/FreeformEditor.kt"
tags: [stridelab, roadmap, strength, calisthenics, progression, workout-player]
---

# Strength & Calisthenics Overhaul (`status: verified`)

> [!NOTE]
> **Verified Implementation (`status: verified`):** Shipped and verified across Phases 1–3. Generated plan blocks remain static across [[Engine-1.5]] while Strength & Calisthenics progression targets and opt-in level-ups project read-only overlays (`StrengthCalisthenicsProgression.kt`) over `WorkoutExecution` (`CoachedSetTable.kt`) and confirmed execution history. Follow-ups parked separately: [[Main-and-Supporting-Plans]] and the visual Anatomical Muscle Map.

## Scope & Core Architecture

### 1. Static Block Generation + Read-Only Progression Projections
- Preserve `PLAN_SCHEMA = 1`, immutable `PlannedSession.difficultyBaseline`, deterministic `validateSaved(plan)`, and isolated `ManualDisciplineStore` writes ([[Hard-Guardrails]], [[ADR-01-Engine15-Isolation-from-Freeform-and-Custom-Logging]]).
- Mirror `Engine15SessionPresentation` so Strength and Calisthenics targets are projected read-only at display/execution time without silently mutating saved plans.
- Key progression history strictly by canonical **exercise ID** (`exerciseId` / `catalogId`), preventing ad-hoc exercise swaps from polluting another movement's history.

### 2. Per-Exercise Metric Types & Strength / Calisthenics Projectors
- Decouple measurement type from discipline (`REPS`, `WEIGHT_AND_REPS`, `HOLD_SECONDS`, `WEIGHT_AND_HOLD`) so core holds (planks, hollow holds, L-sits) work seamlessly inside Strength and Calisthenics sessions.
- **Strength Projector:**
  - **Session 1 (no history):** Never fabricate loads; show effort-anchored guidance (`Sets × Rep Range @ Target RIR`).
  - **Session 2+:** Run deterministic double progression (`CLIMB_REPS`, `BUMP_LOAD`, `HOLD_TARGET`) alongside bounded Epley e1RM dual working options (`3–10` hard working reps only; `> 10` endurance sets excluded; optional `RIR` factored cleanly; snapped to `2.5 kg` / `5 lb` increments).
- **Calisthenics & Timed-Hold Ceiling Upgrades:** When all confirmed working sets meet the movement ceiling cleanly and `ExerciseAlternatives.harder(...)` offers a next variation, surface a quiet opt-in level-up chip applied strictly on athlete tap.

### 3. Set-Table Default & On-Demand Scoped `WorkoutPlayer` Handoff
- Default coached **Strength** and **Calisthenics** sessions in `WorkoutScreen.kt` to the flattened AMOLED Hevy-style tabular set grid (`SET | PREV | metrics | ✓`) backed directly by `WorkoutPlayerEngine` and `player-exec-*` checkpoints in [[Storage-and-FIT-Contracts]].
- Preserve `WorkoutPlayer` defaults for timer-heavy tracks (`Conditioning`, `Isometrics`, `Plyometrics`) while keeping bi-directional switching and `Partial · X of Y sets` receipts intact.
- On timed movements (`HOLD_SECONDS`, `WEIGHT_AND_HOLD`), surface a `>= 48.dp` **Timer** button opening `WorkoutPlayer` scoped directly to the exercise's next unfinished set with three controls (**Back to Table**, **Play / Pause**, **Start Next Set**):
  - Natural completion or **Log Xs** confirms the set and enters rest.
  - Pausing partway (`elapsed >= 3 s`) and tapping **Back to Table** auto-saves elapsed seconds into the checkpoint as an **unconfirmed** partial draft (`SetDisposition.PENDING`, excluded from `WorkoutLog` and [[Guided-Workout-Player-and-Debrief]]). `< 3 s` accidental taps are ignored.

### 4. Muscle Metadata Prep for Future Muscle Map
- Ensure standardized snake_case `primaryMuscles` and `secondaryMuscles` tags across `ExerciseLibraryV2` and `FreeformExerciseCatalog` (`FreeformEditor.kt`) so pickers display lightweight muscle tags now and the parked follow-up **Anatomical Muscle Map** pass (alongside [[Main-and-Supporting-Plans]]) can consume them directly.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Guided-Workout-Player-and-Debrief]] · [[Freeform-Logger]] · [[UI-Design-System]] · [[Hard-Guardrails]] · [[Main-and-Supporting-Plans]]
