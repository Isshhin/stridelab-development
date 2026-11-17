# Activity Intelligence 2.0 — execution notes

## Starting checkpoint and scope

- Activity branch `feature/activity-analysis-2-20261117`, starting HEAD `f56ae10247dac785f0054eb2bfafc35d027c8364` (matches `origin` at task start).
- Engine 1.5 branch `feature/engine15-independent-generation-20261117`, starting remote HEAD `ed625d8b2496e82d6bab822b2214fdbc883473fd`; draft PR #131 is open. Cross-branch work belongs on a separate integration branch/worktree.
- Activity Detail remains the owner-approved draggable map sheet and one continuous scroll with four sticky checkpoints. `TracksideScrollActivityContent.kt` calls `animateScrollToItem` when a checkpoint is tapped and derives the active checkpoint from scroll state. No navigation/motion change is planned.

## Workout detector source audit

- Searched all locally available Git refs/history for `*.ts`, `*.tsx`, `*.js`, and `*.jsx` file history. The only historical JavaScript files found are the Engine 2 bridge/UI and associated `.cjs` files; no TypeScript activity/workout detector was found.
- Inspected the Engine 2 bridge and UI at commit `108806e8` and searched its repository code for workout classification. These files are Engine 2 plan integration, not an imported-activity detector.
- GitHub code search in `Isshhin/stridelab-development` for `workoutType`, `classifyWorkout`, and `fartlek` returned zero code matches.
- Current Kotlin contracts: `ActivityRecord` stores activity sport type and summary values; `ActivityDetail` stores ordered FIT/Garmin samples, unlabelled laps, pause intervals, and optional source HR-zone settings. Laps have no `WORK`/`RECOVERY` purpose or performed step identity. `RunningPrescription` has structured planned steps, but a plan link records association/intent and does not verify execution.
- Existing `HeartRateZoneAnalysis` uses athlete-configured Max HR, LTHR, or HRR settings and excludes sample intervals longer than 30 seconds. Its output is an approximate distribution, not a workout-family detector or effort score.
- Consequence: high average HR, varying pace, or an unlabelled lap pattern cannot safely distinguish easy, recovery, threshold, tempo, or intervals. Until an executed-step source is available, detection must fail closed to “Unknown”; a linked prescription may be shown only as intended workout, and a manual label must retain user-reported provenance.

## Implementation ledger

This section records only changes and checks verified during this task.

### Batch 1 — Workout intent, user labels, and source audit

- Added `ActivityWorkoutIntelligence`: detected execution remains `Unknown` because FIT/Garmin detail has unlabelled laps and no executed step identity. A unique linked completed supported plan is reported separately as intended workout; conflicting links are ambiguous. Heart-rate or pace summaries do not promote it to a detected quality family.
- Added owner-scoped, versioned local manual labels with `USER_REPORTED` provenance. Corrections live in separate preferences and never rewrite imported FIT/Garmin activity rows. Save/clear is offline and fails closed if no owner is available.
- Activity Analysis exposes compact detected/intent/user-label distinctions in the approved scroll section. No navigation/animation behavior changed.
- JVM tests: `ActivityWorkoutIntelligenceTest` passed as part of the full JVM suite.
- Isolated emulator `codex-engine15-clean-20261113` (`emulator-5554`, `ro.boot.qemu=1`): `ActivityIntelligenceUiTest` and `UserActivityWorkoutLabelStoreTest`, **6 tests passed**.

### Batch 2 — Relative effort and initial history evidence

- Added `ActivityEffortAnalysis`: a session score exists only when the athlete supplied post-run effort feedback on a unique linked completed run. Personal comparison requires at least three prior completed feedback records for the same source, running discipline, planned role/family and distance within 10%, within 90 days; known duplicate Garmin display records and ambiguous multiple feedback links are excluded. Comparison is a median of reported ratings, not Strava Relative Effort and not a recovery/readiness judgment.
- Approximate HR-zone distribution reuses the existing saved Max HR/LTHR/HRR calculator and validates activity identity. HR is presented separately and never converted to a scalar effort score. Zone settings are resolved in the activity's local Garmin or signed-in owner scope.
- Added version-1 `ActivityAthleteEvidenceProfile`, derived from owner-allowed cached activity rows. It reports recorded weekly distance, recorded run days, longest recorded run in the recent 28 days, and pace/average-HR summaries only for manually user-labelled easy/recovery runs. Known Garmin summary duplicates are consolidated for display; source coverage is explicitly `UNKNOWN`, and values are not represented as complete training totals or capacity.
- This profile is calculated from the existing offline activity repository; there is no duplicated profile database. No new telemetry or upload was added.
- Focused JVM tests for effort, evidence profile and classification passed; full `:app:testDebugUnitTest` passed. Debug and instrumentation APK assembly passed.
- Focused isolated emulator rerun after final UI update: **6 tests passed** (5 Activity Analysis UI and 1 offline owner-scoped label storage test).
- A broad `:app:connectedDebugAndroidTest` started 247 tests but two pre-existing Engine 1.0 UI flows timed out, then emitted no progress for several minutes. It was interrupted at 66/247; the focused Activity Analysis emulator suite passed independently. The broad suite is therefore incomplete, not green.

### Remaining stages / verified blockers

- **Automatic workout detection: partial/blocked by source contract.** No original TypeScript detector was found in local history or repository code search. Current Garmin/FIT laps lack step-purpose labels; therefore all automatic run-type/subtype detection remains `Unknown`. Manual user labels and planned intent are functional but are not detection.
- **Relative effort: partial.** Athlete-reported effort and HR zones are available; no physiology-derived effort scalar is implemented. Comparable-history estimates are limited to logged comparable plan feedback, not a broad activity-only historical effort series.
- **Performance baselines: partial.** Recorded mileage/run-day and longest-run observations, plus manually labelled easy/recovery pace and average HR summaries exist. Split consistency, pace-at-comparable-HR, HR-at-comparable-pace, HR drift, weather/elevation adjustment, and reliable quality exposure remain unimplemented because verified purpose, complete source coverage, environmental inputs or reliable step evidence are unavailable.
- **Engine 1.5 integration: pending.** The profile is currently read-only Activity Analysis evidence. It has not been merged or integrated into PR #131. The existing profile does not feed prescriptions. Integration must occur on a dedicated worktree based on the current Engine 1.5 branch, preserve both histories, and must not change quality deferral policies.
- **Visual animation QA:** source inspection confirms checkpoint taps call `animateScrollToItem` and active checkpoints follow the list state. No blind animation adjustment was made. Focused emulator tests exercised the Activity Analysis route; no dedicated motion-duration assertion or owner visual review was performed.
