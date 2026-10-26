# Native Android migration — 2026-10-26

This directive supersedes the hybrid UI recommendation in ANDROID-FIRST-ARCHITECTURE.md. The web frontend is frozen. The native app uses Kotlin/Compose; the preserved WebView is an explicit legacy-data entry point, never its core renderer.

## Recovery references

- Accepted Android storage/session foundation: `feature/android-first-foundation-20261026`, remote `0af307f326f2348ad8b2f15db7173ff9cdb95cae`. Private CI run 40282323341 succeeded (actual build + emulator gate).
- Native branch: `feature/native-compose-migration-20261026`, based on that exact commit.
- Engine 2.0 remains on `feature/unified-coaching-engine-2-20261026`, remote `108806e87dc8c2a3f57e2ed2e25bc499f1cb1a6e`.
- Web maintenance checkpoint in Isshhin/stride-lab: `checkpoint/mobile-web-before-android-20261026`, remote `66050942298819041db8de4db96162cdf10d8967`.

## Implemented migration slice (verification tracked separately)

`native-android` is an independent Gradle project. Kotlin 2.2.21, AGP 8.11.2, Gradle 8.13, SDK 36/target 35/min 26, Compose BOM 2025.10.00, Navigation Compose, lifecycle state collection, ViewModel, coroutines, Material 3 and WorkManager. Dependencies are pinned, not dynamically resolved.

- Compose launcher, dark theme, Home / Activities / detail / Settings / email-password authentication navigation. Empty states show no fabricated history or wellness scores.
- Fixed HTTPS Supabase client, authenticated owner filter, server verification of login, refresh rotation saved in Keystore vault before use. No privileged credentials. Public project configuration relies on Supabase RLS.
- Real history fetch uses descending timestamp + ID keyset pagination. Every validated page is committed atomically to existing SQLite; no deletion on partial failure. Invalid records stop import and preserve existing cache. No synthetic revision token is assigned to versionless server activities.
- Cache survives restart; account-scoped reads; logout hides cache and removes local session. Offline logout reports unavailable server revocation. Passwords are never saved in instance state.
- Manual existing Intervals sync action followed by history refresh. Hourly network-constrained WorkManager refreshes only the Supabase cache; existing backend cron retains provider-ingestion ownership.
- Local SQLite, SessionVault, accepted HTML/assets, legacy backups and original MainActivity are reused from their original paths. No destructive migration, schema change, remote write, legacy storage deletion, main merge or public release.

## Data / signing safeguards

Identity remains `io.stridelab.privatealpha`, version code 3 (prototype code 2). This only preserves upgrade data **if the signing certificate matches the installed APK**. Debug CI certificates are ephemeral. A stable original signing key and install/update test are required before personal distribution. Release signing accepts environment variables only; no signing secrets are committed.

SQLite schema/file and Keystore aliases are unchanged. Room is deferred: the existing transactional SQLite foundation is a compatibility layer, not a second database or destructive conversion. Native settings opens the unchanged prototype and its SAF backup/restore flow. No automatic conversion of `afterglow2` has happened. Native and legacy records are deliberately not merged until a validated, duplicate-safe import exists. Preserve backups before device upgrade; do not uninstall the old app.

## Verification and remaining work

The native build and new tests must pass before this slice is described as verified. See private `Native Android migration verification` workflow and continuation notes for actual run IDs and failures. New tests cover metric absence/units/ownership and native navigation/password state; inherited tests cover SQLite rollback, conflicts and Keystore tampering.

No authenticated real-athlete session or physical Android device is accessible to this development environment. Do not claim genuine-account sign-in, provider sync, history completeness, installation/update compatibility or daily-use readiness based on anonymous queries, fakes or a successful build.

Remaining: robust token refresh recovery after interrupted rotation; session expiry/revocation scenarios; integration connection/backfill UI; FIT import; samples/GPS/charts; training plans/calendar/workout execution; wellness; accessibility and performance profiling; legacy import/rollback tests; stable private signing/update pipeline; device tests. OAuth/MFA/password reset are not implemented in native auth. Existing backend remains intact.

Next milestone: validate the real-account vertical slice, add fake-gateway repository/session regression tests, then native GPS/detail ingestion and duplicate-safe legacy backup import. Do not expand Engine 2.0 or web UI ahead of this.

## Continue

Use JDK 17 and Gradle 8.13 with Android SDK 36/build-tools 35.0.0. From `native-android`: `gradle :app:testDebugUnitTest :app:assembleDebug`; with an API 35 emulator: `gradle :app:connectedDebugAndroidTest`. The SDK is unavailable locally; private CI performs actual Android execution. `python3 native-android/checkpoint.py` generates a tracked-source ZIP, internal SHA256SUMS and outer SHA-256. Restore into a separate directory and verify every manifest entry before building.
