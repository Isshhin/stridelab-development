# StrideLab Android: main / dev promotion policy (2026-11-11)

## Branch responsibilities

- `main` is the sole **approved stable baseline** and the source of truth for release APKs. It must contain only changes that the owner has manually accepted on an emulator and, where relevant, their real device.
- `dev` is the **ongoing integration branch**. All new features, fixes, UI changes, and tests land here first. Historical integration/feature/fix/research branches can remain as references or isolated work but may not bypass `dev`.
- **Never push directly to `main`, force-push it, reset it, or change its history.** Promotion is a reviewed pull request from `dev` to `main`, after owner approval. Don't auto-merge.
- Keep `main` unchanged when dev's build compiles or tests pass but visual or device acceptance is incomplete.

## Required gates before promotion

1. Scope and review: verify the precise `dev` commit, changed files, diff, and that approved controls are preserved. No known regressions or silently reintroduced retired UI.
2. Compile and unit tests: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.
3. Focused instrumentation: the affected Compose UI, navigation, persistence/repository and responsive tests must pass on a known emulator. Add a test for any reported regression, including **presence** of approved controls and **absence** of retired controls.
4. Manual visual acceptance: the owner opens the exact dev build, exercises primary navigation and all changed surfaces, and explicitly confirms the appearance and behavior.
5. Data safety: for an APK installed over the existing app, verify application ID, increasing version code, and same signing certificate; back up data when feasible; update in place only. Never uninstall/clear data-bearing StrideLab to get a passing test. Preserve Garmin history, GPS, account isolation, offline cache, and title edits.
6. Approval and promotion: open or update a `dev → main` PR; review changes and checks; merge only when the owner explicitly authorizes the exact commit. Tag approved release/checkpoint after merge. If a defect is found, keep it on `dev` and re-test.
7. All new development branches start from latest `dev` and merge back into `dev`, never directly into `main`. The release branch may trail `dev` intentionally.

## Bootstrap state — do not overwrite main

On 2026-11-11:
- Existing `main` HEAD was `49824dc1c0e4c4825398f76ac5f5ea71bd145ba5`. It contains two README-only commits beyond its common ancestor with the modern Android code.
- The latest selected-test-passing Android integration commit was `60325fcbb655d3236c6c8adcad572f7166293db4`, which still had a user-reported Edit/map-control regression; it is **not** approved for `main`.
- `dev` starts from that integration commit to continue the correction. The activity Edit/map change is pending instrumentation and owner acceptance.
- The first promotion to `main` is a substantial historical reconciliation. Preserve both existing `main` README commits and the approved `dev` source via a regular merge/PR with conflict review; **no force update** to `main`. Do not claim that `main` already matches the Android dev app.
- The Android Studio workspace may have separately staged, uncommitted fixes and an intentionally modified `native-android/gradle.properties` plus `.idea/` folders. Never reset or overwrite those. Do not include the local version-code override or IDE folders in a feature commit.

## Day-to-day commands

```powershell
# Verify dev before editing
git branch --show-current
git status --short
git pull --ff-only origin dev

# After owner-run validation and staging ONLY intended code
git diff --cached --check
git commit -m "fix: description"
git push origin dev

# Promotion: use a PR from dev to main, never an unreviewed direct push.
```

`main` represents the last owner-accepted release, not automatically the latest Android workspace. Branch protections requiring PRs and checks should be enabled in GitHub repository Settings when supported by account permissions.
