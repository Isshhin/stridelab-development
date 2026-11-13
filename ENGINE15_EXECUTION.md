# Engine 1.5 execution tracker

Branch: `codex/engine15-continuous-20261113`. Engine 2.x remains out of scope.

## Ordered milestones

1. **Wave 1 — Stabilization** — Engine 1.5 default for new drafts; explicit Engine 1.0 rollback; separate recent and optional target mileage; old request/snapshot compatibility; saved-plan and backup behavior. Acceptance: default/rollback integration tests, nullable target encode/decode with legacy fixture, repository/backup checks, full JVM suite and build.
2. **Wave 2 — Plan intelligence** — progression bounded by baseline and an explicitly supplied target, recovery weeks, long-run cap, weekly distribution, supported quality styles/frequency, missing-baseline and unrealistic-target cases. Acceptance: deterministic scenario tests and full JVM suite.
3. **Wave 3 — Adaptive coaching** — completed/partial/missed/difficult workout signals, recovery and mixed-discipline proposals, explanation accuracy, and explicit staged approval. Acceptance: preview/stage/accept/dismiss tests prove no plan mutation before approval and preserve account/revision integrity.
4. **Wave 4 — Android reliability** — offline plan persistence, activity links, owner isolation, backup/restore and legacy plan checks; then applicable instrumentation/emulator acceptance. Acceptance: repository/backup tests and emulator flow where available; never touch the owner's physical install.
5. **Wave 5 — Candidate readiness** — full JVM suite, debug build, critical defect review, supported instrumentation, limitations and private-testing handoff. Any unavailable device evidence keeps the candidate provisional.

## Current evidence and status

- Wave 1: **provisionally implemented; repository/instrumentation acceptance pending**. `dbff074a` makes Engine 1.5 the packaged default and retains explicit Engine 1.0 selection. `b165e751` introduced optional `targetWeeklyKm`; `ea4b7bba` preserves legacy staged mileage when absent and applies target-capped progression when explicitly supplied.
- Wave 2: progression, target caps, no-baseline behavior, recovery reductions and six explicit quality styles have deterministic tests. Two-quality UI thresholds match Engine 1.5's 40 km recent-mileage gate.
- Wave 3: preview/stage/accept/dismiss and mixed-discipline safety are covered. Partial high-effort logs now receive a specific partial-workout explanation; staging still preserves the original prescription pending explicit acceptance.
- Latest validation on JDK 21 / Gradle 8.13 / local Android SDK: `:app:testDebugUnitTest` **402 tests, 0 failures**; `:app:assembleDebug` **BUILD SUCCESSFUL**. Focused B7 matrix: 3 tests, 0 failures. Focused feedback/interference tests passed (17 tests).
- Not run: Android instrumentation, emulator (no device is currently listed by SDK `adb`), physical device, authenticated Garmin API, backup round-trip through Android UI. Do not claim device or private-testing acceptance.

## Exact next action

Commit the partial-workout feedback explanation (full 402-test suite and debug build pass), update this tracker/handoff and push. Next inspect repository, backup, account-isolation and Garmin-linkage tests; identify any genuine gaps and run JVM coverage. Check installed AVD definitions and use an isolated emulator if available; never install to or modify the owner's physical phone.
