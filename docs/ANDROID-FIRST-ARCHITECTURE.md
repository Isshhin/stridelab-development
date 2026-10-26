# Android-first implementation baseline

26 October 2026. The user's Android-first directive supersedes Engine 2.0 and web/mobile-web UI work. The web frontend is frozen except essential maintenance. Shared backend correctness remains active. No full rewrite is authorized or underway.

## Source and preserved checkpoints

- Android source is now in `android-app/android`, imported from StrideLab-Phase4E-Final-candidate.zip (archive SHA256 d1d061d8be9d9dd752a71a40867e2bc00f0fb15b97d88032c0b95f45e4e1c3b3). The known Fix1 explicit Gradle version assignments are applied, with developer foundation versionCode 2. Its accepted HTML and all 21 assets remain byte-identical to the accepted Phase 4E source. Provenance verification was executed successfully.
- Stable application ID remains io.stridelab.privatealpha. The appassets origin and afterglow2 key remain unchanged. Existing state is not migrated or deleted.
- Engine source: Isshhin/stridelab-development, feature/unified-coaching-engine-2-20261026, 108806e87dc8c2a3f57e2ed2e25bc499f1cb1a6e. Paused, not integrated into Android.
- Real-data web maintenance: Isshhin/stride-lab, feature/daily-use-reliability-20261026, 6380ca69521380b75c1558b6d6de0b971e0c96db. Useful API, FIT parsing, provenance and missing-data fixes are preserved.
- Subsequent mobile-web/PWA work: Isshhin/stride-lab, checkpoint/mobile-web-before-android-20261026, 66050942298819041db8de4db96162cdf10d8967. Not promoted into Android. That branch records unresolved gesture/scroll checks and the failed cold-navigation CI gate. Do not continue its UI work.

## Architecture decision: retain hybrid, add native services incrementally

The existing Java Activity and AndroidX WebViewAssetLoader provide an isolated packaged UI, verified local origin, lifecycle/back handling, local assets and manual SAF backup. Those are reusable. It currently loads the offline demonstration application; it is not the Next.js/Supabase app and is not suitable for real daily training yet.

Keep this shell. Reuse exercise content, licensed images, fitness data contracts and deterministic TypeScript/JavaScript business logic where appropriate. Add Android-owned persistence, secure authentication, integration transport, background work and device features behind a narrow origin-restricted boundary. Native capabilities do not require replacing the entire UI stack.

Alternatives assessed: loading the remote website alone fails offline requirements; a wholesale Compose/native or Capacitor replatform would replace working navigation/content and require significant migration and approval. Neither is necessary to establish a reliable native data layer. A later UI migration must be supported by measured limitations, a migration plan and user approval.

## Implemented storage foundation (not a completed user workflow)

`TrainingStore` uses Android SQLiteOpenHelper, WAL and transactions. It stores account-scoped activity, wellness, plan, workout and exercise JSON without changing provenance, units or missing values. Native records live in a separate database; old WebView records are untouched.

- Cached records and remote pagination cursors survive process/database closure.
- Offline edits and replay intents commit in one transaction. A database error rolls both back.
- Each operation has a unique id. Duplicate enqueue is idempotent; changing an operation's intended payload is rejected.
- Repeated offline edits form a dependency chain. Acknowledging an earlier edit preserves later edits and advances their server base version.
- Remote pages validate completely before committing. Pending local edits are never overwritten by a refresh.
- Divergent remote versions create durable conflicts and block automatic replay. Explicitly approved merges retain both versions in a recovery audit before queuing a new operation.
- Account identifiers are mandatory on reads and mutations. No implicit current-user global is trusted by the store.
- A record lacking a server version cannot be blindly updated offline. Backend adapters must provide a conditional-write version before replay is enabled.

These classes are not yet wired into the prototype screens, authentication, a real network sync worker, Health Connect or native backup/export. No UI, automatic synchronization or offline workout logging is claimed complete merely because a storage API exists. Current app manifest still has no network permission; no privileged bridge or credential storage has been introduced.

## Required next implementation order

1. Execute unit and Android emulator storage/lifecycle regression; preserve exact source hashes and test reports. Local container has JDK 17 but no Android SDK/emulator, so Android execution uses private CI.
2. Native account session handling with Android Keystore-protected tokens, real Supabase authentication and explicit account switching. Never expose service-role keys or put refresh tokens in browser storage.
3. Read-only real history/wellness synchronization and paged backfill into SQLite, with provenance and a visible sync state. Test process termination and refresh failures. Verify actual user login when an authenticated session is available; administrative database access is not a user login.
4. Connect Android screens to native data through an origin-restricted, bounded message API; remove normal-path demonstration data. Preserve rich exercise detail, automatic valid GPS and meaningful run review. Avoid arbitrary JavaScript interfaces and remote WebView script access to tokens.
5. Offline plan/workout workflows, native backup/restore including the new database, conditional/idempotent backend replay endpoints and conflict resolution UI. Only then enable actual background uploads. Add WorkManager for bounded network-constrained work; do not assume the current browser-only Garmin transport runs in the background on Android.
6. Health Connect and notifications through native APIs when required. These are compatible with a hybrid UI and still require permissions/device testing.
7. Stable private signing, reproducible APK, same-certificate upgrades, physical installation/reopen/update tests and a private updater that does not embed a privileged GitHub token.

Existing signing secrets are not available through this workspace. Do not generate a disposable key and describe it as a durable update solution. No public APK release, automatic merge or production deployment is authorized by this checkpoint.

## Technical references reviewed

- Android's [offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first): persistent local data and queued writes, with explicit synchronization/conflict decisions.
- [SQLiteOpenHelper](https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper) and [SQLite WAL](https://www.sqlite.org/wal.html): lifecycle-managed database, transactions and concurrent-reader behavior. SQLiteOpenHelper fits the existing Java project without adding a framework migration; a typed repository can later use Room if justified.
- Android [WebView native bridge security](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges) and [native API access](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge): restrict origins and avoid broad privileged JavaScript interfaces.
- Android [persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent): WorkManager is a future integration path, not an implemented capability in this checkpoint.

The application remains **not fully validated for daily personal use** until authentication, real-data sync, restart/offline persistence, plan/logging/navigation, conflict replay and installation/updates pass their respective gates. Build success and emulator evidence must remain distinct from physical-device acceptance.
