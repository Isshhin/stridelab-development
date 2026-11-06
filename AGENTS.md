# StrideLab agent workflow (Codex and other repository agents)

This is the persistent default for work in this repository. Follow the user's **current task prompt** and the applicable, up-to-date branch documentation. Do not treat old handoffs, cached graph indexes, or historical green builds as proof of the live state.

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

- This is an Android-first Kotlin / Jetpack Compose multi-discipline training app. Current bottom tabs are **Today, Training, Activity, Library, More**; do not introduce a Progress tab or a sixth bottom tab.
- Near-term priorities are: (1) stabilize Garmin activity/FIT/GPS/map and other core behavior; (2) reconstruct **Today**, **Training**, and **Activity** one tab, page, and modal at a time from **user-approved** Figma nodes; (3) defer Library/More polish.
- **Analysis under More is backlog only.** Do not build or add new analytics features unless separately approved.
- Figma file: `v73TsYo6gKxUoiUock2sSE`. Treat approval as explicit, not implied by a design's presence. Current designs may still be reorganized; verify the exact approved node before implementing it.
- UI reconstruction: keep working Garmin, MapLibre, persistence, and engine boundaries intact. Pass functionality and focused tests, check visual/interaction parity on an emulator, then **STOP for owner acceptance** before moving to the next page. Green tests alone are not visual or device acceptance.
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
