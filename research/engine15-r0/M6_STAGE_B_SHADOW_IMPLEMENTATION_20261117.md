# M6 Stage B — independent quality-spacing shadow implementation

Date: 2026-11-17. Branch `feature/engine15-m6b-independent-quality-spacing-20261117`, stacked on M6A PR #117. Policy preregistered in `M6_STAGE_B_PREFLIGHT_20261117.md`.

## Implemented
- `Engine15M6IndependentQualitySpacing.propose(M4B.Result, M6B spacing input, M5B reviewed quality requests)` recomputes both independent M6A date calendar and independent M5B quality preview. It then verifies original M5A slots match every M6A dated session.
- Quality previews can propose dates only from original EASY slots. There is at most one per build week because M5B enforces this constraint. No session is added, reassigned or converted into a production workout.
- Absolute LocalDate day differences enforce minimum 2..3 days from any LONG run (including across week boundaries) and minimum 2..3 days between quality previews. Recovery-week quality is already rejected by M5B.
- Optional caller-reported hard dates and independently blocked quality dates are additional restrictions. Missing cross-discipline evidence stays explicitly unknown; supplied empty sets are never promoted to authenticated complete history.
- Typed M5B timed previews and original M6A easy/long workouts remain unchanged, as do all M4B weekly mileage budgets. Preview date assignment does NOT reconcile timed seconds with actual kilometres or authorize intensity.
- All source verification, quality dose approval, complete external-hard-history, execution, scheduling and production flags remain false. No Engine 1.0 or live Engine15 generating, persistence, saved plans, Android UI or approvals changed.

## Focused tests
Ten test methods cover normal Tue quality with Thu off/Sun long, Saturday-before-long and Monday-after-previous-long rejection, week-boundary quality separation, explicitly blocked quality dates, known other hard-session dates, three-day exposure-limited weeks, M5B recovery/long/duplicate/review guards, tampered M4B and unavailable M6A, and deterministic identity/unchanged typed workouts.

## Gate B status
Source/tests are committed to GitHub. New M6B Gradle tests have NOT been executed locally; no production or athlete-safety approval is claimed.

From `stridelab-q3-validation/native-android` after fetching and merging this branch:

    .\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15M6IndependentQualitySpacingTest" --tests "io.stridelab.privatealpha.Engine15M6IndependentCalendarTest" --tests "io.stridelab.privatealpha.Engine15M5TimedDistanceReconcilerTest" --tests "io.stridelab.privatealpha.Engine15M5QualityPreviewComposerTest" --tests "io.stridelab.privatealpha.Engine15M5WorkoutComposerTest" --tests "io.stridelab.privatealpha.Engine15M4BIndependentVolumePlannerTest" --no-daemon

Afterward run M3/M4A/Q2/Q3/R0 regression. Future acceptance still needs independently validated quality intensity and dose, authentic segment source evidence, cross-discipline conflict resolution, end-to-end Android/emulator testing, and M9 production gate.
