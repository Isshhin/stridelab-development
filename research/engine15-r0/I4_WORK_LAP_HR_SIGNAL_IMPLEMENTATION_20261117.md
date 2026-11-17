# Engine 1.5 I4 — Work-lap HR signal review implementation

2026-11-17. Branch `feature/engine15-i4-work-lap-hr-signal-20261117`, stacked on I3 PR #123. Original scope frozen in `I4_WORK_LAP_HR_SIGNAL_PREFLIGHT_20261117.md`.

## Delivered
- `Engine15I4WorkLapHeartRateReview.inspect(M4B.Result, M5B quality request, I3 input)` independently reruns I3's two source-bound FIT/Garmin lap-duration consistency checks. It cannot accept an I3 failure.
- For each I3-linked recording and each caller-asserted selected work-lap window, it checks the retained `ActivityDetail.samples` stream for increasing index/elapsed time, HR values in bounded descriptive range, continuous adjacent sample pairs no more than 30 seconds apart, and a time-weighted mean HR.
- Every individual selected work lap needs >=80% of its elapsed window covered by valid, bounded consecutive HR-sample pairs; null readings and sample gaps do not count as covered and are never interpolated. Any invalid/nonfinite work-lap HR reading rejects that observation.
- Optional recorded lap summary mean HR is compared to the time-weighted sample estimate with a maximum 12 bpm discrepancy. If source lap mean is absent, the result explicitly records that this cross-check was not performed.
- Two original I3 source bindings, original M5B preview and original multiweek volume are untouched. Outputs contain per-lap and per-activity descriptive mean HR and coverage only. No pace, threshold boundary, HRR or maxHR comparison, physiological intensity labeling, readiness or exercise prescription.
- All flags remain false: `sourceOwnerAuthenticated`, `sensorCalibrationVerified`, `thresholdOrTempoIntensityVerified`, `athleteResponseVerified`, `doseAuthorized`, `distanceReconciled`, `executable`, `authorizedForProduction`.

## Focused tests
Ten tests cover: two full simulated source streams; a missing selected work-lap HR stream; only 60-second HR intervals; duplicate/retrograde sample indices/timestamps; nonfinite or implausible HR; conflicting optional lap mean; >80% partial window coverage; missing detail or pause overlap in I3; missing review, overstated dose, and duplicate source guards; deterministic replay.

## Gate and limits
Source/test/docs are committed; **the new I4 tests have not been executed**. Q3 validation checkout must fetch/merge I4 and run `Engine15I4WorkLapHeartRateReviewTest`, `Engine15I3SourceLinkedQualityDurationTest`, `IndependentQualitySourceBindingTest`, I1/I2 and M3–M6/Q2/Q3/R0 regression. A passing gate would qualify only the data-integrity check on synthetic HR windows; it does not verify real device origin, sensor calibration, threshold intensity, lap family or dose authority.

Future work: independent FIT/Garmin provenance and authenticated athlete-specific threshold/intensity calibration with error handling; time/pace/distance feasibility, full executable plan generation and Android acceptance. Engine 1.0 production path remains unchanged.
