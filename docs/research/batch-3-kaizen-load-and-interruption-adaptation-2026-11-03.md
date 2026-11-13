# Batch 3 (2026-11-03) — Adaptive weekly load and interruption handling

**Track:** independent StrideLab Coaching Algorithm Research Lab (Engine 2.0). **Status:** vendor-documentation evidence and original test design only. **Not:** controlled current-app output, recovered proprietary algorithms, proven effective training rules, or Android implementation. **Production changes:** none.

## Why this batch
Previous batches reviewed Runna workout dose and phase-specific recovery, Garmin Run Coach adaptation, external/internal load, matched-HR easy pace, and workout feedback vs plan changes. Experiment 002 remains **0/16 current Runna plan outputs** because controlled generation requires authorized mobile access. This batch compares another architectural pattern and specifies tests that do not depend on reverse engineering proprietary internals.

## Vendor-documented behavior

### K1. Kaizen: set weekly load first, then translate into a distance guide
Kaizen says it derives baseline training from past running, assigns a target training load, and translates it into a changeable weekly distance target. More demanding running can reduce the distance needed for the target; easier running can increase it. Past races help calibrate its *best-case* performance projections. Kaizen also says missed work can be redistributed responsibly to later days **or** future weeks. This is vendor-described behavior, not independent validation of its models.

- Primary: https://runkaizen.com/how-it-works
- Secondary product overview: https://runkaizen.com/

**Unknown:** exact load units, weighting by HR/elevation, fatigue accumulation, weekly load redistribution constraints, forecast accuracy in independent samples, and whether matched-HR easy pace directly drives targets. Do not treat weekly km as physiologically exchangeable with hard-work minutes or model a Kaizen-specific coefficient from marketing copy.

### R1. Runna: distinguish one missed workout from multi-session interruption
Runna documents Plan Realignment surfacing after **more than three missed workouts, or a week** of training, typically on Monday; it describes leaving missed workouts unticked to trigger it. For a single miss, it describes rescheduling or skipping, rather than necessarily rebuilding the entire plan. Larger interruption options may include restart, rebuild toward existing end date, or continuation unchanged. These are user-facing feature behaviors, **not** internal adaptation thresholds transferable as safe StrideLab rules.

- Primary: https://support.runna.com/en/articles/10026375-how-to-use-the-plan-realignment-feature

### R2. Runna: explicit temporary recovery mode
Runna's Not Feeling 100% feature permits a user-selected **3–14 day** reduction/pause with a gradual return. Possible reductions include difficulty reduction, easy + long only, short easy only, or no running. Return choices vary; some beginner/return plan families use frequency modifications rather than pace/intensity adjustments.

- Primary: https://support.runna.com/en/articles/12809806-feeling-unwell-how-to-adapt-your-training-plan
- Additional: https://support.runna.com/en/articles/14618279-how-not-feeling-100-works-on-new-to-running-return-to-running-path-to-parkrun-plans

**Unknown:** precise dose gradients, criteria for return style, causal injury-prevention effectiveness, and how plan continuity across a race taper is mathematically constrained.

### G1. Garmin Run Coach and DSW are not the same system
Garmin Run Coach's vendor page says missed and unplanned workouts and available physiological/recovery information can affect future sessions. Garmin's DSW material separately describes suggestions based on fitness, recent training and recovery; a scheduled event plan can take precedence over DSW. Garmin Expert Coach plans are a third category and should not be conflated with Run Coach.

- Run Coach: https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- Garmin plan taxonomy: https://support.garmin.com/en-US/?faq=IkvWNeIoSd48GIYCjkhlo7
- DSW: https://www.garmin.com/en-US/blog/fitness/daily-workout-suggestions-for-runners/
- DSW and plan precedence: https://www.garmin.com/en-GB/blog/three-ways-your-garmin-smartwatch-can-help-you-work-out/

**Unknown:** true proprietary per-day decision rule, metric weights, device-compatibility-specific behavior, and whether Garmin Run Coach explicitly replaces or merely reschedules missed hard stimulus. Do not use DSW examples as controlled Run Coach output.

## Proposed StrideLab architecture (original hypothesis, NOT implemented)

**Layer A: longer-horizon goal and capacity guardrails.** Maintain event/fitness goal, available run days, weekly volume *range*, evidence-backed training baseline, recent longest run, phased progression, taper/deload commitments, and hard-work/recovery spacing. Forecast dose in multiple independently interpretable units (km, moving minutes, step-level hard seconds, long-run share); never blindly add unlike units.

