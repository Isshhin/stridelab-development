# StrideLab Trackside 3.0 — Current Engine 1.5 and Plan-Quality Requirements

**Approved product requirements: 2026-11-05. Implementation / acceptance: PENDING.**

**Authority:** This is the user's latest explicit instruction for the integrated Trackside 3.0 product. It supersedes older documentation that kept Engine 1.0 as the **default** while Engine 1.5 was an isolated candidate. Older handoffs, code, test expectations, CI reports and signed APKs remain historical evidence of what was actually tested, not acceptance of this updated target.

**Repository:** `Isshhin/stridelab-development`. Reviewed QA branch: `fix/trackside-usability-garmin-20261105`, starting head `d560a6e75774a1cacf253962204c8596b03478a5`. Initial integrated baseline: `9d3c559e4b92a366a3b52934b19ea37fb0e65084`.

## R1 — Engine selection and rollback

1. **Engine 1.5 is the primary/default coaching engine for newly generated Trackside plans.** A normal unqualified production Plan Builder request must select Engine 1.5, not Engine 1.0.
2. **Engine 1.0 is a controlled fallback/rollback engine** and remains compatible with its own existing plans. Fallback must be explicit, testable, safe and visible. Do not silently retry failed Engine 1.5 requests using Engine 1.0 or relabel a plan.
3. Saved drafts, approved prescriptions, sessions, history, logs, owner IDs, policy/version IDs, dates and activity links must remain unchanged except for explicitly approved edits. No migration or regeneration merely because the packaged default changed.
4. Preserve the frozen Engine 1.5 safety constraints and review-before-apply behavior; Engine 2.x research remains independent.

**Present mismatch:** `native-android/app/build.gradle` sets `ENGINE15_ACTIVE` default to `false`; `TracksideCoachingIntegrationTest.packagedDefaultSelectsTheConfiguredPolicy` asserts the Engine 1.0 default; the primary builder Android test asserts Engine 1.0. These are historical test expectations that must be changed, with a **separate** Engine 1.0 rollback test retained.

## R2 — Requested quality sessions and workout type

1. Plan Builder must distinctly capture the requested quality **count** (0/1/2 where safe), the requested quality **type(s)** and preferred/pinned day(s), retaining them through UI state, serialized `PlanRequest`, Engine 1.5, saved draft, approval and offline reopen.
2. A performance goal does not by itself prove the user requested zero quality sessions. Initial `qualityCount=0` is currently possible: the UI must make the choice and consequences transparent, and a user-selected nonzero request must not reset to zero invisibly.
3. When eligible, one requested **Threshold** workout must generate a genuine appropriately structured quality session; the same fidelity applies to any supported Tempo, Intervals, Hills, Fartlek or Progression option. The app must not advertise a type as enforceable if the active engine ignores it. Unsupported types must be clearly labelled *advisory/unsupported* rather than presented as guaranteed.
4. Quality prescriptions must have coherent title, training purpose, warm-up, work/recovery intervals, cool-down, time and dates. Do not invent measured heart-rate thresholds, physiological scores or performance data.
5. Preserve conservative gates (experience, recent mileage, duration, returning status, deload, taper, recovery spacing, multi-discipline constraints). These can justify withholding or reducing a requested quality workout **with explicit user-facing explanation**. Do not simply force quality into every week.

**Present mismatches:** `PlanBuilder.kt` can initialize zero quality sessions; `TracksideFigmaAdvancedRunning.kt` calls quality type selection advisory; `Engine15.structure` selects by overall goal rather than requested quality type; UI enables two quality sessions at 30 km/week while Engine 1.5 limits to one below 40 km/week; `Engine15` can convert quality to easy according to existing gates.

## R3 — Requested versus generated plan review

