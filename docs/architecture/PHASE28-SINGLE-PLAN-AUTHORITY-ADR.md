# Phase 28 — ADR: One authoritative approved training plan

**Status: PROPOSED — NOT APPROVED FOR MIGRATION OR WRITABLE ENGINE2.** Source checkpoint: `25be8f3`.

## Context (verified)

- Engine1 plans are JSON `PlanSnapshot` documents in `records(account,'plan',record_id)` within `stridelab-athlete-v1.db` (SQLite v1). `TrainingRepository.changePlan` checks `PlanSnapshot.revision` in the serialized repository state, and `savePlan` atomically stages the updated snapshot and local outbox entry.
- `PlanOperations` makes approved/paused plan changes via explicit UI actions and preserves historical `WorkoutLog`/sets; `Engine1Engines.canManage` protects unknown engine/policy.
- Draft PR #13 `Engine2PlanBridge.preview` is read-only. Draft PR #20 adds `stridelab-engine2-proposals-v1.db` (SQLite v1), `proposal_ledgers(account,plan_id,...payload)`; it has local CAS semantics **within that experimental DB only**. Neither PR proves atomic coordination with Engine1 `records`.
- Engine2 duration proposals use PENDING/APPROVED/REJECTED/POSTPONED states; expiry, cross-discipline reschedule, and atomic cross-store approval are not yet verified.

## Decision for first integration milestone

**Single authority: `TrainingStore.records` `kind='plan'` remains the authoritative approved plan.** The canonical v2 athlete context and Engine2 domain plan are **read-only projections**, not standalone sources of approved session state.

A proposal may be generated locally against an immutable plan snapshot and confirmed evidence, but **must not be accepted through any separate store alone**. The isolated PR #20 ledger is a non-authoritative experimental test fixture until proposal receipt and plan revision can commit in **the same SQLite transaction**.

## Proposed eventual write contract (not implemented)

- Same account and plan ID, expected `PlanSnapshot.revision`, immutable proposal ID, original session values, evidence IDs and timestamp.
- Verify authenticated local account and current stored plan revision inside the transaction, not only the UI's cached state.
- Validate proposal target remains unstarted, account matches, and exact before/after diff is narrow and supported by deterministic reducer.
- Atomically write updated approved `PlanSnapshot`, monotonic revision, proposal status/decision receipt, and one local outbox record. A repeat command with same proposal ID returns the prior receipt unchanged; a stale/different command fails closed.
- Strong candidate: add an account-scoped `proposal_receipts` table in a reviewed additive v1→v2 migration **in the same database as plans**. This is a design option, not chosen SQL or executed migration.
- Preserve old plan JSON, actual `WorkoutLog` values, unrelated activity/wellness/draft rows, account isolation, and pre-upgrade recovery copy. Unknown engine policies remain read-only.
- The existing `Engine2LedgerSqliteStore` must not silently become a second authoritative plan, and accepting its proposal cannot be represented by writing only to its DB.

## Rejected designs

1. **Dual mutable authorities** in two databases with no distributed transaction: crash between commits yields incompatible approved plans.
2. **Updating only the Compose/StateFlow state** without a durable plan revision: process death loses the approved decision.
3. **Reading an obsolete snapshot and blindly replacing latest JSON**: can overwrite a recent actual log or workout move.
4. **Automatically migrating every historical plan** to Engine2: destroys versioned engine policy history and unknown fields.

## Tests before writable activation

1. Seed a realistic v1 saved plan with completed running and strength histories; read through Engine2 bridge; assert original serialized bytes unchanged.
2. Stage a proposal then restart; plan remains unchanged until user approves.
3. Accept with matching revision; persist proposal receipt and plan changes together or neither, including forced crash and DB failure.
4. Replay approval twice; exactly one accepted plan revision/receipt; subsequent different proposal against stale revision rejected.
5. Concurrent account A/B and two processes; no cross-account access and at most one writer wins.
6. Unknown policy/future schema stays read-only and recoverable; migration on an unsupported plan aborts safely.
7. Reject/postpone leaves plan unchanged and retains immutable evidence.

## Gate

Before implementing a v2 migration or enabling UI approval: owner reviews this ADR, source and candidate SQL verified against `TrainingStore`, synthetic migration/rollback and exact-head emulator pass. No public/private APK update should be recommended solely because an approval reducer unit test passes.
