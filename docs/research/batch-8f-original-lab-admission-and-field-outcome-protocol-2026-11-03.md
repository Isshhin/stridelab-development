# Batch 8F — Original lab source-admission gate and field-outcome evidence protocol

**Date:** November 3, 2026 (Asia/Manila). **Source of truth:** the private `Isshhin/stridelab-development` repository, research branch `research/runna-garmin-coaching-20261102`, [draft PR #34](https://github.com/Isshhin/stridelab-development/pull/34). **Status:** pre-integration gates and research protocol, not autonomous adaptation, authenticated athlete observations, or a release candidate.

## Executive decision

**Do not promote Engine 2.0 into the Android app.** Current cloud B0/H1 prototypes pass selected synthetic structural checks but neither is the user's original Python Athlete Simulation Lab. No controlled real-athlete dataset is available to establish a performance benefit. Add an explicit, auditable admission interface before running any newly supplied original source. Avoid accumulating more synthetic pass counts as a substitute for independent validity.

The original Python lab's reported local path `E:\Project\stridelab-copilot\stridelab-coaching-lab` and protected dataset `F:\StrideLab DataSets` are **prior handoff references, not accessible cloud paths**. The accessible connected GitHub repository list does not identify the independent lab as a separate repository, and available Library planning text is a design proposal rather than a verified executable checkout. **Do not conclude that the user's local files are absent.** No local PC, original lab source, private FIT, dataset or other protected file was read or modified in this batch.

## 1. Original-lab source admission

Implemented [original_lab_admission.py](../../research/validation/original_lab_admission.py), with [12 negative-control tests](../../research/validation/test_original_lab_admission.py).

The source handoff manifest schema is `stridelab-original-lab-source-handoff-v1` and specifies:
- `lab_id`: a claimed nonempty source identity;
- `source_commit`: a claimed 40-character source version SHA (not proof of GitHub authorship);
- `entrypoint_file`: a declared Python source file, listed with the same hash in `source_files`;
- `source_files`: relative allowlisted source paths and each file's SHA-256 digest;
- `fixture_manifest_sha256`: **only a hash** of the protected fixture manifest, not raw dataset content;
- `policy_ids`: explicitly declared baseline/candidate policy identifiers.

The admission program:
1. Returns `BLOCKED_SOURCE_NOT_PROVIDED` when no source checkout is supplied.
2. Rejects absent/invalid schema and hashes, duplicate file entries, source path escapes, symlinks, forbidden private-data folders and oversized files.
3. Checks allowlisted files beneath an explicitly selected root and verifies their individual SHA-256 digests **without importing or executing them**.
4. Returns `READY_FOR_MANUAL_ORIGIN_AND_INTERFACE_REVIEW` when and only when the declared files match their manifest, while **always keeping `origin_verified=false` and `execution=NOT_PERFORMED`**.
5. Returns `REJECTED_SOURCE_HANDOFF` with a reason when supplied files or metadata fail checks.

**Security boundary:** A self-produced manifest and matching hashes can be fabricated. They establish content consistency **not** source identity, authorization, safety or historical continuity with the original lab. A separate authenticated human review of the source repo, policy implementations, read-only dataset manifest, file provenance and runner interface remains mandatory before any lab execution. Do not run untrusted source merely because SHA-256 matches its own manifest.

### Required handoff location and command

If the owner later chooses to provide only the lab's **source code**, it should be delivered through an authorized private repository or approved source package **without** `F:\StrideLab DataSets`, recordings, secrets or private FIT files. The separate source checkout may be examined read-only. Do not copy the live athlete dataset to a public or private GitHub Actions runner by default.

A future authorized checkout would be checked with:

```bash
python research/validation/original_lab_admission.py --source-root /path/to/approved/source --output research-original-lab-admission.json
```

**Current CI intentionally omits `--source-root`** so a GitHub Action does not pretend the unavailable Windows folder exists. Current admission status is **blocked**, not passed.

## 2. Policy interface contract for a real adapter

After verified source provenance and explicit consent to inspect the lab code, an adapter should accept a **versioned, immutable** synthetic research state:
- `case_id` and frozen `fixture_sha256`;
- `athlete_pseudonym` and separately identified discipline, training phase and verified repeatable volume capacity;
- `plan_version`, planned workout **step tree** (easy/hard/recovery), next sessions, availability and per-discipline constraints;
- `observed_event_ids`, completed/partial/skipped/unplanned/unknown state, source timestamps and confidence;
- `policy_version`, exact source inventory digest and deterministic seed.

The adapter should emit:
- `policy_version`, candidate action, explanation and uncertainty;
- proposed before/after workout steps and calendar placement, **never an applied revision**;
- specific dose deltas in hard-work seconds, easy minutes, recovery time, long-run kilometers, strength sets/RIR, calisthenics prerequisites and conditioning time/impact;
- source IDs, observation quality, missing/unknown inputs, consent gate, plan version and exact policy/fixture provenance.

A real policy **cannot** be marked verified just because it returns data in this format. First run the exact original baseline unchanged and independently confirm it produces reproducible outputs on frozen test cases. Then compare genuinely separate candidates without letting the proposed policy also define the evaluator's expected decision. Preserve failures, policy disagreement and unknowns. Do not use B0/H1 cloud prototypes as substitutes for the source-of-truth local policies.

## 3. Real-world easy aerobic outcome protocol (not executed)

Implemented [field_outcome_gate.py](../../research/validation/field_outcome_gate.py), with [11 negative-control tests](../../research/validation/test_field_outcome_gate.py). Without approved metadata, it returns `BLOCKED_NO_CONSENTED_FIELD_DATA`; it never loads FIT/GPX/TCX, private activities or actual GPS segments in GitHub CI.

**Candidate preregistered primary endpoint:** change in **running speed in meters per second at genuinely comparable easy heart rate**, not a device VO₂max estimate or aggregate pace across dissimilar routes. Speed may be converted to min/km for reporting. This is a candidate observational measure of aerobic efficiency, **not a full measurement of running economy or proof of physiological adaptation**.

Before inspecting results, freeze the comparison protocol and its exclusions with an independent reviewer:
- Use a repeated, relatively flat, accurately measured **standard road segment**, preferably identical route and direction. Tight loop or GPS-degraded data are excluded from primary pace comparisons; keep hills separately characterized.
- Define a steady-state easy segment after warm-up; distinguish moving vs elapsed time, pauses and walking rests. Do not label total session distance as threshold quality work.
- Use a consistent HR sensor/method and compatible device settings; record HR confidence, sensor loss, environmental temperature, humidity, route grade, surface, time of day, prior strenuous activity and fatigue as available.
- Predeclare practical tolerances for **HR matching, temperature/humidity and grade** *before* viewing the outcome. Example research matching windows (not clinical thresholds): average HR within 3 bpm, temperature within 3 °C, humidity within 10 percentage points, and same road route/direction. If insufficient comparisons meet the frozen quality/matching rules, report `INSUFFICIENT_MATCHED_RUNS`, not an improvement.
- Obtain several comparable baseline and follow-up runs (a provisional minimum of **three per period** for exploring variability, not statistical power) separated in time. Prefer more and repeat under varied conditions. Keep exclusions and unmatched observations in the audit log.
- Define change in paired/matched speed, interval variability and sensitivity to excluded hot/hilly/sensor-poor runs. Avoid assuming a causal treatment effect or predicting injury risk from an observational single-athlete series.
- Secondary measures can include sustained threshold **work-step** pace/duration at supported effort, long-run completion, adherence, actual-vs-planned dose and per-discipline progression. Do not conflate workout tolerance with current racing speed or baseline training capacity.

**No actual athlete measures or paired values were used in this batch.** Do not infer gains from any existing private run history without reading authorized complete observations and quality metadata.

### Field metadata admission contract

A future *metadata-only* handoff, not the recordings themselves, can supply `stridelab-field-outcome-handoff-v1`, protocol ID, pseudonym, dataset manifest digest, measurement source, non-overlapping baseline/follow-up dates, primary endpoint `easy_speed_at_comparable_hr`, and external consent/quality/sampling review status.

The gate explicitly does **not authenticate** self-asserted reviews. Even if a metadata producer labels all reviews `externally_verified`, the maximum status is `READY_FOR_INDEPENDENT_HUMAN_PROVENANCE_REVIEW`, **not** `PERFORMANCE_VALIDATED`. It never calculates or asserts any change in speed and never concludes clinical or training effectiveness.

## 4. Release and safety gates still open

| Gate | Batch 8F status | Requirement before completion |
| --- | --- | --- |
| Original lab source inventory / hashes | **BLOCKED_SOURCE_NOT_PROVIDED** | Authorized source + claimed manifest, independent identity audit, no protected data upload |
| Actual local lab policy execution | **NOT_RUN** | Review source, real policy versions and runner, independent frozen fixtures, exact reproducible output |
| Android Engine 2.0 integration | **OUT_OF_SCOPE** | Separate product decision, integration tests, versioned approval and offline rollback |
| Real athlete performance outcome | **BLOCKED_NO_CONSENTED_FIELD_DATA** | Opt-in, authenticated measured data, predeclared matching/quality, temporal follow-up and uncertainty |
| Runna E002 current app generator | **0/16** | Authorized authenticated controlled app observations |
| Garmin Run Coach paired captures | **No verified pairs** | Supported before/after outputs with correct product/firmware/sync provenance |

**Decision:** Research-only source and outcome admission gates are ready for review, but Engine 2.0 **must not be promoted**. Research passing tests are necessary engineering checks, not a demonstration that workouts improve fitness or prevent illness/injury.

## 5. Execution and evidence

The dedicated GitHub Actions workflow runs the ordinary research suites plus **two extra report-only steps**:

```bash
python research/validation/original_lab_admission.py --output research-original-lab-admission.json
python research/validation/field_outcome_gate.py --output research-field-outcome-readiness.json
```

On the first full 8F CI checkpoint, unit/negative-control tests passed **132/132**, the older research B0/H1 cases stayed at **55 structural PASS, 3 predictive BLOCKED**, 288 seeded adversarial cases passed, and the prior multiweek profiles passed. The new reports clearly read **`BLOCKED_SOURCE_NOT_PROVIDED`** and **`BLOCKED_NO_CONSENTED_FIELD_DATA`**, respectively. The Actions artifact `research-validation-results` now includes both readiness JSONs; no user athlete data or original source artifact is uploaded.

The lab source admission and field-outcome protocol are different gates. Passing hash checks cannot establish coaching efficacy; having longitudinal outcome data cannot establish that an untrusted lab source is safe to execute. No automatic consent or approval is implemented.

## 6. Next checkpoint

**Batch 8G should be evidence-led, not another synthetic counter factory.** If an authorized original lab source checkout becomes available, inspect the actual policy/runner and its manifest, review provenance, and connect an adapter *without editing the original protected files*. Otherwise continue by reviewing the external field protocol and potential trusted source integrations, and keep the source and real-world efficacy statuses blocked. Preserve research PR #34 **draft and unmerged**, no production Engine 1.0/1.5 / Trackside PR #36 changes, and leave the existing daily 09:00 Manila Runna/Garmin evidence automation unchanged.
