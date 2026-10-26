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
