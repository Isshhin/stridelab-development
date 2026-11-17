# StrideLab agent workflow (Codex and other repository agents)

For ongoing Engine 1.5 implementation, follow the ordered milestones and current exact next action in [`ENGINE15_EXECUTION.md`](ENGINE15_EXECUTION.md). Continue after each validated commit; do not treat the tracker as a stopping point.

## Activity Detail header and continuous charts (2026-11-12)

On the modern Kotlin Activity Detail sheet, retain the approved expanded-header layout: a normal back control, title/date, and **styled, tappable AMOLED pill actions** for Edit and Share. Do not restore the decorative lime diagonal-arrow tile, loose text-only Edit/Share controls, the map GPS-gap overlay, or the redundant top-right full-screen chevron. The map-only bottom grip and sheet gestures remain accessible.

Charts for pace, heart rate, elevation and cadence use **continuous source-measured lines on a pause-compressed moving-time x-axis**. Keep timer-pause markers/bands/notes out of the chart canvas, and do not break the displayed line at timer pauses or missing metric samples. Exclude samples recorded during paused intervals from the moving chart, without rewriting source FIT recordings, GPS, split durations, or explicit pause evidence in Overview. Use recorded samples only; do not synthesize or fill missing measurements. Chart scrubbing must map visual moving time back to the actual source sample's elapsed timestamp, keeping cross-chart selection synchronized. Preserve scale outlier handling and bound rendering for long activities. Regressions should be caught in `ActivityChartMovingTimeAxisTest` and `TracksideActivityScrollTest`.

## Garmin Connect sign-in regression guard (2026-11-12)

**Connecting, manually syncing and automatically syncing Garmin Personal API history must work without a StrideLab cloud login.** Garmin credentials are encrypted on device and must use the persistent local Garmin owner, independent of cloud authentication. Local Garmin summaries, FIT/GPS, edited titles, Today, Training and Activity Detail remain available after offline restart and StrideLab sign-out. Never restore a login redirect in More → Garmin Connect or silently hide local Garmin history based on `state.account == null`. **Only optional StrideLab cloud backup and restore require verified cloud sign-in and explicit per-cloud-account consent.** Do not mix local and cloud data owners, auto-upload on switching accounts, overwrite source-owned FIT/measurements, leak credentials, or delete legacy encrypted credentials during migration. Retain targeted Android tests for guest modal, guest sync/persistence, title edits, signed-out Training, and sign-in/sign-out isolation. See `docs/qa/main-dev-release-policy-20261111.md` for owner-run Pixel 9 verification and promotion rules.

## Mandatory release branch policy (owner decision, 2026-11-11)

**`main` is the last owner-approved stable release and the source of truth for release builds; `dev` is the only ongoing Android implementation/integration branch.** Implement and test all feature/UI/fix changes on `dev` (or a short-lived branch merging into `dev`). Do not make or push changes to `main` directly. Promotion requires a reviewed `dev → main` PR, relevant passing automated tests, manual visual/device acceptance, and the owner's explicit approval of the exact dev HEAD. Never force-push, reset or overwrite `main`. The existing main history diverges from the modern Android integration; do not silently replace its README/history. See `docs/qa/main-dev-release-policy-20261111.md` for the exact gate and bootstrap status. A green build is not approval, and the pending Activity Detail Edit/map-control fix must remain on `dev` until validated.


This is the persistent default for work in this repository. Follow the user's **current task prompt** and the applicable, up-to-date branch documentation. Do not treat old handoffs, cached graph indexes, or historical green builds as proof of the live state.

## Active Android design authority (owner update, 2026-11-10)

The **current running Kotlin/Jetpack Compose implementation** is the authoritative
StrideLab visual system: AMOLED-black surfaces, lime branding, established
discipline accents, existing motion springs, navigation, and live component
states. Historical Figma/"parity" labels and preview artifacts are not design
authority; do not regress live layouts to archived frames. Older references
below apply only as historical context unless the owner explicitly reapproves
them. Runtime components formerly prefixed `TracksideFigma` are now named
`Trackside` to reflect their actual use, without changing their behavior.
Do not reintroduce the retired synthetic Figma comparison preview gallery.

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

## Engine 1.5 independent candidate work (2026-11-17 task scope)

