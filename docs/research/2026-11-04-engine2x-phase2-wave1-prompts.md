# Engine 2.x Phase 2 Wave 1 session prompts

Date: 2026-11-04 (Asia/Manila)

Repository:
`Isshhin/stridelab-development`

Research branch:
`research/runna-garmin-coaching-20261102`

Research PR:
`#34`

Frozen Session G baseline:
`26a8331e642c888dd73acb94755dded063baab8d`

Multi-session program:
`docs/research/2026-11-04-engine2x-phase2-multisession-program.md`

Session G independent re-review:
`docs/research/2026-11-04-engine2x-session-g-independent-rereview.md`

All sessions must:
- verify the live branch before writing;
- preserve anything newer;
- keep PR #34 open, draft and unmerged;
- keep raw sensitive athlete data outside Git;
- maintain a durable handoff;
- not modify Engine 1.5 production, Trackside production UI, or APK/release infrastructure;
- not open confirmatory held-out outcomes before the relevant Gate 2 manifest is frozen and independently reviewed.

---

# Prompt: P2-F1 — Rights, source admission, consent and data governance

Continue StrideLab Engine 2.x Phase 2 under the frozen Session G baseline.

Your assignment is:

## P2-F1: RIGHTS, SOURCE ADMISSION, CONSENT AND DATA GOVERNANCE

This session owns source-lawfulness and purpose authorization.

Before doing anything:

1. Verify the live research branch.
2. Preserve commits newer than your starting checkpoint.
3. Confirm PR #34 remains open, draft and unmerged.
4. Read:
   - `docs/research/2026-11-04-engine2x-session-g-independent-rereview.md`
   - `docs/research/2026-11-04-engine2x-session-g-phase2.md`
   - `docs/research/2026-11-04-engine2x-phase2-multisession-program.md`
   - Session F real-data/validation report
   - relevant earlier data-provenance/admission work.

Determine which candidate sources can lawfully and ethically support P2-F and later Phase 2 tracks.

For every candidate source document:

- source/provider/owner;
- acquisition route;
- exact intended use;
- rights/license/DUA/consent basis;
- commercial restrictions;
- research restrictions;
- AI/model restrictions where applicable;
- onward-use restrictions;
- retention requirements;
- deletion/withdrawal behavior;
- data minimization;
- identifiable/sensitive fields;
- route/location privacy concerns;
- whether plan-to-performance linkage is permitted;
- whether derived metrics are permitted;
- whether cross-athlete model development is permitted;
- whether confirmatory evaluation is permitted;
- whether later production use is permitted;
- unresolved terms requiring explicit permission.

Classify each source:

- `ADMIT_FOR_FEASIBILITY`
- `ADMIT_WITH_RESTRICTIONS`
- `QUARANTINE_PENDING_PERMISSION`
- `RESEARCH_ONLY`
- `REJECT_FOR_THIS_PURPOSE`

Do not scrape public activities merely because they are visible.

Do not assume provider API access means AI/model use is permitted.

Prefer:
- explicitly consented original device exports;
- institutionally governed datasets with clear terms;
- prospective opt-in collection;
- provider data only where agreement permits the exact purpose.

Create/update:

`docs/research/phase2/p2-f1-rights-admission.md`

Maintain:

`docs/research/handoffs/p2-f1-rights-handoff.md`

Produce the P2-F Gate 2 source/admission manifest section, but do not self-authorize confirmatory held-out evaluation.

Stop when candidate sources have a clear admit/quarantine/reject decision and unresolved permissions are explicit.

---

# Prompt: P2-F2 — Data contract and plan-to-performance linkage

Continue StrideLab Engine 2.x Phase 2 under the frozen Session G baseline.

Your assignment is:

## P2-F2: DATA CONTRACT AND PLAN-TO-PERFORMANCE LINKAGE

This session owns the canonical research data contract that later Phase 2 tracks depend on.

Before doing anything:

1. Verify live research branch and preserve newer work.
2. Confirm PR #34 remains open/draft/unmerged.
3. Read:
   - frozen Session G state and Phase 2 artifacts;
   - independent re-review;
   - Phase 2 multi-session program;
   - Session F validation report;
   - current research validation harness and relevant schemas.