**Layer B: near-term daily recommender.** Consume completed vs partial vs skipped vs unknown plans, unplanned recorded activity, subjective status, sensor confidence, environmental context and current phase; issue a narrowly scoped next-session proposal with reason/confidence/constraints. Keep the original plan and an audit of before/after changes. Large changes require athlete approval.

**Layer C: interruption and resumption states.** Separate routine missed session, missed quality, consecutive missed days, temporary voluntary reduction, suspected under-recovery, and extended time off. Never assume an unrecorded run is a missed run. Escalate from local schedule repair to user-approved phase-aware rebuild only when necessary.

**Layer D: independent progress measurement.** For the priority outcome, compare *speed at similar heart rate* across repeated road sessions with matched heat, route/grade, sensor, stops, sleep and training proximity. Keep tight-loop runs as a separate condition. Report median and uncertainty; do not substitute raw GPS pace, Garmin VO2max or event time for the chosen endpoint.

## Falsifiable Athlete Simulation Lab cases (future work; all NOT RUN)

| ID | Input contrast | Expected invariant/check, not an assumed vendor behavior |
| --- | --- | --- |
| SL-A01 | 65 km planned vs 65 km completed at higher internal load / higher heat | Do not declare identical physiological cost from distance alone; record uncertainty |
| SL-A02 | One missed easy session, otherwise normal week | No automatic hard-work catch-up or sudden long-run enlargement |
| SL-A03 | One missed quality workout, next days within easy/long commitments | Never assume the skipped threshold stimulus was completed; no automatic doubling |
| SL-A04 | Multiple consecutive missed weeks near planned taper | Preserve race-date and taper feasibility or recommend an explicit revision |
| SL-A05 | Voluntary 7-day reduced-load period | Gradual return based on actual completed exposure, not calendar days alone |
| SL-A06 | Unplanned hard activity reported after scheduling | Detect possible conflict with subsequent hard/long session, propose revising with rationale |
| SL-A07 | Two identical 10 km activities in materially different heat/hills/HR | Preserve external vs internal distinction and context |
| SL-A08 | 70 km/week high-mileage developing-speed runner, recent 15-20 km long | Keep workload capacity ceiling and explicit quality-dose limits |
| SL-A09 | Beginner with no known quality history | Treat missing prior exposure as unknown, not proof of zero ability; do not force quality |
| SL-A10 | 4 completed easy runs after skipped quality, but severe date spacing / uncertain recovery | Do not use a simple count of clean runs as a clinical readiness claim |

**Measurement contract:** for each case, emit input provenance and data completeness, planned-vs-actual dose components, phase, constraints, outcome proposal, confidence, violation reasons, and reproducible seed. Compare to existing conservative/history-scaled/completion-aware/return-to-quality baselines. Test held-out adversarial cases; structural validity alone is not coaching effectiveness.

## Next batches (priority order)

1. **Batch 4, no app credentials needed:** write a constrained load-first vs distance-first design comparison, define what observable fields would distinguish policies, register simulator scenarios and acceptance criteria. Avoid arbitrary load coefficients; gather sports-science support for any proposed combination.
2. **Batch 5, with authorized data:** execute Experiment 002 Runna baseline repeats and controlled variants (B00/B01, mileage, longest run, difficulty, volume, frequency, performance, ability). Keep rejected inputs; 0/16 completed as of this note.
3. **Batch 6, with compatible Garmin screenshots:** capture *Run Coach* before/after snapshots, watch firmware, sync time and actual session metrics. DSW is a separate comparator. No deliberately induced unsafe sessions.
4. **Batch 7:** extend the same audit framework to Fitbod, Thenx, Freeletics, then secondary apps TrainerRoad, RP Hypertrophy App, Calistree, TrainingPeaks. Prioritize real actionable evidence over app count.
5. **Validation gate:** independent robustness, plausible safety, uncertainty and actual athlete opt-in observational evaluation before any Engine 2.0 production candidate.

## Strict handoff boundaries
- Separate cloud GitHub research PR #34 from local independent Python Athlete Simulation Lab and Android production. No GitHub change here proves a local lab change. Local lab historical checkpoints are summarized in TickTick's '05. Coaching Research Lab and dataset' note; verify current local repo before modifying it.
- Original read-only synthetic dataset must remain unchanged; no private FIT files committed.
- PR #34 remains research-only and draft/unmerged unless independently verified otherwise.
- Existing daily 09:00 Asia/Manila research automation exists; do not duplicate it.
- Vendor features and marketing accuracy claims are *not* clinical evidence, proven general coaching rules, or independently reproduced current-app results.
