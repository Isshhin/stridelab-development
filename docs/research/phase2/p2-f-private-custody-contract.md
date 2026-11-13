# P2-F | Private custody contract for one S1 pilot

**Specification, not evidence of deployment.** Do not enter real names, locations, values, keys or receipts here. Before offering the [participant sheet](p2-f-participant-sheet.md), complete a restricted copy of the [admission form](p2-f4-human-feasibility-admission.md). No human material is needed to rehearse these controls.

## Private environment and access

Record privately: encrypted staging location/jurisdiction ___; controller/contact ___; key custodian ___; authorized named operators, role assignments and expiry ___; processor list or “none” ___; offline tool versions ___; data/backup deadlines ___; current revocation authority and recovery procedure ___; synthetic drill date/evidence ___.

The custodian creates separate access-controlled areas for (1) identity/receipt, (2) immutable original, (3) working copy and per-run temp, (4) normalized observations/linkage, (5) segments/derived audit output and (6) restricted reference/comparison records. Keep keys outside data backups. Disable networking, sync, crash uploads, telemetry, auto-indexing and thumbnail generation for staging and tested tools. Use no Git working tree, GitHub Actions, ChatGPT upload, online viewer or cloud synchronization. A future cloud processor would require a new admission decision; this contract admits none. Check operating-system temp, swap, recycle bin, recent-file lists, backup capture and viewer autosave using synthetic input. Unknown egress or uncontained residue blocks intake.

Custodian imports one read-only original under a private scoped label, records integrity and access privately, and allows working copies only inside encrypted per-run space. Never edit the original. Apply restrictive access on creation, not after computation. Tag each object with purpose, lineage, partition and expiry. Annotate each read, copy, correction and disposal in the protected inventory. Never copy private paths or hashes into the project.

| Role | Minimum access |
|---|---|
| Participant | Their exact request, choices, contact, receipt and deletion status. Controls participation; no repository or administrative access required. |
| Controller/steward | Purpose, receipt, provenance, roster, restrictions, approval/expiry and exclusion decisions. No routine raw trace access. |
| Custodian | Restricted mappings, encrypted original, keys/ACLs, object inventory, backups and deletion/restore operation. No authority to widen grants. |
| Reference reviewer | Read-only original via approved independent offline method plus selected diary/plan evidence; private reference worksheet only, no harness output before freeze. |
| Parser/operator | Only authorized scoped input through staging and isolated working/normalized/derived areas; no identity mapping or reference answers. |
| Comparison reviewer | Frozen reference and corresponding scoped harness result, discrepancy log and approved evidence for adjudication; no identity map or blanket access to other recordings. |

Name people separately for each role. Record every overlap, which outputs they already saw and which blinding/independence claims remain possible. For an owner-only case, freeze reference before computing outputs, document tool independence and require separate clearance of critical discrepancies. An already exposed or unverifiable reference cannot be relabeled independent. Withhold the affected claim until independent review is available.

## Development exclusion before intake

Reserve a private study person key and a source family key. Register the participant, recording, diary, accepted revision, aliases and versions **development-only, permanently ineligible for confirmatory use**. Maintain an exclusion authority outside restorable processing snapshots with a monotonic version. Projection and split builders require a current grant, proven ownership, resolved aliases, complete parent lineage and positive eligibility; a missing exclusion entry alone is never proof of eligibility. Propagate the most restrictive label to all children, joins, corrected bytes, re-exports, alternate formats, trace segments, aggregates and copied views. Future aliases resolve to the same restricted person/family, never by filename, time proximity or guessed identity. Unknown/colliding aliases, inaccessible registry or incomplete lineage quarantine before use. Do not consult sealed outcomes to check exclusion.

Keep only agreed minimal protected suppression tokens, scope, authority version and expiry; tokens are still restricted. Disclose their purpose and retention privately. If mapping is no longer permitted, remove it under the retention decision and default-deny unresolved future identities. Never admit a formerly excluded S1 record after token deletion, key rotation, rename, correction or restoration. If permanent partition separation cannot be enforced without an unauthorized identifier, stop intake and split construction.