- When explicitly authorized to continue the independent Engine 1.5 candidate, work in a separate research branch/worktree and inspect the actual current PR heads first. Never use the shared dirty checkout as an edit target.
- Keep I1–I9 and later shadow results typed, reproducible, read-only, and non-executable. Do not turn `sourceEvidenceVerified`, caller assertions, calibration digests, or arithmetic reconciliation into verified athlete provenance or authority.
- M10.6 production-style generation and M4–M6 independent planning have overlapping but distinct workload policies. Preserve both; compare outputs and do not silently delegate, duplicate policy, or replace the M10.6 policy owner.
- Reuse an existing approved/read-only research UI. Do not create another Android planning screen for the same preview.
- Quality-session development is a priority when specifically requested. Keep family structure/version, work/recovery accounting, observed family-specific exposure, user-entered constraints, missing history, recovery holds, and weekly mileage reconciliation distinct. Missing evidence is unknown, not a duration cap or a user limit. Do not activate any experimental quality generator in production without separate owner approval.
- Record exact heads, per-batch diffs, commands, test counts, blocked checks, and unresolved coaching risks in `ENGINE15_EXECUTION.md` and the candidate's research handoff. Green unit tests do not establish real-world coaching reliability.

## Owner-approved Today redesign (2026-11-06)

- **AMOLED true black** Today screen, StrideLab branding, greeting, month/week navigation and seven-day tappable calendar.
- Tapping a weekday changes the selected date, the **real saved workout(s)** displayed and the accent theme. The **concave notch along the workout card's top edge must track the center of the selected weekday**, including Monday and Sunday. Do not leave the notch centered.
- Card variants: one scheduled session (discipline-specific mountain/abstract art), multiple sessions (both/all real session rows, progress and open-next action), an approved-plan day without sessions (recovery/open day), no-approved-plan, and signed-out. Never invent training targets, GPS routes, exercise counts, biometrics, calories or other saved values.
- Keep the compact **Next session**, real saved **Activity / Your week** daily bars and **Weekly summary**, with secondary dashboards still available under Additional insights. Four floating tabs: **Today, Training, Activity, More**. Library is reachable from More.
- The currently implemented view is `StrideLabTodayScreen.kt` connected from `NativeMainActivity.kt`; this is **implemented source pending owner Android Studio acceptance**, not an accepted device rendering.
- This approval applies to Today only and replaces earlier Today Figma nodes; do not back-port old illustrated route cards or photos.

## Permanent UI workspace and owner-approved division of work

- **Reuse one local Android Studio UI workspace for every UI task.** Do not ask the owner to open a new Git worktree for each screen or change. See `docs/qa/permanent-ui-workspace.md`. Keep the owner's main development checkout and existing stashes untouched.
- **Codex is CODE-EDIT ONLY by default**: make only the exact requested source changes, with a brief source review. **Do not run Gradle, compile, assemble APKs, execute tests, run ADB, launch emulators, or produce long QA reports/documentation** unless the owner explicitly authorizes an exception for that task.
- **The owner executes all compilation, tests, APK creation and emulator debugging in Android Studio** using the stable workspace. Regular ChatGPT prepares scoped prompts, reviews the owner's findings and records GitHub documentation.
- These standing instructions take precedence over older build/checklist suggestions elsewhere in this file for routine UI changes. A change is not considered visually accepted until the owner verifies it on-device.

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
- For **approved Compose UI, navigation, gesture, accessibility, responsive, or Figma parity work**, read both `.agents/skills/stridelab-ui-safe-parity/SKILL.md` (project-specific logic-preservation and owner-approval gates) and `.agents/skills/ui-ux-pro-max/SKILL.md` (including its **Jetpack Compose** guidance). Do not substitute generic web design guidance for native Android behavior.
- Discover, then selectively use any actually available Impeccable (visual critique), Superpowers (debugging/verification), Android testing, or Sentry Code Review skills. User-level or plugin installations are **not guaranteed** to be mounted into Codex. Never mass-install or load every skill, and never let skill suggestions override approved Figma or existing app logic. See `docs/qa/codex-skills-setup-20261106.md` for the verified inventory and read-only skill check.
- Use `design-system` for tokens and component states, `graphify` for targeted dependency/caller inspection when useful, and `unslop` only for requested code-quality cleanup. For Compose regression coverage, select only the relevant testing skills for test choice, structure, synchronization, semantics assertions, bounds, state restoration, and accessibility.
- For non-UI API, FIT, database, or pure data-layer work, **skip the UI skills** unless a user-visible interface behavior is actually being changed.

