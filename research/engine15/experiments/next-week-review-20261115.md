# Independent coaching assessment and next-week experiment

2026-11-15, Asia/Manila. Baseline: `de7614d68c27d714d79a6d2db42ab4d017d13075`, PR #76.
Branch: `research/evidence-responsive-coaching-20261115`. Synthetic research only; not a production coach, athlete-safety validation, or release approval.

## Verdict and source assessment

The main limitation is the decision process, not the exact build percentage. The research pipeline has useful typed distance/time and provenance boundaries but no evidence-driven feedback loop connecting successive weeks. Production Engine15 is a different path and must not be described as using M3–M5a.

1. **High — open-loop volume planning.** `IndependentWeeklyMileagePlanner.generate` uses starting load, ceiling, target and week index. It does not observe completion, interruptions or tolerance. Its recovery week is periodic; the following build returns to a pre-recovery anchor. Identical approved inputs yield identical plans despite divergent recent histories. Production `Engine15.runningCeiling` also depends on manual recent mileage and week index. `Engine15Feedback` does provide conservative next-session suggestions from explicit feedback; it is not a weekly adaptive generator.
2. **High — allocate then reject.** M4 fixes a share/even distribution before `IndependentMileageFeasibility` checks per-day ceilings. A feasible alternative can exist: 60 km across six days with a 5 km Monday ceiling fails the original allocation but fits other supplied limits. The new allocator demonstrates this without silently reducing the target.
3. **High — asserted evidence is not verified evidence.** M3 distinguishes missing weeks from zero and rejects conflicting same-ID records. It cannot resolve duplicates with distinct provider IDs or authenticate completeness. M4's approved values and the experiment's TOLERATED response are caller assertions, never trust credentials. In M5a, a boolean and nonblank review reference satisfy a syntactic preview gate; the preview correctly remains nonexecutable. No new authorization route was added.
4. **High — quality distance remains unresolved.** M5a's timed overlays are alternatives, not additions to base kilometers. `distanceReconciled=false` is correct. Production Engine15's descriptive quality steps and stored distance should not be mistaken for source-verified threshold intensity or measured workout distance.
5. **High — incomplete training intent.** Neither this next-week experiment nor M4 models race deadlines, phases, illness, strength fatigue or a recovery decision grounded in feedback. Production includes some goal/return/taper rules; these are not an individualized longitudinal model. This experiment preserves mileage-target semantics and does not claim to solve race preparation.
6. **Product discrepancy — signed-out builder.** `PlanBuilder` immediately returns a sign-in screen when `state.account==null`; this conflicts with the requested core signed-out functionality. The navigation route calls that builder directly. Runtime/account-state reachability remains unverified. Correcting ownership and persistence requires a separate functional change, not removing a UI guard blindly.

## Research basis and alternatives

