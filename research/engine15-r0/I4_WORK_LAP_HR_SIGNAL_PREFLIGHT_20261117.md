# Engine 1.5 I4 — Source-work-lap heart-rate signal review (preflight)

Frozen 2026-11-17 before implementation. Stacked on I3 PR #123, which the user validated in their Q3 checkout (`BUILD SUCCESSFUL in 24s`, selected I3/I2/I1/M3–M6/Q2/Q3/R0). I4 is **shadow-only**, not a threshold, tempo or athlete-readiness classifier.

## Actual source model
- `ActivityDetail` holds samples `(index, elapsed seconds, HR bpm?, pace?)`, selected laps `(index, startSeconds?, seconds?, hr?)`, and pause intervals. It holds optional `sourceHeartRateZones` but without authenticated athlete calibration, owner provenance or verified intensity labels.
- `IndependentQualitySourceBinding.inspect` reconciles caller-asserted quality lap indices to uniquely linked FIT/Garmin source records and lap durations while rejecting pause overlap and missing/invalid timing. I3 requires two distinct recent linked sessions and compares the lower work-time dose. It still explicitly leaves intensity, source-owner authentication, response and execution false.

## I4 review contract
1. Recompute I3 directly from the same typed M4B, single M5B request and I3 evidence input. An I3 deferral must result in an I4 deferral carrying the original reason; never bypass I3. No legacy Engine1 generation or mutation.
2. For each of exactly two I3-bound source sessions, inspect the original selected work lap windows in that matching ActivityDetail, not other elapsed segments or workout titles. Each selected lap must have finite positive start/duration, unique lap index and a matching activity ID. Do not interpolate across work-lap boundaries or timer pauses.
3. Require source HR sample streams to be ordered by unique sample index and *strictly* increasing elapsed time, with bounded reasonable sample counts. A pair contributes only if both endpoints have finite HR in 35..230 bpm, strictly positive gap <=30s, and the full pair lies within one selected work lap. Reject an invalid numeric HR sample in a selected lap instead of silently discarding it. Null HR or gaps lower measurable coverage.
4. Require at least 80% of EVERY selected work lap duration to be covered by valid adjacent sample pairs, not only by point count. Weighted work-lap mean HR uses pairwise trapezoids; source lap summary HR (if present) must agree within 12 bpm. Reject sparse, missing, impossible, nonmonotonic, duplicated or conflicting source HR.
5. Return per-work-lap and per-recording coverage, time-weighted descriptive mean HR and selected sample counts, with original I3 duration-consistency candidate preserved exactly. No comparison to max HR, LTHR, HRR, fixed zone or pace is authorized here. These thresholds are SYNTHETIC DATA-INTEGRITY HEURISTICS, not clinical or exercise recommendations.
6. Output flags remain `hrStreamReviewOnly=true`, `sourceOwnerAuthenticated=false`, `sensorCalibrationVerified=false`, `thresholdOrTempoIntensityVerified=false`, `athleteResponseVerified=false`, `doseAuthorized=false`, `distanceReconciled=false`, `executable=false`, `authorizedForProduction=false`. Passing signal coverage is not proof that the athlete executed threshold/tempo intensity or that the device/source is genuine.
7. Do not alter M4B budget, M5B timed preview, I3 lap bindings, M6 dates, production plan, Android, saved activity data, or Engine 1.0 fallback.

## Prespecified tests
- Two synthetic distinct FIT/Garmin sources with 240s+240s selected work laps and 10s samples at 150 bpm, every lap 100% coverage; candidate is descriptive only and all permission flags false.
- Missing HR throughout one lap, sparse readings with >30s gaps, nonmonotonic sample times, duplicate sample index, impossible HR (e.g., 280), mismatched lap-average HR (e.g., 110 vs 150), mismatched source detail and recorded pause overlap must defer.
- An I3 dose overstatement, duplicate source and unreviewed M5B quality must defer before any I4 interpretation.
- Deterministic evaluation, one recording cannot satisfy two, no unlabeled HR percentile promoted into a threshold prescription.

## Gate
Q3 Kotlin focused tests, plus I3/source binding/I2/I1/M3–M6/Q2/Q3/R0 regression. I4 outputs remain observational technical shadow data; I5 must separately address actual intensity-calibration provenance and timed workout distance feasibility. No production activation.
