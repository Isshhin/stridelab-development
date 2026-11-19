# Engine 1.5: confirmed evidence and coaching controls

Starting checkpoint: `818cdc51df5dd721e68fa4cfd17753edbfd86ea7`, retained draft PR #94. Follow-up branch: `feature/engine15-coach-review-ui-20261115`. Backend commit: `af062328`. This is an implementation candidate, not an athlete-effectiveness validation.

## Implemented

- Imported distance is no longer silently divided by four to establish weekly capacity. The builder displays calendar weeks and requires explicit complete-week confirmation, or the user's own mileage entry. Current/incomplete-distance weeks cannot establish that average. Empty completed weeks can be explicitly confirmed as zero; their completeness is not inferred.
- Repeated, dated, explicitly recorded threshold work can inform the threshold dose. Whole-run distance alone cannot establish work distance. Evidence must pass existing owner, duplicate, completion, date, response and provenance filters. No quality frequency increase is inferred.
- Recorded work remains capped by observed work, weekly work budget, session reconciliation and the first delivered dose envelope. The 60% session work allowance for supported threshold evidence and repetition limits are provisional engineering policies, not scientifically established safety boundaries.
- Typed lower-target/longer-horizon choices survive plan persistence. The UI creates a new draft through the existing builder; it does not approve or silently change the training mode.
- Completed or linked running sessions can receive separate effort/difficulty/discomfort feedback. Original logs, measurements and prescriptions stay unchanged. Changing the source link archives and clears that response; undated feedback does not become recent evidence using the scheduled date.
- Feedback and explicit quality work persist in offline entry drafts. Unanswered feedback remains unknown. Accepted time-only targets remain time-only when progress is saved and no longer contribute the old mileage to current summaries.
- Weekly review exposes fatigue and unavailable days, a preview with reasons, and explicit approval. Extra-running review is optional and uses the athlete's current inputs. No change is applied merely by opening a card.

## UI boundaries

Existing builder, workout, Training Plan tools and Manage Plan screens are retained. New controls reuse `SignalCard`, `SignalChip`, `SignalButton`, existing typography and lime accents. Choice targets have a minimum 48 dp height and wrap at narrow widths. No new navigation destinations, tab changes, provider/auth changes, map changes or Engine 2 changes. The current timeline still opens the calendar through Plan tools.

The quality-work field has a decimal keyboard and Done action. Completed feedback is editable separately while actual measurements remain read-only. New helper text explicitly uses the dark-surface foreground color. This is targeted integration, not a redesign of stable screens.

## Reproducible behavioral comparisons

| Scenario | Previous behavior | Candidate contract |
|---|---|---|
| Three recorded runs with unknown week coverage | Rolling distance could become distance / 4 | No inferred weekly baseline until coverage confirmation |
| Repeated 9 km threshold work at reported RPE 7, as planned, no discomfort | Introductory work-dose assumptions ignore explicit performed work | Threshold work exceeds 2 km but never exceeds observed 9 km in the fixture |
| Same runs with only total distance | No explicit work evidence | Still no threshold-dose evidence; no fabricated interval completion |
| Accepted 30-minute reduction from a 60-minute hard session, then save progress | Original target could reappear | 30-minute time-only target retained |
| Completed linked run, then report difficult/painful response | No separate completed-run response path | Separate response feeds review only when actual date is known |
| Replace linked activity after feedback | Risk of response following wrong source | Old response archived, cleared from active interpretation |

These are tested decision contracts, not evidence of injury reduction, improved fitness, or parity with Garmin/Runna. Easy/long-run pattern learning from PR #94 remains in place; this increment adds explicit threshold evidence rather than replacing their role-based distribution.

## Validation

- `:app:testDebugUnitTest`: 770 tests across 99 suites, zero failures/errors/skips.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed locally, JDK 21, Android SDK, two Gradle workers.
- Eight new JVM contracts cover history uncertainty, effective targets, quality work validation/drafts, typed goal choices, history-sensitive threshold generation, in-progress targets, separate feedback and source replacement.
- Compose at 412 x 950 dp, font scale 1.0: 17/17 selected tests passed (seven new controls, two builder, one calendar, one Manage/back, six workout-player tests).
- Compose at 360 x 800 dp, font scale 1.3: 7/7 new-control tests passed. Screenshots inspected for wrapped choices, readable helpers and reachable actions. The standalone fixtures also use a Material Surface to supply the foreground color that the real app supplies through Scaffold.
- Local evidence: task workspace `outputs/engine15-coach-ui/412` and `360`; final logs copied to `outputs/engine15-coach-ui/validation`. These are component/flow fixtures, not exhaustive whole-app accessibility certification. TalkBack and physical-device acceptance were not performed.
- Initial UI runs exposed test scrolling that did not reach later lazy-list items; tests now scroll through the list to visible save controls and verify callbacks. The calendar navigation test was stale: it bypassed the current timeline's Plan tools. It now exercises that existing route and still asserts the selected date survives Manage/back. No assertion was removed.
- A disposable API 35 emulator was used. No physical phone was installed, reset or modified. Screenshots are synthetic fixtures, not owner acceptance or live Garmin validation.

Reproduction commands: `native-android/gradlew.bat -p native-android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2048m`. Instrumentation runner: `io.stridelab.privatealpha.test/androidx.test.runner.AndroidJUnitRunner`; classes `Engine15CoachControlsTest`, `TracksideEngineBuilderIntegrationTest`, `TracksideTrainingCalendarUiTest`, `TracksideManageNavigationTest`, `WorkoutPlayerTest`. Use a disposable emulator only.

## Remaining gaps and next priorities

1. Persistent recovery holds and explicit resolution: the current recent-evidence window is not medical clearance. A previously reported concern must not be interpreted as resolved merely because time passed. Feedback can update interpretation but does not prove recovery.
2. Broader longitudinal progression: confirm dependable weekly coverage, distinguish opportunity from tolerance, and evaluate positive-response progression beyond current conservative envelopes. Builder longest-run initialization still uses historical provider maximum; it is not a reliable recent-capacity estimate.
3. Quality calibration: actual work is self-reported, not sensor-verified; RPE does not establish physiological threshold. Validate duration/intensity distribution and interference over complete multi-week histories before broadening doses.
4. Race readiness and feasibility beyond the supported search horizon remain incomplete. A longer option is only displayed when the current algorithm actually finds one within 16 weeks.
5. Complete owner visual/accessibility acceptance and longer real-athlete observation remain necessary. This work does not establish competitor parity, injury prevention or production readiness.

Experimental research prescriptions remain non-executable. No merge, default-engine activation, production fitness-data changes or phone installation is authorized by these results.