## Product boundaries and priorities

- This is an Android-first Kotlin / Jetpack Compose multi-discipline training app. The approved floating bottom dock has **four tabs: Today, Training, Activity, More**. The Library remains accessible through More; do not re-add Library or invent Progress as a bottom tab.
- Near-term priorities are: (1) stabilize Garmin activity/FIT/GPS/map and other core behavior; (2) implement **explicitly owner-approved** UI references one tab, page and modal at a time; (3) defer Library/More polish beyond the approved four-tab dock. The owner-approved AMOLED Today redesign of 2026-11-06 **supersedes Today Figma parity only**; other tabs still follow their own approved designs.
- **Analysis under More is backlog only.** Do not build or add new analytics features unless separately approved.
- Figma file: `v73TsYo6gKxUoiUock2sSE` for other approved flows, not as the Today redesign source. Treat approval as explicit; preserve the user's newer Today reference screenshots and interactive notch contract rather than reverting to old Figma.
- UI reconstruction: keep Garmin, MapLibre, persistence and engine boundaries intact. The owner runs compilation, tests and emulator checks in Android Studio, then **STOP for owner acceptance** before moving to the next page. A source commit alone is not visual or device acceptance.
- Preserve all existing application logic and data contracts, including training engines, databases, persistence, synchronization, backup/recovery behavior, and provider boundaries. Do not change navigation, routes, feature behavior, business rules, storage, or migrations without explicit approval.
- Do not modify Engine 1.5 (draft PR #37), Engine 2.x research (draft PR #34), unrelated branches, or unrelated data providers as part of a UI/Garmin fix. Any change to these requires specific authorization and branch verification.
- Garmin is the active wearable source in the native QA workflow. Do not re-enable the retired Intervals.icu UI/sync or destroy preserved historical records without explicit authorization. Keep real recorded GPS provenance; never synthesize geographic tracks.

## Test and device safety

- Read `docs/qa/README.md` and select checks from `docs/qa/verification-playbook.md` and `docs/qa/regression-coverage.md`. QA notes can lag current code: inspect the current branch and update evidence accurately.
- **Owner-run checks only for UI tasks:** explain which Android Studio checks are relevant, but do not execute tests or Gradle in Codex. Never substitute a compile or unit test for owner visual acceptance.
- `assembleDebug` only builds an APK; it does **not** establish device correctness or install the app. Only run device tests/install when supported and safe. Never report mocked or compilation-only tests as live Garmin/FIT/map proof.
- Protect the owner's installed application and SQLite data. Before any update install, verify installed package, version and signer compatibility, and use the read-only `native-android/tools/check_apk_update.py` gate. Never uninstall, clear app data, regenerate signing keys, or bypass a signer mismatch. Prefer an isolated emulator/profile when signer compatibility is uncertain.
- Do not log, upload, print or commit bearer tokens, Personal API keys, signing secrets, account content, or personal activity data. Do not bypass provider consent or unsupported API access.
- Keep GitHub Actions self-hosted runner and workflows secure; do not change permissions, registration, runner labels, or test thresholds just to obtain a green run. Refer to `docs/qa/environment.md`.

## Done means evidence, not optimism

- **For Codex UI edits**, report a concise branch/commit, changed files and blockers only; indicate that builds and device verification were not run. For owner-run QA, regular ChatGPT maintains detailed findings in GitHub after results are supplied.
- Regular ChatGPT maintains task-specific QA/handoff documentation after owner-confirmed findings. Mark unverified capabilities **UNVERIFIED** rather than claiming success.
- Stop at the agreed task boundary. Do not silently begin the next screen, redesign, algorithm project, or provider integration.

Keep UI changes at the presentation layer unless a deeper change is explicitly authorized. Do not weaken validation, error handling, offline behavior, permissions, or accessibility to make a design fit. For UI changes, add or update relevant Compose regression tests covering the user-visible state, important interaction, and relevant accessibility semantics. Use bounds assertions only when geometry is part of the contract.
