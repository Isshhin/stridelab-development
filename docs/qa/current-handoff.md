# Current QA handoff — 2026-11-05

This file is updated at task completion. Entries marked pending are not verified claims.

## Repository

- Starting integration commit: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`
- Working branch: `fix/trackside-usability-garmin-20261105`
- Current committed checkpoint: `12c75faf31be6e1382d644485585d894b95ec60d`
- Final commit: pending

## Completed evidence

- Running occupied-day selection and Activity sheet corrections committed at `12c75fa`.
- `TracksideApprovedBuilderActivityUiTest`: 16/16 passed at `12c75fa`.
- JVM suite: 262/262 passed at `12c75fa`.
- Responsive profiles: 96/96 passed with 220 screenshots at `12c75fa` (412/1.0, 360/1.0, 360/1.3, 412/1.5).
- Garmin client/source JVM tests: 7/7 passed on the current uncommitted tree.
- Garmin encrypted-vault instrumentation: 1/1 passed on the current uncommitted tree.

## Work in progress

- Current navigation/visual/legacy regression assertions are being aligned with the authoritative Trackside flow without ignored tests.
- Garmin foundation is implemented; live key verification and main-history persistence remain open.
- Exact-head `MARC` workflow and final broad regression are pending.

## Garmin status

- Uses only the official Garmin Connect Personal API base URL, token exchange, and activity search contract.
- Personal key and bearer token are account-scoped and encrypted with Android Keystore/AES-GCM.
- Token expiry/re-exchange, error mapping, provenance, explicit dedupe, disconnect, restart, offline, and account isolation have automated coverage.
- Engine 1.5 receives no new hidden Garmin coaching inputs.
- `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`.
- Official references: <https://developer.garmin.com/gc-developer-program/personal/personal-api/>, <https://developer.garmin.com/gc-developer-program/personal/personal-faq>, <https://developer.garmin.com/gc-developer-program/personal/swagger-docs/>, <https://github.com/garmin/connect-personal-api-python>.

## Next verification

1. Finish and pass `Engine1UiTest`, navigation, visual, and full instrumentation.
2. Run all JVM tests after the Garmin changes.
3. Rerun the four responsive profiles because Settings content changed.
4. Commit, push, and dispatch the serialized Windows Android workflow on `MARC` at the exact SHA.
5. Update counts, URLs, unresolved gaps, and final verdict here.
