# Session G | State dictionary and uncertainty

Every observation carries athlete, discipline, event and availability timestamps, source and version, unit, protocol, device, rights, quality mask and the plan revision that preceded it. Every estimate carries input IDs, personal reference window, eligible and excluded counts, age of the newest input, uncertainty reason, applicability and model version. Preserve null, reported zero, confirmed rest, missing upload and ineligible measurement separately.

All latent interpretations below are HYPOTHESIS. Measurements from a consented athlete carry ATHLETE lineage, published rationale SCIENCE, engineering fixtures SYNTHETIC. The decision rules are unvalidated. No inferred field alone may change a workout. A directly supplied restriction/stop can block an action. Apply the shared missing/conflict policy below to every field.

| Field and type (O observed, L latent) | Unit, inputs and reference | Horizon and freshness | Applicable use and interaction | Test/falsifier |
|---|---|---|---|---|
| fitness_trend L, signed estimate | standardized performance change vs same protocol | weeks to months, expire across protocol breaks | performance within discipline, contrast fatigue/environment | A: no future-performance gain over history |
| acute_fatigue L, categorical | perceived fatigue, RPE and achieved dose vs personal typical response | hours to days, refresh after new hard work | all disciplines, retain local soreness separately | A: check-in alone predicts as well |
| chronic_adaptation L, slope | repeated independent benchmarks in native units | block-to-block, refresh after benchmark | event/lift-specific, distinguish practice effects | A/E: repeated gains fail independent endpoint |
| recovery_trajectory L, direction | serial reports and subsequent comparable effort | between sessions, stale on new disruption | all, condition on interruption and illness | A: adds no value to last check-in |
| aerobic_efficiency L, paired trends | steady m/s at comparable bpm and bpm at comparable m/s | repeat blocks, invalidate sensor/route discontinuity | aerobic running, condition on environment and RPE | B: typical error masks useful change |
| quality_tolerance L, by intent | completed vs intended work, recovery and RPE | recent same-intent sessions | quality work, incomplete due to logistics separately | D: cannot distinguish time limit/intolerance |
| threshold_tolerance L, vector | bout minutes, reps, achieved pace, effort and relief | recent comparable sustained bouts | running, distinguish late fade and early pace mismatch | D: no better dose choice than fixed edit |
| long_run_tolerance L, vector | longest recent duration/km plus late-segment response | recent long exposures, stale after gap | endurance, keep durability separate from weekly total | C: no future tolerance gain over weekly volume |
| speed_exposure O, series | fast m/s, seconds, reps, rest by protocol | session and recent rolling history | high intensity/mechanics, affects restoration | C: adding fast exposure gives no gain |
| running_exposure O, vector | actual km/min, frequency, longest run, hard minutes/days | session and week with longer personal reference | running, carry coverage into every aggregate | C: decisions unstable across windows |
| strength_exposure O, vector | movement, load kg, sets, reps, RIR, local demand | per lift and recent block, bridge exercise changes | strength/calisthenics, condition on goal and equipment | E: new exercise yields misleading comparison |
| adherence O, reason-coded fraction | original opportunity, acceptance and performed dose | session/week, versioned before outcome | all, scheduling disruptions distinct from intolerance | F: missing upload changes denominator incorrectly |
| interruption O/L, reason and exposure vector | verified dates, reason and last tolerated task | event through verified return | all, branches into reason-specific review | C: no benefit over duration-only baseline |
| environment O/L, vector | weather units, grade, exposure source and acclimation | segment/day, expire on venue or condition change | outdoors, gates comparability | B: worse held-out error or low coverage |
| cross_discipline_strain L, by region/goal | order, spacing hours, eccentric work and prior tolerance | neighboring sessions plus training block | mixed weeks, protect priority outcome | E: identical results under simple priority calendar |
| local_soreness O, location and ordinal report | athlete report vs usual movement-specific response | today, refresh before affected session | all, never infer injury diagnosis | E: no incremental quality prediction |
| subjective_fatigue O, anchored ordinal | same-question self-report vs personal history | current check-in | all, combine with actual exposure | A: unstable answers or unacceptable burden |
| sleep_context O, hours and quality | diary/device, same source baseline | previous night and recent pattern | optional recovery context, flag travel/device change | A: ablation improves or preserves utility |
| HRV_context O, protocol-specific | morning/night metric, e.g. lnRMSSD, same-device baseline | recent measurement and baseline, reset on change | optional within-person signal, dependent on measurement protocol | A: fails incremental calibration |
| resting_HR_context O, bpm | same-position/time sensor readings vs personal reference | latest comparable morning and trend | optional context with symptoms and HRV | A: no gain over check-in |
| illness_restriction O, scoped event | self-report, explicit stop or supplied restriction, expiry | current decision, verify before return | all, hard constraints override favorable estimates | F/D: any forbidden recommendation fails |
| coverage O, vector | observed/expected sessions and segments, reason missing | each window and endpoint | all, limits which dimensions can be estimated | F: missing observations misclassified |
| measurement_quality O, flags | sensor/firmware, drift, GPS gaps, units and protocol | per record/segment, refresh on source change | all, quarantine invalid inputs | F/B: corrupted fixture accepted |
| decision_uncertainty L, structured category | independent signals, support, calibration and outcome-specific confidence | as-of decision, expires with new material information | all, selects review or abstention | A/D: false confidence or selective coverage |

**Confidence policy.** Assess completeness, recency, independent agreement, sensor/protocol quality, baseline stability, individual N, environmental overlap and held-out calibration separately. A reliability flag concerns the recording; confidence in a decision also depends on evidence maturity. Low support in the target population limits applicability even with a clean recording. No 0–100 score. Record conflicting facts alongside supporting facts. Do not impute a normal baseline for an unseen athlete, infer a poor session from an absent upload or count correlated watch and app summaries twice.

| Output | Conditions and behavior |
|---|---|
| Sufficient for recommendation | All critical inputs eligible, replicated personal support, validated decision domain and passed utility gates. Offer reviewed option. |
| Tentative | Goal and constraints known, uncertainty could change a small reversible choice. Explain options. |
| Strong review advised | Important dimensions disagree but feasible options can be described. Show both interpretations and request review. |
| Insufficient information | A resolvable critical fact is absent. ABSTAIN_NEED_MORE_INFORMATION, one low-burden question if useful. |
| No automated decision | Restriction, invalid lineage, out-of-domain case or unvalidated policy. Enforce independent hold where applicable and log exclusion. |

Freshness windows are parameters for Phase 2 sensitivity tests. Use only measurements available at decision time. For each field, mask it, perturb its window and compare calibration, coverage and burden with a model omitting it. Retire fields that add no practical value or amplify subgroup error. An athlete can simultaneously have a favorable easy trend, short-lived fatigue, old long-run exposure and uninformative HRV.
