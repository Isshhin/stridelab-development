# StrideLab Trackside 3.0 — Engine 1.5 manual remediation roadmap (2026-11-05)

**Status: PLAN ONLY. NO IMPLEMENTATION OR NEW VERIFICATION IMPLIED.**  
**Authority:** [Latest approved product and quality-session requirements](engine15-primary-plan-quality-requirements-20261105.md), followed by latest explicit user direction, approved Trackside Figma, current QA and earlier safety handoffs. Older Engine-1.0-default instructions are historical and superseded for product intent.

**Repository:** `Isshhin/stridelab-development`. Reviewed branch `fix/trackside-usability-garmin-20261105` at `646f353bc09511dfaa6dcb9c629c034dcf68fc54`. Engine 1.5 PR #37 remains open/draft/unmerged; Engine 2.x stays untouched.

## Development operating rules

1. **One bounded correction per commit.** Start each task by verifying live branch head, preserving newer changes, reading affected implementation/tests/spec. Do not restart Engine 1.5 or replace working components.
2. Establish the intended behavior and a **failing focused test** before editing, then make the smallest correction; never change safety tests merely to obtain green status.
3. Verify targeted JVM/Compose tests, then the appropriate broader tests and the exact-head Windows MARC workflow. Document actual pass/fail/skipped and source SHA, not aspirational status.
4. Change the next item only after the current item's acceptance criteria pass. Update QA known defects, regression index, source-specific handoff, and functional behavior register with truthful evidence.
5. Do not use Codex/Work unless a task proves impractical manually and the user authorizes it. Use existing GitHub connection and MARC runner. Do not merge PRs, publish a release, replace signing keys, uninstall/clear data, or modify Engine 2.x.
6. Preserve approved Trackside UI; no redesign beyond minimal required interactions and truthful review copy.
7. A signed debug artifact with a different installed-app signer **must not** be installed over the existing data-bearing app.

## Priority-ordered execution — one item at a time

| Order | Priority | Work item | Implementation/test boundary and acceptance | Current status |
|---|---|---|---|---|
| 0 | Gate | Establish contract and reproducible fixtures | Requirements linked above, record live SHA, preserve baseline and define one eligible 60–70 km, 5-running-day, 1-Threshold, adequate-time journey plus safety and multi-discipline fixtures. No behavior change. | Product requirements documented; new exact-end tests pending |
| 1 | P1 | Correct default Engine 1.5 routing | `build.gradle`, `TracksideCoaching`, primary builder/JVM test, workflow. No-override production route generates Engine 1.5 version/policy; **separate explicit** Engine 1.0 fallback preserves existing snapshots. Do not silently retry a failed plan through another engine. | BLOCKED/MISMATCH: currently Engine 1.0 default |
| 2 | P1 | Fix quality-request state integrity | `PlanBuilder`, Running Setup, Advanced Running, `WeeklyPlacement`. 0/1/2 count, selected types, chosen days survive navigation/back, serialization, generation and offline reopen; changing count synchronizes quality days or clearly requires reassignment. No invisible reset. | NOT VERIFIED; source risks below |
| 3 | P1 | Align UI with safe Engine 1.5 quality gates | Eliminate the 30-vs-40 km two-quality mismatch without weakening conservative policy; handle 0–20 km, beginner, 20–35 minute budget, return, deload, taper and multi-discipline spacing. UI labels cannot promise unschedulable quality. | CONFIRMED code/policy discrepancy |
| 4 | P1 | Honest quality-type prescriptions | Ensure the selected running discipline goal is used; supported Threshold/Tempo/etc inputs yield coherent structured workouts when safe. Unsupported choices must be labelled advisory/unsupported rather than silently treated as implemented. Scope any new workout structure as a reviewed minimal integration correction; no unreviewed physiological algorithm expansion. | CONFIRMED type-usage gap |
| 5 | P1 | Requested-versus-generated draft review | On saved draft, show actual Engine 1.5 version/policy, weekly run volume, requested and generated quality count/type/day/time and exact per-session reason for downgrade/move/withholding. Infeasible plan must block with actionable message; approval explicit. Use accepted Figma patterns. | NOT IMPLEMENTED comprehensively |
| 6 | P1 | End-to-end plan-content acceptance | Production route: eligible runner requests one Threshold, generate → inspect actual structured quality → approve → close/reopen offline; also test two-quality eligible cases, mixed sessions and safety-substituted weeks. Assert actual prescriptions and saved IDs, not just total session count. | NOT RUN against revised default |
| 7 | P2 | Verify multi-discipline weekly scheduling | Mixed 5R+2S+2C / six days, max two/day, daily budget, no double lower-body/high-stress interference, long-day and quality-day pins, availability edits, safe relocation and explicit unresolved sessions. Preserve historical sessions/locks. | Backend/JVM/UI test evidence exists; integrated end-to-end gaps remain |
| 8 | P2 | Verify Engine 1.5 feedback presentation and decisions | Live Trackside Manage + Workout: preview is nonmutating, stage separate from accept/dismiss; stale revisions rejected; accepted shorter run displays **time-only** target, no obsolete distance/intervals; rest 0 min with historical original intact; restart/account boundary. | Backend/UI components exist; full production-route phone acceptance pending |
| 9 | P2 | Full workflow reliability and audit closure | Account switch, offline/outbox, backup/restore, corrupted state, old Engine 1.0 snapshots, manual locks and missed sessions. Reconcile 35/41 detailed behavior rows marked NOT RUN with explicit per-control evidence, never fabricated PASS. | Many automated unit/device checks passed historically; full current contract not closed |
| 10 | P2 | Physical-phone interaction and visual acceptance | Day/agenda, move/review, Activity bottom sheet over map, scrolling, Back, 360/412dp fonts, keyboard, Figma parity. Test with safe signer-compatible installation or independent device/profile only. | Phone acceptance pending |
| 11 | Separate / later | Finish Garmin and file-import integration | Canonical Garmin history persistence + live Personal API when owner key available, end-to-end FIT picker/history, TCX/GPX as separately scoped features; preserve provenance and no automatic coaching mutation. | Foundations only; not Engine 1.5 activation gate |
| 12 | Deferred | Advanced physiological adaptivity, major UI redesign | HRV/heat/matched-HR/personalized readiness/7-run support/new progression intelligence belong in Engine 2.x or a separately approved scope. Do not expand Engine 1.5's frozen policy accidentally. | Deliberately out of scope |

