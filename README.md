# StrideLab Android development

**Use the current Android app and its Kotlin/Jetpack Compose implementation,
not historical Figma designs, for UI work.**

## Current integration candidate

- Source: `integration/modern-ui-lime-builder-share2-20261108` at
  `2c43bdcb01941b253658222a57b7ba8b9953ab20` (draft PR #53,
  **unmerged, awaiting final device acceptance**).
- Dock: **Today · Training · More** (three tabs). Activity history is
  accessible from Training.
- Includes the approved lime Plan Builder, Garmin/SQLite long-run repair,
  Share Studio 2.0, Stage 1 privacy trimming, and offline-first behavior.

**Before editing:** read [current Android baseline and branch safety](docs/qa/current-android-baseline.md)
and [AGENTS.md](AGENTS.md). Verify the exact source checkout and run:

```powershell
python native-android/tools/check_current_ui_baseline.py --require-ancestry
```

Do not build a replacement app from PR #50's older base or
`ui/trackside-dev`. Do not delete live files based on historical names
such as `TracksideFigma*.kt`. See the
[permanent Android Studio workspace guide](docs/qa/permanent-ui-workspace.md)
for safe local commands, and [QA index](docs/qa/README.md) for tests.

Older branches, pre-Trackside source, the legacy Android project and
research records remain for rollback, compatibility and evidence. None
should supersede the current app without explicit owner approval.
