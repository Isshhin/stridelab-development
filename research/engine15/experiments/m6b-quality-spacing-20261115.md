# Engine 1.5 M6b — Quality-day spacing proposals, not workouts

Date: 2026-11-15. Branch: `research/engine15-m6b-quality-spacing-proposals-20261115`, stacked on M6a draft PR #80.

## Objective and difference from current generator

The existing Android Engine 1.5 uses largely fixed template strings, limited quality-count gates and an existing scheduling calendar. Neither it nor M6a establishes athlete-specific threshold intensity, exposure provenance or executable mixed time/distance workouts. The M6b research layer adds **review-only** candidate hard-work slots using the *actual M6a constrained multiweek easy/long-run calendar* rather than placing quality sessions independent of long-run and blocked-day context.

`IndependentQualitySpacingPlanner.propose` takes an externally requested 0–2 potential quality slots per build week, preferred days, and **explicitly supplied blocked dates** (which may represent separately reviewed mixed-discipline constraints). On each build week it considers only existing EASY run days, excludes long runs and days too close to the long run, excludes user-supplied blocked days, searches all 1- or 2-slot combinations, checks minimum calendar separation from the current and preceding week's quality slots, and selects the best deterministic preferred/spaced candidate. During M4 recovery weeks it holds quality opportunities entirely. Impossible schedules defer the entire candidate with an explicit rule instead of silently changing availability or adding sessions.

Example with Saturday long, 6 running days and Thursday unavailable: 1 proposed quality slot defaults to Tuesday; 2 requested slots can be tentatively suggested on Monday and Wednesday. A recovery fourth week produces **zero** quality candidates. These examples are scheduling scenarios, **not authorizations or athlete prescriptions**.

The proposed `IndependentQualityOpportunity` references an **unchanged, preexisting EASY distance run** and carries `proposedOnly=true`, `requiresSourceAndDoseReview=true` and `executable=false`. No threshold pace, HR, work repetitions, time, quality distance, or completed mileage is invented. Nothing gets converted into an executable or persisted training session.

## Validation status

- `IndependentQualitySpacingPlanner.kt`: new pure research component.
- `IndependentQualitySpacingPlannerTest.kt`: **12 new JVM test methods** covering default Tuesday choice, two spaced candidates, explicit preferences, blocked days, impossible schedules, stricter spacing, zero requested quality, unchanged M6a kilometer allocations, extra week blocks, two-run-day hold, crossing a week boundary with adjacent proposed days, and deterministic unauthorized outputs.
- **These tests are committed but not yet executed**. The underlying M6a 12 tests have also not been executed. No claim of an Android build, emulator run, phone installation, or approval for integration.
- To validate both stacked research milestones in **one new clean worktree** (keep existing user's dirty `E:\stridelab-validation` and prior phone-QA worktree unchanged):
  ```powershell
  cd E:\stridelab-validation
  git fetch origin research/engine15-m6b-quality-spacing-proposals-20261115
  git worktree add --detach E:\stridelab-m6b-research-validation origin/research/engine15-m6b-quality-spacing-proposals-20261115
  cd E:\stridelab-m6b-research-validation\native-android
  $sdk = "$env:LOCALAPPDATA\Android\Sdk"
  $env:ANDROID_HOME = $sdk
  "sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII
  .\gradlew.bat :app:testDebugUnitTest --tests '*IndependentConstrainedMultiweekPlannerTest' --tests '*IndependentQualitySpacingPlannerTest'
  .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
  ```

## Known limitations and remaining coaching roadmap

**M6a + M6b are structural advances, not the completed coaching algorithm.** The research pipeline still uses M4's unvalidated weekly build/deload settings and externally asserted caps. Proposed quality slots are not runnable workouts. The system cannot yet deduce reliable session intensity or pace from observed training, reconcile timed session distances into the weekly volume, or claim that planned recovery is physically appropriate for a runner. It also does not model complete strength/calisthenics training loads—the supplied blocked days are simple external policy inputs, not a physiological interference score.

Priorities for subsequent isolated research:
1. **M7 evidence and attribution**: source-verified coverage, activity duplicates, session-to-completion linkage and separately reviewed training intent; avoid the current simplistic `TOLERATED` asserted flag as an inferred physiological fact.
2. **M8 actual adaptive plan decisions**: integrate M6a progression with M7 *verified* feedback, distinguish recovery, illness, missed training, variability and return-to-run, and compare longitudinal trajectories. Distinct Train Your Way mileage targets and race-specific goals must have different plan policy decisions.
3. **M9 executable workout reconciliation**: quality-dose ceilings, typed target/time provenance, a valid distance accounting strategy, spacing against long runs and mixed disciplines, and no fake measured threshold pace/HR.
4. **M10 Android integration**: explicit experimental engine selection, plan persistence, owner isolation, offline signed-out generation, compatibility and rollback. Integrate the reviewed UI work from draft PR #78, but do not merge the entire research stack blindly.
5. **M11 final QA**: full JVM + Android instrumentation, emulator screen-size/font accessibility, synthetic account isolation, Garmin/maps/offline behavior, final owner-controlled physical-phone QA and promotion decision.

**Owner preference:** Do not request phone QA until the final roadmap gate. The previous UI-only QA APK intentionally did not include this coaching research, so it is not evidence about algorithm performance. No production activation or merge.
