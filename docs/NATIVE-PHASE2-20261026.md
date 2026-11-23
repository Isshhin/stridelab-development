> **Native UI update:** [Reconstruction is implemented and CI-verified](NATIVE-UI-VERIFIED-20261026.md). Genuine-account acceptance described here is still pending; synthetic UI tests do not close it.

# Native Android Phase 2 — 2026-10-26

## Verified checkpoint and current acceptance

**Verified implementation:** `f23719eafae60749951df28e4676cc9605d1f721` on `feature/native-real-data-phase2-20261026`.
**Recovery branch:** `checkpoint/native-phase2-verified-f23719ea` (preserved, never overwritten).
**Passing Actions:** [40308952951](https://github.com/Isshhin/stridelab-development/actions/runs/40308952951).

The implementation is checkpointed and the supported tests pass. **Genuine-account and physical-device acceptance remain incomplete. This is not a validated daily-use application.** See [device-validation steps](NATIVE-PHASE2-DEVICE-VALIDATION.md).

| Executed verification | Actual result |
| --- | --- |
| Native/legacy APK builds and debug signature verification | Passed |
| JVM tests | 32 passed, 0 failures/errors/skips |
| Native API 35 emulator suite | 36 passed, 0 failures/errors/skips |
| Read-only APK-checker Python tests | 7 passed |
| Legacy seed before in-place emulator update | 1 passed |
| Separate backend/lifecycle probe executions | 4 passed: anonymous HTTPS/RLS boundary, synthetic seed, offline restart, reconnect/deduplication |
| APK metadata comparison | Built legacy version 2 → native version 4, same CI debug certificate; physicalUpgradeValidated=false |
| UI captures exported | 7 PNGs; inspected Home, Activities, Authentication, Settings, offline Home, running detail and strength detail |
| Accepted Phase 4E content integrity | Passed: original HTML/engine and 21 licensed assets retained |
| Real-account native sign-in/provider sync | Not executed; needs a secure athlete session |
| Physical device install/update | Not executed; no phone access or verified original signing key |

The successful run used real Android HTTPS to request anonymous activities (zero rows) and anonymous user lookup (401). Account-session, pagination, interruption, rotation and reconnect tests use a **fake gateway**, real isolated SQLite and Android Keystore. The process probe starts a normal native process, verifies its PID, force-stops it with networking disabled, reopens offline at 130% font size and checks preserved records/session. It then restores connectivity and refreshes through the fake gateway without duplicate records. Running and strength fixtures are explicitly synthetic and exist only in test sources/disposable emulators; no production records were written.

### Completed improvements and affected files

- `TrainingRepository.kt`: one renewal/retry on 401, owner checks before saving renewed sessions, correct reauthentication state for damaged sessions, serialization of concurrent refreshes, explicit empty-page exhaustion and stable timestamp/ID ordering, retained prior success marker on failures, source-status checks/cache, logout hides records before server revocation.
- `SupabaseGateway.kt`: injectable transport for contract tests; bounded HTTPS/no redirects/no caching; safe whitelisted errors; account-bound token parsing and escaped cursor identifiers. Fixed endpoint and publishable key retained; no privileged credentials introduced.
- `ActivityRecord.kt` / `IntervalConnection.kt`: distinct maximum HR, source labels, timezone-aware presentation, source metric preservation and discipline-aware pace; no invented values or recovery scores. Public integration metadata is kept separately from secrets.
- `NativeMainActivity.kt` / `ConnectivityMonitor.kt` / `StrideApplication.kt`: actual offline link state, real connection-check controls, source-specific sync status, keyboard/password controls, correct detail-tab selection, compact heading, large-font/narrow-screen metrics, readable dark-green component colors and clearer empty states. All five core native screens reviewed using exported PNGs; synthetic detail captures verified at font scale 1.3.
- Legacy `MainActivity.java`: framework pause ordering and detach-before-destroy cleanup; no storage clearing. Native UI remains Compose. Legacy HTML, backup format, assets and engine are preserved.
- Unit/instrumentation tests: metric/transport/error contracts, short server pages, timestamp ties, unauthorized/changed owners, retries, concurrent refresh, damaged sessions, cached connection state and prompt local logout.
- `process-probe-tests/`, `tools/validate_emulator.py`, Gradle and CI: emulator-only lifecycle orchestration, actual PID/force-stop and radio changes, larger fonts, frame/focus/destruction assertions, diagnostic capture, APK retention after tests and screenshot collection before shutdown. Helpers never enter the production APK.
- `tools/check_apk_update.py`: read-only package/version/all-signer comparison with seven tests; no install/uninstall/clear/sign operation. Debug candidate version is now 0.3.1-native-data, versionCode 4, same application ID.

### Durable artifacts and source integrity

[Private source/reports/screenshots artifact 12644227850](https://github.com/Isshhin/stridelab-development/actions/runs/40308952951/artifacts/12644227850) contains the source ZIP, companion archive checksum, per-file SHA256SUMS, JUnit reports, probe outputs, signing comparison and seven PNGs. The 91-file source archive was downloaded and every payload hash and working-source byte checked.

Archive SHA-256: `48100461cab13223404ff9a9e38e74efe2c8fb53aba1fd563108a912d7fa5dc2`.

Keep this tested source archive separate from later reporting-only commits. CI artifacts expire after 30 days; Git checkpoint branches preserve the implementation and `native-android/checkpoint.py` can regenerate archives. The earlier passing Phase 2 source at `a0d21c1388267173a6387cf08686659be62726c7` is also preserved as `checkpoint/native-phase2-data-a0d21c13`.

### Current blockers, required participation and next work

The workspace recovery and visual-review blockers are resolved. No local adb/physical Android device or genuine athlete session is available through the current tools. The GitHub connector does not expose repository secret metadata; original signing/test-credential availability is unknown, rather than assumed absent. No auth impersonation, production schema change or data write was used to work around these limits.

Before any phone update, keep the installed prototype, export **Back up my data**, and compare its actual installed signing identity with a compatible private candidate using the read-only checker. Do not uninstall, clear data or change application ID to work around a mismatch. Genuine-account sign-in must happen through a secure app/device flow, not by sending passwords or tokens in chat. Then verify actual owned activity history → source synchronization → close/reopen → offline reopen → reconnect, comparing metrics with the real source. Exact procedure is in NATIVE-PHASE2-DEVICE-VALIDATION.md.

Still incomplete: genuine native account/provider verification; original-phone signing/update checks; broader device/OS/performance/accessibility coverage; legacy-to-native record conversion/native data export; offline edit replay UI and tombstone-based remote deletion reconciliation. Native GPS/charts/intelligence/plans/library/logging/recovery expansion remains deferred until the real-data acceptance flow is verified. Advanced Engine 2.0 and web UI remain frozen for this milestone. No public APK/release or main merge occurred.

The earlier interrupted response did not lose implementation: branch and checkpoint were reread at f23719e and the successful run was reviewed without rerunning verified development/tests. Subsequent report commits alter documentation only. Resume from the remote Phase 2 branch; local materialized commit IDs are not canonical remote IDs.

---

The sections below preserve the implementation and failure-investigation history; the verified status above supersedes earlier pending statements.


## Recovery

Remote start: Isshhin/stridelab-development, feature/native-compose-migration-20261026, head 65e888383908d3117bad6e75fa9eab79bc262767. Code verified previously at 70e2e2f42c7090d4e90cc2a6a299ef36a388ff12, Actions 40289028829.

All 106 remote Git blobs restored byte-for-byte; staged Git tree exactly 1e3bd45fdcf420e6112a9e213d1ad5e9547ff416. Some old local documents/workflows had normalized line endings; restored original bytes, not just text. Local pre-recovery branch checkpoint/local-before-native-phase2-b1e22e1 retained. Local materialized commits differ from canonical remote SHAs; remote IDs are authoritative.

Verified source archive SHA-256 6e03afcc881de1095067a20f02f7b310408fa7958309d9737d99c8ccc2edc8b4 and all 81 payload manifest entries. Latest reporting commit intentionally updates the migration document and adds continuation notes.

Continuation branch: feature/native-real-data-phase2-20261026, based on remote 65e8883. No device or user database modified during recovery.

## Evidence correction

The prior UI tests captured images on the emulator, but the artifact contains no PNGs: collection ran after the emulator-runner action terminated the device. Prior statements that screenshots were saved in the downloadable artifact were incorrect. Collection now runs inside the emulator action, before shutdown, and requires home.png to exist. Baseline rerun will reproduce the 19 JVM/26 emulator/1 legacy seed gate and supply actual images for review.

## Acceptance

Real-account native sign-in, genuine activity refresh and offline process reopening remain unverified. Simulated network tests must remain explicitly distinguished. Preserve app ID, installed data, Keystore aliases, SQLite schema and legacy backup compatibility. Do not uninstall/clear data to work around signing differences. No production auth bypass or schema writes are authorized by this milestone.

## Baseline rerun

Actions 40300250236 rebuilt the restored code: 19 JVM and 26 emulator tests passed, plus the legacy seed. The overall run failed during PNG export, not the tests. Investigation identified AGP target cleanup after connected tests as another image-retention problem; leaveApksInstalledAfterRun is now enabled to preserve app data/evidence. Instrumentation tests are restricted to disposable emulators; do not run this suite against the personal installation.

## Reliability changes

- Refreshes a rejected access token once, saves rotation before retry, rejects any renewed owner mismatch. No retries on timeouts/5xx provider mutations.
- Continues history until an explicit empty page, validates descending timestamp/ID cursor order, keeps prior completion state on partial failure.
- Intervals status is fetched from the existing edge contract, cached without secret fields and shown with its check timestamp. Disconnected sources cannot trigger a sync.
- Added max HR, explicit provider labels, timezone checks and discipline-aware pace display. Distances and elapsed/moving time remain source values; missing metrics stay absent.
- Native link-state banner distinguishes offline cache viewing; sign-out hides account/cache before server revocation can block.
- Extra CI-only helper tests actual force-stop/reopen with disabled networking and larger font, then reconnects and checks deduplication through a fake gateway. A separate anonymous HTTPS/RLS-boundary phase checks the actual backend; neither is real-account authentication.
- Read-only APK update checker compares package/version/signers, never installs/uninstalls/clears/signs. Seven Python logic tests passed locally. Matching metadata is not physical upgrade validation.

Intermediate CI: 40302186251 built successfully but hit an input-dispatch ANR during the unchanged legacy WebView seed startup; native emulator tests did not start. 40302823522 built and passed the expanded JVM gate but helper compilation failed because Espresso was not on the compile classpath; fixed using Android Instrumentation's Back event. New orchestration captures emulator logs/screens before shutdown on success or failure. These runs are not recorded as full passes.

## Reviewed native captures

Downloaded the successful run 40303916247 artifact and inspected Home, Activities, Settings, Authentication, offline Home and activity detail at font scale 1.3. Found crowded detail columns at large font, overly large detail title, lost Activities selection on detail routes, repetitive signed-out copy and inconsistent default purple accents. Implemented single-column metrics at larger font/narrow width, compact detail titles, Activities selection for details, clearer average-pace naming, account-neutral signed-out sync status, correct singular activity count and coordinated dark green component colors. Missing values remain explicit.

Run 40303916247 passed 32 JVM, 34 Android, 7 Python, 1 legacy seed and 4 backend/lifecycle executions. Real Android HTTPS anonymous API/RLS checks passed. Tests using account sessions still use a fake gateway. Actual built APK metadata checks passed version 2 → 4 with the same CI debug certificate; this is not evidence about the user's phone certificate.

Source 32fdfc4 added concurrent refresh and damaged-session tests but run 40304313579 hit another legacy seed ANR before native tests. Captured logs show the legacy fixture closing its window during first-frame rendering. Both legacy tests now require a committed frame and window focus after DOM readiness before teardown. This strengthens readiness checks without changing the preserved WebView code or disabling ANR detection. The offline probe now starts the native app and verifies a running PID before force-stop, making unexpected termination explicit.

Latest readiness attempt: 40306641753 passed the legacy seed, but NativeUpgradeTest then hit an input-dispatch timeout during teardown after only four native tests; the run failed and the remaining 32 tests were not executed. Inspection found the host destroying an attached WebView, contrary to Android's documented lifecycle requirement. The Java host now removes the view from its parent before destruction, preserves storage, and emits non-sensitive lifecycle diagnostics. Legacy tests finish the activity directly, assert DESTROYED, and retain frame/focus checks, rather than using an implicit EmptyActivity transition. No ANR timeouts or test assertions were disabled. Reference: https://developer.android.com/reference/android/webkit/WebView#destroy().
