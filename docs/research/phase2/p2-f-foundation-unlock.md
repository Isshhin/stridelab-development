# P2-F Foundation Unlock | audit, canonical contract and implementation boundary

Date: 2026-11-06 (Asia/Manila)  
Live starting head: `ac1b0e27c2b16caae32e7fe4d226b7c7860c0135`  
Frozen Session G baseline: `26a8331e642c888dd73acb94755dded063baab8d`  
Scope: research-only P2-F engineering; no human records or held-out outcomes accessed.

## Verdict at sprint entry

- P2-F synthetic engineering feasibility: `PASS_WITH_BOUNDED_IMPLEMENTATION`.
- P2-F Gate 2: `P2_F_GATE_2_NOT_READY`.
- P2-B: `P2_B_PROTOCOL_READY_DATA_NOT_READY`.

The branch has strong private-pilot and parser probes, but the existing `F2.1-pilot.2` adapter intentionally supports one opportunity and one active link. It is not the canonical Training Exposure Ledger. This sprint therefore freezes an additive F2.2 contract and implements only a synthetic reference projection and trace validator. It does not broaden admission, device support or coaching authority.

## Audit against the requested foundation

| Requirement | Entry status | Evidence / decision |
| --- | --- | --- |
| Plan, revision, decision and delivered prescription | Implemented in bounded form | `p2f_linkage.py` checks ancestry, stale writes, delivery receipts and decision-time inputs. F2.2 generalizes the identities without changing those rules. |
| Performed actual and outcome | Partial | Occurrence and independent follow-up exist; canonical completion, dose and outcome roles were not one stable exposure projection. F2.2 separates opportunity, activity, link and outcome. |
| Provenance and account ownership | Implemented in bounded form | Private authority, scoped source maps and cross-account rejection exist. F2.2 requires the account partition on every key and lineage edge. |
| As-of availability and replay | Implemented in bounded form | `available`, view sequence, cutoff and current authority are present. F2.2 names two different products: decision-time replay and current-validity projection. |
| Unknown/missing/zero semantics | Specified; partial code | F2.1 enumerates reason-coded nulls, while the pilot code retains a subset. F2.2 makes typed observations mandatory at the projection boundary. |
| Duplicate and reconciliation | Partial | Explicit cross-format maps and byte-identical retry exist; heuristic matching is refused. Canonical alias groups and conflict state become explicit in F2.2. |
| Correction, withdrawal and invalidation | Implemented in bounded form | Corrections invalidate links; withdrawal suppresses replay and restored copies. F2.2 requires derived-view lineage and current invalidation without deleting journal history. |
| Trace-level measurement | Partial | TCX 0.2 can audit a narrow fixed window; FIT is extraction-only and B0-ineligible. F2.2 freezes a format-neutral minimum trace contract, but no real device is supported. |
| Real-device and human feasibility | Blocked by external evidence | No admitted human source, supported device/firmware, manually reconciled denominator, coverage or burden margin exists. |

## Canonical Training Exposure Ledger F2.2

The ledger is an append-only account-partitioned event journal plus deterministic projections. It does not infer coaching intent, identity or duplicate relationships from similarity.

### Required entities and links

1. `PlanVersion` is immutable and parent-linked.
2. `Opportunity` is the planned denominator and survives move, cancel, miss, split or replacement.
3. `PrescriptionRevision` identifies the dose/intent the athlete could have received.
4. `DeliveryReceipt` identifies the revision actually available before performance.
5. `Decision` freezes its actor/view, cutoff, input IDs and policy/config versions.
6. `Choice` records accepted, rejected, dismissed, expired or superseded separately from action application.
7. `Activity` is one canonical real-world bout, independent of plan ownership.
8. `SourceObservation` preserves provider/export/parser provenance and may be one of several aliases for an activity.
9. `ActivityLink` is an adjudicated many-to-many edge among activity, opportunity, delivered revision and intent role. Unresolved candidates do not enter exposure totals.
10. `TraceSegment` is a bounded measurement child of authorized source observations and an activity.
11. `OutcomeObservation` has its own protocol, window, availability and adjudication; pending or missing is never a negative endpoint.
12. `Invalidation` records correction, link revocation, source withdrawal, grant change or derived-view retirement.

Every identity is scoped by `account_id`. Every event also carries `event_id`, `schema_version`, `recorded_at`, `effective_at`, `available_at`, `origin_id`, `view_id`, `purpose_grant_id`, `partition_id`, `lineage_class` and optional `supersedes_event_id`. Offline writers allocate opaque IDs locally and synchronize them; a date, route, filename or distance is never an identity.

### Two projections, never one ambiguous replay

- `decision_time_view(account, cutoff, actor_view, watermark)` includes only authorized events verifiably available to that actor by the cutoff. Later uploads, reviews, corrections and outcomes are absent.
- `current_validity_view(account, as_of_now, historical_cutoff)` first enforces current grants, withdrawals and suppressions, then reconstructs the requested historical event set. It may say a formerly reproducible view is now ineligible without exposing withdrawn values.

Both projections return an event-set digest, schema/projector version, input lineage, exclusions and reason counts. A correction appends history. It never mutates the event it supersedes. A withdrawal may make a projection unavailable even when its historical cutoff predates withdrawal.

### Completion, intent and dose

