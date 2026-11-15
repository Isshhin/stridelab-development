# Engine 1.5 M9c — Complete-week running program review

Date: 2026-11-15. Draft research stacked on M9b PR #87.

## Purpose

The earlier research work has four complementary boundaries:

1. **M6a**: an explicit multiweek EASY/LONG running-distance calendar with per-day and long-run checks.
2. **M6b**: proposed hard-running DAYS, respecting the long run and recovery-week calendar.
3. **M9b**: time-based threshold/tempo **work-dose previews**, capped by two recent caller-asserted completed doses and explicit per-session/weekly limits.
4. **M9a**: arithmetic-only reconciliation of individually asserted timed-segment DISTANCES to one original M6a daily km allocation.

None of those alone checks whether the *entire next-week calendar* can coexist without silently dropping a quality slot, doubling the weekly distance, or placing a quality day next to an externally reported hard strength/conditioning day.

## What M9c implements

New pure research component: \`IndependentWeeklyProgramReview.inspect(base,spacing,dose,input)\`.

- It requires the M6a **first week**, M6b **first-week spacing**, and a separate M9b preview if M6b selected any quality opportunities. A zero-quality week is supported as an unchanged EASY/LONG base week, with no fabricated quality dose.
- It requires **exact matching sets of quality days** between M6b, M9b and per-day M9a segment claims; missing, extra and repeated days fail closed.
- It checks every typed running day against the M6a weekly distance budget. Selected quality workouts are **hypothetical substitutions for existing EASY distance sessions**, never extra sessions or extra kilometers.
- It runs M9a once per selected quality day, checking every warm-up, work interval, between-interval recovery and cool-down distance sum against the original EASY day allocation. Segment distances are externally *asserted*, not inferred from workout time or assumed pace. Failing any quality day defers the entire weekly preview.
- It separately verifies that hard-running days (quality + designated long run) satisfy an explicitly reviewed minimum calendar spacing.
- It accepts caller-reviewed **other-discipline HARD dates**, including up to three days before and three days after the running week. If any such day is too near an already proposed hard-running session or the long run, the entire candidate defers. The spacing input is a transparent **policy assertion**, not physiological interference modeling. A known-empty hard-day calendar must also be explicitly reviewed; absence of a review is not treated as proof of no hard training.
- It reports later M6a weeks as **base-only/unqualified** rather than incorrectly copying next week's observed dose permission into unseen future weeks.

For a synthetic six-day 60 km M6a week with Saturday long, M6b may propose Tuesday threshold OR Monday threshold plus Wednesday tempo. When the independent M9b dose preview fits and every M9a segment distance adds to each selected existing run's kilometer allocation, M9c produces a **read-only first-week calendar** with unchanged 60 km, all EASY/LONG distances still accounted for, and 0–2 proposed quality replacements. Reported hard training on a selected hard day or the adjacent day blocks the preview under the requested two-day gap.

A successful M9c result has:
- \`segmentDistanceArithmeticChecked=true\` — an arithmetic check over *caller-supplied segment distances*, NOT observed distance truth.
- \`ownerAndSourceProvenanceVerified=false\`, \`qualityIntensityVerified=false\`, \`otherDisciplineFatigueVerified=false\`.
- \`authorizedForPlanMutation=false\` and \`authorizedForProduction=false\`; all day cells carry \`executable=false\`.

No existing PlanBuilder, \`Engine15.generate\`, persisted plan, Garmin/FIT importer, Android UI, offline account policy, or phone APK is changed. Race-specific periodization and source-backed intensity remain separate qualification requirements.

## Tests

- Source: \`native-android/app/src/main/java/io/stridelab/privatealpha/IndependentWeeklyProgramReview.kt\`.
- Tests: \`native-android/app/src/test/java/io/stridelab/privatealpha/IndependentWeeklyProgramReviewTest.kt\`.
- **17 new focused JUnit cases**, committed but **NOT yet run**. Scenarios: complete 60 km single-quality replacement, two different quality families, no-quality unchanged week, missing M9b/one-of-two dose approvals, rejected/extra/duplicate/missing M9a segment claims, hard-day overlap and adjacency within and across week boundaries, independently stricter calendar gap, unknown cross-discipline calendar, calendar time range, and deterministic no-mutation/authorization safeguards.
- Stacked M8a/M8b/M9a/M9b changes also require a fresh exact-SHA Gradle validation. Previous M6/M7 build successes cannot be extrapolated to M9c.

### Focused plus full JVM validation (NO Android instrumentation or phone QA)

A new empty worktree preserves the user's existing edited checkouts. Verify the SHA before testing.

```powershell
cd E:\stridelab-validation
git fetch origin research/engine15-m9c-weekly-program-audit-20261115
$path = "E:\stridelab-m9c-verify-20261115"
if (Test-Path $path) { throw "Validation folder exists. Choose a new empty path; do not delete an existing worktree." }
git worktree add --detach $path origin/research/engine15-m9c-weekly-program-audit-20261115
cd "$path\native-android"
git rev-parse --short HEAD
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
"sdk.dir=$($sdk.Replace('\', '/'))" | Set-Content ".\local.properties" -Encoding ASCII
.\gradlew.bat :app:testDebugUnitTest --tests '*IndependentLongitudinalShadowTest' --tests '*IndependentGoalAwareTrajectoryReplayTest' --tests '*IndependentTimedQualityDistanceReconciliationTest' --tests '*IndependentQualityDoseProgressionTest' --tests '*IndependentWeeklyProgramReviewTest'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

No Android instrumentation/emulator/physical-phone test at this phase. **Owner requested Android tests after M10 integration**, so the integrated app—not isolated research previews—is what will be exercised in M10.1.

## End-of-roadmap acceptance sequence

- **M10 Android integration:** map reviewed independent coaching input/output types to PlanBuilder and the durable plan model through a separately reviewable, opt-in experimental engine. Preserve exact package identity, signed-out offline functionality, account isolation, approved saved plans, original engine rollback, Garmin/maps and the reviewed Week Preview UI. Do not silently merge stacked draft research or present unauthenticated M9c examples as athlete-specific executable workouts.
- **M10.1 Android automated tests:** unit, instrumentation and emulator end-to-end generation with activity/backup/offline and regressions AFTER the integrated experimental path exists.
- **M10.5 Astra independent running-coach audit:** have Astra evaluate actual integrated output for beginner, returning, consistent, high-mileage, mixed-discipline, 5K/10K/marathon and Train Your Way scenarios; challenge progression, workout prescription, recovery and explanations, and record defects.
- **M10.6 Repairs and retesting:** address Astra's defects and rerun affected automated checks.
- **M11 Final physical-phone QA:** only after the previous quality gates, with a verified isolated APK and no automatic replacement of the current user's production data.

M9c is *research arithmetic and scheduling validation*, not the last word on readiness, pace zones, threshold estimation or race periodization.
