# StrideLab Trackside 3.0 — QA / physical-device handoff

Keep PR #36 draft and unmerged until exact-head CI and phone acceptance pass. An emulator test is not proof that an existing athlete's physical-phone app was upgraded without loss.

## Cloud gates
- Android build and unit tests; Exercise Library 2.0 strict manifest/source SHA integrity, 15 pure-JVM domain tests and full Android tests.
- Both 360dp/1.3x and 412dp/1.5x screenshots and instrumentation; seven-day calendar must reveal any selected day without shrinking touch targets.
- Native five-tab / activity detail / library / settings smoke; offline/persistence regression; synthetic legacy APK seed and in-place update.
- Candidate workflow publishes APK, manifest info, signing-fingerprint evidence and SHA-256 tied to an exact commit.

## Do not risk the installed app
The Actions QA debug APK uses an ephemeral CI signing certificate. Android rejects in-place replacement by a differently signed APK, even when the package name matches. Do not uninstall your current app, clear data, or bypass signature verification to make a test build install.

A legitimate update over the real installation requires the SAME signing cert, greater versionCode and a separately preserved backup. Compare both fingerprints before touching the device. Never upload real signing keys, session tokens, saved GPS traces or private athlete datasets into a public CI artifact.

## Phone acceptance
1. First use a spare or clean test device for the downloaded debug APK. Inspect Today/Training/Activity/Library/More, navigation back-stack, large-font clipping, offline reopen and Library 2.0 searching.
2. Record installed app versionCode/signing fingerprint and keep an independent supported backup of original records before any in-place update on a data-bearing phone.
3. Only with the matching-signature APK, install over the existing package without erasing data. Confirm history, planned sessions, saved drafts/outbox, profile isolation and access to preserved prototype.
4. Inspect real GPS trace only when actual cached source coordinates exist. Verify Activity detail presents primary recorded metrics before route and graphs.
5. Report device, Android version, font scale, app version, clean/in-place install and reproducible defects, omitting sensitive personal data.

## Scope
The V2 exercise catalog remains read-only educational discovery. Coaching engine progression and training plans are unchanged; unverified visual media stays hidden. Physical upgrade and production release are not approved by synthetic CI.
