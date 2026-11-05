# Current QA handoff — 2026-11-05

Read [README.md](README.md) before continuing this work. Verification below applies only to the stated commits; it is not phone or live-Garmin acceptance.

## Repository

- Starting integration commit: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`
- Working branch: `fix/trackside-usability-garmin-20261105`
- Trackside interaction fix: `12c75faf31be6e1382d644485585d894b95ec60d`
- Garmin/QA foundation: `d0d6224efd8b3743f4875960700dd44579acd371`
- Exact application/workflow source built and verified on Windows: `261faba877de278c82da0410a976a66ff6e29834`.
- The documentation commit following this evidence does not change executable Android source.

## Exact-head Windows evidence

- Final successful workflow: <https://github.com/Isshhin/stridelab-development/actions/runs/41959405715>
- Final successful job: <https://github.com/Isshhin/stridelab-development/actions/runs/41959405715/job/127021914384>
- Runner: `MARC`; verified labels `[self-hosted, Windows, X64]`.
- Duration: 2026-11-05 03:05:49Z–03:17:00Z; conclusion `success`.
- Artifact: `trackside-engine15-windows-evidence-261faba877de278c82da0410a976a66ff6e29834`, artifact ID `13385196790`, 37,048,750 bytes, retained through 2026-11-26.
- Pure-JVM regression: 247/247 passed.
- Android Gradle unit suite: 269/269 passed.
- Packaged Engine 1.0 default-builder rollback: 1/1 passed.
- API 35 integration-smoke instrumentation: 85/85 passed, including the Garmin credential-vault regression.
- Responsive instrumentation: 96/96 passed across four serialized profiles; 220 screenshots produced (55 per profile).
- Tooling/schema checks: catalog validator and 31/31 Python tests passed in the exact-head workflow.

The first authorized dispatch at `d0d6224` failed before Android execution because five intentionally modified protected files still had old hashes. Run: <https://github.com/Isshhin/stridelab-development/actions/runs/41946014635>. The five hashes were reviewed and updated; the Garmin pure-JVM source boundary was also repaired. No runner labels, registration, workflow permissions, or security controls were weakened.

## Signed personal-QA APK

- Build workflow: <https://github.com/Isshhin/stridelab-development/actions/runs/41960649613>; MARC job: <https://github.com/Isshhin/stridelab-development/actions/runs/41960649613/job/127025952447>; conclusion `success`.
- Private artifact download: <https://github.com/Isshhin/stridelab-development/actions/runs/41960649613/artifacts/13385457304> (artifact ID `13385457304`, retained through 2026-11-26).
- APK: `StrideLab-Trackside-3.0-QA-261faba877de.apk`, 17,441,573 bytes.
- APK SHA-256: `374f9d9c94c1144a1a6c94edd523a925d127f4e2465c768e5e85346edf8a6176`.
- Application ID: `io.stridelab.privatealpha`; version code/name: `15` / `0.6.6-guided-workout`; min/target SDK: `26` / `35`; build type: `debug`.
- `apksigner verify --verbose --print-certs` passed with APK Signature Scheme v2. Certificate SHA-256: `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270`.
- Recorded version-14 signer: `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95`.
- Classification: `INCOMPATIBLE_WITH_RECORDED_VERSION_14_SIGNER`. Do not install this APK over the existing data-bearing StrideLab installation.
- The runner's already-existing debug keystore was required before Gradle ran. No key was generated, uploaded, committed, printed, or changed. Repository/environment durable-signing secrets and a signer pin were not configured.
- The APK build reran 31/31 Python, 247/247 pure-JVM and 269/269 Android unit tests after the complete exact-head workflow had passed.

### Safe phone handling

1. Keep the current StrideLab installation and its data intact. Do not uninstall it and do not clear storage/cache to resolve a signing error.
2. Do not attempt an in-place update with this APK while the recorded signer mismatch remains.
3. For personal QA now, install the APK only on a separate phone, isolated emulator, or genuinely separate Android user/work profile whose StrideLab storage is disposable and independent.
4. If Android reports an incompatible certificate or package conflict, cancel installation. That is the expected safe failure; do not remove the existing app.
5. A future in-place candidate requires the original compatible signing key and a version code greater than the installed build. Before installation, pull the actual installed base APK read-only and compare package, version and all signer digests with `native-android/tools/check_apk_update.py`.
6. No physical installation occurred in CI. Existing account data, plans, workouts and activities were therefore not modified by this build.

## Completed work

- Running occupied-day taps select the owning Long/Quality/Easy slot; only the explicit editor moves a selected slot.
- Activity Details uses real partial/expanded anchors, keeps the map behind the sheet, supports inner scrolling, publishes accessible expand/collapse and tab state, and collapses before route Back.
- Outdated navigation/visual assertions now target the current Trackside routes without ignored tests.
- Garmin Personal API foundation uses the official endpoint contract, account-scoped Android Keystore/AES-GCM storage, token lifecycle/error handling, provenance and explicit dedupe boundaries.
- Permanent QA navigation, defect history, regression index, environment and verification procedures live in `docs/qa/`.
- Full implemented-feature and critical-journey inventory is in `docs/reports/FULL-APPLICATION-FUNCTIONAL-QA-20261105.md`.
- Engine 1.0 is now the packaged default; Engine 1.5 requires an explicit research/integration build property.

## Unresolved work and blockers

- Garmin activity pages are not yet persisted into `TrainingRepository`; the Settings UI states this limitation.
- `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`.
- Physical-phone smoke acceptance has not been rerun for this branch; emulator results are not phone acceptance.
- A private debug-signed QA APK was built and verified, but its signer differs from the recorded version-14 signer. No production release, public artifact, phone installation, uninstall, or data clear occurred.

## Recommended next verification

1. Recover the original version-14-compatible signing path or perform a live read-only installed-APK comparison before any in-place phone update.
2. Until then, run the smoke checklist in [verification-playbook.md](verification-playbook.md) only on a separate device/profile/emulator without uninstalling or clearing the existing app.
3. With an owner-provided Personal API key, verify live connect, token refresh, activity paging, offline recovery and disconnect using a non-production test account where possible.
4. Design and test an explicit Garmin-to-`TrainingRepository` persistence transaction before enabling history import.
5. After any subsequent source change, treat this run as historical evidence and select fresh tests from [regression-coverage.md](regression-coverage.md).
