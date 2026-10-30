# StrideLab repository-wide source review — 2026-10-30

**Scope and confidence:** This is a GitHub-connector source-level audit, not a complete line-by-line review of every blob, a software composition/security scan, an Android compilation, or real-device verification. The earlier [repository-wide inventory](https://github.com/Isshhin/stridelab-development/blob/prep/engine2-persistence-phases22-25-20261028/docs/reports/STRIDELAB-REPOSITORY-WIDE-IMPROVEMENTS-20261028.md) enumerated 275 entries in the verified native Git tree; this review independently rechecked the native verified checkpoint, the #27/#28 draft changed paths, and representative files across Kotlin app, Java SQLite/Keystore, UI, running/wellness data, backend, Gradle/CI, legacy APK and Python tools. The existing web frontend is maintenance-only. No local device, live account, service credential, CI runner or working complete checkout was used.

## Source anchors / no blind merge

- Main branch observed: `368b202128b0df46e76328e188dd971bafa501b7`, not the latest verified Android implementation.
- Exact verified native checkpoint: `25be8f3828374d1e539c7e825afa20fbcb652b56`; passing exact-head [Actions run 40471802151](https://github.com/Isshhin/stridelab-development/actions/runs/40471802151), as reported in [#3](https://github.com/Isshhin/stridelab-development/pull/3).
- Pending reliability/FIT integration: [#27](https://github.com/Isshhin/stridelab-development/pull/27) head `12b9869aadface65ef35f340d3f01e84809eda24`.
- Pending additive library: [#28](https://github.com/Isshhin/stridelab-development/pull/28) head `9e819b7ed15dfed7285260f52d1949b22b571d05`. Direct path comparison showed zero overlapping modified paths with #27, not semantic compatibility.
- Architecture/PR acceptance map: [October 30 handoff](https://github.com/Isshhin/stridelab-development/blob/docs/native-integration-handoff-20261030/docs/reports/NATIVE-INTEGRATION-HANDOFF-20261030.md).

## P0 confirmed source-level data-integrity defect: backup can overwrite preserved opaque plan

**Reproduction path in source before this fix:**

1. `TrainingRepository.loadCache` reads raw `records` of kind `plan` and tries `PlanSnapshot.decode`; decoding failures are intentionally **retained on disk but excluded from** `mutable.value.plans`, with an unsupported-plan count.
2. `TrainingRepository.restorePlans` called `PlanBackup.additions(..., mutable.value.plans)`. That function skips only plan IDs visible in decoded memory.
3. A valid incoming backup using the same ID as one of the retained, undecodable records was treated as a new addition. `TrainingStore.enqueueInside` uses `SQLiteDatabase.CONFLICT_REPLACE` for `records`, and existing locally created/unsynced records with null server version permit replacement.
4. This could replace a retained older/unsupported raw plan payload, despite the user-facing guarantee that original records remain preserved. This is an **on-disk collision risk**; it has not been reproduced on a running Android device in this review.

**Proposed isolated fix:** On a branch based on #27, add a same-account, exact-plan-ID existence check on actual SQLite `records` inside the **existing** backup-restore transaction. Scan all candidate additions for collisions **before enqueueing any**, fail closed with a descriptive `PlanConstraint`, and retain existing data and outbox. Keep legitimate re-imports of already decoded plan IDs idempotent via `PlanBackup.additions`; allow unrelated new IDs. No schema/API/signing/version change.

**Regression test added:** `Engine1RepositoryTest.opaqueRetainedPlanIdCannotBeOverwrittenByBackupRestore` seeds a schema-999 opaque raw plan in real isolated SQLite, reopens offline to ensure it is absent from the decoded view, attempts a valid two-plan backup containing an ordinary plan followed by the conflicting ID, requires complete rejection and unchanged raw payload/outbox, then verifies that the unrelated valid plan still restores, and that the opaque original survives offline reopen.

**Verification:** Source patches and two-commit comparison were re-read and checked for only the intended changed files. **Android Gradle/Compose, emulator, instrumented regression and physical-device tests have not run.** Keep the PR draft and do not integrate or install until its exact head is tested.

## Other findings and blockers

| Priority | Area | Source-grounded state | Next actionable gate |
|---|---|---|---|
| P0 | One approved-plan authority | #27 retains existing `TrainingStore`; Engine 2 draft [#20](https://github.com/Isshhin/stridelab-development/pull/20) holds a separate proposal ledger without integrated canonical authority. | Define approved-plan single writer, tested versioned migrations and rollback before writable Engine 2 integration. |
| P0 | Cross-plan activity-link consistency | `TrainingRepository.linkActivity` checks decoded in-memory plans, and #27 checks imported backup duplicates, but durable database uniqueness for all restored/opaque/contending plan states is still absent. | Explicit migration/SQLite concurrent-writer test and rollback, per [#10](https://github.com/Isshhin/stridelab-development/pull/10); no automatic matching meanwhile. |
| P0 | CI/account verification | #27 GitHub run 40740365723 and #28 run 40743576131 ended without executing returned job steps; #27 reports billing/spending-limit annotation. | Owner resolves account/runner issue; only then run exact-head Android/legacy/emulator tests. Never claim these are product test failures. |
| P1 | App data continuity and private APK | AndroidManifest disables OS backup, SQLite/Keystore and ZIP-based checkpoints are not an automatically verified device restoration path, and debug-signed artifact identity differs between CI runs. | User-controlled export/restore and verified installed signing fingerprint before any APK update. Never uninstall to bypass signing mismatch. |
| P1 | Direct connected Garmin | #27 includes official FIT parsing, local cache and provider contracts, but neither live Garmin OAuth/PAT transport nor a complete user-facing importer. | Authorized account access, secure token storage and picker/review -> canonical activity transaction; manual FIT alternative until then. |
| P1 | Offline outbox delivery | `TrainingStore` supports durable edit/outbox state, but #27 explicitly does not implement a confirmed remote replay consumer or server-side idempotent write route. | Validate remote endpoint/RLS/version contracts and replay under interruption, wrong account, conflict and retry. |
| P1 | UI/UX and accessibility | Old UI audit [#6](https://github.com/Isshhin/stridelab-development/pull/6) found weak outline contrast and missing chart semantics; proposed [#8](https://github.com/Isshhin/stridelab-development/pull/8) and [#23](https://github.com/Isshhin/stridelab-development/pull/23) are unaccepted. | Resume approved AMOLED design only after reliability integration; screenshot, font 1.3–2x, TalkBack, action accuracy and source-freshness checks. |
| P1 | Exercise Library 2.0 | #28 provides 66 structured records and a read-only loader; 43 placeholders, 23 inherited-unverified media, and no automatic coaching selector adoption. | Review provenance, media rights and exact movement fit, then opt-in UI-only adoption with approved selector preserved. |
| P2 | Reproducible verification | `native-android` runs Gradle 8.13/AGP 8.11.2, and supports CI/emulator and Python checks, but many drafts have only local or source-draft reports. | Record exact SHA, test totals, APK signer, original/combined test suites; avoid counting test matrix cases as independent tests. |
| P2 | Wellness and activity interpretation | `ActivityRecord`, `WellnessRecord`, `WeeklyDashboard` and `ActivityIntelligence` preserve null metrics, source and method; present comparison is descriptive only. | Test time zone boundaries, duplicate-source windows, missingness, and longitudinal historical consistency before claiming fitness/readiness. |
| P2 | Legacy code and archived web | Native Gradle sourceSets intentionally compile selected legacy Java storage from `android-app`; `main` retains legacy/archive/docs and isn't interchangeable with Android baseline. | Keep pinned Java interop tests and archive separation, avoid cleaning/changing legacy storage without migration. |

### Positive safeguards seen in source

- Native Android manifest has `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`; only launcher activity is exported, and the legacy activity is internal.
- Supabase transport uses HTTPS, disables redirects/cache, limits response size and maps service errors without echoing JWTs/server bodies. The bundled key is labelled a **publishable client key**, not a privileged secret.
- `TrainingStore` uses SQLite transactions, durable outbox and a strict non-destructive migration failure; `SessionVault` encrypts sessions with Keystore-based AEAD.
- #27 contains actual-set validation, backup schedule checks, account-scoped FIT original file archive and read-only activity candidate matching.
- Old apps and private debug APKs are not treated as signer-compatible without explicit metadata checks. Emulator scripts refuse physical-device targeting.

These observations are from inspected source and prior exact-head reports; they are not a security certification or guarantee of absent bugs.

## Safe work **without** Work execution

Completed in this source-review workstream: diagnosed one disk-collision data-risk, committed a narrow in-transaction guard and real-SQLite regression test to an isolated branch, and documented repository-wide triage. **No CI dispatch, merge, release, signing, database migration, production user data or app installation.**

Additional worthwhile GitHub-only tasks: static CI workflow/security checks; test-fixture additions for backup/link collision, draft timestamp ties, and FIT identity; documentation cleanup; PR overlap/consolidation; curated library metadata review. Full Android integration, physical-device visual review and authenticated live provider testing remain gated by their respective environments.

## Exact-head acceptance list for this fix

- Compile `TrainingRepository.kt` and `Engine1RepositoryTest.kt` against the actual #27 head.
- Run `:app:testDebugUnitTest`, full `:app:connectedDebugAndroidTest` in a disposable emulator, legacy upgrade, offline process reopen, backup/outbox and cross-account suites. Run Python 14 baseline tests.
- Confirm unsupported/opaque raw plan ID collision rejects **without changing even the earlier valid entries in the same file**, leaves raw bytes and outbox identical, and permits unrelated valid restores after rejection.
- Verify `main`, #27, #28, existing verified checkpoints, APKs, installed data and schema remain untouched.
- Keep this change on a draft PR until review and exact-head tests complete.
