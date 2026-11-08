# P2-X Foundation qualification wave — Handoff (2026-11-08)

Isolated branch: `research/p2x-foundation-qualification-20261108`, based on `7c5e0efdbfb525277ad530c169a10f7189c781ea`.

Batches 1–2: Reproduced two HIGH code defects: changed native event values and omitted eligible binding records could pass asserted consistency. Fixed in `p2x_lineage.py` using actual native record SHA-256 binding, full eligible native-event coverage, native source-revision and lineage-root verification, and track-native status checks. 8 new tests; 58 lineage tests total.

Batch 3: Hosted complete P2-A/C/D/E/X research suite **448/448 PASS** under Python 3, seeds 7, 213, 33. Git blob identities match hosted-tested source. New GitHub-hosted workflow on `ubuntu-24.04`: run #42482594419 **FAILED BEFORE START** (no reported runner assignment or steps). Not an observed unit test failure or CI success; no use of PC.

Batch 4 decision: `P2X_FOUNDATION_QUALIFICATION_WAVE_INTERNAL_FIXES_448_HOSTED_PASS`; **INDEPENDENT_ACCEPTANCE_NOT_READY**, **GATE2_CLOSED**. Unkeyed synthetic hashes, caller-asserted ledger and plan lineage, missing independently authenticated approval, external reviewer, and CI allocation remain open.

PR #34 remains OPEN/DRAFT/UNMERGED, original head `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e`. No Engine 1.5 / Android / Trackside / P2-F change. No Work, Codex or user PC.

Full details: `docs/research/phase2/p2-x-foundation-qualification-wave-2026-11-08.md`.
