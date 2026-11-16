# Engine 1.5 Q1 — executable quality-generation baseline

Status: **Harness committed; generator execution not yet verified.** This branch changes only test code and this readme; it does not modify the Engine 1.5 production algorithm, existing approved plans, or PR #102.

Source branch: `feature/approved-training-ux-redesign-20261116`, head `ead138919e39c2e8ee0c7bd24549179a2b0e77f5`.

## Run on a Windows development checkout

From `native-android`:

```powershell
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15Q1QualityBaselineTest" --no-daemon --rerun-tasks
```

The test writes three files (relative to the Gradle app project working directory):

- `app/build/reports/engine15-q1/q1-profiles-and-weeks.json`: request fixtures, provenance notes, eight-week running and quality outputs, exact segment decomposition and explanatory notes.
- `app/build/reports/engine15-q1/q1-weekly-quality.csv`: weekly totals, counts, prescribed hard-work meters, warm-up / recoveries / cool-down meters, quality-work share, and planning-pace-derived **proxy minutes**.
- `app/build/reports/engine15-q1/q1-all-running-steps.csv`: every generated running segment, including easy and long runs.

If Gradle places relative test output under a different working directory, search the checkout for `q1-profiles-and-weeks.json`.

## Exact interpretation limitations

1. This is **synthetic profile testing**, not coaching validation on real athletes.
2. Engine 1.5 v1 persists each running segment in **meters**. `actual_work_minutes_NOT_RECORDED` is deliberately empty. A number calculated from `planningPace` is an administrative **proxy**, not observed threshold-work time.
3. Synthetic 30-/60-minute threshold-history fixtures use 6/12 km WORK distances and an explicitly assumed **5:00/km work pace** only to demonstrate admission and differentiation in the current distance-based evidence pipeline. The engine **does not verify actual 30- or 60-minute work capacity**.
4. The `minutes-only` threshold-history fixture intentionally omits `qualityWorkKm` even when total workout minutes are present, testing whether the existing evidence pipeline can admit duration-only evidence.
5. `Engine15.generateWithHistory` is the actual production entry point being audited. The test does **not** rewrite the algorithm or patch its constraints.
6. The reports record actual generated workout decisions *if the test executes*. They do **not** claim to instrument every internal bound. Precise causal attribution to 20% work budget, session caps, first-week freeze, or repetition-fitting must be derived from added instrumentation or traced source lines, and marked as inferred until validated.
7. Rejected fixtures are captured as `rejected_by_policy`; unexpected runtime exceptions fail the test.

## Acceptance for Q1 completion

- JVM test compilation and execution pass and the report files exist.
- Every case is individually identified with a fixed request, fixed dates, and synthetic evidence.
- Eight weeks are recorded per generated profile, including each fourth-week deload.
- Summaries distinguish session minutes from **unavailable actual work minutes**.
- Generated output is reviewed for unexpectedly large cool-downs, quality deferrals, first-work-envelope freeze, and differences with/without admitted history.
- No production code, migrations, approved workouts, or phone installation changed.

Only after executed baseline outputs are reviewed should work proceed to Q2 mixed-unit contracts and Q3 quality-dose policy.
