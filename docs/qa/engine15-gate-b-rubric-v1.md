# Engine 1.5 Gate B — Precommitted Coaching Plausibility Rubric v1

**Frozen for Batch 1 BEFORE the new scenario harness produces candidate plan output.**
**Prepared:** 2026-11-14. **Reviewer of record:** StrideLab product owner, with an independent second reviewer of the full exported plan and trace when available. A reviewer who sees only excerpts must state that limitation; an assistant that generates a plan must not grade itself as the sole authority.
**Scope:** Research prototypes and generated plans. No injury-prevention, medical, physiological-readiness or efficacy certification.

## Scoring

For each dimension assign **0 critical/invalid**, **1 concerning/poor**, **2 acceptable with stated limitations**, **3 strong with explicit trace**, or **N/A** when the scenario legitimately lacks that feature. N/A requires a reason, not a hidden pass. Any critical hard stop fails Gate B regardless of average. For a candidate to pass a rubric review, every applicable dimension must be at least 2, all critical hard stops must be absent, two reviewers must document disagreements or limitations, and owner sign-off must identify exact generator/config/test set. This is an engineering-review gate, not proof of coaching effectiveness.

| Dimension | 0 / hard stop | 1: concerning | 2: acceptable | 3: strong |
| --- | --- | --- | --- | --- |
| **B1 Athlete evidence and starting capacity** | Invents missing activity, treats unknown as 0 or verified physiological ceiling, uses other athlete's records | Unjustified baseline from stale/uneven records | Shows four-week, two-week, completeness and conflicts; conservative on uncertainty | Explicit sensitivity to missingness/trends and athlete confirmation, reproducible evidence trail |
| **B2 Weekly workload trajectory** | Exceeds configured caps or forces impossible target | Abrupt/excessively stagnant, unexplained weekly changes | Explicit build/hold/recovery transitions within declared caps | Distinguishes absolute vs relative bind and explains achievable/later/unsupported projection |
| **B3 Long-run dose and concentration** | Silent share override, unreported spike or impossible individual session | Major unreasoned single-session jump | Independent recent-session and weekly-share constraints, clear conflict warnings | Repeatable history and available days affect proposal consistently |
| **B4 Quality and stimulus** | Prescribes unsupported intensity/pace or hidden hard dose, hard-week pileup | Same static hard dose forever; no eligibility rationale | Goal-appropriate easy/quality composition where supported; transparent exposure prerequisites | Structured work/recovery totals and dose progression suit athlete history, without false precision |
| **B5 Recovery and interruptions** | Makes up missed workload, implies medical clearance, silently prescribes intensity during unresolved pain/illness | Automatic recovery not explained, confuses taper with ordinary deload | Clear planned reduction and cautious interruption response without diagnosis | Explains distinct total/long/hard dose and appropriate conservative alternatives |
| **B6 Feasibility and schedule** | Overbooks availability, duplicates sessions, silently overrides conflicts | Plan technically fits but crowds recovery or misses athlete goal silently | Correct schedule/recovery, explicit conflict and infeasible state | Feasibility rationale shows alternatives without relaxing constraints |
| **B7 Explainability, stability and user agency** | Undocumented rule change or unapproved edits to saved/completed prescription | Explanation vague, impossible to trace rule source | Rule IDs and before/after values with stated evidence class and explicit unknowns | Deterministic replay, independent reviewer can reproduce every binding reason and policy version |
| **B8 Executable workout structure (M5a+)** | Step totals contradict session totals or player cannot represent prescription | Work/recovery/dose ambiguous | Typed warm-up/work/recovery/cool-down with consistent total and RPE/pace provenance | Fully executable step tree with clear progression and tested Android behavior |

## Mandatory hard stops (synthetic or human review)

- No negative or nonfinite distances/durations, no unbounded percentage step from zero baseline, no false certainty from missing data.
- No generated weekly distance beyond configured capacity/volume ceilings after combining all policy layers and rounding.
- No silently relaxed long-run share or individual-session constraint to fit a high target or three-day schedule; instead output an explicit conflict/infeasible decision.
- No hidden conversion of an unattainable target into a different goal, unauthorized race/date move or overwritten locked/completed workout.
- No more quality than evidence/eligibility permits; do not relabel work as easy to evade the policy.
- No clinical diagnosis, automatic medical clearance, missed-session catch-up, or pain/illness-based forced progression.
- No claims that synthetic validity demonstrates long-term fitness, physiological readiness or injury reduction.
- No fabricated Garmin/Runna proprietary algorithm assumptions, pace, HRV or training-load metrics.
- No mixing synthetic records with real-opt-in or manual sources without preserving origin and explicit consent for real records.

## Prespecified athlete and counterfactual cases

Every tested policy should export the whole weekly trajectory, declared athlete inputs, exact sources, confidence/unknown values, each rule fired and constraints binding, requested vs achievable target, and failure explanations. Do not selectively display only passing plans.

| ID | Precommitted scenario | Primary challenge |
| --- | --- | --- |
| R01 | Beginner at 15 km/week with three sustainable run days | Low-volume percentage arithmetic, quality introduction, long-run concentration |
| R02 | Developing runner at 30 km/week | Volume/quality balance and week-to-week recovery |
| R03 | Runner with three available run days and an aggressive weekly target | **Long-run share versus target conflict must be explicit, not auto-waived** |
| R04 | Experienced runner at 60 km/week targeting 80 km/week | Long-term feasibility, absolute/relative bind, goal-honest plan |
| R05 | High-mileage runner at 90 km/week | Large absolute jumps under pure percentage caps |
| R06 | Returning runner after several weeks off | No immediate historical-peak reuse; re-entry and quality |
| R07 | Four uneven weeks with a sharp two-week decline | Starting baseline and confidence |
| R08 | Incomplete recordings, missing-distance run and self-report disagreement | Unknown-versus-zero and no invented physiological capacity |
| R09 | One missed long run | No workout debt; independent next-long-run review |
| R10 | Several harder-than-prescribed easy runs, with heat/stress tags | Do not erase observed difficulty due to context |
| R11 | Successful threshold sessions and repeated easy reports | No automatic acceleration outside approved ceiling |
| R12 | Illness/pain flag | Immediate cautious prompt, no diagnosis/clearance or forced training |
| R13 | One overly difficult quality workout | Short-term reviewed recovery option versus future-week replan distinction |
| R14 | Input with insufficient days for minimum session composition | Explicit infeasible state, no silently added day |
| R15 | Long-run step limit conflicts with share cap and requested long-run length | Explicit conflicting constraints and a reproducible trace |

Review at least one full 8–16 week candidate trajectory where scenario inputs permit it. Include several policy configurations, including hold-only and single-relative-cap as research comparators, without labeling any one configuration 'safe'.

## Review protocol and artifact

1. Capture the exact source commit, generator version, config hash, fixture hash, frozen rubric version, and reviewer identity/role.
2. Run scenario generation **after** this rubric is committed. Preserve all outputs, including rejected and infeasible plans.
3. Score all applicable dimensions using source-and-trace evidence and write concrete objections with week/session IDs.
4. Document disagreement or unanswered questions. An external reviewer provided only pasted excerpts may critique those excerpts but cannot certify completeness.
5. **Gate B pass is separately recorded by the owner** after inspecting complete plans, traces and tests. No assistant-generated 'pass' substitutes for owner acceptance.

The first harness should only prove structural and behavioral repeatability. A later independent review can decide whether its training prescriptions are credible enough for actual personal use.
