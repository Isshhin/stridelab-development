# Engine 2.x Phase 2 multi-session execution program

## Feasibility implementation milestone

The [implementation handoff](handoffs/p2-f-feasibility-implementation-handoff.md) supersedes the original launch sequence below. The [unsigned draft manifest](phase2/p2-f-gate2-manifest-draft.md) records which decisions require a separately authorized real-device development pilot.

Date: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Research PR: #34  
Frozen Session G baseline: `26a8331e642c888dd73acb94755dded063baab8d`

Session G status:

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`

Current authorization:

`P2-F_FEASIBILITY_PREPARATION_AUTHORIZED`

`CONFIRMATORY_HELD_OUT_EVALUATION_BLOCKED_PENDING_GATE_2_MANIFEST`

## Why Phase 2 uses multiple sessions

Phase 2 should use parallel sessions where work can be separated without contaminating held-out evaluation or violating the frozen dependency graph.

Do not launch P2-A through P2-F as fully active experiments at the same time.

The purpose of multi-session work is to parallelize independent preparation and engineering/research checks while preserving:

- the frozen Session G architecture;
- source-rights boundaries;
- sealed confirmatory outcomes;
- track ownership;
- no double-counting across shared E012/E013 observations;
- independent review before each Gate 2 release.

## Wave 1: start now

Run four sessions in parallel.

### P2-F1 — Rights, source admission, consent and data governance

Owns:

- source-rights verification;
- purpose limitation;
- consent scope;
- retention;
- withdrawal;
- allowed/prohibited use;
- device/provider terms;
- admission decision;
- quarantine rules;
- minimal-field justification.

Deliverables:

- source admission matrix;
- rights/consent checklist;
- permitted-use register;
- prohibited-use register;
- withdrawal/retention protocol;
- draft P2-F Gate 2 source/admission manifest section;
- handoff.

Restrictions:

- do not scrape publicly visible activities;
- do not admit a source because it is technically accessible;
- do not collect raw sensitive athlete records into Git;
- do not open confirmatory held-out outcomes.

### P2-F2 — Data contract and plan-to-performance linkage

Owns:

- canonical event identity;
- athlete/account isolation;
- timestamps and as-of semantics;
- plan version;
- revision;
- decision;
- proposed alternatives;
- athlete choice;
- performed activity;
- partial/missed/substituted reason;
- outcome/follow-up;
- units;
- missing/unknown codes;
- duplicate handling;
- stale-event handling.

Deliverables:

- versioned linkage schema;
- event lifecycle;
- missingness taxonomy;
- duplicate/idempotency rules;
- plan-to-actual linkage contract;
- outcome linkage contract;
- draft Gate 2 linkage-schema manifest section;
- adversarial examples;
- handoff.

Restrictions:

- no model training;
- no athlete-state inference;
- no automatic adaptation policy;
- no held-out access.

### P2-F3 — Parser, provenance and reconciliation reliability

Owns feasibility verification of research-only ingestion/linkage mechanisms.

Inspect and reuse existing validation harnesses where appropriate.

Verify or extend research-only tests for:

- FIT/TCX/GPX source provenance;
- units;
- elapsed/moving/timer semantics;
- zero-value preservation;
- duplicates;
- reordered events;
- stale plan revisions;
- cross-account contamination;
- missing laps;
- malformed records;
- account isolation;
- deterministic replay;
- manual source-to-ledger reconciliation.

Deliverables:

- research-only parser/linker verification report;
- fixture matrix;
- manual reconciliation protocol;
- critical-mismatch definition;
- draft Gate 2 software-correctness evidence;
- handoff.

Restrictions:

- do not modify Engine 1.5 production code;
- do not modify Trackside production UI;
- do not wire research logic into production;
- do not treat parser correctness as coaching effectiveness.

### P2-B0 — Repeatability protocol preparation only

This is a design/preparation session, not active P2-B validation.

Owns:

- exact comparable-easy segment eligibility;
- route/direction matching;
- HR source requirements;
- steady-segment definition;
- pause/artifact exclusion;
- environmental comparability variables;
- RPE/context requirements;
- typical-error estimand;
- common-support reporting;
- negative controls;
- device/route bridge rules;
- pilot burden requirements.

Deliverables:

- P2-B protocol-ready specification;
- required fields handed to P2-F2;
- eligibility logic;
- negative-control plan;
- pilot analysis shell without held-out results;
- handoff.

Restrictions:

- no claim that matched-HR change is useful;
- no progression gating;
- no hidden numerical cutoff invention;
- no confirmatory outcome access;
- do not start P2-B real validation until P2-F supplies eligible linked segments.

## Wave 1 integration checkpoint

After F1, F2, F3 and B0 are substantial, run:

### P2-FI — P2-F integration and Gate 2 readiness review

This is a synthesis/review session, not another broad discovery session.

It must reconcile:

- F1 source rights;
- F2 schema/linkage;
- F3 parser/reconciliation;
- B0 measurement field requirements;
- frozen Session G Gate 2 manifest.

It should answer:

1. Is a development-only feasibility pilot safe and sufficiently specified?
2. Which sources are admitted, quarantined or rejected?
3. Is the linkage schema complete enough to prevent outcome leakage and plan-version ambiguity?
4. Are critical mismatches and stop conditions explicit?
5. Can P2-F open a pilot?
6. Is there enough linked data structure to begin P2-B after P2-F feasibility?
7. What remains blocked before confirmatory held-out evaluation?

The integration session may recommend:

- `P2_F_FEASIBILITY_READY`
- `P2_F_FEASIBILITY_BLOCKED`

and separately:

- `P2_F_GATE_2_READY_FOR_INDEPENDENT_REVIEW`
- `P2_F_GATE_2_NOT_READY`

It may not self-authorize confirmatory held-out access.

## Wave 2: after P2-F feasibility/linkage passes

Do not launch until the integration/review gate says the required inputs exist.

Primary active track:

### P2-B — comparable-easy repeatability

Measure within-athlete typical error and common support using authorized, correctly linked repeated segments.

P2-B may only establish whether the measurement is repeatable/useful enough for later evaluation.

It does not by itself authorize:

- progression;
- workout adaptation;
- fitness claims;
- heat correction;
- threshold prescription.

Parallel preparation sessions may begin for P2-A/C/D/E, but they must remain protocol/design-only until their dependencies pass.

## Wave 3: dependent model experiments

Only launch a track when its declared dependencies have passed.

### P2-A — athlete-state estimation

Requires:

- P2-F decision-time linkage;
- independent follow-up;
- P2-B only for any aerobic-efficiency feature.

Compare:

- history only;
- history + short subjective check-in;
- optional multimodal model.

### P2-C — progression and re-entry

Requires:

- P2-F exposure coverage;
- verified interruption reasons;
- any P2-A/B inputs only within validated scope.

### P2-D — reviewed adaptive prescription

Requires:

- linked plan/reason/decision/actual/follow-up from P2-F;
- any state/progression input only after its gate passes.

Start in shadow mode.

### P2-E — cross-discipline and strength

Requires:

- discipline-native exposure;
- independent acute/chronic outcomes;
- endpoint-specific definitions.

## Parallelism rules

1. A session owns one clearly bounded question.
2. Shared observations must carry one canonical observation ID.
3. E012/E013 data may be reused across tracks but never counted as independent replication.
4. Sessions must not modify another track's contract silently.
5. Any proposed change to the frozen G baseline must be escalated to the coordinator/reviewer.
6. No session may open a sealed held-out set merely because another parallel session is ready.
7. Track-specific Gate 2 manifests require independent review.
8. Raw athlete data remains outside Git.
9. All sessions maintain durable handoffs.
10. Live branch state must be verified before every write.

## Merge/conflict strategy for one shared research branch

Because multiple sessions use one research branch:

- verify live branch head before every commit;
- preserve newer commits;
- modify track-specific files where possible;
- avoid editing shared coordinator files until a track checkpoint;
- if a shared file changed since session start, re-read and reconcile before writing;
- do not force-push;
- do not rewrite another session's results;
- keep PR #34 open, draft and unmerged.

Recommended track files:

- `docs/research/phase2/p2-f1-rights-admission.md`
- `docs/research/phase2/p2-f2-linkage-contract.md`
- `docs/research/phase2/p2-f3-parser-reconciliation.md`
- `docs/research/phase2/p2-b0-repeatability-protocol.md`
- `docs/research/phase2/p2-f-integration-review.md`

Recommended handoffs:

- `docs/research/handoffs/p2-f1-rights-handoff.md`
- `docs/research/handoffs/p2-f2-linkage-handoff.md`
- `docs/research/handoffs/p2-f3-reconciliation-handoff.md`
- `docs/research/handoffs/p2-b0-protocol-handoff.md`
- `docs/research/handoffs/p2-f-integration-handoff.md`

## Stopping rule

A session should stop when it can state:

- what was verified;
- what remains unknown;
- exact accepted/rejected/quarantined items;
- falsifiers/stops;
- dependency status;
- next gate.

Do not keep expanding research after the needed decision contract is specified.

## Immediate action

Launch Wave 1 in parallel:

1. P2-F1 rights/admission
2. P2-F2 linkage/schema
3. P2-F3 parser/reconciliation
4. P2-B0 repeatability protocol preparation

Then run P2-FI integration/review.

Do not start active P2-A/C/D/E validation yet.
