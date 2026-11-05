# Trackside signed QA APK design

## Intent

Produce a privately downloadable, signed QA APK from the latest verified Trackside branch while protecting the data-bearing phone installation. The build is for personal testing, not a public release.

## Goals

- Build the unchanged application source on the existing `MARC` Windows x64 runner.
- Preserve package `io.stridelab.privatealpha`, packaged Engine 1.0, Engine 1.5 opt-in compatibility, and Engine 2.x.
- Verify the APK with `aapt` and `apksigner`, record its SHA-256 and public certificate fingerprint, and upload only the APK plus public evidence.
- Compare the candidate with the recorded version-14 artifact: version code `14`, signer `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95`.
- Fail before building if MARC has no existing debug keystore, so Gradle cannot silently create a substitute key.

## Non-goals and anti-goals

- No phone installation, uninstall, data clear, package rename, branch merge, public release, or production signing change.
- No newly generated signing key and no attempt to recover or expose a private key.
- Signature verification alone never establishes live-phone update compatibility.

## Approaches considered

1. **Existing durable private signer:** preferred in principle, but repository/environment signing secrets, the expected signer variable, and prior durable artifacts are absent. This path must fail closed.
2. **Existing MARC debug signer:** selected. It can produce an installable QA APK without exporting the keystore. The workflow verifies whether its public fingerprint matches the recorded version-14 signer.
3. **Fresh CI debug key:** rejected because it cannot safely update the data-bearing installation and would violate the no-substitute-key requirement.

## Compatibility decision

- Candidate signer differs from recorded signer: `INCOMPATIBLE_WITH_RECORDED_VERSION_14_SIGNER`; use only a separate device/profile/emulator and never uninstall the existing app.
- Signer matches, package matches, and version code is greater than 14: `UPDATE COMPATIBILITY UNVERIFIED`; the historical metadata is compatible, but the actual installed APK was not pulled from the phone.
- A live read-only installed/candidate comparison and physical update smoke are required before calling an in-place update verified.

## Delivery

A workflow-dispatch-only job runs on `[self-hosted, Windows, X64]` with `contents: read`, validates the exact checkout and private repository, executes the required non-emulator checks, builds the default debug APK with the pre-existing MARC keystore, verifies metadata/signature, and uploads a 21-day private Actions artifact. The complete exact-head emulator workflow runs separately and serially before distribution.
