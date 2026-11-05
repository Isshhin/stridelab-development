# Current QA handoff — 2026-11-05

Read [README.md](README.md) before continuing this work. Verification below applies only to the stated commits; it is not phone or live-Garmin acceptance.

## Repository

- Starting integration commit: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`
- Working branch: `fix/trackside-usability-garmin-20261105`
- Trackside interaction fix: `12c75faf31be6e1382d644485585d894b95ec60d`
- Garmin/QA foundation: `d0d6224efd8b3743f4875960700dd44579acd371`
- Exact final branch head verified on Windows: `8dc606fec3209824fc02f3ba5196290be2b11705`
- Full-audit application/test correction head: `a57bedd0a4bca7112308cd6f453056b925247af7` (Engine 1.0 packaged default; final exact-head workflow pending at report commit time).

## Exact-head Windows evidence

- Final successful workflow: <https://github.com/Isshhin/stridelab-development/actions/runs/41948579450>
- Final successful job: <https://github.com/Isshhin/stridelab-development/actions/runs/41948579450/job/126986801760>
- Runner: `MARC`; verified labels `[self-hosted, Windows, X64]`.
- Duration: 2026-11-05 01:26:56Z–01:38:17Z; conclusion `success`.
- Artifact: `trackside-engine15-windows-evidence-8dc606fec3209824fc02f3ba5196290be2b11705`, artifact ID `13379664916`, 37,007,651 bytes, retained through 2026-11-26.
- Pure-JVM regression: 247/247 passed.
- Android Gradle unit suite: 269/269 passed.
- Packaged Engine 1.0 default-builder rollback: 1/1 passed.
- API 35 integration-smoke instrumentation: 84/84 passed.
- Responsive instrumentation: 96/96 passed across four serialized profiles; 220 screenshots produced (55 per profile).
- Tooling/schema checks: catalog validator passed and 31/31 Python tests passed locally before dispatch.

The first authorized dispatch at `d0d6224` failed before Android execution because five intentionally modified protected files still had old hashes. Run: <https://github.com/Isshhin/stridelab-development/actions/runs/41946014635>. The five hashes were reviewed and updated; the Garmin pure-JVM source boundary was also repaired. No runner labels, registration, workflow permissions, or security controls were weakened.

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
- No release APK was built, installed, signed, or published during this QA task.

## Recommended next verification

1. On a compatible physical phone, run the smoke checklist in [verification-playbook.md](verification-playbook.md) without uninstalling, clearing data, or changing signing.
2. With an owner-provided Personal API key, verify live connect, token refresh, activity paging, offline recovery and disconnect using a non-production test account where possible.
3. Design and test an explicit Garmin-to-`TrainingRepository` persistence transaction before enabling history import.
4. After any subsequent source change, treat this run as historical evidence and select fresh tests from [regression-coverage.md](regression-coverage.md).
