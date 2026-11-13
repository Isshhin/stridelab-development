# P2-F private pilot readiness and operator handoff

## Current disposition — 2026-11-13

**`PRIVATE_PILOT_NOT_READY`; HUMAN/PROVIDER DATA ADMISSION NO.** This current status supersedes the prior `PRIVATE_PILOT_READY_AWAITING_EXPLICIT_PARTICIPANT_APPROVAL` heading and older handoff copies. The blank forms and synthetic code are preparation only. Do not transfer, receive, or process a human/provider record under this handoff. S1 remains `QUARANTINE_PENDING_PERMISSION`.

No source/device format is qualified for a real pilot. The TCX path has synthetic parser tests only; no real TCX dialect, firmware or device has passed reconciliation. Direct FIT remains a candidate requiring a fixed device/firmware, decoder and dependency freeze, rights review, and an independently authored offline reference. Neither candidate is an approval.

Before any admission, the independent authority/custody review and actual environment/operator qualification in the [qualification register](../phase2/p2-f-private-pilot-qualification-register-2026-11-07.md) and [reviewer brief](../phase2/p2-f-custody-authority-qualification-brief-2026-11-07.md) remain outstanding. The controller must also document the applicable privacy/legal basis and ethics determination; this repository does not make that determination. The separate v7 read-only preflight packet is ready only for a new independent authorization review. Its collector was never executed, and this handoff grants no execution or target-access permission.

The [pilot runbook](../phase2/p2-f-pilot-runbook.md) below is a procedure for a later separately admitted study, not a readiness decision.

## Operator sequence

1. Start with the [private custody contract](../phase2/p2-f-private-custody-contract.md). Fill in the actual encrypted location, roles, key holder, tool versions, deletion deadlines and backup behavior privately. Run the adverse restore drill with synthetic accounts on that infrastructure. A passed in-memory model cannot establish the behavior of the staging system.
2. Provide the [plain-language participant sheet](../phase2/p2-f-participant-sheet.md) with the completed particulars. Record the participant's independent choices and the steward's separate right-to-process decision in the private [admission instrument](../phase2/p2-f4-human-feasibility-admission.md). No signed grant is represented here.
3. Once the prerequisites hold, use the [runbook](../phase2/p2-f-pilot-runbook.md) and its [14 private worksheets](../phase2/p2-f-private-audit-worksheets.md). Register the roster, exclusions and versions. Freeze the independent reference before opening harness output. Keep separate counts for opportunities and incoming observations, measure actual reviewer burden and preserve original disagreements. Quarantine any unsupported recording. No completed worksheet belongs in the project repository.

## Engineering evidence

| Path | What the synthetic checks establish | Limitation for real use |
| --- | --- | --- |
| [TCX](../phase2/p2-trace-acquisition.md) | Version 0.2 tests child-element identity, time offsets, one activity/lap/track, reported summary and native cadence fields, missing/zero samples and registered trace QC. Malformed supported fields and conflicting identities quarantine. | Timer, sensor and calibrated-distance evidence require independent corroboration. No real TCX dialect, firmware, device or calibration has passed a pilot. |
| [FIT](../phase2/p2-fit-trace.md) and GPX | Synthetic FIT rejects device-relative clocks, unsupported timer groups and distance resets. It reports native sampling gaps. GPX stays outside the registered B0 path. | FIT_REAL_DEVICE_AUDIT_REQUIRED. No real device admission or FIT-to-B0 release. |
| [Linkage and recovery](../phase2/p2-f5-linkage-implementation.md) | Live authority gates historical replay, re-import, audit versions and restore before backup access. Scoped aliases, source corrections and changed parser/reference pins have explicit controls. The integrated TCX probe checks the original, link, bounds and freeze time before comparison and rechecks access before returning derived values. | Operator-supplied mappings, tool independence and durable suppression require verification outside the harness. Returned copies must be handled by the real custodian. |

**Reproducibility.** The [milestone verification](../phase2/p2-f-milestone-verification.md) covers immutable parent `32c9ed71e3ddaf7d77e8aba09009234f1ee1d4ea`, its 40 original tests and two successful CI runs. For the hardening revision, the commit containing this handoff pins the source and tests. CPython 3.12.10 ran `python -m unittest discover -s research/validation -p "test_p2*.py" -v`: 65 passed, zero failed or skipped. Full research unittest discovery with `python -m unittest discover -s research/validation -p "test_*.py" -v` ran 229 locally: 228 passed, zero failed, one skipped because Windows symlinks were unavailable. Local verification used a writable temporary directory and repository synthetic fixtures. The existing workflow discovers the new modules without changing CI configuration. Look up push and PR check results for the exact commit; the two parent runs are evidence for the parent only.

## Decision and remaining evidence

| Field | Disposition |
| --- | --- |
| PRIVATE_PILOT_READINESS | NOT_READY |
| S1_ADMISSION | NOT_ADMITTED |
| REAL_DEVICE_RECONCILIATION | NOT_RUN |
| P2_F_GATE_2 | NOT_READY |
| P2_B | PROTOCOL_READY_DATA_NOT_READY |

The [itemized unsigned Gate 2 register](../phase2/p2-f-gate2-manifest-draft.md) requires an admitted source, an independently reconciled device/firmware configuration, verified operational controls, development-based coverage/burden margins and an untouched confirmatory partition. P2-B additionally needs an audited acquisition configuration and prospectively registered, eligible repeats. Independent Gate 2 review is premature. No human observations or sealed outcomes were accessed, and the Session G architecture at `26a8331e642c888dd73acb94755dded063baab8d` remains frozen.
