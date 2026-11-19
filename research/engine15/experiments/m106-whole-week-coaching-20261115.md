# M10.6 whole-week coaching candidate

Base: `9122fa8655d890531b372f61c77b1e23ead663a7`, branch `fix/engine15-m106-coaching-quality-20261115`. This is an unmerged candidate, not an athlete-validated coach. Latest owner scope excludes UI work and device installation.

## Implemented production changes

The existing scheduler still chooses dates and preserves nonrunning sessions. A running-week composer now assigns easy, recovery, secondary endurance, long and quality purposes before distributing the available weekly distance. Recovery follows demanding days across week boundaries. Individual exposure and explicit availability constrain every run; the long run is limited to 40% of final rounded weekly distance for three or more runs. These are provisional coaching policies, not proven injury thresholds. A low-frequency request may deliberately receive less distance than requested rather than an enormous easy run.

Quality now has persisted, versioned distance segments: warm-up, work, recovery and cool-down reconcile exactly to session distance. Goal-specific repetitions and effort, complementary workout families and genuine continuous progression replace text-only templates. The initial work-distance envelope does not grow automatically alongside mileage; two quality sessions require separately reported recent frequency. This distance accounting is not measured physiological intensity distribution. Existing builder inputs do not expose the new frequency or explicit recovery-hold fields yet, so unknown frequency permits at most one quality session.

Recent owner-scoped, dated difficult-workout feedback can reduce newly generated drafts. This is strictly subtractive: affected distance/time declines, quality becomes easy, no other session grows, and time-only beginners remain time-only. Recent explicit illness can defer a near-term draft for review. Existing plans, approval gates and source revision checks remain unchanged. No successful future workout or easy completion is treated as proof of increased fitness. Free-text injury notes are not clinically interpreted; this remains a material limitation.

Optional prescription metadata validates segment totals against session distance and quality status. Historical snapshots without it remain readable. The schedule-review projection clears obsolete metadata for accepted time-only alternatives. No UI layout, controls, navigation, Engine1/2, Garmin, authentication, signing or migration changes were made.

## Separate research changes

Consecutive recovery weeks cannot restore an old high baseline automatically. Reviewed increments bind to demonstrated load, not an older approved starting volume. Repeated partially completed build weeks may repeat actual exposure rather than trigger a discontinuous classification. Quality progression requires the immediately preceding two complete weeks, checks family frequency and combined weekly work, and cannot reuse one weekly exposure as permission for two sessions. These modules remain non-executable research; none is activated for production prescriptions.

## Identical-input comparison

`Engine15ProgramComparisonTest` reproduces the 23 M10.5 production inputs. Exactly 21 generate and only zero/tiny targets reject. The adjacent CSV contains every generated week compared with the checkpoint output. Full request/plan JSON is generated under `native-android/app/build/reports/m106/`.

| Scenario | Checkpoint | Candidate |
|---|---|---|
| 80 km history, only two run days, longest 12 km | Up to 38 km easy run | No run above 13 km; first week 23.4 km |
| 30-minute availability, recovery week | 30 km / 180 minutes | 24 km / 146 estimated minutes |
| Fast planning pace, 30 km history | No first-week quality | One reconciled quality session |
| 39.9 vs 40 km, unknown quality history | Frequency cliff | At most one quality session on either side |
| Zero/tiny target | Positive-time zero-distance runs | Explicit request correction |
| 40 to 80 km target | Peak 45.5 km | Still peak 45.5 km; unmet target explicitly explained |
| Marathon, 60 km history, longest 12 km | Long run capped at 13 km | Still capped at 13 km; no race-readiness claim |

The last two rows are counterexamples to calling this a competitive autonomous coach. Whole-week composition is improved, but the inherited trajectory remains conservative and insufficiently adaptive. A recovery response regression initially increased total volume from 26.3 to 30 km by reallocating load; a failing test reproduced it and the final subtractive policy fixes it.

## Validation

Final local command (JDK 21.0.13, configured Android SDK):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8'
```

PASS: 741 JVM tests across 95 suites; zero failures, errors or skips. Android debug assembly and instrumentation-source compilation passed. Final run: 32 seconds, 53 tasks. Existing deprecation/native-library warnings remain. No instrumentation execution, emulator, phone, live Garmin, hosted service or real-athlete trial was performed.

Added acceptance, program, history-response, comparison and independent-policy tests cover exact distance sums/serialization, corrupt metadata rejection, 1-6 running days, 1,120 rounded-week long-share cases, explicit time/daily limits, beginner run/walk, goal-specific structures, cross-week recovery, unknown frequency, source ownership/date boundaries, subtractive responses, stale research evidence and rebuild increments. Existing 64-profile matrix retains weekly envelope, identity, nonrunning and calendar assertions; obsolete per-session equal-share expectations were replaced with exposure/availability/reconciliation assertions. New failing counterexamples preceded fixes. Independent source review found no remaining critical defect in the final response implementation; reviewer did not independently execute the build.

## Evidence and rejected shortcuts

[Individualized endurance training trial](https://pmc.ncbi.nlm.nih.gov/articles/PMC9473708/) supports investigating response-informed planning, not these exact heuristics. [Novice progression trial](https://pubmed.ncbi.nlm.nih.gov/17940147/) does not establish a universal injury-preventing percentage. [Training-intensity comparison](https://pmc.ncbi.nlm.nih.gov/articles/PMC7739641/) does not justify claiming a universal 80/20 optimum. Accordingly, no measured thresholds, VO2max, recovery score or injury-prevention claim is fabricated.

[Runna training focus](https://support.runna.com/en/articles/11598487-how-to-choose-your-training-focus) and [training preferences](https://support.runna.com/en/articles/10393191-how-to-use-training-preferences), plus [Garmin training-plan documentation](https://www.garmin.com/en-US/blog/fitness/garmin-training-plans-for-runners/), are capability references only. No proprietary algorithm or head-to-head output comparison was available. Extra templates, increasing the percentage ceiling, or treating planned sessions as successful history were rejected as substitutes for evidence-driven development.

## Remaining priorities before personal-device acceptance

1. Replace static long-run exposure and inherited weekly trajectory with a provenance-aware replan loop based on observed completion/tolerance. Test return, missed-session, repeated-recovery and schedule-change trajectories over months. Do not unlock progression from synthetic future success.
2. Establish reliable history coverage and quality exposure. The unchanged PlanBuilder incomplete-import default still underestimates some histories. Add trustworthy effort/duration and interval evidence before fitness-based quality progression; incomplete evidence must remain unknown.
3. Add event/date feasibility and goal-specific long-run development. A marathon label with a 13 km longest run is not adequate preparation. Preserve mileage-target mode separately.
4. Validate low-volume and high-volume prescription usefulness with independent coach review, then consented observational athlete evaluation. Current role ratios, fixed recovery cadence, taper suppression of quality and exposure limits may over-defer or under-stimulate experienced athletes.
5. In a separately authorized UI phase, expose truthful uncertainty, backend frequency/hold inputs and effective summary distance. No disconnected controls were added here.

This candidate improves reproducible coaching decisions and bookkeeping. It does not establish injury prevention, real-world training effectiveness, or parity with Runna/Garmin.