## Deletion and evidence

Withdrawal, expiry or closure: stop all processing first, revoke sessions/keys as applicable, activate suppression, enumerate descendants and storage from the inventory, then verify each object's disposition separately. Record verifier, method, time, evidence, applicable deadline and next check privately. Never claim physical secure erasure on SSDs, snapshots or infrastructure without proof.

| Object | Verification required |
|---|---|
| Original pilot export and diary copy; each working copy | Remove pilot-controlled copies, remount/reopen the approved store under ordinary roles, verify absence against inventory. Participant's source device is outside this deletion. |
| Normalized and linkage rows; trace segments; derived audit rows, comparisons and aggregates | Query independently by person, recording family and descendant lineage; reject old materializations; purge or rebuild dependent views. Keep no participant-level result as a supposedly anonymous summary. |
| Local caches, parser temp and viewer temp | Close tools; inspect configured and default temp/cache/history, recovery and recycle areas; restart tools and verify no recovery preview or recent file returns. |
| Snapshots, backup copies and staged restore copies | List all snapshots and expiry dates; remove where supported; otherwise isolate and bar normal reads/restores until expiry, then verify rotation. Dispose of restore scratch and verify separately. |
| Suppression records | Verify current blocking, minimal content, scope, key custody and agreed retention; test deny-by-default when a token is absent. At lawful expiry remove eligible tokens and verify absence; never delete the block first and permit late replay. |

Assign the observed state, not the intended one:

- **VERIFIED_DELETED**: removed from the named pilot-controlled store and an independent inventory/read check confirms logical absence. Report limits; this is not proof of media erasure or of backup deletion.
- **ACCESS_REVOKED_PENDING_BACKUP_EXPIRY**: known backup remains; normal reads and restores are restricted, expiry is dated and a follow-up verifier is assigned.
- **SUPPRESSED_FROM_PROCESSING**: object or denial token persists under disclosed authority; processing, joins, exports and replay demonstrably refuse use. Distinguish this explicitly from deletion.
- **UNVERIFIED**: unknown copy, inaccessible verification evidence, failed check or uncertain platform behavior. Block further intake; investigate and report the limit to the participant.

## Exact adverse restore drill — synthetic accounts only, before receipt

1. On the **actual proposed staging configuration**, create invented accounts A and B with reusable external IDs scoped to their accounts. Give A an active development grant, diary, alternate format alias, correction and derived segment; give B an independent valid record. Save the pre-revocation snapshot/backup including active grants and materialized derivatives. Record B's expected result privately. If backups are prohibited, confirm absence and exercise an intentionally stale working-state copy instead. Do not create a human backup for this test.
2. Withdraw A in the current external authority, increment authority epoch, invalidate all A descendants, and verify A's original alias and corrected alias are denied. Keep B active. Ensure the stale snapshot cannot overwrite or select the current authority.
3. Restore the old state into isolated, access-closed scratch; leave processing, viewer, indexer, export and network disabled. Before opening any operator view, require the independent current authority; missing/stale/unreachable authority must keep all views closed. Reapply A's suppression, invalidate old derivatives and purge the restore copy as required. Privileged recovery custody is not ordinary access.
4. Open ordinary reads only after that barrier. Attempt raw read, normalized lookup, old snapshot replay, segment/derived retrieval, export and split selection for A; verify no data or identifying output. Attempt late re-import of original bytes, renamed alias and corrected/re-exported version; deny known A, quarantine unresolved new aliases, and produce no active row or derivative. Verify the exclusion guard still denies confirmatory allocation using a **synthetic** candidate pool.
5. Verify B's expected count/value and replay are unchanged even with the reused external ID; check that A's stale derived result cannot reappear in rebuilt summaries. Close, remove and verify restore scratch, including caches/temp. Log tool/authority versions, each expected/observed result, backup expiry, verifier and disposition. Any silent access, inconsistent B result, unexplained copy or unverified barrier fails the drill. In-memory unit tests alone do not certify private storage. Repeat on changes to storage, access, backup or tool configuration.
