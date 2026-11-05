# Known defects and limitations

**Approved new target:** [Engine 1.5 primary / Engine 1.0 controlled fallback / quality-session requirements](engine15-primary-plan-quality-requirements-20261105.md). The new items below remain open despite green historical Engine 1.0-default QA.

| ID | Severity | Status | Reproduction / impact |
|---|---|---|---|
| QA-20261105-01 | P2 | Open limitation | Garmin Settings can authenticate and query the official activity source, but returned pages are not yet committed to `TrainingRepository` history. The UI states this explicitly; existing history is not changed. |
| QA-20261105-02 | P2 | External verification blocked | A live Garmin connection cannot be verified without the owner's Personal API key. Do not fabricate a key. Expected final wording: `LIVE GARMIN CONNECTION REQUIRES USER PERSONAL API KEY`. |
| QA-20261105-03 | P2 | Verification pending | Physical-phone smoke acceptance has not been rerun on the current commit. The successful exact-head MARC emulator run must not be described as phone acceptance. |
| QA-20261105-05 | P1 product/engine mismatch | Normal new plans use Engine 1.0 because `ENGINE15_ACTIVE` defaults to false; user-approved current target is Engine 1.5 primary with Engine 1.0 as controlled fallback. No code correction or new exact-head acceptance yet. |
| QA-20261105-06 | P1 quality intent and QA gap | Running flow may initialize `qualityCount=0`; user quality style is advisory and the production builder test checks session count but not eligible requested threshold/tempo prescriptions or persistence. The exact cause of the user's device observation still needs reproduction. |
| QA-20261105-07 | P1 UI/engine policy discrepancy | UI enables two quality sessions from 30 km/week; Engine 1.5 caps to one below 40 km/week. Some requested quality is converted to easy based on time, deload, taper or return constraints without a clear side-by-side requested/generated explanation. |
| QA-20261105-08 | P2 QA traceability | Detailed functional matrix still contains 35/41 `NOT RUN` outcomes even though broad automated suites passed; reconcile per-behavior evidence rather than declaring all features accepted. |
| QA-20261105-04 | P1 delivery blocker | Confirmed signing incompatibility | The signed QA APK certificate `62725b9237a0a7f182d99503b16f20d7b2f47be5f99ce07fc59b5e30cd8dd270` differs from the recorded version-14 certificate `fbf186fc95d790c566583830bfca6942c017e77c4968dace023bf490114efe95`. Do not replace, uninstall or clear the existing installation. Use a separate device/profile/emulator until the original compatible signing path is recovered and the live installed APK passes a read-only comparison. |

| QA-20261105-09 | P1 misleading Garmin error | Observed / code-confirmed | Emulator E01: `GarminSettings.kt` displays “Authentication expired or needs attention” for **every** `error` status, including activity-parser/protocol failures. Classify errors accurately; source code correction and regression pending. |
| QA-20261105-10 | P1 Garmin source check failure | Observed / exact cause unverified | Emulator E02: “Garmin activity has an invalid timestamp.” Parser accepts limited ISO formats; exact live timestamp not inspected. Test sanitized matching fixtures before changing accepted formats; no silent corrupted-data import. |
| QA-20261105-11 | P2 account session warning | Observed / investigation pending | Emulator E03: “Your saved session has expired or was revoked.” This is StrideLab account authentication, not Garmin token state. Determine genuine expiry vs refresh defect; keep offline history/account isolation. |
| QA-20261105-12 | P2 More-screen layout density | User observation / verify scale | Emulator E04: Garmin Connections card uses substantial vertical space while Today looks balanced (E05). Compare same device/font/display size before adjusting text/card density. |
| QA-20261105-13 | P3 requested obsolete UI removal | Requested / not implemented | Emulator E06 and E07: remove Legacy Data & Backups card, Open preserved prototype, and Private testing notice, potentially empty Device & Data header. Preserve actual history, backup/restore and migrations. |

No item may be removed from this file until a correction commit and passing regression evidence are recorded in [resolved defects](resolved-defects.md).
