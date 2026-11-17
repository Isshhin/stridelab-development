# Engine 1.5 cohesive candidate reconciliation — 2026-11-17

## Scope and verified repository state

This reconciliation uses source at `cf209b710268f116feac96154f53cb17779dbf73` (I9 PR #129), current remote refs, and the open pull request metadata read on 2026-11-17. The primary `C:\Users\marcs\StudioProjects\stridelab-development` checkout had staged, unstaged, and untracked owner changes, including Engine 1.5 source and `AGENTS.md`; no files there were edited. This work is isolated in a new worktree on `research/engine15-cohesive-candidate-20261117`, branched at the exact I9 head. No history was merged, rebased, or overwritten.

Remote heads at inspection: `dev=f66ac0232bc3dc757163ab2ddc50ab9dd7c2be6a`, `main=49824dc1c0e4c4825398f76ac5f5ea71bd145ba5`. Both are unchanged by this task. Relevant open draft PRs: #129 I9 `cf209b710268f116feac96154f53cb17779dbf73` → I8 #128 `2363560d3e0751a0a3a9f156df32e8c5ba52d52f`; #128 → I7 `54f88f128807aa3be622b646d9054051b4f5a165`; M10.6 #94 `818cdc51df5dd721e68fa4cfd17753edbfd86ea7` → M10.2 #93 `9122fa8655d890531b372f61c77b1e23ead663a7`; coach review UI #95 `1b3bb1570691fd1e01c87198254d6f7c7c77944f` → #94; full acceptance #96 `814be35a260039be07f76268de934a53d6b13407` → `dev`. All are open drafts. #129's two current Actions checks failed before meaningful test evidence; the I9 preflight itself says its source/tests still require local execution.

## Reconciled tracks

| Concern | M10/M10.6 Android candidate | I1–I9 independent research | Integration decision |
|---|---|---|---|
| Production plan creation | `Engine15.kt` creates real `PlanSnapshot` drafts behind the existing engine selection/rollback and approval flow. | No live generator call; outputs remain typed research results. | Keep rollback, plan persistence, and production generation untouched. Do not feed I results into `PlanSnapshot`. |
| Running-week mileage and roles | M10.6 adds history-informed easy/secondary/long/quality role allocation and recovery behavior in the existing generator. Policies are provisional. | M4/M5/M6 independently create bounded weekly budgets, exact distance sessions, long-run roles, dated slots, quality-spacing opportunities, interference checks, and periodization audits. I9 assembles these into a read-only week/day preview. | Two policy owners exist. Preserve both for comparison; do not combine their rules or silently replace M10.6. Make the independent path the research candidate and record disagreements. |
| Quality workout generation | The live candidate has versioned distance segments and threshold-work evidence bounded by recorded exposure; workout detail and review controls exist in PR #95. M10.6 docs say source work is self-reported, not physiological threshold validation. | M2 has versioned threshold/tempo examples and an exposure-informed timed selector; M3–I8 add history, source-lap consistency, HR/pace plausibility, calibration review and artifact integrity. M5/M9 compose/reconcile quality previews, but authority and source evidence remain caller assertions or false flags. I9 refuses every nonempty quality request. | Audit and improve the research generator behind a separate result type. Permit review previews where safe; keep the I9 base preview explicit and defer rather than downgrade quality. Never claim calibration or dose authority from syntactic consistency. |
| Calendar and discipline interference | Existing Android planning/review surfaces handle approved plans, existing sessions, and weekly review. | M6B/C review spacing, dates and cross-discipline sessions from caller-supplied calendar evidence; I9 retains external-session counts but marks the calendar unauthenticated. | Reuse M6 review outputs. Missing/incomplete calendar remains a hold; never fabricate a conflict-free week. |
| UI | `PlanBuilder` has an existing read-only research preview panel and saved review affordances in the M10 integration line. | I9 has no Android screen or UI wiring. | Do not add a second planning interface. Any later typed adapter must target the existing research panel and preserve no-approval/no-save semantics. |
| Data and permissions | M10 code has persistence/owner isolation, backups, offline behavior, and explicit approval paths. | I1–I9 do not create plans, store provenance as verified, write activity data, or grant production authority. | Do not mix ownership models or serialize research previews as approved plans. Keep Garmin/FIT records immutable. |

## Current blockers and API seams

1. I9 accepts only `Engine15IndependentVolumePlanner.Result` plus `Engine15M6CReviewInput`; it does not consume M10.6's `PlanRequest`, history assessment, feedback, or actual imported history. Its caller-supplied baseline, external-session list, review references, and verification claims are not authenticated.
2. M4–M6 and M10.6 make overlapping but not identical progression, long-run, recovery, and workload decisions. There is no validated mapping from M10.6's accepted output back into the independent typed source without rebuilding policy. The correct seam is a read-only comparison/report, not mutual delegation.
3. M2 timed workout totals can conflict with the assigned distance budget. M9a reconciliation relies on asserted per-segment distance; it is arithmetic consistency, not pace or intensity evidence. No reliable, individualized conversion currently justifies adding those kilometers to base distance.
4. Source identity/lap durations, I4 HR signal checks, I6 pace consistency, I7 calibration claims, and I8 artifact digests do not authenticate original provider ownership, measured protocol, threshold validity, or training tolerance. All current flags correctly stop short of verified coaching authority.
5. A stable deterministic core exists, but its end-to-end entry point has only one Saturday-long scenario under development. Missing-history, duplicate cross-provider identity, incomplete sync, difficult response, user time constraint, and workout distance conflicts need one matrix over the assembled typed path.
6. M10.1/M10.2 previously recorded local build/emulator evidence on their exact heads; that evidence does not validate the present I9 head. PR #129 checks currently fail before a useful hosted run. Exact-head local JVM/build work is required here.

## Chosen integration path

Continue on this isolated research branch based on the exact I9 history. Complete the research-only quality selector and multiweek scenario matrix first, then integrate the output into the *existing* read-only research panel through a narrowly typed mapping only if its current storage/review contracts can preserve research-only status. Do not stack I1–I9 source directly into the M10.6 live generator, change a production flag, change saved-plan data, or duplicate the UI. Keep Engine 1.0 rollback available. Existing M10 candidate remains a comparison baseline, not the quality-policy authority.

## Candidate family eligibility policy

- **Safe to display as research preview:** structurally valid, versioned timed/distance examples with explicit targets labeled as examples, exact work/recovery totals, and no athlete-specific eligibility claim. Missing athlete history must be stated as unknown. A user-entered time limit can constrain a preview; missing time evidence must not be converted into an arbitrary short cap.
- **Requires external review:** candidate whose family/dose needs contextual coach judgement (including proposed threshold/tempo progression, two quality sessions, or an asserted schedule/cross-discipline interaction). Keep external review reference and caller assertion visibly distinct from verified authority.
- **Defer:** requested workout lacks sufficient identity, usable source-linked repeated exposure, a conflict-free recovery/calendar assessment, internally consistent work/recovery structure, or feasible base-distance reconciliation. Do not substitute EASY, invent pace/HR, or silently omit the request.
- **Executable approval:** disabled for this candidate. It requires separately verified source and athlete-specific intensity/dose authority, complete workload/recovery controls, independent review, owner approval, and a separate authorized implementation. None of I1–I9 grants this authority.

## Quality-generator audit and current family disposition

### M10.6 candidate

`Engine15RunningProgram.quality` makes weekly quality count conditional on experience, reported mileage, recovery week, taper, return status, requested count, and reported recent frequency. It spaces sessions away from long/lower-body sessions, selects goal/phase-dependent interval counts, budgets work against original session/week volume, and rechecks work share after mixed warm-up/cool-down conversion. Supported family labels include Threshold, Tempo, Intervals, Hills, Fartlek, and Progression; there is no separate VO₂max or Strides library. Prescriptions use distance work segments and planning-pace warm-up/cool-down estimates, not measured threshold HR/pace. Policies remain candidate heuristics, not injury limits.

`Engine15RunHistory` derives family-specific exposure from two or more completed athlete-entered workout logs in separate recent weeks, with logged effort, completion difficulty, and discomfort checks. These logs are not source-authenticated interval measurements. The user-facing explanation and internal accessor previously said “VERIFIED”; batch 2 changes them to “REPORTED” and explicitly states that logs are not source-authenticated. The numeric policy and selection behavior do not change.

### Independent M2/M5/M9 candidate

- M2 generates threshold intervals and continuous tempo from caller-supplied recent work, a separate work ceiling, explicit allowance for any increase, quality authorization, and an optional session-time limit. M9b additionally needs two distinct recent same-family work reports, complete-week assertions, tolerated response, session/week ceilings and spacing. These remain caller assertions, not proof of source, owner, intensity, or tolerance.
- Previously, `availableSessionSeconds` was mandatory and tempo stopped at 40 minutes even when reported and reviewed work supported more. M2/M9b now allow a missing user-entered cap as `null`; explicit zero still defers and any explicit limit constrains selection. Continuous tempo enumerates in one-minute increments up to the lower of reported work and reviewed ceiling. Threshold intervals allow 3–12 minute repeats and emit recovery only between work reps. Same-family dose evidence and reviewed ceilings still bind.
- Added versioned research structure `engine15-threshold-long_repetitions-v1`: 2 km easy warm-up, 3 × 3 km threshold, 90-second easy recoveries between work reps, and 2 km easy cool-down. This is an illustrative mixed-unit preview: elapsed duration and exact total distance are unknown because recovery distance is unmeasured. It is not auto-selected, does not enter M9a reconciliation, and has no executable/production permission. The current timed reconciler correctly defers mixed-unit structures until there is an exact segment-distance contract.

### Family disposition

| Class | Current disposition |
|---|---|
| Safe-to-display research preview | Versioned threshold/tempo structures with exact represented work/recovery and `HEURISTIC` labels. Show reported exposure and an entered time cap separately. This does not establish athlete eligibility. The 3 × 3 km structure is illustrative only; its total distance remains unknown until recovery distance is resolved. |
| Requires external review | Any athlete-specific selection, dose increase, two-quality-session week, or threshold/tempo candidate based on self-reported logs. Review references remain caller assertions. |
| Must defer | Missing/zero usable same-family exposure, missing reviewed ceiling or quality permission, recovery/return hold, unscheduled or too-close placement, explicit user time-limit conflict, incomplete history when evidence is required, or mixed-unit mileage without segment distances. Never silently downgrade a requested workout. |
| Executable approval | Disabled. I1–I9 do not establish this for any family. |

Continuous tempo and basic threshold intervals can only advance as previews within repeated same-family reported exposure and reviewed ceilings. **Progression tempo, Fartlek, VO₂max intervals, strides and hills cannot progress beyond illustrative preview/defer status in the independent candidate yet**: family-specific selection/tolerance rules and exact weekly-distance handling have not been independently qualified. M10's existing Hill/Fartlek labels do not establish those gates. Missing user time input adds no time cap.

## First scenario finding

The realistic six-day 75→80 km, Thursday-unavailable case now exercises Saturday LONG and Sunday EASY while retaining the current 8-week recovery series and exact weekly totals. Current M5A's 30% preferred long-run share yields 20.2 km in its first 67.5 km week; this narrowly exceeds the requested 18–20 km preference. The test must record that as an unmet preference rather than change a policy silently or claim the request was met. This is a real allocation trade-off for the next policy audit. No session duration cap was introduced.

## Batch log

### Batch 1 — source reconciliation and alternate long-day regression

- Starting branch/head: new isolated `research/engine15-cohesive-candidate-20261117` at `cf209b710268f116feac96154f53cb17779dbf73`; batch commit `1abefa11`.
- Change: added this report; added the Saturday-long/Sunday-easy 75→80 km case to `Engine15I9ReadOnlyBasePlanAssemblyTest`.
- Verification: JBR 21.0.11, Gradle 8.13, `:app:testDebugUnitTest --tests '*Engine15I9ReadOnlyBasePlanAssemblyTest' --no-daemon --max-workers=2` passed **10/10**, zero skipped/failures. An initial run passed the nine existing methods but exposed that a strict 18–20 km expectation conflicts with M5A's 30% rule (observed 20.2 km). The final test records that exact output and verifies Saturday placement, Sunday easy, Thursday rest, weekly totals, and non-executable authority flags.
- Regressions addressed: corrected a test assumption; no planner or production code changed.
- Risks: quality eligibility and template variety remain incomplete; no athlete evidence or external review has been authenticated.
- Next: build broader cross-background threshold/tempo selection scenarios and map the independent preview into the existing research UI contract without adding another interface.

### Batch 2 — explicit time limits, longer tempo and threshold structure

- Starting branch/SHA: `research/engine15-cohesive-candidate-20261117`, `1abefa11`.
- Implementation commit: `b2832568` (`feat(engine15): harden shadow quality previews`).
- Change: nullable optional user time limit in M2/M9b; evidence-bounded continuous tempo range (removes fixed 40-minute cap); 3–12 minute interval support; versioned 3×3 km/90-second threshold research structure; athlete-entered M10 history labels/accessors corrected from “verified” to “reported”. No dose values, production switch, saved plan, or persistence format changed.
- Test-first evidence: unknown time limit first failed because the constructor required a value; 48-minute continuous tempo then failed at the old 40-minute cap; report-terminology tests initially failed to compile because `reportedWorkKm` did not exist. All now pass after the minimal changes.
- Verification: JBR 21.0.11 / Gradle 8.13, focused nine-class set covering M2, M5, M9b, M9a, I3, research scenario runner, and M10 Q3 history: **98 tests, zero failures/errors/skips**. `git diff --check` passed.
- Regressions addressed: no user-entered limit is treated as an arbitrary one-hour cap; history is no longer described as source-verified.
- Risks: source and athlete tolerance remain unverified; the mixed-unit 3×3 km pattern cannot reconcile exact day/week distance; unsupported families remain deferred. Full repository suite/build and persistence/rollback checks remain pending.
- Next: add wider background/history scenarios and an exact mixed time/distance segment reconciliation contract, then connect only to the existing read-only Android research panel if it preserves every non-executable boundary.

### Batch 3 — Saturday long-run quality placement boundary

- Starting branch/SHA: `research/engine15-cohesive-candidate-20261117`, `1d3151882a5b87e2d6141c81056cb9a82685d4cf`.
- Change: generalized the integrated shadow test fixture's long day, then added the requested 75→80 km, six-day, Thursday-rest, Saturday-long/Sunday-easy quality opportunity scenario. Added a two-quality-in-one-week rejection assertion. No planner or policy implementation changed.
- Verification: JBR 21.0.11 / Gradle 8.13, `:app:testDebugUnitTest --tests 'io.stridelab.privatealpha.Engine15I1IntegratedShadowMatrixTest' --tests 'io.stridelab.privatealpha.Engine15I9ReadOnlyBasePlanAssemblyTest' --no-daemon --max-workers=2`: **17 tests passed**, zero failures/errors/skips. An initial two-quality acceptance probe returned M5B's existing `MULTIPLE_QUALITY_REQUESTS_IN_WEEK` deferral; the final positive case uses one session and locks the deferral into a negative test.
- Regressions addressed: integrated calendar invariants no longer hard-code Sunday as long day; no production behavior changed.
- Risks: current independent candidate cannot meet a same-week two-session request; M5B has not established safe/verified frequency or dose accumulation for that case. One quality opportunity in a compatible Monday slot remains unscheduled and non-executable. Saturday-long first-week distance is 20.2 km versus the 20.0 km ideal.
- Next: strengthen cross-background and missing-history selection/defer scenarios, then run the candidate's broad JVM and Android compile checks; do not implement multi-session eligibility without distinct frequency and cumulative-dose evidence.

### Batch 4 — reported quality exposure terminology

- Starting branch/SHA: `research/engine15-cohesive-candidate-20261117`, `b82109d48eca740b2f7685b19aceff103f32f78d`.
- Change: renamed the M2 input `confirmedRecentWorkSeconds` to `reportedRecentWorkSeconds` across its I3/M9 use and all Kotlin callers/tests. Output metadata now says `CALLER_REPORTED_QUALITY_WORK_EXPOSURE`; added a regression assertion and a durable AGENTS rule against equating caller reports with source evidence. This changes an internal Kotlin API only; no JSON, database or saved-plan format changed.
- Verification: test-first provenance assertion failed before implementation. Afterwards JBR 21.0.11 / Gradle 8.13 compiled main and test sources and passed `IndependentQualityFitnessAdapterTest`, `Engine15I3SourceLinkedQualityDurationTest`, `IndependentQualityDoseProgressionTest`: **40 tests passed**, zero failures/errors/skips. `git diff --check` passed; `rg` found no remaining old input/rule names in source/test trees.
- Regressions addressed: removed a misleading “confirmed” label at the selector boundary; preserved M3's explicit prohibition against auto-filling from its unverified source assertions.
- Risks: work ceilings, increments, family claims and source bindings remain caller assertions; terminology does not strengthen evidence. Broader JVM/build and persistence/offline checks remain pending.
- Next: broader background/history QA followed by full repository verification; no production activation.
