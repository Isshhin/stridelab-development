# Engine 2.0 focused evidence review and next experiments

Reviewed 2026-11-04 (Asia/Manila). **Research only. No production Engine 1.5 or Trackside changes.**

## Decision

Prioritize authorized data and measurement repeatability before adding coaching complexity. Keep a transparent baseline and compare proposed policies in shadow mode. Do not promote Engine 2.0, infer a proprietary vendor formula, or claim injury prevention from simulation results.

The highest-value next work is: (1) admit a small, explicitly consented dataset with a complete rights/provenance record; (2) measure repeatability of easy running speed at comparable HR and conditions; (3) evaluate separate session, weekly, long-run and interruption exposures; and (4) complete the already registered vendor observations. Predictive workout selection should wait for suitable longitudinal outcomes and independent calibration.

This is a focused, source-led review, not a systematic review or individualized medical prescription. Sources were searched and checked on the review date using scientific publishers/PubMed, institutional repositories, official vendor documentation and official terms. Search topics included session-distance spikes, weekly progression, submaximal HR monitoring, humidity, grade, detraining, running datasets and adaptive product mechanisms. Abstract-only support and inaccessible terms are identified below. No dataset was downloaded or analyzed and no athlete was recruited.

**Access limits:** Buist's abstract was read directly. Frandsen's abstract/search extract and the existing repository evidence addendum support the cohort summary; a publisher full-text fetch was denied, so the full methods were not independently re-audited here. Haaf, Minetti, Chen and the humidity study are used at abstract/extract level; review articles provide context rather than a new systematic synthesis. Dataverse access failures leave licensing unresolved. Treat this pass as a decision-oriented evidence map, not full-text risk-of-bias grading or legal clearance.

## 1. Baseline and evidence accounting

