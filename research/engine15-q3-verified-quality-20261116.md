# Engine 1.5 Q3 — verified quality-work evidence (2026-11-16)

## Scope

Q3 is stacked on the Q2 + Android QA branch `fix/engine15-q2-instrumentation-20261116` (PR #105). It is **not** a standalone phone release. Q2 mixed-unit support-step bounds remain active. Q4 guarded workout progression and Q5 end-to-end acceptance remain separate.

## What changes

- A new draft may use explicitly recorded `WorkoutLog.qualityWorkKm` to bound its quality-work dose **only for the same recorded workout family**: Threshold, Tempo, Intervals, Hills, Fartlek, Progression.
- Evidence requires an approved/manageable plan belonging to the current owner; a completed quality session with a unique ID/source link, a positive log revision, consistent performed date within two days of its scheduled date, a nonfuture workout log, recorded 6–7/10 effort, `As planned` difficulty, `None` discomfort, and finite logged quality-work distance no greater than logged actual total distance.
- A family must have evidence in at least **two different calendar weeks**, separated by at least seven days, with the most recent report no older than fourteen days. Observations older than 27 days and one-week clusters cannot establish a work dose.
- Each week supplies one median observed work value; the per-family median sets a **per-session ceiling**, not a target to increase toward. The median of qualified logged weekly work totals additionally bounds the **combined quality-work dose across the new week**; a single historically tolerated session cannot be multiplied into two harder sessions. Unsupported families retain the prior conservative Q2 dose. Missing measurements, accepted adjustments, duplicate links, stale/future logs and painful or difficult sessions do not qualify.
- The existing quality generator still checks the requested quality frequency, schedule spacing, workout duration, weekly work-share and long-run-share ceilings. It never reallocates missing distance to cool-downs. Historical version-1 prescriptions remain unchanged.
- Plan explanations disclose evidence IDs and why this is **not** a measured threshold, pace recommendation, recovery score or automatic progression.

## Acceptance checks

The focused JVM `Engine15Q3VerifiedQualityHistoryTest` checks (1) qualified threshold-work dose against unqualified Q2, (2) family separation and interval evidence, (3) stale/single-week/pain/accepted-adjustment/future-log rejection, (4) account and duplicate-source isolation, (5) no expansion of individual hill or 5K interval lengths, (6) no multiplication of one qualified weekly dose across two requested quality days, and (7) Q2 timed warm-up/cool-down, distance-estimate, 20% work-share, and long-run limits. Previous Q2 contracts are unchanged.

**Execution status:** source and tests committed; focused local Gradle execution and emulator visual inspection have not yet run. Do not treat this as measured athletic efficacy or authorize a phone installation before acceptance.
