# Engine 1.5 — Batch D verification and Batch E Trackside integration handoff

**Owner branch:** `feature/engine15-versioned-foundation-20261103` · **Draft PR:** [#37](https://github.com/Isshhin/stridelab-development/pull/37) · **Trackside draft:** [#36](https://github.com/Isshhin/stridelab-development/pull/36)

**Do not merge, release or enable Engine 1.5 in Plan Builder from this document.** This file is a verifiable implementation handoff for a private, review-only candidate. It is not evidence that prescriptions improve physiological performance.

## Batch D scope and exact verification requirements

The candidate has three independent gates:

1. **Generation and synthetic safety:** `Engine15ValidationMatrixTest.kt` runs 64 reproducible combinations of 5K/10K/HM/marathon goals, 0/18/35/65 km recent weekly mileage, beginner/experienced and returning/nonreturning profiles. Compare baseline and candidate by stable session IDs and dates; assert weekly bounded mileage, long-run share, no unrequested harder work, fourth-week reductions and taper/return suppression, and unchanged baseline prescriptions for non-running disciplines. Confirm owner-bound JSON roundtrip. These properties are invariants, **not real-world coaching effectiveness estimates**.
2. **Reviewed adjustments and source integrity:** Existing `Engine15FeedbackTest.kt` and new matrix cases cover deterministic previews, no automatic plan changes, recent completed/manual RPE or explicit missed reason, no unperformed strength sets, no uncertain imports, duplicate linked-activity ambiguity, one target per source, conservative reductions/rest, stale plans/log revisions, audit dismissal, accepted vs pending state and owner-restricted backups. `Engine15FeedbackRepositoryTest.kt` runs additional Android instrumented scenarios with a fake private transport.
3. **Real persistence with disposable emulator:** `.github/workflows/engine15-device-qa.yml` runs full Android JVM tests, exercise-library/source integrity, and Android 35 emulator `Engine15FeedbackRepositoryTest` + `Engine1RepositoryTest`. Confirm account switch hides another owner's plans and rejects owner-mismatched backups, a rejected multi-plan restore does not mutate SQLite/outbox, accepted/pending/dismissed changes persist offline after process restart, Engine 1.0 behavior is unchanged. Never use a physical device or real athlete account in CI.

**Previous verified checkpoint** (before this Batch D expansion): [successful Android 35 device run 41580409036](https://github.com/Isshhin/stridelab-development/actions/runs/41580409036) completed 18/18 tests on commit `9c2a9d31`. [Exercise Library pure-JVM job on 41582040797](https://github.com/Isshhin/stridelab-development/actions/runs/41582040797) passed after updating `run_library_jvm_checks.py` to include actual pure-JVM Engine 1.5, validation, wearable and import sources. FIT tests depending on `com.garmin.fit` remain **in** Android Gradle's full unit suite; pure harness explicitly checks that those tests exist and runs every other pure native test. The Python integrity/schema gate remains intact.

**Latest expansion:** Synthetic matrix and owner-switch/atomic-restore emulator tests were added after those verified checkpoints. Determine their exact final commit SHA and exact-head CI status from GitHub Actions; never extrapolate earlier green runs.

## Batch E — staged integration contract (not yet implemented)

Prerequisites: both PRs separately reviewed; PR #36 visual baseline stable; PR #37 all requested validation gates green and its source/policy changes audited; an approved integration sequence and rollback strategy. Do **not** silently merge `feature/engine15-versioned-foundation-20261103` into Trackside #36 or vice versa. After review, create an explicit integration branch from the agreed stable target and resolve both sets of UI/data changes deliberately.

### User-facing flow

1. **Plan Builder:** Engine 1.0 remains default. Engine 1.5 is a clearly labelled *experimental*, explicit opt-in option only when supported. Do not auto-upgrade saved Engine 1.0 plans or relabel a historical plan as Engine 1.5.
2. **Draft review:** display exact engine version, policy ID and explanation in a compact *details* view. Show weekly volume, long-run share, time and quality-session limits, and obvious requested-versus-generated differences. The candidate has no invented readiness scores, VO2max, race times, measured HR zones or diagnostic advice.
3. **Workout feedback:** display only `previewEngine15Adjustments` proposals for approved bounded-running policies. Explain the precise historical source (manual RPE/performed-on or missed reason), unchanged original prescription, proposed time/rest, uncertainty and evidence age. A preview cannot be presented as applied; a stage cannot be presented as accepted.
4. **Legacy suggestion collision gate:** the existing shared `PlanOperations.missed()` still produces an Engine 1.0-style pending suggestion when a session is marked missed, even on a supported Engine 1.5 snapshot. Before enabling Engine 1.5 in Trackside, ensure this legacy behavior cannot preempt or duplicate the explicitly reviewed Engine 1.5 feedback flow, while preserving Engine 1.0 behavior and history. Add policy-specific regression tests; do not globally remove the legacy suggestion path.
5. **Explicit decisions:** user chooses `Review` then `Accept` or `Dismiss`. The repository checks plan revision/current evidence again; a stale result forces refresh, not a silent retry. Dismissed proposals retain audit history; accepted rest changes a future session to skipped only with approval.
6. **Original target compatibility:** an accepted shorter session currently changes `effectiveMinutes` but deliberately preserves original `minutes`, `distanceKm` and historical workout steps. UI **must not show the old distance as the newly required distance or imply a pace increase**. Label original distance as original context, and the approved change as time-only; avoid setting a contradictory pace/distance goal. Treat this as an explicit integration design gate, not a hidden assumption.
7. **Offline and failure states:** show pending/accepted decisions after restart; account switches must never leak previews, proposals, sessions or audit records. No network/permission is required to review a stored, account-owned plan. No autopush of a proposed change to an attached watch or training provider.
8. **Accessibility:** test compact AMOLED Trackside layout on 360/412dp width, 1.3× font scaling, descriptive screen-reader labels, long evidence strings, notification-free empty state and unknown/read-only policy states.

### Release gates and scope limits

- Show side-by-side snapshots and backed-up data before/after accepted and declined adjustments; test stale revisions, duplicate links and rollback.
- Keep Engine 1.0 fallback plus all previously approved session dates, logs, equipment assignments and linked activity identities unchanged.
- Keep Engine 2.0 and the independent Athlete Simulation Lab outside this feature branch. Synthetic scenarios check safety; a true efficacy/longitudinal calibration claim requires separate real-world validation.
- Never infer workout failure from no data, map RHR/HRV to illness, or invent user effort. Manual RPE and missed reasons are user-entered evidence only.
- Do not release or merge until final exact-head GitHub checks, independent PR review and explicit authorization. After Batch E integration, rerun device tests and do a user-controlled private APK acceptance pass.

## Restart checklist for another conversation

1. Inspect [PR #37](https://github.com/Isshhin/stridelab-development/pull/37) **current head SHA** and newest workflows before saying what passed.
2. If pure JVM fails, read the specific failing step and fix the actual source boundary without skipping library/schema tests or removing Gradle FIT coverage.
3. If 64-profile matrix fails, reduce the cause to a reproducible fixture; fix source/test expectation carefully without loosening conservative invariants.
4. If emulator tests fail, inspect the instrumentation results, isolate source of account/corruption/revision behavior and rerun on a new exact head.
5. Update PR and the TickTick Batch D task with the exact verified and unverified gates. Leave PR #37 draft, PR #36 untouched; mark only truly completed checkpoints green.
