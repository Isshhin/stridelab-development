# Known defects and limitations

| ID | Severity | Status | Reproduction / impact |
|---|---|---|---|
| QA-20261105-01 | P2 | Open limitation | Garmin Settings can authenticate and query the official activity source, but returned pages are not yet committed to `TrainingRepository` history. The UI states this explicitly; existing history is not changed. |
| QA-20261105-02 | P2 | External verification blocked | A live Garmin connection cannot be verified without the owner's Personal API key. Do not fabricate a key. Expected final wording: `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`. |
| QA-20261105-03 | P2 | Verification pending | Physical-phone smoke acceptance has not been rerun on the current commit. The successful exact-head MARC emulator run must not be described as phone acceptance. |
| QA-20261105-04 | P1 delivery blocker | Confirmed signing incompatibility | The signed QA APK certificate `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270` differs from the recorded version-14 certificate `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95`. Do not replace, uninstall or clear the existing installation. Use a separate device/profile/emulator until the original compatible signing path is recovered and the live installed APK passes a read-only comparison. |

No item may be removed from this file until a correction commit and passing regression evidence are recorded in [resolved defects](resolved-defects.md).
