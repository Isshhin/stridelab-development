# Batch 2 — Combining External/Internal Load and Evaluating Easy-Pace Improvement

Date: 2026-11-02. Priorities 4–5 of the agreed research agenda. Independent sports-science review, not a training prescription or production-code change. Companion to [master review](runna-garmin-coaching-algorithm.md) and [Batch 1](batch-1-hard-dose-periodization-garmin-adaptation.md).

## Priority 4 — How should StrideLab combine external and internal training load?

### Definitions and direct evidence

In the athlete-monitoring cycle described by Bourdon and colleagues [M1], distinguish the **external work actually performed**, the **internal physiological response**, **perceived wellness/tolerance**, and **readiness for the next stimulus**. No one measurement is a complete representation of the others.

| Axis | Examples | Strength | Limitation |
| --- | --- | --- | --- |
| Prescribed external demand | planned distance/duration/target step pace and recovery | Athlete/coach intention | Does not prove session was completed |
| Completed external work | running distance, elapsed/moving duration, velocity, elevation, interval completion | Direct record of executed task | GPS tight loops, hills and terrain can distort comparisons |
| Internal response | HR trace, HR drift, session RPE and notes | Contextualized strain | HR lag, heat, illness, hydration, medication, sensor artifacts |
| Proprietary vendor estimate | Garmin EPOC-exercise load and Training Effect, if provided | Longitudinal device-specific summary | Not open source; device, sensors, calibration and fitness level affect result |
| Recovery/fitness outcome | performance across standardized comparisons, wellness, repeated hard-session tolerance | Connects stimulus to progress | Delayed, confounded and cannot be inferred causally from one run |

The [Garmin Run Coach technical page][G1] describes EPOC-based exercise/acute/chronic training load. The algorithm weights and Firstbeat models are proprietary; **do not synthesize Garmin-equivalent load** from kilometers or HR minutes using arbitrary coefficients.

### Proposed, original StrideLab decision sequence (HYPOTHESIS)

1. Record and validate complete workout steps (target and achieved) with uncertainty for GPS/HR.
2. Calculate separate external and internal summaries *without normalizing them into one unexplained score*.
3. Compare each metric against the same athlete's recent baseline, accounting for planned deloads and relative duration.
4. Include athlete-reported exertion and recovery when available; missing data stay missing.
5. Generate a **proposed** workload/target change with its evidence, constraints and uncertainty. Require user approval for substantial changes and preserve an immutable before/after plan.

Initially keep external and internal load as a **feature vector**, not a forced scalar. A composite score, if later useful, must be calibrated and validated against an outcome; avoid adding HR, km and EPOC as if they share units.

### Example: why apparent contradiction is normal

Two 10 km easy runs have equal distance (external distance) but different heart-rate traces, perceived effort, heat and post-run recovery. This doesn't prove one produced better fitness; it shows their response was not identical. Similarly, one hard workout with total session time 60 minutes may have only 25 minutes at the intended work target.

The earlier StrideLab research [I1] already documents that speed-based, HR-based and RPE-based training intensity distributions can differ sharply on the same training data. Always state denominator: session-goal count, distance, total moving time, work-step time, or HR time-in-zone.

## Priority 5 — Which intensity distribution improves easy pace at a matched HR?

### Main finding: there is no robust universal winner for the user's exact outcome

A 2025 individual-participant-data network meta-analysis of **13 studies / 348 endurance athletes** found no overall significant advantage for polarized (POL) vs pyramidal (PYR) training on **VO2max or time-trial performance**. Its subgroup analysis suggested competitive vs recreational athletes may respond differently, but these are subgroup findings. Crucially **neither VO2max nor time-trial performance is the same outcome as field-measured pace at matched HR**. [E1]

A 2023 randomized four-arm trial in **60 well-trained male runners** contrasted POL vs PYR with and without weekly plyometrics, keeping running training load constant. Improvements in 5K performance and lactate-related speeds varied across groups; adding plyometrics seemed beneficial for some performance outcomes irrespective of distribution. The results do **not** prove one distribution optimizes an individual's easy HR pace. [E2]

A later 2025 randomized 16-week study in **120 recreational marathon runners** reported greater mean marathon improvement with POL than PYR. This differs from the aggregate result above, but the protocol, race distance, study population and *marathon-time* outcome differ; a single trial does not justify a universal prescription or a prediction of matched-HR speed. Critically appraise its volume matching, randomization, adherence and ML validation before using any individual responder classes. [E3]

**Conclusion:** Selecting POL or PYR solely to boost easy pace at 140 bpm is not supported by direct evidence reviewed here. Keep a low-intensity-dominant baseline appropriate to the individual and test whether a specific change improves validated outcomes without compromising durability and threshold performance. Do not represent "80/20" without a precise denominator and physiological threshold definitions.

### Very relevant adjacent result: running economy and strength

A 2024 systematic review/meta-analysis of **31 studies / 652 middle- and long-distance runners** found small improvements in laboratory running economy with high-load strength work, some combined programs, and plyometric work. Plyometrics showed a signal at lower running speeds (at or below roughly 12 km/h), whereas higher-load training effects varied by speed and VO2max. The meta-analysis reported moderate-to-low certainty, depending on intervention. [E4]

A 2025 umbrella review of **17 systematic reviews** broadly supported strength-training benefits for running economy and endurance performance but warned that many included reviews had low or critically low methodological confidence. [E5]

