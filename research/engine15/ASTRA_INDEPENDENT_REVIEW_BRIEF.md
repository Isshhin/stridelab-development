# StrideLab Engine 1.5 — Astra independent adversarial research brief

**Snapshot date:** 2026-11-15 (Asia/Manila)  
**Purpose:** Independent research, criticism, redesign and reproducible experiment design—not a production release approval.  
**Reference code:** `feature/engine15-m5a-synthetic-scenario-gate-20261115` at commit `02db938a90433923cd49337d06f55308af5ceabb` (stacked through M3, M4 and M5a).  
**This handoff branch:** `research/engine15-astra-adversarial-baseline-20261115`; create a **different isolated experiment branch** for implementation.  
**Relevant draft PRs:** #70 (M3 source-lap reconciliation), #71 (M4 weekly volume), #72 (M4 feasibility), #73 (M5a easy/long composer), #74 (M5a quality/strides previews), #75 (M5a synthetic runner). Earlier M3 research branches are stacked beneath them.

## 1. Reviewer mandate

You are an **independent adversarial research reviewer**. Do not simply approve the existing approach. Challenge its premises, falsify its assumptions, propose alternatives, write edge-case simulations, and—if useful—rebuild an *experimental* algorithm on your own branch for measurable comparison. You have broad freedom over synthetic research code, fixture generation, metrics, algorithm selection, tests and documentation. This includes replacing the current 5% build/15% deload heuristics *within the experiment* when evidence or scenarios support doing so.

Do not treat the observed Gradle build result as proof of coaching quality, athlete safety, or trustworthy activity provenance. Distinguish structural/engineering correctness from training science and real-world coaching effectiveness.

**Boundary:** no changes to production coach defaults, no merge into main/dev/stable, no owner-device install, no touching private athlete activity data, no actual external athlete prescriptions, no changing cloud accounts, no production schema migration. Keep draft PRs isolated and clearly state if a requested experiment would cross the boundary. No original engine 1.0 rollback removal; no hidden changes to existing plans. Use synthetic data first.

## 2. Observed validation checkpoint (owner reported, not independently reproduced by reviewer)

- Latest local checkout: `feature/engine15-m5a-synthetic-scenario-gate-20261115` (validation branch based on it), at `02db938a90433923cd49337d06f55308af5ceabb`; **verify HEAD**, do not assume it.
- M3 4 focused suites: owner reported `BUILD SUCCESSFUL` after fetching current M3.
- M4 Batch 1 `IndependentWeeklyMileagePlannerTest`: `BUILD SUCCESSFUL in 9s`.
- M4 Batch 2 `IndependentMileageFeasibilityTest`: `BUILD SUCCESSFUL in 6s`.
- M5a Batch 1 `IndependentRunWeekComposerTest`: `BUILD SUCCESSFUL in 5s`.
- M5a Batch 2 `IndependentQualityOverlayComposerTest`: `BUILD SUCCESSFUL in 5s`.
- M5a Batch 3 `IndependentResearchScenarioRunnerTest`: `BUILD SUCCESSFUL in 4s`.
- **Full Android JVM tests and debug build:** owner reported `BUILD SUCCESSFUL in 18s; 44 actionable tasks: 5 executed, 39 up-to-date` for `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` from `E:\stridelab-validation\native-android`. No independent environment reproduction, device instrumentation, coverage, or multi-run statistics have been supplied here.

Reproduce with a clean checkout and record exact commit, JDK, Gradle, Android tooling and test output. Do not infer that every test task re-executed: most Gradle tasks were up to date in this owner's run.

## 3. Architectural map and current hard safety boundaries

- M2 typed workout contract: `IndependentWorkout.kt`, `IndependentWorkoutComposition.kt`, `IndependentIntervalGroup.kt`, `IndependentQualityTemplates.kt`, `IndependentQualityFitnessAdapter.kt`.
- M3 candidate evidence: `IndependentRunningHistory.kt` (four previous complete weeks), `IndependentCapacityProfiler.kt`, `IndependentQualityExposure.kt`, `IndependentQualitySourceBinding.kt`. Completeness attestations and performed work intensity are **not** source verified. The current reconciliation can match Garmin/FIT lap timings but not prove threshold/tempo WORK classification. `authorizedForTemplateSelection=false` is intentional.
- M4: `IndependentWeeklyMileagePlanner.kt` + `IndependentMileageFeasibility.kt`. Inputs explicitly called "approved" are **caller assertions**; they are not proof of safe load. Default values (5% incremental build, week-4 15% recovery, 28% preferred/35% max long-run share) are exploratory heuristics, *not* clinical guidance.
- M5a: `IndependentRunWeekComposer.kt` turns bounded day distances into EASY meter-targeted workout preview steps. `IndependentQualityOverlayComposer.kt` creates **separate** explicit-review-gated time-based threshold/tempo/strides alternatives and holds them during recovery weeks, on the long-run day, and other conflicts. `IndependentResearchScenarioRunner.kt` connects the research gates.
- Critical missing bridge: Time-based quality workouts have no supported distance-to-allocated-M4-kilometers reconciliation. Do **not** add their seconds to a weekly distance or replace a 10 km base run with a 34-minute workout while still reporting the original total. `distanceReconciled=false`, `executable=false`, `authorizedForProduction=false` remain correct until independently resolved.
- No production Android coach, saved-plan repository, workout player, Garmin importer, athlete profile or UI change is included in these research batches.

## 4. Required adversarial experiments and falsification matrix

Investigate, at minimum, and extend beyond this list:

