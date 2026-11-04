# Feature inventory

| Component | Expected behavior | Authority |
|---|---|---|
| Running weekly setup | Each occupied weekday selects its owning session. Moving a session occurs only through the explicit editor. The chosen identity and weekday persist into review/generation. | Figma `v73TsYo6gKxUoiUock2sSE`, node `548:95`; `TracksideFigmaRunningSetup.kt` |
| Activity details | A fixed map remains behind a partially expanded/expanded draggable sheet. Sheet content scrolls independently. Back collapses an expanded sheet before leaving detail. | Figma nodes `551:235`, `551:365`; `TracksideFigmaActivityDetail.kt` |
| Activity tabs | Summary, stats, and related views expose selected tab semantics and keep recorded values source-backed. | Figma `551:235`; Activity UI tests |
| Plan builder | Trackside staged wizard creates a real Engine 1.5 draft when the build flag is active, supports explicit review, approval, persistence, and offline reopening. | `PlanBuilder.kt`; `TracksideEngineBuilderIntegrationTest.kt` |
| Engine 1.5 | Frozen deterministic policy baseline. Garmin data must not silently change coaching inputs. | Engine 1.5 tests and current handoff |
| Library 2.0 | Validated on-device educational catalog. Catalog entries do not automatically create workouts; reusable sessions start from Templates. | `NativeLibrary.kt`; `ExerciseLibraryV2.kt` |
| Garmin Personal API foundation | Official API-key token exchange, short-lived bearer token lifecycle, activity search, account-scoped encrypted credentials, provenance, and explicit dedupe classification. No unofficial endpoints or refresh-token invention. | `GarminPersonalApi.kt`; official Garmin links in current handoff |
| Existing sources | Garmin, Intervals.icu, and file imports are independent sources. Uncertain cross-source matches require review and must not silently delete data. | `ActivityImportMatcher.kt`; provider contracts |

The full application inventory and current status matrix live in the [functional behavior audit](../reports/TRACKSIDE-FUNCTIONAL-BEHAVIOR-AUDIT-20261105.md).
