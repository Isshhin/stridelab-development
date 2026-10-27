# StrideLab — Unified Engine 2.0 Dataset Expansion V2

**Status:** Draft, synthetic-only, not accepted as test-passing. The coaching engine is not implemented here.  
**Relationship:** Stacked on [Engine Dataset V1, draft PR #11](https://github.com/Isshhin/stridelab-development/pull/11), not merged into the frozen Engine 1.0 source.  
**Privacy:** Every athlete, device, activity, recovery metric, time series, user event, and identifier is fictional. No wearable connections, real user records, competitor algorithms or private training plans were used.

## What V2 adds

| Artifact | Purpose | Capacity |
|---|---|---|
| `app/src/test/resources/engine/engine2_longitudinal_recipes_v2.json` | Human-reviewable long-horizon cases | **64** distinct recipes, each **12–16 weeks** |
| `tools/build_engine2_scenarios.py` | Generate data from recipes with deterministic seed and optional variants | 1–20 variants per recipe; **maximum 1,280 fictional athlete histories** |
| `tools/tests/test_engine2_scenario_matrix.py` | Validate recipe contracts, generated observations, missingness, equipment IDs, duplicate sources and seed stability | Runs with Python standard library in normal CI |
| `tools/report_engine2_coverage.py` | Inspect the generated test corpus without dumping raw data | Compact deterministic counts by discipline, source, missing measurement, missing week, duplicate group, flagged sensor and rejected unit input |
| `app/src/test/java/io/stridelab/privatealpha/Engine2RecipeContractTest.kt` | Assert Kotlin/JVM compatibility with recipe and evidence metadata | Runs as part of `:app:testDebugUnitTest` |
| `app/src/test/resources/engine/engine2_evidence_registry_v1.json` | Source-scoped research metadata, not coaching parameters | WHO physical-activity guidelines, ACSM 2026 resistance-training position, IOC training-load consensus |

**Eight dimensions, eight deliberately different cases each:** running, resistance training, calisthenics, coordinated hybrid training, self-reported recovery, source provenance/synchronization, plan-state lifecycle/consent, and unusual limitations. The matrix includes **23 named candidate invariants**: no invented measurements; missing is unknown; no auto-Missed or catch-up sessions; no unsupported skill promotion or one-rep-max estimates; one unified training context; source/identity integrity; no unapproved changes; account isolation; and conservative interpretation of wellness.

This is **scenario breadth**, not 64 scientifically proven coaching rules.

## Run locally (from `native-android/`)

```bash
# Small deterministic output to a NEW file, without touching app data
python3 tools/build_engine2_scenarios.py --seed 20261027 --limit 8 --variants 1 --output /tmp/stride-fixtures-small.json

# All 64 different long-term scenarios, each with one synthetic history
python3 tools/build_engine2_scenarios.py --seed 20261027 --limit 64 --variants 1 --output /tmp/stride-fixtures-full.json

# Stress run: 640 entirely fictional histories. Output can be large.
python3 tools/build_engine2_scenarios.py --seed 20261027 --limit 64 --variants 10 --output /tmp/stride-fixtures-stress.json

# Lightweight coverage inspection without emitting full athlete histories
python3 tools/report_engine2_coverage.py --seed 20261027 --limit 64 --variants 1

# Automated input and provenance tests, including all 64 scenarios
python3 -m unittest discover -s tools/tests -p 'test_engine2_scenario_matrix.py' -v
gradle --no-daemon :app:testDebugUnitTest
```

The CLI refuses overwriting a previous file (exclusive-create), negative/zero limits, limits above 64 and variants outside 1–20. Use different new filenames for additional runs. Seed/variant changes alter fictional values reproducibly; they do **not** establish actual athlete response probabilities. Never commit generated volume dumps into source history or interpret them as a representative population.

## Schema and limitations

Each V2 **recipe** declares an ID, target fictional sport mix, wearables/equipment context, 12- or 16-week horizon, optional missing weeks, an optional user/sync/equipment event, expected invariant IDs, and source conditions. A recipe can deliberately encode an undesirable situation; it is not an endorsed workout plan.

The **materializer** produces dated, source-labeled synthetic workout records, optional strength/cal set measurements, sparse or missing wellness values, and explicit event metadata. It does not generate medical diagnoses, race-performance predictions, coaching actions or approval decisions. Planned-but-unconfirmed sessions carry planned targets separately from actual measurements. An entirely missing week is absence of data, **not an automatically skipped workout**. Some two-provider observations intentionally describe the same fictional physical event; downstream code must preserve provenance and deduplicate only with an explicit reviewed identity strategy.

The generated order and placement of workouts are synthetic stress inputs, not recommendations for scheduling safe training. The generator can produce close-spaced hard sessions **to challenge** the future engine's conflict handling. The numeric fictional HR, HRV, RPE, distances and loads are *test syntax*, not normative thresholds, measured results, or expected adaptation rates.

Some adversarial events now materialize **deterministically** as source examples: two adjacent hard-run records, a flagged implausible HR sample, an explicitly chosen rest/skipped row without fabricated actuals, a planned-but-unconfirmed row, an intentionally quarantined invalid-unit input, and a cross-account duplicate identity case. The Python tests require those examples to exist. Other recipe events remain *context annotations*. A corresponding **adaptive behavior** check can only become executable after a versioned Engine 2.0 response schema defines `proposedChanges`, approvals/rejections, unchanged historical records and evidence citations. Do not mark a candidate invariant as passed merely because its identifier or test input exists in JSON.

## Source-grounded research register

The structured research register separates references from rules:

- **WHO, 2020:** public-health activity guidelines. Useful context for broad physical activity and resistance participation, **not** an individual's optimal running mileage or safety ceiling. [WHO primary source](https://www.who.int/publications/i/item/9789240014886).
- **ACSM, 2026:** updated resistance-training position statement emphasizes consistency, goal-dependent training and effective non-gym options. Do not automatically infer unknown 1RM, apply elite percentages to everyone, or assume a technique is universally required. [ACSM official update](https://acsm.org/resistance-training-guidelines-update-2026/).
- **IOC, 2016:** load-and-injury/illness consensus emphasizes monitoring external load, internal response and individual context; no single metric reliably predicts maladaptation. Its population-specific historical heuristics are **not** a justified universal safe load increment. [Load and injury](https://doi.org/10.1136/BJSPORTS-2016-096581) · [Load and illness](https://doi.org/10.1136/BJSPORTS-2016-096572).

No research reference constitutes clinical clearance, a validated training-load model, or authorization to override a user-approved plan. More recent, independently reviewed evidence and exercise-specific sources will be needed before adding numeric coaching rules.

## Acceptance criteria before Engine 2.0 can use these cases

1. V1 and V2 dataset JSON and Kotlin/Python quality gates pass **exact-head GitHub CI**; no skipped tests. Any failing accepted Engine 1.0 case is a diagnostic to investigate, not something to silently weaken.
2. Introduce a versioned, deterministic `AthleteContext` and `CoachProposal` contract; every activity and wellness field must retain source, measurement unit and date, and missingness.
3. Match each invariant to a **machine-executable test over actual Engine 2.0 output**, not only to a recipe label. Specifically verify no unauthorized schedule changes and no invented actual metrics.
4. Provide explicit feasible/infeasible scheduling tests with a shared workload view. Running distance, resistance load/sets, and calisthenics skill progressions must not be silently added as interchangeable units.
5. Add approved domain-specific, source-grounded exercise progressions and load adjustment bounds, with review and safe uncertainty handling; test no-watch and no-equipment athletes.
6. Verify account isolation, provider duplicates, offline reopen, failed sync, stale or edited data, and non-destructive migrations. A failure must not delete a historical plan or workout log.
7. Compare external public Runna/Freeletics behavior separately and conservatively. Proprietary methods and paid plans must not be scraped or represented as official ground truth.
8. Review accidental data leakage before any future telemetry dataset. No personally identifying training records, account credentials or public APK archives in PRs.

**No app/engine implementation, model training, migration, signing change, APK packaging, or automatic merge is included in this V2 expansion.**
