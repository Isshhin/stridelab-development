# Permanent StrideLab UI workspace (Windows / Android Studio)

Owner decision, 2026-11-06: **one existing UI worktree, one Android Studio project**, reused for all Today, Training, Activity and later UI work. Do **not** create per-screen Android Studio project folders. The main `stridelab-development` checkout, its staged/untracked changes and `stash@{0}` are separate and must remain untouched.

## Project baseline

- Remote UI integration branch: `ui/trackside-dev` (work in progress, not automatically accepted/merged).
- Permanent folder: `C:\Users\marcs\StudioProjects\stridelab-ui`.
- Android Studio project: `stridelab-ui\native-android`.
- Pinned **Gradle wrapper: 8.13**, supplied in `native-android/gradlew.bat`, `native-android/gradlew`, and `native-android/gradle/wrapper`. These are versioned files; Android Studio should select **Wrapper** instead of the latest installed Gradle.
- Gradle JDK: **Temurin 17**; select it once per persistent project in Android Studio. Local machine's installed JDK path need not be committed.

## One-time migration — REUSE, do not create a new checkout

Close Android Studio for the old Today worktree, then run the PowerShell block below. The move preserves its existing ignored files and IDE data. It **does not delete** the original checkout or any stash.

```powershell
$repo = "C:\Users\marcs\StudioProjects\stridelab-development"
$old = "C:\Users\marcs\StudioProjects\stridelab-today-redesign-20261106"
$ui = "C:\Users\marcs\StudioProjects\stridelab-ui"

if (-not (Test-Path $ui)) {
    if (-not (Test-Path $old)) { throw "Neither the current UI worktree nor the permanent UI folder was found." }
    git -C $repo worktree move $old $ui
    if ($LASTEXITCODE -ne 0) { throw "Git worktree move failed; no further action taken." }
}

if (-not (Test-Path (Join-Path $ui "native-android\settings.gradle"))) {
    throw "Permanent folder isn't the expected StrideLab UI worktree."
}
git -C $ui fetch origin ui/trackside-dev
if ($LASTEXITCODE -ne 0) { throw "Fetch failed." }
if ((git -C $ui status --porcelain --untracked-files=no)) {
    throw "Tracked modifications exist in UI worktree; preserve/review them before switching."
}
git -C $ui switch --detach origin/ui/trackside-dev
if ($LASTEXITCODE -ne 0) { throw "Cannot switch safely; existing files left intact." }
git -C $ui rev-parse --short HEAD
```

Open `C:\Users\marcs\StudioProjects\stridelab-ui\native-android` **once** in Android Studio.

Under Settings → Build Tools → Gradle:
- Gradle distribution: **Wrapper** (Gradle 8.13, now tracked in repository)
- Gradle JDK: **Temurin 17** (`C:\Users\marcs\.jdks\temurin-17.0.20.1` on this machine)

Apply, sync, then perform all emulator debugging and builds *locally in Android Studio*.

## All future updates

ChatGPT/GitHub should place UI edits on `ui/trackside-dev`, not create a new folder. Owner can update **the same** UI workspace:

```powershell
$ui = "C:\Users\marcs\StudioProjects\stridelab-ui"
git -C $ui fetch origin ui/trackside-dev
if ((git -C $ui status --porcelain --untracked-files=no)) {
    throw "Tracked local edits present; preserve them before switching."
}
git -C $ui switch --detach origin/ui/trackside-dev
```

After the update, use Android Studio **Sync** and **Run** in the existing project window. No need to configure Gradle again or reinstall Java.

## Safety and ownership

- **Codex edits source only.** No Gradle builds, tests, APK assembly, emulator or ADB use, unless the owner explicitly requests an exception.
- **Owner uses Android Studio** for compilation, tests, emulator and UI acceptance.
- **ChatGPT documents** owner-confirmed outcomes; no unnecessary Codex reports.
- Never use `git reset --hard`, `git clean`, delete/apply stashes or overwrite staged/untracked work as part of this process.
- If the `stridelab-ui` folder already exists but is **not** a registered Git worktree, stop and investigate; do not replace it.
