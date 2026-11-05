# Codex skills setup — StrideLab UI work (2026-11-06)

This is a **configuration and skill-discovery checkpoint**, not permission to redesign the Android app. The purpose is to help Codex produce closer Figma parity, better Compose UX, and reliable testing **without altering existing app logic**.

## What is actually verified on this GitHub branch?

| Item | Verified status | When Codex should use it |
| --- | --- | --- |
| `.agents/skills/ui-ux-pro-max/SKILL.md` | Present in repository, including `data/stacks/jetpack-compose.csv` | Native layout, interaction, accessibility, responsive design and visual polish |
| `.agents/skills/stridelab-ui-safe-parity/SKILL.md` | Present in repository after this docs PR | Required project-specific UI-only guardrails and acceptance gates |
| Impeccable | **Not present in repository tree**; personal Codex/plugin install **not verified** | Optional critique/polish, always secondary to approved Figma |
| Superpowers | Repository has historical `docs/superpowers` plans/specs, but **no confirmed installed skill in repo** | Optional systematic debugging and task discipline when available |
| Android testing / QA skills | **Personal environment not inspected** | Optional focused Compose/UI/emulator test guidance |
| Sentry Code Review | **Plugin/user installation not verified** | Optional review of an authorized, non-sensitive code diff |
| Graphify | Connected in ChatGPT and repository indexed; **not proof of Codex access or complete current-branch indexing** | Optional targeted symbol, dependency and test discovery |

**Important:** A skill installed for one tool, operating system user, or working directory is not automatically present in every Codex cloud session. If a skill is not visible in the Codex session, report it as unavailable and continue with the two repository skills. Never claim external skills were executed without actually loading them.

Codex typically discovers repo-local skills at `<repo>/.agents/skills/<name>/SKILL.md` and personal skills at `~/.agents/skills/<name>/SKILL.md`, in addition to available plugins and bundled skills. Restart Codex or open a new task if newly installed skills are not shown.

## Task routing: minimal context

1. **Pure Garmin/API/algorithm/database defect:** no UI skills by default; use relevant debugging/testing skill only.
2. **Approved Figma → Compose implementation:** load `stridelab-ui-safe-parity` and `ui-ux-pro-max`; optionally Impeccable for visual audit and an Android testing skill for verification.
3. **Broken Compose interaction:** use the two repo skills only when interface behavior is involved; optionally Superpowers systematic debugging and focused instrumentation.
4. **Code review:** use Graphify impact/context and a review skill only when helpful; verify against live source, not an old index.
5. **Never automatically chain all skills.** Each extra skill should have a concrete contribution to the current issue, with no unrelated changes.

## Safety contract

- Work on **one page or modal** under Today, Training, or Activity. No broad app-wide redesign.
- Never convert the approved screen into a different visual style merely because a UI skill suggests it.
- The UI work must preserve the actual callback/state/data contracts for Garmin history, FIT GPS, MapLibre routes, offline storage, training plans, persistence and user identity.
- If the UI and business rules share one Compose file, minimize the diff and explicitly check that callbacks, calculations, and repository access remain unchanged.
- Complete targeted tests and emulator/screenshot comparison, then **stop for owner acceptance**.
- Library, More and the proposed future **Analysis under More** remain deferred.

Read `.agents/skills/stridelab-ui-safe-parity/SKILL.md` for detailed scope and gates; do not copy its full text into every Codex request.

## Low-cost, read-only Codex verification prompt

Use this once in a **new Codex task after updating to the branch containing the skills**:

> Read root AGENTS.md. Without modifying files or running a build, list all skills actually discoverable in this Codex session that could help StrideLab Jetpack Compose UI design, visual QA, interaction debugging, accessibility or Android testing. Verify the repository skills `stridelab-ui-safe-parity` and `ui-ux-pro-max` are accessible. Check whether Impeccable, Superpowers, Android testing skills and Sentry Code Review are installed; distinguish Codex plugins, personal skills and repository skills. Check whether Graphify is available here, without requiring it. Report exact skill names/paths, unavailable items and which 2–3 skills should be used for a single Figma-approved screen. Do not install anything or edit code.

Follow-up UI task prompts can be short:

> Follow AGENTS.md and `stridelab-ui-safe-parity`. Implement **only** Figma-approved [tab/page/node]. Preserve existing callbacks, engine, Garmin, GPS, persistence and navigation behavior. Use relevant installed skills; run focused tests; report emulator screenshots and parity gaps; stop for my approval.

## Source of truth

- Root `AGENTS.md`: repo-wide work and safety.
- `docs/qa/verification-playbook.md`: test selection and evidence.
- `docs/qa/activity-e19-real-map-implementation-20261105.md`: verified Garmin GPS/MapLibre data and presentation contract.
- Approved Figma node from the owner, not a draft or unverified design.
- Current live branch and checked-out code, not cached skill notes or Graphify graph snapshots.
