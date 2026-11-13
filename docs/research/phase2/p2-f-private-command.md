# One private TCX comparison

**Entry condition.** The steward has completed the private participant decision, admission, exclusion and custody checks. During preparation, run only the generated rehearsal below. This command cannot grant consent or verify the authenticity of a consent receipt. The steward supplies current decisions from the separately controlled private register. One registered original, one source, one opportunity, one accepted revision and one linked running activity are supported.

## Prepare and rehearse

Use local CPython 3.12, the standard library and an offline, read-only, reviewed checkout. In the private operator record, pin the **full 40-character reviewed operator-kit commit containing this command**, interpreter point release and the literal on-disk SHA-256 of the four modules below from that checkout. The earlier 29b8cb5 milestone predates the command. Verify a clean checkout against the approved Git object and its line-ending policy before any private input. The command checks the four deployment digests. The commit label alone cannot authenticate the checkout.

From the checkout root, in a private, transcript-free PowerShell console, run:

```powershell
py -3.12 -E -s -B research/validation/test_p2_private_audit.py --synthetic-demo
```

Expect **PASS**, exit 0. The demo constructs every byte in a new temporary directory and invokes the command in fresh processes for comparison, post-withdrawal denial and a withdrawal check. It removes its generated tree. Use a verified, writable local temporary directory outside any checkout. Repeat this and the separate restore/deletion drill on the intended machine. Only those on-machine checks and custody signoff establish readiness to invite an actual transfer.

The operator must keep all private paths and records outside Git, sync, backup rollback and public logs. The steward selects two disjoint local directories privately. Verify the volume is local, encrypted and outside network/mapped drives. Verify no unauthorized account can change either tree, ancestor directories or deployment modules during processing. Verify the original's read-only attribute and ACL, and output directory inheritance. Code rejects relative, UNC, repository, symlink, junction/reparse and multiply linked input files. It cannot establish encryption, role independence, physical deletion, remote-drive mapping or immunity to a privileged local administrator. A missing/unknown prerequisite stops execution.

## Configure the two private inputs

**Authority directory.** Only a designated steward may set its contents or change the launcher's authority-directory binding. Keep it outside the staging restore domain. An independently authenticated live register must cover withdrawals, subject aliases, permanent exclusion, corrections, current version and deadlines. Never use a grant export from a working backup as the current register. Before each invocation the steward checks the live register and separately signed decision, then publishes an expiring, scope-bound projection in `current.json`. This file is sensitive. Its protection and freshness are operational prerequisites. The parser may read the projection and lease file but may not replace either one or modify the projection. A registry switch, fresh alias, correction, disputed decision, stale register or withdrawal request pauses processing. If an operation is running, stop it before acknowledging revocation and close downstream access immediately.

Create `lease.lock` once in the authority directory, containing the single ASCII byte L. Parser and steward use the **same permanent file** for mutual exclusion. The command holds its OS lock until comparison publication. Every grant update/refresh/withdrawal writer must take the same exclusive lock before replacing the projection. Never replace the lock object. Custody signoff must verify contention and writer behavior across real operator accounts. The in-memory replay authority is constructed only under this lease from the trusted projection and pinned journal. It is never an independently authoritative durable grant.

| Authority JSON field | Required private value |
|---|---|
| decision, suppressed, confirmatory_excluded, custody_verified, current_registry_verified, reference_sealed | Current steward decision and separately verified booleans. Comparison requires SOURCE_ADMITTED, no suppression, permanent exclusion and all required checks. |
| receipt_ref, steward_ref, independence_review_ref | Opaque private evidence locators for the actual decision, steward and reviewer-independence determination. Signed records stay with their authorized custodian. |
| grant_start, grant_end, verified_at, execution_expires | Explicit-offset instants. Current wall clock must be inside both the consent interval and the steward-approved execution interval. Verify machine clock privately. |
| scope, aliases, partition | Exact five-element account, study pseudonym, grant, development-pilot, deciding-view tuple, complete registered alias array and development-pilot partition. |
| operations | Private decision per exact key original_inspection, normalization, within_person_derivatives, diary_linkage, development_testing. Each required choice must actually be authorized. The command accepts no optional diary fields or free-text journal payload. |
| workspace, format | Absolute private staging root and authorized source format. Fixed TCX filenames below. |
| code_commit, command, parser, schema, modules | Reviewed commit, private-audit/1, p2-trace/0.2, F2.1-pilot.2, and exact deployment hashes keyed by p2_private_audit.py, p2_trace.py, p2_pilot_probe.py and p2f_linkage.py. |
| original_sha256, journal_sha256, reference_sha256 | Three lower-case SHA-256 values from the authorized receipt, independently reviewed event journal and sealed reference. A null or missing pin stops processing. |

**Staging directory.** Use the registered root with the fixed relative files below. UTF-8 JSON accepts an optional BOM. Source-specific fields and completed worksheets remain private. No new grant, answer, device or location is included here.