**Key warning:** These studies measured *energy/oxygen cost at fixed speed*, not faster free-running pace at a fixed HR. Running economy measured by metabolic cart is **NOT** interchangeable with field matched-HR pace. The study findings support investigating strength alongside intensity distribution, not changing the user's current training schedule on the basis of a proxy.

### Critical confounder: heat adaptation and physiological drift

A 2025 quantitative review/meta-analysis of heat acclimation including **211 papers** reported lower exercise HR and other physiological adaptations after acclimation. This means better pace at the same HR on a hot route can reflect **heat acclimation** as well as aerobic training, and cannot be automatically labeled a new lactate threshold or improved laboratory running economy. [E6]

A 2025 commentary on marathon cardiovascular drift summarized field observations in which HR increases relative to external running speed during prolonged racing, influenced by heat, hydration and fatigue; interpretation as an athlete's "durability" requires careful study and condition matching. It is a synthesis/commentary, not a controlled intervention demonstrating which weekly intensity distribution causes improvement. [E7]

### Proposed field measurement protocol (research design, not established clinical standard)

For the user's high-volume (~60–70 km/wk) case:
- Use several **comparable road** sessions before changing a plan; keep **tight rectangular-loop sessions in a separate route class**. Do not pool them by raw average pace.
- Record local date/time, actual route/grade, temperature/humidity where available, training position (after threshold/strength/long run or not), sleep, shoes/sensor/device, and easy effort/RPE.
- Analyze pace as **speed in km/h at a prespecified narrow, personally verified HR band** (example only: 136–144 bpm), after the first easy warm-up minutes. Do not treat HRR watch Zone 2 as physiologically identical to *below lactate threshold 1 (LT1)*.
- For longer easy sessions, separately report first vs last matched-condition segment **cardiovascular drift / speed-HR decoupling**, and RPE change.
- Use repeated tests across conditions, with sensitivity analysis excluding major heat swings, hill/climb segments, GPS anomalies, stop/start interruptions and sessions with known under-recovery.
- Report **median and variability/uncertainty**, not a single personal record or claims of causal adaptation.
- If designing a future controlled comparison of intensity-distribution policies, pre-register metrics and compare multiweek windows with stable mileage and recovery, but do not impose a hard week-to-week experimental change solely for data collection.

### Outcomes need distinct names

- **Matched-HR speed (field proxy):** measured km/h during comparable conditions at similar HR. Can improve from fitness, acclimation, fatigue changes, medication, sensor error, or other factors.
- **Running economy (laboratory):** oxygen or energy cost at specified submaximal speed, generally requires testing beyond FIT data.
- **Aerobic threshold / LT1:** physiological transition requiring appropriate validated assessment; cannot be inferred from Garmin HRR zone boundary alone.
- **Durability:** the ability to sustain performance with less physiological/biomechanical deterioration during prolonged exertion.
- **Fitness/race outcome:** 5K, 10K, and longer performance; not identical to either matched-HR speed or VO2max.

### Strongest falsifiable research questions after these batches

1. At unchanged weekly running mileage, does adjusting threshold interval *hard-work minutes* change subsequent comparable matched-HR speed, RPE, and 5K/threshold performance? Avoid forced changes without safety review.
2. Are observed easy-speed gains largely explained by route, temperature/humidity, fatigue and acclimation rather than training distribution?
3. Does strength-load timing explain slow easy runs immediately after leg sessions, and do months of consistent strength training improve running economy?
4. Does a physiologically calibrated threshold-based time-in-zone description differ from Garmin HRR Zone 2 labels and from pace-based session-goal counts for this athlete?
5. What is the minimal amount of reliable repeated activity history needed for a StrideLab adaptation proposal without overreacting to one hard/slow day?

## Implications for Engine 1.5 vs Engine 2.0

**Engine 1.5:** Store external steps, HR, RPE, route, environment and missing-data flags separately; produce transparent planned-vs-actual and route-comparable simple summaries. Do not implement an unvalidated cumulative fatigue formula.

**Engine 2.0:** Research individualized load-response/uncertainty models, environmental corrections, cross-discipline effects, and multiweek counterfactual testing. Require prior independent validation against outcomes and a human approval gate for major training changes.

## Primary source register

- [M1] https://bjsm.bmj.com/content/51/20/1451 — athlete monitoring cycle (external -> internal -> wellness -> readiness).
- [G1] https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [I1] https://pubmed.ncbi.nlm.nih.gov/31188692/ — method dependence of intensity-distribution proportions.
- [E1] https://pubmed.ncbi.nlm.nih.gov/39888556/ — 2025 network meta-analysis, 13 studies, 348 athletes.
- [E2] https://pubmed.ncbi.nlm.nih.gov/36326594/ — 2023 runners, POL/PYR ± plyometrics RCT.
- [E3] https://www.nature.com/articles/s41598-025-25369-7 — Nov 2025 POL/PYR marathon RCT with ML claims; single study.
- [E4] https://pubmed.ncbi.nlm.nih.gov/38165636/ — 2024 strength-training and running-economy meta-analysis.
- [E5] https://pubmed.ncbi.nlm.nih.gov/40153564/ — 2025 strength-training umbrella review.
- [E6] https://pmc.ncbi.nlm.nih.gov/articles/PMC12122934/ — 2025 heat-acclimation meta-analysis.
- [E7] https://pmc.ncbi.nlm.nih.gov/articles/PMC12034733/ — 2025 heart-rate-drift commentary.

**Status:** Source-reviewed findings and proposed independent evaluation protocol. No new personalized FIT processing, controlled Runna outputs or Garmin recommendation stream analyzed. No causality claimed for the initial athlete and no production code changes.
