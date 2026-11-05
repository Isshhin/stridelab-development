# Runna workout feedback versus actual plan adaptation — evidence note (2026-11-05)

## Finding and source
Runna's first-party **Reviewing Your Workouts To Support Your Training** article (Sian/Nayth, dated 2026-09-10, accessed 2026-11-05): https://support.runna.com/en/articles/9973602-reviewing-your-workouts-to-support-your-training

**DOCUMENTED (vendor claim):** After a workout, athletes can give thumbs-up/down feedback and reasons such as "Paces too tough", "Workout felt too long", or "Not feeling 100%". The article explicitly says this feedback is **initially used internally by coaching/product teams**, and describes direct use for dynamic personal-plan adaptation as **future** functionality. Do **not** label a thumbs-down as an observed automatic change to next week's workout.

Compare Runna **Pace Insights** (dated 2026-08-31, accessed 2026-11-05): https://support.runna.com/en/articles/14656203-what-are-pace-insights-and-how-do-they-work . **DOCUMENTED:** eligible pace-target sessions can trigger pace-change recommendations; the athlete must accept before pace targets change. Pace Insights is a different pathway from post-workout qualitative review.

Compare **Workout Briefings** (accessed 2026-11-05): https://support.runna.com/en/articles/13169751-what-are-workout-briefings . **DOCUMENTED:** dynamic pre-session contextual advice includes prior-workout and weather insights, but its existence does **not** prove the prescribed workout itself changed.

## Research consequence
Keep separate fields for (a) athlete review submitted, (b) briefing text changed, (c) Pace Insights suggestion, (d) athlete acceptance, and (e) objectively observed prescription change. Capture the pre/post workout prescriptions and app/plan version to test actual adaptation. Do not infer causal changes from review submission alone.

**Uncertainties:** documentation may lag feature rollouts; exact eligibility, timing, and whether particular versions use feedback for individual plan changes remain **UNKNOWN** until authorized before/after app captures. No authenticated app-output experiment was performed for this note.

**Engine 1.5:** no production change. Its existing review/approval mechanism is an independent StrideLab design, not proof of Runna parity.

**Engine 2.0:** model subjective feedback, informational coaching, pace recalibration and structural plan changes as separate evidence and decision channels. Test whether subjective reports add predictive value beyond measured workout completion and pace.

No proprietary algorithm, numerical threshold or causal response was reconstructed. Research-only note.
