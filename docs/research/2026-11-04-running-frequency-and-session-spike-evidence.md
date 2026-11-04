# Independent evidence addendum — frequency, running-session spikes, and identifiable comparisons

**Reviewed:** 2026-11-04 (Asia/Manila). **Evidence:** peer-reviewed observational studies; not Runna/Garmin proprietary plan observations. **Scope:** Engine 2.0 research only. No policy deployment, injury-risk prediction or production changes.

## 1. Baseline running frequency is not a causal frequency intervention

**INDEPENDENT SCIENTIFIC EVIDENCE — observational cohort:** A Garmin-RUNSAFE Running Health Study analysis of **7,391 runners** used self-reported prior three-month weekly running frequency, highest recent weekly distance, training experience and structured-plan participation at baseline, then prospectively tracked running-related injuries. Its abstract reports that runners training seven times weekly were among the groups with fewer new reported injuries when analyzed over 1,000 km, whereas low-frequency/low-volume runners experienced more. See [Using Self-Reported Training Characteristics to Better Understand Who Is More Likely to Sustain Running-Related Injuries Than Others](https://doi.org/10.1111/sms.70004), published online 2024-12-23; [PubMed](https://pubmed.ncbi.nlm.nih.gov/39713859/).

**LIMITATION:** This is not a random allocation from the *same* 65 km/week runner to 5, 6 and 7 days. Frequency and volume, fitness/experience, health status, plan adherence, exposure and injury vulnerability can all differ. Experienced injury-resistant athletes may self-select higher frequency; lower-frequency groups might include previously injured athletes. A favorable seven-day *association* cannot demonstrate that assigning seven days lowers injury risk or that a faster mileage increase is safe. Do not treat its reported cumulative risk differences as a causal training frequency coefficient.

## 2. One workout may be a relevant risk exposure independently of weekly total

**INDEPENDENT SCIENTIFIC EVIDENCE — observational cohort:** Frandsen et al., 2025 ([British Journal of Sports Medicine](https://doi.org/10.1136/bjsports-2024-109380); [PubMed PMID 40623829](https://pubmed.ncbi.nlm.nih.gov/40623829/)) followed **5,205 adult Garmin users and 588,071 recorded sessions**, with self-reported injury outcomes. A session exceeding the longest run over the preceding 30 days by **more than 10%** was associated with increased overuse-injury hazard relative to a reference including regressions and up-to-10% progression. Adjusted hazard ratios were **1.64 (95% CI 1.31–2.05)** for >10–30%, **1.52 (1.16–2.00)** for >30–100%, and **2.28 (1.50–3.48)** for >100% session-distance spikes. In the same analysis, week-to-week changes in running distance were not significantly associated with injury hazard; the ACWR results did not show a conventional positive-risk increase. The published author conclusion urges caution in interpreting weekly load ratios.

**LIMITATION:** Not a randomized training intervention and does not establish a universal 10% safety threshold, calibrated individual injury probability, or a causal protective effect of splitting runs. Exposure is based on **completed session distance compared with the preceding 30-day longest completed run**, not prescribed workout mileage vs a static synthetic 18 km input. Prospective outcomes were self-reported. The investigators discuss variation in exposure aggregation (closely spaced sessions), residual confounding, and measurement limits. The published data-availability statement says the dataset cannot be shared outside Aarhus University under the informed consent; this is **not** a source of harvestable athlete activities.

**DEPENDENCE:** Both papers arise from the broader **Garmin-RUNSAFE** research program; they are not two wholly independent experimental replications.

## 3. Consequence for matched 5/6/7-day Runna Experiment 007

At a **65 km/week** recent baseline, a hypothetical even distribution would be **13.00 km/day (5 days)**, **10.83 km/day (6)** and **9.29 km/day (7)**. These are *arithmetic illustrations*, not Runna prescriptions, and actual plans include long runs, workout warm-ups and uneven daily sessions. Higher frequency may change the size of individual exposures and time between recovery days even without changing weekly distance.

For E007, retain the registered **weekly progression** primary endpoint to test Runna's **DOCUMENTED VENDOR STATEMENT** about six/seven-day conservatism. Add a distinct secondary descriptive endpoint from **observed plan sessions** rather than infer sessions from weekly averages. See [session-level capture template](experiments/experiment-007-runna-frequency-sessions.csv). Capture scheduled date, day of week, session category, *full prescribed* distance and distinct hard-work dose, long-run identity and source. The template stays header-only pending mobile captures; no plan sessions have been observed.

After collecting **actual activity data under separate consent**, a different field-outcome study could evaluate distance of each completed session against that athlete's *longest actual session during the previous 30 days*, along with strength-training interference, surface/elevation, heat/humidity, fatigue, and illness. Avoid transferring an observed population association into a deterministic universal 10% auto-rejection rule; thresholds are research flags until independently validated.

| Question | Evidence currently supports | What remains unknown |
|---|---|---|
| Does Runna numerically build 6/7-day plans more conservatively than matched 5-day plans? | Vendor assertion only | No authenticated 5/6/7 matched plan outputs; 0/6 drafts |
| Is running 7 days safer at fixed 65 km/week for an otherwise identical athlete? | No causal test; baseline cohort association only | Injury/fitness outcomes under matched exposure and adherence |
| Could single-session progression matter even if weekly progression appears stable? | Observational association in 5,205 runners | Individually calibrated prospective thresholds and response to intervention |
| Does Runna's distribution reduce single-session exposure for 7-day plans? | Arithmetic possibility only | Session-level actual generated prescriptions and history |

## Evidence categories

- **Documented vendor statement:** Runna claims conservative progression for six/seven training days. Not a recovered formula.
- **Directly observed app behavior:** None in E007, 0/6 matched plans. No Garmin Run Coach pre/post recommendations in E008.
- **Independent scientific evidence:** Two observational Garmin-RUNSAFE analyses above, with dependent program and confounding.
- **StrideLab hypothesis:** Joint frequency-conditioned progression, largest-session exposure and strength interference may be more informative than one weekly-mileage cap.
- **Synthetic experiment:** Even-distribution arithmetic, synthetic validator fixtures, not evidence of athlete safety.
- **Real athlete observation:** None admitted in this research addendum.

**Decision:** Keep the Runna primary frequency experiment registered and blocked until authorized current app captures exist. Do not change Engine 1.5, Trackside or live Engine 2.0 behavior based on these publications.
