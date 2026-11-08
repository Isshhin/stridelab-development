# P2-E DEV-0 — Multi-discipline synthetic coordination, Batches 1–4

Date: 2026-11-08 (Asia/Manila).
Repository: `Isshhin/stridelab-development`.
Branch: `research/p2e-dev0-batches1-4-20261108`, based on the isolated P2-D checkpoint `d826be24662a42553ade91544d16327134ed7b68`.
Scope: **research-only invented fixtures**. No live data, app integration, source ingestion, user workstation access, coaching optimization, clinical clearance, actual plan mutation, or Gate 2 authority.

## Research dependency

The frozen Session G P2-E track and the prior cross-discipline review distinguish mechanical/scheduling overlap from actual physiological response. Published concurrent-training group findings do not establish individual fatigue thresholds, universal 24/48-hour spacing requirements, or a common dose score. The already-registered Experiment 004 (MD01–MD22) remains **NOT RUN for real athletes**; this wave implements a separate, invented-data contract covering related structural counterexamples. The benchmark is not reinterpreted as empirical outcome evidence.

## Batch 1 — Discipline-native exposure accounting

`research/validation/p2e_exposure.py` describes version-scoped planned sessions and deduplicated invented source events. Running units are distance and separate easy/hard/recovery seconds; strength uses exercise identity, completed sets/reps/load, RiR when recorded, muscles and reported demand; calisthenics uses skill-node, reps, hold seconds, form and explicit prerequisite status; conditioning uses rounds, work/rest, landings when observed, impact type and reported demand.

The summary exposes **separate native totals**, never a common cross-discipline fatigue score. Observed completion cannot be reconstructed from a planned workout; missed upload is `unknown`, explicit confirmed skip is distinct, and partial dose remains partial. A missing RiR or landing count remains `null` rather than zero. It does not declare a skill mastered from repetition count or assume tonnage predicts tissue recovery.

At the snapshot's offset-aware decision-time cutoff, future/delayed reports are excluded. Exact same-event reimports are idempotent, conflicting duplicates fail, conflicting distinct source records for one session are not double-counted, and unlinked/stale/low-quality/unverified reports do not contribute dose. The explicitly asserted `synthetic_verified` link is **only a fixture property**, not authentication or lawful data admission. Cross-athlete and cross-plan records fail closed. The module supports one synthetic plan version and does not perform date relocation, source-identity resolution, athlete consent or FIT parsing. A session appearing earlier than its versioned scheduled time is excluded pending a separate relocation contract.

## Batch 2 — Cross-discipline review

`research/validation/p2e_coordination.py` compares an upcoming target session with the plan's explicit same-local-calendar-day opportunities and **eligible observed** source context. It records observations of reported high-demand lower-body strength, demanding lower-body calisthenics, or jump-heavy conditioning relative to a priority threshold, interval or long run, and can flag a verified priority run when reviewing a strength/skill target. It distinguishes genuinely observed overlap from planned-but-unverified sessions.

**Overlap is a flag for review, not proof of fatigue, interference, injury, physiological intolerance, a minimum recovery interval or a prohibition on combined training.** Upper-body and low-impact counterexamples remain separate; strength sets, calisthenics holds and jumps never convert to running km. Same-day grouping uses the recorded local date of each session, not an assumed UTC training day.

The structural calendar checks count distinct planned sessions and planned minutes under explicit invented daily limits. Unknown budgets defer; locked days, missing equipment or skill prerequisites produce separate review/deferral statuses. Capacity checks do not alter the workout. Hard stop, active/unknown/reported-clearance clinical restrictions and eligible reported pain take precedence over ordinary calendar or overlap review. An unverified clinical clearance cannot be accepted as medical approval.

## Batch 3 — Shadow comparison and non-authority

`review()` returns auditable research review categories (e.g. `REVIEW_OBSERVED_OVERLAP`, `REVIEW_CAPACITY_CONFLICT`, `DEFER_SYMPTOM_REVIEW`) plus native exposure, timestamps, evidence IDs and a coarse context. No actual alternative session, training dose, workout recommendation, numerical risk probability, HRV-derived readiness, medical clearance, arbitrary waiting time or adaptive plan revision is generated.

`compare_shadow()` places a calendar-only review baseline alongside contextual review **on the same invented opportunities**, including abstentions. It never declares a winner, effect size, forecast advantage or observed counterfactual. Results explicitly include `best_policy=null`, `effectiveness=null`, `new_plan=null`, `applied=false`, `coaching_authority=NONE_SYNTHETIC_RESEARCH`, and `gate2=CLOSED`. An immediate athlete stop requires no approval to *stop*, but any future saved plan change would require a separately authenticated approval and plan-version lock.

## Batch 4 — Hosted synthetic adversarial checks

`research/validation/test_p2e_wave.py` contains **90 deterministic synthetic tests** covering all four native dose dimensions, status/partial/unknown distinctions, exact duplicates, multi-source conflicts, athlete/plan isolation, time gating and stale/low-quality sources; equipment and skill claims;  same-local-day versus earlier-day overlap; false fatigue inference; explicit stop and clinical precedence; daily capacity/locked days; reproducibility, nonmutation, identical-denominator shadow comparisons and absence of claimed training outcomes.

The exact staged implementation and test files were executed using Python 3 in the hosted environment: **90 tests passed, 0 failed**. These are **author-side** synthetic tests, not GitHub Actions CI, not a full legacy regression sweep and not an independent code/security/scientific review. Earlier P2-A (31+40), P2-C (70) and P2-D (65) synthetic suites were not reexecuted in this wave.

## Unresolved risks and non-promotion boundaries

- Source authentication, admission rights, consent scope, custody, accurate FIT parsing and production linkage remain **unimplemented** in P2-E.
- Real workout history, athlete preference/priority resolution, sensor quality, verified skill prerequisites and actual follow-up are absent; fixture assertions cannot qualify a human participant or a machine.
- The local-calendar-day same-day flag is a research comparison convention, not a biologically justified interference window. Longitudinal training effects, delayed soreness, schedule moves, double sessions across midnight and session overlap with other identities need independent design and data.
- Conditional categories cannot establish the correctness of the assumed physiological mechanisms, selection of a real workout, or comparative effectiveness. No athlete-facing use of the outputs is permitted.
- `past_coverage_complete` refers only to explicitly enumerated earlier scheduled sessions in the synthetic snapshot; it is not proof of complete real-life activity acquisition.
- For clinical context the program withholds a training proposal; it is not a diagnosis, treatment rule, athlete clearance or substitute for expert assessment.
- Frozen Session G, P2-F custody, P2-B DEV-0, P2-A/C/D research files, Engine 1.5, Android/Trackside and research PR #34 remain unchanged.

**Author-side checkpoint:** `P2E_DEV0_BATCHES_1_TO_4_SYNTHETIC_IMPLEMENTED_HOSTED_TESTS_PASS`.

Independent code and scientific review, adversarial correction, and exact-head testing on infrastructure that does not use the user's PC must precede any claim of independent P2-E DEV-0 acceptance. Track-specific real-data qualification and Gate 2 remain closed.

**Next research wave:** cross-track P2-A/C/D/E integration-contract audit and independent review; identify missing source provenance and proposal-approval dependencies without authorizing real-data evaluation or automatic plan generation.
