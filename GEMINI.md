# StrideLab Gemini / Antigravity Project Rules

See `AGENTS.md` for full scope, git, regression, and Gradle verification policies.

## Project Memory (`docs/wiki/`)
- **Read First:** At the start of any planning, refactoring, or debugging task, read `docs/wiki/index.md` and only the specific topic page(s) relevant to the request before scanning source files.
- **Keep Updated:** After completing any architectural change, engine rule tweak, or UI refactor, update the affected `docs/wiki/*.md` page(s) in the same turn so the wiki always matches the codebase.
- **Stay Concise:** Keep wiki pages modular and under ~150 lines. Record *why* key constraints exist (e.g., warm-up caps, removed UI blocks) so future sessions do not regress them.
