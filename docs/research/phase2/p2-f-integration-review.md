# P2-FI | Wave 1 integration and Gate 2 readiness review

Reviewed: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Wave 1 reviewed head: `bd07966475510f9349c2cea0a06027b1211c591d`  
Frozen Session G baseline: `26a8331e642c888dd73acb94755dded063baab8d`

PR #34 was verified open, draft and unmerged at review start.

## Verdicts

### P2-F feasibility

`P2_F_FEASIBILITY_READY`

**Scope:** synthetic engineering feasibility only.

This verdict authorizes continued development/audit of the linkage, parser, provenance, withdrawal and replay machinery using project-authored synthetic fixtures and other separately admitted non-human engineering fixtures.

It does **not** authorize acquisition or analysis of human athlete records.

### P2-F Gate 2

`P2_F_GATE_2_NOT_READY`

Confirmatory held-out evaluation remains blocked.

### P2-B readiness

`P2_B_PROTOCOL_READY_DATA_NOT_READY`

The repeatability protocol is sufficiently specified to preserve prospective analysis choices, but the required authorized human repeated segments and trace-level measurement support do not yet exist.

## Independent integration summary

Wave 1 produced four logically compatible preparation artifacts:

- P2-F1: rights/source admission
- P2-F2: plan-to-performance linkage contract
- P2-F3: parser/provenance/reconciliation feasibility
- P2-B0: comparable-easy repeatability protocol

No material contradiction requires changing the frozen Session G architecture.

The main result is a clear boundary between **engineering feasibility** and **human/confirmatory validation**.

## P2-F1 review

### What passes

The source register is purpose-specific rather than globally permissive.

It correctly rejects the following shortcuts:

- public visibility as permission;
- API availability as model-development permission;
- broad consent as permission for every future purpose;
- provider/platform access as permission to inspect or retain all available data;
- one source grant as authorization for cross-athlete fitting or commercial use.

Only project-authored synthetic fixtures with verified provenance are currently admitted:

`S0 = ADMIT_FOR_FEASIBILITY`

All proposed human/device/provider routes remain quarantined, research-only or rejected.

This is conservative and consistent with the frozen Session G admission rules.

The withdrawal protocol also correctly requires:

- purpose-specific grants;
- predeclared retention;
- downstream lineage invalidation;
- backup/restore withdrawal behavior;
- account isolation;
- suppression of withdrawn data from replay.

### What remains blocking

No human source has a completed admission receipt.

In particular:

- S1 owner direct export has no recorded study-specific permission;
- S2 independent adult participants have no approved enrollment/consent package;
- S3 prospective cohort has no partner/ethics/DUA package;
- S4-S7 require unresolved source-specific terms or permission;
- S8-S9 are rejected for the proposed use;
- S10 remains documentary only.

Therefore P2-F may not yet claim a lawful human feasibility dataset.

## P2-F2 review

### What passes

The linkage contract is sufficiently explicit to preserve forward-time semantics.

It separates:

- plan identity;
- plan version;
- session opportunity;
- prescription revision;
- decision;
- alternatives;
- athlete choice;
- performed activity;
- reason/context;
- follow-up/outcome.

The contract preserves distinct timestamps such as:

- recorded time;
- effective time;
- performed time;
- available time;
- import/sync time.

This is important because later uploads and later corrections must not become information that a historical decision supposedly knew.

The contract also correctly preserves:

- account partitioning;
- stale-revision behavior;
- unshown shadow recommendations;
- manual occurrence vs measured dose;
- many-to-many intent links;
- missing upload vs confirmed skip;
- unknown vs zero;
- follow-up not-due vs overdue-missing;
- withdrawal and correction history;
- decision-time versus later-label projections.

The B0 field handback is also incorporated.

### Remaining concerns

F2.1 is still a logical contract.

It has not yet been validated on an admitted human/device pilot.

The future implementation must prove that:

- all required event times are actually obtainable;
- source/provider identifiers are stable enough for the contract;
- offline/late-sync view reconstruction works in practice;
- duplicate aliases can be adjudicated without time/route guessing;
- rights/withdrawal metadata are enforced before every derived view.

These are feasibility tasks, not reasons to reject the schema.

## P2-F3 review

### What passes

