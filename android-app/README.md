# StrideLab Phase 4E — integrated private Android pre-alpha candidate

**Android-first development:** current architecture, implemented native storage/session modules and verification limits are recorded in `../docs/ANDROID-FIRST-ARCHITECTURE.md`. The text below describes the preserved Phase 4E source baseline. The native modules do not yet make its demonstration UI a real-data daily-use application.

This is a *source archive*, not an APK. It builds a private native Android shell over the **GitHub-accepted Phase 4D** prototype without rewriting its coaching engine or inventing new server dependencies.

## Files

- `android/`: Java Android project; full offline HTML and 21 local files in `app/src/main/assets/www`.
- `scripts/verify_phase4e.py`: exact-accepted-upstream and security/provenance verification (needs Phase 4D ZIP).
- `docs/PHASE4E-IMPLEMENTATION.md`: implementation limits and detailed physical-device checklist.
- `docs/SIGNING-SETUP.md`: private release signing, same-certificate update policy.
- `SHA256-MANIFEST-Phase4E-Final-candidate.json`: SHA256 of archive members (created at packaging).

## Planned single acceptance gate

Publish this ZIP unchanged to *private* prerelease tag `phase4e-final-candidate` in `Isshhin/stridelab-development`, then manually run the dedicated GitHub Actions workflow `.github/workflows/phase4e-final-android.yml`. It obtains the accepted Phase 4D ZIP, validates provenance/manifest, installs an Android build toolchain, compiles APK, runs backup unit tests and emulator smoke tests, and uploads private APK artifacts. Completion of the automated gate is not the same as successful physical-device testing. Do not mark Phase 4E device-accepted without running the manual checklist.

The original production repo `Isshhin/stride-lab` remains untouched.
