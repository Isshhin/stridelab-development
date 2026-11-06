# Independent coaching evidence review — 2026-11-06

**Scope:** Research-only addendum to `runna-garmin-coaching-algorithm.md`. Public primary scientific abstracts and vendor documentation checked 2026-11-06. No Runna or Garmin private implementation inferred; no authenticated new plan exports or Garmin Run Coach recommendation captures.

## New-to-reviewed-core scientific leads (not new vendor algorithm disclosures)

1. **Intensity-distribution randomized trial (Esteve-Lanao et al., published 2026-07-31)**. Primary journal abstract: https://doi.org/10.1177/17479541261468168 . Twenty recreational marathoners randomized to 16 weeks of matched weekly training load, polarized (82/8/10% in zones 1/2/3) versus pyramidal (75/22/3%). Reported marathon results 3 h 45 min vs 3 h 46 min, p=.23; pyramidal group showed greater running-economy improvements, polarized group less late-race slowing. **Evidence:** published peer-reviewed abstract, randomized design; full text restricted, small sample, exact per-run prescription and confidence intervals unavailable. **Reproducible arithmetic:** middle-zone share difference = 22−8=14 percentage points; high-zone difference = 10−3=7 percentage points. Neither is a recommended athlete-specific allocation. No demonstrated superiority on the primary marathon outcome.

2. **Heart-rate versus race-pace prescription crossover (Ranieri et al., 2026-03-24)**. University-hosted primary author metadata and abstract: https://portalcientifico.urjc.es/en/ipublic/item/10483452 ; DOI https://doi.org/10.1123/ijspp.2025-0578 . N=37 runners; randomized crossover, 45-min low-intensity continuous session and 5×800 m high-intensity intervals. Time in prescribed zone: HR-based 85% vs race-pace-based 69% for low-intensity (p=.002); HR 41% vs pace 27% for intervals (p<.001). **Reproducible arithmetic:** +16 and +14 percentage points respectively. **Evidence:** peer-reviewed study abstract, not a StrideLab field validation. Zone boundaries established with treadmill ventilatory tests; findings do not establish that generic watch HR zones are accurate, that HR should exclusively control short intervals, or that HR-based training causes greater long-term fitness.

3. **Long-run-history and running-economy durability (Zanini, Folland, Blagrove, Jan 2026)**. Primary journal abstract: https://doi.org/10.1249/MSS.0000000000003840 . 13 vs 13 performance-matched male runners, regular long runs ≥90 min versus all runs <70 min, compared on 90-min economy durability. **Evidence:** observational cross-sectional, performance matched, NOT randomized long-run assignment; causality, applicability to other populations, and precise effect size require full-text review. This is especially relevant to separating training history from 10-km performance in the preregistered 25-vs-65-km/week generator comparison. Do not treat as a validated long-run dose rule.

## Vendor boundary and prior-work comparison

The existing core file already documents Runna weekly-mileage and long-run constraints, difficulty/volume preferences, deloads, pace insights, Garmin Run Coach vendor inputs, and the distinction from Garmin Daily Suggested Workouts (DSW) and named Expert Coach plans. These studies **do not** disclose or validate any Runna/Garmin decision rule. The core also contains 92 weeks from eight historical published Runna plans and an unexecuted 16-case current-generator protocol. No new verified current-plan output found in this review.

Runna primary references rechecked: https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan and https://support.runna.com/en/articles/11794078-what-are-mileage-insights . Their statements remain **VENDOR DOCUMENTED**, not independently observed. Garmin Run Coach, DSW, and Expert Coach evidence must remain separate.

## Research implications and unresolved questions

- **Engine 1.5:** no code or policy changes justified. Preserve deterministic safeguards and athlete review.
- **Engine 2.0:** investigate dual-mode prescription: HR/effort for easy runs and calibrated pace/effort with HR as secondary context for faster repeats; test execution accuracy, not just physiological outcome. Distinguish lab-derived threshold zones from wearable default zones.
- Prospectively measure running-economy *durability* (early vs late matched-effort/terrain/temperature segments), not only first-30-min matched-HR pace. Normalize for heat, elevation, wind, fatigue, hydration and sensor quality.
- Compare pyramidal/polarized intensity distributions at comparable load before claiming a single optimal split. Record time-in-zone denominators and intensity-zone definitions.
- **Next highest-value tests:** (1) authorized 25-vs-65-km/week matched-performance Runna exports; (2) timestamped genuine Garmin Run Coach pre-workout prescriptions plus post-workout context, separately from DSW; (3) repeated easy-run matched-HR pace and drift measurements; (4) inspect full text and confidence intervals for these studies before designing policy.

**Evidence status:** scientific publications independently checked; vendor documentation checked; app behaviors unobserved this run; no numerical proprietary algorithm reconstructed. No production changes, no merge.
