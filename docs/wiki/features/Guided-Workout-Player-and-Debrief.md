---
title: "Guided Workout Player & Coach's Debrief"
area: feature
status: verified
last_verified_commit: "36a5eeb8"
key_files:
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutScreen.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayer.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutPlayerEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/WorkoutExecutionCheckpoint.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/Engine15DebriefEngine.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/DebriefCopyTemplates.kt"
  - "native-android/app/src/main/java/io/stridelab/privatealpha/TrainingEffectCalculator.kt"
tags: [stridelab, feature, workout-player, debrief, training-effect, checkpoints]
---

# Guided Workout Player & Coach's Debrief

Guided plan execution pairs a durable AMOLED **Workout Player** for non-running disciplines with read-only **Coach's Debrief** and **Training Effect** analyzers grounded strictly in confirmed work and verified telemetry.

## Durable Guided Workout Execution (`WorkoutPlayer.kt`, `WorkoutPlayerEngine.kt`, `WorkoutExecutionCheckpoint.kt`)
- **Pure Reducer & Local Checkpoints:** `WorkoutPlayerEngine` reduces typed work/rest/skip/undo events (including table-driven `FocusSet`, `CompleteSetAt`, `SaveUnconfirmedPartialHold`, `SkipSetAt`, `UnconfirmSetAt`, `AddExtraSet`, and `StartNextSet`) against immutable prescriptions. Owner-scoped `player-exec-*` JSON checkpoints persist in `TrainingStore` (`kind = "workout"`, local-only). Confirming a set and entering rest commit together; per-second timer ticks stay in memory.
- **Set-Table Default & Scoped Hold-Timer Handoff (`CoachedSetTable.kt`, `DurableWorkoutPlayer.kt`):** Coached Strength and Calisthenics workouts default in `WorkoutScreen.kt` to the Hevy-style flattened AMOLED set grid (`CoachedSetTable`) backed directly by `WorkoutExecution`, while timer-heavy tracks keep `WorkoutPlayer` first. Timed holds expose a `>= 48.dp` **Timer** button opening `WorkoutPlayer` in `scopedToTable` mode with three controls (**Back to Table**, **Play / Pause** + `Log Xs`, **Start Next Set**); pausing or backing out after `>= 3 s` auto-saves an unconfirmed `PENDING` hold excluded from `WorkoutLog` / `Engine15DebriefEngine` (taps `< 3 s` are ignored).
- **Discipline Controls & Timers:** Supports strength reps/load, plyometric ground contacts, isometric holds, and conditioning intervals including **EMOM** (advances clock at interval boundaries without auto-confirming sets) and **AMRAP** (prompts confirmation of completed rounds/reps). Running timers anchor to elapsed realtime on the current boot and wall-clock time across reboots.
- **Independent Receipts (`Partial · X of Y sets`):** Finalization freezes a compatibility log before updating the plan and writes a local outcome receipt separating session lifecycle (`Finish Early` vs full finish) from set coverage (`confirmed` vs `skipped`). Skipped sets stay out of performed logs; process death mid-save recovers from the frozen log match.

## Coach's Debrief & Training Effect (`Engine15DebriefEngine.kt`, `DebriefCopyTemplates.kt`, `TrainingEffectCalculator.kt`)
- **6 Read-Only Analyzers:** Dispatches discipline-specific analyzers (`Running`, `Strength`, `Plyometrics`, `Isometrics`, `Calisthenics`, `Conditioning`). Cross-training debriefs inspect confirmed workout sets/reps/holds/contacts and explicit `RIR: n` notes; stationary Activity Detail renders a debrief only via an exact workout-log link.
- **Running Narrative & Highlights:** Evaluates pause-excluded moving-time streams (`> 30 s` gaps excluded) to produce one concise lead plus up to two ranked highlights (`CoachDebriefCard.kt`). Half-run pace comparisons require `>= 20 min`; pulse/dynamics drift comparisons require `>= 45 min` at comparable pace. Sparse runs omit the card cleanly with zero filler placeholders.
- **Verified-Zone Training Effect (`TrainingEffectCalculator.kt`):** Produces bounded `0.0..5.0` Aerobic (5-zone weights `1/2/3/5/7`, `>= 75%` moving coverage, verified Garmin/FIT zones only) and Anaerobic (Z4–Z5 or pace surges bracketed by lower intensity) transient scores.

## Related Nodes
- [[00-StrideLab-Index]] · [[Engine-1.5]] · [[Storage-and-FIT-Contracts]] · [[Garmin-and-Activity-Import]] · [[Hard-Guardrails]] · [[Screens-Map]]
