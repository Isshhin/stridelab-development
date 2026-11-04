# Engine 1.5 Batch B — bounded running prescriptions

Development branch: `feature/engine15-versioned-foundation-20261103`
Draft review: https://github.com/Isshhin/stridelab-development/pull/37
This is NOT a released or user-selectable coaching policy.

## Verified Engine 1.0 components retained

The existing Engine 1.0 generator supplies all dates, multi-discipline
scheduling, strength and calisthenics recipes, equipment checks, plan
snapshots, user approval, workout logs, and offline storage. Engine 1.5
operates on new draft RUNNING prescriptions only. Every existing saved
plan, performed workout, and source activity remains unchanged.

## Provisional Engine 1.5 rules

- Conservative week-specific mileage ceilings based on *user-supplied*
  recent weekly distance, not a model-derived readiness estimate.
  Three staged loading weeks are followed by a lower fourth week;
  ceilings never exceed 110% of the supplied reference. Existing time
  and longest-run constraints can reduce planned volume further.
- No catch-up distance: a removed distance is not redistributed to
  other sessions, and no individual run distance or duration increases
  relative to Engine 1.0.
- When at least three weekly runs are present, the long run is capped
  at 40% of final scheduled weekly distance, with the existing longest
  distance bounds retained.
- Before quality: require at least 20 km/week of reported recent
  distance and non-beginner experience. Two quality sessions require
  at least 40 km/week. Quality is replaced by easy running in every
  fourth week, in the first three return-from-break weeks, and in the
  final two weeks of longer half-marathon/marathon plans.
- Controlled effort structures by goal: 5K (3 x 3 min), 10K (2 x 6),
  half-marathon (2 x 8 steady), marathon (2 x 8 easy/steady), with
  warm-up and cool-down and no inferred race pace or heart-rate target.
- If data is missing, preserve Engine 1.0's easy time-based run/walk
  instead of guessing recent mileage or fitness.

## Immutable policy compatibility

`stridelab.engine15.bounded-running-20261103.1` is the new policy ID
within version `1.5.0`. Previous foundation snapshots are still
recognized under `stridelab.engine15.foundation-20261103.1`.
Engine 1.0 policies and the plan JSON schema are unchanged.

## Evidence limitations and release gates

Every rule above is a conservative **engineering assumption**, not
a validated exercise-science prescription, individualized training
response, or guarantee against injury. Synthetic tests establish
contract integrity and behavior only. Keep PR #37 draft until the
Android unit suite, sample matrix, saved-data regression checks,
completion-feedback Batch C, emulator/device checks, and independent
Trackside integration are reviewed. Do not merge the visual revamp
or copy data from the separate Python Coaching Research Lab.
