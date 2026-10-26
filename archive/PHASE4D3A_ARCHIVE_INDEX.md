# Phase 4D.3A checkpoint archives

Updated 2026-10-26. **4D.3A-A candidate, official acceptance pending.** The prior WS5-D source remains the latest accepted baseline.

| Archive | SHA-256 | Bytes | Verified source entries |
|---|---|---:|---:|
| `StrideLab-4D3A-A-WIP.zip` | `340a6e0195cea0947171e4ab8c43d6eaf76732438b6a631c25934f409fbb8d00` | 20,204,466 | 623 |
| **`StrideLab-4D3A-A-candidate.zip`** | **`fd347ff2cc904750d6ed3cb9e4d4d88b1f6316e725477d74fe82afd56ec1862c`** | **20,229,146** | **626** |

Both archives opened without ZIP CRC errors; all manifest payload hashes matched. Candidate rebuilt HTML hash: `286f82e08099c13c974845e8a1f4de8b82c2d275b81c241e457ada260ae7ba0b`. Protected engine and 4D.1 baseline unchanged.

These are *locally created conversation artifacts*, not release assets until a private GitHub prerelease is uploaded. Archive WIP is a recovery snapshot, not the source to verify. Use the exact candidate with [CI workflow `phase4d3a-a-playwright.yml`](https://github.com/Isshhin/stridelab-development/actions/workflows/phase4d3a-a-playwright.yml). The expected official suite has **258 test cases**, and no official A result is yet available. See [Phase 4D.3A status](../docs/PHASE4D3A_STATUS.md).
