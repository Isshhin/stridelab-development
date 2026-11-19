# M2 continuous Tempo structural dry run — 2026-11-18

## Policy identity and scope

- Selection policy: `stridelab.engine15.quality-family-selection-v3`.
- Dry-run construction policy: `stridelab.engine15.tempo-structural-dry-run-v1`.
- Development route only. The result is returned as an Engine 1.5 `Deferred` decision; it is not a `PlanSnapshot`, saved workout, approved prescription, or executable session.
- One Tempo Intro alternative is emitted for the first feasible loading-week easy slot. It does not repeat the template as a multiweek progression.

## What existing evidence supports

`IndependentQualityTemplates.kt` contains a versioned continuous Tempo Intro structure: 10 minutes easy warm-up, 12 minutes labelled TEMPO, and 8 minutes easy cooldown. `IndependentWorkoutComposition` preserves those three ordered steps and can represent interval groups without flattening repetition identity. This source supports only that the example is structurally representable and totals 30 minutes; every template is marked `HEURISTIC`.

The research packet and M9b records explicitly state that source activity measurements do not prove family intensity or athlete tolerance, the timed M2 preview does not reconcile to M4/M6 kilometres, and caller-supplied review references are not authenticated approvals. No independent coaching review or real-athlete validation for this family matrix/template was found in the current branch. The earlier review packet is a prepared packet, not a completed external review.

## Current v3 family matrix review

The v3 mapping is not supported as a coaching rule. In particular:

- selecting Threshold for declared experience level 2 on 5K/10K goals has no reviewed evidence basis in this branch;
- selecting Tempo for every aerobic-foundation, half-marathon, and marathon goal is an unsupported universal mapping;
- experience level is self-declared and does not establish threshold ability or quality tolerance.

Those decisions remain `PROVISIONAL_ENGINEERING_RULE`. This batch does not promote or broaden that policy. The dry run is created only for an automatic provisional Tempo candidate; a Threshold candidate or explicit user choice does not get silently changed to Tempo.

## Dry-run applicability and holds

The builder accepts only a provisional goal/experience matrix result for Tempo, in a debug build, and only when the family selector has attached a feasible M6-generated easy-run slot. Existing selector constraints remain binding: one requested quality session, a consistent positive baseline, at least three running days, no return-to-running or recovery hold, an available unlocked slot in each loading week, and no detected hard-session conflict. Recovery weeks do not receive a candidate slot. This is an engineering screen, not evidence that a runner is ready for Tempo.

## Construction and accounting contract

The dry run copies the existing `engine15-tempo-intro-v1` `IndependentWorkoutComposition` without changing step order or identity:

1. WARM_UP — 600 seconds, EASY.
2. WORK — 720 seconds, TEMPO label.
3. COOL_DOWN — 480 seconds, EASY.

The exact represented duration is 1,800 seconds. There are no recoveries in this continuous template. The TEMPO label and 720-second work duration remain unqualified; no pace, heart-rate, or effort target is inferred.

Every independent M4/M6 base-plan week is checked against the sum of its scheduled running-session distances. That base weekly ledger is retained unchanged. The Tempo template is time-based and contains no distance equivalence, so `qualitySessionDistanceKm` and the weekly total after replacing the EASY slot remain null. The alternative is not inserted into the calendar or added to weekly mileage. Accordingly `qualityDistanceReconciled=false`, `completePlanDistanceAccounting=false`, and no complete plan containing this workout is returned.

## Required review before executable prescription

An independent qualified coach must review and approve the family-selection basis and Tempo purpose, applicable athlete profiles, return/recovery contraindications, work duration, easy warm-up/cooldown, intensity definition, weekly frequency and spacing. Separately, Engine 1.5 still needs trustworthy athlete/source evidence, an authorized dose ceiling, an explicit supported time-to-distance or distance-based workout contract, complete session/weekly mileage reconciliation, progression/recovery-week rules, and whole-plan validation. A unit test or arithmetic match cannot replace that review.

## Source records

- [`IndependentQualityTemplates.kt`](../../../native-android/app/src/main/java/io/stridelab/privatealpha/IndependentQualityTemplates.kt) — versioned M2 structures and explicit `HEURISTIC` labels.
- [`Engine15M5QualityPreviewComposer.kt`](../../../native-android/app/src/main/java/io/stridelab/privatealpha/Engine15M5QualityPreviewComposer.kt) — explicit review, exposure, time, and non-executable gates.
- [`Engine15M5TimedDistanceReconciler.kt`](../../../native-android/app/src/main/java/io/stridelab/privatealpha/Engine15M5TimedDistanceReconciler.kt) — per-segment distance claims and arithmetic-only reconciliation; source distances remain unverified.
- [`Engine15M6IndependentQualitySpacing.kt`](../../../native-android/app/src/main/java/io/stridelab/privatealpha/Engine15M6IndependentQualitySpacing.kt) — easy-slot, recovery, and spacing review; opportunities remain unscheduled.
- [`ASTRA_REVIEW_PACKET_ENGINE15_20261117.md`](../ASTRA_REVIEW_PACKET_ENGINE15_20261117.md) — previous documented family authorization boundary; it records a prepared review packet, not a completed independent review.
- [`m9b-reviewed-quality-dose-progression-20261115.md`](m9b-reviewed-quality-dose-progression-20261115.md) — dose comparisons and caller-asserted review evidence; no source authentication.