- A prospective cohort of 5,205 runners associated single-session distance spikes with injury incidence. It motivates inspecting individual sessions as well as weekly totals; it does not establish a universal safe cap or causal prevention policy. We deliberately did **not** turn its 10% observation into a new safety rule. [Original study, 2025](https://bjsm.bmj.com/content/early/2025/07/07/bjsports-2024-109380).
- Training-intensity distribution and periodization need evaluation beyond weekly kilometers. The runner-specific literature review is a research direction, not permission to infer thresholds from volume. [Systematic review, 2022](https://pubmed.ncbi.nlm.nih.gov/34749417/).
- Concurrent strength/endurance evidence does not justify treating every strength session as harmful interference. A randomized trial reported improvements in running outcomes; session dose and context matter. No numerical fatigue conversion was invented here. [Original trial, 2022](https://pubmed.ncbi.nlm.nih.gov/34767655/).
- Garmin describes adapting to workouts outside the plan and using device-derived load/recovery information. This is a product reference for closing the loop, not independent proof of effectiveness or access to Garmin's proprietary policy. StrideLab cannot assume it has equivalent evidence. [Garmin Run Coach](https://www.garmin.com/en-US/garmin-technology/garmin-coach/garmin-run-coach/?cdncache=false).

Considered approaches:

| Approach | Decision |
|---|---|
| Tune fixed percentages/templates | Rejected as primary improvement: leaves evidence and allocation ordering unchanged |
| Physiological latent-state model / optimizer with injury score | Not implemented: insufficient trustworthy measurements or outcome labels; unjustified precision |
| Constraint search plus explicit next-week evidence policy | Implemented as two separate comparators; simple, deterministic, no new dependencies |

## Implemented boundary and behavior

`IndependentAdaptiveWeek.allocate` retains M4 input gates and finds a complete one-week allocation under caller-supplied day/long-run limits. It selects the feasible long-run distance nearest M4's preferred dose, then balances remaining distance in integer 0.01 km units. It checks the result through the existing feasibility checker. It rejects zero-distance scheduled sessions and impossible totals, and never silently changes the requested target. Two-day allocations remain without a distinguished long run. These limits are asserted constraints, not validated physiological tolerances.

`respond` first preserves original M4 gates, then requires fresh complete preceding weeks and explicit tolerated feedback. It proposes no more than the lower of the two recent weekly totals or the approved start, bounded by target/ceiling. Unknown feedback, unknown coverage, zero weeks and difficult response have distinct deferrals. It only plans a full Monday-start next week; future completion is not fabricated. All results remain source-unverified and unauthorized for production. This is a **repeat-only research policy**, not successful upward progression or a complete coach.

Scope: one new internal production-source file (unreferenced by runtime callers), two JVM test files, this report and CSV. No repository, provider, schema, Android UI, Engine 1.0 rollback, default Engine15 policy or Engine2 modification. No external dependencies.

## Comparative results

Run `IndependentCoachingComparisonTest`: 14 named scenarios × baseline, constraint-only, responsive = 42 results. Both alternatives receive the same available scenario evidence; baseline deliberately cannot consume response/history, exposing its current API limitation. These are **one-week** comparisons, not a longitudinal trial. `next-week-comparison-20261115.csv` contains every result.

| Outcome | Baseline M4 | Constraint-only | Responsive |
|---|---:|---:|---:|
| Feasible candidates | 12 | 13 | 9 |
| Candidates conflicting with supplied limits | 2 | 0 | 0 |
| Explicit deferrals | 0 | 1 | 5 |
| Distance reconciliation error > 0.00001 km | 0 | 0 | 0 |

The original pipeline would defer its two conflicting candidates downstream; this table does not claim that production executes them. Constraint-only repairs one avoidable rejection. The impossible three-day/15 km long-run ceiling case remains deferred. Responsive additionally defers unknown import coverage, zero recent running, difficult response and unknown response. These deferrals are uncertainty decisions, not evidence of improved outcomes.

For repeated 30 km completions with a 60 km asserted start and 80 km goal, baseline and constraint-only emit 60 km; responsive emits 30 km. Stable 60/60 yields 60. A 60/100 spike does not raise the candidate. A lower 30 km target is respected by all variants. The repeat-envelope metric is an experimental definition, **not an injury-risk metric**.

**Counterexamples:** after a deliberate 60→40 km recovery week, responsive emits 40 where baseline emits 60. Without recovery intent and tolerance evidence, it cannot identify an appropriate rebuild. Stable athletes cannot progress above the repeat envelope, and absent feedback may cause unnecessary review. Two available days can concentrate 60 km into 30+30 under loose supplied limits: feasibility is not safety. An authenticated per-session history envelope is still missing. Race-goal changes and multi-discipline recovery are not tested by this comparison. No superiority claim across the full requested scenario space is warranted.

## Quality time/distance decision

Two designs considered, neither activated:

1. Whole-session distance budget with timed work inside it: require a reviewed feasible envelope for work, recoveries, warm-up/cool-down and distance stop behavior. Without it, the distance may finish before timed work, or timed work may overshoot the budget. Completed distance is observed afterward and cannot prove feasibility beforehand.
2. Athlete-specific pace **ranges** for each segment: retain an interval for predicted distance and reject if its upper bound exceeds the distance allocation. Requires provenance, freshness, pause handling, terrain context and intensity review. A single average pace or race predictor is insufficient. Use explicit unresolved status when unavailable.

The existing nonexecutable overlay is retained. Duplicate sources, fake reviewer references, missing work classification and unverifiable effort remain admission problems, not quantities to fill with defaults.

## Validation and independent critique

Environment: Windows, Gradle wrapper 8.13, explicit Temurin JDK 21.0.13, installed SDK compile 36. Set JAVA_HOME to `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`, ANDROID_HOME to the user's local SDK. Commands from `native-android`:

```
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"
.\gradlew.bat :app:testDebugUnitTest --tests "*IndependentAdaptiveWeekTest" --no-daemon --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"
```

Baseline: 527 tests, debug build passed, 44 tasks executed. Initial default-JBR attempt failed Gradle configuration (`Type T not present`); explicit JDK21 resolved it. New test-first run failed compilation with missing candidate symbols, then five focused methods passed. Initial candidate full suite: 533 tests, zero failures/skips, debug build passed.

Independent reviewer found an approval contradiction hidden by adapting the starting load before checking original M4 inputs. Added `responseCannotRepairContradictoryApprovalAssertions`; observed its assertion fail; now original M4 gates run first. Comparison assertions strengthened to require each expected decision/distance/rule rather than merely produce a CSV. Final full JVM suite: **534 tests, zero failures/skips; debug build passed in 24s**, 8 tasks executed and 36 up-to-date. This includes six candidate test methods (one with 36 deterministic day/volume combinations) and the 42-row comparator.

No connected devices and no configured AVD (`adb devices -l`, `emulator -list-avds`). No physical installation, emulator instrumentation, live Garmin, map rendering, real import, cloud isolation or athlete-outcome validation. Existing JVM tests exercise covered regressions only. Existing compiler/deprecation warnings remain.

## Highest-value next work

1. Make authenticated evidence freshness, coverage, deduplication and plan-to-completion attribution reviewable; keep observations distinct from inferred capacity.
2. Add explicit recovery/illness/availability intent and session-level completed-distance envelopes before considering a bounded progression probe. Evaluate sequential missed/recovery/rebuild trajectories against a repeat-only control.
3. Develop separate race feasibility/phase decisions; retain Train Your Way's weekly-mileage goal. Do not fabricate a target race time from volume.
4. Resolve quality dose/intensity provenance and dimensional reconciliation, then add mixed-discipline density constraints. No arbitrary global minute cap.
5. Audit signed-out plan ownership end-to-end and run Compose/navigation/persistence checks on an isolated emulator. Review targeted UI changes separately.

This delivery establishes a comparative experimental foundation and a constraint repair, not the complete adaptive coaching system requested as the long-term objective.
