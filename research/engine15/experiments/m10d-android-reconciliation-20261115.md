# Engine 1.5 M10d — Android review-flow and backup reconciliation

Date: 2026-11-15. Isolated follow-up stacked on PR #91's M10c head `a27a552632b53f97b932635236363cbb9b19adaa`. This change does not promote the research preview into an executable plan or change the Engine 1.5 production generator.

## Changes

- Reconciled the current Week Review surface with the newer Android implementation: explicit existing-coach/research selection, current dark/lime styling, readable week navigation and selected-week running-distance summary. Missing, non-finite, or invalid run distances remain unknown; no-running weeks do not claim zero kilometers.
- Added a saved-review read-only view in the builder and Training/Plan Tools. Reviews retain their research record type and explicit non-executable label; no approval, calendar, workout, or player action is added.
- Added backup schema 2 with separate `plans` and `researchReviews` arrays. Schema 1 plan-only backups remain readable. Schema 2 validates owner, record type, bounds, identifiers, and all records before restore; unsupported/corrupt input fails closed. The legacy plan-only reader refuses a schema 2 bundle containing reviews instead of silently dropping them.
- Restore computes additions without overwriting existing records and writes new plans plus local-only research reviews in one SQLite transaction. Research reviews are not placed in the cloud outbox. A review ID collision with different contents fails before mutation.
- Backup ownership remains exact-owner scoped. A local-only backup cannot be imported into a different local identity or cloud account; there is no account reparenting or identity-transfer mechanism in this batch.

## Verification before M10.1

- Test-first: the two new backup contract tests initially failed to compile because `PlanBackup.encode`/`readBundle` did not yet exist; this confirmed the missing contract surface.
- Focused JVM command: `:app:testDebugUnitTest --tests 'io.stridelab.privatealpha.IndependentResearchDraftTest' --tests 'io.stridelab.privatealpha.BackupIntegrityTest' --tests 'io.stridelab.privatealpha.WeekReviewDistanceTest'` — **29 tests, 0 failures, 0 errors, 0 skipped** (11 + 15 + 3).
- `:app:compileDebugKotlin` — BUILD SUCCESSFUL.
- `git diff --check` — clean.
- Instrumentation changes have not yet been compiled or executed; they are part of M10.1, which follows this M10d commit. No emulator or physical phone was changed in M10d.

## Preserved boundary and next step

Existing coach / Engine 1.5 remains the default. Research remains explicit, local, preview-only and non-executable; existing approved plans are not rewritten. No database schema migration was needed. After this M10d commit, execute M10.1 JVM, debug build, instrumentation compilation, and the available disposable-emulator scenarios. M10.5 Astra review and physical-device QA remain later owner-visible gates.
