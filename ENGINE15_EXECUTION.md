# Engine 1.5 execution tracker

Branch: `codex/engine15-continuous-20261113`. Engine 2.x remains out of scope.

## Ordered milestones

1. **Wave 1 — Stabilization** — Engine 1.5 default for new drafts; explicit Engine 1.0 rollback; separate recent and optional target mileage; old request/snapshot compatibility; saved-plan and backup behavior. Acceptance: default/rollback integration tests, nullable target encode/decode with legacy fixture, repository/backup checks, full JVM suite and build.
2. **Wave 2 — Plan intelligence** — progression bounded by baseline and an explicitly supplied target, recovery weeks, long-run cap, weekly distribution, supported quality styles/frequency, missing-baseline and unrealistic-target cases. Acceptance: deterministic scenario tests and full JVM suite.
3. **Wave 3 — Adaptive coaching** — completed/partial/missed/difficult workout signals, recovery and mixed-discipline proposals, explanation accuracy, and explicit staged approval. Acceptance: preview/stage/accept/dismiss tests prove no plan mutation before approval and preserve account/revision integrity.
4. **Wave 4 — Android reliability** — offline plan persistence, activity links, owner isolation, backup/restore and legacy plan checks; then applicable instrumentation/emulator acceptance. Acceptance: repository/backup tests and emulator flow where available; never touch the owner's physical install.
5. **Wave 5 — Candidate readiness** — full JVM suite, debug build, critical defect review, supported instrumentation, limitations and private-testing handoff. Any unavailable device evidence keeps the candidate provisional.

## Current evidence and status

- Wave 1: **in progress**. `dbff074a` makes Engine 1.5 the packaged default and retains explicit Engine 1.0 selection. `b165e751` introduced optional `targetWeeklyKm`. Compatibility correction currently uncommitted: absent targets preserve the previous staged mileage formula; explicit targets use separate target-capped progression.
- Quality gate alignment: UI now uses the engine's 40 km recent-mileage threshold for two quality sessions. Current uncommitted code includes two advanced-builder callbacks corrected in addition to the normal weekly setup.
- Latest validation on JDK 21 / Gradle 8.13 / local Android SDK: `:app:testDebugUnitTest` **401 tests, 0 failures**; `:app:assembleDebug` **BUILD SUCCESSFUL**. Focused B7 matrix: 3 tests, 0 failures. Targeted coaching and architecture tests passed.
- Not run: Android instrumentation, emulator, physical device, authenticated Garmin API, backup round-trip through the Android UI. Do not claim device or private-testing acceptance.

## Exact next action

Commit the validated nullable-target/legacy-ceiling fix and 40 km UI alignment, update this file and `docs/qa/engine15-continuous-handoff-20261113.md`, push the branch, then inspect `Engine15FeedbackTest`, `Engine15FeedbackRepositoryTest`, and `Engine15InterferenceSchedulingTest` against Wave 3 acceptance and close a focused feedback milestone with its tests.
