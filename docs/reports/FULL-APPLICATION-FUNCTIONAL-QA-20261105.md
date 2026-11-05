# StrideLab full-application functional QA — 2026-11-05

## Scope and baseline

- Previous QA integration baseline: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`.
- Approved Trackside source branch at audit start: `origin/design/trackside3-figma-parity-no-build-20261103` = `0b06bb894d791ee7c51f355f79d7c32316cb0e3a`.
- Follow-up audit baseline: `8dc606fec3209824fc02f3ba5196290be2b11705` on `fix/trackside-usability-garmin-20261105`.
- Final application/test correction head before this report: `a57bedd0a4bca7112308cd6f453056b925247af7`.
- The design head is an ancestor of the verified integration/fix head; no reset, force-push, merge, release build, signing change, or runner change was performed.
- Open related pull-request boundary: draft PR [#37](https://github.com/Isshhin/stridelab-development/pull/37), Engine 1.5 versioned foundation `ff9cfdd03a9fe7ff7ff1ef7b5ccc37d8c9296ce6` into `integration/native-pre-trackside3-verified-20261030`. The current QA branch has no open PR and nothing was merged.

## Exact-head Windows evidence

Final workflow: [run 41948579450](https://github.com/Isshhin/stridelab-development/actions/runs/41948579450), [job 126986801760](https://github.com/Isshhin/stridelab-development/actions/runs/41948579450/job/126986801760). It ran on `MARC` with labels `[self-hosted, Windows, X64]` from 2026-11-05 01:26:56Z to 01:38:17Z and concluded `success`.

Artifact `trackside-engine15-windows-evidence-8dc606fec3209824fc02f3ba5196290be2b11705` (ID `13379664916`, 37,007,651 bytes, expiry 2026-11-26) contains these unique result sets:

| Gate | Environment | Passed | Failed / errors / skipped |
|---|---|---:|---:|
| Python integrity/tooling | Windows, Python 3.12 | 31 | 0 |
| Pure-JVM regression | Windows, JDK 17 | 247 | 0 |
| Android Gradle unit | Windows, JDK 17 / Gradle 8.13 | 269 | 0 / 0 / 0 |
| Packaged Engine 1.0 rollback | Windows, Gradle | 1 | 0 |
| Integration smoke | API 35 Pixel 9 AVD | 84 | 0 / 0 / 0 |
| Responsive 412dp / 100% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 360dp / 100% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 360dp / 130% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 412dp / 150% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |

The XML artifact contains 450 unique Gradle test executions (269 + 1 + 84 + 96) and 220 responsive screenshots. The 247-test pure-JVM harness and 31 Python tests are recorded in the workflow log rather than those XML directories. A copied final 24-test result under `outputs/androidTest-results` is not counted twice.

Earlier implementation-head evidence is preserved in [run 41946839417](https://github.com/Isshhin/stridelab-development/actions/runs/41946839417). The preceding failed gate is [run 41946014635](https://github.com/Isshhin/stridelab-development/actions/runs/41946014635); it did not execute Android tests and is not passing evidence.

Engine-default correction run [41951434494](https://github.com/Isshhin/stridelab-development/actions/runs/41951434494) is also not passing evidence: 83/84 smoke tests passed and all four responsive suites passed, but `TracksideEngineBuilderIntegrationTest` still asserted the superseded Engine 1.5 default. The corrected test now requires Engine 1.0 prescriptions and passed 1/1 on a local API 35 Pixel 9 AVD before redispatch.

## Evidence boundaries

- The tests use synthetic accounts, fixtures, local SQLite/Keystore state and an emulator. They are not physical-phone acceptance.
- Garmin client/source tests use controlled transports and fixture records. `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`.
- Emulator screenshots establish rendered state and automated reachability, not real-hand ergonomics or complete visual correctness.
- Passing suites support the named behaviors; they do not prove every application interaction is correct.

## Implemented functionality and journey audit

The detailed component inventory is [docs/qa/feature-inventory.md](../qa/feature-inventory.md). The application implements authentication/session persistence; five root navigation destinations; Today; Training; the staged multi-discipline Plan Builder; Manage Plan; workout player/logging; Activity history/detail; Intervals.icu state/sync; FIT validation/cache helpers; Garmin Personal API foundations; Library/templates; backup/restore; and offline outbox/repository recovery.

TCX and GPX imports do not exist. FIT is not an end-to-end user-facing history importer. Garmin fetch results are not persisted. These are unimplemented boundaries, not passing journeys.

### Critical journey evidence

| Journey | Status | Evidence | Boundary |
|---|---|---|---|
| A. Open → authenticate → Today | PARTIAL PASS | Auth/session repository tests, `NativeNavigationTest`, `TracksideTodayTest` | Synthetic gateway/emulator; no hosted-auth or fresh-device phone run |
| B. Builder → configure → generate → review → approve | PASS (automated) | `TracksideEngineBuilderIntegrationTest`, builder UI tests, 24-case responsive suite on four profiles | Synthetic account/local database; no phone acceptance |
| C. Training → selected workout → edit/move → save/reopen | PASS (automated core) | calendar/manage navigation, movement validation, feedback repository offline reopen | Archive UI outcome is not directly asserted |
| D. Record/complete workout → history/persistence | PASS (automated) | `WorkoutPlayerTest`, `Engine1RepositoryTest`, reliability validation | Emulator and SQLite evidence only |
| E. Import → metrics → dedupe → match | PARTIAL | FIT decoder/cache, provider repository, matcher and manual-link tests | No end-to-end FIT UI/history commit; no TCX/GPX; Garmin not persisted; no live provider |
| F. Activity Detail map/sheet/scroll/Back | PASS (automated) | focused Activity sheet tests plus four responsive profiles | No real-hand gesture/phone acceptance |
| G. Restart → reopen plans/workouts/activities | PASS (automated) | repository recreation, navigation recreation, upgrade and cache tests | Process/device restart behavior is simulated by repository/activity recreation |
| H. Offline operations → reopen → integrity | PASS (automated) | repository, outbox, feedback and FIT cache recovery suites | Synthetic offline/failure injection |

### Interaction and outcome gaps

- Manage Plan archive has a confirmation implementation but no dedicated UI outcome assertion.
- Garmin Settings connect/disconnect UI is backed by client/source/vault tests, but not a live key or canonical-history import.
- FIT safe-intake helpers are not reachable through a production picker or manifest share target.
- Intervals.icu actions are covered with fixture gateways, not an authenticated real provider.
- Phone touch ergonomics, soft-keyboard variants and OEM Back behavior remain untested.

## Findings and readiness

- **P1 fixed, exact-head emulator verification pending:** the Android packaged default enabled Engine 1.5 (`strideEngine15Active` defaulted to `true`) contrary to the explicit Engine 1.0 production-default requirement. The new flag assertion failed against the old fallback and passed after changing only the fallback to `false`. Explicit research-build Engine 1.5 tests remain intact. Focused 56/56, Gradle unit 269/269, pure-JVM 247/247, tooling 31/31, and the focused API 35 builder/offline integration test pass locally.
- Existing open P2 limitations are Garmin history persistence, live Garmin credential verification, and current-branch physical-phone smoke acceptance.
- No newly reproduced data-loss, crash, or account-isolation P0 was found during source/test mapping.

### Confirmed defect accounting

| Classification | Count | Items |
|---|---:|---|
| Fixed application defects | 5 | Running occupied-day P1; Activity anchored sheet/Back P1; Activity tab semantics P2; outdated route assertions P2; Engine 1.0 packaged default P1 |
| Resolved QA gate defect | 1 | Protected-source manifest / pure-JVM boundary; not an application defect |
| Unresolved P0 | 0 | None known |
| Unresolved P1 | 0 | None known after exact-head verification succeeds |
| Unresolved P2 | 3 | Garmin history persistence; live Garmin credential verification; physical-phone smoke |
| Unresolved P3 | 0 | None recorded |

Critical-journey status: 6 automated PASS, 2 PARTIAL, 0 automated FAIL. “Partial” is used for hosted authentication and the incomplete provider/file-import journey; it is not upgraded from fixture evidence.

### Engine boundary

- The packaged fallback is Engine 1.0 (`ENGINE15_ACTIVE=false`).
- Engine 1.5 remains reachable only through an explicit build property for frozen research/integration verification; its existing matrices are unchanged.
- Rollback tests prove only new drafts change routing; saved Engine 1.5 snapshots and prescriptions decode unchanged.
- No Engine 2.x production or research source was modified by this audit.

### Garmin and import findings

- Garmin tests cover the official Personal API transport contract, token expiry/re-exchange, error mapping, activity normalization, dedupe classification, account isolation, disconnect and encrypted credential recreation.
- The tests use controlled transports. No live key, Garmin account, device, or athlete record was used.
- FIT bounds/CRC/profile decoding, original-file cache and safe URI helpers pass; the app does not expose a complete picker/share-to-history workflow.
- TCX and GPX are unimplemented.
- Intervals.icu state/sync is fixture-tested but not live-account verified.

### Offline and persistence findings

- Synthetic offline repository recreation passes for plans, workout drafts/logs, activity detail/history, wellness, feedback, locks, manual links, templates and outbox operations.
- Corrupt database/session/cache and interrupted write/ack paths fail closed or preserve the last good data in their named tests.
- Account-switch, wrong-owner refresh/restore/ack and Garmin vault boundaries have dedicated tests.
- These are automated SQLite/Keystore/failure-injection results, not proof against every device or OS failure mode.

### Physical-phone smoke checklist

Do not uninstall, clear data, change package identity, or replace signing. Compare package/version/signer read-only before installation.

1. Launch the existing app and verify the prior account remains private and available only after valid authentication.
2. Open each root tab; open a Today workout and Activity card; verify Back returns to the owning tab and prior list position.
3. Build an Engine 1.0 plan through running plus one secondary discipline; confirm occupied-day selection does not move another session; approve and reopen it offline.
4. Move one future workout, cancel another move, pause/cancel, lock/unlock, and reopen Training to verify only the intended item changed.
5. Record one valid set, leave/reopen the draft, reject an empty completion, then complete and reopen history.
6. Open Activity Details; drag between both anchors, scroll each tab, confirm the map remains behind the sheet, use the accessibility action, and verify first Back collapses.
7. At 360dp/130% and 412dp/150% equivalents, verify keyboard/forms, Continue/Save/Back actions and bottom navigation remain reachable.
8. Toggle airplane mode after a successful sync; reopen cached plans, logs and activities; create a supported local change; restore connectivity and verify no duplicate or cross-account data.
9. If an owner Personal API key is intentionally supplied, test Garmin connect, paging, expiry recovery, offline error, disconnect and restart without representing fetched pages as imported history.

## Verdict

**READY WITH DOCUMENTED LIMITATIONS**, contingent on the final exact-head MARC workflow passing. Automated evidence supports personal emulator testing and a cautious phone smoke pass. It does not establish live Garmin, end-to-end FIT/TCX/GPX import, hosted authentication, or complete application correctness.
