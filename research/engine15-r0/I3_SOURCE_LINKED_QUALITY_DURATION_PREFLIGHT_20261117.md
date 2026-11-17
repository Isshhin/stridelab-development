# Engine 1.5 — I3 source-linked quality duration consistency, preflight

Frozen 2026-11-17 before implementation. Stacked on I2 PR #122, whose focused and selected full regressions passed in the user's Q3 checkout (BUILD SUCCESSFUL in 20s / 14s). **I3 stays shadow-only.**

## Existing facts and gap
- `IndependentQualitySourceBinding.inspect` currently compares a caller-asserted completed quality session with a uniquely linked saved session, FIT/Garmin activity summary, and selected recorded lap timing; it rejects missing laps, pauses, time mismatches, source and owner conflicts. The owner's source-namespace isolation is caller-supplied, and `qualityClassificationVerified`/`authorizedForTemplateSelection` remain false.
- `Engine15M5QualityPreviewComposer.compose` currently requires externally reviewed work seconds, ceiling and family but does not prove these correspond to source-linked, repeated recent recorded work. The M3 quality-exposure contract forbids silently treating recorded lap durations as threshold/tempo intensity.
- Therefore a new boundary may check **duration consistency** against two separate linked recordings, without claiming either recording's intensity or permission to progress.

## I3 contract
1. Input typed M4B Result, exactly one independently preflighted M5B request, explicitly asserted owner, as-of date and zone, PlanSnapshot set, source ActivityRecord set, and exactly TWO distinct linked quality-lap claims with their matching ActivityDetail. No read of private data beyond the caller-provided lists and no persistent writes.
2. Recompute M5B preview from M4B. A pending returning-runner/recovery/2-day/long-slot/unreviewed/missing-dose request must fail closed before inspecting source.
3. Each linked claim must match asserted owner and requested family, and pass `IndependentQualitySourceBinding.inspect` against the same plan and activity records. Two observations must use distinct source record IDs, planned session IDs, and performed local dates, each within the source binder's 28-day window. Do not count a duplicate activity or two sets of laps from one workout as repeated exposure.
4. Sort two observations by recency; compute a **conservative floor** of the smaller recorded work-lap duration sum. Fail if the M5B `fitnessInput.confirmedRecentWorkSeconds` exceeds this conservative bound; fail if the preview's proposed work seconds exceed the existing request's confirmed dose + explicit allowance, independent ceiling, or available time. Never automatically fill missing work seconds from the laps or raise any ceiling.
5. This is evidence *consistency*, NOT source identity attestation, training-intensity classification, athlete recovery proof, or quality dose approval. Source records and family labels are still caller-scoped/asserted. Even success must return `sourceOwnerAuthenticated=false`, `qualityIntensityVerified=false`, `doseAuthorized=false`, `distanceReconciled=false`, `executable=false`, `authorizedForProduction=false`.
6. Preserve the exact M5B preview and original independent M4B week budget. No generated Engine1/Engine15 session, no approval/persistence, no pace or distance inferred from seconds, no automatic adaptation.

## Frozen cases
- Two separate recent synthetic source-linked FIT/Garmin quality sessions, same asserted owner/family, selected work laps matching the source details, latest repeated work floor=480 s, request confirmed work=480 and reviewed ceiling 480: duration-consistency candidate only.
- Request claims 600 s despite only 480 s repeated source duration: explicit deferral; missing plan/source/detail, mismatched lap durations or pause overlap defers with source-binding diagnostic.
- Duplicate source ID, duplicate planned session ID, duplicate performed day, mixed owner, incompatible family and missing number of observations defer; no silent deduplication.
- Returning runner, recovery week, quality on long, unreviewed quality and infeasible time remain upstream deferrals.
- Successful result preserves original M5B candidate, each original source binding, and zero verified intensity, pace, source authentication, execution or production permissions.

## Gate
Focused I3 tests plus I1/I2 and M3/M4/M5/M6/Q2/Q3/R0 regressions in the user's Q3 validation checkout. Even passing I3 does not qualify threshold pace, FTP/HR threshold, zone targets, exercise-dose increase, a runnable plan, or production Engine 1.5.
