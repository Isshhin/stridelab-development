# StrideLab — Agent instructions and current engineering state

**Updated 2026-11-21.** Scope: GitHub `Isshhin/stridelab-development`. These are active development instructions, not a release certification. This file replaces superseded historical status claims; detailed experiments belong in `ENGINE15_EXECUTION.md`, QA documents and their original PRs.

## Current development state — 2026-11-21

**Treat the checked-out source, exact branch and owner approval as authoritative.** A draft PR, local worktree, or reported QA is not automatically an integrated or accepted release.

### Branch and feature status

- **`main`** remains the last owner-approved stable release. **`dev`** is the integration branch; draft stacked feature PRs and local worktrees are not automatically part of `dev`. Promotion still requires explicit owner acceptance and reviewed `dev → main` PR.
- Owner's active Phase 1 working copy (reported 2026-11-21): `C:/Users/marcs/stridelab-garmin-qa`, branch `feat/garmin-coach-ui-20261120`. It reportedly includes PR #166 (modern More/Running Analytics, backup, manual-log UI), PR #167 (discipline-aware Strength/conditioning Activity views), PR #169 (Garmin Section 1), and a new **Running dashboard**. **This branch was not found as a remote GitHub ref at documentation update time; its source, HEAD, dirty state, and exact integrated behavior must be checked locally before edits.** Do not pretend that these changes are already on `dev` or `main`.
- PR #165 incorporated the modern Android QA stack and **inactive** experimental Engine 1.5 source into `dev`; `native-android/app/build.gradle` on `dev` defaults `ENGINE15_ACTIVE=false` and `ENGINE15_INDEPENDENT_RUNNING_ACTIVE=false`. Release and isolated `.qa` builds force both off. Do not infer activation from source presence or other experimental branch defaults.
- **Engine 1.0 normal generation fallback is retired.** `Engine15IndependentPlanGenerator` is the intended independent Engine 1.5 static plan generator. There may still be historical legacy adapters/config paths; they are not permission to restore Engine 1.0 policy or fallback. Running-first multi-discipline static generation and eight-week Tempo/Threshold candidates exist in experimental work, but quality dosing/progression, complete Android/manual acceptance, and production cutover are not settled. See `ENGINE15_EXECUTION.md`, relevant candidate PRs (#140/#141), and the checked-out source.
- **Engine 1.5** handles static plan generation, periodization, constraints, scheduling, explicit review and versioned prescriptions. **Engine 2.x** is reserved for future automatic adaptation to performed/missed training and recovery/readiness; do not implement it as a Phase 1 side effect. **Exercise Library 2.0 is removed from the current feature roadmap**; do not revive it. Historical source or references may remain and must not be blindly deleted.
- **Garmin Section 1, draft PR #169:** actual source-read Garmin sport-specific HR-zone parsing, selected Garmin/manual zones, conservative FIT workout-step/lap recognition and performed-run classification, manual corrections separate from immutable source records, and descriptive planned-vs-performed adherence on linked activities. Reported isolated Pixel 9 API 35 QA at `f3ea569c`: **1,150 JVM passed / 3 skipped** and **275 Android passed / 14 skipped**. **Live authorized Garmin API verification, manual visuals, some real-FIT edge cases and final merge/release acceptance remain open.** Do not call this fully production-verified.
- **Existing analytics and offline non-engine work:** the QA lineage includes recorded running-history aggregates, comparison/filtering, source-backed pace bests, filtered history navigation, Share Studio, offline maps, owner-scoped local ZIP backup/restore, and signed-out manual workout logging. PR #166 modernizes existing More subpage visuals; PR #167 provides duration/HR-first non-running Activity Detail without inventing distance, GPS, or strength sets. Verify current branch placement and tests before describing any feature as live.
- **Current limitations:** StrideLab is not confirmed to push prescribed workouts to Garmin watches and does not provide a complete in-app run recorder. Do not make running analytics depend on either. Retain Garmin guest access, encrypted local owner credentials, offline activity data, account isolation, and FIT/GPS source fidelity.

### Phase 1 — approved Running dashboard scope (owner decision 2026-11-21)

**Target when working in the owner's local environment:** continue from `C:/Users/marcs/stridelab-garmin-qa` on `feat/garmin-coach-ui-20261120` after verifying its current HEAD/dirty state. Do not rebase onto remote `dev` or replace the worktree merely to start Phase 1. Do not assume a GitHub documentation-only branch contains those local changes.

**Preserve all dirty and untracked work.** Specifically, **leave `RunningHistoryTools.kt` untouched**: do not delete, format, stage or incorporate it into Phase 1. It is related to Phase 2/history and lap-pace interpolation; do not introduce interpolated source measurements into Phase 1. Stop and report a hard dependency if the requested change would require editing it.

**Phase 1 features only:**
1. Weekly Running training-phase labels (Base/Build/Recovery/Deload/Taper), derived from actual saved plan evidence, with honest unknown states; do not change engine periodization.
2. Evidence-based **Weekly Coach Review** with concise positives, attention points and next focus; no invented physiology, progress, readiness or automatic plan edits.
3. Planned-vs-completed sessions, weekly mileage and consistency/completion summaries, with clear activity-to-plan matching, eligibility denominators and incomplete-history caveats.
4. Existing AMOLED/lime Running dashboard presentation and required empty, partial-history, offline, signed-out, no-plan and multiple-activity states. Preserve four approved bottom tabs: **Today, Training, Activity, More**.

**Running page visibility:** do **not** use `TodayDisciplineAnalytics.available()` (historically ≥3 running days in each of the prior two completed weeks) as a navigation gate. Users should be able to open the Running page with an approved plan, any run, or no data, signed out or offline. Retain sufficient-history rules **only for individual analyses that actually require them**. Do not count future sessions as missed, assume absent imports mean inactivity, or treat an unmatched run as a completed prescribed session.

**Phase 2 is separate and not authorized by Phase 1:** richer athlete analytics, detailed training-history tools/interpolation review, efficiency comparisons, intensity distributions, run comparisons, journal/goals/shoes. Reuse actual recorded values, preserve missing-data provenance, and obtain separately scoped approval before editing. Approval of Phase 1 does not grant blanket approval for Phase 2.

**Execution/QA:** For this Phase 1 task, **source edits only** unless the owner explicitly overrides that restriction. The owner runs Gradle, JVM tests, Android tests, ADB, emulator installation and visual acceptance. Agent reports exact modified files, branch/HEAD, diff, unperformed checks as NOT RUN and next owner verification; no automatic merge, Phase 2 continuation, or production activation.

### Implementation facts to verify, not guess

- Single Gradle `:app` module with Groovy scripts under `native-android/`; Kotlin/Compose and Navigation Compose; `io.stridelab.privatealpha`, `StrideApplication`, `TrainingRepository`, `TrainingViewModel`, `NativeMainActivity`. `dev` build currently states AGP 8.11.2, Kotlin 2.2.21, compileSdk 36, minSdk 26, targetSdk 35, JVM 17, Compose BOM 2025.10.00, Navigation Compose 2.9.5, FIT SDK 21.218.0, MapLibre 13.7.1, WorkManager 2.10.1. Build files, not chat summaries, govern actual values.
- Gradle includes shared older sources under `android-app/android/app/src/`; do not remove them solely because Compose is the launcher. Manual `StrideApplication` repository injection and Flow/Compose collection are present; do not introduce Hilt, Koin, Room, or a new app architecture without finding a concrete reason.
- Root `AGENTS.md`, `.agents/skills/stridelab-ui-safe-parity/SKILL.md`, and `.agents/skills/ui-ux-pro-max/SKILL.md` continue to govern approved UI work. Current Compose is design authority, not historical Figma. Graphify is optional and can be stale; validate live source. Exact “Ponytail” workflow was not verified and must not be invented.
- Work evidence belongs to its actual commit and build variant. Green isolated QA does not prove live Garmin behavior, phone acceptance, or the latest local working copy.


---

## Branch and release protection

- `main` is the last owner-approved stable release. `dev` is the integration branch. Work on `dev` or a reviewed short-lived branch targeting `dev`; never commit directly to `main`. A user-selected local QA branch is valid for its explicitly scoped task, not automatically a release source.
- Promote through a reviewed `dev → main` PR only after relevant automated tests, manual visual/device acceptance and owner approval of the **exact** dev HEAD. Do not force-push, reset, overwrite or silently reconcile the divergent `main` history. A green build is not approval.
- The owner-local `feat/garmin-coach-ui-20261120` state cannot be inferred from remote `dev`; inspect local HEAD and working tree before any Phase 1 modifications. Do not assume merged or accepted status for draft PR #166/#167/#169. See `docs/qa/main-dev-release-policy-20261111.md`.

## Activity Detail header and continuous charts (2026-11-12)

On the modern Kotlin Activity Detail sheet, retain the approved expanded-header layout: a normal back control, title/date, and **styled, tappable AMOLED pill actions** for Edit and Share. Do not restore the decorative lime diagonal-arrow tile, loose text-only Edit/Share controls, the map GPS-gap overlay, or the redundant top-right full-screen chevron. The map-only bottom grip and sheet gestures remain accessible.

Charts for pace, heart rate, elevation and cadence use **continuous source-measured lines on a pause-compressed moving-time x-axis**. Keep timer-pause markers/bands/notes out of the chart canvas, and do not break the displayed line at timer pauses or missing metric samples. Exclude samples recorded during paused intervals from the moving chart, without rewriting source FIT recordings, GPS, split durations, or explicit pause evidence in Overview. Use recorded samples only; do not synthesize or fill missing measurements. Chart scrubbing must map visual moving time back to the actual source sample's elapsed timestamp, keeping cross-chart selection synchronized. Preserve scale outlier handling and bound rendering for long activities. Regressions should be caught in `ActivityChartMovingTimeAxisTest` and `TracksideActivityScrollTest`.

## Garmin Connect sign-in regression guard (2026-11-12)

**Connecting, manually syncing and automatically syncing Garmin Personal API history must work without a StrideLab cloud login.** Garmin credentials are encrypted on device and must use the persistent local Garmin owner, independent of cloud authentication. Local Garmin summaries, FIT/GPS, edited titles, Today, Training and Activity Detail remain available after offline restart and StrideLab sign-out. Never restore a login redirect in More → Garmin Connect or silently hide local Garmin history based on `state.account == null`. **Only optional StrideLab cloud backup and restore require verified cloud sign-in and explicit per-cloud-account consent.** Do not mix local and cloud data owners, auto-upload on switching accounts, overwrite source-owned FIT/measurements, leak credentials, or delete legacy encrypted credentials during migration. Retain targeted Android tests for guest modal, guest sync/persistence, title edits, signed-out Training, and sign-in/sign-out isolation. See `docs/qa/main-dev-release-policy-20261111.md` for owner-run Pixel 9 verification and promotion rules.

## Engine 1.5 development guardrails

- **Engine 1.5 is the intended independent static plan generator** via `Engine15IndependentPlanGenerator`. Engine 1.0 normal generation fallback is **retired**; do not resurrect legacy generator/validator calls or registry policy in the active independent path. `Engine1Engines` can exist for historical caller compatibility only when verified.
- The checked-out `native-android/app/build.gradle` is the source of truth for build flags. The `dev` default is **both Engine 1.5 flags false**; release and isolated QA force them off. Earlier PR #131 notes describing independent debug activation are historical, not current `dev` defaults.
- Static generation owns athlete constraints, weekly volume and scheduled recovery, workout-family construction, hard-session spacing, valid long-run/rest-day placement, discipline combinations, plan validation and explicit approval. Engine 2.x remains research for automatic post-workout/readiness adaptation.
- For independent candidate work, use an isolated approved research branch/worktree; inspect exact current PR heads and `ENGINE15_EXECUTION.md`. Never edit the owner's dirty shared checkout as an unrelated research target.
- Keep I1–I9 and subsequent research/shadow artifacts typed, reproducible, read-only and non-executable. `sourceEvidenceVerified`, caller assertions, hashes, calibration digests and arithmetic reconciliation do not establish verified athlete provenance, physiological intensity or safety.
- M10.6 and independent M4–M6 development policies have historically differed. Before changing them, inspect the exact active ownership and do not silently delegate, duplicate or replace one policy with another.
- A requested mileage target is not established athlete capacity. Incomplete imported mileage is not a numeric baseline without complete coverage or a separately confirmed usual average. Missing history, exposure, recovery holds or constraints remain unknown; reject/defer unsupported generation instead of inventing limits.
- Keep family/version, observed quality exposure, interval work/recovery accounting, weekly distance reconciliation, training constraints and cross-discipline conflicts distinct. Recompute parallel review results from the same typed source; do not pair caller-asserted result objects. One permitted easy+upper-body double day does not authorize hard-session interference.
- Debug-only user-directed `PlanRequest` / `RunningPrescription` allows explicitly authored structured sessions with exact scheduled-distance reconciliation and valid hard-session spacing; timed steps need explicitly provided per-step distance equivalents, never inferred pace conversions. Preserve repeat group structure and ordered transitions. User-authored target text remains unqualified. Do not present authored workouts as verified automatic coach selection.
- Experimental Tempo/Threshold eight-week candidates exist; quality dosing/progression and source-backed tolerance still need evidence and full Android/manual gates. Do not activate candidates for release or conflate green synthetic tests with dependable coaching.
- Record exact code versions, tests actually run, blocked gates and coaching risks in `ENGINE15_EXECUTION.md` when an Engine task is authorized. Do not use Phase 1 Running UI work as permission to change the engine.

## Today and navigation design contract

- Approved floating bottom dock has exactly **Today, Training, Activity, More**. Do not invent Progress or Library as a fifth tab; any existing Library surface is reached through its approved More route. The existing **Running dashboard** belongs to its current host flow; do not change top-level navigation without separate approval.
- `StrideLabTodayScreen.kt`, connected through `NativeMainActivity.kt`, uses a true-black background, greeting, tappable seven-day calendar and real scheduled workout(s) for the selected date. The top-card concave notch follows the selected weekday center, including Monday and Sunday.
- Support one scheduled session, multiple actual saved sessions, approved recovery/open day, no-approved-plan and signed-out states. Keep actual Activity/Your Week daily bars, Next session and Weekly summary. Do not fabricate workout targets, activity measurements, photos, routes or exercise counts.
- The current running Kotlin/Jetpack Compose app, **not historical Figma** or synthetic preview galleries, is design authority. Preserve `TracksidePalette`, established discipline accents, existing motion springs and user-accepted interactions. Runtime components use `Trackside` rather than retired `TracksideFigma` naming.

## UI workspace and execution authorization

- Reuse the owner's relevant existing worktree; **for the current Phase 1 Running task** use `C:/Users/marcs/stridelab-garmin-qa` after checking its exact local branch and dirty files. Do not create a new per-screen worktree or switch to the older `ui/trackside-dev` workspace merely because historical `docs/qa/permanent-ui-workspace.md` mentions it.
- Coding agents are **SOURCE-EDIT ONLY by default** on UI tasks. Do not run Gradle, compile, assemble APKs, execute JVM/instrumentation tests, use ADB, launch emulators, install apps or perform live Garmin calls unless explicitly authorized for the particular task.
- The owner performs compilation, test execution, APK installation and emulator/visual acceptance in Android Studio. Source changes alone are not device acceptance. Record checks as `NOT RUN` when the owner has not supplied results.
- Preserve other agents' work, staged files, untracked data, local stashes and repository history. Never reset/clean a worktree to resolve conflicts without explicit instruction.

## Required handoff and implementation brief contract (owner preference, 2026-11-08)

Every Codex prompt, Work handoff, or delegated implementation batch MUST be precise enough that an execution model can act without guessing what the owner meant. The initiating agent must explicitly document, and the executing agent must verify against the actual task:

1. **Primary goal and user intent:** state *why* the change is needed and what successful user-facing behavior looks like, not merely the filenames or commands to run.
2. **Current problem and reproduction:** describe the observed wrong behavior, how to reproduce it, and the relevant starting branch/commit or evidence. Distinguish verified facts from hypotheses.
3. **Expected outcome with concrete examples:** give representative inputs and exact expected outputs, navigation paths, persistence and error states, including what must *not* happen. For example, a **Train Your Way** 80 km/week running target must create mileage-based running sessions aimed at the requested weekly total when safe, not silently turn into an **Improve 10K** race plan. Safety limits/deloads must be explicit.
4. **Implementation scope and boundaries:** identify relevant screens, modules, interfaces, and permitted edits; explicitly name protected engines, data, workflows and UI elements. Do not treat a narrowly scoped request as authorization for unrelated redesigns or refactors.
5. **Acceptance criteria:** provide objective, checkable conditions for success, including positive and negative regression scenarios and any data-integrity, offline, account-isolation or compatibility constraints that apply.
6. **Verification plan and evidence:** identify the smallest useful unit/instrumentation/device checks, who is authorized to run them, and how results will be reported. Never equate a source edit, compilation or a mock test with verified device behavior.
7. **Completion handoff:** report exact branch, starting and final commit, changed files, behavior delivered, checks actually run with PASS/FAIL/BLOCKED, known limitations, and the next required action.

**Execution discipline:** Re-read the specific owner goal before edits; optimize for the intended result rather than literal but incomplete steps. Use concise, implementation-ready language and avoid ambiguous wording. If existing code or prior plans conflict with the requested outcome, flag the conflict and address it within authorized scope; do not silently inherit unrelated goals or invent behavior. Ask a question only when an essential unresolved choice would materially change the result. Do not expand scope, remove safety constraints, change the active engine, or claim completion without evidence.

This contract governs **how tasks are specified and reported**, not permission to ignore the owner's current prompt, protected code, signing/data safety, Codex's default code-edit-only rule, or owner-run verification requirements.

## Task startup: keep context and usage bounded

1. Verify the exact repository, checkout, remote branch, current HEAD, working-tree state, and concurrent changes. Never overwrite newer work. Do not switch branches or modify unrelated worktrees without a reason. If another agent is editing the same files, coordinate or isolate the changes.
2. Read this file and **only the relevant** linked docs/code. Prefer targeted file reads, symbol searches, and small diffs. Do not reread the whole repository or paste large documents into every task.
3. State the current task's scope, reproduction/acceptance criteria, and smallest plausible edit surface. One bug, one feature, or one approved page at a time. Avoid unrelated refactors.
4. Preserve an accurate handoff: branch, exact commit, paths changed, tests run/results, evidence limitations, and next owner action. Record durable facts in existing docs instead of repeating chat history.

## Graphify (conditional; not a source of truth)

- If Graphify is **actually available to this Codex session**, use it selectively to find relevant symbols, dependencies, callers, impact, and linked tests when this would avoid broad repository inspection. A sensible order is `list_repositories` / targeted `graphify_rank_files` or `graphify_find`, then `graphify_node`, `graphify_file_neighbors`, or `graphify_tests_for` as needed.
- Do **not** assume Graphify is enabled merely because it is connected in ChatGPT. If unavailable, proceed with normal local search (`rg`, targeted file reads, GitHub search) without blocking or repeated connection attempts.
- The indexed graph may be partial or lag behind live branches. Confirm every relevant result against the current checkout/HEAD before modifying code. Graphify does not replace actual test runs or review.
- Do not query Graphify for trivial, already-known file locations. Do not load giant graph responses just to appear thorough. Tool usage is not automatically cheaper.

## Skills (load only when relevant)

- Check currently available skills; do not claim that a skill is installed unless its instructions are accessible in this environment.
- For **approved Compose UI, navigation, gesture, accessibility, responsive or Figma-reference work**, read both `.agents/skills/stridelab-ui-safe-parity/SKILL.md` (project-specific logic-preservation and owner-approval gates) and `.agents/skills/ui-ux-pro-max/SKILL.md` (including its **Jetpack Compose** guidance). Do not substitute generic web design guidance for native Android behavior.
- Discover, then selectively use any actually available Impeccable (visual critique), Superpowers (debugging/verification), Android testing, or Sentry Code Review skills. User-level or plugin installations are **not guaranteed** to be mounted into Codex. Never mass-install or load every skill, and never let skill suggestions override approved Figma or existing app logic. See `docs/qa/codex-skills-setup-20261106.md` for the verified inventory and read-only skill check.
- Use `design-system` for tokens and component states, `graphify` for targeted dependency/caller inspection when useful, and `unslop` only for requested code-quality cleanup. For Compose regression coverage, select only the relevant testing skills for test choice, structure, synchronization, semantics assertions, bounds, state restoration, and accessibility.
- For non-UI API, FIT, database, or pure data-layer work, **skip the UI skills** unless a user-visible interface behavior is actually being changed.

## Active product scope and protected boundaries

- Android-first Kotlin/Jetpack Compose multi-discipline training app. **Current priority:** Phase 1 Running dashboard—weekly plan labels, evidence-grounded coach review and completion/consistency; Phase 2 athlete analytics is distinct and separately scoped. Maintain current More analytics and other existing features while developing Running.
- Continue validating existing Garmin/FIT, Activity Detail, owner-scoped offline activity backup/restore, manual logs, Share Studio and map behavior as needed for regressions; they are not authorization for a map/route rewrite or new workout recorder.
- Do not revive **Exercise Library 2.0**, the retired Intervals.icu sync/UI, Engine 1.0 fallback or a fifth bottom tab as incidental changes. Preserved historical source/data is not permission for destructive deletion.
- Present physical recorded data faithfully. Never fabricate GPS, route samples, heart rate, speed, distance, strength sets or workout compliance; no source overwrite to satisfy a display.
- Preserve all engine, provider, persistence, local/cloud owner, authentication, FIT, MapLibre, backup, migration, activity matching and navigation contracts unless the owner explicitly scopes changes. UI-only changes should stay at the presentation layer.
- UI revisions require actual user-approved current screen scope; historical Figma file `v73TsYo6gKxUoiUock2sSE` is reference-only, not overriding current Android. Apply changes one screen/modal at a time; stop for owner acceptance.

## Test and device safety

- Read `docs/qa/README.md` and select checks from `docs/qa/verification-playbook.md` and `docs/qa/regression-coverage.md`. QA notes can lag current code: inspect the current branch and update evidence accurately.
- **Owner-run checks only for UI tasks:** explain which Android Studio checks are relevant, but do not execute tests or Gradle in Codex. Never substitute a compile or unit test for owner visual acceptance.
- `assembleDebug` only builds an APK; it does **not** establish device correctness or install the app. Only run device tests/install when supported and safe. Never report mocked or compilation-only tests as live Garmin/FIT/map proof.
- Protect the owner's installed application and SQLite data. Before any update install, verify installed package, version and signer compatibility, and use the read-only `native-android/tools/check_apk_update.py` gate. Never uninstall, clear app data, regenerate signing keys, or bypass a signer mismatch. Prefer an isolated emulator/profile when signer compatibility is uncertain.
- Do not log, upload, print or commit bearer tokens, Personal API keys, signing secrets, account content, or personal activity data. Do not bypass provider consent or unsupported API access.
- Keep GitHub Actions self-hosted runner and workflows secure; do not change permissions, registration, runner labels, or test thresholds just to obtain a green run. Refer to `docs/qa/environment.md`.

## Done means evidence, not optimism

- **For source-only UI edits**, report branch/commit, precise file diffs and blockers; explicitly state no builds or device verification were run. Record owner-supplied QA evidence only against the exact tested HEAD. Mark unverified capabilities **UNVERIFIED** rather than claiming success.
- Stop at the agreed task boundary. Do not silently begin the next screen, redesign, algorithm project, or provider integration.

Keep UI changes at the presentation layer unless a deeper change is explicitly authorized. Do not weaken validation, error handling, offline behavior, permissions, or accessibility to make a design fit. For UI changes, add or update relevant Compose regression tests covering the user-visible state, important interaction, and relevant accessibility semantics. Use bounds assertions only when geometry is part of the contract.