Define the minimum canonical linkage model for:

`ATHLETE/ACCOUNT`
→ `PLAN VERSION`
→ `SESSION OPPORTUNITY`
→ `REVISION`
→ `DECISION`
→ `ALTERNATIVES`
→ `ATHLETE CHOICE`
→ `PERFORMED ACTIVITY`
→ `REASON / CONTEXT`
→ `FOLLOW-UP / OUTCOME`

Specify:

- stable IDs;
- account scoping;
- event timestamps;
- recorded-at vs effective-at vs performed-at;
- as-of decision semantics;
- plan revision ancestry;
- locks;
- accept/dismiss/reject;
- partial/skipped/substituted/unknown;
- multiple activities for one prescription;
- one activity linked to multiple intended purposes;
- duplicate imports;
- source precedence;
- units;
- elapsed/moving/timer duration;
- HR/pace/power/cadence provenance;
- environment linkage;
- RPE/subjective fields;
- missing/unknown/not-applicable distinctions;
- stale event handling;
- corrections;
- withdrawal;
- follow-up availability;
- no-outcome-yet semantics.

Design for future forward-time evaluation and leakage prevention.

Create adversarial examples covering:

- activity uploaded late;
- plan revised after the workout occurred;
- duplicate FIT and TCX for same activity;
- manual completion plus imported activity;
- missing upload but confirmed activity;
- class/work interruption vs dose intolerance;
- device change;
- account mismatch;
- timezone/date boundary;
- offline edit later synchronized.

Create/update:

`docs/research/phase2/p2-f2-linkage-contract.md`

Maintain:

`docs/research/handoffs/p2-f2-linkage-handoff.md`

Produce the draft Gate 2 linkage-schema manifest section.

Do not train models or implement coaching policy.

Stop when a future experiment can reconstruct exactly what the athlete was prescribed, what information existed at decision time, what changed, what was actually done and what happened afterward without future leakage.

---

# Prompt: P2-F3 — Parser, provenance and reconciliation reliability

Continue StrideLab Engine 2.x Phase 2 under the frozen Session G baseline.

Your assignment is:

## P2-F3: PARSER, PROVENANCE AND RECONCILIATION RELIABILITY

This session validates the research-only ingestion and linkage foundations.

Before doing anything:

1. Verify live research branch and preserve newer work.
2. Confirm PR #34 remains open/draft/unmerged.
3. Read:
   - Session G Phase 2 protocol;
   - independent re-review;
   - Phase 2 multi-session program;
   - P2-F2 if it has advanced;
   - previous FIT/TCX/GPX parser and ledger work.

Inspect the existing research validation harness.

Reuse or extend research-only code/tests where appropriate.

Verify:

- FIT parsing;
- TCX parsing;
- GPX parsing;
- timer vs moving vs elapsed duration;
- cadence normalization;
- zero-value preservation;
- HR/pace/power units;
- lap/session boundaries;
- timestamps/timezones;
- malformed input behavior;
- duplicate detection;
- reordered events;
- missing laps;
- stale plan revisions;
- idempotent re-import;
- account isolation;
- deterministic replay;
- source provenance;
- plan-to-actual reconciliation.

Create an adversarial fixture matrix including:

- duplicate activity;
- same activity in two formats;
- zero HR/pace/cadence values where valid;
- corrupted sensor sample;
- missing segment;
- late upload;
- stale revision;
- cross-account activity;
- timezone rollover;
- partial activity;
- manual completion plus import;
- activity with no matching plan;
- plan with no observed activity.

Define:

- critical mismatch;
- quarantinable mismatch;
- recoverable mismatch;
- audit coverage;
- manual reconciliation procedure;
- stop conditions.

Create/update:

`docs/research/phase2/p2-f3-parser-reconciliation.md`

Maintain:

`docs/research/handoffs/p2-f3-reconciliation-handoff.md`

Research-only implementation/tests are allowed on the research branch.

Do not modify production Engine 1.5 or Trackside.

Do not interpret parser correctness as coaching effectiveness.

