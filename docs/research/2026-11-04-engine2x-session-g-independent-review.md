# Engine 2.x Session G independent review

Reviewed: 2026-11-04 (Asia/Manila)

Repository: `Isshhin/stridelab-development`  
Research branch: `research/runna-garmin-coaching-20261102`  
Reviewed Session G commit: `4b734f1b97a54e6c15ba3bf0662d456de5c68413`  
PR #34: open, draft, unmerged at review start.

Reviewed artifacts:

- `docs/research/2026-11-04-engine2x-session-g-synthesis.md`
- `docs/research/2026-11-04-engine2x-session-g-state.md`
- `docs/research/2026-11-04-engine2x-session-g-candidates.md`
- `docs/research/2026-11-04-engine2x-session-g-phase2.md`
- `docs/research/handoffs/session-g-synthesis-handoff.md`

Reference requirements:

- `docs/research/2026-11-04-engine2x-session-g-final-prompt.md`
- `docs/research/2026-11-04-engine2x-pre-synthesis-review.md`

## Verdict

**PASS_WITH_REQUIRED_CORRECTIONS_BEFORE_FREEZE**

Session G does not need to be redone from scratch.

The synthesis is directionally strong and preserves the central research conclusions correctly:

- capability maturity and evidence maturity are separated;
- `ABSTAIN_NEED_MORE_INFORMATION` is distinct from KEEP;
- athlete state is multidimensional;
- practical utility, calibration, coverage and burden are part of promotion;
- contradictory evidence is preserved;
- Phase 2 starts with trustworthy data/linkage rather than immediately fitting a complex coaching model;
- Engine 1.5 reliability principles remain a fallback constraint;
- no parity, injury-prevention or adaptive-effectiveness claim is made.

However, G should remain in **REVIEW** until the required corrections below are explicitly resolved. Phase 2 should not be declared frozen or started from this synthesis until then.

## What passed

### 1. Architecture

PASS.

The proposed flow is coherent:

`OBSERVATIONS`
→ provenance/quality/eligibility
→ multidimensional state
→ uncertainty
→ safety/constraint gates
→ training intent
→ primary action or abstention
→ downstream-week review
→ athlete review
→ execution
→ outcome
→ state update

This is consistent with A-F + H and the pre-synthesis review.

### 2. Two-axis maturity model

PASS.

The candidate register separates:

- capability maturity;
- evidence maturity;
- competitive classification.

No Engine 2.x policy is incorrectly marked INTEGRATED or prospectively/effectiveness validated.

### 3. Explicit abstention

PASS, with one contract clarification required below.

G clearly distinguishes KEEP from `ABSTAIN_NEED_MORE_INFORMATION` and also identifies a separate "No automated decision" condition.

### 4. Contradiction register

PASS.

The register preserves the major unresolved conflicts:

- HRV/readiness;
- matched-HR interpretation;
- MAF 180 Formula;
- polarized/pyramidal/threshold distribution;
- lactate vs watch zones;
- 10%/ACWR/session spikes;
- taper vs deload vs rebuild;
- concurrent training;
- active recovery;
- environment;
- completion vs adaptation;
- interruption vs missing upload.

The interim behavior generally remains conservative rather than forcing doctrine.

### 5. Phase 2 dependency direction

PASS.

G correctly changed the initial expectation from launching several model tracks immediately to:

1. P2-F admission/linkage;
2. P2-B measurement repeatability;
3. dependent state/progression/adaptation/cross-discipline tracks after the foundations are trustworthy.

That dependency change is justified by the discovery findings.

### 6. Promotion philosophy

PASS.

The Phase 2 protocol appropriately separates:

- software correctness;
- construct validity;
- prediction;
- effectiveness;
- safety association.

It also requires practical margins, coverage, burden, calibration, held-out athletes/future dates and stop conditions.

## Required corrections before freeze

### G-R1. Expand the state dictionary into an explicit implementation/validation contract

**Priority: REQUIRED**

The final G prompt required each state variable to define:

- observable inputs;
- directly measured vs latent;
- time horizon;
- discipline relevance;
- required baseline;
- missing-data behavior;
- conflicting-signal behavior;
- evidence class;
- applicability;
- confidence;
- what can falsify it;
- required Phase 2 experiment;
- whether it may directly change a workout;
- validation status.

The current state dictionary covers many of these, but several are only implied by a shared paragraph or compressed into combined columns.

Before freeze, make the contract explicit enough that implementation cannot invent missing semantics.

Add, either in the state table or a linked state-contract table:

- required baseline/reference requirement per field;
- missing-data behavior per field;
- conflict behavior per field;
- evidence lineage/class per field;
- applicability grade;
- confidence grade;
- direct-decision permission: `BLOCK_ONLY`, `CONTEXT_ONLY`, `CANDIDATE_MODIFIER`, `RESEARCH_ONLY`, etc.;
- current validation status;
- Phase 2 owner/experiment.

This is the largest remaining G gap.

### G-R2. Formalize the control-state contract: KEEP vs ABSTAIN vs NO_AUTOMATED_DECISION vs hard hold

**Priority: REQUIRED**

The synthesis distinguishes these concepts, but the implementation contract still risks ambiguity.

Define separate control semantics and reason codes for at least:

- `KEEP`: sufficient positive grounds to preserve the approved prescription;
- `ABSTAIN_NEED_MORE_INFORMATION`: a resolvable critical fact is missing/conflicting;
- `NO_AUTOMATED_DECISION`: out-of-domain, invalid lineage or unvalidated policy;
- `HARD_HOLD` or equivalent gate: supplied restriction/athlete stop blocks the affected action independent of model confidence.

