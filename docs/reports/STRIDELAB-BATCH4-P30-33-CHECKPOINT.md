# Batch 4 implementation checkpoint — Steps 30–33

**Source baseline:** `25be8f3828374d1e539c7e825afa20fbcb652b56`. **Status: draft/source-only, no exact-head Gradle, Android instrumentation or emulator acceptance.** No active SQLite schema version was changed.

## Step 30 — ported from existing PR #7
- Adopted `PlanOperations.kt`, `WorkoutScreen.kt`, `TrainingScreens.kt`, `Engine1Test.kt`, `Engine1RepositoryTest.kt`, and `Engine1UiTest.kt` from PR #7, retaining its existing completion and running-card fixes.
- Workflow packaging was **not** ported in those commits; Step 33 below commits it separately.
- This is a code port, not a tested closure of all workout semantics.

## Step 31 — isolated-copy link integrity candidate
- New `ActivityLinkMigrationCandidate.java` creates `activity_links(account,activity_id,plan_id,session_id)` with `PRIMARY KEY(account,activity_id)` and `UNIQUE(account,plan_id,session_id)` only when explicitly invoked on an **isolated test copy** of an SQLite v1 source DB.
- Source scan is fail-closed: account/id/schema validation; duplicate source links or invalid plan bodies throw; a single SQLite transaction rolls back changes without rewriting `records` or `outbox`.
- `ActivityLinkMigrationCandidateTest.kt` tests valid cross-account backfill and rollback on duplicates/unknown schemas.
- **Not done:** Production `TrainingStore.onUpgrade` remains unchanged at v1; no phone data migrated. There is NO production transaction unifying plan JSON changes, link index and outbox. Do not declare the uniqueness P0 solved. The migration/SQL test must pass Android CI and backup/rollback acceptance before any production wiring.

## Step 32 — durable local queue and recovery
- Verified baseline already has `TrainingStore.enqueue`, `ready`, `acknowledge`, `recordRetry`, `resolveConflict`.
- New `OutboxOfflineRecoveryTest.kt` covers offline queue and source record surviving store reconstruction, chained local edits, retry after simulated acknowledgement, wrong-account acknowledgements, and a failed write leaving no extra row.
- **Not done:** No authenticated Supabase plan-write endpoint/idempotency contract is known, so no remote upload/replay consumer was enabled. No arbitrary rest-timer-state durability claim.

## Step 33 — account isolation and packaging
- The outbox regression suite checks cross-account read, pending and acknowledgement isolation at the SQLite boundary. Delayed in-flight authenticated gateway callbacks and broader process death remain to be tested.
- Separately committed the original PR #7 workflow change: standard tests do **not** package/upload private debug APKs without manual `workflow_dispatch` and `package_debug_apk=true`. Preserved the existing durable signing gate.
- No Actions dispatch, public/private APK generation, upload or phone install occurred.

## Exact-head acceptance needed before merging/activation

1. Compile Kotlin + Java; run JVM, Python and Android instrumentation on the exact branch commit.
2. Test copy-based v1->v2 backfill from realistic/unknown/duplicated synthetic plans, plus two-writer contention and transaction rollback.
3. Implement audited atomic link-plan-outbox mutations and actually migrate to schema v2, only after approving and verifying the design.
4. Test delayed callback across account switch; crash at different local-write/ack points.
5. Verify server plan-write idempotency and RLS before enabling automatic outbox replay; no fabricated server version tokens.
6. Keep installed APK data and original signing identity untouched. Do not merge without explicit owner approval.

**Design references:** Phase 3.5 PR #21 and P0 link design PR #10. The code in this branch is intentionally additive and does not use the experimental Engine2 ledger as an approved-plan authority.
