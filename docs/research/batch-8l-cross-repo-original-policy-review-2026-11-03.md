# Batch 8L — Review real original-lab policy evidence in Engine 2.0 research

**As of November 3, 2026 (Manila).** **Branch:** `research/batch8l-evidence-review-20261103` stacked on the existing **draft, unmerged** Engine 2.0 research PR #34. Original Coaching Lab source remains in its **separate private** [`Isshhin/stridelab-coaching-lab`](https://github.com/Isshhin/stridelab-coaching-lab); Engine 1.0/1.5 and Trackside Android are not modified.

## Actual source policy evidence, rather than toy policy assumptions

The original lab's [Batch 8L producer](https://github.com/Isshhin/stridelab-coaching-lab/blob/research/batch8l-sanitized-handoff-20261103/src/coaching_lab/research_handoff_8l.py) generated a sanitized evidence envelope from **actual implemented original Python policy functions**, using seven checked-in synthetic athlete profiles and six original policies. Its original-lab GitHub Actions run [41605557158](https://github.com/Isshhin/stridelab-coaching-lab/actions/runs/41605557158) passed **128/128 tests on Python 3.11**, the same **128/128 on Python 3.12**, with the two runtime outputs byte-for-byte identical in their compact JSON streams. The original-lab producer source HEAD in this review is `29aa83ad7cdf0a036c370670ccee8883514779c5`.

**Manually reviewed canonical handoff body SHA-256:** `1e860cdcbdef28002bd62bb9966d908d711523178442a885fbefa7faf1cbf0cd`.

**Verified original baseline module fingerprint:** `748e3c84a9a19db570f3275ee8fcfd70305fc1023c884deaefa6b6e988dc9314`; **synthetic fixture checksum:** `b9be1149fb3468fcf191f3fb17c8f6b94b70ba36edc18aff502dfa98fb90fc98`; original baseline HEAD `e255c5d369af3576cc6ccfec44696ed4f15e1c35`.

The sanitized payload was transferred through the authorized connected GitHub interface **without a cross-repo token, workflow secret, personal access token or upload of real athlete files**. The receiver stores:

- `research/validation/evidence/original_lab_8l.json`: **exact canonical JSON** from the original-lab Python 3.11 and 3.12 CI producer logs, not parsed and reformatted into different numeric lexical forms.
- `research/validation/evidence/original_lab_8l_review_pin.json`: the manually reviewed digest and originating GitHub Actions run / commit.
- `research/validation/original_lab_evidence_8l.py`: a separate, fail-closed evidence-integrity verifier.
- `research/validation/test_original_lab_evidence_8l.py`: nine independent negative-control tests.

**Why the original bytes matter:** The first receiving CI rejected the payload when a JavaScript formatter converted some JSON float lexical representations (e.g. `12.0`) into integers (e.g. `12`). These are semantically equal in arithmetic, but result in a different canonical JSON SHA-256. The exact original compact JSON was then copied, and the verified receiver accepted it without weakening the hash checks. The error is preserved in GitHub CI history and demonstrates that source-integrity gates are active.

## Receiving repository verification

[**Verified Engine 2.0 research GitHub Actions 41606273970**](https://github.com/Isshhin/stridelab-development/actions/runs/41606273970) at receiver HEAD `fb082a97241a09fc518a913f9d34f7e7f6ffcff4` passed **141/141 Python research tests**. The independent verifier returned:

```text
PINNED_SYNTHETIC_POLICY_EVIDENCE_STRUCTURALLY_VERIFIED
```

It checked the allowlisted original source paths and module SHA-256 fingerprint structure, known baseline/frozen fixture checksums, original policy and case lists, all **42** outcome entries, generated six-week distance values, and **kept exactly three original `base.fixed_10_percent` `workload.capacity` errors and six explicit legacy infeasible schedules**. Nine new negative controls reject fabricated origin claims, modified inputs, private fields, missing infeasible states, suppressed validator errors and mismatched fixture and source hashes.

The research CI's **existing** B0/H1 toy policy checks remain distinct: 55 structural PASS, three predictive BLOCKED, and original toy contract suite still reports actual Engine 2.0 implementation unconnected. The handoff does **not** verify fitness outcomes or grant production Android access.

## Critical truthfulness and security boundaries

**This is NOT a live private-repository checkout or cryptographic origin attestation.** A GitHub CI run and a human-reviewed SHA snapshot provide auditable provenance within this workflow, but the receiving CI does not independently authenticate the producer runner's identity or verify an external digital signature. The original source is not executed inside the receiving CI. The existing `original_lab_admission.py` should still return `BLOCKED_SOURCE_NOT_PROVIDED` when invoked without a local authorized checkout, and `field_outcome_gate.py` should remain `BLOCKED_NO_CONSENTED_FIELD_DATA`.

**No real personal activity data** were included in the evidence or tested in either receiver's CI. Case IDs and outputs are synthetic-only. The local opt-in FIT/TCX/GPX pilot from Batch 8K has not been run against personally controlled real data, and the Groningen selected observational dataset has not been used to train or assess a coaching outcome policy.

**The three known validation failures are intentionally preserved.** The fact that the remaining 33 original plans have no validator errors does not mean their weekly targets are medically safe or comparatively effective, and six explicit infeasibilities are expected constraints, not repaired schedules. Hashes validate file consistency, not training prescriptions.

## Next best step — Batch 8M

Use this pinned, original software-behavior evidence as a **frozen original policy baseline** for an independent **same-input-semantic** B0/H1 comparison. First map athlete availability, recent volume, intensity, planned vs actual workout data and missingness without assuming numeric equality implies comparable training. Then run policy comparisons with independently defined structural expectations (capacity, recovery, long-run mix, skipped sessions, no invented HR or threshold). **Do not** rank an original policy against a different algorithm until inputs and output decisions are harmonized. Separate sample-selection/missingness assumptions from real effect.

In parallel, gather genuinely consented and reviewed longitudinal **planned → completed → follow-up** training observations through the local Batch 8K pilot. The private owner must explicitly choose the data; no automatic cloud harvesting is authorized. A future more automatic source handoff requires a documented least-privilege read mechanism and independent provenance review, not broad shared tokens.

Keep original-lab PRs #1–#5 and the separate research PRs draft and unmerged; Android Engine 1.0/1.5, production data and Trackside untouched.
