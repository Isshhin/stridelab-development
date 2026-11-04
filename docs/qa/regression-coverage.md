# Regression coverage

## Test selection index

| Feature | Test file / method | Verifies | Type / environment | Selection |
|---|---|---|---|---|
| Running occupied-day identity | `TracksideApprovedBuilderActivityUiTest` / all-slot identity and explicit move tests | Tapping an occupied day selects its owner; editor moves only the selected session | Compose instrumentation; API 35 emulator | Run the class |
| Activity sheet | `TracksideApprovedBuilderActivityUiTest` / drag, map, tabs, accessibility and Back tests | Partial/expanded anchors, independent scroll, fixed map, tab semantics, collapse-first Back | Compose instrumentation | Run the class |
| Four responsive profiles | `TracksideResponsiveQaTest` | 412/360 dp and font scales 1.0/1.3/1.5, reachability and screenshots | Serialized API 35 emulator profiles | Windows workflow or profile commands |
| Builder to persisted plan | `TracksideEngineBuilderIntegrationTest` | Real generation, approval, database persistence, offline reopen | Instrumentation + local database | Run the class |
| Legacy/current end-to-end behaviors | `Engine1UiTest` | Generation, same-day independent logs, library/templates, large text, draft restoration | Compose instrumentation | Run after builder/library/navigation changes |
| Navigation and visuals | `NativeNavigationTest`, `NativeVisualTest` | Current routes, Settings reachability, source-backed visual states | Compose instrumentation | Run after root navigation/layout changes |
| Garmin official client | `GarminPersonalApiTest` | Token contract, expiry, auth/rate/protocol/network failures, parsing, provenance, dedupe | JVM unit | `:app:testDebugUnitTest --tests=...GarminPersonalApiTest` |
| Garmin source lifecycle | `GarminPersonalSourceTest` | Connect, token reuse/re-exchange, paging, disconnect, account isolation, offline error | JVM unit | `:app:testDebugUnitTest --tests=...GarminPersonalSourceTest` |
| Garmin secure storage | `GarminCredentialVaultTest` | Keystore encryption, restart read, account isolation, clear, redaction | Android instrumentation | Run the class |
| Import dedupe | `ActivityImportMatcherTest`, `FitActivityDecoderTest` | Explicit IDs, hashes, possible same event, no cross-account match | JVM unit | Run both classes |

## Selection guide

- Pure model/parser change: focused JVM class, then the complete JVM suite.
- One Compose component: its focused instrumentation class, then related navigation/responsive class.
- Builder, persistence, navigation, database, or source boundary change: focused tests, integration test, then full instrumentation.
- Density/font/layout change: focused UI tests plus all four responsive profiles.
- Emulator jobs on `MARC`: one at a time. Never dispatch concurrent emulator workflows to the same runner.

## Current gaps

- No live Garmin test without an owner-provided Personal API key.
- Garmin activity-page persistence into main StrideLab history is intentionally not enabled in the foundation.
- Physical-phone smoke acceptance remains distinct from emulator evidence.
- Emulator screenshots establish reachability and layout, not touch ergonomics on a real phone.
