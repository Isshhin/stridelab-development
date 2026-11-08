# P2-D DEV-0 — Synthetic adaptive shadow review, Batches 1–4

Date: 2026-11-08 (Asia/Manila)
Scope: additive **invented-data-only** research on isolated branch `research/p2d-dev0-batches1-4-20261108`, based on the P2-C isolated checkpoint `c27a2191577d7203fc054357652c84c4fa13ee22`. It is **not** Engine 2.x coaching logic, medical advice, source admission, plan generation, or Gate 2 authority.

## Research baseline and objective

The frozen Session G P2-D track retains E013 shadow comparison and depends on trusted P2-F plan/reason/decision/actual/outcome linkage. The Session E candidate set distinguishes KEEP, REDUCE_VOLUME, REDUCE_INTENSITY, SHORTEN_INTERVALS, REDUCE_REPS, SUBSTITUTE, MOVE, RECOVERY and REST. These are unvalidated hypotheses, not an empirical ranking. Existing `week_change_contract.py` already describes concrete pending versioned move/step-replacement drafts with explicit approval; P2-D DEV-0 deliberately **does not call** that contract, does not materialize drafts and does not modify it. A later separate review must determine whether and how to connect a validated decision policy to that authority boundary.

## Batch 1 — Decision provenance / eligibility

New `research/validation/p2d_shadow.py` validates a synthetic-only snapshot, scoped athlete and plan identities, unique planned session IDs, explicit current plan version and purpose, offset-aware decision-time cutoff, required evidence references and matching observed session identity. The evidence recorder excludes future/delayed uploads, inconsistent availability, missing provenance and lineage, invalid quality and unmeasured events; exact event duplicates are counted once and contradictory duplicate identities fail closed. Requests cannot claim an unregistered session purpose. A SHA-256 fingerprint binds the unmodified input plan to the shadow decision. Recursive outcome/held-out-label keys are forbidden from snapshot input. These are software contracts for invented fixtures, not operational source authentication or legal permission checks.

## Batch 2 — Shadow candidates, tradeoffs and stop precedence

The non-executable reviewer returns an ordered collection from the nine research action names, each with its evidence IDs, a purpose-tradeoff warning, `preview_status=NOT_MATERIALIZED`, `candidate_plan=null`, `proposed_dose=null`, `applied=false` and `requires_explicit_approval_to_save=true`. Canonical option ordering is **not** a recommendation or a preference ranking. No option includes a work-minute target, running pace, recovery interval, prescribed distance or actual modified calendar.

Clinical restriction/unknown status, illness/injury and relevant eligible clinical observations force `CLINICAL_BOUNDARY_DEFER` before ordinary choices. A requested or directly observed stop becomes `STOP_REQUESTED`: there is no approval prerequisite to stop an activity, but any future saved revision would require separate authenticated approval. Missing decision evidence, unknown or unverified missed sessions, locked sessions, unregistered synthetic fixture, asserted re-entry authority, or conflicting difficult-response evidence cause `ABSTAIN`. A forged `source_authorized=true` field has no effect; genuine source-rights admission is not implemented.

## Batch 3 — Matched-denominator shadow comparison

`compare_shadow` evaluates a hold/keep baseline and contextual shadow review over **the same invented opportunities**. It counts all opportunities including abstained cases and rejects duplicate (athlete, opportunity) identities. Baseline HOLD is forced for clinical or stop cases and baseline ABSTAIN for unavailable evidence. The comparator reports no model winner, observed training result, expected benefit, risk/false-restriction rate or calibration. It explicitly sets `best_policy=null`, `outcome_quality=null`, `false_restriction_rate=null`, `no_plan_write=true`, `gate2=CLOSED`.

## Batch 4 — Hosted adversarial tests

New `research/validation/test_p2d_shadow.py`: **65 tests, 65 passed, 0 failed** in the hosted Python runtime on the exact implementation source prepared for publication. Cases cover all nine action names and reason/purpose incompatibility; hard-stop precedence; same-identity and cross-identity conflicts; future data and leaked labels; stale plan version; unqualified and foreign-session records; missing/unknown work not treated as verified; locked plan sessions; spoofed fixture/source authority; duplicate opportunities; immutability; identical-denominator reporting; and deterministic replay. Two control issues found during implementation were corrected before the final pass: clinical/stop precedence over unchanged-baseline review and malformed-input handling. No existing repository tests or pre-existing P2-A/P2-C test suites were rerun in this wave. **The 65 passing tests are author-side synthetic results, not independent review or GitHub Actions CI.**

## Explicit limits and next decisions

- The input `synthetic_only=true` and fixture registration are caller-supplied test contracts, **not** a trustworthy authorization system; do not pass real athlete, provider or private data. No tool actually authenticated a source, participant consent or individual workout choice.
- Candidate options are descriptive hypotheses. They have no validated physiological effect, no demonstrated safety, no automatically optimized exercise prescription and no executable plan revision. No real training history, athlete outcomes or approved held-out cohort were used.
- The nine-option vocabulary does not mean that any alternative is feasible. In particular MOVE still needs actual calendar/availability/adjacency checks, interval edits need exact intended-bout and recovery semantics, and substitutions need modality-native assessment. These are deliberately deferred to the existing separate review/approval contract and future independent studies.
- Clinical deferral is not diagnosis, medical triage or clearance. It is a **stop/withhold research boundary only**.
- Neither P2-C's invented restoration labels nor P2-A's synthetic features are imported as coaching authority. No P2-F operational data rights/security or Gate 2 status changes. P2-B DEV-0 remains synthetic-only.
- The work was performed without the user's PC, Work, or Codex. This research branch does not match the self-hosted Windows Actions push trigger, and no Actions run should be expected from its push. A separate non-PC CI option and independent review remain outstanding.

**Author-side checkpoint:** `P2D_DEV0_BATCHES_1_TO_4_SYNTHETIC_IMPLEMENTED_HOSTED_TESTS_PASS`. No independent acceptance is claimed. The next independent wave is P2-E multi-discipline synthetic exposure and coordination, with discipline-native units, while P2-A/P2-C/P2-D review remains pending. PR #34 must remain OPEN, DRAFT and UNMERGED, and Engine 1.5, Android/Trackside, frozen Session G and P2-F remain unchanged.