Completion is one of `completed`, `partial`, `confirmed_skipped`, `substituted`, `unknown`; an unmatched activity is separately `unplanned_or_unknown_intent`. Plan ownership is `planned_by_stridelab`, `planned_externally`, `observed_external_training`, or `unplanned_or_unknown_intent`. Unknown intent stays unknown and cannot silently become StrideLab-prescribed.

Numeric observations are `{value, unit, status, method, source_ids}` where `status` is one of `measured`, `not_collected`, `missing_upload`, `unknown`, `invalid`, `conflict`, `not_applicable`, `withheld`, `withdrawn`. Only `measured` may carry a finite numeric value, including a legitimate zero. A confirmed skip can provide measured zero performed work for that opportunity, while HR remains `not_applicable`. Manual-only occurrence never fabricates numeric dose.

### Duplicate, alias and conflicting-source rules

- Byte-identical retry for the same scoped source identity is idempotent.
- Garmin/API, FIT, TCX, GPX or manual aliases collapse only through explicit, account-scoped adjudication evidence.
- Similar time, route, title, distance or HR is candidate evidence only and cannot form a canonical alias group.
- An alias belongs to at most one activity in an account; reuse or cross-account reference quarantines the edge.
- Per-field authority is explicit. Equal-authority disagreement becomes `conflict`; neither source wins by format priority.
- One activity counts once in athlete exposure even when it carries multiple source aliases or intent roles. Shared intent credit is not summed unless disjoint segment allocation is proven.

### Minimum trace-level measurement contract

A trace segment must preserve:

- account, activity, source observation(s), source digest and parser/profile version;
- segment start/end UTC, original offset, clock precision and sampling timestamps;
- timer events/state, pause mask, unknown-timer intervals, gaps and segment bounds;
- cumulative distance samples, reset flags, distance method and calibration/version;
- native or derived speed method and exact time basis; pace remains undefined at zero speed;
- HR samples with missing/zero/invalid distinction, sensor method and sensor/device epoch;
- device/firmware/export family and any route/device bridge identity;
- eligibility flags, all exclusion reasons and derived-row lineage;
- only separately authorized context fields needed by the registered protocol.

Timestamp order must be strict within a stream. Malformed or offset-free clocks, distance reversal, ambiguous timer state, source correction, sensor epoch change, unsupported gap handling or missing provenance quarantine the affected segment. Summary averages cannot substitute for trace samples. Environment, RPE, symptoms, route and schedule context are not collected merely because the schema can represent them; F1 purpose admission controls each field.

## Adversarial acceptance matrix

| Case | Required outcome |
| --- | --- |
| Garmin plus FIT copies | One activity only after explicit alias adjudication; otherwise both are quarantined from pooled exposure. |
| Late sync / offline import | Activity absent from earlier decision view and present only after its trusted `available_at`. |
| Stale plan revision | Decision/link remains historical but cannot apply against a newer active revision. |
| Partial workout | Preserve planned dose, delivered dose and measured partial dose separately; reason may remain unknown. |
| Manual-only completion | Occurrence can be confirmed; HR, speed and device dose remain reason-coded missing. |
| Corrected activity | Append corrected source, invalidate dependent current links/segments/views, retain historical lineage. |
| Missing HR | `not_collected`/`unknown` as supported; never zero and never imputed from a summary. |
| Malformed timestamp | Quarantine before ordering or linkage; never normalize an impossible offset. |
| Cross-account mismatch | Reject without exposing foreign identifiers or changing either account's counts. |
| Duplicate alias | Reject alias reuse across canonical activities; identical retries remain idempotent. |
| Unknown intent | Count actual exposure once and retain `unplanned_or_unknown_intent`; no prescription claim. |
| Conflicting sources | Keep both observations, mark affected metric `conflict`, exclude it until adjudicated. |
| Withdrawal after derived views | Deny current and historical reads, invalidate derived outputs/splits and leave other accounts intact. |

## Module boundaries and implementation scope

- `p2f_linkage.py`: private bounded pilot journal and authority enforcement; unchanged contract.
- `p2_trace.py` / `p2_fit_trace.py`: source-specific research probes; no automatic promotion to B0.
- `p2f_foundation.py`: synthetic F2.2 reference projector and format-neutral trace validator. It demonstrates invariants only.
- Future durable store: must provide transactional append, immutable lineage, live authorization checks, suppression before decryption, migrations and independently tested restore. The reference module is not that store.
- Future adapters: translate admitted source bytes into F2.2 events, never create trusted ownership/alias/link evidence from their own payload.
- P2-B: consumes only independently audited, eligible trace segments after its own Gate 2; it does not own ingestion or deduplication.

## Versioning and migration

F2.1 and `F2.1-pilot.2` remain immutable historical contracts. F2.2 is additive. A future implementation migrates by replaying original events through a pinned projector, not by overwriting stored rows. Every projection stores source schema, projector version and digest. If an old event cannot satisfy account, timestamp, missingness or lineage requirements, migration emits an exclusion with a reason; it does not invent defaults. Pilot data remain permanently excluded from confirmatory partitions.

## Promotion gates

Synthetic reference tests can establish only software/contract feasibility. P2-F Gate 2 remains blocked until a lawful human development source, supported device/firmware/export path, independent real-device reconciliation, observed coverage and audit burden, frozen margins, withdrawal/restore evidence and an independently signed manifest exist. P2-B remains blocked until P2-F provides authorized, linked, eligible repeated trace segments and P2-B freezes its own margins and manifest. Coaching effectiveness is outside this sprint.

