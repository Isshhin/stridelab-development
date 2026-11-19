# M4 Stage B — independent volume candidate (shadow-only)

Date: 2026-11-17. Branch: feature/engine15-m4b-independent-volume-candidate-20261117; stacked on M4 Stage A draft PR #112.

## What has actually been implemented

- `Engine15IndependentVolumePlanner.propose(Engine15AthleteCapacity, Input)` operates without any Engine 1.0/1.5 plan generation, schedules or old baseline sessions. It returns **week-level budgets** only; no sessions/workout dosage or approvals.
- Uses the **unchanged M4 Stage A Engine 1.5 ceiling** as an absolute numerical upper bound. The candidate independently intersects that with provisional session exposure (reported longest + 1 km for the one longest run, at most 80% of that for other planned days), return-from-break bounds and a single recovery-week cap based on the **previous planned budget**, not a post-Q2 timed-support estimated distance.
- Carries M3 evidence provenance. Unknown mileage, peak-only without confirmed weekly mean, conflicting reports, legacy-unclassified baseline, explicitly confirmed zero and explicit recovery hold have distinct outcomes. Unverified users with a **reported usual average** may receive a provisional budget, with a first-two-completed-weeks reassessment marker.
- Reports estimated target shortfall rather than claiming attainment. Capacity is only an **optimistic upper bound**; M5 must verify actual roles, spacing, time and exposure, and may require a smaller plan.
- All calculations are deterministic and rounded downward to 0.1 km; no new increased weekly ceiling, quality-work dose, late catch-up, longer cooldown or injury override.

## Grounded example (target 80 km, reported usual 75 km, six days)

Predeclared independent candidate weekly budget: **67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9 km**. Peak 78.7 km means 1.3 km short of the 80 km target even **before** M5/Q2 final workout composition. Current R0 pre-Q2 allocations: 67.5, 71.2, 75.0, 54.1, 71.2, 75.0, 78.7, 56.4 km. The difference in recovery weeks is explained by not feeding a Q2 *estimated* time-to-distance conversion backward into the next week's volume budget. It is **not** evidence of improved athlete safety.

The old R0 mixed-unit estimated peak of 70.533 km cannot be compared directly to this M4B 78.7 km *pre-composition* peak as though both are final prescribed distances. This candidate **does not fix** the final Q2 estimate shortfall.

## Focused tests and reports

From `native-android` on a checkout that contains this PR:

```powershell
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon
```

Expected synthetic reports (only created after test execution) in `native-android/app/build/reports/engine15-m4b/`:

- `shadow-weekly-comparison.csv`: 8-week pre-Q2 R0 allocated volumes next to M4B pre-composition budgets, with signed difference.
- `synthetic-profile-comparison.csv`: R0 *mixed-unit final estimate* and M4B *pre-composition budget* side by side across seven test personas, explicitly labeled **NOT_DIRECTLY_COMPARABLE**.

Other tests cover 50→80 in four weeks, 30→35, 0→20 time-only, returning 40→50, three-day 75→80, unknown, peak-only, legacy scalar, health hold, contradicted peak, confidence-no-numeric-override, missing longest, and 48 combinations of mileage/days/returning for ceiling and deload invariants.

**Validation at commit:** Source committed; no focused local Gradle run yet. Do not label calculations `EXECUTED` until the tests run. Math is preregistered in `M4_STAGE_B_GATE_B_PREFLIGHT_20261117.md`.

## Architectural migration and retirement plan

- **Now:** The live app still runs the existing hybrid Engine 1.0 → 1.5 scheduling/workout generator. Stage B is a separate unused, deliberately shadow-only independent module.
- **After Gate B candidate review:** M5 must generate long/easy/quality workouts from the independent budget, faithfully preserve Q2 timed support and Q3 evidence rules, and reconcile final mixed-unit estimated distances before approval.
- **After M6 scheduling/periodization:** Direct new running-plan generation through the independent M3→M4B→M5→M6 pipeline, with *no silent Engine 1.0 fallback*. Fail with an explicit review reason when independent generation is infeasible.
- **After M9 acceptance:** Retire Engine 1.0 runtime routing for new plans. Maintain decoding of historical saved plans, existing backups and completed activity data.

Do not remove Engine 1.0 early: Stage B deliberately has **no session composer or scheduler**, so such a change would make running-plan generation unavailable rather than safely independent.
