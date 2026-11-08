# P2-X DEV-2 Batches 1–2 — handoff (2026-11-08)

Branch: `research/p2x-dev2-plan-lineage-review-20261108`; parent `0bb7440a4d90e18a6a777d81a734bbf87a8827ff`.
Batch 1: author-side code/architecture audit; high findings on P2-C native ID and unkeyed source/receipt authority; cross-track event namespace and independent check gaps. **Not** an external independent review.
Batch 2: `research/validation/p2x_lineage.py` adds invented-only scoped manifest, P2-C copy-on-write native ID migration, per-track event mappings, source revision/availability checks, SHA-256 payload claims and optional DEV-1 ledger projection comparison. No real authentication or plan-write permission.
New tests: `research/validation/test_p2x_lineage.py` — 50/50 passed in isolated hosted Python and two repeated hash-seed runs; exact Git source/test blobs confirmed.
Report: `docs/research/phase2/p2-x-dev2-foundation-review-lineage-2026-11-08.md`.
Prior combined 340 DEV-0 tests + 50 DEV-1 tests **not rerun in this wave**. No independent CI or reviewed DEV-2 live integration.
Next: Batch 3 combined exact-head regression on permitted non-PC infrastructure, then Batch 4 independent conformance/release checkpoint. No Gate 2, human/provider/FIT data, coaching effectiveness or production app changes.
PR #34 remains OPEN/DRAFT/UNMERGED at `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e`; Engine 1.5 and frozen Session G unchanged.
