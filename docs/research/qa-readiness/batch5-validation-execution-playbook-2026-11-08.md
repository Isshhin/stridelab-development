# StrideLab Pre-QA Batch 5 — November 12 Execution and Evidence Playbook

Prepared: 2026-11-08 (Asia/Manila). Intended first execution window: 2026-11-12, **not an automatic schedule**.
Preparation branch: `research/qa-readiness-batches56-20261108`.
Status: **READY FOR AUTHORIZED EXECUTION / NOTHING RUN BY THIS DOCUMENT**.

Read `AGENTS.md` at the CURRENT checkout first; its owner-approved workflow controls over historical plans. In the currently reviewed version, **Codex is code-edit-only by default**, no Gradle/tests/ADB/emulator/installation, and **owner performs Android Studio builds, device runs and visual acceptance**. A future explicit owner exception is needed before any agent executes these tasks. Connected Work/Codex capabilities do not imply device, credential, billing or ethics authorization.

## 0. Frozen tested candidates (different trees)

| Track | Branch | Historical exact candidate | Evidence |
|---|---|---|---|
| Engine 2.x synthetic | `research/p2x-integrity-wave4-20261108` | `6142d7a8f8976f9ea4647850ff9e6ea51ff289df` | 481 combined author-side synthetic tests pass under seeds 7 and 213; **not exact-head GitHub-hosted CI** |
| Android native | `fix/plan-builder-training-wave12-20261108` | `f1a0a6ce735f3f62b9a45e8a9253e7ded03da921` | GitHub Actions [#42486561531](https://github.com/Isshhin/stridelab-development/actions/runs/42486561531) unit/build and [#42486561683](https://github.com/Isshhin/stridelab-development/actions/runs/42486561683) targeted emulator workflow both SUCCESS at this SHA, each on the owner's Windows self-hosted runner; **not physical-device or all-scenario acceptance** |
| Pre-QA specifications | `research/qa-readiness-batches34-20261108` | `41bef351240a0eaa109a260949529d714d981f61` | Batch 1–4 written specifications and 56-case QA matrix, NOT_RUN |
| Independent P2-F design | Frozen PR #34 | `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e` | OPEN/DRAFT/UNMERGED. Latest bounded review permits a separate **preflight authorization review**, not collector execution / Gate 2 |

**STOP on SHA drift:** query current remote branch head and local branch worktree before checkout/execution. If either differs, save the original as-is, compare commits and update test plan; do not silently switch the owner's active worktree or run destructive cleanup. Preserve all local branches, commits, stashes and untracked files. Never call a documentation-only descendant SHA newly CI validated.

## 1. Run ownership and workspace boundaries

- **Ordinary ChatGPT / connected GitHub:** pin refs, prepare QA scripts/docs/reports, inspect published logs, review test results and record evidence. No claim of device execution without actual logs.
- **Codex (default):** scoped source edits only, exact file boundaries, fail-first tests may be authored but **not executed**, explicit report of unrun tests; follow `AGENTS.md`. Do not run Gradle or touch the user's PC unless the owner explicitly overrides the standing rule.
- **Owner Android Studio:** builds, tests, isolated emulator and device visual approval, with one reused existing workspace; do not construct per-screen copies. A separately authorized Work/test executor may run checks in a **separate permitted host** with verified scope and no private credentials; it cannot assume access to the owner's Windows PC.
- **GitHub-hosted CI:** P2-X uses hosted Ubuntu only when runner allocation is available and usage/billing approval is respected. Current research workflow is branch-specific and only lists eight suites, **omitting newer Wave 3 and 4 tests**. Do not rely on that historical workflow as the complete 481-case suite or force-run on a different branch.
- **P2-F authority:** separately scoped reviewer and designated human steward; **do not run preflight collector, TPM/TBS, operational key provisioning, private-data import or Gate 2 operations**.

## 2. Phase A: 15-minute *logical* preflight checklist (no promised duration)

1. Read `AGENTS.md`, Batch 1–4 docs and this playbook; check active checkout branch, `git status --porcelain`, exact HEAD, remotes and shared worktrees. A dirty tree is **not** to be reset or discarded.
2. Verify P2-X target SHA, Android target SHA and separate PR #34 state against table above; compare latest remote if changed. No automatic merges/cherry-picks.
3. Confirm execution role: default owner-run Android Studio for UI. If Work/Codex is asked to build/test, record explicit owner exception and exact sandbox environment; otherwise prepare commands only.
4. Verify JDK 17, Gradle wrapper 8.13, Android SDK platform 36 / build tools 35.0.0 and isolated API-35 emulator (only if authorized). Check available storage, emulator serial and existing active jobs. Do not terminate someone else's emulator.
5. Perform read-only installed package, version and certificate comparison using the repository's `native-android/tools/check_apk_update.py` before **any** data-bearing-device upgrade. If not compatible, stop the update and use a disposable emulator/profile. Never uninstall or clear data as a workaround.
6. Prepare an evidence ledger with rows prepopulated for **E01–E22, A01–A28 and P01–P06**; all statuses start **NOT_RUN**.

## 3. Phase B: Engine 2.x isolated synthetic regression (execution only upon authorization)

Clean checkout pinned to `6142d7a8f8976f9ea4647850ff9e6ea51ff289df`; keep it separate from the owner's permanent Android workspace. The historical ten-module command from Batch 2, from repo root on Linux, is:

```bash
PYTHONPATH=research/validation PYTHONHASHSEED=7 python -m unittest -q \
  test_p2a_state test_p2a_comparison test_p2c_wave test_p2d_shadow \
  test_p2e_wave test_p2x_integration test_p2x_dev1 test_p2x_lineage \
  test_p2x_qual_roster test_p2x_qual_boundaries

PYTHONPATH=research/validation PYTHONHASHSEED=213 python -m unittest -q \
  test_p2a_state test_p2a_comparison test_p2c_wave test_p2d_shadow \
  test_p2e_wave test_p2x_integration test_p2x_dev1 test_p2x_lineage \
  test_p2x_qual_roster test_p2x_qual_boundaries
```

These are **planned commands**, not executed in this preparation. Preserve Python version/platform, checked-out exact commit and Git blob hashes, each test count, tracebacks and complete reproducible logs. Historical 481 PASS is a comparison only; no minimum result is preapproved if the suite changes. No threshold weakening, skipping failing cases, or rewriting source to match tests.

**GitHub CI blocker:** prior isolated hosted runs [#42476987211](https://github.com/Isshhin/stridelab-development/actions/runs/42476987211), [#42481627243](https://github.com/Isshhin/stridelab-development/actions/runs/42481627243) and [#42482594419](https://github.com/Isshhin/stridelab-development/actions/runs/42482594419) failed before running steps. First inspect run failure banner, GitHub account Actions usage/budgets, repository permissions/approval rules and any runner rejection annotations using authorized access; this evidence has not been available in ordinary chat. Root cause **UNKNOWN**; other Android Actions ran successfully. Do not assume a billing problem or create spending changes. If no GitHub-hosted job launches, mark **BLOCKED**, retain 481 hosted sandbox pass as separate author-side evidence, and do not reroute to the user's Windows runner.

## 4. Phase C: Android source/build/targeted emulator QA (owner-run unless explicitly excepted)

Review the actual branch HEAD, preserve the current checkout and uncommitted work. Source-scoped tests are already present and the inspected SHA provides the project `native-android/gradlew.bat`, Gradle 8.13 wrapper and instrumentation classes.

From **`native-android/`** in an authorized Windows/Android Studio test environment:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest --console=plain
# On exactly ONE authorized, isolated, already-running API 35 emulator:
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=io.stridelab.privatealpha.TracksideApprovedBuilderActivityUiTest,io.stridelab.privatealpha.TracksideActivityScrollTest,io.stridelab.privatealpha.GpsThumbnailCacheTest,io.stridelab.privatealpha.TodayThumbnailRequestsTest,io.stridelab.privatealpha.TracksideTodayTest,io.stridelab.privatealpha.TracksideTrainingCalendarUiTest,io.stridelab.privatealpha.ActivityIntelligenceUiTest' --console=plain
```

**These commands are for the future authorized owner/test executor, not instructions to automatically dispatch the Windows runner.** The historical GitHub workflows use `runs-on: [self-hosted, Windows, X64]` and a reserved isolated Pixel_9 emulator; don't dispatch either workflow merely because usage reset occurred. `assembleDebug` is a build, not an install or proof of live UI correctness.

Start P0 QA cases from Batch 4: A01, A03, A04, A06, A08, A09, A10, A12, A15, A16, A20, A23–A25, P04–P06. If any fails, stop only the affected gate and log a focused defect. Then P1 chart/map/Garmin/thumbnail/performance, followed by P2 visual polish. Test 360x800dp at 130% and 412x950dp at 150%, plus a controlled larger phone if available; capture environment for every screenshot.

**Important visible discrepancy:** inspected source `TracksideFloatingNavigation.kt` presents THREE primary tabs while the user's explicitly approved dock is FOUR (Today, Training, Activity, More). This is a known review item A10; don't silently change design or mark as accepted merely because navigation tests pass. The latest map/sheet code and long-run mitigations similarly need actual interaction and stress acceptance, not conclusions drawn from older docs.

## 5. Safe Garmin/FIT and offline acceptance

- Pure synthetic emulator FIT files and pre-approved non-private fixtures first. Only use real Garmin authentication or personal activities with a separate, explicit rights/consent decision; do not print, export or log tokens, FIT payloads or account data.
- FIT summary != validated detailed GPS. A source that has only a summary is **RECORDING_NOT_LOADED**, not proof that the athlete never recorded GPS. A true validated indoor record with no coordinates is **NO_GPS**.
- Large-record case: preserve entire measured source samples; UI/map may simplify display geometry but may not mutate raw points, pause timing, laps, distance, heart rate, cadence or elevation.
- Pause connectors are display-only between actual endpoints and never reconnect privacy cuts. Check cached thumbnails, offline rendering, full map, attribution and share/export privacy separately.
- Verify signed-out offline plan creation and history, optional cloud sign-in and insert-only restore, local account isolation, cache corruption, multiple activities/day and 40+ page loading. Never clear app database or wipe an existing owner profile.
- Use the read-only signer/version checker before an update; installed and candidate signers must match and version code must increase. Device installation is a separately approved step, **not** part of normal Codex default.

## 6. Phase D: result classification, stop rules and correction loop

A case must record: ID, priority, status, exact test HEAD, file blobs for research, Android APK identity/certificate when relevant, environment API/OS/emulator, test class or manual steps, expected vs actual, source of evidence, screenshot/log/artifact link, and a named next owner/action.

Valid outcomes:
- **PASS**: actual assertions executed at exact SHA and passed, with log/capture.
- **FAIL**: executed check deviated, with reproduction and trace. A P0 FAIL blocks that gate; do not change result to PASS due to unrelated suites.
- **BLOCKED**: no authorized runner, missing entitlement, incompatible signer, missing rights, missing emulator, absent reviewer; never equate BLOCKED with software failure or with PASS.
- **NOT_RUN**: intentionally omitted; reason/time recorded.
- **NEEDS_REVIEW**: approved design or test oracle conflicts with source; do not guess or silently reinterpret requirements.

Fail-first correction loop: preserve original failing test+evidence -> smallest authorized isolated code change -> focused passing test -> impacted suite -> cross-track regression -> replay exact merged candidate -> independent reviewer when required. Do not alter execution rules or source truth to manufacture green output. Never auto-merge P2-X into Android, or change PR #34, P2-F v7 authority, Android stored history, production signing or GitHub runner permissions.

## 7. Required output files / artifacts on Nov 12

1. **Machine-readable case ledger** with all 56 cases, exact statuses, reproduction and evidence URLs; row totals must sum to 56, no double counting.
2. **Engine test report** per SHA/Python version/hash seed, counted and failed cases, stored logs, CI runner state; independent reviewer status separately.
3. **Android QA report** with Gradle suite logs, API-35 emulator results, screenshot profiles, P0/P1/P2 findings, performance trace, offline/Garmin evidence and signer gate.
4. **Risk/gate decision:** software PASS/FAIL versus CI BLOCKED versus authority NOT_GRANTED. Gate 2 remains CLOSED unless independently opened through proper authority.
5. **Isolated fix register** linking defect -> exact correction branch/commit -> fail-first test -> retest, with explicit no-auto-merge state.
6. **New handoff** summarizing what remains, rather than claiming work will run asynchronously.

**Batch 5 verdict:** `B5_EXECUTION_PLAYBOOK_PREPARED_NOT_EXECUTED`.
