# Runna strength sessions and running-plan adaptation boundary — 2026-11-05

**Checked:** 2026-11-05 (Asia/Manila). **Evidence class:** VENDOR-DOCUMENTED only; not authenticated app observation. **Scope:** research documentation only.

## Finding

Runna's support article dated **2026-09-22** explicitly states that adding strength or other additional sessions does **not** change its running sessions and that strength sessions are outside its adaptive running plan, including adherence, intensity guidance and plan adaptations. This is a substantive boundary on *documented Runna behavior*, not a claim about the internal algorithm or Garmin Run Coach.

Primary source: Runna, *Adding Strength Training to Your Runna Plan*, https://support.runna.com/en/articles/15624879-adding-strength-training-to-your-runna-plan (published 2026-09-22; accessed 2026-11-05), FAQ **“Will adding strength or additional sessions affect my running plan?”**

The same source documents user-configurable strength ability, goal, equipment, 30/45/60-minute sessions, and up to four sessions per week; strength exercise substitutions via equipment changes do not establish running-load feedback. It recommends separating lower-body lifting from intervals/long runs. These are product descriptions/recommendations, not demonstrated outcomes.

## Interpretation and limitations

- **DOCUMENTED:** Strength session addition is not a driver of running-plan adaptations according to the vendor's current FAQ.
- **UNKNOWN:** Whether the app enforces scheduling conflicts, uses any implicit global fatigue signal derived from other connected data, or changes this boundary in later versions. The FAQ is not an instrumented test.
- **NOT EVIDENCE:** This does not show that Garmin Run Coach or Garmin Daily Suggested Workouts ignore strength. Their distinct products and device-specific load inputs must be studied separately.
- **SCIENCE:** No new controlled intervention or outcome evidence is asserted here. The claim is about software behavior, not the physiological value of strength training.

## StrideLab implications

**Engine 1.5:** No production change. Preserve existing deterministic, review-only and interference-aware mixed-session scheduling; do not copy vendor exclusions without testing.

**Engine 2.0 hypothesis:** Model cross-discipline *scheduling interference* separately from *physiological training response*. Candidate experiment: compare the same 65–70 km running week with (A) no strength, (B) two easy upper-body sessions, and (C) two hard lower-body sessions; evaluate feasibility, workout spacing, next-day subjective soreness, actual run quality and adherence. No automatic intensity penalty or quantitative injury threshold is justified by this vendor FAQ.

## Next verification

Capture authorized Runna plan before and after adding strength sessions at fixed plan inputs and version; compare every running session's date, type, duration/distance and pace targets, and repeat after actual strength completion. Record app version, settings, snapshots and any manual scheduling changes. Avoid concluding that unchanged planned runs imply no physiological interaction.

No private algorithm was reconstructed, no app output was fabricated, and no production code was modified.
