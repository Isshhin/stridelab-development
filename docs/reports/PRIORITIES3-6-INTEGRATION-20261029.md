# Priorities 3–6 integration audit — 2026-10-29

Baseline: `25be8f3828374d1e539c7e825afa20fbcb652b56`. Isolated branch: `integration/priorities3-6-20261029`.

All 25 open PRs (#2–#26) inspected using current GitHub metadata and local fetched heads. This is a scoped integration decision, not approval of every draft or a full line-by-line audit of all Engine 2 code.

| PR | Exact head | Base | Disposition |
|---|---|---|---|
| [#2](https://github.com/Isshhin/stridelab-development/pull/2) | `8df75bd436de0a8f5426e7b368bef8e47a9f93f2` | `checkpoint/native-engine1-goals-verified-7d5acdf` | Already represented by #3/baseline; do not merge divergent snapshot. |
| [#3](https://github.com/Isshhin/stridelab-development/pull/3) | `25be8f3828374d1e539c7e825afa20fbcb652b56` | `checkpoint/native-engine1-goals-verified-7d5acdf` | Already represented by exact baseline; do not merge duplicate snapshot. |
| [#4](https://github.com/Isshhin/stridelab-development/pull/4) | `92a80b7d8e28767d8d4b449d3ba79aab7ce927d9` | `main` | Roadmap only; current user priority order governs this work. |
| [#5](https://github.com/Isshhin/stridelab-development/pull/5) | `8f60c6ca26682b6debc28ffcf26854e8a4901908` | `checkpoint/native-guided-multisession-verified-25be8f3` | Baseline test reference; does not verify new head. |
| [#6](https://github.com/Isshhin/stridelab-development/pull/6) | `66036c1ea4db72a493ab5044c73ac29665a3dec9` | `checkpoint/native-guided-multisession-verified-25be8f3` | Read-only design audit reference; no design integration. |
| [#7](https://github.com/Isshhin/stridelab-development/pull/7) | `8790e7dbf3739c46947389cede79c3837d4f0eb3` | `checkpoint/native-guided-multisession-verified-25be8f3` | Completion fixes superseded by #26. Running label/UI held; workflow overlap excluded. |
| [#8](https://github.com/Isshhin/stridelab-development/pull/8) | `5bb58a15cc7849f7056d677c5aa5027c2451caa2` | `checkpoint/native-guided-multisession-verified-25be8f3` | Held: UI accessibility candidate needs independent visual review; not required for data integration. |
| [#9](https://github.com/Isshhin/stridelab-development/pull/9) | `26d57bc7d33e9ec44419f3aed765dae7e0021bfd` | `checkpoint/native-guided-multisession-verified-25be8f3` | Integrated explicit unlink guard and JVM/SQLite regressions; does not solve cross-plan transactional uniqueness. |
| [#10](https://github.com/Isshhin/stridelab-development/pull/10) | `5813b1091e01550c9108e8929bd66af86160665d` | `checkpoint/native-guided-multisession-verified-25be8f3` | Design only; production uniqueness migration requires reviewed backups, rollback and isolated upgrade evidence. |
| [#11](https://github.com/Isshhin/stridelab-development/pull/11) | `60ff51415f54caea7b3f0fd1307458125ccc360d` | `checkpoint/native-guided-multisession-verified-25be8f3` | Separate synthetic dataset candidate; no athlete records; hold until fixture policy reviewed. |
| [#12](https://github.com/Isshhin/stridelab-development/pull/12) | `265247ab7d94c1763cc77ba29ab39152361d9c1c` | `data/engine-validation-v1-20261027` | Stacked on #11; expansion not required for QA/FIT gate. |
| [#13](https://github.com/Isshhin/stridelab-development/pull/13) | `f30a030aa93d06c784bc521b09351b06608af40c` | `checkpoint/native-guided-multisession-verified-25be8f3` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#14](https://github.com/Isshhin/stridelab-development/pull/14) | `bde3b7ca0b8a588c34918dbeb58df77d6dcb58c1` | `prep/engine2-context-proposals-20261027` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#15](https://github.com/Isshhin/stridelab-development/pull/15) | `2f48fc22ca060a10580199d5b0eddc9009410656` | `prep/engine2-phases1-4-batch-20261027` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#16](https://github.com/Isshhin/stridelab-development/pull/16) | `96c381807e1f19adbe063647896487cc35fac93f` | `prep/engine2-phases5-8-batch-20261027` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#17](https://github.com/Isshhin/stridelab-development/pull/17) | `6b6ca35ad9452897079e412ea06f0f262eed89c3` | `prep/engine2-phases9-12-batch-20261027` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#18](https://github.com/Isshhin/stridelab-development/pull/18) | `def70a74b48b896ca5225e5560f30cb5f86fd0c8` | `prep/engine2-phase13-acceptance-batch-20261027` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#19](https://github.com/Isshhin/stridelab-development/pull/19) | `b8636b691a5dce64290117c0e9f2a20b221c4ec0` | `prep/engine2-hardening-phases14-17-20261028` | Held: stacked Engine 2 candidate (#13 → #14 → #15 → #16 → #17 → #18 → #19 → #20 → #25); independent compilation does not resolve persistence authority. |
| [#20](https://github.com/Isshhin/stridelab-development/pull/20) | `9e868ca0fe689c87a3084b71cfa0c86c043b8087` | `prep/engine2-hardening-phases18-21-20261028` | Blocked writable integration: separate Engine 2 ledger conflicts with TrainingStore approved-plan authority. |
| [#21](https://github.com/Isshhin/stridelab-development/pull/21) | `78a95a8b092406081aa9a7a455b121d250011e9c` | `checkpoint/native-guided-multisession-verified-25be8f3` | Architecture reference; proposals not owner-approved migration policy. |
| [#22](https://github.com/Isshhin/stridelab-development/pull/22) | `a6d6a1a067f4b096e1bd6c95ae116a0c30fe50ef` | `checkpoint/native-guided-multisession-verified-25be8f3` | Selected offline outbox instrumentation only. Logging overlaps #26; migration candidate and packaging excluded. |
| [#23](https://github.com/Isshhin/stridelab-development/pull/23) | `030ddb96e18a9d0aeaa60ea84479d02eab0f73b4` | `checkpoint/native-guided-multisession-verified-25be8f3` | Held: UI/design branch; avoid concurrent Canva scope and unaccepted visual changes. |
| [#24](https://github.com/Isshhin/stridelab-development/pull/24) | `73e2b8c7a5338cf03ae74142e60df68b9dde8feb` | `checkpoint/native-guided-multisession-verified-25be8f3` | Integrated foundation; extended with official SDK decoder and local original-file cache. Not a live provider. |
| [#25](https://github.com/Isshhin/stridelab-development/pull/25) | `245349ca5c9e13b095f1dcca609a9bde93a3c79b` | `prep/engine2-persistence-phases22-25-20261028` | Held behind #20; read-only projection may be reviewed independently after single-authority design. |
| [#26](https://github.com/Isshhin/stridelab-development/pull/26) | `06bbe48c0869734cceebc0a3711b72f42311690b` | `checkpoint/native-guided-multisession-verified-25be8f3` | Integrated QA source/tests; corrected Android JSON compile incompatibility. Android gates required. |

## Integration order and persistence boundaries

1. #26 performed-set and restore validation on baseline.
2. #24 standalone import identity/provider/FIT contracts.
3. #9 explicit unlink protection; #22 offline-recovery tests only.
4. Official Garmin SDK profile decoder and app-private atomic original-file cache; no schema changes, no outbox enqueue, no automatic training-plan links.
5. Exact-head JVM, APK compilation, isolated emulator/CI verification.

Existing `TrainingStore` v1 remains the only application plan store. Engine 2's separate approval ledger is excluded. Cross-plan source-link uniqueness is still not database-enforced; automatic matching remains blocked. FIT cache is an immutable source archive, not a second approved-plan ledger. Its same-file identity is account scoped; ambiguous physical events require review. No automatic cloud upload or inferred completion.

## Garmin authorization investigation

Official documentation inspected 2026-10-29:
- https://developer.garmin.com/gc-developer-program/overview/ now documents Personal access (own data, API key/token from Garmin Connect, pull only), Starter business (push only, limited users, no history), and Enterprise options.
- https://github.com/garmin/fit-java-sdk and Maven Central `com.garmin:fit:21.218.0` supply the official profile decoder. SDK decoding does not grant API authorization.

No Garmin credential environment variable or configured Garmin client/token implementation found in this checkout. No private credential value was printed or copied. Account portal eligibility and actual grants cannot be established from public pages. No application submitted, account connection created, credentials requested in chat, or live health data fetched. Next required owner step: provision an authorized Personal credential through an approved secure local configuration, or obtain the appropriate business app grant; inspect its actual scopes/docs before implementing transport. Do not reuse Personal tokens for other athletes or invent API endpoints. Existing authorized Intervals.icu gateway remains available, but no live-account validation was attempted. User-selected FIT remains the offline alternative.

## Remaining gates

FIT backend supports single-session activity files and standard recorded metrics. Multisport files fail closed pending explicit session splitting. Unknown/developer fields remain recoverable in original bytes; no unsupported metric claim. Import picker/review UI and promotion into canonical activity records remain pending approved UI integration and persistence review; cache is not registered as a live connected source. No Adaptive Analysis Composer.

No main merge, release, signing/version change, physical installation, production database migration, real-account mutation, or Canva edits.
