# Phase 2 foundation readiness — 2026-11-13

This register is preparation only. It does not amend the frozen Session G architecture, admit data, authorize a target or collector, or advance a gate. Source: `engine2x-current-handoff.md` at `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e`.

| Dependency | Engineering evidence | Missing decision or evidence | Next permitted action |
| --- | --- | --- | --- |
| P2-F synthetic linkage and custody | Durable journal and coordinated authority/custody synthetic models; v7 design accepted for preflight authorization review | Operational key custody, rollback/recovery, gateway enforcement and actual target qualification | Independent preflight authorization review of the frozen read-only packet and proposed boundary |
| P2-F source admission | Purpose-specific rights contract and quarantine rules | OpenFitness file-level rights, privacy basis, processor/custodian, retention and withdrawal decision | Designated steward completes the decision packet below; data stays at source |
| P2-B measurement | DEV-0 synthetic pipeline closed | Admitted repeated observations, independent reference, eligible denominator and uncertainty | Await source or prospective protocol admission; do not calculate real repeatability |
| P2-A athlete state | Synthetic models and protocol | Decision-time authorized inputs and independent follow-up | Keep in shadow research |
| P2-C progression/re-entry | Synthetic policy and protocol | Verified reasons, exposure coverage and follow-up | Keep in shadow research |
| P2-D adaptation | Synthetic decision protocol | Linked decisions, approved revisions and independent future outcomes | Keep in shadow research |
| P2-E concurrent training | Synthetic coordination | Discipline-native exposure and independent outcomes | Keep in shadow research |

## OpenFitness source-admission decision packet

The steward must identify the exact release and files, origin and acquisition route, license and exceptions for each file, privacy/legal basis, permitted research purposes, prohibited uses, controller and processor roles, storage location and access controls, retention/deletion and withdrawal procedure, independent-reference rights, and publication limits. Record evidence links, date, decision maker and a signed decision of **admit for a specified purpose**, **quarantine pending evidence**, or **reject**. No generic repository license or public availability substitutes for these fields. Current decision: **NOT ADMITTED**.

## Independent preflight authorization review packet

Review the frozen read-only packet and v7 contract cited by the current handoff. The reviewer must name the proposed Windows x64 workstation only as a candidate, identify owner/operator and physical custody, OS/build and patch provenance, Secure Boot/TPM claims to be measured later, administrative and ordinary-user separation, executable and binary hash verification, network and capture boundary, output destination, log retention, stop and incident procedure, and whether the packet can run without private data or operational keys. Require an independent written accept/correct/block decision with evidence and retest criteria. This packet does not authorize execution or TPM commands.

## Proposed target qualification specification

Before any target access, approve a specific workstation identifier and non-invasive evidence collection plan. Later qualification must establish hardware/firmware and TPM identity, Windows x64 version, secure boot and update state, account privileges, disk and backup controls, network isolation, clock trust, collector binary identity, capture/output inventory, rollback and restore behavior, and independent observation of failures. Record each observation as measured, unavailable or failed; do not infer suitability from workstation class or synthetic tests. No workstation is currently approved or qualified.

## Gate record and handoff

### Batch 2 synthetic adversarial coverage verdict

`VERIFIED` below means an executable assertion exists and passed in the Python 3.12 suite. It does not establish a real process, machine, key, or device property.

| Invariant | Verdict and representative tests | Limit |
| --- | --- | --- |
| Deterministic replay and reconstruction | VERIFIED: `test_p2f_durable.test_old_projector_replay_and_schema_migration`, `test_p2f_v7_vectors.test_catalogue_replays_from_exact_named_history` | No operational restore proof |
| Duplicate and conflicting identity | VERIFIED: `test_p2f_durable.test_duplicate_aliases_and_explicit_adjudication`, `test_p2f_foundation.test_identical_event_retry_is_idempotent_and_keeps_projection_current`, v7 signed rejection catalogue | Synthetic fixture scope |
| Stale revision and ordering | VERIFIED: `test_p2f_durable.test_stale_concurrent_writers_and_idempotent_retry`, `test_correction_cannot_precede_its_source`, `test_revocation_cannot_precede_its_link` | No external clock qualification |
| Interrupted write and incomplete transaction | VERIFIED: `test_p2f_durable.test_process_death_before_commit_rolls_back`, `test_p2f_private_authority.test_crash_at_each_transition_boundary` | Simulated fault points, not power-loss testing |
| Withdrawal during read, write and publication | VERIFIED: `test_p2f_private_authority.test_withdrawal_during_read_and_during_output`, `test_publication_and_withdrawal_have_shared_fence`, `test_p2f_epoch_publication.test_withdrawal_blocks_other_open_broker_with_same_scope` | Delivered plaintext cannot be recalled |
| Rollback and recovery | VERIFIED: `test_p2f_private_authority.test_data_rollback_and_removed_unknown_descendants`, `test_restore_restart_reinstall_and_copied_store`, `test_p2f_linkage_recovery.test_restore_and_suppression_before_backup_read` | Real TPM, disk and backup behavior untested |
| Nested calls and concurrency | VERIFIED: `test_p2f_coordinator.test_nested_mutation_is_reentrant`, `test_other_thread_waits_for_shared_lock`, `test_p2f_private_authority.test_caught_nested_read_fences_outer_read`, `test_p2f_durable.test_simultaneous_writers_are_serialized` | No multi-host deployment |
| Cross-account isolation | VERIFIED: `test_p2f_durable.test_cross_account_keys`, `test_p2f_linkage_recovery.test_external_ids_reused_between_accounts_do_not_resolve_foreign_plan_or_revision` | No real account authority |
| Epochs and historical key roles | VERIFIED: `test_p2f_coordinator.test_each_domain_mutation_uses_one_shared_epoch`, `test_p2f_v7_vectors.test_role_history_survives_replay_retirement_and_replacement`, `test_bridge_retains_same_roles_but_cannot_restore_retired_keys` | Signed fixtures, not provisioned keys |
| Rejection leaves authority unchanged | VERIFIED: `test_p2f_private_authority.test_ambiguous_advance_is_not_blindly_retried`, `test_p2f_v7_vectors.test_full_objects_and_rejections` and complete catalogue no-mutation checks | Fixture oracle cannot prove deployed service behavior |

No demonstrable missing synthetic invariant was found in this bounded audit, so no redundant test was added. Operational power-loss, target trust, TPM nonrollback, key custody, human withdrawal and real measurement are **NOT SYNTHETICALLY ESTABLISHABLE**. The independent reviewer should challenge this matrix against the exact committed code and vectors.

`P2_F_GATE_2_NOT_READY`; `P2_B_PROTOCOL_READY_DATA_NOT_READY`. Private pilot, participant collection and OpenFitness ingestion remain closed. Next permitted action: independent review of this coverage verdict and preflight authorization packet, plus the steward's separate source decision. No real-data feasibility claim follows.
