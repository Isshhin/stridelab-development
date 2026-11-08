# P2-C DEV-0 — Synthetic progression and re-entry, Batches 1–4

Date: 2026-11-08 (Asia/Manila)
Branch: `research/p2c-dev0-batches1-4-20261108`, based on P2-A's isolated head `8e1983dad18541693b6d218ca76d95c62eaf8c97`.
Scope: additive, synthetic-only research oracle. **Not** real coaching, original Engine 2.x production implementation, field validation, clinical guidance, plan mutation, source admission or Gate 2 approval.

## Implemented batches

### Batch 1 — Deterministic exposure accounting

`research/validation/p2c_exposure.py` accepts invented, scoped, versioned plan opportunities and source events with offset-aware `planned_at` / `observed_at` / `available_at` fields. It checks athlete and plan identities, event/plan compatibility, source IDs, explicit links, complete or partial dose (distance and duration), status, self-reported response and next-day follow-up.

- No upload is `unknown`, not a missed workout. A `confirmed_skipped` record requires an explicit reason and must have null numeric dose; completed zero and missing are distinct.
- Count only verified distinct linked activities. Exact same-ID reimports are idempotent; conflicting event IDs reject. Two separate asserted sources for one opportunity remain a `conflict` without explicit alias adjudication, not doubled distance.
- Future/delayed observations are invisible at decision time. A future unrelated event does not change the historical review category. A source event predating its available plan is a conflict. Invalid offset, negative/NaN/dose type, unsupported discipline and foreign athlete identity are rejected.
- Retain planned and observed status separately; unknown, conflict and unlinked observations prevent full-coverage claims. Summed kilometers, minutes and longest verified run are **known-only descriptive quantities**, not whole-week estimates or injury-risk scores.
- This prototype supports running, race and running-session types only. Strength, calisthenics and conditioning exposure remains outside scope for P2-E. One plan version is modeled per invented snapshot; historical migration and real source identity are unresolved.

### Batch 2 — Interruption-state review

`research/validation/p2c_progression.py` represents confirmed isolated misses, confirmed nonclinical absence, uncertain gaps, planned rest, race response, illness and injury separately. It applies clinical/restriction deferral before ordinary training-review categories. A reported clearance string is never treated as medical authorization. Unknown reason or incomplete exposure prevents a progression claim. A registered set of recent verified response event IDs is required to classify an observed difficult response; an old difficulty does not automatically become current.

### Batch 3 — Non-executable progression proposals

Research categories include `MAINTAIN_REVIEW`, `DEFER_RECONCILIATION`, `DEFER_CLINICAL_REVIEW`, `RACE_RECOVERY_REVIEW`, `REBUILD_EXPOSURE_REVIEW`, `REDUCE_OR_DELOAD_REVIEW`, `DEFER_SESSION_TYPE`, `RESTORE_LONG_RUN_REVIEW`, `RESTORE_QUALITY_REVIEW`, `DEFER_FOLLOWUP_UNVERIFIED` and `PROGRESS_ONE_DIMENSION_REVIEW`.

No categories specify an actual running dose, schedule, clinical clearance, return timeline, permissible session or automatic plan edit. A proposed restoration must reference an explicitly registered **synthetic** review protocol and same-session-kind, current-exposure-epoch, verified completed events with recorded tolerated workout and next-day response. Two distinct synthetic dates are required by this narrow test contract as a structural independence check, **not** as a medically/physiologically validated minimum. Old-epoch records, single-day duplicates, other session types or unreviewed claims cannot unlock restoration. Threshold versus intervals and long-run versus quality remain separate.

All results retain `dose_change=null`, `plan_change=null`, `applied=false`, `clinical_clearance=false`, `risk_probability=null`, `prediction_status=NOT_VALIDATED_NO_MODEL`, `coaching_authority=NONE_SYNTHETIC_RESEARCH`, `gate2=CLOSED`.

### Batch 4 — Synthetic adversarial verification

`research/validation/test_p2c_wave.py` contains **70 deterministic tests** for the above rules, source/athlete isolation, stale plan versions, backdating, future leakage, duplicate and changed sources, missing/partial/zero work, unsupported disciplines, unknown and clinical interruptions, race/planned-rest distinctions, current-response checking, separate restoration gates, old exposure epochs, identical-day samples, immutable output and session-concentration differences at equal weekly mileage.

**Hosted Python 3 result:** `python -m unittest discover -s . -p 'test_*.py' -v` executed on the three exact source files staged for this branch: **70 passed / 0 failed**. Before final publication, each of the three Git blob SHA-1 identifiers was checked against the independently computed SHA-1 of the local hosted-tested bytes. This is author-side testing, not an independent expert review or GitHub Actions CI. The pre-existing research suite and earlier P2-A tests were not rerun in this wave.

## Scientific/operational limits

The supplied `synthetic_only=true` flag and source IDs are fixtures, not a permission system; no real participant/provider/Garmin/FIT data are admitted. An asserted registered gate is not an independently proven real athlete tolerance benchmark. No numeric progression/deload cap, post-race day cutoff, return-to-run dose, injury prevention claim, clinical diagnosis, or efficacy estimate was derived. History can be incomplete or biased. P2-C E011/E012 real experiments and independent outcomes remain **NOT RUN**. The clinical categories are safety deferrals, not medical triage tools.

The frozen Session G architecture and its P2-F admission/linkage requirements remain controlling. P2-F real custody/authorization and relevant P2-B athlete repeatability, approved data, independent reviewers and a signed Gate 2 manifest are still missing. No change was made to frozen protocol, Engine 1.5, Trackside/Android, original research policies or PR #34.

## Checkpoint and next step

Author-side verdict: **`P2C_DEV0_BATCHES_1_TO_4_SYNTHETIC_IMPLEMENTED_HOSTED_TESTS_PASS`**.

Pending: separate independent P2-C code and research review; additional synthetic adversarial corrections if found; non-PC, exact-head CI if supported. Only after that should DEV-0 be independently accepted. Next independent research wave: P2-D shadow proposal constraints and decision provenance, without connecting P2-C's invented categories to automatic planning or claiming any beneficial training outcome.
