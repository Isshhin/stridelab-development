# Engine 1.5 Q2 — versioned mixed-unit prescriptions and bounded cool-down

**Status: implemented as a draft candidate, not yet built or verified on Android.** This branch is stacked on research Q1 PR #103, which was based on UI PR #102. Neither parent branch is merged or changed by Q2.

## What Q2 changes

- Existing version-1 `RunningSegment` and distance-only prescription serialization remain intact for historical plans and existing workout backups.
- A nested version-2 `RunningPrescription` uses `RunningStepV2` with **one executable extent per step**, `meters` or `seconds`, and rejects unknown future versions and invalid mixed extents.
- Newly generated quality workouts retain existing Engine 1.5 **WORK and RECOVERY distance doses** (Q3 will address conservative dose selection), replacing legacy distance warm-up/cool-down with **10–12 min warm-up and 8 min or minimum 5 min cool-down**.
- Remainder distance is **not** added to cool-down, hidden in easy running, or automatically redistributed. If minimum support steps cannot fit the original session-distance allowance and (where explicitly enabled) time allowance, quality is withheld and an easy alternative is used.
- Mixed workouts have `PlannedSession.distanceKm=null` because the total is not an exact distance prescription. A separate `RunningPrescription.estimatedDistanceKm` is a **planning-pace administrative estimate**, not measured performance or a runnable distance target.
- The original long-run 40% and work-distance 20% guardrails are rechecked on the smaller estimated delivered week; no work-dose increase is authorized by Q2.
- Workouts, Today, Training calendar/timeline and legacy training cards differentiate estimated mixed totals from exact distances. Accepted time-only review hides original mixed steps and estimates.
- Mixed-step grouping preserves exact repeats and does not invent recovery after the last repetition.
- Q1 distance-only audit test is disabled **on this Q2 branch only**, because rerunning it against a changed generator would falsely appear to be the original baseline. The executed Q1 results remain attached to the parent PR #103 and reported in its findings document.

## Boundaries and caveats

- This does **not** implement evidence-based Q3 workout selection, progression or deload improvements.
- The 20% cap remains provisional; coaching appropriateness remains unproven.
- Timed segments do not guarantee a particular total distance; the weekly kilometre chart displays estimates and target-shortfall notes. Distance caps for timed segments and FIT export/device support are **not claimed**.
- A non-updated app with a v1-only decoder **cannot read v2 prescriptions**. This is a forward-version compatibility boundary; migration is read-new/write-new for new workouts, read-v1/retain-v1 for old.
- Never use a `planningPace` proxy as confirmed hard-work minutes. The Q1 30/60-minute history cases were synthetic distance proxies, not proven threshold minutes.
- The engine still uses the inherited baseline schedule and work-dose policy; Q2 does not claim physiological safety or improved training efficacy.

## Windows targeted verification

From the Q2 branch's `native-android` directory:

```powershell
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest --tests "io.stridelab.privatealpha.Engine15Q2MixedUnitTest" --tests "io.stridelab.privatealpha.MixedRunningStepGroupingTest" --no-daemon
.\gradlew.bat -PstrideEngine15Active=true :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Required before merge: both Gradle commands pass, end-to-end backup and restore reviewed, zero unexplained safety or policy regressions, and emulator QA of original/accepted time-only/mixed step previews plus weekly-distance labels. **Do not install on the user's phone or merge to dev/main until validation.**

If a regression test fails because it intentionally asserted v1-only structure, update the assertion to distinguish v1 exact meters from v2 mixed extents; do not delete the test or weaken unrelated safety assertions.
