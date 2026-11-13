# Garmin Run Coach September 2026 plan-family expansion (2026-11-05)

**Evidence:** Garmin primary-source announcement, vendor-documented feature availability; not an observed generated plan, internal rule, or numerical progression threshold.

## Source and dated claim

Garmin, “Garmin rolls out new feature updates for select smartwatches and cycling computers,” **2026-09-24**, accessed **2026-11-05**:
https://www.garmin.com/en-AU/blog/garmin-rolls-out-new-feature-updates-for-select-smartwatches-and-cycling-computers/

Garmin reports **new Garmin Run Coach plans incorporating run/walk workouts and lower-volume training**, with daily adaptation based on health and recovery metrics. Rollout was described for selected compatible watches, including fēnix 8 series, Forerunner 570/970, Venu 4 and vívoactive 6; the article does **not** establish that every listed device receives every feature or that rollout is universal. Separately, Garmin announces **Quick workouts**, generated from desired time and intensity; do not treat Quick workouts as Run Coach prescriptions without direct evidence.

Prior Garmin Run Coach introduction (2024-09-19): https://www.garmin.com/en-US/newsroom/press-release/wearables-health/train-smarter-with-personalized-garmin-coach-training-plans-that-can-adapt-to-help-you-reach-your-goals/

Garmin's separate DSW explanation (2026-08-05): https://www.garmin.com/en-US/blog/fitness/daily-workout-suggestions-for-runners/

## What is and is not established

- **DOCUMENTED:** Garmin publicly describes new lower-volume and run/walk Run Coach plan options by September 2026.
- **UNKNOWN:** eligibility thresholds, whether the options are user-selected or automatically assigned, run/walk interval construction, maximum/minimum weekly mileage, progression, supported device/firmware combinations, and any interaction with recovery metrics.
- **NOT OBSERVED:** no authenticated Garmin Connect plan output, pre-workout prescription snapshot, or longitudinal recommendation comparison.
- **DISTINCTION:** Run Coach, DSW, named Expert Coach plans and Quick workouts are different product surfaces; do not transfer evidence across them.

## Reproducible observation protocol

On an eligible device, record model, firmware, Garmin Connect version, date/time, exact plan label, available plan modes, goal, run days, current training history, and recovery context. Capture each displayed prescription **before** exercise. For each week, compute `total_km = sum(prescribed_run_km)` where distances are known; mark duration-only prescriptions `DISTANCE_UNKNOWN`. Compute `run_walk_work_fraction = sum(run_minutes)/(sum(run_minutes)+sum(walk_minutes))` only when all segment durations are visible. Record missing fields rather than guessing. Compare matched inputs across lower-volume versus ordinary plan modes only when both are actually available; never interpret a difference as a causal effect without controlling version, device and context.

## StrideLab implications

**Engine 1.5:** No production change; preserve conservative deterministic safeguards and version freeze.

**Engine 2.0:** Add an experimental dimension for run/walk and lower-volume plan families, especially interruption/re-entry and lower-tolerance profiles. For a 60–70 km/week athlete seeking improved easy/Zone 2 speed, this announcement provides **no evidence** that lower-volume mode improves aerobic efficiency or should be applied to high-mileage training. Prioritize matched-HR longitudinal data and controlled plan observations.

**Uncertainty:** No published plan output or independent corroboration of actual selection/progression logic obtained in this review.
