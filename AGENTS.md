# StrideLab Agent Instructions

Read @[Core Engine Spec](F:/StrideLab/Workflow Vault/Isshhin/10-Architecture/Engine-Rules.md) before modifying training engine, validation, scheduling, or workout UI code.

## Obsidian Workflow & Memory Hub
- Use `F:\StrideLab\Workflow Vault\Isshhin` as the workflow and memory hub across sessions to conserve context/quota.
- Keep all vault notes concise and bullet-pointed (no long essays).
- Keep `10-Architecture/Engine-Rules.md` and `20-Tasks/Active-Roadmap.md` current when architecture or milestones change so future sessions avoid re-exploring the codebase.
- After completing any major audit or bug fix, append a 3-bullet summary (**Symptom**, **Root Cause**, **Fix**) to `F:\StrideLab\Workflow Vault\Isshhin\30-Logs\`.

## Scope & Git Policy
- Read only target files, their direct tests, and `Engine-Rules.md`; keep diffs tightly scoped with no unrelated refactors.
- Work exclusively on `dev` (never edit, reset, force-push, or push `main`). Never stage `native-android/.idea/`, credentials, or FIT exports, and never push without explicit owner approval.

## Protected Regression Guards
- **Engine 1.5 Invariants**: Preserve weekly budget equality (`sum(week.distanceKm) == week.proposedKm` within `1e-6`), immutable `PlannedSession.difficultyBaseline` round-trip validation, and pure preview + explicit user-approval coaching flows.
- **Garmin Offline/Guest Independence**: Garmin Personal API sync, local FIT/GPS, and Training/Activity views must work offline and signed-out (`state.account == null`). Never gate Garmin behind StrideLab cloud auth or overwrite source FIT recordings.
- **UI & Chart Authority**: Keep the active Kotlin/Compose AMOLED design system, 4-tab dock (`Today`, `Training`, `Activity`, `More`), and continuous source-measured lines on a pause-compressed moving-time x-axis.

## Verification & Testing
- Run Gradle sequentially from `native-android/` with JDK 21: `.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:testDebugUnitTest --tests "io.stridelab.privatealpha.<TestClass>"` (and full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, `:app:compileReleaseKotlin` before handoff).
- Report exact PASS/FAIL/BLOCKED results; never claim device verification from unit tests or builds alone.