For each define:

- whether the plan remains unchanged;
- whether execution is permitted or merely not edited;
- whether a question may be asked;
- expiry/re-evaluation trigger;
- UI/review expectation;
- logging reason code;
- whether a later signal can automatically clear it.

This prevents a future UI or engine implementation from treating "I do not know" as "continue normally."

### G-R3. Add mechanism-location provenance to the capability scorecard

**Priority: REQUIRED**

The current capability maturity table intentionally uses the nearest demonstrated StrideLab mechanism, including Engine 1.5/reusable foundations.

That is defensible but can be misread as "Engine 2.x already implements this."

Add a field such as:

`CURRENT_MECHANISM_LOCATION`

Examples:

- ENGINE_1_0
- ENGINE_1_5
- RESEARCH_CONTRACT
- TRACKSIDE
- SHARED_PERSISTENCE
- NOT_IMPLEMENTED

Also state whether promotion requires reuse, port, rewrite or new implementation.

This keeps functional maturity separate from the actual Engine 2.x code state.

### G-R4. Add an explicit Phase 2 freeze/readiness gate

**Priority: REQUIRED**

G identifies open design choices, but before Phase 2 starts it needs a small freeze checklist.

Define what must be frozen before opening P2-F/P2-B held-out evaluation, including:

- source/admission scope;
- event/linkage schema;
- baseline comparator;
- endpoint definition;
- smallest worthwhile change or process for establishing it;
- allowed false restriction/escalation tradeoff where applicable;
- minimum useful coverage;
- acceptable burden;
- abstention accounting;
- subgroup/device/climate reporting;
- negative controls;
- leakage boundaries;
- stopping conditions.

The numerical values do not need to be invented now. The rule should state which values are set from feasibility/pilot data and who/what evidence sets them before confirmatory evaluation.

### G-R5. Make differentiator classifications explicit

**Priority: REQUIRED, SMALL**

The synthesis narratively groups existing foundations, prototype distinctions and hypotheses, but the final prompt requested explicit classification.

Add a short table for potential differentiators such as:

- explicit uncertainty/abstention;
- evidence provenance;
- reviewed adaptations;
- transparent counterevidence;
- mixed-discipline coordination;
- environment/fitness separation;
- offline deterministic replay;
- rollback;
- conservative missing-data behavior.

Classify each as:

- EXISTING
- PROTOTYPE
- HYPOTHESIS
- VALIDATED

No row should be VALIDATED as a comparative advantage without direct evidence.

### G-R6. Reconcile Phase 2 naming and dependency references

**Priority: REQUIRED, SMALL**

The candidate register uses single-letter experiment references such as A/B/C/D/E/F while the roadmap uses P2-A through P2-F and older work uses E009-E012.

Create one authoritative mapping table:

- P2-F ↔ E009 admission/linkage
- P2-B ↔ E010 comparable-easy repeatability
- P2-C ↔ E011/E012 progression/re-entry where applicable
- P2-A athlete-state estimation
- P2-D adaptive prescription
- P2-E cross-discipline/strength

Also define whether E009-E012 remain experiment IDs, protocol ancestors, or aliases.

This prevents future sessions from confusing Session letters with Phase 2 track letters.

## Recommended but non-blocking improvements

### G-N1. Comparator evidence maturity

For competitor rows, record whether the comparator is:

- vendor-documented only;
- directly observed;
- prospectively captured;
- independently validated.

This would prevent "vendor has feature" from being interpreted as "vendor has proven outcome advantage."

### G-N2. State transition examples

Add a few adversarial examples that demonstrate simultaneous dimensions, for example:

- improved easy trend + poor threshold tolerance;
- good HRV + reported illness;
- old long-run exposure + normal easy mileage;
- hot/hilly run + no comparable environment;
- missing upload + confirmed cross-training.

These are useful for later implementation tests but are not required to accept the architecture.

### G-N3. Explicit research-only register

The synthesis names research-only areas in prose. A compact table linked to the candidate IDs would make freeze/review easier.

## Phase 2 start decision

**BLOCKED_PENDING_G_CORRECTIONS**

Do not start the main Phase 2 model experiments yet.

P2-F administrative/source-rights preparation may continue only if it does not open held-out evaluation or collect data under an unfrozen schema.

After G-R1 through G-R6 are resolved:

1. independently recheck the changed artifacts;
2. mark Session G `REVIEW_PASSED`;
3. freeze the architecture/candidate/state/Phase 2 baseline;
4. start P2-F;
5. start P2-B after the P2-F linkage gate allows it;
6. begin P2-A/C/D/E only when their declared dependencies pass.

## No rerun required

The literature synthesis and architecture do **not** need a broad restart.

The correction task is documentation/contract completion.

Do not reopen A-F + H unless one of the corrections exposes a genuine evidence contradiction that prevents specifying a decision or experiment.

## Boundaries remain

- PR #34 stays open, draft and unmerged.
- Engine 1.5 production code remains frozen except reliability fixes.
- Trackside production UI remains separate.
- No release APK.
- No claim of parity, injury prevention, validated readiness, personalized heat correction, MAF prescription or adaptive effectiveness.

## Review status

Session G status should be:

`REVIEW_REQUIRED_CORRECTIONS`

not `COMPLETE` or `FROZEN`.

Once G-R1 through G-R6 are resolved and independently checked, it may become:

`REVIEW_PASSED / PHASE_2_BASELINE_FROZEN`.