The synthetic parser/reconciliation probe exercises meaningful failure cases:

- FIT/TCX/GPX parsing;
- timer/elapsed distinctions;
- cadence normalization;
- zero preservation;
- malformed files;
- invalid HR;
- missing/ambiguous lap boundaries;
- timezone rollover;
- late availability;
- duplicate formats;
- stale revision;
- cross-account mismatch;
- manual confirmation plus import;
- unmatched activity/opportunity behavior;
- deterministic replay.

The reconciliation layer fails closed on ambiguous identity and revision conflicts.

The severity model is appropriate:

- `CRITICAL_MISMATCH`
- `QUARANTINABLE_MISMATCH`
- `RECOVERABLE_MISMATCH`

Any observed silent critical mismatch correctly blocks advancement.

### Important measurement boundary

The current adapter does **not** supply everything P2-B0 requires.

Specifically, F3 documents that the present adapter cannot establish:

- sample-level pace/speed streams;
- sample-level power;
- moving time;
- steady 20-minute measurement windows;
- reliable pause-free segment reconstruction for B0;
- environmental exposure.

FIT session summaries are not enough for the P2-B protocol.

This is not a failure of P2-F3. It is an explicit and useful limitation.

It means P2-B cannot begin active repeatability measurement from the current parser adapter.

A trace-level authorized ingestion path plus manual audit is required first.

### Real-device requirement still unmet

The seven test methods use generated fixtures.

No real-device denominator, device-specific coverage, observed audit burden or real-device critical-mismatch rate exists yet.

## P2-B0 review

### What passes

The protocol is sufficiently prospective and constrained.

It fixes before outcome inspection:

- route and direction;
- start-marker behavior;
- segment duration;
- HR-control behavior;
- speed-control behavior;
- device/sensor continuity;
- pause rules;
- environment/context matching;
- pair assignment;
- typical-error estimand;
- common-support reporting;
- exclusion accounting;
- negative controls;
- device/route bridge behavior.

The six-date / three-disjoint-pair design is explicitly described as feasibility-level and does not claim statistical power.

The protocol correctly refuses to infer:

- progression;
- fitness change;
- personalized heat correction;
- MAF threshold;
- physiological threshold;
- training effectiveness

from repeatability alone.

The protocol also makes a useful distinction between:

- speed at comparable HR;
- HR at comparable speed;
- drift;
- decoupling.

No universal decoupling cutoff is introduced.

### Why active P2-B remains blocked

P2-B0 requires fields and sampling fidelity that the current F3 adapter cannot yet provide.

It also requires:

- admitted human records;
- repeated eligible dates;
- route/environment/context fields;
- sensor continuity;
- prospective acquisition;
- frozen practical margins before confirmatory access.

Therefore:

`P2_B_PROTOCOL_READY_DATA_NOT_READY`

## Cross-track consistency

### F1 ↔ F2

**PASS WITH HUMAN-DATA BLOCK**

F2's schema is compatible with F1's purpose-grant model.

However, F2 must not cause collection of route, time, symptom, HR or diary fields unless the exact source/purpose has been admitted.

The schema defines possible fields, not universal permission to collect them.

### F2 ↔ F3

**PASS FOR SYNTHETIC STRUCTURAL FEASIBILITY**

F3 can exercise a narrow subset of F2 identity/revision/replay requirements.

It does not implement the full F2 event graph.

That is acceptable for the current engineering feasibility stage.

Before a human pilot, the implemented research ledger must cover the required F2 subset for that pilot and pass account/withdrawal/replay tests.

### F2 ↔ B0

**PROTOCOL CONTRACT PASS**

F2 includes the B0 segment interface and the required provenance/context fields.

No outcome-aware schema addition is currently required.

### F1 ↔ B0

**PASS WITH PURPOSE-SPECIFIC ADMISSION REQUIRED**

B0's route, weather, HR, RPE and context fields can be privacy-sensitive.

They are not automatically admitted.

A later human pilot must separately authorize/minimize those fields under F1.

### F3 ↔ B0

**BLOCKING IMPLEMENTATION GAP FOR ACTIVE P2-B**

The current parser/reconciliation path does not expose the trace-level measurements needed for B0.

This is the main technical dependency before active P2-B.

## P2-F feasibility decision

