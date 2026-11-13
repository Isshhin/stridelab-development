# Research addendum — High-frequency progression and Runna Score limitations

**Reviewed:** 2026-11-04 (Asia/Manila)  
**Evidence:** Public Runna vendor documentation, not independent current-app observation.  
**Scope:** Research only; no production changes or verified proprietary formulas.

## 1. Six- and seven-day runners receive a more conservative build rate

**DOCUMENTED (vendor statement).** Runna explicitly says that athletes running **six or seven days per week** receive a more conservative weekly build rate, to avoid compounding high existing load. This is a specific frequency-dependent constraint beyond the already documented fact that high-mileage runners have different mileage ceilings. The vendor does not disclose its formula or how much the rate changes.

Source: [How Does Runna Reduce the Risk of Injury and Overtraining?](https://support.runna.com/en/articles/15279082-how-does-runna-reduce-the-risk-of-injury-and-overtraining), published 2026-08-31; accessed 2026-11-04.

The same source documents (a) a **first-week-back mileage cap** after a break; (b) a separate **long-run cap after two or more weeks without a long run**; (c) rejection of goal/B-races beyond attainable long-run preparation; and (d) a pre-B-race long-run reduction. These are distinct constraints and should not be collapsed into a generic deload percentage.

**Engine 1.5:** No change proposed for the release branch. Existing bounded progression and safeguards remain the baseline.  
**Engine 2.0 hypothesis:** A frequency-conditioned build-rate constraint and interruption-specific return-to-running caps are candidate policies for testing, not established optimal physiology. Compare 5 vs 6 vs 7 days while holding recent total mileage, long-run history, plan length and race goal constant. **UNKNOWN:** numeric cap, whether frequency effects persist when mileage is matched, and interaction with strength training.

## 2. Runna Score is plan-relative and currently unadjusted for key confounders

**DOCUMENTED (vendor statement).** Runna describes its premium Runna Score as relative to the prescribed plan, **not a universal fitness scale**, with no fixed upper limit. The vendor explicitly says weather, terrain, fatigue, illness and life commitments are not yet accounted for. Changing estimated race time can change the score via pace recalibration. The article's example of a 28:00 to 25:00 5K and score 688 to 760 is **illustrative vendor material**, not a captured athlete output or a generalizable formula.

Source: [What Is My Runna Score?](https://support.runna.com/en/articles/7895279-what-is-my-runna-score), published 2026-09-07; accessed 2026-11-04.

**Reproducible calculation (illustrative vendor example only):** 28:00 = 1680 s, 25:00 = 1500 s. Time change = -180/1680 = **-10.7143%**. Score change = (760-688)/688 = **+10.4651%**. The similar percentages do **not** demonstrate a linear score formula: only two vendor illustrative points, multiple possible inputs, and no controlled observations.

**Engine 1.5:** Avoid treating any plan-relative compliance/progress score as a direct measure of aerobic improvement.  
**Engine 2.0:** Evaluate matched-heart-rate pace under comparable route, heat/humidity, elevation, fatigue and device conditions as a distinct longitudinal endpoint. Do not use Runna Score as a ground-truth label for aerobic efficiency.

## Garmin Run Coach and external science

No new controlled **Garmin Run Coach** plan output, decision threshold or longitudinal adaptation experiment was verified in this pass. Garmin Daily Suggested Workouts and Expert Coach plans remain separate comparators. These Runna vendor statements are not independent proof that the stated policies improve injury rates or running performance; no new causal exercise-science result was established in this addendum.

## Next reproducible tests

1. Runna: generate matched-input 5-, 6-, and 7-day plans at the **same** 60–70 km/week baseline and same recent longest run; record every weekly total and progression rate as `(week_n - week_(n-1))/week_(n-1)`, with deload weeks separately tagged.
2. Runna: after a naturally occurring interruption, compare first-week-back weekly and long-run caps with uninterrupted control outputs; avoid deliberately missing training.
3. Garmin Run Coach: capture recommendation **before** each workout, with device/firmware, plan phase, available load/recovery metrics and post-session next recommendation. Do not substitute DSW screenshots or completed FIT data.
4. StrideLab: prospectively compare easy pace at matched HR on similar routes and temperature/humidity; stratify loop vs road and hill exposure.

**Status:** Two additional vendor-documented distinctions; zero new independently observed generator samples, zero proprietary formula recoveries. 
