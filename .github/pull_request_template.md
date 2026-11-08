## Source baseline and acceptance (required for Android UI changes)

- [ ] I verified the exact source checkout/HEAD and preserved local work.
- [ ] I started from the current combined Android baseline documented in
      `docs/qa/current-android-baseline.md`, not an old Figma/PR #50 branch.
- [ ] `python native-android/tools/check_current_ui_baseline.py --require-ancestry`
      passed on this exact commit (or the reason it could not run is documented).
- [ ] The modern Today screen and dock remain **Today, Training, More** only;
      Activity history remains reachable via Training.
- [ ] Older `TracksideFigma*.kt` filenames were not mistaken for unused code.
- [ ] Changes preserve SQLite long-record handling, Garmin, offline support,
      Share Studio route privacy, training engines and local user data.
- [ ] I documented build/tests, real Android Studio screenshots (for UI edits),
      failing checks and pending phone acceptance. Passing unit tests alone
      does not establish visual correctness.
- [ ] No app uninstall, data clearing, forced branch reset or APK signer bypass
      was used.

## Scope and evidence

Base commit:
Head commit:
Files changed:
Tests and build result:
Android Studio screenshot/device result:
Remaining limitations:

Do not merge until the owner explicitly accepts the integrated UI.
