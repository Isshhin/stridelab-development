# Gate B preflight — workload planning, version 0.1

Created 2026-11-16 **before M3/M4 refactoring**. Status: **DRAFT for review; not an approved coaching safety threshold**. The diagnostic acceptance conditions below are fixed for R0 execution. Proposed M3/M4 training limits must receive separate review before becoming implementation requirements. R0 reports cannot retroactively change the rubric.

## R0 diagnostic pass/fail (mechanical, predeclared)

1. **Parity:** all seven non-rejected synthetic profile plans generated with diagnostics must be structurally identical to the same uninstrumented Engine 1.5 Q2/Q3 plan (zero session, duration, role, prescription, explanation and metadata differences).
2. **Coverage:** each generated plan must have `weeks` distinct weekly summary rows and at least one executed baseline and final stage event for every running week; total rows must reconcile with each plan's running sessions.
3. **Unknown handling:** zero recent-mileage / time-only weeks must have **blank** delivered distance and gap fields, not `0.000` or a fabricated target. Mixed Q2 sessions must be identified as planning-pace estimates.
4. **Attribution:** stage comparisons state units in km and report to 0.001 km (presentation tolerance only); session-cap candidates must have blank output/delta. A considered limit is **not** an executed reduction.
5. **Privacy:** no real account IDs, GPS coordinates, names, injuries or personal activity IDs in synthetic artifacts; no persisted or uploaded diagnostic in production generation.
6. **Safety regression:** the diagnostic path must not change the existing Q2 mixed-unit or Q3 evidence contracts. Existing approval and recovery restrictions remain in force.
7. **Recovery hold:** an explicit `runningRecoveryHold` still rejects generation. It does not yield a synthetic approved plan.

A failure in any mechanical condition blocks R0 acceptance. Where a plan cannot generate because of current constraints, a reason-coded rejection is recorded instead of fabricated weekly mileage. R0 evidence is not a clinical safety validation.

## Prespecified scenarios (same engine, fixed date 2026-11-16, pace 360 s/km)

| Scenario | Recent km/wk | Target km/wk | Weeks | Run days | Longest km | Additional setup | Diagnostic expectation |
|---|---:|---:|---:|---:|---:|---|---|
| Experienced new app | 75 | 80 | 8 | 6 | 20 | experience=2, 1 quality | Executed baseline, ceiling, caps, Q2 and final; report shortfall if any |
| Large target jump | 50 | 80 | 4 | 6 | 16 | experience=2, 1 quality | Show distance feasibility and **actual** binding stages; do not assume 80 km is attainable |
| Developing | 30 | 35 | 8 | 4 | 10 | experience=1, no quality | Trace low/moderate volume allocation and recovery week |
| Beginner | 0 | 20 | 8 | 3 | 0 | experience=0, no quality | Time-only unknown mileage; do not present 20 km as prescribed |
| Returning | 40 | 50 | 8 | 4 | 12 | returning=true, no quality | Explicit reduced return baseline and recovery stage |
| Limited availability | 75 | 80 | 8 | 3 | 20 | experience=2, no quality | Trace number-of-sessions/exposure feasibility |
| Unverified quality | 75 | 80 | 8 | 6 | 20 | experience=2, quality requested, no completed work history | Preserve conservative quality dose, trace unchanged output |
| Recovery concern | 75 | 80 | 8 | 6 | 20 | runningRecoveryHold=true | Rejected before any approval |

These numbers are diagnostic *inputs*, not target workouts or evidence of safe progression.

## Proposed M3/M4 acceptance questions (not frozen safety numbers)

- **M3 baseline:** Does a new experienced runner with a reported *average* of 75 km/wk receive a provisional capacity profile rather than being confused with unknown or zero data? Is a peak week distinguished from a typical week and is the reported longest run retained?
- **M4 weekly budget:** In the 75→80 km case, does a trace explain any stage preventing the target? Does the final budget reconcile to prescribed/estimated distances without concealing discarded workload?
- **Feasibility:** In 50→80 km in four weeks, does the planner use a stated capacity constraint and, where appropriate, report an alternative rather than force an 80 km output?
- **Recovery:** Does explicit adverse feedback remain review-gated, independent of mileage ambitions?
- **Workout quality:** Are Q2 warm-up/cool-down extents and Q3 family-specific verified-work constraints preserved with no unjustified Q2 remainder-filling?

Proposed numerical coaching thresholds (including the current 40% long share, 20% quality work, and provisional progression bands) remain unchanged until: (a) the R0 executed ledger is reviewed; (b) a binding rule is identified; (c) a competing policy and safety rationale are written down; and (d) before/after scenario outputs are evaluated against this **pre-change** rubric.

**Decision gate:** Gate B may approve a changed rule, keep it, or request more evidence. It may not silently widen limits to make a target easier to hit.
