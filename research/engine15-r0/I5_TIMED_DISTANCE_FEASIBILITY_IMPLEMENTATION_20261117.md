# Engine 1.5 I5 — Timed Workout Distance Feasibility (Shadow Implementation)

2026-11-17. Branch `feature/engine15-i5-timed-distance-feasibility-20261117`, stacked on I4 PR #124. Source scope frozen in `I5_TIMED_DISTANCE_FEASIBILITY_PREFLIGHT_20261117.md`.

## Implementation
- New `Engine15I5TimedDistanceFeasibility.review(M4B.Result, single M5B request, I3 evidence, I5 input)` recomputes I4's original two-recording HR-signal/quality-duration source integrity *and* M6B's independent quality opportunity/calendar. Both must be candidates; any deferral propagates.
- The identical M5B preview is required across the I4 and M6B branches. The existing M5A EASY allocation remains the only mileage session; neither timed workout nor replacement slot is activated.
- The I5 input contains one explicitly asserted min/max metres-per-second interval for EACH original chronologically expanded M5B timed phase (warm-up, work, between-repetition recovery, cooldown). Each interval also identifies original phase index/purpose/target; no defaults or inference from HR, Garmin summary, I3 lap duration, or M5C arithmetic.
- Per-phase min and max mathematical distance are computed as `seconds × asserted m/s`; summed range must contain the original EASY slot distance within 0.01 metres. If original budget is too high or too low, defer with `ALLOCATION_ABOVE_ASSERTED_MAXIMUM` or `ALLOCATION_BELOW_ASSERTED_MINIMUM`. Never add/remove sessions, change the budget, or stretch workout time.
- Speed input is bounded to strictly positive finite min<=max<=15 m/s for numeric sanity only; this does not certify any athlete can sustain those speeds. Caller must provide a bounded nonblank external speed-review reference; reference/intervals are UNAUTHENTICATED assertions.
- Candidate retains untouched original I4 and M6B outputs and original selected quality opportunity; returned phase distances are only possible bounds, not prescribed speeds or actual segment distance observations.
- `boundsExternallyAsserted=true`. Pace calibration, workout intensity, source and actual segment distances, athlete response, dose authorization, executability and production approval remain FALSE.
- No production Engine1/Engine15 generation, Android or persisted-plan code changed.

## Synthetic test methods
Eleven focused tests: mathematically feasible 30→35 km four-day original slot; 75→80 km six-day short quality not fitting supplied speed maximum; minimum distance exceeding slot; absent external review / incorrect phase count/order/role; invalid/NaN/Infinity/zero/reversed/extreme ranges; too-slow envelope; upstream I4 missing HR/detail; M6B blocked long/quality spacing; M5B unreviewed/recovery holds; tempo family with matching asserted source family; deterministic identity/mileage preservation.

## Validation status and next admission gate
**Not yet run locally**. Q3 focused tests and I4/I3/source-binding/I1/I2/M3/M4/M5/M6/Q2/Q3/R0 regression required. This is a shadow mathematical possibility test only; even a passing result cannot prove authentic athlete-specific pace, threshold intensity, physiological capacity or workout execution. Future engineering must bridge authenticated source pace/distance observations and approved dose into a production-eligible, properly versioned running plan without removing Engine 1.0 prematurely.
