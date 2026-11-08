# StrideLab: current Android source of truth (2026-11-08)

## Prevent another obsolete-branch UI regression

**The actual installed modern Android Kotlin/Jetpack Compose UI is authoritative.**
Archived Figma frames, screenshots of older builds, `docs/design` previews,
historical test results and source filenames containing "Figma" are not
evidence of the **current** visual design.

### Current integration candidate — draft, not merged

- Branch: `integration/modern-ui-lime-builder-share2-20261108`
- Commit: `2c43bdcb01941b253658222a57b7ba8b9953ab20`
- PR: [#53](https://github.com/Isshhin/stridelab-development/pull/53)
- Parent: `046deac20453a17ad98d6bbfe8c1dfe04bab11d5`
- The combined build needs owner acceptance in Android Studio before it
  becomes the accepted production baseline. Do not promote this PR to
  release/main without that acceptance.

It contains:
1. The owner-referenced **three-tab** floating dock:
   `Today` (`home`), `Training` (`training`), `More` (`settings`).
   Activity history and detail remain reachable through Training.
2. Compact Today calendar, discipline cards, next session and weekly activity
   from `StrideLabTodayScreen.kt` in the existing Android UI.
3. The approved lime Plan Builder transferred via `046deac` (PR #52) from
   PR #50 without merging the obsolete app base.
4. SQLite long-record `CursorWindow` reader fix `bdb9023`.
5. Share Studio Stage 1 privacy and continuity `0e7e2d4` and Share Studio
   2.0 source `076f6c5` integrated in `2c43bdc`.

### Branches and references that must not become the new app base

| Source | Classification | Handling |
| --- | --- | --- |
| `fix/trackside-usability-garmin-20261105` | Old app base of PR #50 | Do not build a new UI release from it |
| `design/lime-plan-builder-approved-20261108` / PR #50 | Builder-only UI source atop old app | Historical reference; its builder changes have already been selectively integrated |
| `ui/trackside-dev` | Older modern Today UI work | Useful for code provenance, but lacks the full combined features; not the working base |
| Outdated Figma frames | Historical designs | Never use as the visual authority for current app |
| `design-qa.md` (root, 2026-11-04) | Superseded Figma Strength design QA | Removed from active checkout; recover from Git history if historical evidence is needed |

### Files that must NOT be deleted just because their names look old

- `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFigma*.kt`:
  historical naming, **live Kotlin/Compose screen source** used by the
  Plan Builder and Activity Detail.
- `android-app/android/`: legacy Java app still used by APK-upgrade and
  data-preservation tests under `.github/workflows/trackside-legacy-upgrade.yml`.
- `archive/` and phase/handoff documents: retained historical/research
  evidence. An audit should establish all consumers and retention requirements
  before retiring them.
- `native-android/app/src/androidTest/`: tests may fail to compile for
  independent reasons; do not delete them merely to make a test build pass.
- `.agents/skills/`: development aid files; retain the skill instructions
  and correct their guidance when obsolete.

## Mandatory startup check for future Android UI tasks

From the repository root, after preserving local edits:

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
git fetch origin
python native-android/tools/check_current_ui_baseline.py --require-ancestry
```

The guard verifies the current integrated branch is an ancestor and checks
the modern Today source, exactly three floating tabs, current lime Plan
Builder, and privacy/export code. It intentionally rejects UI branches
still rooted in the pre-integration state. Never bypass it by editing the
expected SHA or disabling the workflow to make a stale branch pass.

**Source checks do not replace runtime verification.** After updating a
branch, compile and test in Android Studio, inspect rendered Today/Training/
More screens on a real device or emulator, verify the Plan Builder and Share
Studio exports, preserve signing/version compatibility, and obtain explicit
owner approval before merging.

The code is actively developed; when a newer combined baseline is approved,
update this document, the script's required ancestor, and the CI gate
**together** in an independently reviewed change.
