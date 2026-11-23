# Engine 1.5 I5 — Timed Workout Distance Feasibility (Preflight)

Frozen 2026-11-17 before writing I5 implementation. Stacked on I4 PR #124, after the user reported I4 plus 17 regression classes `BUILD SUCCESSFUL in 26s`. Still SHADOW-only.

## Problem and scope
- Existing M5C checks **caller-asserted** segment distances add to the original EASY slot budget; equal sums do not establish that the time-based threshold/tempo alternative can fit those kilometres.
- Existing I3 checks two distinct source-linked lap work durations; I4 checks HR sample coverage only. Neither measures verified athlete-specific pace, calibrated threshold HR, running economy, or actual quality session distance.
- I5 performs a *conditional mathematical feasibility* check: if the caller explicitly supplies a nonempty review reference and **one numeric speed range for each original timed phase** (warm-up, work, between-repeat recovery, cooldown), can the M5B total time theoretically span the original M4B/M5A allocated kilometres? It must NOT fabricate pace from elapsed time, HR, historical averages or distance.

## Frozen gate rules
1. Recompute I4 from original typed M4B Result + single M5B request + I3 source-link evidence; defer with I4 diagnostic if I4 did not pass. This is a SIGNAL integrity precondition, not proof the recorded work was threshold/tempo.
2. Recompute M6B from same M4B Result, M5B request and independent M6A calendar. Require the original M5B preview exactly equals I4's M5B preview, and original selected EASY slot date/distance exactly matches. Do not set a scheduled/executable quality workout.
3. Expand the typed M5B workout elements to chronological timed phases (including *only between-repeat recovery*). All units must be SECONDS, times positive/finite, phase count 3..400, and warm-up / work / cooldown structure must match the original M5B contract. No duplicated final recovery or new workout steps.
4. Each caller-supplied speed window has explicit phase position, WorkoutPurpose, WorkoutTarget, finite min/max metres/second and 0 < min <= max <= 15.0. The 15 m/s limit is an extreme numeric sanity bound, **not** an athlete ability or safety threshold. Require one and only one matching window per phase, without silent defaults or reordering.
5. Require explicitly caller-supplied nonblank review reference (length <=256) for these ranges, but mark it *asserted, not authenticated*. Never derive ranges from FIT/Garmin HR, elapsed seconds, or M5C arithmetic claims.
6. Compute min/max possible metres as sum over all timed phases of seconds × min/max speed. Reject if original M5A allocated metres are below min - 0.01m or above max + 0.01m. Return descriptive interval and original M4B/M5B/I4/M6B references **unchanged**, not a set of prescribed paces or distances.
7. A mathematically feasible range **does not** prove that one continuous feasible per-segment pace sequence is demonstrated or safe for this runner. No intensity, source pace calibration, athlete response, quality dose, actual distance, schedule, or production approval may become true. `boundsExternallyAsserted=true`, `paceCalibrationVerified=false`, `qualityIntensityVerified=false`, `actualSegmentDistancesVerified=false`, `doseAuthorized=false`, `executable=false`, `authorizedForProduction=false`.
8. No Engine1/Engine15 live generate, PlanSnapshot mutation, Android UI, persistence, fallback or auto-adaptation.

## Prespecified cases
- A developing 30→35 km, four-day running plan with source-linked 480s work and clean HR signal and an explicitly asserted 2.4–4.2 m/s EASY and 2.8–5 m/s WORK envelope can span its planned EASY slot mileage, retaining original weekly budget.
- A 75→80 km six-day plan with short 480s quality preview cannot fit its original EASY slot within this same synthetic bounded-speed envelope; return `ALLOCATION_ABOVE_ASSERTED_MAXIMUM`. Do not automatically lengthen workouts or override weekly mileage.
- Narrow high-speed-only envelopes may exceed the slot's budget even at their minimum: `ALLOCATION_BELOW_ASSERTED_MINIMUM`.
- Missing review reference, missing/extra/misordered or mislabelled phase window, reversed speed range, NaN/Infinity, zero or extreme speed, unknown source/HR data, I4 failure, blocked long day, Saturday-before-Sunday quality and recovery-week request: explicit deferral.
- Deterministic, unchanged original typed workouts and all permission flags false.

## Validation
I5 focused tests plus I4/I3/IndependentQualitySourceBinding/I2/I1/M3/M4/M5/M6/Q2/Q3/R0 in Q3 checkout. Passing I5 establishes ONLY arithmetic possibility given caller-asserted speed bounds and technical source HR integrity. It does not justify executable, athlete-tailored quality prescriptions. Actual authenticated segment pace/distance source and adaptive coaching remain future gates.
