# Permanent StrideLab Android Studio workspace (2026-11-08)

Use the **existing** `C:\Users\marcs\StudioProjects\stridelab-ui\native-android`
Android Studio project. Do not create a new IDE project for each UI task,
delete or replace the owner's project, or touch another worktree's uncommitted
changes. Keep the main `stridelab-development` checkout and its stashes intact.

## Current source, not historical design

- Current combined UI candidate: `integration/modern-ui-lime-builder-share2-20261108` at
  `2c43bdcb01941b253658222a57b7ba8b9953ab20` (draft PR #53; still awaiting
  owner acceptance for the combined build). This incorporates the modern
  **three-tab** Android dock, approved lime Plan Builder, SQLite long-run fix,
  and Share Studio Stage 1 and 2.
- The previous workspace instruction to use `ui/trackside-dev` is **retired**.
  Its work predates the combined SQLite/Share Studio/Plan Builder changes.
- Do not use `fix/trackside-usability-garmin-20261105`, PR #50, the old
  `TracksideFigma*` design snapshots, or an older merge base to generate a new
  app. The `TracksideFigma*.kt` source filenames are historical names but the
  implementations remain actively used by the current Plan Builder: **do not
  delete these Kotlin files**.
- Owner-approved device screenshots and the current Kotlin/Compose app—not
  Figma—establish visual truth. Today has **Today, Training, More**, not four tabs.

## Safe local checkout for the combined integration

Open a PowerShell terminal in the existing workspace, then run:

```powershell
$ui = "C:\Users\marcs\StudioProjects\stridelab-ui"
git -C $ui status --short
git -C $ui fetch origin
git -C $ui branch --show-current
git -C $ui rev-parse HEAD
```

If the current workspace has tracked edits, stashes or another task's uncommitted
work, **stop and preserve it**; do not switch or reset automatically. If clean
and you explicitly intend to test the combined integration:

```powershell
git -C $ui switch -c test/modern-lime-share2 origin/integration/modern-ui-lime-builder-share2-20261108
```

If that testing branch already exists locally, use
`git -C $ui switch test/modern-lime-share2` and check its HEAD. Never overwrite
or force-reset a reused testing branch.

In the IDE select the installed Gradle 8.13 wrapper if present, otherwise the
known-working local Gradle 8.13 installation; use JDK 17. Verify build/test
commands in Android Studio **after** checking the exact checkout.

## Before making or accepting ANY UI change

1. Verify repo, current branch, remote, clean working tree and exact HEAD.
2. Run `python native-android/tools/check_current_ui_baseline.py --require-ancestry`
   from the repository root. It rejects branches that do not descend from the
   present combined integration. Run it on the intended source checkout before
   editing; if the baseline is absent from a shallow clone, fetch the history
   rather than bypass the guard.
3. Compare the proposed change against the current Android UI, retaining
   **exactly three dock tabs**. Use source-specific cherry-picks or narrow file
   ports rather than merging an entire stale branch.
4. Build and test using the owner's preferred Android Studio environment.
   Reproduce actual UI screenshots and obtain owner acceptance. Passing
   tests alone does not prove design parity.
5. Do not uninstall the existing app, clear data, bypass signing/version checks,
   delete stashes or run `git reset --hard`/`git clean`.

Historical audit files should remain clearly marked historical; see
`docs/qa/current-android-baseline.md` for the verified source composition
and pending acceptance checks.
