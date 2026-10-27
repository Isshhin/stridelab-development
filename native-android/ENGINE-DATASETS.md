# StrideLab — Versioned Engine Validation Datasets (V1)

**Status:** Synthetic fixture pack proposed in draft PR. **Not** Unified Coaching Engine 2.0 implementation, model weights, a real-world training corpus, physiological ground truth, a coaching certification, or authorization to enable automatic plan changes.  
**Pinned integration baseline:** `25be8f3828374d1e539c7e825afa20fbcb652b56`.  
**Privacy:** All athletes, sessions, identities, activities, dates, wellness values and provider names in these files are **invented test data**. No account connections, medical records, Garmin exports, private running logs, or AI-generated personal profiles are used.

## Why these datasets are different

StrideLab is designed as **one deterministic offline coach** coordinating running, strength, calisthenics and conditioning. A reliable engine needs reproducible tests **before** it needs a large volume of synthetic rows. We therefore separate:

1. **Executable Engine 1.0 contracts** — examples with specific accepted/rejected `PlanRequest` inputs that the *current engine* must evaluate truthfully.
2. **Unified Engine 2.0 athlete timelines** — multisport history and recovery edge cases with *expected safety/behavior checks*, **not** prespecified optimal training prescriptions or proven recommendations.
3. **Existing exercise knowledge** — the shipped `native-library/catalog.json` currently provides **44** catalog entries and remains the source for movement IDs and available media/progression metadata. This PR does **not** replace that catalog with fictitious exercise facts.

## Files and current coverage

| File | Purpose | Contents |
|---|---|---|
| `app/src/test/resources/engine/engine1_plan_cases_v1.json` | Current Engine 1.0 generation contract | **22** labeled cases: **10 accepted**, **12 rejected**. Beginner/no-watch, 5K/10K, strength, calisthenics, mixed disciplines, coordinated goals, multi-session scheduling, return from a break, over-constraint, invalid equipment/quality/policy/time inputs. |
| `app/src/test/resources/engine/synthetic_athlete_scenarios_v1.json` | Future Engine 2.0 behavior/safety fixtures | **10** independent four-week scenarios, **117** synthetic activity rows and **9** synthetic wellness observations. No data science validity is claimed. |
| `app/src/test/java/io/stridelab/privatealpha/EngineDatasetTest.kt` | JVM acceptance and structure checks | Runs accepted/rejected cases through actual Engine 1.0 policy selection and generation. Checks determinism, session counts, bounded dates, policy IDs, no fabricated actuals/mileage, quality/long-day rules, daily budgets and compatibility; parses synthetic history structure. |
| `tools/tests/test_engine_datasets.py` | Lightweight cross-platform checks | Validates fixture IDs, version, required data, absence of invented values on unrecorded rows, exercise IDs against the shipped catalog, duplicate provider identity test cases and explicit synthetic disclaimers. |
| `tools/generate_engine_synthetic.py` | Scalable adversarial fixtures (generated on demand, not checked into app assets) | Fixed-seed generator for **1–1000** fictional athlete histories; varies activity availability, discipline combinations, wellness missingness, explicit skipped sessions, and sometimes duplicate synthetic provider IDs. Never generates approved "optimal" coaching labels. |
| `tools/tests/test_engine_synthetic_generator.py` | Seed/volume/privacy invariants | Same seed produces identical bytes; different seeds vary outputs; invalid counts are refused and nonrecorded activities have no fabricated actual values. |

### Athlete scenarios included

- **S-001:** Beginner run/walk, no watch, no pace/HR/distance estimation from missing records.
- **S-002:** Consistent recreational runner, real-vs-target separation in pacing and volume logic.
- **S-003:** Running + lower-body strength + calisthenics with cross-discipline scheduling.
- **S-004:** *Synthetic* self-reported illness/break; reduced frequency, missing subsequent wellness and return-request approval.
- **S-005:** The same *synthetic physical run* supplied by two different *synthetic* providers; tests no double counting and conservative linking.
- **S-006:** Strength-only repetition/load history; avoid invented one-rep-max or unapproved overload.
- **S-007:** Calisthenics practice without automatic level promotion.
- **S-008:** Offline training history with genuine absence of GPS/HR/sync streams.
- **S-009:** Training-equipment availability changes during travel.
- **S-010:** Higher-volume running history, then interrupted/partial week with incomplete wellness; no catch-up load.

Scenarios deliberately vary data availability and use `null`/omission for absent values. Missing data is **not zero**, **not a negative workout**, and **not permission to invent a metric**.

## Data contracts

**Engine 1.0 cases:** Each row supplies `id`, `policy`, `expected` (`accept` or `reject`), and a partial `request` object using *existing* `PlanRequest` field names. The Kotlin test merges values into the current `PlanRequest` default serialization, then decodes with the production contract. `start`, owner, plan ID and clock are fixed for repeatability. No hand-written random expected timestamps or race predictions.

