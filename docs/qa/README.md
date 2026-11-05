# StrideLab QA knowledge base

**Read first:** [Latest approved Engine 1.5 primary / Engine 1.0 controlled fallback / quality-session requirements](engine15-primary-plan-quality-requirements-20261105.md). The older Engine 1.0-default QA results remain historical; this corrected product target is **not yet implemented or verified**.

Read this file before starting QA work. This directory is the durable index; detailed design and audit evidence stays in the authoritative documents linked below.

## Start here

1. Record `git branch --show-current` and `git rev-parse HEAD`.
2. Read [known defects](known-defects.md), [regression coverage](regression-coverage.md), and the [current handoff](current-handoff.md).
3. Inspect the latest evidence. A passing result predating a code change is historical evidence, not verification of the changed code.
4. Select focused tests from the [verification playbook](verification-playbook.md), then broaden at meaningful checkpoints.
5. Investigate new failures or behavior affected by the change; do not repeat closed investigations without contradictory evidence.
6. Update these files only after a finding or fix is confirmed. Preserve historical records.

## Index

- [CURRENT product requirements and mandatory end-to-end acceptance](engine15-primary-plan-quality-requirements-20261105.md)

- [Feature inventory and behavior](feature-inventory.md)
- [Regression coverage and test selection](regression-coverage.md)
- [Known defects and limitations](known-defects.md)
- [Resolved defects](resolved-defects.md)
- [Verification playbook](verification-playbook.md)
- [Environment and MARC runner](environment.md)
- [Current handoff](current-handoff.md)

Authoritative current-task sources:

- [Trackside functional behavior audit](../reports/TRACKSIDE-FUNCTIONAL-BEHAVIOR-AUDIT-20261105.md)
- [Trackside usability and Garmin design](../superpowers/specs/2026-11-05-trackside-usability-garmin-design.md)
- [Execution plan](../superpowers/plans/2026-11-05-trackside-usability-garmin.md)

Do not copy large logs into this directory. Record the exact command, result count, commit SHA, and durable workflow/report link.
