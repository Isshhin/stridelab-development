# Evidence addendum: interval training and running economy (2026-11-04)

**Scope:** Independent StrideLab Coaching Algorithm Research Lab. Research-only; no Engine 1.5/2.0 implementation change.

## New independent evidence

Feng Y, Li D, Liu Y, Tang D. *High-Intensity Interval Training and Moderate-Intensity Continuous Training Affect Running Economy in Endurance Runners: A Systematic Review and Meta-Analysis of Randomized Controlled Trials.* Journal of Human Kinetics 100:113–134. DOI: https://doi.org/10.5114/jhk/205427 ; full text https://pmc.ncbi.nlm.nih.gov/articles/PMC12946864/ ; PubMed https://pubmed.ncbi.nlm.nih.gov/41766811/ . Online 2025-09-23; 2026 collection; reviewed 2026-11-04.

**Evidence status: PEER-REVIEWED SYNTHESIS, not observed Runna/Garmin app behavior.** Nine randomized studies, 168 runners (110 male, 48 female), primarily recreational/moderately trained. The meta-analysis reports HIIT versus moderate continuous training running-economy SMD 0.44 (95% CI 0.15–0.72); it reports greater VO2max response for MICT (mean difference 2.48, CI 1.61–3.34), though the latter result requires careful checking of comparison direction and original trial methods before operational use. The study reports heterogeneity in intervention dose and measurement, possible bias, and omission of heat, sleep and nutrition context. The study's **three-zone model defines Zone 2 as between LT1 and LT2**, unlike the common watch five-zone 'Zone 2' convention. Do not equate these.

### Reproducible descriptive checks
- 110 + 48 = 158, which **does not equal the article's stated total 168**; the sex breakdown is internally inconsistent and must be flagged rather than silently corrected.
- CI midpoint for RE SMD: (0.15+0.72)/2 = 0.435, consistent with reported 0.44 after rounding.
- CI width: 0.72-0.15 = 0.57. These are arithmetic checks, **not independent recalculation of pooled effects**.

### Implications and boundaries
- Running economy (oxygen cost at fixed speed) is **not equivalent** to faster GPS pace at fixed HR, and neither alone establishes LT1 movement.
- For a 60–70 km/week athlete seeking improved easy pace, compare threshold/interval and continuous-run prescriptions while tracking *separate* economy proxies, matched-HR pace, threshold, total easy time, hard-work dose, heat, route, and fatigue. This is a StrideLab **research hypothesis**, not a recommendation to increase intensity or a verified Runna/Garmin decision rule.
- **Engine 1.5:** no change; maintain bounded progression and explicit approval.
- **Engine 2.0:** add this study as rationale for testing intensity-mix policies with controlled outcomes and measurement provenance; do not hard-code HIIT superiority.
- No new Runna generator output, Garmin Run Coach snapshot, or proprietary adaptation formula was found. Garmin Run Coach remains distinct from DSW and Expert plans.

## Highest-value unanswered questions
1. Do controlled Runna plan outputs change hard-work minutes or only workout counts at fixed weekly distance and race ability?
2. Can matched-HR pace changes in humid conditions be separated from measured oxygen-cost economy and threshold adaptations?
3. Do prospective Garmin **Run Coach** recommendations respond to actual recovery context independently of recent workload and event phase?

**Repository comparison:** Existing `docs/research/intensity-distribution-and-physiological-load.md` already discusses denominator effects, Garmin load and other training-intensity meta-analyses; this study adds a direct HIIT-versus-MICT running-economy synthesis, not a vendor algorithm discovery.
