# P2-B DEV-0 report

Status: `P2B_DEV0_PIPELINE_CORRECTED_SYNTHETICALLY`; previous review found bounded defects. No longitudinal public repeatability evidence, P2-B activation, or Gate 2 readiness is claimed.

## Scope

This additive, standard-library pipeline asks whether repeated fixed running windows can be extracted and compared without unstable pairing, silent missingness, duplicate inflation or endpoint leakage. It is not LT1, Zone 2, fitness, coaching-effectiveness, progression or Gate 2 evidence. FitRec/Endomondo was rejected by the existing registry for academic-only, non-commercial and non-redistribution terms and was not accessed, downloaded, derived from or used.

## Pipeline

`research/p2b_dev0/pipeline.py` defines scoped canonical identities `(source_dataset, athlete_id, activity_id)`, deterministic fixed windows beginning at elapsed 720 s, strict timestamp/coverage/QC checks, capped 5-second time weighting, chronological disjoint pairing, descriptive statistics, and sorted JSON replay. Route groups compare start/end proximity, direction, path-length fraction, and nine-point ordered normalized geometry; STRICT is endpoint/shape `.0008`, path fraction `.03`; MODERATE `.002`, `.20`; LOOSE `.005`, `.30`. Speed is excluded from route grouping and pair membership.

The primary configuration is 1200 s / start 720 s / HR ±3 bpm / STRICT. Named sensitivities are 900 s, HR ±2 bpm, HR ±5 bpm, MODERATE, and LOOSE. The report exposes raw input, canonical candidates, duplicates, fixed-window, QC, route-compatible, HR-compatible, final-pair, and estimability/state fields. Exact scoped duplicates are counted and deduplicated; conflicting content for one scoped identity raises explicitly. Same activity IDs under different athletes or sources remain independent.

The synthetic matrix has 16 input records: healthy repeated easy series (6), HR mismatch (3), route mismatch (3), trace-quality failures (3), and missing-GPS unsupported measurement (1), plus an exact duplicate import. A stronger deterministic fixture yields pair differences `+0.06, -0.06, +0.04` m/s, signed bias `0.013333`, sample SD `0.064291`, TE `0.045461`, and 95% limits `[-0.112677, 0.139344]`. The report is deterministic on replay and endpoint blinding preserves pair membership.

Adversarial coverage includes deleted HR, 30-second gap/pause, reversed timestamp/identity/route-direction cases through the QC/grouping contract, impossible speed, +15 bpm shift, missing GPS, duplicate identity and changed source identity. Endpoint blinding materially changes speed values while preserving pair membership exactly.

## 4TU support

4TU v1 (DOI `10.4121/efa64223-ba51-48fa-91be-53f0e48460b4.v1`) is now `RESEARCH_ONLY_ISOLATED` under the source registry. The adapter supports bounded TCX XML trackpoint extraction for timing, HR and GPS; no raw files were ingested in this run, so actual-data mode is `NOT_RUN`. Its endurance, interval and 5 km sessions are not equivalent easy repeats and must not produce B0 comparable-easy TE.

## Verdict and limits

`P2B_DEV0_PUBLIC_REPEATABILITY_PIPELINE_FEASIBLE` for synthetic software qualification. `LONGITUDINAL_PUBLIC_REPEATABILITY_EVIDENCE_UNAVAILABLE` remains the real-data status. No private StrideLab data, athlete data, product code, custody protocol, Engine 1.5, Trackside, Android UI, APK infrastructure or frozen Session G code was used or changed. No coaching authority follows.
