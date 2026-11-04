# Research addendum — target-mileage plan semantics and Garmin taxonomy caution

Reviewed: 2026-11-04 (Asia/Manila). Evidence: vendor-published documentation, not captured current-app plans. Research only.

## Runna Run Further / Train Your Way target semantics

**DOCUMENTED; plan-type-specific.** Runna states that weekly-mileage and longest-run targets are distances aimed for in the **final week** of these plans, rather than necessarily a peak week. It describes **three progression weeks followed by a deload week**, and says ability, days/week, and plan length can prevent reaching requested targets. User-entered target guidance of 10–15% above current average is **advice about target selection**, NOT a verified per-week generator cap. Increasing ability or days/week can raise allowable target bounds. Source: https://support.runna.com/en/articles/11598695-how-to-use-and-manage-your-target-mileage-settings-on-run-further-and-train-your-way-plans (published 2026-08-30; reviewed 2026-11-04).

**Illustrative arithmetic, not app output:** For a 65 km/week baseline, 10–15% above baseline is 71.5–74.75 km (65*1.10 and 65*1.15). This is an example of Runna's target-selection advice, **not** a recommendation that the athlete should jump to those distances next week, nor a validated injury-safe rate.

**UNKNOWN:** How a final-week target interacts with a final deload, whether final-week semantics are applied to race plans, and exact bounds by ability/frequency. Do not generalize the three-on/one-off structure to all Runna plans.

## Runna Mileage Insights: important state and reversibility distinction

**DOCUMENTED.** Mileage Insights tracks planned, free and instant runs; may suggest weekly mileage, long-run mileage or run-frequency changes; is limited to race plans; and resets when a recommendation is accepted/dismissed or the plan is manually edited. Accepted changes have no automatic revert, although manual editing can rebuild the plan. Source: https://support.runna.com/en/articles/11794078-what-are-mileage-insights (published 2026-09-10; reviewed 2026-11-04). Some points already appear in the main notes; the **no automatic undo** distinction is useful for StrideLab's approval/rollback evaluation.

## Garmin primary-source taxonomy caution

Garmin's 2026-06-03 German blog mixes descriptions of Garmin Coach's named coach selection with marathon static plans and Daily Suggested Workouts. It explicitly describes marathon structured plans as not fully adaptive, with DSW an alternative; therefore it is **not** evidence of a single unified Run Coach marathon generator. Source: https://www.garmin.com/de-DE/blog/garmin-coach-welcher-trainingsplan-passt-am-besten-zu-dir/ (published 2026-06-03; reviewed 2026-11-04). **UNKNOWN:** Current region/device/version behavior. Label every future observation by exact plan family and device, not merely 'Garmin Coach'.

## Independent exercise science and limits

Muñoz et al., 2014, 10-week recreational runner intervention: both polarized and between-threshold groups improved 10K; difference in whole-group improvement was not significant. https://doi.org/10.1123/IJSPP.2012-0350 . This does not establish an optimal fixed 80/20 prescription for a specific 60–70 km/week runner. Three-zone scientific 'zone 2' (between thresholds) is **not** the same as easy Zone 2 in common five-zone watch systems. See https://pmc.ncbi.nlm.nih.gov/articles/PMC4621419/ .

## StrideLab implications (proposals, no engine changes)

- Engine 1.5: retain deterministic safeguards and existing explicit approval/rollback; avoid interpreting Runna's 10–15% target advice as a mandatory weekly increase.
- Engine 2.0: distinguish final-week target, peak-week target, long-run target and allowed ceiling; validate deload schedule by plan type. Test acceptance/rejection/reset behavior as distinct states, with reversible StrideLab revisions.
- Aerobic efficiency: compare pace at matched HR under similar heat, humidity, route and fatigue; no vendor score establishes improvement.

Next tests: generate matched-input Run Further/Train Your Way drafts to distinguish final week vs peak week, and record actual Garmin Run Coach outputs separately from named Coach and DSW. No independently observed new generator outputs or proprietary formula were obtained.