1. The actual saved draft must show **Engine 1.5** identity and the **requested vs generated** quality counts/types, dates, total weekly volume and duration when materially different.
2. If requested quality becomes easy, disappears in a deload week, changes type, cannot fit recovery constraints or is reduced by mileage/time limits, provide the **specific applicable reason for that session/week**, not a generic explanation detached from the change.
3. Invalid/infeasible configurations must block generation or require review; no session, selected weekday or discipline may be silently discarded.
4. Draft approval remains separate and explicit. A generated plan is not automatically accepted or pushed to devices.

## R4 — Preserve Trackside UI approvals and integrations

- Maintain the latest approved Trackside 3.0 Figma file `v73TsYo6gKxUoiUock2sSE`, Running Weekly Setup session ownership/move semantics, Activity Detail draggable sheet, accessibility, responsive behavior, and current offline/persistence boundaries.
- Do not invent unapproved 1:1 Figma screens. Use approved interaction patterns for explanations or seek design approval before changing screen layouts.
- Keep Garmin/FIT/TCX/GPX and Intervals.icu independent. Garmin is not considered fully imported until results persist to canonical training history.
- No release, merge, phone uninstall/data clear, signing-key substitution or activation of Engine 2.x as part of this requirements change.

## R5 — Mandatory acceptance gates (ALL PENDING)

| Gate | Required outcome |
|---|---|
| Production default | Without a Gradle override, a complete production-route Builder -> generated draft -> approval -> offline reopen records Engine 1.5 version/policy. |
| Deliberate rollback | Explicit rollback selects Engine 1.0 only for new eligible drafts and retains existing Engine 1.0/1.5 plans unchanged. No silent fallback. |
| Eligible quality | Experienced runner at 60–70 km/week, 4–6 weekly runs, adequate workout time, requests one Threshold session. Eligible normal weeks contain a correctly dated, structured quality workout after approval and offline reopen. |
| Workout types | All visible quality-style choices map to genuine supported prescriptions or are clearly marked advisory/unsupported. Do not substitute without disclosure. |
| Frequency | Request 0/1/2 quality sessions; reconcile UI 30 km vs engine 40 km gates and enforce existing conservative policy. |
| Safety boundary | Test 0/12/18/35/45/65 km, beginner/experienced, returning athlete, fourth-week deload, taper, 20–40 minute budgets. Show exact per-week quality count and reasons for each suppressed/replaced quality workout. |
| Scheduling | Quality/long/easy slot identity, pinned weekdays, manual/automatic, strength/cali/conditioning interference and double-day constraints preserve all valid requested sessions or clearly reject conflicts. |
| Persistence | Account-scoped versions, drafts, backups, acceptance/rejection, activity links, completed logs, rollback and offline reopen retain original prescription and ownership. |
| Visual and phone | Targeted Compose tests, full JVM/instrumentation, all four responsive profiles, approved Figma comparison, and independent physical-phone acceptance. |

A green build or count-only integration test is **not** sufficient. Update tests to assert the actual quality prescription and active version. Never weaken safety invariants to make tests pass.

## R6 — Current QA and status bookkeeping

- Historical Windows QA at `261faba877de278c82da0410a976a66ff6e29834` was green for its **Engine 1.0-default implementation**. It remains valid historical evidence but **does not accept this Engine 1.5-primary requirement**.
- `TracksideEngineBuilderIntegrationTest` currently checks successful generation, session count, approval and reopen, not quality-session content. It must gain targeted assertions.
- The detailed functional behavior audit still contains many `NOT RUN` rows; these must be reconciled with actual per-behavior evidence and cannot be labelled PASS merely because broad suites passed.
- Known gaps remain: Garmin history persistence and live key, incomplete FIT user-facing import, absent TCX/GPX, physical-phone verification and signed APK update incompatibility.
- Update `docs/qa/known-defects.md`, `regression-coverage.md`, `feature-inventory.md`, `current-handoff.md` and later QA results with exact SHA and acceptance status when implementation work occurs.

**Completion rule:** This specification is APPROVED, but implementation is **NOT COMPLETE**, and no corrected exact-head Engine 1.5-primary verification has passed. Code changes and acceptance testing must follow as a separate work item.
