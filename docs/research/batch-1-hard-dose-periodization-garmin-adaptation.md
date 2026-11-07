# Batch 1: Workout difficulty, training phases, and Garmin adaptation

Research notes from November 2, 2026. This document is for the Coaching Research Lab, not the production app. It brings together what Runna and Garmin have publicly documented, what we can calculate, and what we still need to test. The [main research review](runna-garmin-coaching-algorithm.md) has more background.

We use four labels throughout: **vendor documentation**, **illustrative calculation**, **research hypothesis**, and **unknown**. The labels matter because the apps do not publish the exact rules behind their plan generators.

## 1. What changes when someone adjusts Runna's Difficulty setting?

**What Runna documents**

Runna separates **Training Volume** from **Difficulty**. Training Volume affects overall mileage and how that mileage builds. Difficulty can affect how much hard running a plan contains, including the number of quality sessions, repetitions, work duration, and faster sections of long runs.

Changing either setting does not, by itself, change the pace targets assigned to individual workout steps. Runna bases those targets on its estimate of the runner's current race performance. The app regenerates the plan after a preference change, and some combinations may not be available. [R1]

**An example, not a measured Runna result**

Suppose a workout changes from eight 3-minute repetitions to six. Hard-running time drops from 24 minutes to 18 minutes, a 25% reduction, even if the repetition pace stays the same. A change in the length or type of recovery could also change how demanding the session feels.

We do not know Runna's exact coefficients for Comfortable, Balanced, or Challenging difficulty, or its rules for adding and removing repetitions.

**How we would test it**

Create two plans with the same race estimate, current mileage, longest run, running days, race date, plan week, training volume setting, and advanced preferences. Change only Difficulty.

Save the full workout steps before and after the change. Compare hard-running minutes, number of quality sessions, repetitions, recovery duration and type, pace targets, total distance, and faster sections of long runs. Repeat the change in reverse to see whether the output is stable.

If a change makes the plan ineligible, record that separately. It is not a clean comparison of workout dose.

## 2. How does Runna vary training across phases and recovery weeks?

**What Runna documents**

Runna describes general or base training followed by a more event-specific block with more tempo or interval work. [R3]

During deload weeks, hard-running distance can fall more than easy-running distance. Long runs have their own protections and may not follow the normal deload pattern. In race plans, taper length depends on the event distance and the length of the plan. Harder sessions may become shorter or disappear, while some easier training remains. [R2]

Run Further and Train Your Way target-mileage plans specifically use three progression weeks followed by a lighter week. That does **not** establish the same pattern for every Runna race plan. [R4]

Runna also discusses 80/20 training as general guidance. It does not document a fixed 80/20 ratio that every generated plan must follow. [R5]

**What we might model in StrideLab**

As a research hypothesis, track weekly mileage, faster-running time, quality-session count, long-run distance, faster work inside long runs, recovery spacing, and training phase as separate values.

That would help us understand changes in training dose without assuming Runna uses the same internal model.

**How we would test it**

Collect detailed workout steps from base, build, deload, and taper weeks. Calculate planned time in faster steps, planned time in easy steps, and total running time for each week.

Then compare how much fast-running time and easy-running time change during a deload. Be explicit about which sessions and segments count toward each number.

A plan that labels a session "10 km tempo" does not prove all 10 km were run at tempo pace. We need the actual workout steps or a clearly stated limitation.

## 3. How does Garmin respond when the completed workout differs from the plan?

**What Garmin documents**

Garmin Run Coach can use recorded heart rate and pace, exercise load related to EPOC, recent and longer-term training load, fitness, recovery information, and unplanned completed activities to adjust future suggestions. The exact information available depends on the device. [G1]

Garmin's published Training Effect examples also show that a threshold interval workout can produce different aerobic and anaerobic effects. Training Effect may be less stable while the device is still learning about a runner's fitness. [G2]

**What we do not know**

Garmin does not publicly document a rule such as: "If the runner was prescribed threshold but completed something closer to tempo, schedule another threshold session next."

There are at least two plausible explanations for changes in the next recommendation:

1. The completed workout changes Garmin's training load and recovery estimates, which then affect the next session.
2. Garmin also considers how closely the athlete followed the prescribed workout steps.

Both are hypotheses. We do not have matched observations that separate them.

**How we would test it**

Before each session, save the Run Coach recommendation and note whether it came from Run Coach, Daily Suggested Workouts, or an Expert plan. Record the watch model, firmware, date, sync state, and the physiological metrics actually available.

After the session, record completed workout steps, heart rate, pace, exercise load, Training Effect, and the next recommendation. Compare cases carefully, without inventing missing measurements or intentionally training through poor recovery.

For a Forerunner 165, we must not assume that features such as Training Readiness or load focus are available.

## What this means for StrideLab

For **Engine 1.5**, the most practical research candidates are a user-adjustable hard-work dose that does not automatically change pace targets, better accounting for hard running across training phases, and clearer deload and recovery-spacing checks.

For **Engine 2.0**, the longer-term questions are how to distinguish the intended workout from the workout the athlete actually completed, how to interpret load and recovery together, and how confidently those measurements should change the next prescription.

These are research directions, not approval to move the rules into the Android app.

## Sources

- [R1] https://support.runna.com/en/articles/10393191-how-to-use-training-preferences
- [R2] https://support.runna.com/en/articles/15272605-how-does-runna-build-recovery-into-your-training-plan
- [R3] https://support.runna.com/en/articles/8456480-training-for-the-longer-term-goal
- [R4] https://support.runna.com/en/articles/11598695-how-to-use-and-manage-your-target-mileage-settings-on-run-further-and-train-your-way-plans
- [R5] https://support.runna.com/en/articles/9551650-80-20-training-principle
- [G1] https://www.garmin.com/en-GB/garmin-technology/garmin-coach/garmin-run-coach/
- [G2] https://www.garmin.com/en-GB/garmin-technology/running-science/physiological-measurements/training-effect-samples/

**Limits of this review:** We do not have Runna's proprietary coefficients, matched outputs from an active personal Runna plan, or controlled Garmin adaptation traces. Vendor documentation is useful evidence of product behavior, but it is not a physiological safety standard.
