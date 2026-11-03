# Batch 8D — Multiweek event reconciliation and synthetic policy lifecycle

**Verified:** November 3, 2026, Asia/Manila. **Repository:** `Isshhin/stridelab-development`. **Branch:** `research/runna-garmin-coaching-20261102`. **Research PR:** [#34](https://github.com/Isshhin/stridelab-development/pull/34), draft and unmerged. **Actual GitHub Actions evidence:** [successful run 41585527016](https://github.com/Isshhin/stridelab-development/actions/runs/41585527016) on commit `a257df00b90ffdaee2e5a20ade363a693bb06226`. **Status:** new synthetic research code, *not* a migrated local Athlete Simulation Lab or Android coaching engine.

## 1. Work executed and verified

Added a strict event-level, source-aware reconciliation module and a multiweek synthetic simulator on the isolated Python research branch.

- [multiweek_reconciliation.py](../../research/validation/multiweek_reconciliation.py) adds append-only source ingest, exact source/external ID identity, atomic batch ingestion, identical replay handling, confirmed cross-source session deduplication, conflicting-record quarantine, unlinked source records, separate planned and actual running/strength doses and missing-versus-skipped distinction.
- [multiweek_runner.py](../../research/validation/multiweek_runner.py) drives **four entirely synthetic training profiles, eight illustrative weeks each**, with 5 planned runs + 2 planned strength sessions/week, explicit fictional athlete approval of new week plans, an approved multi-session calendar edit followed by rollback, a rejected edit, process restart, interrupted batch ingestion/retry and B0-versus-H1 research decisions. All data are freshly constructed in disposable GitHub-hosted SQLite databases.
- [test_multiweek_reconciliation.py](../../research/validation/test_multiweek_reconciliation.py) adds 15 strict ingest/reconciliation tests. [test_multiweek_runner.py](../../research/validation/test_multiweek_runner.py) adds nine multiweek invariants and repeatability tests, including separate easy and threshold work-step distances.
- [Research workflow](../../.github/workflows/research-engine2-contract-validation.yml) additionally invokes the multiweek runner, publishes a CI summary and uploads per-week `research-multiweek-results.json` alongside the earlier four research reports.

**Verified exact GitHub logs** (workflow [run 41585527016](https://github.com/Isshhin/stridelab-development/actions/runs/41585527016)):

| Lane | Observed result | Boundary |
| --- | --- | --- |
| New 8D multiweek synthetic profile evaluation | **4 PROFILE PASS, 0 FAIL, 32 simulated weeks** | Pass means that *defined structural invariants* held in all weeks of each synthetic profile, not an improvement to running |
| Research Python unit tests | **66 tests passed**, zero errors | Includes 24 new tests and earlier SQLite/in-memory/harness cases |
| New 8B B0/H1 frozen counterfactuals | **55 structural PASS, 0 FAIL, 3 predictive BLOCKED** | Not 58 real-engine passes; forecasts remain unsupported |
| 8C separately seeded adversarial variants | **288 PASS, 0 FAIL** | Distinct synthetic stress test, not physiological validation |
| Earlier 8A small toy-model contracts | **58 PASS, 0 FAIL** | Separate model and evaluator; results are **not additive** |
| B0/H1 decision differences within 8D | **4 synthetic athlete-weeks** | 3 cross-discipline and 1 sensor-gap; no adjudicated coaching outcome |

**No existing Android Engine 1.0, 1.5 or future production Engine 2.0 was executed.** The user's older independent Python Athlete Simulation Lab was not accessible or modified.

## 2. Synthetic profiles, weeks and observed source counts

| Synthetic profile | Weeks | Raw source records | B0/H1 differing weeks | Outcome |
| --- | ---: | ---: | ---: | --- |
| STEADY | 8 | 58 | 0 | PASS |
| INTERRUPTED | 8 | 56 | 0 | PASS |
| CROSS_DISCIPLINE | 8 | 56 | 3 | PASS |
| SENSOR_GAPS | 8 | 57 | 1 | PASS |
| **Total** | **32** | **227** | **4** | 4 profiles PASS |

The 227 figure is **raw source records**, not 227 confirmed sessions; duplicate source observations and unplanned/unlinked events are deliberately present. Each week has seven planned sessions (five running, two strength), for **224 planned session slots** over 32 synthetic weeks. Do not conflate source-event count with true unique training sessions.

### Distinct source outcomes verified

- **STEADY:** sessions remain confirmed and complete. Two quality-run observations were provided by both manual and wearable source identity; their identical **explicitly verified** session records are counted only once.
- **INTERRUPTED:** partial threshold session, explicitly skipped quality workout, absent easy session (remains **unknown**, not skipped), later unplanned hard run (kept separate), return-from-break inputs. No automatic workout debt is assigned.
- **CROSS_DISCIPLINE:** a leg-strength overlap flag is supplied for some quality/long-run contexts, one incomplete strength session is recorded, and one pain report defers instead of silently modifying training. H1's three differing proposed actions have **not** been shown to be physiologically preferable to B0.
- **SENSOR_GAPS:** an untrusted GPS/HR context, conflicting double-sourced quality session (both source records preserved but **neither credited** as a resolved dose), a completely unlinked extra upload (not assumed to be a planned session), and one missing planned easy workout. H1 differs from B0 in one week.

### Week-to-week plan and audit behavior

- Each next-week plan is committed only through an **explicit simulated athlete approval**; code does **not** treat H1 output as consent.
- In week 2, an explicit multi-session calendar edit is approved and rolled back. The old plan is exactly restored, but the audit history remains intact.
- In week 3, a proposed calendar adjustment is explicitly rejected, with no plan-version or plan-content mutation.
- In week 4, the disposable SQLite store is closed and reopened. Previous plan version, activity records and audit remain intact.
- In week 7, the runner deliberately raises an exception inside a multi-event SQLite transaction. It verifies that no partially ingested batch is committed, then retries the batch normally.
- Every ingestion batch is replayed with the **same** source IDs and payloads. Each replay is counted as replay, not fresh training; conflicting reuses of an existing source ID fail.
- Reconciliation reports **known-only** running km, threshold-work seconds, easy seconds and strength sets independently. When source observations are absent, conflicted or unlinked, `total_actual_complete=false`. Known-only km is **not** labeled a fully confirmed weekly training total.

**Important trust boundary:** `verified_session_id` is a *synthetic fixture assumption*, not a verified permission/authentication system or proven cross-device identity resolver. For a future production importer, independent authorization and trustworthy matching evidence must establish that identity; never trust arbitrary external metadata as canonical. This research test does not establish that capability.

## 3. What the test actually shows

The new research B0/H1 prototypes preserve some important **bookkeeping and audit invariants** across long sequences of varied synthetic input: a known missed workout is different from an unobserved upload, a partially completed interval isn't credited in full, strength sets aren't silently added to running km, device duplicates aren't automatically doubled, and explicitly approved plan revisions can be restored without destroying completed historical activity records.

This is **not evidence that the proposed H1 makes good multiweek training prescriptions**. The week-to-week training plans were supplied by an illustrative fixture (including illustrative distances and quality-step ratios), **not autonomously generated and progressed by H1**. H1 still produces *single-context proposals* with qualitative flags rather than calibrated long-horizon planning. No training physiology, performance improvements or real-world injury outcomes are validated. The source evidence, original synthetic scenarios and assessment criteria originate from the same research effort rather than an independently blinded external team.

The earlier 8C review found 14 one-step proposed-action differences; the four 8D differing *athlete-weeks* are different measurements and should not be added as a new effectiveness score.

## 4. Known limitations and risk register

1. **No autonomous multiweek progression:** eight-week plans are fixed synthetic inputs with explicit fictional user approval. B0/H1 decision outputs are advisory; no experimentally tested policy selects a complete dose and adapts the subsequent week based on actual performance.
2. **Cross-device identity verification is assumed:** explicitly verified canonical IDs are required for dedupe. This is not a trust boundary suitable for real uploads; false links must remain blocked until source/identity verification exists.
3. **Source conflicts do not auto-resolve:** safely preserving and flagging them is the tested behavior; conflicting input requires trusted reconciliation.
4. **No complete physical recovery model:** H1 overlap flags do not use actual hours elapsed, effort or movement-specific strain. A move proposal is not a validated medical or training recommendation.
5. **SQLite is an isolated research database:** tests demonstrate transaction rollback and replay on GitHub Linux, not Android Room migrations, concurrent app lifecycle, offline sync resolution, encryption or physical power-loss behavior.
6. **No training efficacy claims:** no real matched-condition easy-HR speed, threshold, strength, calisthenics skill or adherence outcome was measured.
7. **Three E005 forecasts remain blocked:** E005:AL02, AL03, AL07. No calibrated individualized difficulty predictor.
8. **Historical local Athlete Simulation Lab inaccessible:** its own policies and manifests were not tested or modified; no athlete FIT and no protected dataset uploaded.
9. **Runna/Garmin access still missing:** Runna E002 0/16 authenticated current app cases; no controlled Garmin Run Coach before/after paired outcomes.

## 5. Next research decisions — Batch 8E

**First priority should be connecting an actual source-of-truth coaching policy**, not expanding synthetic pass counters indefinitely.

1. **Read-only interface audit**: identify existing local Athlete Simulation Lab code and real versioned coaching baselines if they are shared/synced into an authorized private repository. If unavailable, clearly label the separate B0/H1 candidate track as prototype-only.
2. **Workout-to-week progression contract**: define how an accepted/declined recommendation can alter *specific next-week workout steps*, quality exposure, long-run continuity and multi-discipline recovery while preserving athlete consent and explainability. Require an independently specified evaluation before writing any progression policy.
3. **Trusted source-linking gate**: introduce explicit provenance/authorization for cross-device duplicates and a conflict-review workflow; never automatically match by distance/time alone.
4. **Prospective real-world evaluation design**: prioritize easy running speed at genuinely comparable HR/route/heat/gradient, threshold continuity and workload adherence. Data cannot be fabricated and any performance effect needs longitudinal observation.
5. **Android integration remains out of scope** until production engine interface, verified baseline, migration/rollback, offline persistence and source authorization requirements are met.

The existing daily 09:00 Manila Runna and Garmin research automation remains active; do not duplicate. Keep PR #34 draft and unmerged, Engine 1.0/1.5 and Trackside PR #36 unchanged, and original protected datasets private.
