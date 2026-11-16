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
