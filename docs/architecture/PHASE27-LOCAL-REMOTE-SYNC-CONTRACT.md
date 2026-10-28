# Phase 27 — Offline-first local/remote synchronization contract

**Status:** Proposed architecture, no remote mutation implementation. Baseline commit `25be8f3`.

## Verified primitives

- `TrainingStore.java` extends `SQLiteOpenHelper`, DB `stridelab-athlete-v1.db`, version **1**. Tables: `records` PK (account,kind,record_id), `outbox` PK seq and UNIQUE operation_id, `conflicts` PK (account,kind,record_id), `cursors` PK (account,scope), `resolutions` PK seq.
- `enqueue` updates cached `records` and inserts a durable `outbox` operation in one SQLite transaction. `ready(account,limit)` selects head operations, `acknowledge(account,operationId,serverPayload,serverVersion,now)` atomically acknowledges one, `recordRetry` records categorized retries, and `resolveConflict` requires explicit approval with conflict evidence.
- `TrainingRepository.savePlan` uses `enqueue`, but has a source comment: “No cloud replay is claimed here.” No native use of `ready`/`acknowledge` was found. `fetchActivities`, `refreshWellness`, `loadDetail` instead fetch authenticated remote records and cache them via `applyRemotePage`.
- `WorkoutEntryDraft` is locally staged through `applyRemotePage` with `kind=workout`, not sent as a confirmed workout, and is validated by owner, plan, session and log revision.
- `SupabaseGateway` is a read/auth/integration boundary: sign-in, token refresh, read activities, read wellness/details, call `stridelab-intervals`. A verified native plan-write backend with authoritative version tokens and idempotent operation IDs **is not present**.

## Authority matrix — safe initial policy

| Kind | Read authority | Local behavior | Remote replay policy until contract approved |
|---|---|---|---|
| `activity`, `activity_detail`, `wellness` | Authenticated provider/Supabase source | Account-scoped validated read-through cache | **READ-ONLY**; do not synthesize writes |
| `plan` | Latest account-scoped local `records` snapshot | Local-first, atomic outbox staging | **DISABLED** until versioned server write and RLS checked |
| `workout` entry draft | Local `records` | Per-account only, never confirmed actual | **NEVER** replay unconfirmed input as workout |
| `exercise` shelf/templates | Local versioned record | Queue remains local | **DISABLED** until server kind and conflict semantics verified |
| Engine2 proposal/receipt | Experimental separate store in draft PR #20 | Not app-connected, no UI approval | **NEVER** replay until one authoritative plan/revision contract exists |

**Source of truth:** Local SQLite is authoritative for accepted local edits while offline; remote providers are authoritative for source-imported records. “Local committed” does not mean “server acknowledged.” If there is a conflict, retain both histories and require explicit review. No silent winner or destructive replacement.

## Future writable replay contract (not implemented)

1. Select pending head operations from `ready(account,limit)` under the *verified current account* and authenticated session. Bind account, record kind/id, base_version and immutable operation_id.
2. Server must enforce authenticated ownership, RLS, version/compare-and-swap and **idempotency keyed by (account, operation_id)**, storing a receipt atomically with the changed server record. Repeated requests with the same ID/payload return the same receipt, not a second mutation; reused ID with different payload is rejected.
3. Only after a verified server acknowledgement (matching account, ID, kind, record, revision, and operation ID), invoke `acknowledge` locally. Commit new server version while preserving any later local chained edits.
4. On connectivity loss, preserve the pending operation and retry with the *same* immutable ID. On authentication failure, stop replay until reauthentication; don't attempt under the next signed-in account.
5. On version conflicts, keep the full local and remote payload in `conflicts`, block that chain, and let the user review a lossless merge. Do not replay a different account's rows.
6. Never automatically treat a remote activity's absence as a missed workout, or infer an activity match solely from date/distance.

## Critical crash and account-switch scenarios

- Server commits, client dies before acknowledgement; retry must receive same server receipt and apply exactly once.
- SQLite commit succeeds, UI process dies; restart must show saved plan with pending outbox and no false “synced” status.
- Account A has a pending write; switch to B while worker result for A is delayed; B must never see A payload, cursor, or receipt.
- Multiple edits of one record chain must replay in order (one unblocked head per account/kind/id).
- Conflict resolution must preserve both versions and original audit record; never wipe old records.

## Blockers and verification

**Unknown:** A backend authoritative plan-write endpoint, table shape, remote version token and server-side idempotent receipt semantics were not established. Do not add a replay consumer that blindly posts to Supabase. WorkManager currently refreshes history, not the unverified plan queue. Define provider/auth security before enabling remote mutations.

Acceptance after a backend exists: exact-head unit tests, DB concurrency tests, mock HTTP replay tests and a server idempotency test that simulates server-commit/client-crash, with manual approval of production schema/RLS if involved.