`P2_F_FEASIBILITY_READY`

This means only:

- continue synthetic engineering feasibility;
- finish the research-only event/linkage implementation;
- exercise withdrawal/account-isolation/replay behavior;
- prepare an admitted human-pilot package;
- build/verify the trace-level path needed for future B0 measurements.

It does **not** mean:

- human data are admitted;
- real-device linkage accuracy is established;
- Gate 2 is frozen;
- held-out data can be opened;
- P2-B can begin active measurement;
- any coaching model can be fitted.

## Gate 2 readiness decision

`P2_F_GATE_2_NOT_READY`

The following items remain unresolved.

### G2-F1 — admitted human source

At least one human feasibility source must have a complete, versioned admission receipt covering the actual intended pilot fields and uses.

The receipt must resolve:

- controller/custodian;
- purpose;
- consent/grant;
- supported device/source;
- access holders;
- retention;
- withdrawal;
- location handling;
- processor/storage scope;
- pilot partition;
- exclusion from confirmatory partitions.

### G2-F2 — implemented/pinned pilot linkage schema

F2.1 must be translated into the minimal pilot implementation and pinned.

The pilot must prove:

- account isolation;
- plan/revision identity;
- as-of replay;
- duplicate handling;
- stale-decision rejection;
- manual-only occurrence;
- unknown/missing behavior;
- withdrawal invalidation.

### G2-F3 — real-device manual reconciliation

Run the F3 manual audit on the separately admitted development pilot.

Freeze:

- supported device/firmware/format;
- parser/decoder versions;
- device-specific coverage;
- manual reference procedure;
- critical mismatch rule;
- observed burden;
- unsupported channels.

Any silent critical mismatch blocks that device/source path.

### G2-F4 — trace-level measurement support for P2-B

Before P2-B can move from protocol to data collection, implement and manually audit the authorized trace-level fields required for:

- continuous HR;
- speed/distance method;
- timestamps;
- timer/pause/gap;
- segment bounds;
- device/sensor epoch.

Do not infer them from summary-only FIT/TCX/GPX values.

### G2-F5 — pilot-only margins and burden

Use development-only feasibility data to freeze:

- minimum useful linkage coverage;
- acceptable audit burden;
- device/source inclusion;
- stop/restart rules.

Do not derive confirmatory margins from the future sealed set.

### G2-F6 — signed Gate 2 manifest

The P2-F owner and independent reviewer must freeze the full versioned manifest before confirmatory access.

At minimum it must record:

- source/admission scope;
- linkage schema;
- comparator/endpoint for the confirmatory P2-F audit;
- practical margin process/value where applicable;
- coverage;
- burden;
- abstention/missingness accounting;
- subgroup/device reporting;
- negative controls;
- leakage boundaries;
- stop/restart authority;
- exact development versus sealed partition.

## P2-B readiness decision

`P2_B_PROTOCOL_READY_DATA_NOT_READY`

Next prerequisites for active P2-B:

1. admit a lawful human development source;
2. implement the minimal F2 pilot linkage;
3. pass real-device F3 reconciliation;
4. implement/audit trace-level measurement;
5. collect the prospectively registered B0 opportunities;
6. freeze the P2-B Gate 2 manifest before confirmatory evaluation.

## Recommended immediate next step

Do **not** open P2-A/C/D/E.

Do **not** start confirmatory P2-B.

The next work should be a bounded **P2-F feasibility implementation/pilot preparation wave** focused on:

1. consent/admission package for S1 or another lawful human feasibility source;
2. minimal F2 event/linkage implementation in the research harness;
3. trace-level FIT/TCX/GPX measurement support required by B0;
4. synthetic adversarial tests for withdrawal, account isolation, replay and duplicate behavior;
5. then an independently reconciled, separately admitted real-device development pilot.

After that pilot, prepare the P2-F Gate 2 manifest for another independent review.

## Boundaries

- Frozen Session G baseline remains `26a8331e642c888dd73acb94755dded063baab8d`.
- PR #34 stays open, draft and unmerged.
- Engine 1.5 production remains unchanged.
- Trackside production UI remains unchanged.
- No APK/release work.
- No raw human athlete data in Git.
- No confirmatory held-out access.
- No coaching-effectiveness or competitive-parity claim.
