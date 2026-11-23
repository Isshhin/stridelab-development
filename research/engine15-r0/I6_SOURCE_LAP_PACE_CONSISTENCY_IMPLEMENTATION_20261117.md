# Engine 1.5 I6 — Source-Linked Work-Lap Pace & Distance Consistency

2026-11-17. Branch `feature/engine15-i6-source-lap-pace-consistency-20261117`, stacked on I5 PR #125, following the user's successful selected I5/I4/I3/I2/I1/M3–M6/Q2/Q3/R0 regression (`BUILD SUCCESSFUL in 26s`). Scope frozen before implementation in `I6_SOURCE_LAP_PACE_CONSISTENCY_PREFLIGHT_20261117.md`.

## What changed
- `Engine15I6SourceWorkLapPaceConsistency.inspect` recomputes I5, which recomputes I4/I3/M5B and M6B from original independent M4B inputs. I6 never accepts a caller-supplied I5 Candidate as an authorization.
- For the same two separate linked FIT/Garmin source recordings and their exact I3-bound selected work laps, I6 requires positive finite recorded metres; compares duration/distance-derived lap pace with optional recorded lap pace; and requires selected work-lap *sample pace* time coverage >=80% using consecutive pairs with gaps <=30s.
- Invalid/nonfinite/extreme sample paces fail closed, rather than being silently excluded. Observed sample speeds and recorded lap distance/duration must agree within the prespecified 15% relative data-coherence bound.
- All historical recorded work-lap average speeds must fit at least one original I5 caller-asserted WORK-phase speed window (0.05m/s numeric tolerance). This is only a contradiction check; no new athlete-specific speed limits or threshold intensity are estimated. I5's mathematical distance envelope must already pass.
- The complete original typed M4B/M5B/M6B/I3/I4/I5 outputs are retained. No changes to planned EASY distance, original running calendar, sample storage, saved sessions, Android UI or live Engine 1.0/1.5 generator.
- All flags remain false: sourceOwnerAuthenticated, paceCalibrationVerified, qualityIntensityVerified, actualFutureSegmentDistanceVerified, athleteResponseVerified, doseAuthorized, executable, authorizedForProduction.

## Tests
12 synthetic I6 test methods: two linked source work-lap descriptions, missing selected lap metres, absent optional lap pace, contradictory lap summary, missing/60s-gap pace samples, numeric pace corruption, contradictory lap-vs-sample speed, uncorroborated WORK-phase speed envelope, partial >80% pace coverage, upstream I5/I4/I3/M5B deferrals, TEMPO family and deterministic unchanged mileage.

## Validation
**I6 has NOT been compiled or run in Q3.** User must fetch/merge I6 and run focused I6 with I5/I4/I3/source binding, I1/I2, M3–M6 and Q2/Q3/R0 selected suites. This is still a technical shadow integrity gate; does NOT verify real source ownership, sensor calibration, threshold/tempo intensity or coach eligibility. No production activation or Engine 1.0 retirement.
