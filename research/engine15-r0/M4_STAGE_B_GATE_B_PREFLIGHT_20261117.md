# M4 Stage B — Gate B preflight: independent running-volume candidate

**Frozen on 2026-11-17 before Stage B code or results. Status: PROPOSED, not approved for production.**

## Scope

Build a **shadow-only independent** weekly volume candidate. Inputs: M3 typed athlete baseline, target, number of weeks, running frequency, returning flag, explicit recovery hold. The candidate MUST NOT invoke Engine1, CoordinatedEngine1, MultiSessionEngine1 or the current Engine15 generator. It must NOT be wired to app plan generation/approval. Stage A, historical plans, Q2/Q3, and fallback routing remain untouched until independent workout/scheduling acceptance at M5/M6/M9. Eventually there will be no Engine 1.0 running-plan fallback; no early retirement before there is a fully validated replacement.

All ceilings are *candidate planning bounds*, not athlete outcomes, prescriptions or fitness measurements. Retain the existing Engine 1.5 progression ceiling formula. Retain the **old source policy** for per-session maximum using reported longest + 1 km, with LONG weight 1.0 and conservative other-run weight 0.8, but do not invent workout types before M5. For missing longest, flag unknown session exposure and do not describe the budget as schedulable. Preserve the existing return-from-break **0.6 × min(1.2, 1+0.05 × weekIndex)** bound as a single independent cap without invoking Engine 1.0. Preserve deloads: every fourth week is <=80% of preceding planned volume and <= existing Engine 1.5 recovery ceiling. No catch-up.

Only logged complete-four-week reference may be labeled complete. An explicitly reported typical week may be used provisionally; no Garmin import required. Legacy unclassified positive mileage, unknown/peak-only without verified usual average, and contradictory input must NOT silently become a certified numeric training baseline. Confidence is not a numeric multiplier or proof of performance. Provisional and returning profiles require an explicit first-two-week future review marker.

## Prespecified expected numeric candidate results (km, 100 m flooring)

A. Experienced *self-reported average* 75, peak 85, longest 20, target 80, six days, eight weeks:
- **67.5, 71.2, 75.0, 60.0, 71.2, 75.0, 78.7, 62.9 km** (last week is 80% of 78.7 after floor, not 63.0). Peak 78.7, shortfall 1.3 km before Q2; do not claim 80 km reached.
- Compared with R0 **67.5, 71.2, 75.0, 54.1, 71.2, 75.0, 78.7, 56.4 km** pre-Q2, week 4 and week 8 differences arise from removing Q2 *estimated* week distance as an upstream recovery-budget input, not proof of improved safety.
- Note: no Q2 timed-workout composition has happened; none of these figures can be claimed as final running distance.

B. Large target jump 50→80, six days, longest 16, four weeks: **45.0, 47.5, 50.0, 40.0**. Peak 50.0, shortfall 30.0; no false 80 km claim.

C. Developing 30→35, four days, longest 10, eight weeks: initial **27.0, 28.5, 30.0, 24.0**; week 7 **31.5**; target shortfall >=3.5.

D. Beginner explicit 0→20: TIME_ONLY; all distance values null, never zero or 20 as an invented delivered prescription.

E. Returning 40→50, four days, longest 12: first week <=24.0; no hidden target catch-up; first-two-week review required.

F. Three-day 75→80, longest 20: session-exposure **upper-bound** = (20+1) + 2×(20+1)×0.8 = 54.6 km; no proposed week may exceed 54.6. This is NOT a guarantee a safe 54.6 km schedule exists; M5 must check actual easy/recovery roles.

G. Unknown average or peak-only without verified complete history: NEEDS_BASELINE, numeric proposal null. Legacy positive unclassified: NEEDS_CONFIRMATION, numeric proposal null until user confirms average. Contradicted peak below reported average: NEEDS_REVIEW, numeric proposal null.

H. Explicit recovery hold: RECOVERY_REVIEW; no numeric proposal.

## Pass/fail

1. Standalone Stage B class never invokes Engine1.generate, Engine15.generate, CoordinatedEngine1, MultiSessionEngine1, PlanSnapshot or old baseline sessions. It may use only the M3 typed capacity and the standalone M4 Stage A ceiling function.
2. Deterministic 100 m flooring, no negative/NaN outputs, no undocumented increase above existing Engine 1.5 ceiling, return cap, or conservative session cap.
3. Every week: target comparison reports an honest positive gap when target is not reached. Recovery weeks <= 0.8 × previous proposed week (subject to 100 m floor). No post-recovery catch-up.
4. Missing data/peak-only/explicit zero/recovery hold remain separate, with null planned distance except explicit time-only status. Self-report usable as *provisional*, not certified.
5. M4 Stage A, Q2/Q3 and existing generated sessions unchanged: no production path calls the Stage B module; R0/M3/M4 Stage A focused tests continue passing.
6. No runtime fallback removal, plan-schema mutation, telemetry, user records or approval-gate change.

## Decision authority

Stage B is **shadow-only until** its new focused test passes, Gate B compares all seven R0 profiles, and independent M5/M6 scheduling/composition can actually satisfy the proposed volume. Any change to the current 1.5 progression formula or numerical safety constraints needs its own predeclared alternative and review. Stage B outputs are not release evidence.
