# Validated lime UI integration — 2026-11-10

This branch starts from `refactor/trackside-legacy-name-cleanup-20261110` at `941b5b53d394fc6e9306985e53d075556e2a887e`.

The implementation ports the approved AMOLED/neutral-gray/lime Plan Builder presentation from `integration/modern-ui-lime-builder-share2-20261108` while retaining the cleaned runtime `Trackside*` function and file names.

## Scope
- Adds the source branch's `PlanBuilderPalette` and stage progress component.
- Replaces the visual implementations of ten Builder screens: Advanced Running, Builder Schedule, Builder Screens, Calisthenics Setup, Cross-Training Goals, Guided Running, Other Discipline Slots, Running Setup, Strength Setup, and Week Review.
- Preserves `PlanBuilder.kt`, all engine sources, Garmin, Today discipline analytics, offline maps, saved activity handling, and the prior unit-test/UI-test fixes in the validation baseline.
- Keeps existing Compose test tags and screen function signatures. Some tags retain historical strings for test compatibility.
- Red/pink warning accents may remain for errors, not primary selection surfaces.

## Validation status
- **Source-integrated only**; Gradle compilation, JVM unit tests, instrumentation-test compilation and emulator runtime tests MUST be rerun on this new branch.
- Use JDK 21 and Android SDK 36 for Gradle 8.13. On the user's workstation, use Android 15/API 35 emulator (API 37 triggers Espresso `InputManager.getInstance` initialization failures).
- Verify physical-device signing compatibility separately before any installation. Never uninstall or clear existing activity data as part of QA.

## Required QA
```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
```
Then run targeted instrumentation tests on a dedicated API 35 emulator; do not run against the existing installed physical phone.