Stop when the feasibility harness can fail closed on ambiguous provenance and the manually reconciled audit procedure is explicit.

---

# Prompt: P2-B0 — Comparable-easy repeatability protocol preparation

Continue StrideLab Engine 2.x Phase 2 under the frozen Session G baseline.

Your assignment is:

## P2-B0: REPEATABILITY PROTOCOL PREPARATION ONLY

This is not active P2-B validation.

Do not use held-out outcomes and do not make fitness/progression claims.

Before doing anything:

1. Verify live branch and preserve newer work.
2. Confirm PR #34 remains open/draft/unmerged.
3. Read:
   - Session D;
   - Session G state dictionary;
   - Session G Phase 2 protocol;
   - independent re-review;
   - Phase 2 multi-session program;
   - any current P2-F2 linkage schema.

Define the exact protocol required to test whether comparable easy running provides a repeatable within-athlete measurement.

Specify:

- eligible activity type;
- warm-up exclusion;
- segment duration/distance;
- steady-effort requirements;
- HR sensor provenance;
- pace/speed provenance;
- pause handling;
- GPS artifact handling;
- grade/route matching;
- route direction;
- temperature;
- humidity;
- dew point/vapor pressure if justified;
- wind/solar availability;
- surface;
- altitude;
- acclimation;
- fueling/hydration context where relevant;
- RPE/context;
- fatigue context;
- device continuity;
- protocol bridge after device/route changes.

Define candidate descriptive metrics:

- speed at comparable HR;
- HR at comparable speed;
- signed drift;
- decoupling.

Do not assume any universal decoupling threshold.

Define:

- within-person typical error estimand;
- common-support requirements;
- repeat count needed for feasibility;
- coverage reporting;
- exclusion accounting;
- negative controls;
- sensitivity analyses;
- device/route bridge failure behavior;
- what result means "not usable."

Hand required fields back to P2-F2.

Create/update:

`docs/research/phase2/p2-b0-repeatability-protocol.md`

Maintain:

`docs/research/handoffs/p2-b0-protocol-handoff.md`

Stop when P2-B can begin without inventing eligibility or measurement semantics after outcomes are visible.

---

# Prompt: P2-FI — Integration and Gate 2 readiness review

Run only after P2-F1, P2-F2, P2-F3 and P2-B0 have substantial outputs.

Your assignment is:

## P2-FI: P2-F INTEGRATION AND GATE 2 READINESS

This is an independent synthesis/review session.

Do not redo broad source research.

Before doing anything:

1. Verify live branch and preserve newer work.
2. Confirm PR #34 remains open/draft/unmerged.
3. Read:
   - frozen Session G baseline and re-review;
   - Phase 2 multi-session program;
   - P2-F1;
   - P2-F2;
   - P2-F3;
   - P2-B0;
   - all four handoffs.

Reconcile them into one P2-F feasibility package.

Review:

- admitted/quarantined/rejected sources;
- exact purpose authorization;
- minimal schema;
- event identity;
- as-of semantics;
- duplicate/account isolation;
- parser/reconciliation evidence;
- missingness codes;
- withdrawal/retention;
- pilot-only vs confirmatory data separation;
- B0 required measurement fields;
- critical mismatches;
- leakage boundaries;
- stop conditions.

Produce:

`docs/research/phase2/p2-f-integration-review.md`

Maintain:

`docs/research/handoffs/p2-f-integration-handoff.md`

Issue two separate verdicts:

### Feasibility verdict

One of:
- `P2_F_FEASIBILITY_READY`
- `P2_F_FEASIBILITY_BLOCKED`

### Gate 2 manifest verdict

One of:
- `P2_F_GATE_2_READY_FOR_INDEPENDENT_REVIEW`
- `P2_F_GATE_2_NOT_READY`

Do not self-authorize confirmatory held-out evaluation.

If Gate 2 is ready, identify the exact manifest snapshot that a separate reviewer should accept or reject.

If feasibility passes but Gate 2 does not, allow only the bounded development pilot explicitly permitted by the frozen G protocol.

Do not start P2-A/C/D/E validation from this session.