| Path | Source, purpose and exact shape |
|---|---|
| immutable-original/source.tcx | Custodian's read-only copy of the one authorized direct original. Digest must match the registered version. Limit 16 MB. A working copy used by a reference viewer remains under separate custody. |
| linkage/journal.json | Reviewed F2 journal with source_key (four strings, connection/namespace/external/format), activity, review_id, review_edge (ordered actual/opportunity/revision), source_id, edge_id, cutoff and events. Four standard F2 events: initial easy plan, receipt, source and link, with their scoped envelope. Source imported_at must precede or equal the reference freeze, which must precede cutoff. One row per kind. Quarantine a changed plan or more complex journal for separate review. Limit 4 MB. |
| independent-reference/sealed.json | Reviewer freezes frozen_at, reference and expected **before first harness exposure**. reference uses all fields of p2_trace.Reference. Serialize marker_crossings as explicit-offset timestamp strings, timer_events as [timestamp, start-or-stop] pairs, epochs_seen as an array. Seal all three top-level fields together. Limit 16 MB. |
| comparison/ | Private output directory. comparison.json must be absent. Never erase a disagreement to re-run. Use a new separately approved version/workspace and retain the prior result only while authorized. |

The independent `expected` object contains three keys. The keys, source order, values and missingness must match. For `identity`, enter account, study, activity, opportunity, revision, plan, source_id and start_utc. For `points`, enter one array per original point in original order: UTC instant, explicit cumulative distance in metres, native HR including zero or null, native cadence including zero or null. Normalize UTC instants to ISO 8601 with +00:00 and Python datetime's six-place fractional representation when nonzero. The independent reviewer uses their own viewer, decoder or direct XML worksheet to establish these values.

For `qc`, enter source_session_ordinal, source_lap_ordinal, first_to_last_sample_elapsed_s, lap_reported_time_s, lap_reported_m, lap_reported_mean_hr_bpm, lap_reported_max_hr_bpm, lap_native_cadence, start_offset_s, duration_s, distance_m, quarter_valid_s, quarter_mean_hr_bpm, quarter_distance_m and max_observed_interval_s. The quarter arrays have four values in chronological order. Use null for genuinely absent native fields. Retain explicit unit, basis, source resolution, uncertainty, reviewer, cutoff and effort in the private audit worksheet. Exact equality is a conservative first pass. Preserve even small rounding disagreements for adjudication under a predeclared source-resolution rule. The generated `stage` function in the companion test shows the complete input shape **only for invented values**. Never derive private expected rows or a real grant from it.

## Run and close

The custodian supplies P2F_AUTHORITY_DIR through the trusted local process environment, using the private launcher configured during setup. Do not put its value, any participant paths or data in the command line, PowerShell history, transcript or CI. Verify the working directory is the reviewed code checkout. Use:

```powershell
py -3.12 -E -s -B research/validation/p2_private_audit.py compare
```

The command checks the current grant, registered inputs, historical cutoff and independent freeze before reading the original. It checks source ownership/linkage, derives private measurements, then reconstructs a fresh ledger with reversed events and repeats the import. It compares every field in the sealed outer reference, including every trace row. It rechecks grant expiry and module pins before publishing a complete fsynced file without overwriting an existing result. The source read-only flag supplements the access-control check the operator performed. If the filesystem cannot safely publish the result, stop. On a crash, quarantine any .pending- file until the custodian inventories and removes it under the deletion procedure. Comparison output contains sensitive observed and expected values. Never copy it to the project or print its contents.

| Exit | Terminal line | Operator action |
|---|---|---|
| 0 | PASS | Agreement on this bounded comparison, or verified access denial in withdrawal-check mode. Record elapsed audit minutes privately. |
| 2 | QUARANTINE | Withhold the item. A complete comparison.json preserves both readings when measurement values differ. A precondition or decoding failure may produce no new file. Use the private checklist to record the failure and denominator. |
| 3 | UNSUPPORTED | Authorized format outside this TCX command. Record and abstain. |
| 4 | CRITICAL_MISMATCH | Independently frozen identity disagrees or replay differs. Stop the affected path, isolate dependent outputs and preserve the original comparison under current rights. |

To verify **actual** withdrawal, the steward first suppresses the private grant and aliases in the live register, preserves the exclusion and stops access to derivatives. Publish the fresh minimum projection under the same lease: decision WITHDRAWN, suppressed, confirmatory_excluded, current_registry_verified, scope, aliases, steward_ref, verified_at and execution_expires. Run:

```powershell
py -3.12 -E -s -B research/validation/p2_private_audit.py withdrawal-check
```

Expect PASS only for a current scoped suppression decision which the normal entry gate denies before opening staging. The command never revokes a participant itself. Verify previously produced files, caches, restores and late reimports separately using the custody drill. For controls before an actual withdrawal, use the generated rehearsal. Record each denial or disposition privately, preserve denominator counts and disclose only reviewed non-identifying results.
