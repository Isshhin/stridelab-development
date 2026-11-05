# StrideLab full-application functional QA — 2026-11-05

## Scope and baseline

- Previous QA integration baseline: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`.
- Approved Trackside source branch at audit start: `origin/design/trackside3-figma-parity-no-build-20261103` = `0b06bb894d791ee7c51f355f79d7c32316cb0e3a`.
- Follow-up audit baseline and final exact-head evidence target: `8dc606fec3209824fc02f3ba5196290be2b11705` on `fix/trackside-usability-garmin-20261105`.
- The design head is an ancestor of the verified integration/fix head; no reset, force-push, merge, release build, signing change, or runner change was performed.
- Open related pull-request boundary: draft PR [#37](https://github.com/Isshhin/stridelab-development/pull/37), Engine 1.5 versioned foundation `ff9cfdd03a9fe7ff7ff1ef7b5ccc37d8c9296ce6` into `integration/native-pre-trackside3-verified-20261030`. The current QA branch has no open PR and nothing was merged.

## Exact-head Windows evidence

Final workflow: [run 41948579450](https://github.com/Isshhin/stridelab-development/actions/runs/41948579450), [job 126986801760](https://github.com/Isshhin/stridelab-development/actions/runs/41948579450/job/126986801760). It ran on `MARC` with labels `[self-hosted, Windows, X64]` from 2026-11-05 01:26:56Z to 01:38:17Z and concluded `success`.

Artifact `trackside-engine15-windows-evidence-8dc606fec3209824fc02f3ba5196290be2b11705` (ID `13379664916`, 37,007,651 bytes, expiry 2026-11-26) contains these unique result sets:

| Gate | Environment | Passed | Failed / errors / skipped |
|---|---|---:|---:|
| Python integrity/tooling | Windows, Python 3.12 | 31 | 0 |
| Pure-JVM regression | Windows, JDK 17 | 247 | 0 |
| Android Gradle unit | Windows, JDK 17 / Gradle 8.13 | 269 | 0 / 0 / 0 |
| Packaged Engine 1.0 rollback | Windows, Gradle | 1 | 0 |
| Integration smoke | API 35 Pixel 9 AVD | 84 | 0 / 0 / 0 |
| Responsive 412dp / 100% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 360dp / 100% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 360dp / 130% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |
| Responsive 412dp / 150% | API 35 Pixel 9 AVD | 24 | 0 / 0 / 0 |

The XML artifact contains 450 unique Gradle test executions (269 + 1 + 84 + 96) and 220 responsive screenshots. The 247-test pure-JVM harness and 31 Python tests are recorded in the workflow log rather than those XML directories. A copied final 24-test result under `outputs/androidTest-results` is not counted twice.

Earlier implementation-head evidence is preserved in [run 41946839417](https://github.com/Isshhin/stridelab-development/actions/runs/41946839417). The preceding failed gate is [run 41946014635](https://github.com/Isshhin/stridelab-development/actions/runs/41946014635); it did not execute Android tests and is not passing evidence.

## Evidence boundaries

- The tests use synthetic accounts, fixtures, local SQLite/Keystore state and an emulator. They are not physical-phone acceptance.
- Garmin client/source tests use controlled transports and fixture records. `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`.
- Emulator screenshots establish rendered state and automated reachability, not real-hand ergonomics or complete visual correctness.
- Passing suites support the named behaviors; they do not prove every application interaction is correct.

## Implemented functionality and journey audit

Task 2 expands this section from production routes and tests. Unimplemented behavior is kept separate from defects.

## Findings and readiness

Pending the journey/coverage map. No unresolved P0/P1 claim will be made until that review is complete.
