# Exercise Library 2.0 integration and extension contract

This is an additive, offline educational library. It does not replace Engine1/Engine2 selection, assign training, award skills, prescribe doses, migrate data, or change native screens.

## Exact source boundary

- Native baseline: `25be8f3828374d1e539c7e825afa20fbcb652b56`, branch `checkpoint/native-guided-multisession-verified-25be8f3`. The independent draft targets that checkpoint, not historical main or an unverified Engine2 branch.
- Current repository main at inspection: `368b202128b0df46e76328e188dd971bafa501b7`; it contains archived web/prototype material and workflows, not this native integration. It is not interchangeable with the Android baseline.
- No `AGENTS.md` was present in the recursively inspected baseline or latest Engine2 source tree. No parent workspace instructions were found in the fresh cloud workspace.
- Related Engine2 drafts #13–20 and #25 were inspected as context. #25 head `245349ca5c9e13b095f1dcca609a9bde93a3c79b` contains the latest observed `Engine2ExerciseSelection.kt`; its exact text is a test fixture, not new production Engine2 code. #18/#19 catalog identity/guide checks were read. These drafts are unverified context, not an integration approval.
- Native UI/Canva (#23), QA (#26/#22/#7), FIT/Garmin (#24), synthetic histories (#11/#12), and other draft branches are untouched. No merge, release, installation, provider connection or user-data operation belongs to this branch.

## Existing interfaces preserved

`NativeLibrary.kt` defines `LibraryEntry(raw: JSONObject)` and `readLibrary(context: Context)`, reading bundled `native-library/catalog.json`. That schema-1 file remains byte-for-byte identical, including order, 44 IDs, 23 executable entries, 21 guides, technique text, alternatives, asset references and the original prototype hash. The baseline fixture is a full identity/content snapshot, not just a count.

`ExercisePrescription(id, name, sets, reps, seconds, rest, equipment, cue)` is a persisted snapshot. Engine1 has explicit selections and synthetic fallback IDs, such as `supported-hang`, `bb-bench`, `bb-row`, `hollow-tuck`, `deadbug`, and `prone-w`; not every such ID is a legacy catalog ID. Do not equate these with similar guide names, rewrite historical prescriptions, or map an unknown ID by fuzzy title. Two additions deliberately use existing unambiguous Engine1 IDs `wall-push` and `plank`; loading them does not rewrite existing prescriptions.

Engine2's latest observed API is:

```kotlin
Engine2ExerciseSelection.available(
    discipline: Engine2ExerciseDiscipline,
    movement: Engine2Movement,
    equipment: Set<Engine2Equipment>,
    confirmedSkills: Set<String> = emptySet(),
    maximumSkill: Engine2Skill = Engine2Skill.FOUNDATION
): List<Engine2ExerciseOption>
```

The explicit 18-record reviewed list has 12 executable options and six non-executable guides (`core-tuck`, `core-hollow`, `pull-hang`, `pull-scap`, `hand-pike`, `hand-wall`). It filters discipline, movement, equipment, confirmed prerequisites and maximum skill, then sorts by skill ordinal and ID. Preserve all of that behavior. This library does not extend its enum types or reviewed whitelist. The pure JVM compatibility test compiles the exact pinned selector fixture and exercises all 512 equipment sets × 2 disciplines × 6 movements × 3 skill ceilings × 4 confirmed-skill sets = 73,728 queries.

## New companion interface

`exercise-library-v2.json` bundles 66 structured records, 44 enriched legacy records and 22 additions. All metadata is available offline. The new Kotlin loader has no Android, network, database, account, clock, random or provider dependency:

```kotlin
val library = ExerciseLibraryV2.load { path -> context.assets.open(path) }
val entry = library.find("machine-leg-press")
```

The loader reads exactly three bundled files: the unchanged legacy catalog, v2 library and v2 media manifest. It checks version, legacy content digest, preserved identities/instructions, duplicate IDs/names, source references, graph integrity, guide status and media/path constraints before returning records. Unsupported/corrupt companions throw; the existing `readLibrary` remains independent. No silent partial catalog or guide promotion is allowed. Runtime loading performs no source-URL resolution.

`discover(category, equipment, confirmedSkills, maximumDifficulty)` is educational discovery only. Results exclude guides and use stable ID order. Do not call it as a coaching replacement. A future engine adapter must **start with its current reviewed selector**, then attach metadata by exact ID. Do not union new discovery results into a plan. A consumer that chooses to filter reviewed results further must document that behavior and test it; this branch makes no such change.

Equipment options are OR between arrays and AND within each array. `[[]]` means no equipment. For example, row guides permit either a secure low bar OR rings with an anchor, while `br` requires BOTH band and anchor. Floor space and suitable footing remain setup notes. Normalized string tokens are separate from Engine2 enums; map them explicitly and fail closed for unknown equipment. Conditioning and mobility are educational categories, not new Engine2 disciplines. Calisthenics guide records remain guides even when their movement can be practiced elsewhere.

Difficulty and primary/secondary muscle involvement are editorial labels, not measured activation, individualized safety, or automatic skill prerequisites. `prerequisites` point to earlier capabilities; `progressions` point toward harder stages and `regressions` are reciprocal. These three graphs must be acyclic. Alternatives may form cycles; they are choices with different equipment, range, load and stimulus, never claims of equivalence. Guide nodes may explain a path but must not become prescriptions. Do not use graph traversal as automatic promotion.

## Text and media provenance

Each record links to source IDs with a scope statement. The source registry includes inspected NHS, ACE, NASM and CDC pages and the exact inherited catalog. New instructions are concise independent paraphrases. The retained legacy instructions are explicitly inherited, not described as newly scientifically validated. Anatomy, setup choices and staged links are identified as editorial judgments. No clinical clearance, treatment, injury-prevention, outcome guarantee, EMG ranking or fabricated study is claimed. The CDC reference supports activity intensity, not bicycle fit. The step-jack is explicitly an editorial no-jump variant.

`exercise-media-v2.json` has one record per exercise/guide, separate from text provenance. All 22 new exercises and 21 inherited guides use placeholder states. No new third-party image was copied. The 23 existing images are retained with their baseline attribution and rights claims, marked `inherited-unverified` in the new manifest. `verifiedDisplayFile` returns null for all currently bundled metadata; a future approved UI should show a neutral placeholder and instructions. This does not change the legacy UI's existing image display.

The media audit records per-asset source/license/verification URLs, fit, source hash checks and bundled PNG hashes. Eleven unique pinned Everkinetic source URLs were fetched and matched all 15 associated records. The pinned `LICENSE.md` was checked as CC BY-SA 4.0. This verifies source bytes and repository license, not technique equivalence, complete authorship chain, or arbitrary reuse permission. Several diagrams are explicitly related variants rather than exact demonstrations. Three original-schematic claims and five uses of one running context photo remain independently unverified. A context running photograph is not a technique demonstration. Public ACE/NASM/NHS technique pages were researched but no per-asset reusable grant was established; their images were not downloaded.

Before declaring an asset `verified`, record the exact asset URL, author, license and version, license verification URL, changes/derivation, exercise-fit review, local file hash and attribution obligations (including ShareAlike where applicable). Do not infer rights from search snippets, repo language/license badges, page access or text citations. Do not fetch demonstration assets at runtime. A placeholder is a null file plus an explicit unresolved-rights reason, not borrowed artwork.

## Safe extension and validation

1. Keep every baseline record and ID. Never repurpose an ID; use a new stable kebab-case ID for a materially different movement. No fuzzy aliases or migration of saved data.
2. Keep schema-1 catalog and existing extraction workflow unchanged. Edit the versioned companion, not the historical HTML/prototype. An intentional companion-breaking change requires a new schema/loader; `2.0.0` rejects unknown schema versions.
3. Add independently curated technique, equipment options, primary/secondary muscles, difficulty, prerequisite notes, caveats, regressions/progressions/alternatives, source scope and media state. Empty reference arrays are valid where no useful named relationship exists. Do not invent links to inflate graph density.
4. Sort records by ID and keep arrays unique. Extend schema token enums explicitly during review. Validate both dependency graphs and reciprocal stage links. Keep alternatives semantically defensible.
5. Run `python3 native-android/tools/validate_exercise_library.py` and `python3 -m unittest discover -s native-android/tools/tests -v` after installing development-only `jsonschema==4.26.0`.
6. Run `python3 native-android/tools/run_library_jvm_checks.py`, then repeat with `--offline` to forbid dependency downloads. Maven dependencies are pinned by SHA-256. This compiles all pure baseline Java/Kotlin unit tests, the new loader and exact pinned selector contract. The exact `historyWeeklyKm` helper is extracted from its existing Compose file into scratch build output; it is not stubbed or changed. This is not full Android/Compose verification.
7. Run Android `gradle --no-daemon --stacktrace -p native-android :app:testDebugUnitTest` in an Android SDK environment. The dedicated cloud workflow attempts this and uploads unit reports only. It has no APK publishing, signing, deployment or installation job. The original native workflow is unchanged and its branch filter does not include this workstream.

UI consumption, any automatic selection expansion, clinical review and new demonstration artwork are separate future integrations requiring the relevant approvals. Runtime works without the internet; URL checks are a development audit, not an application dependency.
