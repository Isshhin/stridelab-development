# StrideLab Engine 1.5 decision log — R0 / Gate B

## Decision 2026-11-16 / D-001 — Diagnose before changing guardrails

**Status:** Implemented as instrumented, unmerged research candidate. Test evidence pending.

**Problem:** A new runner reporting 75 km/week and targeting 80 km/week received a draft with an approximately 70 km/week estimated peak, then the approval guard rejected the target gap. The report is a user-observed symptom, **not** an executed attribution to any specific engine rule.

**Decision:** Implement an opt-in, read-only R0 ledger on the existing Q2/Q3 Engine 1.5 code. Record actual outputs for Engine 1.0 baseline, Engine 1.5 ceiling, session capacity, weighted allocation, long share, rounding, quality-role reconciliation, timed support conversion and final mixed-unit reconciliation. Maintain clear separation of considered caps from executed deductions. Test synthetic profiles against the uninstrumented generator to confirm zero behavioral differences.

**Approved R0 scope:** Observability, deterministic synthetic tests, offline CSV output and preflight acceptance documentation. No threshold changes, no workout generator rewrite, no plan-schema change, no automatic telemetry.

**Rejected alternative:** Removing/raising the 40% long-run or 20% work-share safeguard before measuring when it binds. Also rejected: building another weekly workload planner inside the current hybrid path before M3/M4 ownership and interfaces are established.

## Roadmap mapping (not new parallel milestones)

- Existing **Q1/Q2/Q3**: current quality diagnosis, Q2 mixed units, and Q3 evidence-gated quality work; preserve.
- **R0**: diagnostic and causality gate before any M3/M4 refactor.
- **Gate B**: prewritten numerical testing rubric, decision about which constraints can be reconsidered.
- **M3**: confidence-aware athlete baseline (self-report is provisional; unknown is not zero).
- **M4**: one reusable volume skeleton/weekly planner interface. Do not duplicate it inside the hybrid engine and independent generator.
- **M5/M6**: workout composition and recovery/periodization, using M4's workload budget.
- **M7/M8/M9**: evidence-aware adaptation, rolling updates and independent final validation.
- Existing **Q4/Q5**: guarded progression/final coaching acceptance; sequence only after R0+Gate B prove the current constraints and changes.

## Change-authorization protocol

Every proposed guardrail adjustment must document: (1) current source and rationale, (2) evidence level, (3) executed R0 binding profile/week/event, (4) numeric alternative to compare, (5) invariants preserved, (6) before/after results against predeclared scenarios, (7) review verdict. A shortfall is not itself evidence that a rule is wrong. Test results must be tagged `EXECUTED`, `CONSIDERED_ONLY`, or `INFERRED_PENDING_INSTRUMENTATION`.

## Next decision

Run `Engine15R0WorkloadLedgerTest` locally and review `executed-weekly-ledger.csv` and `executed-rule-events.csv`. When R0 evidence is available, decide whether the Engine 1.0 baseline boundary requires deeper internal instrumentation before Gate B authorizes M3/M4. No results are recorded or claimed before the test actually runs.

## Decision 2026-11-17 / D-002 — R0 executed readout and Gate B authorization

**Status:** R0 accepted on executed synthetic measurements; M3 contract implementation authorized; M4/M5 behavior changes NOT authorized.

The user locally executed the R0 focused test (BUILD SUCCESSFUL, 37s) and supplied console displays of the CSV reports. For 75→80 km, Week 7's Engine 1.0 baseline measured 85.700 km, Engine 1.5 allocated 78.700 km, and the final Q2 mixed-unit estimate was 70.533 km. The 40% long-run and 20% quality-work safeguards made no selected-profile reduction. This does not prove the safety of relaxing any threshold. Full evidence readout: GATE_B_EXECUTED_READOUT_20261117.md.

**Approved:** A provisional, source-aware M3 athlete baseline metadata interface. Reuse existing IndependentRunningHistory evidence with explicit complete-week proof. Preserve legacy plan JSON and actual Q2/Q3 prescriptions.

**Not approved:** Making Engine 1.0's output a new target, loosening current progression/long-run/quality rules, raising total workload, auto-certifying Garmin records, or creating a second weekly workload planner.

