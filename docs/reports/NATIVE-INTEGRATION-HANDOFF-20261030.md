# StrideLab native Android integration handoff — 2026-10-30

**Status:** GitHub-only source review and integration plan. **No source merge, Android build, emulator run, APK publication, live provider session, migration, or phone operation was performed in this handoff.** Use this as a queue map, not an acceptance certificate.

## Short answer for the next maintainer

1. **Stay on Android.** The web prototype is maintenance-only. Do not use `main` as an Android product baseline. At review, `main` most recently exposed commit `368b202128b0df46e76328e188dd971bafa501b7`, an archival/documentation checkpoint.
2. **Protect the exact verified source:** `checkpoint/native-guided-multisession-verified-25be8f3` = `25be8f3828374d1e539c7e825afa20fbcb652b56`. PR [#3](https://github.com/Isshhin/stridelab-development/pull/3) records successful exact-head Android CI [run 40471802151](https://github.com/Isshhin/stridelab-development/actions/runs/40471802151) and an installable private test APK. It remains an independent checkpoint, not an authorization to overwrite an installed app.
3. **Review reliability first:** draft [#27](https://github.com/Isshhin/stridelab-development/pull/27), head `12b9869aadface65ef35f340d3f01e84809eda24`, base the verified checkpoint. Includes source fixes for invalid workout completion/restoration and an offline FIT decoder/cache. This is not a complete import UI, live Garmin integration, or new plan authority.
4. **Treat the library as an independent additive candidate:** draft [#28](https://github.com/Isshhin/stridelab-development/pull/28), head `9e819b7ed15dfed7285260f52d1949b22b571d05`, same base. It preserves the 44 original catalog entries and adds a structured 66-record companion. **Current UI and coaching selector do not adopt the new records automatically.**
5. **Do not advance writable Engine 2.0, production activity-link migration, or automatic provider matching yet.** Preserve existing `TrainingStore` approval authority. No source-backed proof yet establishes safe coexistence with the separate Engine 2.0 proposal database.

## Source and review evidence

| Workstream | Current source / verified report | What is actually established | Gate still open |
|---|---|---|---|
| Native guided workouts and multi-session | [#3](https://github.com/Isshhin/stridelab-development/pull/3), `25be8f3` | 94 JVM, 79 emulator instrumentation, legacy upgrade seed, offline/process probes and APK packaging recorded in passing exact-head CI | Physical-device installation, signer compatibility and actual daily use |
| QA guards / FIT offline foundation | [#27](https://github.com/Isshhin/stridelab-development/pull/27), `12b9869` | Its report records local 147 JVM, 14 Python and 90 emulator passes plus separate upgrade/lifecycle probes | Exact-head GitHub Actions CI, PR review; FIT picker and canonical activity promotion; live account auth |
| Exercise Library 2.0 | [#28](https://github.com/Isshhin/stridelab-development/pull/28), `9e819b7` | Report records 31 Python, 105 JVM and 104 Gradle Android unit passes; 66 offline structured records with legacy source preserved | Exact-head CI, combined integration and emulator checks, content/media rights review, UI adoption |
| Native UI / design | [#8](https://github.com/Isshhin/stridelab-development/pull/8), [#23](https://github.com/Isshhin/stridelab-development/pull/23), design audit [#6](https://github.com/Isshhin/stridelab-development/pull/6) | Candidate accessibility/detail UI code and documented audit findings | Approved visual reconstruction, screenshot/TalkBack/font-scaling/device review; avoid conflicting Canva work |
| Unified Coaching Engine 2.0 | Stacked [#13](https://github.com/Isshhin/stridelab-development/pull/13) through [#20](https://github.com/Isshhin/stridelab-development/pull/20), then [#25](https://github.com/Isshhin/stridelab-development/pull/25) | Candidate data-context, selector, draft plans, approval and explanations | One canonical plan authority, persistence/migration design, exact-head Android and multi-discipline acceptance |

The PR #27 vs PR #28 changed-path sets were independently compared through GitHub on 2026-10-30: **32 paths in #27, 19 in #28, zero identical changed paths.** Both are descendants of the exact checkpoint (`git compare` reported 19 and 2 commits ahead, respectively). This establishes only **file-level non-overlap**. It does **not** prove merged Kotlin/Android semantics, Gradle compatibility, account isolation, instrumentation, or runtime behavior.

### PR consolidation decisions

- **#26 QA fixes, #24 FIT foundation, #9 explicit unlink protection, selected #22 offline outbox tests** are already represented in #27; do **not** merge the originals again wholesale. **#7** overlaps completion fixes; its UI wording and packaging changes need separate consideration.
- **#2** is already represented in the verified multi-session work of **#3**. The source-based baseline is the checkpoint, not a merged `main`.
- **#28** is standalone, largely additive; preserve schema-1 `catalog.json` and the legacy `readLibrary` path. Do not silently connect `discover()` to prescription or infer skills from guides.
- **#10** is a migration design only: cross-plan activity-link uniqueness is not yet transactionally enforced in the verified app or #27. Hold automatic matching, and require backup/rollback/concurrency tests before schema changes.
- **#11/#12** synthetic datasets and **#13–#20/#25** Engine 2.0 stack remain independent candidates. Do not conflate generated fixture coverage with real athlete outcome validation. The Engine 2 approval ledger must not become a competing writable plan store.
- **#5/#6/#21** are reference audits/architecture docs. **#8/#23** are unaccepted UI branches. Avoid bulk cherry-picking unrelated redesign work.

Original dependency inspection and rationale: [#27 integration audit](https://github.com/Isshhin/stridelab-development/blob/12b9869aadface65ef35f340d3f01e84809eda24/docs/reports/PRIORITIES3-6-INTEGRATION-20261029.md). Library contract: [#28 contract](https://github.com/Isshhin/stridelab-development/blob/9e819b7ed15dfed7285260f52d1949b22b571d05/docs/EXERCISE-LIBRARY-V2-CONTRACT.md).

## CI blocker: distinguish environment from code failures

- The source report for [#27 run 40740365723](https://github.com/Isshhin/stridelab-development/actions/runs/40740365723) says the job did not start and that GitHub annotated a billing/spending-limit issue. A fresh connector check shows the job concluded `failure` with no returned steps. **Do not describe this as a failing application test.**
- [#28 run 40743576131](https://github.com/Isshhin/stridelab-development/actions/runs/40743576131) similarly reports two failed jobs with no returned steps. Runner/account root cause for #28 was **not independently confirmed**. Do not assume its workflow code is broken or claim green Actions.
- Account owner must resolve the GitHub Actions account/runner limitation and permit exact-source CI reruns. Do not repeatedly dispatch costly Android jobs while the blocker persists.

## Safe execution order once Android execution is available

1. **Snapshot and inspect.** Fetch the exact three SHAs above; check branch heads have not advanced, review open PRs and `AGENTS.md` if newly introduced, confirm the base remains the approved native checkpoint. Keep any new integration branch separate.
2. **Validate #27 alone.** Review its final changed files and QA assertions, compile actual Compose/Gradle source, run JVM/Python, emulator tests, legacy v1-to-current upgrade test and process death/offline reopen; check invalid actual sets/backups reject atomically and unchanged saved plans remain intact. Verify the FIT decoder handles missing values/unknown fields; multisport remains fail-closed.
3. **Validate #28 alone.** Run schema/content/media checks, existing-and-new JVM contract tests, Android unit tests; confirm 44 preserved legacy IDs/source bytes, 22 new records and no automatic engine eligibility expansion. Do not interpret educational guides or image placeholders as ready-to-prescribe exercises.
4. **Construct a new reviewed combined candidate** from #27 only after its acceptance, then layer #28's exact additive changes onto it. Retest the **combined exact SHA**, including Gradle build, Android tests, legacy-upgrade/reopen, library open/discover and absence of proposal/recording regressions. No historical checkpoints or original drafts should be force-pushed.
5. **Hold installation** until the candidate's package ID, version code, APK SHA-256 and signing-certificate fingerprint are documented and compared with the currently installed app's signer. The checkpoint APK used an ephemeral CI debug signing key; **do not uninstall/clear the current app** to bypass an update signature mismatch. Keep backup/export and rollback evidence.
6. **Resume UI and feature adoption separately.** Implement the approved AMOLED reconstruction on the accepted native baseline, test real/synthetic data absent-state behavior, TalkBack, scaling, dashboard provenance and route/chart authenticity. Afterward, add reviewed library UI metadata only; re-evaluate Engine 2.0 single plan authority before any writable integration.

Recommended minimum combined acceptance evidence:

- Exact commit SHA and full `git status` before/after tests; actual build/test commands and summaries (not just report claims).
- Gradle/Compose compilation and Kotlin/JVM tests; Python library/schema checks; Android instrumentation and legacy upgrade; process kill/offline reopen/account-isolation checks; physical-device smoke test when possible.
- Valid/invalid workout completion and restore; no duplicate completed records, no data corruption on rejected backup or interrupted sync.
- FIT original byte preservation, account isolation, explicit review for ambiguous imports; no fabricated direct Garmin auth.
- Private APK signer/version/update compatibility; no public release; unchanged old app and stored data until verified.
- Explicit distinction between **passing locally/cloud** and **passing GitHub CI**.

## Re-entry instructions for a future Work session

> Resume `Isshhin/stridelab-development` from exact verified Android checkpoint `25be8f3828374d1e539c7e825afa20fbcb652b56` and read `docs/reports/NATIVE-INTEGRATION-HANDOFF-20261030.md` on branch `docs/native-integration-handoff-20261030`. Recheck current PR #27 (QA/FIT head `12b9869`) and #28 (Exercise Library 2 head `9e819b7`) against their live branch heads. First resolve the account/runner CI blocker; do not mislabel zero-step Actions as code failures. Review #27 on its own; verify exact-head Android tests; then build and test one isolated combined candidate incorporating #28. Leave Engine2's competing writable proposal ledger, production database migrations, automatic Garmin matching, UI/Canva redesign and public releases out of scope. Preserve current phone data and signing. Do not merge or package until exact-head evidence and owner review are complete.

## Follow-on source review and proposed safety fix (same date)

[Draft PR #29](https://github.com/Isshhin/stridelab-development/pull/29), based on #27, guards `TrainingRepository.restorePlans` against overwriting retained undecodable `records` rows when a backup contains the same ID. A new isolated SQLite instrumented regression covers whole-backup rejection, unchanged raw payload/outbox and successful unrelated restore. Its [repository-wide audit](https://github.com/Isshhin/stridelab-development/blob/fix/native-opaque-backup-id-guard-20261030/docs/reports/REPOSITORY-SOURCE-REVIEW-20261030.md) documents source-level findings and limits. **This is uncompiled/unverified source only.** It must be tested against #27's exact head before integration. If #27 or #29 changes, refresh ancestry and test evidence; never assume existing CI applies to the new candidate.

## This handoff's limitations

This document was authored through the GitHub connector without a checkout of all source bytes and without any execution environment. File-list comparison and cited PR reports ground the plan; **none of the test results above were rerun by this handoff**. Current CI states may change after 2026-10-30. New implementation should verify rather than trust this snapshot.
