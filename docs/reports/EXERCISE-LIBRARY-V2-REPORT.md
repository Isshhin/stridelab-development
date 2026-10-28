# Exercise Library 2.0 cloud engineering report

Implementation baseline: `25be8f3828374d1e539c7e825afa20fbcb652b56` (verified native guided/multi-session checkpoint). Main at inspection was `368b202128b0df46e76328e188dd971bafa501b7`, an archive checkpoint. Work branch: `feature/exercise-library-v2-cloud-20261029`; draft targets `checkpoint/native-guided-multisession-verified-25be8f3`. No ongoing draft was merged or modified.

All work runs in the cloud Work container and GitHub connector. No PC, device, local user checkout, live provider or background execution is required. Direct Git clone had no credentials; private source and isolated commits are handled by the GitHub connector. A materialized cloud source snapshot is checked against Git blob identities, not represented as a full cloned repository. 140 retrieved baseline files matched their actual repository blob hashes before testing. No `AGENTS.md` appeared in either inspected native baseline/Engine2 tree.

## Delivered

- Unchanged schema-1 catalog and all 44 original IDs/records/order/assets. New schema-2 companion with **66 records: 44 preserved/enriched + 22 added**, including 45 exercises and 21 educational guides.
- Multi-category coverage (not mutually exclusive): strength/gym 17, calisthenics 22, bodyweight 15, conditioning 4, mobility 6, running 10. See the checked-in coverage fixture for machine-readable counts.
- Structured muscles, equipment alternatives, movement patterns, difficulty, prerequisite notes/references, staged regressions/progressions, alternatives, technique, caveats, scoped text provenance and one media record per ID.
- Standalone Kotlin read-only offline loader, exact-ID lookup and deterministically sorted educational discovery. No native screen, coaching selection, persisted prescription, user-data schema or migration changed.
- JSON schema plus offline validation for duplicates, normalized title collisions, preserved legacy bytes, guide status, equipment vocabulary, provenance, reference integrity, reciprocal stages, acyclic progression/prerequisite graphs and media rights/path states.
- Meaningful corruption fixtures/tests, unchanged-baseline source hashes, exact PR #25 selector fixture and 73,728 actual selector queries. No new production Engine2 source is included.
- Integration/extension contract: `docs/EXERCISE-LIBRARY-V2-CONTRACT.md`.

## Media and provenance

No new third-party demonstration was copied. The manifest has **43 placeholders** (22 new exercises plus 21 inherited guides) and **23 inherited-unverified image uses**, with attribution/license/verification records and reasons. Eleven unique pinned Everkinetic source URLs were fetched; all 15 associated records matched their recorded source SHA-256. The pinned upstream `LICENSE.md` states CC BY-SA 4.0. Exact exercise fit/derivation and authorship are not certified merely by byte/license matching. Three existing original-schematic claims and five uses of one running context photo remain independently unverified. Zero media records are promoted to verified display. Source audit: `docs/reports/EXERCISE-LIBRARY-V2-MEDIA-AUDIT.json`.

NHS/ACE/NASM/CDC references are scoped per record; technique is independently paraphrased and editorial tags/skill gates are identified as such. Text provenance grants no image permission. Baseline text is explicitly inherited, not newly scientifically validated. No fabricated research, treatment or injury-prevention certainty is asserted.

## Executed verification at implementation checkpoint

| Command/check | Observed result |
|---|---|
| `python3 native-android/tools/validate_exercise_library.py` | Passed schema/content/graph/media checks, 66 records |
| `python3 -m unittest discover -s native-android/tools/tests -v` | 30 passed: 13 unchanged existing tests plus 17 new tests |
| `python3 native-android/tools/run_library_jvm_checks.py --cache ../work/jvm --offline` | 104 passed: 94 existing Java/Kotlin tests plus 10 new loader/selector tests; Java 17.0.20 and Kotlin 2.2.21 |
| Pinned Engine2 selector matrix | 73,728 queries passed within the JVM suite |
| Restored baseline Git-blob comparison | 140 retrieved files matched; zero mismatches |
| New-file whitespace check | Passed on explicitly staged deliverables |
| Android Gradle/Compose/unit build | Dedicated cloud CI configured; result pending inspection |
| Emulator/SQLite/device/lifecycle | Not executed by this workstream; no UI/persistence change; no result claimed |

Initial harness attempts exposed missing Java-source dependencies and the existing pure `historyWeeklyKm` helper in a Compose file. The harness now compiles the original Java dependencies and extracts that exact helper into temporary build output; it does not stub or edit production code. An early new cycle-test fixture interfered with existing reciprocal edges; the fixture was corrected before the passing run. The first cloud text materialization added terminal whitespace; original source bytes were restored and matched against Git blob hashes before final source validation. These were harness/materialization corrections, not baseline product fixes.

The broader Android workflow is separate from the direct JVM suite. It attempts `gradle --no-daemon --stacktrace -p native-android :app:testDebugUnitTest`, without APK publishing/signing/install steps. Exact-head CI status and final commits are recorded in the follow-up evidence checkpoint. The legacy native workflow is untouched.

## Remaining integration gates

- Review library content/taxonomy and conservative editorial prerequisite choices; no medical clearance is provided.
- Confirm exact-head Android compilation/unit CI. Direct JVM success does not verify Android packaging or Compose.
- Obtain/verify suitable demonstration rights and exact exercise fit before replacing placeholders or promoting legacy assets to verified in the new loader.
- Any native UI adoption or expansion of coaching's reviewed exercise whitelist is a separate approved change. This deliverable is usable through the offline Kotlin API while the current UI and engine remain unchanged.
- Keep draft; no merge, release, deployment, signing change, app install/uninstall or real user-data mutation.