## Additional findings from second source audit

These are **new inspection candidates** beyond the previously registered Engine-default, quality policy, and QA-summary issues. A code-level risk is not counted as reproduced phone failure without a focused test.

- **N1: Quality chips may misrepresent saved values.** `PlanBuilder.advancedQualityCsv` displays Threshold and Tempo selected when `initial.allowedQualityTypes` is empty, but the underlying serialized `PlanRequest.allowedQualityTypes` remains empty until the user toggles a chip. Reproduce in a builder state test before fixing.
- **N2: Advanced quality count does not synchronize requested weekdays.** The Advanced Running `onQualityCount` callback updates `qualityCount` only; `qualityDays` and `weeklySlots` are not updated together. Check whether a subsequent Running Setup/manual Continue produces an unassigned slot, blocks later generation, or loses intent.
- **N3: Manual/automatic intent can be ambiguous.** `runningCustom` starts `true` even if `weeklySlots["run"]` is absent, and Continue can save default suggested weekdays as explicit pins. Distinguish truly athlete-pinned days from coach-suggested days; add state/restore tests.
- **N4: Requested quality type may not affect engine output.** `PlanRequest.preferredQuality` and `allowedQualityTypes` exist but `Engine15.structure(request.goal)` is driven by the overall goal, not these preferences or `disciplineGoals["run"]`. Test a mixed primary-strength + secondary-running goal to verify correct running prescription.
- **N5: Historical "not wired" presentation documentation is stale.** Current `WorkoutScreen`, `Engine15ScheduleReview`, and `TracksideManagePlanScreen` do reference `Engine15SessionPresentation` and recommendation actions. **Do not reimplement the backend/UI blindly**; verify actual production routes, accepted rest/time-only display and Figma fidelity.
- **N6: Review copy is generic in suppression cases.** Engine 1.5 currently explains withheld quality with a combined list of possible mileage/time/deload/return/taper causes. Require one or more **actual** reasons for each affected session and week.
- **N7: Guided race-result entry is not part of current PlanRequest.** Running Ability UI itself says race/time-trial results are not stored. Record as a known limitation, not a failure of current Engine 1.5 physics; decide any expanded input contract separately.
- **N8: Six-run frequency ceiling is a deliberate current policy.** Seven running days are explicitly rejected in the B7 scenario matrix. Do not claim seven-run support or silently reduce a seven-day request.

## Current implemented foundation to preserve, not rewrite

- Deterministic versioned Engine 1.5 generation, conservative running volume caps/deloads/return and safety-limited quality.
- Mixed-discipline generation, 2/day capacity and interference-aware pairing (Engine 1.5-specific rules), selected long/quality days and time budgets.
- Individual locks, reviewed availability relocation/unassigned conflicts, edited-plan integrity, immutable historical activity/logs, Engine 1.0 compatibility.
- Completion/missed evidence-driven recommendation preview, explicit stage/accept/dismiss, stale-evidence and revision rejection, secure account-scoped repository/outbox/backup.
- Active-session time-only/rest projection already referenced in Trackside screens, plus tested Activity sheet and running weekday-selection corrections.
- Existing JVM/Android regression suites and MARC exact-head evidence as historical safeguards, **not** blanket acceptance of revised product.

## Completion rule for each manual task

A task is DONE only when: (1) acceptance criteria were met by source inspection and executed named tests; (2) exact source SHA and results are recorded; (3) previous unaffected engine/repository behaviors remain intact; (4) associated QA defect(s) are updated; and (5) no unrelated feature or branch was changed.

**Next actionable task:** Order 0: add a strict production-route Engine 1.5/quality fixture to the test plan; then Order 1: correct the packaged default and separate rollback assertions. Do not flip only the flag and call the integration finished.
