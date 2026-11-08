# P2-X Wave 3 — Qualification handoff (2026-11-08)

Isolated branch: `research/p2x-roster-qualification-20261108`, parent `fa4e308b1c0f96205b7d7e3a88c1d4ee5187150c`.

- Reproduced six baseline failures: duplicate/missing native P2-C opportunity IDs and P2-D/E session IDs were accepted by existential target checks.
- Corrected full roster identity uniqueness in `research/validation/p2x_lineage.py`.
- Added 8 synthetic tests in `research/validation/test_p2x_qual_roster.py` (6 negative/2 positive).
- Combined isolated hosted unittest discovery **463/463 passed**, seeds 7 and 213. Git blobs match exact tested code. No combined GitHub Actions CI PASS claimed.
- GitHub Actions earlier research runs fail before runner/step allocation; billing/permissions/policy root cause unconfirmed. Other repo Actions workflows succeeded. No self-hosted PC fallback.
- Review packet: `docs/research/phase2/p2-x-foundation-review-wave3-2026-11-08.md`.
- CI diagnostic: `docs/research/phase2/p2-x-hosted-ci-investigation-2026-11-08.md`.
- **External reviewer signoff still absent**, custody and plan/source authority incomplete, Gate 2 CLOSED, zero production or coaching authority.
- PR #34 unchanged OPEN/DRAFT/UNMERGED at `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e`. Engine 1.5, Android/Trackside and P2-F protocol unchanged; no Work, Codex, or user's PC.
