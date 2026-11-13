# Runna foundation-plan dual progression guard and deload dose — 2026-11-04

## Source and evidence classification
Runna, *How Does Runna Reduce the Risk of Injury and Overtraining?*, authored Ben, dated **2026-08-31**, accessed **2026-11-04**: https://support.runna.com/en/articles/15279082-how-does-runna-reduce-the-risk-of-injury-and-overtraining

**Vendor-documented policy; not independently observed generated output or a validated physiological formula.**

## Incremental details relative to the existing research
1. For **New to Running, Return to Running and Path to parkrun** plans, Runna describes a **dual weekly progression ceiling**: the smaller of a fixed absolute increment and a multiple of the athlete's current best. Neither the increment nor multiplier nor the precise definition of 'current best' is published. Do not generalize this specific mechanism to advanced/high-mileage plans.
2. Runna says interval, tempo and hill sessions are reduced **more strongly than easy runs during deloads**. This is a relative-dose rule, not a disclosed numeric ratio.
3. Runna says its **Not Feeling 100%** adjustment pauses both mileage and pace progression and caps short easy-session distances by ability level during the adjustment period.
4. In non-distance plans at the mileage ceiling, Runna describes **gentle week-to-week variation** rather than a perfectly flat plateau. The size and cadence are unknown.

The separate long-run build cap, high-frequency conservative build, and interruption safeguards are already documented in the research; do not count them as new findings.

## Reproducible arithmetic — illustrative, NOT Runna parameter values
Let baseline exposure B = 20 arbitrary load units, absolute increment A = 3 units, and relative multiplier M = 1.10. A hypothetical ceiling would be min(B + A, M × B) = min(23,22) = **22 units**. At B = 50, the same hypothetical parameters give min(53,55) = **53 units**. This illustrates the switching behavior only; it is NOT a recovered Runna rule.

## StrideLab implications
- **Engine 1.5:** no changes; preserve frozen, deterministic progression, deload, and athlete-approval logic.
- **Engine 2.x hypothesis:** test dual absolute/relative exposure ceilings against single-rule ceilings for beginners/returners; separately model quality-session deload dose versus easy-run volume. For high-mileage athletes, do not assume the foundation-plan policy applies.
- Evaluate whether illness-state pauses should independently freeze mileage progression and pace calibration, with explicit user review and evidence checks.

## Uncertainties and falsification
Unknown: numerical limits, exposure metric, exact definition of current best, recovery-week hard/easy reduction ratio, plateau-variation amplitude, and whether these policies are enforced in every plan version. Collect consented paired current-app outputs and change only one input per trial. No current Runna outputs were captured.

Garmin Run Coach, Daily Suggested Workouts and Expert plans remain separate; this source provides no Garmin evidence. No proprietary implementation is reconstructed.
