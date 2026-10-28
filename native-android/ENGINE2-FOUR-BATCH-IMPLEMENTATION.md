# StrideLab — Four Stacked Engine 2.0 Implementation Batches

**Status:** Four implementation candidates with tests, not an accepted or integrated Engine 2.0 release.  
**Starting verified baseline:** `25be8f3828374d1e539c7e825afa20fbcb652b56`; predecessor draft foundation PR #13.  
**Work mode:** Connected GitHub contents API, no Actions dispatch or build; `prep/engine2-*` branches are outside the checkpoint workflow's push triggers.  
**Distribution:** Private personal Android alpha, no public release or owner-requested APK delivery.

## Branch integration order

1. `prep/engine2-context-proposals-20261027` — prerequisite draft PR #13, eight read-only/pure Kotlin domain components and seven JUnit suites.
2. `prep/engine2-phases1-4-batch-20261027` — Batch 1, based on the PR #13 branch.
3. `prep/engine2-phases5-8-batch-20261027` — Batch 2, based on Batch 1.
4. `prep/engine2-phases9-12-batch-20261027` — Batch 3, based on Batch 2.
5. `prep/engine2-phase13-acceptance-batch-20261027` — Batch 4, based on Batch 3.

**Do not merge a child branch independently of its parent**, flatten the history into `main`, or accept a child PR solely because it looks small. These are source candidates; an exact-head build is still needed. Existing Engine 1.0 persisted structures, screens, database, release workflow, signing and saved logs were not modified in these branches.

## Batch 1 — Phases 1–4

| Phase | Implemented increment | Important limit |
|---|---|---|
| 1: Athlete context | Strict `Engine2ContextAssembler` accepts source-backed rows and rejects malformed/cross-account entries individually | Not wired to actual provider or persisted athlete tables |
| 2: Approval lifecycle | Immutable `Engine2ApprovalLedger` stages owner-reviewed proposals, enforces one pending proposal per session, maintains decision receipts and introduces optimistic revision commit port | No production SQLite implementation; staged receipt not durably saved |
| 3: Workload/recovery | `Engine2WorkloadReview` compares windows of known confirmed running distance and warns about incomplete evidence | Not an injury-risk model, HRV-derived recovery prescription or complete activity history |
| 4: Unified plans | `Engine2UnifiedPlanGenerator` creates deterministic **draft schedule skeletons** across running/strength/calisthenics/conditioning, with availability, max duration and long/quality preferences | One session per day, generic durations, no pace/load/rep prescription or automated mileage ramp |

`Engine2Batch1Test` checks invalid ingestion, approval receipts, duplicate pending changes, incomplete distance, deterministic calendars, and infeasible scheduling.

## Batch 2 — Phases 5–8

| Phase | Implemented increment | Important limit |
|---|---|---|
| 5: Adaptation | `Engine2ConservativeAdaptation` prepares an optional shorter session **only when explicitly requested**, grounded in prior confirmed source-backed workout evidence | No automatic load progression, coaching claims or unapproved changes |
| 6: Exercise knowledge | `Engine2ExerciseSelection` selects a curated subset of **existing catalog IDs** using equipment capability and explicitly confirmed skill prerequisites | No proprietary exercise content, automatic calisthenics promotion or unreviewed imagery |
| 7: Validation | `Engine2Batch2Test` exercises actual adaptation outcomes, false-ready cases, prerequisite/equipment refusals, provider units, and missing-evidence boundaries | Broader V1/V2 data PR #11/#12 integration and full behavioral scenario replay still pending |
| 8: Provider foundations | `Engine2AuthorizedProviderPort`, `Engine2ProviderNormalizer` define paging and convert meters → km without inventing heart rate/duration | No Garmin OAuth, API client, privileged endpoint or real account test |

## Batch 3 — Phases 9–12

| Phase | Implemented increment | Important limit |
|---|---|---|
| 9: Recorded-run matching | `Engine2ActivityMatcher` emits manual-review candidates for confirmed UTC-date-matching runs and excludes exact provider-ID collisions | **Never** performs automatic link; timezone must be verified by user; storage uniqueness is unresolved |
| 10: Android UI | `Engine2ProposalReviewCard` Compose preview shows original/proposed duration, weekly impact and evidence, with actions **disabled by default** | No existing screen navigation wired; needs accessibility/emulator tests and durable decision adapter |
| 11: Intelligence | `Engine2FitnessInsights` explains observed distance change and uncertainty, never fabricates VO₂max, readiness, elite comparison, or predictions | No advanced activity split/GPS/map integration |
| 12: Offline storage | `Engine2VolatileLedgerStore` is a strict in-memory CAS reference test double that recomputes proposed changes before commit | **Not persistent**, not SQLite, no migrations, outbox or process-restart guarantee |

`Engine2Batch3Test` covers ambiguity, source collisions, missing-data language, CAS stale write and forged candidate rejection.

## Batch 4 — Phase 13

- `Engine2AcceptanceGate` formalizes independent evidence required for domain review, persistence integration, authorized provider sync and private phone-update delivery.
- `Engine2Batch4AcceptanceTest` exercises a fully synthetic provider → context → draft plan → explicit simulated owner approval → lighter proposal → read-only comparison → staged ledger → volatile CAS cycle, plus fail-closed integration gates.
- **No actual app integration**, schema upgrade, account authentication, Android build, emulator acceptance, physical phone test, APK packaging or public release is claimed.
- Every checkbox defaults to false unless verified independent evidence exists. The acceptance-gate code does not itself authenticate the evidence.

## Known boundary conditions to resolve before production adoption

1. **Source/identity**: preserve per-account/per-provider IDs and timestamps. Cross-provider duplicates cannot be merged by matching dates or raw IDs alone; persist canonical associations atomically after the separate P0-3 migration review.
2. **Approval persistence**: the volatile store is *test infrastructure only*. Implement atomic transaction + outbox + versioned receipts, with stale-write, crash, replay, account-switch, offline-reopen and synthetic v1→v2 migration checks.
3. **Fixture coverage**: integrate dataset PRs #11/#12 only after exact-head tests pass. Expand the scenario test harness to assert emitted proposals and refusals, not simply input JSON validity.
4. **Safety**: this first policy is user-requested duration reduction. Plan generation remains a generic schedule, not an exercise-science approved training prescription. Introduce evidence-based, discipline-aware prescriptions and independent review before real use.
5. **User interface**: the Compose review card is intentionally not activated; real buttons must call the durable approval adapter, not an in-memory `copy`.
6. **Environment**: Android tests, visual screenshots, signing compatibility and Garmin authorization require the appropriate tools and permissions and cannot be inferred from committed source.

## Verification sequence after available CI minutes

From a checked-out, reviewed child branch containing all ancestors:

```bash
cd native-android
gradle --no-daemon :app:testDebugUnitTest
# Then run the project's existing instrumentation and legacy-upgrade jobs
# only in an isolated configured environment with adequate Actions budget.
```

Preferred consolidation: a single reviewed test workflow run against the **exact final head**, followed by targeted fixes on the relevant parent branch (propagate downstream safely), then emulator/SQLite validation in separate gates. Check the GitHub Actions billing budget before any dispatch or merge.

**No downloadable APK should be created unless the owner explicitly requests it. No public release.**
