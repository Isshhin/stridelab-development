# StrideLab Batch 6 source checkpoint — Steps 38–41

**State:** Draft, uncompiled, **not** an end-to-end FIT activity importer or live Garmin integration. Base: verified native checkpoint `25be8f3`. No schema changes or installed data modifications.

## Added

- **P38 — `FitContainerReader.kt`:** bounded (16 MiB) FIT structural inspection of 12/14-byte headers, declared data boundaries, header/file CRC16, definition and opaque data records, compressed-timestamp header flag, and SHA-256 provenance. Rejects chained/oversized/unsupported files rather than silently creating partial activity histories. `FitContainerReaderTest.kt` includes synthetic valid, corrupted, truncated and identity scenarios. Grounded in Garmin's FIT protocol: https://developer.garmin.com/fit/protocol/
- **P39 — `ActivityImportMatcher.kt`:** account-scoped provider/external ID or exact file hash identity; ambiguous same-date/duration/distance records are review candidates, NEVER automatically linked/merged; separate Kotlin tests.
- **P40 — `WearableProviderContracts.kt`:** fixture-only `WearableImportProvider`, bounded Garmin mock paging and explicit unit/HRV-method wellness normalization with tests. No authorization flow or real network endpoint.
- **P41 — `FitFileIntake.kt`:** read-only Android file-URI inspection and single-file share-intent acceptance (content/file schemes), with Android instrumentation tests. No activity persistence or UI picker enabled.

## Still required before declaring native FIT import complete

1. Profile-aware, official FIT field scaling/sentinel/endianness handling and time reconstruction (prefer vetted Garmin FIT Java SDK or validated native adapter). Current reader intentionally returns **opaque raw fields**, not imported distance, HR, GPS or strength sets.
2. Authentic diverse running, cycling and Garmin strength FIT fixtures, including developer fields and malformed/oversized cases; compare against independent reference decoder outputs.
3. Transactional account-scoped import and source provenance in SQLite, including Step 31 link integrity. No workout mutation until data and source identity are verified.
4. Android ACTION_OPEN_DOCUMENT picker and registered safe share-target UX, full permission and cancellation testing; only a backend inspection function is implemented now.
5. Direct Garmin auth and provider sync remain deferred; the mock is not a live connector.
6. Run exact-head Gradle/JVM/Android instrumentation tests before integration; tests are authored but NOT executed. Keep web frontend maintenance-only.

**No GitHub Actions dispatch, merge, APK, release or device installation occurred.**
