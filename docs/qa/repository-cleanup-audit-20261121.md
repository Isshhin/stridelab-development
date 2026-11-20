# Repository cleanup audit — 2026-11-21

**Scope:** Documentation-only review against remote `dev` plus draft #141/#142/#166/#167/#169, carried on `docs/agents-current-state-20261121`. This is **not a complete repository-wide dependency scan**; the owner's local `feat/garmin-coach-ui-20261120` branch and dirty files were unavailable remotely. No executable code, tests, Gradle configuration, workflows, migrations, research files, historical test evidence or local user data were modified.

## Resolved obsolete documentation

| File | Action | Reason |
| --- | --- | --- |
| `docs/ROADMAP.md` | **Rewritten** | Removed obsolete October 26 Engine 1.0/WebView reconstruction priorities and superseded Phase 4D/4E-as-current instructions; now the current product index. Original text remains recoverable through Git history, blob `51e23f7da0cdefe8a538350e110d660da16892b3`. |
| `docs/ENGINE_DEVELOPMENT_ROADMAP.md` | **Added to current documentation branch** | Previously existed only on a separate unmerged documentation branch. Keeps the 10-stage development/science reference with a November 21 current-status reconciliation and Garmin PR #169. |
| `docs/HANDOFF.md` | **Removed as obsolete current handoff** | Its contents referred to the October 26 Phase 4E SDK/setup-android workflow and no longer describe the native Android workstream. Exact previous blob: `1c2040ac85560a6e176a0e404372b455f4439e7f`. Current entrypoints are `AGENTS.md` and `docs/ROADMAP.md`. The historical content remains available in Git history. GitHub code-search did not find references, but its index may be incomplete; check repository link consumers before merge. |
| `README.md` | **Updated** | Replaces an empty repository description with current source-of-truth links and release distinctions. |
| `AGENTS.md` | **Updated documentation links** | Keeps operational guardrails and directs agents to current planning documents. |

## Intentionally retained

- All `research/` and `research/engine15/` trees, experiments, research citations, synthetic fixtures and Engine 2.x exploratory work.
- `ENGINE15_EXECUTION.md` and `research/engine15/ENGINE15_INDEPENDENT_GENERATION_AUDIT_20261117.md`: chronological evidence, not current roadmap authority.
- `docs/ENGINE1-BASELINE.md`: historical serialized plan/compatibility contract. Engine 1.0 is retired as the normal independent planning fallback, but old saved snapshots may still rely on compatibility.
- `docs/NATIVE-PHASE1-PARITY.md`: historical source/prototype acceptance evidence; do **not** use its old Overview/Training/Activities/Library dock as present UI authority.
- Existing QA documents, signing/update playbooks, backup/migration fixtures, CI workflows, and native/legacy source sets: possible active compatibility or regression dependencies.
- More/Running Analytics, old UI files, Exercise Library legacy references: deletion deferred until an exact current HEAD dependency scan proves they are unreachable. The **Exercise Library 2.0 roadmap feature is removed**, not necessarily all historical dependencies.

## Deferred cleanup — requires full source graph and owner worktree

1. Capture the owner-local `feat/garmin-coach-ui-20261120` exact HEAD, dirty files, staged/untracked list and dependency graph.
2. Search live source and resources for unused Compose screens, abandoned `TracksideFigma` previews, legacy WebView assets and duplicate UI routes. Confirm no manifest, navigation, tests, archive/migration path or Android source-set references before deleting.
3. Check old root/documentation links and internal relative links after this PR merges.
4. Review legacy Engine 1.0 and Exercise Library references **for migration/serialization dependencies**; do not remove just to simplify the tree.
5. If a file is demonstrably unused, use a separate narrowly scoped deletion PR with exact call sites, verification and rollback instructions.

## Do-not-touch rules

- Never delete a research dataset, experiment, or source evidence to make the repository smaller.
- Do not delete `RunningHistoryTools.kt` or any dirty/untracked owner file. It is outside Phase 1.
- Do not remove backup codecs, offline owner data, historical plan readers, FIT parsers, Android resources, or CI guards without proving dependency safety.
- A docs-only change does not warrant or establish app build/test acceptance.

## Verification

- Reviewed actual files `docs/ROADMAP.md`, `docs/HANDOFF.md`, `docs/ENGINE1-BASELINE.md`, `docs/NATIVE-PHASE1-PARITY.md`, `README.md`, root agent rules, the separate detailed engine roadmap and relevant PR descriptions.
- GitHub code search returned no references for the obsolete handoff filename but can be incomplete; link checks remain open.
- No Gradle, JVM, Android instrumentation, ADB, live Garmin API or physical device actions performed.
