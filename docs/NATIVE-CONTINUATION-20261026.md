> **Latest corrections:** [Navigation roots and silent sync-success feedback are implemented and verified](NATIVE-FEATURE-RECONSTRUCTION.md). Tested source 8c5dae4; CI 40326759340 passed (32 JVM / 45 emulator tests, legacy seed and four probes). Carry these required behaviors into the upcoming unified Native Feature Reconstruction. Existing checkpoints and real-account acceptance boundaries remain intact.

> **Latest milestone: Native UI/UX Reconstruction implemented and verified.** Tested source 092d1f6884cfac58271d11f6a07af28a436f708f; CI 40321549037 passed (32 JVM / 41 emulator tests, legacy seed and four probes). See [verified UI handoff](NATIVE-UI-VERIFIED-20261026.md). Real-account Phase 2 and physical-phone acceptance remain pending. Earlier sections below retain foundation history.

# Native Android continuation — 2026-10-26

> Current Phase 2 status: [NATIVE-PHASE2-20261026.md](NATIVE-PHASE2-20261026.md). Verified code f23719e, Actions 40308952951; 32 JVM / 36 emulator tests plus the documented probes. This file retains the earlier foundation history.

## Status

Native foundation and first authentication/history slice are implemented. This is **not a validated daily-use application**. Authentication and provider requests use the real Supabase contracts, but no genuine-athlete session has been available for end-to-end verification.

Canonical repository: Isshhin/stridelab-development.
Implementation branch: feature/native-compose-migration-20261026.
Tested implementation commit: 70e2e2f42c7090d4e90cc2a6a299ef36a388ff12.
The subsequent reporting commit changes documentation only.

## Implemented and reused

- Independent Kotlin/Compose Gradle project in native-android. Compose is the launcher and renders Home, Activities, activity details, Settings and email/password sign-in. Navigation Compose, ViewModel/StateFlow, Material 3, coroutines and WorkManager are wired.
- Native HTTPS Supabase client and existing Intervals sync action. Owner-filtered timestamp/ID keyset history paging; validated pages committed transactionally; no fixed history cap; source JSON/units retained; null metrics stay absent. Interrupted import retains committed pages and does not claim full refresh completion.
- Android Keystore encrypted sessions; server identity verification before activation; saved refresh rotation; password excluded from saved instance state; rejected account switching retains the existing session/cache.
- Account-scoped SQLite cache restored on reopen; network-constrained background cache refresh; offline read support. Existing outbox/conflict behavior is reused, but workout logging/replay UI is not implemented.
- Existing TrainingStore, SessionVault, NativeSession and BackupCodec reused through source sets. Shared TrainingStore now fails closed on corruption instead of silently deleting/recreating damaged files. No schema migration or destructive SQL.
- Original Phase 4E HTML, 21 licensed assets, protected engine, WebView origin and explicit backup/restore entry remain. Native Settings opens the preserved prototype. Core native screens do not render through WebView.
- Stable application ID io.stridelab.privatealpha; native version code 3. Signing secrets are environment-only. No APK distribution, public release or main merge.

## Changed modules

| Module | Purpose |
| --- | --- |
| native-android/app/src/main/java/io/stridelab/privatealpha | Compose UI, activity metrics, repository, Supabase gateway, application/ViewModel/worker |
| native-android/app/src/main/AndroidManifest.xml and Gradle files | Native launcher, permissions, pinned dependencies, shared source reuse, optional signing |
| native-android/app/src/test and src/androidTest | Metric contracts, repository/SQLite/Keystore integration, UI restoration, upgrade and corruption tests |
| native-android/legacy-upgrade-tests and legacy-upgrade.init.gradle | Explicit isolated legacy fixture for update verification; not packaged in production |
| android-app/android/app/src/main/java/io/stridelab/privatealpha/TrainingStore.java | Non-deleting corruption error handler; schema/file remain version 1 |
| .github/workflows/native-android.yml | Actual build, unit/emulator tests, same-key update sequence, private source/evidence archive |
| native-android/checkpoint.py | Reproducible source ZIP, payload SHA256SUMS and outer archive SHA-256 |
| docs/NATIVE-ANDROID-MIGRATION.md and this file | Architecture, results, blockers and recovery |

## Actual verification

