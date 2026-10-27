# StrideLab Engine 2.0 — Persistence Candidate, Phases 22–25

**Status:** Draft additive implementation, **not** installed, deployed, migrated or emulator-verified.  
**Source baseline:** Existing `TrainingStore.java` under the legacy Android source set defines `stridelab-athlete-v1.db`, SQLite v1, and owns records/outbox/conflicts/cursors/resolutions.  
**New candidate:** `Engine2LedgerSqliteStore.kt` uses **only** `stridelab-engine2-proposals-v1.db` unless an isolated test database name is supplied, and rejects the legacy database filename. No existing athlete schema is upgraded.

## Implemented

- `Engine2LedgerCodec.kt` encodes and decodes complete, versioned Kotlin ledger snapshots: plan sessions, revisions, all pending and finalized proposals, supporting record IDs, decisions and receipts. Unknown versions are refused. The existing `Engine2ApprovalLedger` constructor validates decoded account IDs, duplicate pending sessions, decision/status consistency and associated receipts.
- `Engine2LedgerSqliteStore.kt` creates a **separate**, account/plan-primary-keyed SQLite store. Provides guarded one-time seed, atomic staging of pending proposals, transactionally checked compare-and-swap approval/rejection/postponement and durable reload. Every decision candidate is recomputed by the deterministic reducer **inside the same database transaction** before updating, and expected plan and ledger revisions are compared before writing. Rollback leaves prior data untouched.
- `Engine2ApprovalCoordinator.kt` supplies an unbound, explicit-user-action facade that first persists a reviewable proposal, then requires a separate confirmed decision. A stale revision returns a refresh-needed result. It has no background scheduler hooks and cannot silently change original Engine 1.0 calendars.
- `Engine2LedgerCodecTest.kt` verifies versioned JSON roundtrips, invalid schema/owner/decision tampering and duplicate proposal IDs.
- `Engine2LedgerSqliteInstrumentedTest.kt` is Android-only and tests seed/stage/decide/reopen, stale compare-and-swap, forged candidate refusal, rejection preserving original minutes, account isolation, explicit confirmation, and prohibition on using the original v1 database filename.

## Explicitly blocked / not yet addressed

1. **No real legacy migration.** This is intentional. New Engine 2.0 data is isolated and **not synchronized** to the legacy approved plan or existing outbox. An atomic multi-database migration is not available; in-app use requires a coordinated architecture that does not permit two contradictory authoritative schedules.
2. **No UI actions enabled.** `Engine2ProposalReviewCard.kt` still renders a disabled preview and is not bound to the coordinator. Real navigation and authenticated-user checks are absent.
3. **No authenticated account binding in storage API.** The caller passes the account ID; the future native repository must bind it to the verified current session. The storage database is Android app-private but not independently encrypted. Account switch, token loss, backup and restore policies need device testing and security review.
4. **No cross-provider activity-link transactional invariant or FIT/Garmin authorization.** A separate design PR #10 addresses the activity-link association schema. Do not silently conflate it with proposal receipt storage.
5. **No legacy app schema-version change**, no APK signing compatibility verified, no user phone data affected. Existing legacy outbox stays untouched.

## Acceptance sequence after renewal

- Run Python native tooling suite, Kotlin/JUnit and Android instrumentation against an exact reviewed commit.
- Exercise interruption/crash recovery, corruption handler, account switches, concurrent commits, library catalog guards, disk-full cases and controlled forward-migration from synthetic copies.
- Confirm the existing v1 athlete database is byte-preserved through all Engine 2.0 tests.
- Only after authentication, persistence and device acceptance may a UI implementation expose the approval buttons; no automatic approvals or hidden plan edits.

No public release, private APK packaging or physical phone update was requested.
