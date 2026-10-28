# Engine 2.0 Audit and Corrections — Phases 14–17

**Reference:** Verified Engine 1.0 baseline 25be8f3; candidate Engine 2.0 draft PRs #13–#17.  
**Result:** Source-only hardening. No hosted CI or emulator execution; no modification of original Engine 1.0 or installed database.

## Verified against repository source

- Real exercise catalog: `native-android/app/src/main/assets/native-library/catalog.json` contains 44 entries. All 18 IDs previously present in `Engine2ExerciseSelection` were located by direct comparison, **but six IDs are explicitly `guide: true`** (hand-pike, hand-wall, core-tuck, core-hollow, pull-hang, pull-scap). They are educational images/notes rather than executable workouts. The selector now excludes these six from workout prescription options, while retaining them as non-executable progression references. The prior test wrongly expected hollow-body instructional guidance to be eligible for a live workout; corrected.
- Existing Android `TrainingStore.java` is in the legacy app source set included by `native-android/app/build.gradle`. Its database file is `stridelab-athlete-v1.db`, SQLite version **1**, containing records, outbox, conflicts, cursors and resolutions. `onUpgrade` deliberately throws rather than risking destructive migration. **Do not increment its schema or write Engine 2.0 ledger data into those tables without separately verified migration paths.**
- Original PR #16 Compose review card accepted `persistenceVerified: Boolean` supplied by callers. Such a flag is not a durable proof of persistence and could accidentally enable actions before real SQLite tests. Removed callbacks and enabled-state override; actions remain always disabled in the experimental preview.
- Original Phase 4 generator silently used only the first `preferredQualityDays` entry while accepting larger sets. Input validation now fails closed for >1 quality day, overlapping long/quality days, or quality requested with <2 running sessions. This is intentional until a full multi-quality, multi-session optimizer has accepted tests.
- `Engine2ProviderBoundary` is just a synthetic adapter with conversion and no real Garmin auth. Source provenance collisions are **not** physical-event deduplication.
- `Engine2VolatileLedgerStore` is only a correct-by-reference in-memory compare-and-swap store. It **does not** survive process death.

## Corrections applied

1. Explicit `executable` flag and `guideOnlyIds` in exercise selection, with executable-only filtering.
2. Planner rejects incompatible or silently truncated quality-day requests.
3. Preview approval UI cannot enable itself without an actual future storage implementation.
4. New `Engine2HardeningTest` regression checks.

## Remaining audit limitations

A full Android Kotlin compilation, emulator testing, access to real installed records, rigorous physiological training-policy review and actual Garmin credential verification have **not** been performed. These are candidates for Phase 26+ acceptance.

Do not merge into the verified baseline until exact-head tests and user-data-safe migration checks succeed.
