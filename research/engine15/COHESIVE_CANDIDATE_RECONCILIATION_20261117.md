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

## First scenario finding

The realistic six-day 75→80 km, Thursday-unavailable case now exercises Saturday LONG and Sunday EASY while retaining the current 8-week recovery series and exact weekly totals. Current M5A's 30% preferred long-run share yields 20.2 km in its first 67.5 km week; this narrowly exceeds the requested 18–20 km preference. The test must record that as an unmet preference rather than change a policy silently or claim the request was met. This is a real allocation trade-off for the next policy audit. No session duration cap was introduced.

## Batch log

### Batch 1 — source reconciliation and alternate long-day regression

- Starting branch/head: new isolated `research/engine15-cohesive-candidate-20261117` at `cf209b710268f116feac96154f53cb17779dbf73`.
- Change: added this report; added the Saturday-long/Sunday-easy 75→80 km case to `Engine15I9ReadOnlyBasePlanAssemblyTest`.
- Verification: JBR 21.0.11, Gradle 8.13, `:app:testDebugUnitTest --tests '*Engine15I9ReadOnlyBasePlanAssemblyTest' --no-daemon --max-workers=2` passed **10/10**, zero skipped/failures. An initial run passed the nine existing methods but exposed that a strict 18–20 km expectation conflicts with M5A's 30% rule (observed 20.2 km). The final test records that exact output and verifies Saturday placement, Sunday easy, Thursday rest, weekly totals, and non-executable authority flags.
- Regressions addressed: corrected a test assumption; no planner or production code changed.
- Risks: quality eligibility and template variety remain incomplete; no athlete evidence or external review has been authenticated.
- Next: audit M2 threshold/tempo selection and structure; add realistic 3×3 km/longer-threshold and background/history cases, then add versioned families only where evidence permits.
