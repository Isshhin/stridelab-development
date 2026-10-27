# StrideLab — Repository-wide improvement opportunities (2026-10-28)

**Review scope:** Enumerated the complete recursive Git tree at verified native checkpoint `25be8f3828374d1e539c7e825afa20fbcb652b56` (**275 entries, not truncated**), including native app, Java legacy storage, native bundled assets, 15 GitHub Actions workflows, Python tools, docs and archives. Inspected representative core source files and the source-grounded T0 and T5A audits (draft PRs #5–#6), the integrated checkpoint report, live draft PR metadata and the full stacked Engine 2.0 code differences. This is a repository-wide architecture/source **review, not a line-by-line audit of every blob or a device test**. The separately frozen web repository is outside this source tree.

**Work policy:** Direct GitHub changes and synthetic source/tests through the GitHub connector without Work or Copilot. Open separate non-CI draft branches; preserve exact verified baseline and do not merge, upload an installable APK, or trigger costly CI automatically. Kotlin/Compose source can be authored now; native runtime acceptance requires Android build/emulator resources later.

## Prioritized practical improvements

| Rank | Area / precise candidate | Can implement source now? | Acceptance/dependency | Existing overlap |
|---|---|---|---|---|
| P0-A | Complete and reconcile truthful strength/calisthenics completion semantics, partial sets, and truthful running-card actions | **Yes** | JVM/UI tests, offline re-open, explicit performed sets; ensure no recorded data lost | PR #7 drafted |
| P0-B | Transactionally unique account/provider activity links across plans; explicit unlink/relink; non-destructive existing-record migration | **Yes, on isolated branch** | Android SQLite v1→v2 migration and concurrency tests needed before adoption | PRs #9 and #10 |
| P0-C | Stabilize signed private-update compatibility, backup/export, rollback and migration verification | **Scripts/tests/docs now** | Existing phone APK signer fingerprint and device tests unavailable to this chat | existing checker and device procedure |
| P1-A | Finish Engine 2.0 proposal receipts, source-truthful context and read-only Engine 1.0 bridge; preserve approval/decision integrity | **Yes** | Native JVM/Android tests and agreement on one authoritative calendar required | PRs #13–#20 |
| P1-B | Centralize workout status/action language and high-contrast accessible borders, readable calendar week dates, charts and keyboard selection | **Yes, screen-scoped** | Screenshot, TalkBack, font scaling and emulator tests | PR #8; T5A PR #6 |
| P1-C | Make dashboard data provenance and missingness explicit: recorded vs prescribed, provider freshness, incomplete 7-day totals and unknown wellness | **Yes** | JVM model tests and visual review; don't infer no training when offline | WeeklyDashboard/WellnessOverview exist |
| P1-D | Screen-scoped native Activity Detail reconstruction: Overview / Splits / Charts / Intelligence tabs, authentic GPS route presentation, sample/route linkage, honest empty state | **Yes, incremental Compose** | Route source presence and emulator visual/accessibility testing | RecordedActivityDetail currently offline Canvas trace only |
| P1-E | Robust offline workout drafts and saved-set revision conflict handling across account changes, interruption and process death | **Yes** | Emulator process-death/SQLite instrumentation, no changes to legacy DB without migrations | existing WorkoutPlayer/TrainingRepository |
| P1-F | Source-normalization adapters and bounded FIT/GPX/TCX file import with quarantine on impossible units and explicit provenance | **Yes** | Parser fixtures/Android local file-open tests, provider interoperability; genuine files not fabricated | native FIT/GPX parsing absent |
| P2-A | Authorized Garmin personal pull integration, paginated import and token lifecycle | **Interface, parser and mocks now** | Legal/terms/access approval and real account auth required for live data | native direct client absent |
| P2-B | Cautious planned-vs-recorded session candidate matching, ambiguity review and original source identity preservation | **Yes** | P0-B transactional uniqueness and real timestamps before auto-association | manual linking + Engine 2.0 candidate matcher |
| P2-C | Finish curated workout catalog: executable-vs-guide enforcement, real licensed exercise images, truthful variation-specific visuals, equipment/skill prerequisite validation | **Yes** | Rights/provenance review and UI/device tests | 44 JSON entries, some bundled PNGs, PR #18 guide fix |
| P2-D | Useful gym/calisthenics progression feedback from actual sets/reps/holds/load and conservative manual approval, with correct units | **Yes** | Tested fitness rules, versioned progression policy, actual log data | Engine 1.0 and Engine 2.0 proposal scaffolding |
| P2-E | Meaningful fitness narrative and goal progress from comparable real training windows, without unsupported readiness or race predictions | **Yes** | Historical comparisons and unknown-data sensitivity testing | ActivityIntelligence and Engine2FitnessInsights |
| P2-F | CI-minute and workflow hygiene: dedicated lightweight unit/Python verification, opt-in expensive emulator pipeline and APK artifacts; preserve historical workflows | **Yes, isolated workflow PR** | Workflow syntax/security review and first permitted CI validation; owner must authorize CI run | 15 workflows, most old ones dispatch-only, current native workflow packages APK by default |
| P2-G | Repository documentation index: clarify current native checkpoint, all outstanding stacked PR chains, archived reports vs current source, and exact-head acceptance | **Yes** | Source commit IDs and PR dependencies must be kept accurate | `README.md` has negligible explanatory content at verified checkpoint |
| P3-A | Cross-provider dedup of the same physical activity using conservative confidence and explicit user review | **Design/fixtures now** | Real provider identity records, account scope and physical-event verification needed | no complete provider-equivalence contract |
| P3-B | Offline-first achievement/goal charts with transparent estimates, optional 5K/10K/HM/marathon fitness assessments | **Yes, UI/model candidates** | Defensible measurement definitions and real historical validation required | planned fitness intelligence |
| P3-C | Curated Android acceptance dashboard/report generator with exact commits, test classes, run IDs, migration and signing evidence | **Yes** | Host CI results needed to mark criteria passed | existing test/report scripts |
| P3-D | Backup/versioned data export/import dry-run and account isolation regression harness | **Yes, test-only first** | Real restore compatibility and corrupt-backup rejection tests | `PlanBackup`, `BackupCodec`, `TrainingStore` |

## Immediate recommended GitHub-only development order

1. Reconcile existing draft P0 PRs #7, #8, #9 and SQLite link-unicity design #10 against verified checkpoint (DO NOT merge untested).
2. Run a **code-only static contract and catalog validation** over the Engine 2.0 stacked PRs #13–#20; Kotlin JVM compilation/Android instrumentation remain unverified until a suitable runner is available.
3. Create an isolated lightweight (unit/Python) verification workflow restricted to manual dispatch or a separate protected integration branch, with **APK packaging opt-in only** and no unrequested hosted CI launch. Preserve existing workflows.
4. Implement visual/native Activity Detail improvements in small Compose screen-scoped branches after T5A and P0 terminology corrections, without inventing GPS/HR data.
5. Add genuine offline FIT/GPX/TCX parser contracts and synthetic fixtures independent of credential-based Garmin access.
6. Once service authorization, migration, test resources and user consent exist, complete live Garmin import, transactional link integrity and offline/physical device acceptance.

## Findings that specifically constrain how much can be validated immediately

- Current native source includes the legacy Java `TrainingStore` SQLite v1 and retained records/outbox. Changing SQLite schema in place without migration would risk installed private-alpha data. The separate experimental Engine 2.0 proposal database in draft PR #20 **does not migrate or synchronize existing plans**, so it is not ready for app-wide activation.
- The source-audited T0 report flagged false completion without performed sets and storage-layer link uniqueness. Draft source fixes exist; do not call them closed until exact-head tests pass.
- An offline GPS polyline and source lap list are NOT equivalent to the approved immersive map with selectable split/chart-route highlights. This is a safe next UI implementation with real data only.
- Real Garmin authentication and provider endpoints cannot be verified using synthetic adapter tests alone.
- Old public/private APK signing mismatches must never be bypassed by uninstalling the existing app.
- Only an exact-head compiling and passing test run supports an integration or release-ready status.

**No source commit or PR is evidence of compilation.** No GitHub Actions invocation, release, merger or APK install was performed in this review.
