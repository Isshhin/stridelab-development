# StrideLab — Engine 2.0 Foundation, Pure-Kotlin Increment

**Status:** Draft source and tests only. No Android build, emulator, offline database migration, clinical validation, APK packaging, CI minutes or user installation performed.  
**Baseline:** Native checkpoint `25be8f3828374d1e539c7e825afa20fbcb652b56`.  
**Branch:** `prep/engine2-context-proposals-20261027` (not selected by the checkpoint's GitHub Actions push-branch filter).  
**Scope:** Additive, internal Kotlin classes; no change to verified Engine 1.0 planners, XML, Compose screens, production tables, cloud code or existing records.

## Implemented now

1. `Engine2AthleteContext.kt` — account-isolated, immutable workout/wellness/equipment/goal inputs with source identity, observation/receipt timestamps, explicit measurement units and missingness. Planned and draft data are never quietly counted as confirmed running mileage. No assumption that an absent device measurement means zero.
2. `Engine2ProposalWorkflow.kt` — pure proposal preparation and approval/reject/postpone transitions. Only a reduction in the duration of an untouched **planned** session is proposed in this increment. A coach-authored proposal needs cited prior observations, a narrative reason and original plan revision; preparing it never alters the plan. Approval requires exact matching owner/plan/session/revision and records an immutable receipt. Postponing or rejecting leaves the plan untouched. Replays require matching durable receipts, not merely matching minutes.
3. `Engine2ScheduleAuditor.kt` — non-mutating schedule warnings from **explicit** difficulty/lower-body/long-run labels and user-given daily time budget. Flags unknown effort, hard sessions on consecutive days, hard work alongside lower-body training, and lower-body work the day before a long run. These are explainable conservative review prompts **not** validated injury-risk predictions.
4. `Engine2ProvenanceAudit.kt` — detects the same provider-local record ID claimed by multiple workout records. Different provider IDs are never automatically assumed to be one physical event; exact record uniqueness is **not** evidence of cross-provider deduplication.
5. `Engine2PlanBridge.kt` — strictly read-only projection of recognized Engine 1.0 plan snapshots into Engine 2.0 review models. Unsupported engine versions, workout states or disciplines fail closed. Existing approved shorter-session proposals are represented by their effective minutes; historical logs are not rewritten.
6. `Engine2Timeline.kt` — windowed, confirmed-only per-discipline session counts and known durations; separate running distance with missing-distance counts, plus exact identity collision warning. No adding up running km and lifting kg; partial known totals must not be shown as complete training history.
7. `Engine2WellnessAudit.kt` — time-window presence and latest source observation/arrival dates for resting HR, HRV and sleep, without a single-metric readiness classification or medical diagnosis.
8. `Engine2ReviewPreview.kt` — immutable proposal review information (before/after minutes, estimated change to scheduled week, explanation, evidence IDs, and eligibility). A stale/completed workout cannot appear as an actionable change.

**Tests:** Focused synthetic JVM/JUnit regression suites for ownership, missing/future metrics, evidence freshness, rejected and postponed decisions, idempotent approval receipts, stale revisions, no historical rewrites, schedule spacing, unknown effort, provider identity, week-window separation, wellness staleness and read-only proposal previews. Tests are source committed but **not yet executed** on exact-head Android Gradle CI or emulator.

## What is intentionally NOT implemented

- **No adaptation algorithm** chooses volumes, effort, progression levels or medical thresholds. The proposal API accepts a reduced duration; this is not proof that the recommended duration is appropriate.
- **No durable Engine 2.0 storage adapter** exists yet. The current v1 plan JSON schema and SQLite transaction/outbox do not contain Engine 2.0 approval receipt persistence, versioned source observation storage, or multi-provider cross-plan link uniqueness. Returning immutable proposal/plan candidates from `decide` is *not a committed transaction*.
- **No connection to Garmin, Intervals.icu or user accounts** beyond an internal read-only bridge for existing plan snapshots. Data must retain original provider IDs and source arrival times.
- **No Engine 2.0 UI controls** are wired to these classes. Do **not** surface an operational approval button until a transactional adapter durably persists the proposal decision, receipt and plan revision in one atomic operation.
- **No automatic missed/skipped status**, skill promotion, performance prediction, injury risk statement, readiness score, catch-up mileage, or overwrite of existing Engine 1.0 logs.

## Future integration gate after Actions reset

1. Run JVM unit tests against the exact branch SHA and review failures without weakening the contracts.
2. Add a versioned storage schema/transaction interface for plan + proposal + durable receipts with optimistic revision compare. Reconcile with the account-scoped source-link design in draft PR #10. Verify non-destructive upgrades from synthetic existing v1 saved plans, outbox and exercise logs.
3. Translate real imported activity and wellness records into context using documented provider fields. Retain original units/measurement type, and **never** treat one provider ID as a canonical physical activity across sources.
4. Establish a versioned, explicitly evidenced coaching-policy layer with testable behavior. Execute the V1/V2 synthetic scenarios in draft PRs #11 and #12 against *actual decision outputs*, instead of just checking fixture structure.
5. Only then wire compact Compose review and confirmation UX. Require user approval, unchanged originals on rejection, truthful state messages, and offline reopen/regression evidence.
6. Run full private Android Gradle/emulator tests as a **consolidated CI run**, after confirming GitHub billing budgets and available Actions minutes. Do not package an APK unless explicitly requested. Preserve signing compatibility and on-device data.

### Local unit-test command after integration into a full checkout

From `native-android/`:

```bash
gradle --no-daemon :app:testDebugUnitTest --tests 'io.stridelab.privatealpha.Engine2*Test'
```

If an Android Gradle JVM test filter does not support wildcards in your setup, run `:app:testDebugUnitTest` normally instead.

**Review status:** Source and tests are committed on a separate branch for later exact-head verification; GitHub creation of files does not establish successful compilation or emulator acceptance.
