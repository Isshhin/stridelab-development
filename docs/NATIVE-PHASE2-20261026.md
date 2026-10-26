# Native Android Phase 2 — 2026-10-26

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
