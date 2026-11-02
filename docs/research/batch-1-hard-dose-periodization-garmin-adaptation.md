# Batch 1 — Hard-work dose, periodization and Garmin actual-vs-planned training

Date: 2026-11-02. Research-only; no production code changes. Evidence categories: vendor-documented, arithmetic example, hypothesis, unknown. Companion to [master review](runna-garmin-coaching-algorithm.md).

## Priority 1 — What changes when Runna Difficulty changes?

**Vendor documented:** Runna separately exposes Training Volume (overall mileage and build rate) and Difficulty (hard workout amount and structure). Difficulty may change number of hard sessions, repetition counts, work duration, and faster segments in structured long runs. Changing either preference **does not itself change prescribed step pace targets**, which derive from estimated current race performance. The plan is regenerated and feasibility constraints can block certain combinations. [R1]

**Arithmetic only, not observed Runna output:** If a 3-minute repetition is reduced from 8 repetitions (24 minutes) to 6 (18 minutes), work dose falls 25% although rep pace is identical. Changes in recovery mode/duration could modify the physiological stimulus further. No public source gives the exact Comfortable/Balanced/Challenging coefficients, relative increases, or rep thresholds.

**Controlled observation:** At the same race estimate, current mileage, longest run, run days, race date, plan week, volume preference, and advanced settings, change ONLY Difficulty. Record complete before/after step trees, hard-work minutes, hard-session count, rest duration and mode, pace targets, total distance, long-run faster work, and any regenerated-week discontinuity. Repeat/reverse to assess stability. Exclude changes in plan eligibility from the main causal comparison.

## Priority 2 — How Runna periodizes intensity and recovery?

**Vendor documented:** Runna describes a base/general training stage followed by an event-specific key block with increased tempo/interval work [R3]. Its deloads reduce hard-session distance **more than** easy-running distance; long runs have separate protections, potentially independent of the normal deload schedule [R2]. For race plans, taper duration depends on distance and plan length; harder sessions are reduced or removed while enough easy training remains [R2]. Run Further / Train Your Way target-mileage plans specifically have THREE progression weeks then a lighter week [R4]; do not generalize this exact microcycle to every race plan. Runna describes 80/20 as contextual advice, not a fixed plan-generator fraction [R5].

**Model hypothesis:** Represent weekly volume, faster-running minutes, number of quality sessions, long-run km, hard segments in long runs, recovery spacing, and phase separately. Do not claim Runna internally uses this schema.

**Test:** Export detailed steps across base, build, deload and taper. Compute planned fast-step seconds, easy-step seconds, and total running time for each week. Quantify whether hard seconds decline more sharply than easy seconds during deload. A PDF listing a 10 km tempo day is NOT evidence that 10 km was performed at tempo pace. Use both work-duration and session-goal classification; specify the denominator.

## Priority 3 — Does Garmin compensate for a different-than-intended measured stimulus?

**Vendor documented:** Garmin Run Coach incorporates actual HR/pace, EPOC-related exercise load, recent/chronic load, fitness, sleep/recovery where supported, unplanned completed workouts, and event phase to adapt future suggestions [G1]. Garmin's sample Training Effect tables show that threshold intervals can produce a range of aerobic and anaerobic impact, and Training Effect can be unstable early in the device's fitness-learning period [G2].

**Unknown:** No Garmin source provides a rule such as 'intended threshold but measured tempo -> schedule threshold next'. Two compatible hypotheses remain: (A) adaptive load/recovery state changes next workout; (B) compliance or step-target performance changes next workout, perhaps alongside (A). Neither is established with a controlled pair of counterfactuals.

**Observation:** Capture Run Coach prescription BEFORE each session, exact origin (Run Coach vs DSW vs Expert), watch/firmware, timestamp/sync, available physiological metrics, actual step HR/pace, reported exercise load/Training Effect and next recommendation. Do not fabricate measurements or deliberately train through poor recovery. A Forerunner 165 study must not assume Training Readiness or load focus are exposed.

## Combined research implication

Engine 1.5 candidates: user-controlled hard-dose changes that preserve demonstrated speed ability, phase-aware planned hard-work accounting, and deload/recovery-spacing constraints. Engine 2.0: model intended vs measured stimulus, athlete-specific load/recovery, and uncertain future prescription effects. None is authorized for production by this review.

## Source register

- [R1] https://support.runna.com/en/articles/10393191-how-to-use-training-preferences
- [R2] https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan
- [R3] https://support.runna.com/en/articles/8456480-training-for-the-longer-term-goal
- [R4] https://support.runna.com/en/articles/11598695-how-to-use-and-manage-your-target-mileage-settings-on-run-further-and-train-your-way-plans
- [R5] https://support.runna.com/en/articles/9551650-80-20-training-principle
- [G1] https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [G2] https://www.garmin.com/en-GB/garmin-technology/running-science/physiological-measurements/training-effect-samples/

Evidence limitations: exact proprietary coefficients, matched personal plan outputs and controlled Garmin response traces remain unavailable. Commercial documentation is not a physiological safety standard.