1. **Mileage provenance and ambiguity:** unverified or partially imported weeks, duplicate Garmin/FIT activities with different IDs, missing distance, zero-mileage genuine complete week, conflicting timezone/week boundaries, canceled/partial workouts. Does the system distinguish unknown from genuinely zero rather than inventing capacity?
2. **Training science:** regressions after missed runs, multiweek illness/return-to-run, unusual high-volume runners, abrupt target changes, short training horizon, deload after consecutive hard weeks, whether a fixed 5% rule creates unjustified ramps or needless stagnation. Critique heuristic assumptions rather than only validating numerical limits.
3. **Long-run scheduling:** 2 vs 3 vs 6 vs 7 run days, no feasible Saturday, time-unavailable Thursdays, day caps less than naive distribution, rounding to 0.01 km, long run not actually longest, too-rapid long-run build despite weekly cap, deload then re-build without catch-up.
4. **Quality exposure and authorizations:** mismatched intervals, Garmin auto-laps vs manual laps, paused activity time, repeated sources, unreliable workout classification, manual self-report, fake caller approval reference, retroactive editing. Explicitly test whether the current API can be tricked by `explicitlyReviewedDose=true` and an arbitrary nonempty reference. Do not overstate authenticity.
5. **Quality-to-distance reconciliation:** propose at least two candidate designs that *don't* fabricate pace or distance (for example, entire-session distance budget accounting with verified completed data, or an explicitly unresolved session requiring an approved athlete-specific pacing envelope). Include failure modes of each and do not promote unknown to known.
6. **Intensity/dose scheduling:** poor spacing of quality and long workouts, lack of intensity ceilings, concurrent strength/conditioning, no-exposure case, too many consecutive days, recovery after injury, availability changes. Existing schedule checks are primarily **distance** checks and are not a comprehensive coaching policy.
7. **Numerical/serialization boundaries:** nonfinite and extreme inputs, overflow risks, precision, zero distance allocated on a scheduled day, accidental mixed meters/seconds sums, malformed synthetic object construction, repeated and duplicate requests, determinism and nonmutation.
8. **Regression/compatibility:** tests on existing Engine 1.0 rollback, Engine 1.5 defaults, plan persistence, signed-out operation, offline reopen, and backup/restore *only in a sandbox with authorized synthetic data*. No changes to these for review purposes.
9. **Gaming the research gate:** if a caller constructs a syntactically `Approved` object without evidence, does a synthetic preview look more legitimate than warranted? Identify missing trust boundaries, provenance custody and review artifacts.
10. **Release gap:** enumerate missing Android instrumentation, owner-device QA, performance budget and integration contracts required before any experimental preview could be shown to users.

## 5. Comparison method

- Fix reproducible synthetic seed(s) and version all policy settings, inputs and deterministic results.
- Run the current baseline and experimental algorithm on **identical** scenario fixtures; record per-case rule IDs, weekly/day distances, long-run peaks and increments, deload behavior, requested target attainment, defer rates, number of quality previews, overcount/unknown distance cases and policy violations.
- Mark hard invariant violations separately from *subjective or evidence-limited* coaching-quality judgments; do not invent a "safe runner" label.
- Give counterexamples where experimental design is worse, not just successes; compare complexity, explainability, failure modes and integration risk.
- Investigate fitness-adaptation evidence where legitimate and cite original or qualified training science sources with dates; clearly separate evidence-supported conclusions from novel assumptions.
- Treat uncertainty as a first-class result: show exactly what unknown inputs would change the recommendation. A simulation does not validate athletic outcomes.

## 6. Acceptance/stop criteria

A candidate must:
- keep all source/owner identities and uncertain provenance explicit;
- refuse to convert M3 provisional observations into auto-approved loads or quality exposure;
- never silently overshoot an explicit approved absolute or per-day ceiling;
- reconcile every base distance day/week and never double count timed overlays;
- avoid creating workouts for unscheduled days or contradictory long-run assignments;
- prove no saved plan mutation until owner review/approval and rollback readiness;
- retain separate, visible labels for `test passed`, `algorithmically plausible`, `source verified`, and `athlete validated`.
Fail closed and document the counterexample if any criterion is unmet. Passing this research review is **not** production authorization.

## 7. Reviewer deliverables

1. **Independent written verdict**: scope, timestamp, baseline head and confidence; critical/high/medium issues, proof/examples, classification (code bug vs algorithm-design flaw vs missing provenance/athlete evidence).
2. **Reproducible synthetic comparison suite** and concise numerical outcomes; include stress cases, degenerate cases, adversarial authorization attempts and regression fixtures.
3. **Experimental architecture and implementation(s)** in your own branch; variant identifiers and assumptions; no edits to stable production path.
4. **Explicit time→distance / quality provenance design decision**: proposed protocol, failure conditions, unknown classification semantics and edge-case tests.
5. **Ranked next work**: unblockers for independent coach functionality vs later safe owner preview; avoid silently merging.
6. **Review ready draft PR** with links to code, tests and report. Provide exact commands, Gradle output, pass/fail, and known gaps. If you cannot execute tools, say so and provide code-level evidence only.

## 8. Handoff command and restrictions

After branch checkout, inspect the actual code; this brief is not a substitute for reading the implementation:

```powershell
cd E:\stridelab-validation
git fetch origin
git switch -c research/astra-review-working origin/research/engine15-astra-adversarial-baseline-20261115
cd native-android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Avoid assuming this local branch name is free. In automation or other machines choose a distinct branch name; preserve any preexisting uncommitted changes. Do not install to the owner's physical device.

**Gate sequence:** independent synthetic critique and experimentation → owner review of findings → revisions and repeated local tests → controlled Android *experimental read-only preview* and instrumentation → owner-controlled private testing → separate decision on production eligibility. No merge or auto-enable by default.
