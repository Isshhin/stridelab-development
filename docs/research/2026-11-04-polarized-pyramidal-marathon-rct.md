# Evidence addendum: randomized marathon intensity-distribution comparison (2026-11-04)

**Review date:** 2026-11-04 (Asia/Manila). **Scope:** research only; no Runna/Garmin output observation or production-code change.

## New independent evidence

Esteve-Lanao J, Casado A, Arévalo-Chico H, Sellés-Pérez S, Cejuela R. *Polarized vs pyramidal training intensity distribution in recreational marathoners: A randomized controlled study*. International Journal of Sports Science & Coaching. First published **2026-07-31**. DOI: https://doi.org/10.1177/17479541261468168 . Source abstract: https://journals.sagepub.com/doi/10.1177/17479541261468168 (accessed 2026-11-04).

**Status: PEER-REVIEWED PRIMARY STUDY, ABSTRACT-ONLY REVIEW.** The publisher restricts full text; verify methods, randomization, attrition, and detailed effect estimates before elevating confidence.

Twenty recreational runners randomized to 16-week polarized (POL) or pyramidal (PYR) training with matched weekly training load. Reported zone-1/zone-2/zone-3 proportions: POL 82/8/10%, PYR 75/22/3%. The abstract reports no statistically significant difference in marathon finishing performance (POL 3h45m; PYR 3h46m; p=0.23), and similar improvement in ventilatory-threshold-associated speed. PYR improved running economy more, whereas POL showed less second-half marathon slowing. The abstract's unusual '±21 s'/'±22 s' notation should not be interpreted as precision or variability without the full paper.

**Reproducible descriptive calculations, not effect-size estimates:** POL minus PYR zone proportions = +7, -14, +7 percentage points; POL/PYR zone-2 proportion ratio = 8/22 = 0.364; zone-3 ratio = 10/3 = 3.333. Nominal finishing-time difference = 1 minute, or 60/226 = 0.265% of PYR's 226 minutes. This descriptive difference is not statistically significant (reported p=0.23).

**Interpretation:** There is no basis here to hard-code a single intensity distribution as universally optimal for high-mileage runners. The distinct running-economy versus late-race durability signals motivate separately tracked outcomes, not an inferred Runna/Garmin algorithm. Sample is small, recreational, marathon-focused, and not a controlled comparison of matched-HR easy pace. Neither vendor is studied.

## Cross-check against existing research

The main document `docs/research/runna-garmin-coaching-algorithm.md` already separates Runna mileage progression, pace calibration, and Garmin Run Coach/DSW/Expert plan taxonomy. The existing historical eight-plan/92-week analysis is not a current matched-input generator experiment; the 16-case Runna protocol remains NOT_RUN. No new vendor numeric progression rule or authorized Garmin Run Coach output was obtained this review.

## Engine implications (research hypotheses only)

- **Engine 1.5:** feature-frozen; no changes recommended.
- **Engine 2.0:** compare POL/PYR-like distributions only as pre-registered simulation alternatives; report intensity denominators, training volume, threshold pace, running economy proxy, easy-pace-at-matched-HR, and long-run durability separately. Do not treat this 20-runner trial as a validation of any StrideLab policy or as evidence of parity with Runna/Garmin.
- For 60–70 km/week Zone 2 improvement, prioritize repeated route/temperature/humidity/elevation-matched easy efforts with stable HR, while tracking threshold progression and fatigue.

## Open questions

1. Does the study's full text support the economy/durability contrast after accounting for baseline differences and multiplicity?
2. What are the absolute zone times and adherence distributions, and how were zones assigned?
3. Can consented prospective athlete data replicate changes in matched-HR pace and drift without confounding by heat or hills?
4. Obtain authorized current Runna matched-input plan captures and Garmin **Run Coach** pre-workout recommendations, explicitly not DSW or named Expert Coach outputs.

**No private algorithm reconstruction claimed.**
