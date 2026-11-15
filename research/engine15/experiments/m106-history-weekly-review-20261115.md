# M10.6 follow-up: observed patterns and approval-first review

Starting candidate: `29ec206936684fa5229573903260a8242106f9c0`, retained draft PR94. Owner confirmed weekly approvals, optional overrun review, post-run effort/difficulty/discomfort, weekly fatigue/availability, and changes only to upcoming unstarted sessions. No UI implementation was authorized.

## Delivered behavior

**New drafts:** repeated completed easy/long logs now influence session allocation. Only compatible, same-owner, dated, revisioned, nonduplicate, low-reported-effort records without adverse feedback qualify. Repetition must span at least seven elapsed days, with at least two calendar weeks and an observation within the last 14 days; the observation window is 28 days. These are provisional evidence requirements, not physiological constants. Missing total-week coverage never establishes zero training or replaces reported weekly mileage.

Repeated long-run exposure can supersede an outdated reported longest-run cap in a new draft, except in return mode. Easy and secondary endurance runs receive separate observed-exposure caps. Final-role checks prevent a failed quality recipe from becoming an oversized easy run. Long-run proportion and work-distance budget are checked again after any reduction. The first delivered quality-work envelope, after reconciliation, bounds later provisional work.

**Weekly approval:** `previewEngine15Week` returns a pure review. `acceptEngine15Week` runs through the existing owner/revision/mutex repository boundary, rechecks the exact snapshot, evidence, review date and input, then applies the entire approved batch. Completed, started, linked, already-suggested and locked sessions remain unchanged. Original distances and recipes remain historical prescriptions; accepted reductions use the existing time-only suggestion contract. Unavailable days are not relocated as catch-up training. An unchanged week still saves its check-in and approval.

**Feedback:** optional `completionDifficulty` and `discomfort` fields round-trip in WorkoutLog; legacy records remain readable. The existing effort field is reused. Recent reported pain influences both weekly review and new-draft screening even at low RPE. Pain evidence uses a consistent seven-day review window; this is not a recovery prediction or medical clearance. A persistent explicit health-hold/resolution workflow remains future work.

**Optional overrun review:** within the current/previous day, recorded distance exceeding the original eligible target by both 10% and 1 km, or duration exceeding effective target by both 10% and 10 minutes, prompts review. These thresholds avoid small recording differences and are provisional interaction policy, not injury thresholds. Extra distance alone causes neither progression nor a reduction. Accepted time-only alternatives do not retain an active original-distance target for this comparison.

**Mileage options:** an unmet target now includes a lower planned-peak option and evaluates longer generated horizons up to 16 weeks. A proposed longer timeline must actually reach the requested peak under the same constraints. If none does, the explanation says the timeline is not established. Requests are not silently changed and race readiness is not inferred. Optional infeasible horizons do not invalidate the original draft.

## Actual synthetic examples

For an 80 km reported baseline with a 24 km long run, two different repeated easy-run histories produce different first-week allocations. An 8 km easy history produces 9 km ordinary easy runs; a 12 km easy history produces roughly 9.8-9.9 km ordinary easy runs with a different long-run allocation. This confirms history sensitivity, not training effectiveness.

With a stale 12 km reported longest run but repeated logged 24 km long runs, the first-week long run changes from 13 km to 19.6 km while reported weekly mileage remains unchanged. The new plan does not immediately prescribe the maximum observed distance. Raw synthetic snapshots are produced by Engine15HistoryPatternTest in `app/build/reports/m106-followup/`.

## Tests and independent critique

New tests cover history sensitivity, sparse/foreign/future/duplicate evidence, consecutive Sunday/Monday exposure, adverse feedback, final easy-role caps, long share, source revisions, protected targets, weekly approval/replay, maintenance feedback persistence, overrun without automatic penalty, serialization, and alternative timelines. A 48-profile observed-pattern matrix checks future work against the first delivered envelope. The work-envelope review concern was corrected defensively; that matrix already passed before this correction and does not establish a reproduced failure.

Failing regressions were observed for identical allocations despite different histories, stale long-run exposure, maintenance feedback loss, unsupported/duplicate weekly sources, low-effort pain ignored by fresh drafts, consecutive-day evidence, downgraded easy exposure and mismatched pain windows. The first dose-matrix run used an invalid 20 km/two-quality fixture; it was corrected to supported inputs rather than weakening the production gate.

Final validation command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m'
```

PASS: 762 JVM tests across 98 suites, zero failures/errors/skips; Android debug assembly and instrumentation-source compilation passed. Final run took 33 seconds, 53 tasks. `git diff --check` passed; existing compiler/deprecation warnings remain. No emulator/instrumentation execution, phone installation, real-athlete validation, live provider test, merge or experimental-engine activation.

## Research boundary and remaining gaps

The [individualized training trial](https://pubmed.ncbi.nlm.nih.gov/35975912/) supports investigating response-informed prescriptions. It does not validate our ratios, time windows or thresholds. No HRR zones, threshold pace, VO2max, injury probability or recovery score is inferred.

Weekly mileage still depends on the reported baseline; imported-history coverage needs explicit confirmation. Positive-response progression, race-specific readiness and longer-than-16-week feasibility remain incomplete. Workout logs do not establish completed interval work, so a reported 3 x 3 km threshold session is not automatically reproduced or progressed. General-fatigue check-ins are persisted as review audit records, not a physiological longitudinal model. Threshold/quality dose remains conservative. This candidate is not equivalent to Runna/Garmin and does not prove real-world effectiveness.

## Recommended UI work — not implemented

1. **Weekly coach review:** a compact upcoming-week before/after list, total changes, reasons, uncertainty, and one approve action. Maintenance reviews must also be saveable. Refresh stale reviews rather than applying them.
2. **Post-run check-in:** existing effort plus completion difficulty and discomfort, with optional unanswered states. Keep actual distance/time distinct from prescribed targets and show any linked activity source.
3. **Optional overrun prompt:** offer review without a warning-style penalty or automatic change. Explain whether the trigger was extra time or distance; allow dismissal.
4. **Mileage-goal alternatives:** show the lower peak and supported longer-horizon option side by side, or explicitly identify an unresolved timeline. Add structured option data before wiring selection; do not parse explanatory strings as application state.
5. **History confirmation:** show the weeks covered, reported versus observed mileage, and missing periods. Let users distinguish availability interruption, deliberate recovery and difficulty. Do not label partial imports as complete history.
6. **Workout purpose and current target:** explain easy/recovery/endurance/long/quality roles and display the accepted effective prescription consistently across Today, calendar and details. Existing summary distance inconsistencies should be fixed in this separate UI phase.

Use the current AMOLED/lime identity and existing navigation/components. The new weekly review and feedback APIs require this UI wiring before athletes can use them; no new screens or disconnected controls were added.
