# StrideLab non-engine source candidate ledger

Updated: 2026-11-18, Asia/Manila. **Private developer-only work.**
Owner direction: **implement the non-engine features first; do not install on any emulator or phone yet.** Gradle/instrumentation and visual QA are combined future acceptance gates, not silent claims of completion.

## Implementation stack

All PRs remain **draft and unmerged**, stacked serially. Current leaf: `feature/nonengine-manual-backup-completeness-20261118` at `1c200eb27bbb6bf6902722ecea64fb86269b8e7e`. The stack begins from Share Studio draft PR #143, which itself is stacked on an unmerged Engine 1.5 development branch. **Do not merge the stack into production as an indirect coaching-engine cutover.** Preserve the no-engine-integration decision.

| Phase | PR | Source delivered | Acceptance |
| --- | --- | --- | --- |
| A1 | #144 | Optional separate Android QA package/label, preserving original package storage | Static review only; install/signing checks unexecuted |
| B1 | #146 | Offline running mileage and elapsed-time summaries across 8 weeks/12 months/5 years | Owner reported a successful Gradle invocation after SDK fix; exact task unconfirmed |
| B2 | #147 | Same-source/sport/distance two-run descriptive comparison | Tests authored, not executed |
| B3 | #148 | Provider, running discipline and distance filters | Tests authored, not executed |
| C1 | #149 | Local, editor-only Share Studio photo background with bounded image decoding and PNG render | Android photo permission/export tests authored; not executed |
| D1 | #150 | Owner-scoped offline manual strength/calisthenics/conditioning logs, notes and archive | JVM/Android tests authored; not executed |
| C2 | #151 | Strict cached-map coverage for offline Share Studio MapLibre snapshot | Android geography tests authored; no actual offline snapshot run |
| D2 | #152 | Reusable manual workout templates; schema-2 combined backup, schema-1 import and non-destructive DB migration | JVM/Android tests authored; not executed |
| B4 | #153 | Distance-matched recorded pace bests (NOT certified race PRs) | Tests authored, not executed |
| A3 | #154 | About screen shows package/version/build identity | Android UI test authored, not executed |
| C3 | #155 | Optional, approximate Instagram Story safe-area overlays in editor only | Android UI test authored, not executed |
| B5 | #156 | Lazy, filter-aware history browser with navigation to real Activity Detail | Android UI test authored, not executed |
| C4 | #157 | Adjustable offline map-selection box, projection matched to the visible box | JVM test authored, not executed |
| C5 | #158 | User-configurable metric order in Share Studio, saved with legacy-compatible presets | Tests authored, not executed |
| B6 | #159 | Canonicalize equivalent run labels without asserting road terrain | Tests authored, not executed |
| A4 | #160 | Reject over-limit manual-log/template backups instead of silently truncating; warn unencrypted JSON | Android tests authored, not executed |

PR #145 is the tracking issue, not a pull request. The standalone original Share Studio fix is in PR #143.

## Reused existing app features — do not reimplement blindly

- More → Offline Maps already includes box-based map selection, MapLibre offline regions, bounded tile estimates and download limits, Wi-Fi download requirement, pause/resume, saved areas, storage totals and deletion. New code only adds selection sizing and conservative Share Studio offline reuse.
- Garmin Settings already displays last sync status, imported summary count, recordings downloaded, pending recordings and failed details. Garmin authentication is local; StrideLab sign-in is optional and required only for optional cloud backup.
- Source/FIT integrity checks, account-scoped caches, plan schema-1/2 backup reading, insertion checks and SQLite transactions already exist. Do not replace them without fixtures proving correctness.
- Today/Training/More timeline and native Activity Detail exist. Reuse existing recorded metrics; never fabricate Garmin split streams, elapsed/moving substitutions, GPS, pace, HR or training scores.
- Share Studio already supports export through the Android share sheet, real alpha PNGs, Activity Dark maps, route privacy trimming and saved style presets.

## Deliberately still open before full non-engine product acceptance

1. Full local **activity/FIT stream** backup and cross-version recovery safety is **not** implemented by the new manual-session backup. Existing plans and optional Garmin cloud backups are different scopes. Full archive design must handle private credentials, large activity_detail payloads, owner isolation, partial failures and transactionality before any destructive update. Do not claim all user data are backed up.
2. Garmin importer end-to-end failure, idempotency and owner-isolation fixture verification is pending; source has relevant safeguards but needs run evidence.
3. Existing unified Training timeline, signed-out history, multi-run day cases and 360dp/412dp accessibility are not newly verified by these PRs.
4. Share Studio now supports metric order (#158), but unrestricted drag-anywhere placement of map/stat blocks and full third-party Instagram Story delivery remain unfinished. Approximate guides are preview-only.
5. Offline vector tile availability, style assets, attribution and cached snapshot behavior must be checked against real downloaded regions when emulator acceptance is authorized.
6. Manual log history is independent of Garmin and coach plans; no automatic activity timeline/plan linking or fitness scores are invented.
7. No analytics collection/upload toggle is needed for this local-only descriptive analytics implementation. Network telemetry remains unapproved.
8. Performance and security under large history, malformed backups, repeated imports and account switches require final acceptance.

## Consolidated test gate — not run

Before recommending any installation, check source/build with Windows JDK/Gradle and SDK configured in the isolated non-engine QA worktree:
- Focused JVM classes: `ActivityHistoryAnalyticsTest`, `ActivityRunComparisonTest`, `ActivityAnalyticsFilteringTest`, `RecordedDistanceBestsTest`, `OfflineSelectionRectTest`, `ManualDisciplineCodecTest`, `ManualWorkoutTemplateTest`, and `ShareStudioTest` (metric-order regressions).
- Android classes: `ActivityRunComparisonUiTest`, `SharePhotoRendererAndroidTest`, `StrideOfflineCoverageAndroidTest`, `ManualDisciplineStoreAndroidTest`, `BuildIdentificationUiTest`, `ActivityAnalyticsBrowseUiTest`, and existing `ShareStudioUiTest`.
- Compile all affected Kotlin sources and manifest merge; verify debug `-PstrideIsolatedQaInstall=true` resolves package `io.stridelab.privatealpha.qa`, with no normal-package replacement.
- Emulator only when the owner authorizes the **single combined acceptance**; use separate QA package, disposable data, explicit `adb -s` serial, no physical-device automated tests.
- Any failing build/test must be repaired on the isolated branch before an owner go/no-go on merge. A successful source diff is never a successful Gradle run.

## Release guardrails

No Engine 1.5 code is intentionally changed in the non-engine PR diffs. No coaching release flag was promoted. No PR was merged, no APK installed, and no original app data was accessed by these GitHub connector edits. **Do not proceed to QA yet:** full activity/FIT archival recovery and unrestricted layout controls are not implemented. Real user-data recovery and signing compatibility remain release blockers until tested.