- Tested source: [70e2e2f42c7090d4e90cc2a6a299ef36a388ff12](https://github.com/Isshhin/stridelab-development/commit/70e2e2f42c7090d4e90cc2a6a299ef36a388ff12).
- [Private CI run 40289028829](https://github.com/Isshhin/stridelab-development/actions/runs/40289028829): native APK assembled and signature verified; preserved legacy APK/test APK built; 19 JVM tests passed; 26 API 35 emulator tests passed, zero failures and zero skips; separate legacy seed test passed (1 test).
- Native emulator suite: 10 TrainingStore + 4 SessionVault + 8 repository + 2 Compose navigation/state + 1 APK upgrade + 1 corrupt-cache retention test.
- [Private source, reports and screenshots artifact](https://github.com/Isshhin/stridelab-development/actions/runs/40289028829/artifacts/12635436954). No APK is distributed in this artifact.
- Archived source: 81 tracked files; outer SHA-256 `6e03afcc881de1095067a20f02f7b310408fa7958309d9737d99c8ccc2edc8b4`. Archive contains payload `SHA256SUMS`; companion `StrideLab-native-source.zip.sha256` verifies the ZIP.
- Actual real-athlete native sessions tested: 0. Physical devices tested: 0. Screenshot capture succeeded; human/agent visual inspection remains blocked by the unavailable workspace.

Earlier successful gates:
- Preserved Android foundation 0af307f326f2348ad8b2f15db7173ff9cdb95cae: run 40282323341, 16 emulator tests passed.
- Native e4739b35bf45b93d2cfe2e8fddcd332cfa99c54b: run 40287884886, 19 JVM + 24 emulator tests, 0 failures/skips; 1 legacy seed test passed.
- Native 4b92094b6f2172298b991ee38d6010f9749fb769: run 40288393416, build passed, 25 emulator tests, 0 failures/skips; includes corruption retention. 1 legacy seed test passed.
- Local inherited JVM runner before workspace outage: 13 tests passed. Local accepted-content integrity check passed (21 assets and protected engine hashes unchanged). Checkpoint archive payload hashes verified and repeated local generation matched.

Initial CI attempts failed in the harness, not accepted as application passes: 40284302222 / 40284807005 ran out of emulator userdata disk; 40285281269 had an init-script path error; 40285945178 failed KVM permissions; 40286680699 confirmed shrinking the configured partition did not bypass the image minimum. The workflow now frees unused SDKs only on its disposable runner, checks available space and sets KVM access explicitly.

## Data migration / update evidence

No legacy-to-native record conversion has happened. Do not interpret an empty native cache as deletion of legacy records. Automatic conversion/import needs a duplicate-safe contract and additional tests.

The successful emulator upgrade tests install the legacy APK, seed isolated data, then install the higher-version native APK using the same ephemeral debug signing key. They verify exact afterglow2 storage, a separate origin marker, SQLite record/version/cursor, pending mutation, and decrypted Keystore session. This proves that tested same-key emulator update path only. It does **not** verify the certificate used by the APK on the user's phone.

Keep the original signing key and backups. Do not uninstall the installed app, clear data, delete old storage or change application ID to work around signing errors. A compatible signing path and actual device installation/update checks remain required.

## Preserved development

- Android foundation branch feature/android-first-foundation-20261026 at 0af307f326f2348ad8b2f15db7173ff9cdb95cae.
- Engine 2.0 branch feature/unified-coaching-engine-2-20261026 at 108806e87dc8c2a3f57e2ed2e25bc499f1cb1a6e.
- Isshhin/stride-lab web checkpoint checkpoint/mobile-web-before-android-20261026 at 66050942298819041db8de4db96162cdf10d8967. Web UI remains frozen.
- Supabase schemas, activity contracts and backend cron ownership remain unchanged.

## Blockers and incomplete behavior

- Development workspace became unavailable mid-session; final changes were made through the authenticated GitHub connector and executed in private CI. Resume from a fresh authenticated checkout of the remote branch; do not rely on a stale local snapshot. Screenshot artifacts exist, but visual inspection was blocked by the workspace outage.
- No real-account login, genuine history completeness or Intervals request has been verified from native UI. Use a secure sign-in flow on the test app; do not paste passwords or mint an impersonation token.
- Original phone signing identity/key and physical device access are unavailable for validation.
- No native FIT file import, samples/GPS/maps/charts/contextual analysis, Exercise Library, plans/calendar, workout execution/logging, wellness/recovery, source credential setup/backfill UI, MFA/OAuth/password reset or private update UI yet.
- SQLite outbox/conflict foundation is tested; supported user edits and server replay are not wired into native UI. No claim of complete offline modification synchronization.
- Cache does not infer server deletions from absent pages. Tombstones/reconciliation contract remains to be designed before deleting local data.
- Fixed metric display currently; raw source units retained. Accessibility, larger font/screen coverage, Back gestures, performance and crash/process-termination scenarios need broader verification.
- Interrupted refresh-token rotation/storage failure can require sign-in again; cached data remains.
- Native data backup/export and duplicate-safe legacy conversion are not implemented. The original legacy backup/restore tools remain available.

## Recovery and next milestone

Stable private recovery branch: [checkpoint/native-compose-verified-70e2e2f4](https://github.com/Isshhin/stridelab-development/tree/checkpoint/native-compose-verified-70e2e2f4), exactly at tested source 70e2e2f42c7090d4e90cc2a6a299ef36a388ff12. The active feature branch may contain later documentation only.

Download the source/evidence artifact above, verify the outer SHA-256 and every payload SHA256SUMS entry before restoring into a separate directory. Do not replace installed athlete data while restoring source. Recreate an archive from the tested Git commit using python3 native-android/checkpoint.py when needed. Git remains the durable source after CI artifacts expire (30-day retention).

Use JDK 17, Gradle 8.13 and Android SDK 36/build-tools 35.0.0. Check out the full repository (native-android reuses android-app sources/assets). From native-android: gradle :app:testDebugUnitTest :app:assembleDebug. Run the private native-android workflow for the complete API 35 emulator and legacy-upgrade sequence. A standalone connectedDebugAndroidTest invocation intentionally skips the upgrade assertion unless the legacy seed is installed and expectLegacyUpgrade=true is supplied; do not count that skip as migration verification.

Next: restore the development workspace and validate the native sign-in → genuine history refresh → connected Intervals sync → offline reopen slice with a compatible privately signed test APK. Then implement accurate activity samples/GPS details and duplicate-safe legacy backup import, followed by native training workflows. Keep advanced Engine 2.0 and web UI work deferred.
