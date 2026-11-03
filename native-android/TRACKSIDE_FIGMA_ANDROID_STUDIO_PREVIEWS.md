# Trackside Figma comparison — Android Studio previews

**Branch:** `design/trackside3-figma-parity-no-build-20261103`
**Authoritative Figma:** `v73TsYo6gKxUoiUock2sSE`, APPROVED MASTER GALLERY.
**Build restriction:** Do NOT run Gradle, produce an APK, merge this into the build-triggering branch, or publish screenshots as visually accepted until the user approves. Preview tooling may compile layout code internally but does not package or deploy an app by itself.

## Open and inspect

1. In the existing Android Studio project, Git **Fetch** / **Pull** this isolated branch. Commit or stash any local changes first; do not reset them away.
2. Open `native-android/app/src/main/java/io/stridelab/privatealpha/TracksideFigmaPreviewGallery.kt`.
3. Select Code/Split/Design → **Compose Preview**. Refresh preview after Gradle Sync. The project already declares `androidx.compose.ui:ui-tooling-preview` and debug `ui-tooling`.
4. Use the previews at **412 × 950 dp**; the compact goal state is **360 × 800 dp**. Compare each frame to its exact approved Figma target with matching status/navigation insets and theme.
5. Each preview uses *synthetic fixture values*, not athlete or production records. In particular, the Activity route may show a clearly labeled illustrative reference asset when GPS samples are absent.

| Preview / target | Expected |
| --- | --- |
| `551:580` | Discipline cards; colored selection; no checkboxes or bottom five tabs |
| `551:603` | Five running-goal cards; approved fixed Continue control |
| `614:1236` | Same goal view at compact 360 × 800 |
| `551:796` | Race/PR intent, distance selector, PR panel, target-date row |
| `551:865` | Running experience, weekly distance, longest run |
| `614:195` | Run frequency, E/Q/L cells, quality controls |
| `614:1374` | Advanced mileage slider, quality preference grid |
| `614:88` | Availability, provisional capacity, explicit multi-session policy opt-in if needed |
| `551:235` | Fixed decorative map, overlapping rounded glass sheet, tabs and recorded-data sections |
| `551:365` | Matte alternative of the recorded-activity sheet |

## Do not treat a preview as a verified Android app

- Compare the **rendered output** (type scale, background, vertical offsets, card widths, edges, content clipping, contrast and fixed CTA) against the exact Figma screenshot. Source-only geometry checks cannot establish pixel parity.
- For Activity, verify that the map stays visible while only the sheet scrolls. Actual run data must never be inferred from Figma artwork; the source-provided route is a schematic until a real geo-map implementation is integrated.
- Exercise the real navigation, data persistence, accessibility, offline behavior and plan approval only after explicit permission to run a local debug build and tests. Do not update the physical phone or release to others.
- An Engine 1.0-only goal input cannot truthfully store a target finish time, PR record, future race date, or enforce a quality-workout whitelist. Those UI affordances explicitly disclose unsupported behavior; never claim full Engine 2.0 coaching integration.

## Current unverified scope / stop conditions

- No Kotlin compilation, Compose render, screenshot-diff test or APK build was performed by the assistant on this source branch.
- Some dedicated Strength, Calisthenics and Conditioning builder flows still fall through to older controls and are **not 1:1** with their approved Figma frames.
- Inter typography and small custom icon assets in the Activity section are not confirmed as exact final design assets. Screens are **not approved** until independently rendered and compared.
- If preview shows errors, record the full build error, file/line and branch SHA before changing code. Prefer minimal source fixes; do not blame Gradle Sync for a Kotlin type error.

Review note format: `Figma node | Android preview name | viewport | discrepancy | screenshot path`.