The connected repository is `Isshhin/stridelab-development`; the research branch is `research/runna-garmin-coaching-20261102`. Review baseline: `085e68b5f74a05bb24f27c299b65b775f00be259`. GitHub reported [PR #34](https://github.com/Isshhin/stridelab-development/pull/34) **draft, open, unmerged**. This addendum extends existing work without replacing its experiment IDs or historical results.

Relevant existing artifacts are Batch 7 (traceability), Batch 8F (source admission/field outcomes), November 4 pace-provenance and session-spike addenda, and E007/E008 capture protocols. At this baseline, the research artifacts report E002 **0/16 current-app cases**, E007 **0/6 matched draft plans**, and E008 **0 verified Garmin Run Coach pairs**. Historical Runna PDFs are published-plan evidence, not current generator captures. Previously reported synthetic pass counts were not rerun or re-certified in this pass.

The separate local coaching lab is accessible at `E:\Project\stridelab-copilot\stridelab-coaching-lab`, source HEAD `e255c5d369af3576cc6ccfec44696ed4f15e1c35`, with a clean working tree when inspected. Read-only inspection covered README, `activity.py`, the beginning of `file_activity_adapter.py`, and `v031_activity_validation.py`. No local lab execution or source-admission approval occurred. Earlier cloud statements that this path was unavailable remain historically accurate for those environments; they are not a current local-access finding.

| Evidence class | Meaning in this report | What it cannot establish |
|---|---|---|
| SCIENCE | Identified published human study/review, with design and population | Vendor implementation or individual causal benefit |
| VENDOR | Official explanation of a product or policy | Independent effectiveness, undisclosed thresholds or actual account behavior |
| OBSERVATION | Authenticated, dated app/device output with version and input provenance | Proprietary internals or physiological correctness |
| HYPOTHESIS | Proposed StrideLab measure, policy or interpretation | A validated intervention |
| SYNTHETIC | Constructed fixtures, arithmetic and software invariants | Real-athlete response, injury prevention or generalization |
| ATHLETE | Authorized measured human records with quality/context and outcome definitions | Causality without an appropriate design |

No new OBSERVATION or ATHLETE evidence was collected. Academic datasets contain real human observations, but cataloguing them is not admitting, analyzing or validating them for StrideLab. A parser fixture, a file called `real_benchmark`, or a populated `synthetic=false` field does not authenticate athlete provenance.

## 2. Lawful/authorized data acquisition

### Candidate sources and what they can answer

These are conditional acquisition candidates, not an approval to download, train on, redistribute or commercialize data. A paper's open-access license, a repository's code license and the underlying data license are separate rights.

| Source | Available research signal | Rights/access evidence checked | Fit and next action |
|---|---|---|---|
| **Consented direct device exports**: participant-supplied original FIT/TCX/GPX or research logger | Repeated time-resolved speed, HR, location/grade and laps when actually recorded; add RPE, context and adverse reports | Obtain permission for each intended purpose, processor, retention and sharing route; check any upstream contracts. Local possession is not blanket research/AI permission | **First priority** for easy-HR repeatability. Prefer device-origin files with documented chain of custody. No exports were opened in this pass |
| **Groningen/Lövdal replication dataset**, [institutional record](https://research.rug.nl/en/datasets/replication-data-for-injury-prediction-in-competitive-runners-wit/), [DOI 10.34894/UWU9PV](https://doi.org/10.34894/UWU9PV) | Seven years of logs, 74 high-level runners, one team/coach; injury-labelled longitudinal research | Institutional provenance verified; Dataverse landing page returned an anti-bot challenge, so exact current data license/version/file terms **not verified** | Best candidate for an independent training-log benchmark **after rights review**. Do not use Kaggle mirrors to bypass missing terms. No established suitability for matched-HR analysis |
| **Málaga treadmill tests**, [PhysioNet v1.0.0](https://physionet.org/content/treadmill-exercise-cardioresp/1.0.0/) | 992 graded tests with breath-by-breath HR, gas exchange and speed | Current linked [license](https://physionet.org/content/treadmill-exercise-cardioresp/view-license/1.0.0/) is Contributor Review Health Data License 1.5.0, including scientific-research-only use and no access sharing. The page also labels the database open access; resolve the applicable download/DUA terms rather than assuming CC0 | Useful for acute HR/speed/gas-exchange construct checks; not longitudinal training adaptation or injury outcomes. **Conditional**, no commercial clearance inferred |
| **Sanford Stride Project**, [owner README](https://github.com/SanfordEAS/Sanford_Stride_Project_Public/blob/main/README.md) | De-identified 3D motion capture plus self-reported training/injury information | Explicit **CC BY-NC-SA 4.0** data terms and written donation consent for research/education | A clearly licensed academic candidate, but **noncommercial restriction** matters to product-directed research. Obtain separate permission if intended use exceeds it. Not automatically a continuous HR/speed corpus |
| **RSU treadmill kinematics/HR**, [institutional record](https://science.rsu.lv/en/datasets/treadmill-running-kinematics-and-heart-rate-dataset-across-experi/), [DOI](https://doi.org/10.48510/FK2/K6W5E5) | Experimental treadmill-task kinematics and HR | Record verified; linked Dataverse page could not be fetched, so license, participant count and detailed schema remain **unverified** | Secondary acute-measurement candidate; hold admission until actual terms and files are reviewed |
| **Bath HYROX 12-week cohort**, [archive](https://researchdata.bath.ac.uk/1699/) | Weekly running/resistance/conditioning exposure and health-problem surveillance; relevant mixed-discipline burden | Underlying data **restricted/request access**. Public analysis code is MIT; that does not license the data | Potential collaboration for cross-discipline outcomes; request purpose-specific access if pursued. Weekly exposure cannot resolve within-run drift |
| **Garmin-RUNSAFE 5,205-runner cohort** | Published session-spike/injury association | [Study](https://doi.org/10.1136/bjsports-2024-109380) data-availability statement, also recorded in existing research, limits sharing outside Aarhus under participant consent | **Literature-only** here. Do not treat a published cohort as an obtainable activity dataset |

The catalog deliberately favors sources whose origin and limits can be traced over a large list of scraped or vaguely licensed activities. None alone covers repeated easy-HR performance, environmental exposure, adherence and injury surveillance well enough to answer every Engine 2.0 question.

### Platform restrictions are a separate gate

**VENDOR/POLICY:** Strava's current [API Policy](https://www.strava.com/legal/api_policy), effective June 1, 2026, prohibits API-data use for AI development, evaluation and operation, including derived/anonymized forms; it also restricts aggregation and scraping. Its personal MCP exception is not a route to a commercial research corpus. **Research decision:** exclude Strava API-derived data from this proposed AI research pipeline absent an explicit applicable agreement. Do not assume user consent or de-identification overrides those terms. This conclusion concerns the documented route; it does not establish that every independently created device file is Strava Data.

**VENDOR/POLICY:** The public [Garmin developer agreement template](https://www8.garmin.com/en-US/GARMINCONNECTDEVELOPERPROGRAMAGREEMENT/GARMINCONNECTDEVELOPERPROGRAMAGREEMENT_EN.pdf), section 15.10, specifies AI transparency and explicit consent requirements. It is not evidence of StrideLab's executed contract, regional terms or approved purpose. [Garmin brand guidelines](https://developer.garmin.com/downloads/brand/Garmin-Developer-API-Brand-Guidelines.pdf) require appropriate attribution for materially derived analytics. **Research decision:** verify the actual agreement and source route before ingestion; attribution alone grants no data-use rights.

For a future field study, start with an adult opt-in cohort and original device/logger exports under a documented protocol. Keep research consent distinct from product terms, and distinguish local deterministic analysis, AI inference, model fitting, publication and redistribution permissions. Determine applicable privacy/ethics obligations for the actual organization, participants and processors before collection; this report does not make a jurisdiction-wide legal clearance claim.

### Proposed provenance record and admission rules — HYPOTHESIS

Each dataset/version needs: owner and original URL/DOI; acquisition method/date; exact license/DUA version and retained terms reference; allowed/prohibited purposes and derivatives; participant consent/ethics scope; approved processors/storage region; pseudonymous participant IDs; raw-file SHA-256 and immutable manifest; device/sensor/firmware/timezone; schema/units/sampling; transformations/code version; missingness; exclusions; retention/deletion owner; and whether data are observed, synthetic, vendor-prescribed or manually transcribed.

Admission is **purpose-specific**: `PENDING_TERMS`, `PENDING_CONSENT`, `RESTRICTED_RESEARCH_ONLY`, `APPROVED_FOR_NAMED_PROTOCOL` or `REJECTED`. An honest hash proves file consistency, not lawful origin. Consent withdrawal must be traceable to raw and derived records and future training use; a plan for already fitted models must exist before fitting. Strip direct identifiers and unnecessary GPS endpoints, retain identity keys separately, and keep raw recordings outside Git and CI artifacts. Route geometry can still identify someone after names are removed.

## 3. Aerobic-efficiency evidence and measurement

### What speed at comparable HR means

**SCIENCE:** A standardized submaximal test can contribute to monitoring, but HR is influenced by training state and context. [Haaf et al. (2019)](https://doi.org/10.1080/17461391.2019.1571112) found submaximal HR inadequate as a sole intensified-training monitor and recommends perceptual measures alongside it. Its setting does not validate free-living pace/HR scoring for everyone. A [systematic review of minimally invasive running fitness monitoring](https://pmc.ncbi.nlm.nih.gov/articles/PMC9940993/) similarly describes protocol and within-person limitations.

**HYPOTHESIS:** Treat repeated running **speed in m/s at comparable easy HR** as a field proxy, paired with RPE and context. It is not measured running economy (oxygen/energy cost), laboratory VO₂max, a lactate threshold or a causal measure of training benefit. HRmax-based watch “Zone 2” and zone 2 in a three-zone lactate model can mean different intensities; save the zone definition and test source. Faster speed at one nominal HR is not enough to declare adaptation, and lower exercise HR can accompany fatigue rather than improvement.

### Drift and decoupling

**SCIENCE:** [Coyle and González-Alonso (2001)](https://www.cibr.es/files/ka/apps/cibr/docs/04_2001_cardiovascular_drift_during_prolonged_exercise_and_the_effects_of_dehydration.pdf) discuss the rise in HR and fall in stroke volume during prolonged exercise, with heat/dehydration interactions. This physiological phenomenon is distinct from an app's pace/HR decoupling calculation. It does not establish a universal 5% cutoff for aerobic readiness.

**HYPOTHESIS:** For a stable, eligible segment after warm-up, define `EF = mean(speed_m/s) / mean(HR_bpm)` for each equal-duration half, then `decoupling_pct = 100 * (EF_first - EF_second) / EF_first`. A positive value denotes reduced speed per beat in the second half. Record this exact convention; do not interchange ratio-of-means, mean-of-ratios or opposite-sign definitions. Also report HR change at stable speed, speed change under HR clamping, duration and RPE separately. The two protocols answer different questions. HR lag, stops, hills, intervals and unmatched halves can make a numeric result uninterpretable. No universal pass/fail threshold is recommended.

### Heat and humidity

**SCIENCE:** In a repeated-condition experiment, [Che Muhamed et al.](https://pubmed.ncbi.nlm.nih.gov/28349085/) studied 11 trained male runners at 31°C across five RH conditions during 60-minute runs and a subsequent exhaustion test. Higher humidity increased thermal/circulatory strain and impaired performance in this setting. This small male laboratory sample does not supply a universal seconds/km correction. The broader [heat-stress review](https://doi.org/10.1152/physrev.00038.2020) explains how environment, evaporation and cardiovascular strain interact.

**HYPOTHESIS:** Save temperature and RH together, plus source, timestamp and uncertainty. Where available retain dew point/vapor pressure, wind, solar exposure and acclimation context. A distant weather station and wrist temperature are imperfect ambient estimates. First compare similar conditions; only then evaluate a context model with held-out data. Do not “correct” hot runs into fitness gains using an uncalibrated coefficient. Missing weather should reduce confidence rather than imply cool/dry conditions.

### Hills, elevation and fatigue

**SCIENCE:** [Minetti et al. (2002)](https://pubmed.ncbi.nlm.nih.gov/12183501/) measured nonlinear uphill/downhill energy costs in ten treadmill runners. Its small, controlled sample is support for grade as a confounder, not validation of a universal grade-adjusted pace formula for trails. Elevation gain alone cannot characterize the order or steepness of slopes. Altitude is distinct from cumulative ascent and should be retained separately.

**HYPOTHESIS:** Prefer the same flat route and direction for the primary trend. Analyze hills separately with grade-profile and surface quality; evaluate grade adjustment as an ablation, not ground truth. Downhill speed or low HR does not establish low tissue loading. Record recent long/hard running, leg strength work, sleep, illness, pain, fuelling and perceived effort where consented. Do not infer recovery or injury from a single HR anomaly.

### Longitudinal interpretation and a concrete measurement protocol

Extend the existing Batch 8F endpoint rather than replace it. Proposed protocol decisions below must be frozen before seeing outcomes:

1. Use repeated familiar easy runs, without asking athletes to increase training for the study. Select a fixed steady segment after warm-up, initially 20 minutes on the same flat route/direction where feasible. Capture continuous timestamps, HR and speed; retain sensor-quality, pause and route flags.
2. Preserve elapsed, timer and moving-time semantics. Do not divide total distance by elapsed time and call it steady running speed. Separate walking, warm-up, work, recovery and cool-down. Reject cadence lock/dropouts and unreliable GPS from primary comparisons using preregistered quality rules.
3. Pilot matching windows already proposed in Batch 8F—HR within 3 bpm, temperature within 3°C and RH within 10 percentage points—are **study assumptions**, not validated physiological equivalence. Save route, sensor, time of day and RPE. Test narrower windows without choosing the one that produces the largest apparent improvement.
4. Collect repeated baseline and follow-up observations; first estimate within-person typical error. Three observations per period are a feasibility floor from the earlier protocol, not a power calculation. If matches are sparse, report `INSUFFICIENT_MATCHED_RUNS`. Do not interpolate beyond observed HR/weather ranges.
5. Report all exclusions, paired speed changes, between-run variability and uncertainty; resample at the athlete/run level rather than treating thousands of seconds as independent athletes. Keep same-device and changed-device analyses separate.
6. Count a training-related improvement only as an observational signal exceeding measurement variation and supported by consistency/RPE context. Seasons, familiarity and recovery remain alternative explanations. A controlled comparative study is needed to attribute benefit to an Engine 2.0 policy.

## 4. Progression and injury risk

| Question | SCIENCE and limits | Research implication — HYPOTHESIS |
|---|---|---|
| Weekly “10% rule” | [Buist et al. (2008)](https://pubmed.ncbi.nlm.nih.gov/17940147/): 532 novice runners assigned to 13-week graded vs 8-week standard programs; injuries 20.8% vs 20.3%, no preventive effect detected. This compares whole programs, not every isolated progression percentage | Retain explicit assumptions if testing a weekly cap; do not label 10% an established safe boundary or conclude all progression limits are useless |
| Single-session spikes | [Frandsen et al. (2025)](https://doi.org/10.1136/bjsports-2024-109380): 5,205 runners/588,071 sessions; >10% beyond longest actual run in prior 30 days associated with overuse injury. Category hazard ratios 1.64, 1.52, 2.28 for progressively larger spike bands (not strictly monotonic). Weekly change did not show the same association | Evaluate session-distance history independently of weekly totals. These are relative hazards, not personal injury probabilities; observational associations do not prove a 10% prevention intervention |
| Frequency vs volume | [2022 systematic review](https://pmc.ncbi.nlm.nih.gov/articles/PMC9528699/) reports inconsistent associations across heterogeneous studies. The existing [7,391-runner cohort reference](https://pubmed.ncbi.nlm.nih.gov/39713859/) is baseline observational frequency, not a randomized fixed-volume frequency intervention | Compare fixed weekly dose with changed distribution, then separately changed dose at fixed frequency. Capture running days, number of sessions and recovery spacing distinctly; doubles are not two running days |
| Volume vs intensity progression | [Run Clever randomized trial](https://bmjopensem.bmj.com/content/4/1/e000333) detected no difference between its volume-focused and intensity-focused progression groups; eligibility, attrition and uncertainty constrain interpretation | Do not assume one lever is inherently safer. Preserve time/distance, hard-work dose and prior exposure separately |
| Long-run progression | Session-spike study addresses any session; Runna separately documents long-run build limits. Neither establishes a universal long-run share of weekly distance | Test absolute long-run duration/distance, prior tolerated exposures, session spike and weekly concentration separately; do not turn a 25–30% coaching convention into a validated law |
| Interruption | [Chen et al.](https://pubmed.ncbi.nlm.nih.gov/33517866/) found cardiopulmonary changes after two weeks of detraining in male endurance runners; [2024 review](https://pubmed.ncbi.nlm.nih.gov/38344385/) emphasizes limited and heterogeneous evidence | Distinguish holiday, reduced training, illness, injury and unknown missing records. No validated generic “lose X% capacity/day” coefficient is available here |
| Return after injury | [2024 tibial bone-stress-injury scoping review](https://doi.org/10.1007/s40279-024-02051-y) supports criteria-based, individualized return and identifies weak evidence for many specific protocols | Injury-related return belongs behind appropriate clinical clearance and symptom/context checks. Do not apply a generic break algorithm or infer clearance from improved HR |

Session-spike and baseline-frequency Garmin-RUNSAFE publications are related program evidence, not independent experimental replications. Their wearables cohort and self-reported outcomes can introduce selection and outcome-measurement bias. Absence of an association in one weekly metric is not proof that cumulative load has no relevance.

For research exposure calculations, retain a complete preceding window and distinguish confirmed rest from no recording. Never use a planned long run as the “longest actual prior run.” A zero or missing denominator yields an undefined spike, not infinity or zero risk. Preserve date/time and a rule for split files/doubles; sensitivity analyses should assess aggregation. Following a long interruption, a stale previous best is not proof of current tolerance.

Candidate safeguards to compare are: independent bounds on weekly distance and long-run/session exposure; no automatic make-up stacking; resume from recent verified exposure rather than an aspirational target; pause escalation when illness/pain is reported; and defer when key history is unknown. Their numeric limits remain uncalibrated hypotheses. Synthetic tests can check that safeguards execute; they cannot establish that they prevent injuries.

## 5. Adaptive coaching comparison

All rows below are **VENDOR documentation**, accessed on the review date. There were no authenticated account/device observations in this pass and no head-to-head effectiveness trial establishing a winner. Transfer the testable concept, not claimed accuracy or proprietary coefficients.

| System and source | Documented adaptation/control | Useful StrideLab research concept | Boundary or unknown |
|---|---|---|---|
| **Runna** — [Pace Insights, Aug 30 2026](https://support.runna.com/en/articles/10854865-what-are-pace-insight-recommendations-and-how-do-they-work); [safeguards, Aug 31 2026](https://support.runna.com/en/articles/15279082-how-does-runna-reduce-the-risk-of-injury-and-overtraining) | Pace recommendations follow workout trends and are optional; accepting changes pace targets. Separate weekly/long-run limits, return caps and conservative 6/7-day build documented | Separate performance calibration from schedule/dose revision; make eligibility and authority explicit | Page acknowledges weather, hills, sleep and fuelling limits. Exact formulas unknown. [Eligibility page](https://support.runna.com/en/articles/10855216-why-am-i-not-receiving-pace-insights) includes some pace-targeted long runs; version differences should be logged |
| **Garmin Run Coach** — [technology](https://www.garmin.com/en-US/garmin-technology/garmin-coach/garmin-run-coach/); [plan taxonomy](https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7) | Personalized day-to-day workouts use performance and health/recovery context; supported-device constraints apply | Prospective recommendation → actual activity → next recommendation ledger | Keep Run Coach separate from DSW, Expert Plans and Fitness Coach. Exact weighting and thresholds unknown; E008 has no verified pairs |
| **Kaizen** — [how it works](https://runkaizen.com/how-it-works) | Builds from running history, sets a weekly load target, translates it into distance and updates with actual training | Compare load-first guidance with separate dose constraints; make changing targets explainable | Proprietary load units and accuracy claims not independently verified. Equal score must not imply equal mechanical or recovery burden |
| **TrainerRoad** — [2026 AI update, revised Jun 23](https://www.trainerroad.com/blog/whats-new-with-trainerroad-ai/) | Predictive four-week simulation using training history, power/HR/RPE, schedule and goals. Selection no longer relies directly on Workout/Progression Levels. Alternatives and revert controls described | Future consequences, difficulty calibration and athlete choice; evaluate forecasting against simple baselines | [Older Adaptive Training](https://www.trainerroad.com/blog/how-to-use-adaptive-training/) used reactive progression/surveys; label historical. Cycling power/difficulty forecasting is not validated running injury forecasting |
| **Fitbod** — [algorithm, Jun 9 2026](https://fitbod.me/blog/fitbod-algorithm/); [help](https://help.fitbod.me/hc/en-us/sections/360001078993-Understanding-Fitbod-How-It-Works) | Separate exercise selection and load/sets/reps recommendations; prior logs, settings and estimated muscle recovery inform workouts; manual recovery adjustment available | Separate movement choice from dose; allow feedback to challenge model estimates | Recovery percentage is estimated, not measured tissue readiness. Vendor scale/“never overtrain” wording is not an independent safety guarantee |
| **RP Hypertrophy** — [app](https://rpstrength.com/pages/hypertrophy-app); [progressive overload explanation](https://rpstrength.com/blogs/articles/the-truth-about-progressive-overload); [release notes](https://releases.rpstrength.com/hypertrophy) | Week-to-week recommendations use pump, soreness, workload and performance feedback; joint-pain feedback is documented | Local tissue feedback, progression history and separate set allocation vs load progression | Pump/soreness are subjective signals, not direct measures of growth or running readiness; do not convert them into equivalent running kilometers |
| **Polar FitSpark** — [official support](https://support.polar.com/us-en/fitspark-daily-training-guide) | Daily cardio/strength/supportive alternatives based on fitness, training history and Nightly Recharge, updated after sessions | Bounded choices preserving session intent, with explicit readiness uncertainty | A daily suggestion system is not automatically a race-periodization or mixed-discipline optimization engine |

The common pattern is richer state plus feedback, with different scopes of authority. It does not justify collapsing performance, load, fatigue and injury into a single number. For mixed disciplines, maintain running exposure, strength sets/load/RIR, skill prerequisites and impact separately before evaluating any shared scheduler. No vendor row validates a kilometers-to-tonnage conversion.

## 6. Actionable StrideLab gap analysis

| Priority | Observed gap at reviewed baseline | Consequence | Next evidence needed |
|---|---|---|---|
| P0 | No admitted field-outcome dataset; rights/provenance incomplete | Cannot evaluate real aerobic benefit or fit a defensible forecast | E009 admission, then E010 repeatability |
| P0 | Local activity model exposes averages/laps but no explicit continuous HR/speed stream or weather/grade context contract in the inspected dataclass | Session averages cannot recover warm-up, HR lag, stable segments or decoupling | Source-only capability audit and independent time-series reference before any implementation |
| P1 | Field protocol has proposed matching windows but no empirical typical error | A change can be weather, sensor noise or recovery rather than adaptation | E010; prioritize measurement uncertainty over a fitness score |
| P1 | Synthetic structural checks are separate from observed outcomes; local lab and cloud B0/H1 are different implementations | More passes do not validate physiological policy coefficients | Reproduce exact baseline/provenance later; use independent evaluators and real-data splits |
| P1 | Weekly, session, frequency and interruption exposures not clinically calibrated | A single score/cap can conceal concentrated sessions or stale capacity | E011/E012; keep thresholds exploratory |
| P1 | Current Runna/Garmin captures remain empty | Vendor statements cannot explain actual matched outputs | Complete E007/E008 under authorized capture access |
| P2 | No calibrated predictive outcome model or comparative policy trial | Simulation-generated forecasts can reinforce their own assumptions | E013 shadow comparison; defer learned selector |

This is a scoped capability assessment, not an exhaustive audit of production Engine 1.5. The local README documents FIT/TCX/GPX import and reference checks; source inspection confirms session-level fields and hash provenance. This pass did not verify every parser path, run tests or establish admission of private activities. The existing synthetic corpus and parser fixtures remain SYNTHETIC/software evidence regardless of file naming.

## 7. Ranked next experiments — proposed, all NOT RUN

E009–E013 are new identifiers for this agenda. They do not change E001–E008 status. Rank reflects dependency and expected decision value, not a claim of statistical power. All numerical margins/sample targets below are proposals to preregister and review, not validated coaching thresholds.

### Rank 1 — E009: authorized-source admission and capability audit

**Question:** Can a small authorized source support the intended analysis without silently losing meaning or exceeding permission?

Acquire a metadata-only consent/rights manifest first, then a small set of opt-in original files in approved local storage. Include at least two device/sensor configurations if permitted; use manufactured fixtures for dropout/duplicate negative controls rather than corrupting originals. Independently compare timestamp, elapsed/timer semantics, distances, HR series and lap/step identity against source displays/export specifications. Retain original hashes and all exclusions. Do not commit raw recordings or participant references.

**Gate:** Every admitted record has a named permitted purpose and chain of custody; all required endpoint fields are verified or explicitly unavailable; no unexplained reference mismatch. Missing continuous samples blocks drift analysis even if average HR parses correctly. Matching hashes alone never passes rights review. **Output:** capability matrix and approved/blocked manifest, no efficacy result. **Effort:** small once permissions/files exist; access may dominate.

### Rank 2 — E010: matched-HR repeatability before adaptation claims

**Question:** Does a matched-condition metric distinguish a sustained change from ordinary within-athlete variation?

Pilot with approximately 8–12 consenting adult runners doing familiar training, aiming for at least six eligible repeated easy segments per athlete over roughly 3–4 weeks. This is a feasibility/reliability sample, not a powered efficacy trial. Compare same-route/sensor matched runs with unrestricted averages. Primary result: within-athlete typical error and eligible-run coverage; secondary results: RPE concordance, drift reproducibility and context sensitivity. Freeze segment selection, matching and exclusion rules before analyzing speed changes. Use athlete/run clustered uncertainty and leave-run-out checks.

**Gate:** Predeclare a smallest useful speed change with the research reviewer. If uncertainty/typical error is too large relative to that change, or exclusions leave inadequate coverage, report the metric unready. No automatic fitness label. **Output:** measurement error budget, exclusion log and protocol revision; no Engine 2.0 benefit claim. **Effort:** medium, prospective collection.

### Rank 3 — E011: independent session/weekly exposure replication

**Question:** Do separate session-history features add information beyond weekly summaries in an authorized injury-labelled dataset?

First review Groningen file license, label definition, time index and available session resolution. If only daily totals are available, single-session exposure is **not identifiable**; do not rename daily totals as sessions. Start with a descriptive reproduction and simple base-rate/previous-injury/weekly-exposure baselines. Compare additions of prior-30-day longest-session ratio, frequency, long-run duration and interruption history only where measured. Preserve natural event prevalence. Split by athlete and forward time; keep overlapping history windows out of opposite folds. Evaluate precision-recall, Brier/calibration, confidence intervals and alerts per athlete-week, not accuracy alone. Avoid future injury/status information in predictors.

**Gate:** No modeling until purpose-specific permission, usable labels and leakage audit. Associations remain observational; no individual risk display. A lack of incremental calibration on held-out data rejects the added complexity. **Output:** reproducible limits/associations, including nulls; no prevention claim. **Effort:** medium to large, conditional on resolution and access.

### Rank 4 — finish E007/E008; add E012 interruption/coordination observation

Complete the registered 5/6/7-day Runna drafts and prospective Run Coach pairs before expanding vendor case counts. Preserve input matching, app/firmware, plan family and rejected cases; never backfill a recommendation from a FIT activity. These are behavioral observations, not efficacy studies.

**E012 question:** How do actual interruptions and concurrent leg training affect tolerance/context at otherwise similar running exposures? Observe naturally occurring schedule breaks and strength sessions in consented familiar training; do not induce illness, injury or large load spikes. Record break reason, last verified exposure, planned vs actual dose, RPE, symptoms, missingness and subsequent recommendations. Compare policies offline only. Predefine clinician review for injury/illness cases and keep them separate from holidays. **Gate:** no hidden make-up sessions, no presuming missing history is rest, no model-issued medical clearance. Numeric return caps remain unknown. **Output:** case series and falsification cases, not a causal recovery formula. **Effort:** medium, event-dependent.

### Rank 5 — E013: context-aware shadow policy and ablation

**Question:** Does an interpretable H1 improve decisions over the exact unchanged local baseline without unnecessary revisions?

After E009/E010, freeze independent baseline/candidate versions and a held-out evaluation plan. Evaluate baseline, context-quality gate only, and full H1; ablate weather, grade and feedback separately. Use later consented observations unavailable during development. Policies propose changes in shadow mode without changing anyone's plan. Independently review disagreement, unnecessary-change rate, invalid recommendations, deferral coverage and uncertainty. Forecasts require calibration and a simple baseline; synthetic outcomes are not labels.

**Gate:** Zero known authority/data-integrity violations in evaluated cases, plus prespecified independent decision-quality improvement without excessive deferral. These criteria establish research behavior, not physiological safety. If benefit is absent, retain the simpler baseline. Only a separately designed and approved controlled prospective study could assess comparative training effectiveness; injury prevention would require its own suitable study size/follow-up. **Effort:** large; depends on earlier evidence.

## 8. Handoff and boundaries

Deliverables are this evidence review and a companion experiment-priority CSV. All new experiments are NOT RUN; terms checks are not data admissions. No raw/private datasets, FIT files, accounts, credentials or GPS were collected. No production source, coaching lab source, Trackside UI, workflow, automation or release state is changed by this documentation addition. Keep PR #34 draft/open/unmerged.

The next concrete action is E009 rights/provenance review for a small direct-export pilot and a source-only check of continuous-sample availability. If those inputs are unavailable, continue resolving dataset permissions and protocol measurement details; do not substitute another batch of synthetic success counts.
