# Trackside Signed QA APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, verify, and privately deliver a signed Trackside QA APK without risking the existing phone installation.

**Architecture:** Add one dispatch-only MARC workflow that uses the runner's existing debug keystore, fails if it is absent, and emits only an APK plus public verification metadata. Run the established exact-head suite first, then independently inspect the uploaded APK and record the result in `docs/qa/`.

**Tech Stack:** GitHub Actions, Windows PowerShell, Gradle 8.13, JDK 17, Android SDK 35/36, `aapt`, `apksigner`.

**Spec:** `docs/superpowers/specs/2026-11-05-trackside-signed-qa-apk-design.md`

## Global Constraints

- Preserve package identity, version metadata, Engine 1.0 default, Engine 1.5 rollback behavior, user data, and Engine 2.x.
- Never create, upload, print, replace, or modify signing keys or signing credentials.
- Never install, uninstall, clear data, merge, or publish a release.
- Use only the existing `MARC` runner labels `[self-hosted, Windows, X64]` and serialize emulator work.

## Review Focus

- Missing MARC debug keystore must stop before Gradle can generate a new one.
- A signer mismatch must remain explicit in both artifact evidence and phone instructions.
- Artifact contents must exclude the keystore, passwords, tokens, and runner-private paths.
- The checkout SHA and APK metadata must be tied to the same workflow run.
- A matching historical signer must still not be called live-phone update verification.

---

### Task 1: Add the scoped MARC APK workflow

**Files:**
- Create: `.github/workflows/trackside-signed-qa-apk-windows.yml`

- [ ] Add manual dispatch, read-only contents permission, private-repository assertion, exact-checkout assertion, and `[self-hosted, Windows, X64]` labels.
- [ ] Verify the existing debug keystore exists before Gradle runs.
- [ ] Run schema/tooling, pure-JVM, Android unit, and debug APK build gates with Engine 1.0 default.
- [ ] Use `aapt` and `apksigner` to create public metadata, SHA-256, compatibility classification, and safe-install instructions.
- [ ] Upload only the APK and public evidence with 21-day retention.
- [ ] Commit and push the workflow.

### Task 2: Verify and build the exact source

**Files:**
- Verify: `.github/workflows/trackside-engine15-windows.yml`
- Verify: `.github/workflows/trackside-signed-qa-apk-windows.yml`

- [ ] Dispatch and wait for the existing full exact-head MARC workflow.
- [ ] If it succeeds, dispatch the signed QA APK workflow at that same SHA.
- [ ] Download the artifact and independently recompute package, version, SDK, signer, signature status, and APK SHA-256.
- [ ] Compare the signer to `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95` without installing anything.

### Task 3: Preserve the QA handoff

**Files:**
- Modify: `docs/qa/current-handoff.md`
- Modify: `docs/qa/environment.md`
- Modify: `docs/qa/known-defects.md`

- [ ] Record source SHA, workflow/job/artifact URLs, filename, hash, package/version/SDK, signer, signature status, build type, compatibility classification, and test results.
- [ ] Add phone instructions that prohibit uninstall/data clear and describe the safe alternative on mismatch.
- [ ] Commit and push documentation without changing executable source.
- [ ] Verify final local/tracking/remote heads and report the APK source SHA separately from the documentation head.