**Future athlete histories:** `profile` describes a fictional athlete. `records` distinguish `recorded`, `planned` and `skipped`; discipline IDs are `run`, `str`, `cal` and `cond`. Recorded measurements carry units in the top-level `units` dictionary. Optional `sets` reference real catalog IDs, while any load/rep number is an invented fixture value. `wellness` entries use explicit `null` for unavailable values. `events` are fictional user/sync/equipment context. `requiredChecks` are **test specifications for later Engine 2.0 behavior**, not validated labels, personalized advice or outcomes. Source names always begin with `synthetic-`.

No individual provides more than the available source; do not turn a hypothetical RHR/HRV change into a medical diagnosis or deterministic training readiness score.

## How to run

From `native-android/`:

```bash
python3 -m unittest discover -s tools/tests -p 'test_engine_datasets.py' -v
python3 -m unittest discover -s tools/tests -p 'test_engine_synthetic_generator.py' -v
python3 tools/generate_engine_synthetic.py --seed 20261027 --count 100 --output /tmp/stridelab-synthetic-scenarios.json
gradle --no-daemon :app:testDebugUnitTest --tests 'io.stridelab.privatealpha.EngineDatasetTest'
```

The seeded generator's default output is standard output; `--output` writes a **new file only** and refuses overwriting existing fixtures. Generated cases are deliberately noisy software stress inputs, not calibrated populations; **never train a clinical or sports-science prediction model on them** or interpret fabricated wellness trends as validated effects.

The existing full native workflow already runs Python tests under `native-android/tools/tests` and `:app:testDebugUnitTest`. A dedicated PR must still pass **exact-head CI** before its JVM assertions and input expectations are accepted. The normal build may create ephemeral debug binaries needed to test, but packaging/upload of an installable private APK artifact is **opt-in only**, not required for dataset work.

## Quality controls for expansion

- **Versioned, reproducible, deterministic.** Retain V1 rows; change semantics in a new version rather than silently modifying previous benchmark targets.
- **Traceable source categories.** Distinguish actual device/provider telemetry with permission, manually confirmed activities, planned prescriptions, invented synthetic fixtures, and independent published exercise-science reference material.
- **Rights and privacy.** No scraped proprietary Runna/Freeletics algorithms, copyrighted subscriber plans, unauthorized Garmin endpoints, or private user logs in a test corpus. External benchmark claims require a dated, cited public source and clear limitations.
- **No synthetic-efficacy claim.** A generated athlete timeline can expose software defects but cannot establish injury prevention, dose-response, safe load progressions, physiological adaptation, or valid individualized heart-rate zones.
- **Negative cases matter.** Keep explicit boundary inputs, conflicts, missingness, equipment changes, uncertainty, self-reported interruptions, and multiple source IDs for one physical event.
- **One coordinated engine.** Even with separate movement libraries and physiological signals, maintain shared athlete context and cross-discipline scheduling; never optimize a run and leg day independently into a known conflict.
- **User approval.** An engine may *propose* changes, not automatically rewrite a dated approved plan. No time-triggered Missed/Skipped. Manual completion requires recorded/confirmed work.
- **Measured metrics remain measured.** Do not manufacture HR, cadence, GPS, VO₂max, paces, rep maxima or wellness baselines from unsupported values.
- **Coverage gate.** Next iteration should include adverse-conditions and truly longitudinal datasets with source quality markers and a defined adjudication protocol, but only after the engine's measurable outputs and acceptance criteria are specified.

## Follow-up work before Engine 2.0 acceptance

1. Define a **versioned unified athlete-context schema** with provenance, timestamps, source deduplication and missingness semantics; reconcile with current normalized Garmin/Intervals.icu data model rather than duplicating it.
2. Convert `requiredChecks` into direct assertions **against proposed Engine 2.0 outputs**, including exercise/equipment selection, load change requests, safety pauses, recovery proposals and no-change recommendations. Do not call a string-only check a passed adaptation test.
3. Add evidence-based reference ranges and exercise progression rules only with independent sourcing and review; keep user-specific clinical handling out of an unvalidated deterministic engine.
4. Benchmark safely against publicly documented Runna/Freeletics behaviors and independent sports-science literature, without claims about private algorithms or a fixed optimal plan.
5. Add seeded property-based simulations for edge cases **after** the state-transition and scheduling APIs are stable. Use failure shrinking and reproducible seeds; never drown known invariant failures in random output.
6. Require exact-head JVM, offline process/lifecycle, real SQLite migration, device accessibility, and emulator suites. Preserve signed private-app data; APK packaging only on owner request.

**Acceptance boundary:** This data pack is usable for fixture validation when CI passes; it does not mean the Unified Coaching Engine 2.0 exists or that any simulated coaching adjustment is medically or scientifically validated.
