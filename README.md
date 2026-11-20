# StrideLab Development

Native Android application: Kotlin, Jetpack Compose, Garmin FIT import, offline training/activity records, and experimental independent Engine 1.5 static coaching.

**Current source-of-truth documents**

- [Agent instructions and project safety rules](AGENTS.md)
- [Current product/development roadmap](docs/ROADMAP.md)
- [Engine 1.5 ten-stage development roadmap](docs/ENGINE_DEVELOPMENT_ROADMAP.md)
- [Engine 1.5 implementation and QA ledger](ENGINE15_EXECUTION.md)
- [Repository documentation cleanup audit](docs/qa/repository-cleanup-audit-20261121.md)

**Branches:** `main` is the last owner-approved stable release; `dev` is the Android integration branch. Draft PRs/experiment branches are not automatically integrated. Engine 1.5 candidate logic is present but disabled by default in normal `dev` debug and forced off in release/isolated QA until an explicit acceptance gate.

The owner-local Garmin QA/Running page worktree may include newer unmerged code than remote `dev`. Verify its HEAD and dirty state before editing. No public release is authorized.