## Decision 2026-11-17 / D-003 — M3 input contract and consumer

**Status:** Code committed on a draft research branch; focused Kotlin tests pending.

PlanRequest receives optional recentMileageBasis and reportedPeakWeeklyKm metadata. Existing weeklyKm remains the legacy scalar consumed by Engine 1.5 until M4. Engine15AthleteBaseline.assess returns a typed capacity profile with typicalWeeklyKm (nullable), reported peak, reported/unverified longest run, verified four-week reference when available, provenance confidence, and review reasons. Unknown and explicit zero remain distinct in that typed profile even though the old generator currently handles both as time-only.

**Constraint:** The M3 module is read-only. An imported *partial* week or self-reported average does not become verified capacity. A reported peak week alone cannot be considered a typical week. Contradictions return NEEDS_REVIEW and suppress the M4-ready typical weekly value. The M4 caller must explicitly make the eventual volume/progression decision, and must not silently interpret an unresolved profile as zero.

**Approval test:** Exact generated-session parity against pre-M3 Q2/Q3 inputs, new and old serialization roundtrips, no automatic week-coverage promotion, explicit zero vs unknown vs peak-only, contradiction rejection by the typed profile, and no safety-policy mutations.

## Decision 2026-11-17 / D-004 — M4 Stage A parity migration, no relaxed limits

**Status:** Committed on draft branch; focused local validation pending.

Following the user's successful M3 Gradle result (BUILD SUCCESSFUL in 1m 47s), the first M4 batch extracts the EXISTING Engine 1.5 weekly ceiling and upstream-volume/recovery intersection into `Engine15WeeklyVolumeSkeleton`. The generator delegates to it, while future M4 candidates may consume its separate `outline(Engine15AthleteCapacity,...)` interface. The pre-written mechanical rubric in `M4_GATE_B_PREFLIGHT_20261117.md` defines expected ceiling and R0 parity before test execution.

**Approved scope:** numerical ownership consolidation and tests; no change to existing 0.9/0.95/etc coefficients, 80% recovery limits, 40% long-run ratio, 20% quality work, Q2 support timing, Q3 historical work or PlanSnapshot persistence. The Engine 1.0 scheduling baseline remains an upstream cap in Stage A.

**Not yet authorized:** independent M4 Stage B workload progression, removal of Engine 1.0's session-volume influence, using confidence to widen workload, reallocating Q2 differences or changing target approval. This stage is a migration with *unchanged training outputs*, not a fix to the 80 km target gap. Focused M4 test execution and Gate B comparison required before progressing.

## Decision 2026-11-17 / D-005 — Independent M4 Stage B shadow candidate

**Status:** Implemented on draft branch; focused execution and Gate B candidate review pending.

The user's M4 Stage A focused Gradle result passed (BUILD SUCCESSFUL in 32s). A new independent M4 Stage B candidate is committed with a pre-written review rubric at `M4_STAGE_B_GATE_B_PREFLIGHT_20261117.md`. It consumes M3 capacity plus explicit run-day/target/return/recovery flags; it never calls Engine 1.0, Engine 1.5 plan generation, the calendar, session composer or saved-plan storage.

**Scope:** Standalone weekly budget proposals, current 1.5 ceiling retained; conservative session-exposure upper bound; return-from-break bound; one deload applied from prior *planned budget*, not prior Q2 *estimated final distance*. Provisional self-reported averages are usable with a two-completed-week review marker. Peak-only, unknown, legacy-unconfirmed, explicit zero, contradiction and health hold remain distinct and do not produce numeric candidate weeks.

**No final coach authorization:** M4B budgets are not scheduled workouts and are not proven feasible. They do not remove runtime Engine 1.0 fallback yet; removing it before M5/M6 would leave the app unable to build its running sessions. The final independent engine will reject infeasible requests rather than silently fall back. The measured R0 70.533 km mixed-unit estimated peak must never be compared directly with a pre-composition M4B budget as though both were final prescription volumes.

**Gate:** Run focused M4B tests; review all profiles and the single-recovery deload contrast with R0; approve or reject actual policy changes before wiring into production. The 40% long-run and 20% quality-work constraints remain unchanged.
