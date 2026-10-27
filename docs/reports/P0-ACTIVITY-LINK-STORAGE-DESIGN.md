# P0-3 — Transactional Recorded-Activity Link Integrity (Engineering Design Draft)

**Status:** Engineering proposal only; not approved or implemented.  
**Source audited:** `checkpoint/native-guided-multisession-verified-25be8f3` @ `25be8f3828374d1e539c7e825afa20fbcb652b56`.  
**Related candidate hardening:** draft PR #9 blocks silent link replacement, but it does **not** resolve storage-level uniqueness.  
**Owner policy:** preserve existing records, no automatic merges, private APK delivery only by explicit request.

## Problem and authoritative baseline

Manual association of a cached recorded run with one planned session already exists. Normal `TrainingRepository.linkActivity` checks the cached `ActivityRecord`, checks its run discipline, scans loaded plans for the source activity ID under the repository mutex, and persists a rewritten `PlanSnapshot`. `PlanOperations.linkActivity` stores `linkedActivityId`, `linkPreviousStatus`, and the completed status in the planned session. Unlink restores the previous status without deleting the source record or an independent manual log. `PlanBackup.additions` already rejects duplicate linked IDs across visible existing/imported plans.

However, a link currently resides **inside plan JSON** in SQLite `records` (`account,kind,record_id` primary key); there is no normalized unique index on `(account,linked activity)`. `TrainingStore.enqueue` wraps *one record and its outbox edit* in a transaction, but a check across all plan JSON records is not a database invariant. Multiple writers, future provider sync, plan import, or stale cached plans must not silently reassign or double-count the same activity.

Relevant source:
- `native-android/app/src/main/java/io/stridelab/privatealpha/TrainingRepository.kt` — `linkActivity`, `changePlan`, `savePlan`, `restorePlans`.
- `native-android/app/src/main/java/io/stridelab/privatealpha/PlanOperations.kt` — link/unlink semantics.
- `native-android/app/src/main/java/io/stridelab/privatealpha/EngineContracts.kt` — `PlannedSession`, `PlanSnapshot`.
- `native-android/app/src/main/java/io/stridelab/privatealpha/PlanBackup.kt` — restore validation.
- `android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java` — schema version 1, transactions, records, outbox, cursors, conflicts, non-destructive upgrade guard.
- `native-android/app/src/main/java/io/stridelab/privatealpha/ActivityPlanLink.kt` — explicit user-facing linking.

## Required invariants

1. For each StrideLab **account**, one canonical recorded activity ID may be linked to **at most one** planned session across all plans.
2. A planned session may link to **at most one** recorded activity at a time.
3. Identical link/unlink operations must be idempotent. Changing an existing link requires an explicit user-approved unlink followed by relink.
4. Persisting the association, prior status, completed/unlinked status, plan revision, and outbox record must succeed or fail **atomically**.
5. The recorded source activity is never deleted or mutated by linking. Original workout prescription and any separate manual `WorkoutLog` remain intact.
6. Account ownership, recorded-run eligibility, supported plan policy, and revision checks remain enforced on every write.
7. A linked run is an **association**, not proof of completing prescribed intervals or pace. Do not create a second synthetic activity or count the source run twice.
8. Existing local plans, drafts, outbox, activity details, wellness, and session-vault data must survive the migration.
9. No background automation or sync may mark a workout Missed/Skipped, silently replace an association, or auto-approve an ambiguous match.

## Proposed persistence contract (requires review before coding)

Introduce a normalized SQLite table alongside existing plan snapshots, **not** a destructive conversion of saved plans:

```sql
CREATE TABLE activity_links (
  account TEXT NOT NULL,
  activity_id TEXT NOT NULL,
  plan_id TEXT NOT NULL,
  session_id TEXT NOT NULL,
  PRIMARY KEY (account, activity_id),
  UNIQUE (account, plan_id, session_id)
);
CREATE INDEX activity_links_by_session
  ON activity_links(account, plan_id, session_id);
```

The existing `PlannedSession.linkedActivityId` and `linkPreviousStatus` must remain readable for compatibility; the normalized table is the uniqueness authority. It must agree with stored plan JSON at every committed transaction boundary. The explicit account key prevents links from colliding across signed-in athletes. **Provider identity design is still open:** avoid assuming two provider-specific raw activity IDs are globally unique; resolve canonical source identity and deduplication semantics before enabling Garmin + Intervals.icu multi-provider auto-matching.

Keep schema/table naming and indexes subject to detailed implementation review. Do not introduce cascade deletes or foreign keys that could erase imported activities or historical plan records.

## Versioned, non-destructive migration

`TrainingStore` currently opens version **1** and `onUpgrade` intentionally throws instead of wiping data. Proposed v1 → v2 migration must:

