# R0 — Engine 1.5 executed workload ledger

Date: 2026-11-16. Branch: `research/engine15-r0-workload-ledger-20261116`. Based on Q2 + Q3 + Train Your Way integrity PR #109. This is **diagnostics only**; no guardrail, coaching policy, schema, UI or historical workout changes.

## Run and artifacts

From `native-android` in the R0 checkout:

```powershell
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15R0WorkloadLedgerTest" --no-daemon
```

The test creates these files under `native-android/app/build/reports/engine15-r0/`:

- `executed-rule-events.csv` — ordered events per profile/week, original input, considered limit, actual output and delta. Candidate session caps have blank output/delta by design.
- `executed-weekly-ledger.csv` — reported baseline, requested target, executed Engine 1.0 baseline, Engine 1.5 ceiling, pre-composition allocation, final delivered/estimated running volume, gap to requested target, unused ceiling.
- `synthetic-scenario-summary.csv` — one row per profile, peak planned volume, unknown weeks and event counts.

Only synthetic profile IDs and numeric inputs appear in these reports. Never write real account IDs, GPS traces, private health notes or connected activity identifiers to this diagnostic output.

## Executed vs inferred

An **executed stage result** is an input or output recorded along the actual `Engine15.generateProgram` → `Engine15RunningProgram.compose` invocation. It is *not* an observed athlete response or proof that a safeguard improves safety.

The `ENGINE1_BASELINE_OUTPUT` event is measured at the Engine 1.0 / coordinated scheduling boundary. The individual internal Engine 1.0 factors are **not yet separately instrumented**; attributing the boundary gap to a specific Engine 1.0 factor without an additional trace is a *code-level hypothesis*. Never label it an executed cause.

`*_CANDIDATE` / `EFFECTIVE_SESSION_CAP` events display caps considered by the generator, with empty `output_km`. Only `SUM_SESSION_CAPS`, `WEIGHTED_ALLOCATOR`, `INITIAL_LONG_SHARE`, `DISTANCE_ROUNDING`, `FINAL_PRE_COMPOSE_ALLOCATION`, `LONG_SHARE_RECHECK`, `QUALITY_SHARE_DOWNGRADE`, `Q2_TIMED_SUPPORT_CONVERSION`, `Q2_POST_CONVERSION_CHECK`, and `FINAL_PRESCRIPTION` report executed outcomes. Deltas across separate stages are **not additive** unless the stage boundaries are truly sequential.

Mixed-unit distances are estimates calculated from the planning pace. `UNKNOWN_TIME_ONLY_FOUNDATION` must remain unknown, never be converted to 0 km of actual running. The target-minus-output gap is a comparison, **not automatically lost mileage**; the ceiling-minus-output gap shows unused capacity *within the existing policy*, not a distance to force into workouts.

## Privacy and invariants

- Tracing must be opt-in through `Engine15.diagnoseWorkload`; ordinary generation cannot start a trace, write a file or upload data.
- A diagnosed plan must be byte-for-byte equivalent as a data structure to ordinary production generation with identical request, owner, ID and timestamp. The focused test asserts full `PlanSnapshot` equality for each scenario.
- No change to weekly ceiling, 40% long-run rule, 20% quality work share, return/illness holds, Q2 mixed-step bounds, Q3 evidence qualification, plan approval or persistence.
- No guardrail changes are authorized by R0 alone.

## Readout protocol

1. Run the focused test. A successful Gradle result establishes that instrumented generation executed; an unrun test file does **not** establish findings.
2. Inspect the week 1–8 rows for `experienced-new-app-75-to-80`. Identify first applied reduction from `ENGINE1_BASELINE_OUTPUT`, `ENGINE15_WEEKLY_CEILING`, `SUM_SESSION_CAPS`, role reconciliation, and mixed-unit conversion.
3. Label causes as `EXECUTED`, `CONSIDERED_ONLY`, or `INFERRED_PENDING_INSTRUMENTATION`.
4. Compare to all other profiles before proposing changed M3/M4 logic.
5. Freeze Gate B criteria and log the proposed rule changes **before** any refactor runs. Do not retroactively rewrite pass criteria to fit generated results.

Execution status at authoring: **not executed in the user's local Gradle environment**. No measured totals are asserted by this document.
