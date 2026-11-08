# P2-X Wave 4 Handoff — 2026-11-08

Isolated branch: `research/p2x-integrity-wave4-20261108`, based on `4424d3dd33993354bff5d1e8dbb4a90d4766714a`.

- Reproduced three issue families in the source's exact old blob: duplicate identical native events (4 tracks), omitted ledger disposition lists, and future-outcome/policy-score key leakage.
- The new 18-case test module failed 15 cases against the old source and passed all cases after correction; 3 controls already passed baseline.
- Updated `research/validation/p2x_lineage.py`, added `research/validation/test_p2x_qual_boundaries.py`. Exact Git blob hashes: `2c6a768d5eb3b6c56d2b3f9c3644ad17ad8e4b04`, `4f10509899497424885029a2c6cc4e425fa20145`.
- Combined author-side Python 3.13.5 synthetic regression: **481/481 PASS** under two distinct hash seeds; compileall PASS. These are not GitHub Actions CI or independent review.
- Independent review packet: `docs/research/phase2/p2-x-independent-review-packet-2026-11-08.md`. No independent reviewer verdict received. Gate 2 remains CLOSED.
- CI blocker: GitHub-hosted research runs failed before step allocation; metadata/log APIs do not reveal the root cause. Another repo workflow succeeded during same period; do not claim a GitHub-wide outage or exhausted billing without evidence. Do not use user's self-hosted PC.
- PR #34 remains OPEN/DRAFT/UNMERGED at `5cd9b785d72d8f72914c32dc3f4f7d987b21a86e`. Engine 1.5, Android/Trackside and P2-F normative protocol remain unchanged. No Work/Codex/user PC.
- Next: independent external reviewer executes exact-head packet, authorized admin checks GitHub Actions policy/billing, then exact-head GitHub-hosted CI if permitted; retain separate P2-F operational/data-rights gate.