1. Run inside SQLite's existing upgrade transaction; do not call `onCreate` destructively, drop any tables, reset credentials, or modify legacy WebView data.
2. Create `activity_links`; scan the current account-scoped `records` table for `kind='plan'`. Decode safely, retaining original JSON and all unknown fields.
3. Backfill one normalized association for each existing non-null `linkedActivityId`.
4. If two saved sessions claim the same account/activity ID, if a plan snapshot cannot be interpreted safely, or if IDs violate expected constraints, **do not choose a winner or delete records**. Abort or expose a non-destructive review path and retain original v1 bytes. Define that recoverability path explicitly before implementation.
5. Assert that number/identity of associations matches the recognized plan snapshots before committing.
6. Preserve all existing `records`, `outbox`, `conflicts`, `cursors`, and `resolutions`, plus account isolation and encryption. Consider checkpoint/backup evidence before device acceptance.
7. Keep prior versions readable where allowed, but never downgrade destructively. A migration failure is a blocked acceptance condition, not a reason to uninstall the app.

Do not copy or package a production user database for public CI. Use fully synthetic SQLite fixtures, including v1 fixtures with existing links.

## Atomic mutation design

Implement one **store-owned transaction** for account-scoped manual link and unlink. It should load and revision-check the latest saved plan snapshot (not just a stale `TrainingState` copy), verify that the recorded activity is cached and genuinely a run, enforce existing plan-policy and status rules, apply normalized-table uniqueness, and persist the updated plan JSON plus corresponding outbox edit in that same transaction.

Do **not** implement nested unrelated write flows that commit a link table row without its plan snapshot. Refactor `TrainingStore.enqueueInside` or provide a tightly scoped transaction primitive with the same idempotency/version protection; ensure transaction APIs cannot be called outside their expected locking boundary. Repository mutexes remain useful for the in-process UI but are not the only invariant.

For unlink: remove only the relevant link row and restore `linkPreviousStatus` as already defined, then update the plan snapshot in that transaction. For relink: require a committed explicit unlink first. Retain the source activity and manually recorded workout log even when unlinking.

Review `restorePlans` and plan import as multi-record operations. `TrainingRepository.restorePlans` currently starts an outer SQLite transaction and calls `store.enqueue` for each addition, while `PlanBackup.additions` checks duplicates in memory. A v2 design must ensure plan restoration and any normalized link backfill are one atomic unit; test rollback of the *entire* batch if any one association conflicts.

## Required test and review matrix

- **Migration v1 → v2:** synthetic linked and unlinked plans, pending outbox edits, one supported and one unknown future-policy plan; preserve raw payloads, revisions and account ownership after reopening.
- **Collision:** v1 duplicate links must fail closed and preserve original records; no silent reassignment.
- **Unique constraint:** two planned sessions in different plans cannot claim the same activity ID; same ID in separate accounts must not conflict.
- **Session uniqueness:** one planned session cannot claim a second distinct activity without unlinking.
- **Concurrency:** two repository instances/process-like writers race on the same source run; exactly one transactional claim succeeds and the loser retains original state.
- **Rollback:** simulated interruption, write/SQLite constraint failure and outbox failure leave link table and plan JSON mutually consistent.
- **Restore:** imported duplicate links, conflicting existing links, multi-plan import failure, repeated import and foreign-account backups do not partially mutate records.
- **Historical preservation:** prior completed logs, manually logged run data, drafts, unrelated activity/HRV/sleep data and original prescriptions survive migration and offline reopen.
- **Provider dedup:** the same physical run through Garmin/Intervals.icu must not produce two accepted completion associations without identity resolution; no inference based on coincidental date/distance alone.
- **UI:** user sees explicit link, unlink, rejection and retry messages; no false claims of interval compliance or hidden link replacement.
- **Upgrade:** preserve an installed app's schema via synthetic upgrade seed; signer/update compatibility remains a separate phone acceptance gate.

Verify tests on local JVM, disposable Android emulator with real SQLite, and exact-head private GitHub CI. Keep automatic APK artifact packaging disabled unless owner requests an installable package.

## Integration gates

**Safe to do now:** preserve manual linking behavior and reject implicit replacement (draft PR #9); audit identity/provenance handling; write synthetic migration fixtures and test design.

**Blocked pending review and execution environment:** schema v2 migration, storage-level link constraints, canonical multi-provider dedup, transaction refactor, and full concurrency/upgrade verification.

**Never claim P0-3 closed** until the new invariant is proven by persistent SQLite tests after process restart, concurrency tests, migration/backup rollback, and exact-head CI. This design document is a plan, not an implementation or approval.
