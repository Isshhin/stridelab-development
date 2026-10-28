# Confirmed QA defect fixes — 28 October 2026

Base: upstream `25be8f3828374d1e539c7e825afa20fbcb652b56`, guided-workout/multi-session integration. Local QA evidence originated at reconstructed test checkpoint `90d6a623052196b22f4fa30593f2dabceb1b4236`. Remote commits are parented to the actual upstream base, not the synthetic local history.

## Changes

* QA-01: `PlanOperations.record` now requires at least one positive performed set in the prescribed measurement before a strength/calisthenics session can become completed. Partial workouts remain completable; unfinished empty or load-only input remains saveable; run/conditioning summary-only logging remains supported. Repeat completion stays a no-op. No targets are copied into actuals by this guard.
* QA-02: extracted existing recording bounds/identity/date validation into `WorkoutLogValidation`, reused when importing known-policy actuals. Unknown exercise IDs, duplicate set identities, invalid set indices, negative/out-of-range values and invalid dates are rejected before repository writes. Existing on-disk records are not migrated or rewritten. Historical duration-only completed snapshots remain readable/restorable; the new positive-set requirement applies to new completion commands, not retroactively to history. Unsupported policies retain opaque read-only snapshots rather than being interpreted as current coaching contracts.
* QA-03: `PlanScheduleValidation` uses the existing baseline/multi-session conflict functions, checks internal nonterminal sessions in imported approved plans, and checks cross-plan conflicts plus combined daily budgets after existing IDs are skipped. Valid easy-run/calisthenics same-day pairs are preserved when both plans use the multi-session policy. Completed/missed/skipped facts are retained without rescheduling. No generator or coaching prescription changes.

Production changes: `PlanOperations.kt`, `PlanBackup.kt`, new `WorkoutLogValidation.kt` and `PlanScheduleValidation.kt`. No database version, app ID/version, signing, account, provider or design changes.

## Verification so far

* Before changes: real source compiled via Java 17/Kotlin 2.2.21; JUnit **113 tests, 7 failures**, matching all original QA failures.
* After changes: JUnit **127 passed, 0 failures**. This includes all original 19 QA tests unchanged, 14 additional restoration/compatibility tests, and the original 94 tests with four obsolete fixture inputs corrected to contain valid synthetic sets (assertions retained).
* Seeded matrix: 300 requests; 290 generated schedules satisfy invariants and snapshot round-trip, 10 explicitly rejected. This is one JUnit test, not 300 independent test counts.
* Python safety/catalog suite: **14 passed**.
* Added Android repository test checks invalid completion and two malformed backups preserve saved plan/outbox across offline reopen. Added Compose review test checks empty completion is rejected while unfinished progress remains saveable. These require Android execution; source presence alone is not verification.
* Dedicated branch-scoped GitHub Actions workflow attempts the existing full native/unit build, legacy upgrade seed, emulator instrumentation and offline/process probes. It uploads test evidence only; it has no APK packaging/release or durable signing job. [Run 40713015201](https://github.com/Isshhin/stridelab-development/actions/runs/40713015201), code head `e9a69c2a8aa5da56303a9e11ac84066efd157993`, failed before any job steps started (job `122903077234`, steps=null). Log retrieval returned BlobNotFound; two reported failure annotations could not be retrieved because the connector does not support that endpoint. The precise runner/account cause is unknown. No Android build, Compose test, emulator test, upgrade or process probe ran in this attempt; do not interpret it as an application compilation/test failure.

Direct JVM runner uses the unchanged pure domain sources, original test classes, and a verbatim `historyWeeklyKm` helper extracted from its UI file. It does not substitute Android storage or Compose. Local Gradle/ADB communication was unavailable during QA; no phone operations were attempted.

## Integration notes

Review against `checkpoint/native-guided-multisession-verified-25be8f3`, not old main. Reconcile overlapping changes in draft PRs #7/#22 rather than merging duplicate fixes. Keep the original seven assertions enabled. No merge, release, real record mutation, Canva edit or installation is authorized by this checkpoint.
