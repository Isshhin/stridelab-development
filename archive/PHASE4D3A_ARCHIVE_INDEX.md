# Phase 4D.3A checkpoint archives

## Phase 4D.3A-A Fix1: test-only correction (2026-10-26)

**Pending official verification; not accepted.** [Original A official run 40249781541](https://github.com/Isshhin/stridelab-development/actions/runs/40249781541) finished **257 passed / 1 failed out of 258**. The archive checks, 626/626 manifest, protected engine and build verification succeeded; the single failure is the new elevation keyboard test.

**Root cause confirmed in exact source and local Chromium:** the test preselected the final split, preserved that selection through metric tab switches, then pressed `End`+`Enter` while still targeting that last split. The chart's documented keyboard contract makes Enter/Space *toggle* the targeted split, so the first Enter correctly deselected it. The test incorrectly expected the selected count to remain one. Local Chromium reproducer observed counts **1 → 0 → 1**, then Home+Enter selected the first legitimate split, with zero page errors.

**Fix1 changes only** `suite/tests/p4d3a-run-intelligence.spec.js` plus handoff/documentation/evidence: check first Enter toggles off, second toggles back on, and Home+Enter maps the first elevation endpoint to the first valid split. No UI, data, chart logic, engine, assets or inherited assertions changed.

- Archive: `StrideLab-4D3A-A-fix1.zip` (**20,252,556 bytes**), SHA-256 `424063a8400e57eebd89012cd71b55bf38218678b78a4ee0f34206619cc81e87`, ZIP CRC and **629/629** authoritative manifest hashes checked.
- Rebuilt HTML: SHA-256 `286f82e08099c13c974845e8a1f4de8b82c2d275b81c241e457ada260ae7ba0b`, **byte-identical to original A candidate**.
- Protected engine SHA-256 `3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2`; protected 4D.1 baseline `cffaad8e9856d988f864cb09143a63aeac573b453aa0978abcded3dfb054bd9d`.
- Local evidence in ZIP: `4D3A-A-FIX1-CI-REPAIR.md` and `evidence/4D3A-A-FIX1-KEYBOARD-REPRO.json`.
- **Next:** upload the exact ZIP to a **private prerelease** tagged `phase4d3a-a-fix1`, asset filename `StrideLab-4D3A-A-fix1.zip`, then manually dispatch [Fix1 official Playwright + axe workflow](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-fix1-playwright.yml) on `main`. Must achieve **258 passed, 0 failed, 0 skipped, 0 flaky** before promoting Phase 4D.3A-A. WS5-D (253/253) is the last accepted fallback.


Updated 2026-10-26. **4D.3A-A candidate, official acceptance pending.** The prior WS5-D source remains the latest accepted baseline.

| Archive | SHA-256 | Bytes | Verified source entries |
|---|---|---:|---:|
| `StrideLab-4D3A-A-WIP.zip` | `340a6e0195cea0947171e4ab8c43d6eaf76732438b6a631c25934f409fbb8d00` | 20,204,466 | 623 |
| **`StrideLab-4D3A-A-candidate.zip`** | **`fd347ff2cc904750d6ed3cb9e4d4d88b1f6316e725477d74fe82afd56ec1862c`** | **20,229,146** | **626** |

Both archives opened without ZIP CRC errors; all manifest payload hashes matched. Candidate rebuilt HTML hash: `286f82e08099c13c974845e8a1f4de8b82c2d275b81c241e457ada260ae7ba0b`. Protected engine and 4D.1 baseline unchanged.

These are *locally created conversation artifacts*, not release assets until a private GitHub prerelease is uploaded. Archive WIP is a recovery snapshot, not the source to verify. Use the exact candidate with [CI workflow `phase4d3a-a-playwright.yml`](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-playwright.yml). The expected official suite has **258 test cases**, and no official A result is yet available. See [Phase 4D.3A status](../docs/PHASE4D3A_STATUS.md).
