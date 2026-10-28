# StrideLab continuation checkpoint — 2026-10-28

## Current status
- Source baseline: `25be8f3828374d1e539c7e825afa20fbcb652b56` on `checkpoint/native-guided-multisession-verified-25be8f3` (the last exact-head verified Android checkpoint).
- Current safe working branch: `docs/stride-architecture-batch35-20261028`, created directly from the verified checkpoint.
- This file is a *planning checkpoint only*. It does not implement, compile, test, merge, or deploy app behavior.
- Native app is private Android-first; no public release, no production database modification, and no update/uninstall recommendation for an existing signed installation.
- Existing Engine 2.0 experimental source is in stacked draft PRs #13–20, **not** in this clean architecture branch. P0 draft fixes are #7–10. All are unmerged as last verified.

## Requested execution
Implement continuous batches with four phases each while following test-first and single-source-of-truth safeguards:
- Batch 3.5 / phases 26–29: versioned canonical multi-discipline read adapter and legacy compatibility specification; local/remote mutation and idempotency contract; single plan-revision/approval authority decision; GitHub branch/PR dependency triage.
- Batch 4 / phases 30–33: truthful completion (reuse PR #7), audited unique recorded-run associations and safe copy-based migration, existing SQLite outbox recovery/replay contract, account isolation and independent opt-in APK packaging.
- Batch 5 / phases 34–37: compact shared Compose design components and activity view model, Activity Detail for genuine recorded and manually logged work, Overview/calendar, curated library filtering/image fallbacks.
- Batch 6 / phases 38–41: native FIT parsing, source identity/link review, Garmin-ready mock adapters/wellness normalization, Android import-picker and malformed-file tests; defer GPX/TCX and live Garmin authorization.
- Batch 7 / phases 42–45: Engine 2.0 *read-only* integration before writes, cross-discipline workload policy, explicit user-approved atomic proposal path, structured explanations and accepted-PR consolidation.

## Verified key facts
- Native `TrainingStore.java`: SQLiteOpenHelper `stridelab-athlete-v1.db`, schema v1, tables `records`, `outbox`, `conflicts`, `cursors`, `resolutions`; upgrades deliberately fail closed. Outbox `enqueue`, `ready`, `acknowledge` exist, but no native remote plan mutation replay caller was verified.
- `EngineContracts.kt` defines `PlanSnapshot`, `PlannedSession`, `WorkoutLog`, `SetRecord`, `ExercisePrescription`; `PLAN_SCHEMA=1`. Keep unknown engine/policy payloads opaque and historical actuals unchanged.
- Exercise catalog JSON has 44 entries: 23 image-backed executable entries, 21 guide-only entries without images. Rights metadata exists for image-backed entries but is not independent legal verification.
- No native FIT parser or direct Garmin authorization in the verified checkpoint. Existing fetched activity streams can include authentic source-backed laps and GPS.
- Engine 2.0 PR #20 adds a **separate experimental** `stridelab-engine2-proposals-v1.db`, not a verified single-authority production migration.
- Verified source checkpoint has a passing exact-head native CI history; Engine 2.0 PRs #13–20 have **not** passed a complete exact-head Android/Gradle/emulator build.

## Resume instructions
1. Fetch this file and check branch/PR heads for concurrent changes.
2. Complete the four Phase 3.5 specification files with exact source evidence: (26) canonical adapter, (27) sync contract, (28) authority and approval ADR, (29) PR integration matrix.
3. Open a **draft PR** against the checkpoint, without merging or triggering GitHub Actions. Do not claim Phase 3.5 verified until its documents and relevant tests are inspected.
4. Work on an isolated follow-on branch for Batch 4; do not merge/modify installed data or run destructive migrations. Continue staged batches only with explicit test status.
5. User wants substantial implementation independent of ChatGPT Work and Copilot, while the CI budget is limited. No silent CI triggers, deploys, public releases, or APK installation.

**Unattended work disclaimer:** A normal chat cannot continue autonomous implementation after the session stops. Resume from this checkpoint in a new chat when the user returns.
