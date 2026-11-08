---
name: stridelab-ui-safe-parity
description: "Use when implementing, auditing, or polishing an approved StrideLab Jetpack Compose screen, modal, navigation surface, map overlay, responsive layout, accessibility, or UI interaction. Focus on actual current Android app parity and user-approved visual improvements while preserving Garmin, training-engine, persistence, provider, and business logic. Do not trigger for pure API, FIT, database, or algorithm changes."
---

# StrideLab — UI-only, behavior-preserving implementation

This skill applies to **one approved page or modal per task**. It adds project-specific safety and acceptance requirements; it does not replace general-purpose UI/testing skills or create permission to redesign unapproved screens.

## 0. Establish boundaries

- Read root `AGENTS.md` and the relevant current QA handoff. Verify checkout/HEAD and keep other agents' work.
- Establish the actual current bottom tab and page: exactly **Today**, **Training**, **More**. Activity history/detail is reachable through Training; Library through More. Do not add an Activity or Library dock tab.
- Identify the **current Android Compose implementation** and the owner's last accepted *app screenshot*. Old Figma nodes and old build screenshots are historical references only, never implementation authority. First verify that the checkout descends from the current integrated baseline in `docs/qa/current-android-baseline.md`.
- Capture current observable behavior and data flow *before* editing: screen navigation, buttons, handlers, scrolling, loading/error/empty states, real data sources, and map/sheet gestures. List the exact behavior that must remain unchanged.
- If the actual installed app behavior or a required interaction is unclear, do not invent a feature or redesign adjoining pages; isolate the uncertainty for owner review.

## 1. Choose only applicable companion skills

- **Required for UI work:** read `.agents/skills/ui-ux-pro-max/SKILL.md`; use its **Jetpack Compose** stack guidance and relevant accessibility, responsive layout, interaction, and typography rules. Translate generic web guidance to native Android; never paste web-specific APIs into Compose.
- **Impeccable (if discoverable in this Codex session):** use for focused visual critique/polish; never substitute its style preference for the current approved Android app.
- **Superpowers (if discoverable):** use applicable planning, systematic debugging and verification workflows, but avoid unconstrained brainstorm/reimplementation or needless skill chaining.
- **Android testing/debugging/review skills (if discoverable):** use for focused Compose and emulator regression tests. **Sentry Code Review** is optional if actually installed, connected, and appropriate for non-sensitive diff review.
- Search both repo-level `.agents/skills/` and Codex-exposed user/plugin skills. List what was actually found and what was used. Do not assume that a skill installed elsewhere is mounted here; do not attempt mass installations or clone entire skill catalogs.
- Graphify is optional for targeted Kotlin symbol/caller/test discovery, and can be stale or incomplete. Check the exact live files before editing.

## 2. Allowed changes and forbidden behavior

**Allowed within the approved page's scope:** Compose layout, alignment, design tokens, typography, accessibility semantics, animations, responsive sizing, overlays, UI-only state for visual behavior, preview fixtures, and UI tests.

**Do not change without a separate, explicit task:**
- Training plans, coaching policy, Engine 1.5 / Engine 2.x, exercise prescription, or workout calculations.
- Garmin HTTP/FIT, MapLibre GPS integrity, source provenance, provider integrations, authentication or token handling.
- Repository/database schemas, activity histories, ownership/authorization rules, persistence, or user data.
- API request/response contracts, background jobs, business rules, validation rules, or unsupported features.
- Bottom-tab architecture, neighboring pages, plan-builder steps, or any modal not included in the approved scope.

Existing Compose files can contain mixed UI and logic. Do **not** assume an entire file is safe to refactor. Trace callbacks/state, preserve their data types and original destination, and review the diff for inadvertent behavioral changes. If parity requires changing a logic or data contract, **stop and propose a separate change**; do not silently adjust it.

Specific Activity map contract: keep real FIT-derived coordinates and gaps, account-scoped offline detail, honest offline/no-GPS state, route camera, attribution, gestures, and TextureView glass-sheet overlay. Reuse the existing MapLibre engine rather than rewriting it.

## 3. One-page implementation and acceptance loop

1. Record exact source commit, owner-approved current Android screenshot, UI contract and baseline tests.
2. Implement the approved page/modal only, minimizing edits. Do not polish or implement another page automatically.
3. Run focused unit/UI/Compose tests and compile Android; make any test blocker explicit. Build success is **not** emulator success.
4. Verify on a compatible emulator at least 360dp and 412dp widths and normal/increased font scaling where available. Exercise all controls, scrolling, navigation, loading/empty/error/offline states, modal dismissal, and accessibility semantics. Inspect screenshot parity with the current owner-approved Android implementation and screenshot; report differences.
5. For Activity map, verify sheet-versus-map gesture regions, route rendering, attribution and truthful GPS states. Never claim a live provider result from a mock.
6. Report exact commit, changed file paths, relevant tests and failures, before/after screenshots, any behavioral risks, and remaining owner checks.
7. **STOP for owner visual and functional acceptance.** Do not mark accepted or start the next page on green tests alone.

Do not uninstall/clear the data-bearing app or bypass incompatible APK signing. Follow `docs/qa/verification-playbook.md` and `docs/qa/environment.md` for device safety.

## Success definition

The **approved UI** is reproduced closely; existing functionality and data contracts are unchanged; all test limitations are stated; and the owner explicitly accepts that *one* page.
