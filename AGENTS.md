# StrideLab Agent Instructions

Read @[Core Engine Spec](F:/StrideLab/Workflow Vault/Isshhin/10-Architecture/Engine-Rules.md) and @[Research Index](F:/StrideLab/Workflow Vault/Isshhin/Research-Index.md) before modifying training engine, validation, scheduling, or workout UI code.

## Obsidian Workflow & Memory Hub
- Use `F:\StrideLab\Workflow Vault\Isshhin` (including `Research-Index.md`, `10-Architecture/Engine-Rules.md`, `20-Tasks/Active-Roadmap.md`, `30-Logs/`) as the workflow and memory hub across sessions to conserve context/quota.
- Keep all vault notes concise and bullet-pointed (no long essays).
- Keep `10-Architecture/Engine-Rules.md` and `20-Tasks/Active-Roadmap.md` current when architecture or milestones change so future sessions avoid re-exploring the codebase.
- After completing any major audit or bug fix, append a 3-bullet summary (**Symptom**, **Root Cause**, **Fix**) to `F:\StrideLab\Workflow Vault\Isshhin\30-Logs\`.

## Scope & Git Policy
- Completely ignore `F:\StrideLab\Legacy-And-Archives` and `F:\StrideLab\DriveBackup` in all searches, audits, and edits.
- Read only target files, their direct tests, and `Engine-Rules.md`; keep diffs tightly scoped with no unrelated refactors.
- Work exclusively on `dev` (never edit, reset, force-push, or push `main` unless told). Never stage `native-android/.idea/`, credentials, or FIT exports, and never push without explicit owner approval.

## Protected Regression Guards
- **Engine 1.5 Invariants**: Preserve weekly budget equality (`sum(week.distanceKm) == week.proposedKm` within `1e-6`), immutable `PlannedSession.difficultyBaseline` round-trip validation, and pure preview + explicit user-approval coaching flows.
- **Garmin Offline/Guest Independence**: Garmin Personal API sync, local FIT/GPS, and Training/Activity views must work offline and signed-out (`state.account == null`). Never gate Garmin behind StrideLab cloud auth or overwrite source FIT recordings.
- **UI & Chart Authority**: Keep the active Kotlin/Compose AMOLED design system, 3-tab dock (`Today`, `Training`, `More`), and continuous source-measured lines on a pause-compressed moving-time x-axis.

## Verification & Testing
- Run Gradle sequentially from `native-android/` with JDK 21: `.\gradlew.bat "-Dorg.gradle.java.home=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot" :app:testDebugUnitTest --tests "io.stridelab.privatealpha.<TestClass>"` (and full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, `:app:compileReleaseKotlin` before handoff).
- Report exact PASS/FAIL/BLOCKED results; never claim device verification from unit tests or builds alone.

## Project Memory (`docs/wiki/`)
- **Read First:** At the start of any planning, refactoring, or debugging task, read `docs/wiki/00-StrideLab-Index.md` and only the specific topic page(s) relevant to the request before scanning source files.
- **Keep Updated:** After completing any architectural change, engine rule tweak, or UI refactor, update the affected `docs/wiki/` node(s) in the same turn so the graph always matches the codebase.
- **Stay Concise:** Keep wiki nodes focused, sanitized, and modular with unique stems and YAML frontmatter. Record *why* key constraints exist (e.g., warm-up caps, retired UI blocks, GPS trimming) so future sessions do not regress them.

## Knowledge Graph & Context Pack Maintenance
- Whenever a task changes screens, architecture, features, or contracts, update the affected `docs/wiki/` node(s) and bump `last_verified_commit`.
- Re-run `python scripts/compile_context_packs.py` from the repository root so `docs/context-packs/` and `F:\StrideLab\Workflow Vault` (`Knowledge-Graph/` and `Context-Packs/`) stay automatically synced.
- Confirm `0` broken `[[wikilinks]]`, `0` filename collisions, `0` orphan nodes, and `0` PII/secret findings before committing.
