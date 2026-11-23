# Private native APK checkpoint — 2026-10-26

Implementation branch: `feature/native-real-data-phase2-20261026`.
APK source commit: `f2079bf29a261221b5745d4263d36069b064f470`.
The verified application implementation from `f23719e` is unchanged; this checkpoint adds packaging, tests and installation guidance. Previous checkpoint branches remain untouched.

Successful run: https://github.com/Isshhin/stridelab-development/actions/runs/40313303719
Private APK artifact: https://github.com/Isshhin/stridelab-development/actions/runs/40313303719/artifacts/12646716119
Source/evidence artifact: https://github.com/Isshhin/stridelab-development/actions/runs/40313303719/artifacts/12646421819
Artifacts expire 2026-11-25; source remains in Git. No public release was created.

## Actual APK metadata

- File: `StrideLab-native-private-test.apk`
- Application ID: `io.stridelab.privatealpha`
- Version name/code: `0.3.1-native-data` / `4`
- APK SHA-256: `da8e288a902cc6535ab92340b5f3d08f6c5a47e35e2e2a47f4c3a8e68efb12af`
- Signing certificate SHA-256: `a312bb979b544bb26a62bdd2be9107e9100b897d70788bd4d7d013557a78ffdf`
- APK artifact ZIP SHA-256: `1d5ae75f464956a9c9b266bd5c6148bd4ef6bcd371a72bfb039a1c88af88bcba`
- Source archive SHA-256: `dd9024aafe041961ae8d17be2ee02c0293fa85500fd2e936a8681ce35255845c`

The downloaded artifact ZIP digest and enclosed APK digest were independently recomputed and matched. APK signature verification passed in CI. Metadata, checksum, warnings, read-only comparison tool and CI emulator comparison are included beside the APK.

## Executed verification

- 9 Python tooling tests passed (including packaging integrity, wrong identity rejection, and existing signer mismatch gate).
- 32 JVM tests passed; downloaded XML reports show zero failures/errors/skips.
- 36 API 35 emulator tests passed; downloaded XML reports show zero failures/errors/skips.
- 1 legacy seed test and 4 separate backend-boundary/lifecycle probe executions passed.
- Native and legacy builds, signature verification, same-job APK metadata comparison, emulator upgrade, offline reopening and reconnection checks succeeded.

These are automated/synthetic-account checks plus the real anonymous backend boundary. Genuine native account login and physical-device validation remain incomplete.

## Installed phone update: unknown, not approved as safe

This candidate is signed with an ephemeral CI debug key. CI's legacy/native certificate equality applies only to that job's disposable emulator. The actual installed phone certificate and version have not been read; no phone was accessed, updated, uninstalled or cleared. No database/schema/version/storage migration was introduced in this change.

Before a phone update, export and preserve the original legacy backup and run the included read-only `check_apk_update.py` against the installed APK. If certificates differ, keep the existing app: do not uninstall or clear its data. Obtain the original compatible signing path. Future CI debug builds can have different signing certificates and may not update this test APK. Use a separate emulator until compatibility is established.

Follow [device validation](NATIVE-PHASE2-DEVICE-VALIDATION.md) for comparison and subsequent real sign-in/sync/offline verification. A metadata pass is necessary but does not establish physical update/data-preservation success.
