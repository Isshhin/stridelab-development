# P2-B DEV-0 report

Status: `PIPELINE_FEASIBLE_SYNTHETICALLY`; no longitudinal public repeatability evidence is claimed.

## Scope

This additive, standard-library pipeline asks whether repeated fixed running windows can be extracted and compared without unstable pairing, silent missingness, duplicate inflation or endpoint leakage. It is not LT1, Zone 2, fitness, coaching-effectiveness, progression or Gate 2 evidence. FitRec/Endomondo was rejected by the existing registry for academic-only, non-commercial and non-redistribution terms and was not accessed, downloaded, derived from or used.

## Pipeline

`research/p2b_dev0/pipeline.py` defines canonical activities/samples, fixed windows beginning at elapsed 12:00 (1200 s primary, 900 s sensitivity), strict monotonic timestamps, coverage/QC checks, capped 5-second previous-value time weighting, explicit speed method, frozen route tolerances, chronological disjoint pairing, descriptive bias/SD/TE/agreement limits, explicit athlete states and sorted JSON reporting. Speed is excluded from route grouping and pair membership.

The synthetic matrix has 16 input records: healthy repeated easy series (6), HR mismatch (3), route mismatch (3), trace-quality failures (3), and missing-GPS unsupported measurement (1), plus an exact duplicate import. The healthy series yields 3 independent pairs with known signed differences 0.01 m/s and expected TE 0.0 m/s; the report is deterministic on replay.

Adversarial coverage includes deleted HR, 30-second gap/pause, reversed timestamp/identity/route-direction cases through the QC/grouping contract, impossible speed, +15 bpm shift, missing GPS, duplicate identity and changed source identity. Endpoint blinding materially changes speed values while preserving pair membership exactly.

## 4TU support

4TU v1 (DOI `10.4121/efa64223-ba51-48fa-91be-53f0e48460b4.v1`) is now `RESEARCH_ONLY_ISOLATED` under the source registry. The adapter supports bounded TCX XML trackpoint extraction for timing, HR and GPS; no raw files were ingested in this run, so actual-data mode is `NOT_RUN`. Its endurance, interval and 5 km sessions are not equivalent easy repeats and must not produce B0 comparable-easy TE.

## Verdict and limits

`P2B_DEV0_PUBLIC_REPEATABILITY_PIPELINE_FEASIBLE` for synthetic software qualification. `LONGITUDINAL_PUBLIC_REPEATABILITY_EVIDENCE_UNAVAILABLE` remains the real-data status. No private StrideLab data, athlete data, product code, custody protocol, Engine 1.5, Trackside, Android UI, APK infrastructure or frozen Session G code was used or changed. No coaching authority follows.
