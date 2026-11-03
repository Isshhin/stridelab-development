# Batch 8C — Seeded adversarial evaluation, SQLite crash recovery, and B0/H1 disagreement audit

**Verified:** November 3, 2026 (Asia/Manila). **Execution:** [GitHub Actions run 41583816601](https://github.com/Isshhin/stridelab-development/actions/runs/41583816601), source commit `2c94b634cc72901981e35e1c581b10dc5f40ace1`. **Scope:** independent cloud *research* branch / draft PR #34. **No production Android, coaching Engine 1.0/1.5, user's local Python Athlete Simulation Lab, private FIT or protected synthetic dataset was modified or tested.**

## 1. Exact executed results

| Validation lane | Verified Actions log | Scope |
| --- | --- | --- |
| Newly generated adversarial synthetic examples | **288 PASS, 0 FAIL**, across **24 cohorts × 12 fixed-seed examples** (`seed=82603`) | Frozen Batch 8B cases were not used as test inputs; outcomes are **structural assertions**, not real training adaptation |
| Unit and integrity tests | **42 PASS** | Previous 21 unit/negative controls + 15 research SQLite tests + six held-out generator tests |
| B0/H1 Batch 8B paired synthetic fixtures | **55 structural PASS, 0 FAIL, 3 BLOCKED** | Same original 58 cases, same authored expectations; no new predictive calibration |
| Previous standalone toy-contract suite | **58 PASS, 0 FAIL** | Separate toy model, NOT additive to the B0/H1 pass count |
| B0 vs H1 action differences | **14/58 scenarios**, all 14 on changed (`variant`) side | Behavioral discrepancies requiring review; no empirical superiority verdict |
| User's original local lab or Android engine | **NOT TESTED** | Source not available in this cloud research branch |

Artifact: the workflow uploads `research-validation-results`, containing `research-validation.json`, `research-policy-comparison.json`, `research-heldout-results.json`, and `research-decision-differences.json` with per-case data and source/prototype SHA-256 provenance. Exact runner code:
- [heldout_runner.py](../../research/validation/heldout_runner.py): 24 deterministic synthetic cohorts; separate from Batch 8B's fixed 58-case input matrix.
- [test_heldout_runner.py](../../research/validation/test_heldout_runner.py): six reproducibility/negative-control tests.
- [sqlite_ledger.py](../../research/validation/sqlite_ledger.py) and [test_sqlite_ledger.py](../../research/validation/test_sqlite_ledger.py): 15 temporary-file SQLite tests including abrupt subprocess termination while an uncommitted approval or rollback transaction is in progress.
- [difference_review.py](../../research/validation/difference_review.py): reports exact B0/H1 decisions, provenance and limitations for every disagreement.

**Caution on the word "held-out":** these were *new seeded examples separate from the original fixed 58*, and were not used to retrain a model or adjust B0/H1 in this batch. They were still designed and checked by the same research effort, not by an independent scientific team, and cannot demonstrate generalization to real people or independent predictive validity.

## 2. Adversarial cohort coverage

Each cohort has 12 independently seeded variants drawn from synthetic capacities (25–110 km/week), distance/time and environmental values. The case registry **is not** a scientifically validated dose distribution. Cohorts:
`OVER_CAP`, `BELOW_CAP`, `TIME_OVER`, `PAIN_ONLY`,
`EQUIPMENT_GAP`, `SKILL_GAP`, `LOWER_OVERLAP`, `MISSING_ACTIVITY`,
`CAPACITY_UNKNOWN`, `RETURN_FROM_BREAK`, `SKIPPED_SESSION`,
`PARTIAL_QUALITY`, `HEAT_CONTEXT`, `MISSING_HR`,
`INVALID_STRESS`, `MUSCLE_ALLOCATION`, `NO_PREDICTOR`,
`PAIN_AND_CAP`, `CAP_AND_TIME`, `TIME_AND_SKILL`,
`CROSS_DOMAIN`, `STEP_SEPARATION`, `REPRODUCIBILITY`,
`UNOBSERVED_ACTUAL_STEPS`.

Independent checks require source-faithful step durations, distinct actual vs planned work, no missing-as-zero, numeric muscle-set conservation, separate running/strength doses, no prediction-as-observation, unchanged unapproved plans, consistent approval flags, and transparent priority when multiple constraints collide. Deliberately mutating H1 to `KEEP` is detected in capacity, pain, and time cohorts.

**Do not infer** that the held-out generator captures real physiological distributions, equipment-specific safe alternatives, true response to heat, evidence-based deload doses, race preparation, or the user's actual training history.

## 3. Manual review of all 14 B0/H1 action differences

The GitHub job logs list exactly the following 14 scenarios. **Every case remains OPEN_NOT_OUTCOME_VALIDATED.** The text below evaluates rationale/evidence quality, not which engine is a proven better coach.

| Case | H1 differs from B0=KEEP | What is structurally supported | Important potential false positive or missing evidence |
| --- | --- | --- | --- |
| E003:L06 | **PROPOSE_MOVE_SESSION** | Unplanned hard work can conflict with planned quality | Time separation, actual hard dose and athlete baseline not evaluated |
| E003:L12 | **DEFER_UNKNOWN** | Unverified volume capacity should not be imputed as a safe high-capacity dose | Could over-defer if adequate historical data exists elsewhere |
| E003:L17 | **DEFER_UNKNOWN** | Missing activity records are unknown, not three confirmed skipped workouts | Source-sync recovery and athlete confirmation are not implemented |
| E003:L18 | **PROPOSE_MOVE_SESSION** | Prior lower-body strength may overlap quality running | No validated recovery-hours threshold or per-muscle dose |
| E004:MD01 | **PROPOSE_MOVE_SESSION** | Leg-strength vs threshold overlap is recorded | Unclear actual proximity and whether upper-body session is an acceptable comparator |
| E004:MD02 | **PROPOSE_MOVE_SESSION** | Jump-heavy conditioning adds impact before long running | No landing-count or surface-based safe impact conversion |
| E004:MD07 | **PROPOSE_EXERCISE_SUBSTITUTION** | Missing required equipment is detected | No actual movement-equivalent equipment-free exercise selected |
| E004:MD09 | **DEFER_UNKNOWN** | Capacity after a training interruption is not proven by historical maximum | Return-from-break dose cannot be personalized from elapsed time alone |
| E004:MD11 | **DEFER_UNKNOWN** | Poor reported pull-up form should block automatic skill promotion | Form not independently assessed; possible under-progression |
| E004:MD12 | **DEFER_UNKNOWN** | Failed hold progression should not become mastered automatically | No regression and retraining dose generated |
| E004:MD13 | **DEFER_UNKNOWN** | One-arm pull-up prerequisites missing | Skill-tree validity and individual prerequisites unverified |
| E004:MD14 | **PROPOSE_EXERCISE_SUBSTITUTION** | Missing parallettes recognized | No confirmed safe equivalent and no quantified stimulus continuity |
| E004:MD20 | **DEFER_UNKNOWN** | Athlete pain report takes priority over ordinary schedule | Not an injury diagnosis or clinician clearance decision |
| E005:AL11 | **DEFER_UNKNOWN** | Explicit joint pain is not treated as ordinary fatigue | Need context and human assessment; no exact safe workload inferred |

**Audit categorization:** four *move-session* proposals (3 lower-body/impact overlap plus one unexpected hard workout), two *exercise-substitution* proposals (no actual substitute selected), and eight *deferrals* (missing information, skill prerequisites or pain). **Zero** have been independently shown to improve adherence, aerobic pace at matched HR, threshold capacity, strength gains or avoid injury. All 14 differences are recorded and flagged for review rather than labeled "correct."

### Prioritize review before considering H1 as a coach

- **High concern:** lower-body overlap uses a binary recent-session flag, not actual hours, intensity or athlete-specific recovery. Treat `PROPOSE_MOVE_SESSION` as a request for review, not a mandatory move.
- **High concern:** `PROPOSE_EXERCISE_SUBSTITUTION` does not yet generate a goal-equivalent feasible replacement. It should not be accepted as a completed workout prescription.
- **High concern:** subjective pain and technique report defer conservatively but do not independently measure medical readiness or skill competence.
- **Medium concern:** missing upload and unknown capacity trigger deferral without a verified reconciliation path; false-positive friction is possible.
- **No measured benefit:** B0 saying `KEEP` is not proof B0 is unsafe; H1 saying `DEFER` is not proof H1 is safer.

## 4. SQLite persistence and deliberate interruption tests

Added a distinct **research-only** SQLite revision store that uses `BEGIN IMMEDIATE` transactions and a small independent plan/proposals/append-only-activities/audit schema. Tests use disposable temporary databases and run in GitHub's Python 3.12 environment; they do **not** import Android Room, device user data, or the actual Athlete Simulation Lab.

Fifteen tests passed, covering:
- Durable initialization and idempotent activity inserts; conflicting duplicate activity IDs rejected.
- A proposed change never modifies the saved plan before approval.
- Explicit approve/reject, version increment, durable audit entries, exact rollback of latest approved plan, and preservation of completed workout records.
- Optimistic-version conflict rejection across two SQLite connections.
- Duplicate/replayed approval and rollback rejected.
- Injected exception between plan update and audit step rolls back the *whole transaction*.
- **Abrupt child-process termination** during uncommitted approval and during uncommitted rollback, followed by reopen, unchanged committed plan/history and `PRAGMA integrity_check = ok`.
- WAL mode used by the research store.

**Limits:** This demonstrates rollback of uncommitted SQLite transactions under controlled process termination on a GitHub Linux runner. It does **not** prove resilience to real power loss/hardware failure, all concurrent client races, Android app lifecycle, SQLite migrations, Room-specific interactions, encrypted databases or distributed sync conflicts.

## 5. Integrity and decision

**Batch 8C status: COMPLETE for preregistered cloud research scope.** All tested assertions passed; the controlled 14-decision review is captured and includes explicit shortcomings.

**Engine 2.0 release decision: NOT READY.** Structural correctness is necessary but insufficient. H1 has no demonstrated fitness benefits, session substitutions are not fully prescribed, and the user's original independent Athlete Simulation Lab remains unaccessed from this GitHub branch. Do not promote H1 into Android or claim that it is a reverse-engineered Runna/Garmin engine.

## 6. Next milestone — Batch 8D

1. **Independent evaluator review**: review priorities for 14 disagreement cases; design explicit athlete inputs or outcome checks for false positives. Require explanation, time-proximity and actual dose rather than simple category flags.
2. **Real B0/H1 policy lifecycle**: simulate multiweek complete/missed/unplanned work with persistent plans and replay; exercise rollback during multi-day changes, interruptions, and duplicate ingestion; keep results as synthetic.
3. **Actual old local lab adapter, only if source becomes accessible**: inspect schema/runner/manifest and keep protected datasets read-only. Do not assume Windows paths resolve in GitHub Actions.
4. **G4 real outcome protocol, not execution**: repeated easy pace at matched HR under comparable weather, route, fatigue, and hills; supported threshold effort and adherence. Requires authorized real-world longitudinal data.
5. **Runna/Garmin controlled branches** remain unexecuted (Runna E002 0/16; Garmin Run Coach paired snapshots unavailable). Never claim commercial algorithm coefficients.

Daily 09:00 Manila Runna/Garmin research automation remains active; do not duplicate. Research PR #34 remains draft/unmerged, with all production Android and protected data unchanged.
