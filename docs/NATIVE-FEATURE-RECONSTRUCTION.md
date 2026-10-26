# Unified Native Feature Reconstruction — required behavior

Navigation and synchronization-feedback corrections are prerequisites for the upcoming unified native feature milestone. Carry these behaviors and their regression tests forward; do not restore the old tab-stack or persistent-success behavior while reconstructing additional features.

## Navigation

- Every primary bottom tab opens its root destination. Activities always opens the list, even after a detail was opened before switching away.
- Discard transient detail/auth entries before saving a tab stack. Keep Navigation Compose root saveState/restoreState and single-top navigation, including saved Activities list position.
- All activity entry points, including Home's latest activity, establish Activities as the parent before opening detail. Android Back returns to the list.
- Repeated tab selection/switching must not accumulate duplicate roots. Normalize old restored stacks to their selected root.

## Synchronization feedback

- Shared successful refresh code must not publish a global success banner. This covers the background worker, sign-in history refresh, manual refresh and provider synchronization.
- Manual refresh updates the existing last-refreshed timestamp in place. A brief unobtrusive confirmation remains optional; no persistent success notice is required.
- Preserve actionable errors, interrupted/unsupported-data warnings, offline status, cached records and authentication recovery. Successful sign-out is quiet; failed remote revocation remains visible.
- Do not add generic debug-message controls to standard Settings. Developer diagnostics must remain outside the production UI.

## Required regression coverage

NativeTabRootTest exercises Activities → Detail → Home → Activities, retained list position, repeated primary-tab switching/reselection, and Android Back from both list-opened and Home-opened detail. TrainingRepositoryTest checks silent successful shared refresh/provider sync, cache retention and meaningful failure messages, then silent successful recovery. Existing NativeVisualTest retains visible error/offline assertions, and the full CI keeps SQLite, Keystore, auth, same-key update and offline forced-process probes.

These are synthetic UI/repository tests and preservation checks, not genuine-athlete integration acceptance. Retain the current native design, application ID, gateway/schema contracts and all existing GitHub checkpoints. No main merge or public release is part of these corrections.

## Verified corrections checkpoint — 2026-10-26

Implemented source: 8c5dae4e013f81113246db4d7278b5aa4033753e. [Verified checkpoint](https://github.com/Isshhin/stridelab-development/tree/checkpoint/native-navigation-sync-verified-8c5dae4).

[Android CI 40326759340](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340) passed: 32 JVM tests, 45 emulator tests, zero failures/errors/skips; one legacy seed, all four lifecycle/network probes, and nine APK compatibility/packaging tests passed. Local targeted coverage passed 29 tests with zero failures/skips. Existing error/offline UI assertions remain in the suite.

[Private versionCode 6 / 0.4.1-native-navigation APK package](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340/artifacts/12653116281). APK SHA-256: 6480f2781fcdd497d63bdcdc2c380571d024f68b610a5f2c42833a3465057471. Certificate SHA-256: 20bc17e151abf55ec3c7ed0dffbfd7ef27e465e87da4daf9f43d4e71cd7e0c8d. Downloaded APK signature and checksum independently verified. The CI debug certificate differs from the previous test APK; phone in-place update compatibility is not established. Preserve the installed app and data; use an isolated emulator or an appropriately signed compatible build.

[Source and full CI evidence](https://github.com/Isshhin/stridelab-development/actions/runs/40326759340/artifacts/12653111253). Source ZIP SHA-256: d1285afbf535f422f50eedecaea1f1c232e005105cce745faaed7919ee3451bb. All 106 manifest payloads independently verified against the exact Git commit after download. Artifacts have 30-day retention; the source checkpoint remains in Git.

The existing phase-2 and native-UI checkpoints are unchanged. Gateway, worker, shared SQLite/Keystore/legacy code and design resources are unchanged. Authentication and real synchronization control flow remain unchanged; repository changes affect completion-message presentation only. No standard Settings debug toggle, main merge, public release, data clearing or uninstall was introduced. Genuine-athlete and physical-phone acceptance remain pending and separate from synthetic regression evidence.
